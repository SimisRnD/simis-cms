#!/usr/bin/env python3
"""Keep every settings field's help text tied to the field it describes.

``admin/site-properties-editor.jsp`` renders every settings page in the admin
console. Each field's help is a ``<p class="help-text" id="...HelpText">``, and the
input that renders the field points at it with ``aria-describedby``. That pointer is
the only thing that makes a screen reader announce the help when the user reaches
the field. Without it the help is just a paragraph further down the table, read only
by someone who goes looking for it. That fails WCAG 1.3.1 Info and Relationships and
3.3.2 Labels or Instructions, both Level A in WCAG 2.0, so both in scope for
Section 508 as written.

The pointer is easy to forget, because the help and the input are written in
different places: the help sits below the field loop, but each input type (text,
secret, switch, select, image) has its own branch with its own list of
``aria-describedby`` lines. Adding help without adding a line to the right branch
looks finished and renders correctly. By November 2026, 45 of the editor's 116 field
help paragraphs had no pointer, 10 of them because they had no id to point at.

What counts as a finding
------------------------
In the editor and every file it statically includes (``<%@ include file=... %>``):

  * a ``help-text`` element with no literal ``id``, so nothing can point at it.
    ``page-help`` paragraphs describe the whole page rather than one field, so
    they are left alone.
  * a help id that no ``aria-describedby`` points at.
  * an ``aria-describedby`` on ``<input type="hidden">``. A hidden input is never
    focused or announced, so help attached to it reaches no one. The four logo
    color pickers were linked this way, and so were silent despite looking linked.
  * an ``aria-describedby`` naming an id that doesn't exist, which is usually a
    typo and links nothing.

``aria-describedby="${name}"`` is followed through ``<c:set var="name" value="..."/>``,
which is how ``logo-color-picker.jspf`` picks the help for whichever of its four
properties it is rendering. Comments and ``<script>`` bodies are blanked first,
preserving line numbers, so prose that mentions an attribute is not a link.

There is no backlog: every field help in the editor is linked, so any finding is a
regression.

This is a read-only reporter. It changes no files.

Exit status is 1 under --strict when a finding is reported.
"""
from __future__ import annotations

import argparse
import os
import re
import sys

JSP_ROOT = os.path.join("src", "main", "webapp", "WEB-INF", "jsp")
EDITOR = "admin/site-properties-editor.jsp"

COMMENT_RE = re.compile(r"<%--.*?--%>|<!--.*?-->", re.S)
# [^>]* after the tag name: the parser ends a script at </script foo> too. See
# check-icon-link-names.py, where CodeQL py/bad-tag-filter flagged the narrower form.
SCRIPT_RE = re.compile(r"<script\b.*?</script\b[^>]*>", re.S | re.I)
INCLUDE_RE = re.compile(r'<%@\s*include\s+file\s*=\s*"([^"]+)"\s*%>')
SET_RE = re.compile(r'<c:set\s+var\s*=\s*"(\w+)"\s+value\s*=\s*"([\w-]+)"\s*/>')
# Scriptlets and JSP action tags. These are blanked before reading HTML tags, because the
# editor puts <c:if ...>aria-describedby="..."</c:if> INSIDE an input's opening tag, and the
# ">" each <c:if> carries would otherwise end that tag early. Quoted strings are consumed
# whole so a ">" inside an attribute value doesn't end the action tag either.
JSP_TAG_RE = re.compile(
    r"<%.*?%>|</?[a-zA-Z][\w-]*:[\w-]+\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*/?>", re.S)
ELEMENT_RE = re.compile(r"<([a-zA-Z][\w-]*)\b((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>", re.S)
ATTR_RE = re.compile(r"([\w:-]+)\s*=\s*\"([^\"]*)\"")
VAR_RE = re.compile(r"^\$\{\s*(\w+)\s*\}$")


def _spaces(m: re.Match) -> str:
    return re.sub(r"[^\n]", " ", m.group(0))


def strip_comments(text: str) -> str:
    return SCRIPT_RE.sub(_spaces, COMMENT_RE.sub(_spaces, text))


def collect_files(root: str) -> list[str]:
    """The editor plus everything it statically includes, as paths relative to JSP_ROOT."""
    base = os.path.join(root, JSP_ROOT)
    order: list[str] = []
    pending = [EDITOR]
    while pending:
        rel = pending.pop(0)
        if rel in order or not os.path.isfile(os.path.join(base, rel)):
            continue
        order.append(rel)
        with open(os.path.join(base, rel), encoding="utf-8", errors="replace") as handle:
            text = strip_comments(handle.read())
        for target in INCLUDE_RE.findall(text):
            joined = os.path.normpath(os.path.join(os.path.dirname(rel), target))
            pending.append(joined.replace(os.sep, "/"))
    return order


def scan(root: str) -> list[tuple[str, int, str]]:
    """Return (file, line, message) for every finding, in file order."""
    base = os.path.join(root, JSP_ROOT)
    files = collect_files(root)
    elements = []  # (file, line, tag, attrs as list of (name, value))
    variables: dict[str, set[str]] = {}
    for rel in files:
        with open(os.path.join(base, rel), encoding="utf-8", errors="replace") as handle:
            text = strip_comments(handle.read())
        for var, value in SET_RE.findall(text):
            variables.setdefault(var, set()).add(value)
        cleaned = JSP_TAG_RE.sub(_spaces, text)
        for m in ELEMENT_RE.finditer(cleaned):
            line = cleaned[: m.start()].count("\n") + 1
            elements.append((rel, line, m.group(1).lower(), ATTR_RE.findall(m.group(2))))

    ids: set[str] = set()
    for _rel, _line, _tag, attrs in elements:
        for name, value in attrs:
            if name.lower() == "id" and "${" not in value:
                ids.add(value)

    findings: list[tuple[str, int, str]] = []
    help_paragraphs = []
    linked: set[str] = set()
    for rel, line, tag, attrs in elements:
        classes = [v.split() for n, v in attrs if n.lower() == "class"]
        classes = classes[0] if classes else []
        hidden = tag == "input" and any(
            n.lower() == "type" and v.strip().lower() == "hidden" for n, v in attrs)
        if "help-text" in classes and "page-help" not in classes:
            own_id = [v for n, v in attrs if n.lower() == "id"]
            help_paragraphs.append((rel, line, own_id[0] if own_id else None))
        for name, value in attrs:
            if name.lower() != "aria-describedby":
                continue
            if hidden:
                findings.append((rel, line, "aria-describedby on a hidden input is never "
                                 "announced; put it on the control the user reaches"))
                continue
            var = VAR_RE.match(value.strip())
            tokens = sorted(variables.get(var.group(1), set())) if var else value.split()
            for token in tokens:
                if "${" in token:
                    continue
                if token not in ids:
                    findings.append((rel, line, 'aria-describedby="%s" names no element '
                                     "in the editor" % token))
                linked.add(token)

    for rel, line, own_id in help_paragraphs:
        if not own_id:
            findings.append((rel, line, "help text has no id, so no field can point at it"))
        elif own_id not in linked:
            findings.append((rel, line, 'id="%s" has no aria-describedby pointing at it'
                             % own_id))
    findings.sort(key=lambda f: (files.index(f[0]), f[1]))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 on any finding (CI runs with this)")
    args = parser.parse_args()

    if not os.path.isfile(os.path.join(args.root, JSP_ROOT, EDITOR)):
        print("MISSING  %s" % os.path.join(JSP_ROOT, EDITOR), file=sys.stderr)
        return 1

    findings = scan(args.root)
    if findings:
        print("FAIL  %d settings help problem(s) in %s and its includes"
              % (len(findings), EDITOR))
        for rel, line, message in findings:
            print("  %s: line %d  %s" % (rel, line, message))
        print()
        print("A field's help is announced with the field only when the field points at it.")
        print("Give the help paragraph an id, then add a line for the property to the input")
        print("branch that renders it (text, secret, switch, select, ...):")
        print("  <p class=\"help-text\" id=\"mailPortHelpText\">...</p>")
        print("  <c:if test=\"${siteProperty.name eq 'mail.port'}\"> "
              "aria-describedby=\"mailPortHelpText\"</c:if>")
        return 1 if args.strict else 0

    print("OK  every field help in %s is linked to its field (%d file(s) checked)"
          % (EDITOR, len(collect_files(args.root))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
