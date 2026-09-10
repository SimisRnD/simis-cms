#!/usr/bin/env python3
"""Verify that declared field-length limits match the column widths they guard.

Background
----------
Issue #1740: almost no ``Save*Command`` validated a value against the width of the
column it was about to be written to. An over-length entry was refused nowhere on
the way down, so it reached Postgres, which rejected the write; the repository
logged the SQLException and returned null; and the form widget read that null as a
system fault and told the admin::

    Your information could not be saved due to a system error. Please try again.

Advice that cannot work, because the same value fails identically every time.

The fix puts the limit in the command as a constant. That creates a second copy of
a number whose truth lives in the DDL, and the two drift apart silently:

* a migration that **widens** a column leaves a stale constant, which merely
  refuses values the database would now accept;
* a migration that **narrows** one leaves a check that passes values the database
  then rejects -- which puts the original bug straight back, in the exact place
  someone had already fixed it.

Nothing else in the build would notice either case. This check does.

What it does
------------
Reads every ``@column <table>.<column>`` annotation in the Java sources, pairs it
with the ``int`` constant declared immediately below it, and compares that value
against the column width parsed from the install schema.

::

    // @column groups.name
    private static final int MAX_NAME_LENGTH = 100;

Three ways to fail, all of them real drift:

* the constant and the schema disagree;
* the annotated column does not exist in the schema (a typo, or a column that was
  renamed out from under the check);
* the annotated column has no width -- TEXT, BIGINT and friends -- so a character
  limit against it is meaningless and almost certainly names the wrong column.

The install schema is the reference because it is what a fresh database is built
from. A width changed only by an upgrade migration would make install and upgrade
disagree, which is its own bug (issue #1478) and is why upgrade files are scanned
for ``ALTER ... TYPE VARCHAR`` and reported when they contradict install.

Usage
-----
    python3 tools/check-column-length-limits.py [ROOT] [--strict]

``--strict`` exits non-zero on any finding, which is how CI runs it.
"""

import argparse
import os
import re
import sys

# // @column groups.name
ANNOTATION_RE = re.compile(r'//\s*@column\s+([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)')
# private static final int MAX_NAME_LENGTH = 100;
CONSTANT_RE = re.compile(r'\bint\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(\d+)\s*;')

CREATE_TABLE_RE = re.compile(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*\(',
                             re.IGNORECASE)
# name VARCHAR(100) UNIQUE NOT NULL,   -- trailing modifiers are ignored on purpose; requiring the
# line to end after the type is what made an earlier version of this match nothing at all
COLUMN_RE = re.compile(
    r'^\s*([A-Za-z_][A-Za-z0-9_]*)\s+'
    r'([A-Za-z][A-Za-z0-9_]*(?:\s+VARYING)?)'
    r'\s*(?:\((\d+)(?:\s*,\s*\d+)?\))?',
    re.IGNORECASE)
ALTER_TYPE_RE = re.compile(
    r'ALTER\s+TABLE\s+([A-Za-z_][A-Za-z0-9_]*)\s+ALTER\s+(?:COLUMN\s+)?([A-Za-z_][A-Za-z0-9_]*)\s+'
    r'(?:SET\s+DATA\s+)?TYPE\s+(?:VARCHAR|CHARACTER\s+VARYING)\s*\((\d+)\)',
    re.IGNORECASE)

SQL_KEYWORDS = {
    'primary', 'unique', 'constraint', 'foreign', 'check', 'insert', 'create',
    'alter', 'drop', 'select', 'update', 'delete', 'with', 'values', 'index',
}
WIDTH_BEARING = ('varchar', 'character varying', 'char')


def parse_install_schema(root):
    """table -> column -> (type, width or None), from the install DDL."""
    schema = {}
    install_dir = os.path.join(root, 'src', 'main', 'resources', 'database', 'install')
    if not os.path.isdir(install_dir):
        return schema
    for name in sorted(os.listdir(install_dir)):
        if not name.endswith('.sql'):
            continue
        with open(os.path.join(install_dir, name), 'r', encoding='utf-8', errors='replace') as handle:
            text = handle.read()
        position = 0
        while True:
            match = CREATE_TABLE_RE.search(text, position)
            if not match:
                break
            table = match.group(1).lower()
            body, end = _read_paren_body(text, match.end() - 1)
            position = end
            columns = schema.setdefault(table, {})
            for line in body.splitlines():
                stripped = line.strip()
                if not stripped or stripped.startswith('--'):
                    continue
                column_match = COLUMN_RE.match(stripped)
                if not column_match:
                    continue
                column = column_match.group(1).lower()
                if column in SQL_KEYWORDS:
                    continue
                column_type = column_match.group(2).strip().lower()
                width = int(column_match.group(3)) if column_match.group(3) else None
                columns.setdefault(column, (column_type, width))
    return schema


def _read_paren_body(text, open_index):
    """The text between a '(' and its matching ')', plus the index just past it."""
    depth = 0
    for index in range(open_index, len(text)):
        if text[index] == '(':
            depth += 1
        elif text[index] == ')':
            depth -= 1
            if depth == 0:
                return text[open_index + 1:index], index + 1
    return text[open_index + 1:], len(text)


def parse_upgrade_width_changes(root):
    """(table, column) -> [(file, width)] for widths changed by an upgrade migration."""
    changes = {}
    upgrade_root = os.path.join(root, 'src', 'main', 'resources', 'database', 'upgrade')
    for directory, _, files in os.walk(upgrade_root):
        for name in sorted(files):
            if not name.endswith('.sql'):
                continue
            path = os.path.join(directory, name)
            with open(path, 'r', encoding='utf-8', errors='replace') as handle:
                text = handle.read()
            for match in ALTER_TYPE_RE.finditer(text):
                key = (match.group(1).lower(), match.group(2).lower())
                changes.setdefault(key, []).append((os.path.relpath(path, root), int(match.group(3))))
    return changes


def collect_declarations(root):
    """Every '@column table.column' annotation paired with the constant beneath it."""
    declarations = []
    java_root = os.path.join(root, 'src', 'main', 'java')
    for directory, _, files in os.walk(java_root):
        for name in sorted(files):
            if not name.endswith('.java'):
                continue
            path = os.path.join(directory, name)
            with open(path, 'r', encoding='utf-8', errors='replace') as handle:
                lines = handle.readlines()
            for index, line in enumerate(lines):
                annotation = ANNOTATION_RE.search(line)
                if not annotation:
                    continue
                table, column = annotation.group(1).lower(), annotation.group(2).lower()
                constant = None
                # the constant is normally the next line; allow a couple of comment lines between
                for lookahead in range(index + 1, min(index + 5, len(lines))):
                    found = CONSTANT_RE.search(lines[lookahead])
                    if found:
                        constant = (found.group(1), int(found.group(2)), lookahead + 1)
                        break
                    if lines[lookahead].strip() and not lines[lookahead].strip().startswith('//'):
                        break
                declarations.append({
                    'file': os.path.relpath(path, root),
                    'line': index + 1,
                    'table': table,
                    'column': column,
                    'constant': constant,
                })
    return declarations


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('root', nargs='?', default='.')
    parser.add_argument('--strict', action='store_true',
                        help='exit non-zero when anything is reported')
    args = parser.parse_args()
    root = os.path.abspath(args.root)

    schema = parse_install_schema(root)
    upgrade_changes = parse_upgrade_width_changes(root)
    declarations = collect_declarations(root)

    findings = []
    for declaration in declarations:
        where = "%s:%d" % (declaration['file'], declaration['line'])
        table, column = declaration['table'], declaration['column']

        if declaration['constant'] is None:
            findings.append("%s  @column %s.%s has no int constant beneath it" % (where, table, column))
            continue

        constant_name, constant_value, constant_line = declaration['constant']
        where = "%s:%d" % (declaration['file'], constant_line)

        if table not in schema:
            findings.append("%s  %s guards %s.%s, but no CREATE TABLE %s is in the install schema"
                            % (where, constant_name, table, column, table))
            continue
        if column not in schema[table]:
            findings.append("%s  %s guards %s.%s, but %s has no column %s in the install schema"
                            % (where, constant_name, table, column, table, column))
            continue

        column_type, width = schema[table][column]
        if width is None:
            findings.append("%s  %s guards %s.%s, which is %s and has no character width -- "
                            "a length limit against it is meaningless"
                            % (where, constant_name, table, column, column_type.upper()))
            continue
        if not any(column_type.startswith(prefix) for prefix in WIDTH_BEARING):
            findings.append("%s  %s guards %s.%s, which is %s(%d), not a character type"
                            % (where, constant_name, table, column, column_type.upper(), width))
            continue
        if constant_value != width:
            findings.append("%s  %s is %d but %s.%s is %s(%d)%s"
                            % (where, constant_name, constant_value, table, column,
                               column_type.upper(), width,
                               "  -- the check now PASSES values the database rejects"
                               if constant_value > width else ""))
            continue

        for path, altered_width in upgrade_changes.get((table, column), []):
            if altered_width != width:
                findings.append("%s  %s.%s is %s(%d) in install but %s sets it to VARCHAR(%d); "
                                "install and upgrade disagree (see issue #1478)"
                                % (where, table, column, column_type.upper(), width, path, altered_width))

    checked = sum(1 for d in declarations if d['constant'] is not None)
    if findings:
        print("Column length limits that do not match the schema:\n")
        for finding in findings:
            print("  " + finding)
        print("\nSummary: %d finding(s), %d declared limit(s) checked." % (len(findings), checked))
        return 1 if args.strict else 0

    print("Summary: 0 findings, %d declared limit(s) checked against the install schema." % checked)
    return 0


if __name__ == '__main__':
    sys.exit(main())
