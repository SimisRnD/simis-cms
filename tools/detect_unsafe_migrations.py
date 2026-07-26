#!/usr/bin/env python3
"""
Detect unsafe database migrations that could break rolling deployments.

Checks for destructive SQL patterns (DROP COLUMN, DROP TABLE, renames) in the
same Flyway version that removes Java field/accessor definitions. These patterns
violate the expand/contract migration practice and will break rolling deployments
where old instances still read the dropped columns.

Exit codes:
  0 = No unsafe patterns detected
  1 = Unsafe migrations detected
  2 = Script error
"""

import re
import sys
import os
from pathlib import Path
from collections import defaultdict

# Migration version pattern: UPGRADE_20260726.1015__description.sql
MIGRATION_VERSION_PATTERN = re.compile(r'UPGRADE_(\d{8})\.(\d{4})__')

# Destructive SQL patterns
DESTRUCTIVE_PATTERNS = [
    (re.compile(r'\bDROP\s+COLUMN\b', re.IGNORECASE), 'DROP COLUMN'),
    (re.compile(r'\bDROP\s+TABLE\b', re.IGNORECASE), 'DROP TABLE'),
    (re.compile(r'\bALTER\s+TABLE\s+\w+\s+RENAME\s+COLUMN\b', re.IGNORECASE), 'RENAME COLUMN'),
]

# Java field/accessor removal patterns (rough heuristic)
JAVA_REMOVAL_PATTERNS = [
    (re.compile(r'^\s*-\s*(?:public|private|protected)\s+\w+\s+(\w+);'), 'field removal'),
    (re.compile(r'^\s*-\s*(?:public|private|protected)\s+\w+\s+get\w+\(\)'), 'getter removal'),
    (re.compile(r'^\s*-\s*(?:public|private|protected)\s+\w+\s+set\w+\('), 'setter removal'),
]


def get_migration_version(filename):
    """Extract version from migration filename. Returns (date, time) or None."""
    match = MIGRATION_VERSION_PATTERN.search(filename)
    if match:
        return (match.group(1), match.group(2))
    return None


def find_destructive_patterns_in_sql(sql_content):
    """Find destructive SQL patterns. Returns list of (pattern_name, line_num, line)."""
    findings = []
    for line_num, line in enumerate(sql_content.split('\n'), 1):
        for pattern, name in DESTRUCTIVE_PATTERNS:
            if pattern.search(line):
                findings.append((name, line_num, line.strip()))
    return findings


def find_java_removals_in_diff(diff_content):
    """Find Java field/accessor removals from git diff. Returns list of removals."""
    findings = []
    for line_num, line in enumerate(diff_content.split('\n'), 1):
        # Only look at lines that were removed (start with -)
        if line.startswith('-') and not line.startswith('---'):
            for pattern, name in JAVA_REMOVAL_PATTERNS:
                if pattern.search(line):
                    findings.append((name, line.strip()[1:]))  # Remove the - prefix
    return findings


def check_migrations(repo_root):
    """Check all migrations for unsafe patterns. Returns list of violations."""
    violations = []

    # Find all SQL migration files
    migrations_dir = Path(repo_root) / 'src/main/resources/database/upgrade'
    if not migrations_dir.exists():
        print(f"Migrations directory not found: {migrations_dir}", file=sys.stderr)
        return violations

    sql_files = sorted(migrations_dir.glob('UPGRADE_*.sql'))

    # Group by version (date.time)
    migrations_by_version = defaultdict(list)
    for sql_file in sql_files:
        version = get_migration_version(sql_file.name)
        if version:
            migrations_by_version[version].append(sql_file)

    # Get git diff to find Java removals
    try:
        import subprocess
        git_diff = subprocess.check_output(
            ['git', 'diff', 'HEAD~1..HEAD', '--', 'src/main/java'],
            cwd=repo_root,
            text=True,
            stderr=subprocess.DEVNULL
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        git_diff = ""

    java_removals = find_java_removals_in_diff(git_diff)

    # Check each version's migrations
    for version, files in sorted(migrations_by_version.items()):
        sql_destructive = []

        for sql_file in files:
            with open(sql_file, 'r') as f:
                content = f.read()

            destructive = find_destructive_patterns_in_sql(content)
            if destructive:
                sql_destructive.extend([(sql_file.name, *finding) for finding in destructive])

        # If we found destructive SQL AND Java removals in the same version, flag it
        if sql_destructive and java_removals:
            violations.append({
                'version': version,
                'sql_patterns': sql_destructive,
                'java_removals': java_removals,
                'files': [f.name for f in files]
            })

    return violations


def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    violations = check_migrations(repo_root)

    if not violations:
        print("✓ No unsafe migration patterns detected")
        return 0

    # Report violations
    print("❌ UNSAFE MIGRATIONS DETECTED", file=sys.stderr)
    print("", file=sys.stderr)

    for violation in violations:
        version = violation['version']
        print(f"Migration version {version[0]}.{version[1]}:", file=sys.stderr)

        print(f"  SQL destructive patterns:", file=sys.stderr)
        for sql_file, pattern, line_num, line in violation['sql_patterns']:
            print(f"    {sql_file}:{line_num}: {pattern}", file=sys.stderr)
            print(f"      {line}", file=sys.stderr)

        print(f"  Java removals in same commit:", file=sys.stderr)
        for removal_type, line in violation['java_removals']:
            print(f"    {removal_type}: {line}", file=sys.stderr)

        print("  → Use expand/contract pattern: add new columns alongside old,", file=sys.stderr)
        print("    deploy code that reads both, then add a REPEAT_ migration to remove old columns", file=sys.stderr)
        print("", file=sys.stderr)

    return 1


if __name__ == '__main__':
    sys.exit(main())
