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
In the editor and every file it includes (``<%@ include file=... %>``,
``<jsp:directive.include file=.../>`` or ``<jsp:include page=.../>``):

  * a ``help-text`` element with no literal ``id``, so nothing can point at it.
    ``page-help`` paragraphs describe the whole page rather than one field, so
    they are left alone.
  * a help id that no ``aria-describedby`` points at.
  * an ``aria-describedby`` on ``<input type="hidden">``. A hidden input is never
    focused or announced, so help attached to it reaches no one. The four logo
    color pickers were linked this way, and so were silent despite looking linked.
  * an ``aria-describedby`` naming an id that doesn't exist, which is usually a
    typo and links nothing.

The check fails rather than passes when it can't see something, because a gate
that quietly reads less than the page renders passes the very change it exists to
catch:

  * an include it can't find, or whose path is computed, is a finding, not a skip.
  * an ``aria-describedby`` it can't resolve to an id is a finding.
    ``aria-describedby="${name}"`` is followed through ``<c:set var="name" ...>``
    (either attribute order, either quote style, ``value=`` or a tag body), which
    is how ``logo-color-picker.jspf`` picks the help for whichever of its four
    properties it is rendering. A variable that is never set, or is set from an
    expression, can't be resolved.
  * attributes are read whether double-quoted, single-quoted or unquoted, so
    ``type='hidden'`` or ``class=help-text`` can't slip past.

Comments and ``<script>`` bodies are blanked first, preserving line numbers, so
prose that mentions an attribute is not a link.

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

WEBAPP_ROOT = os.path.join("src", "main", "webapp")
JSP_ROOT = os.path.join(WEBAPP_ROOT, "WEB-INF", "jsp")
EDITOR = "admin/site-properties-editor.jsp"

COMMENT_RE = re.compile(r"<%--.*?--%>|<!--.*?-->", re.S)
# [^>]* after the tag name: the parser ends a script at </script foo> too. See
# check-icon-link-names.py, where CodeQL py/bad-tag-filter flagged the narrower form.
SCRIPT_RE = re.compile(r"<script\b.*?</script\b[^>]*>", re.S | re.I)
# A quoted attribute value, either quote style. Used wherever a tag is read whole, so a ">"
# inside a value never ends the tag early.
_QUOTED = r"(?:\"[^\"]*\"|'[^']*')"
_TAG_BODY = r"(?:[^>\"']|%s)*" % _QUOTED
INCLUDE_RES = (
    re.compile(r"<%@\s*include\b(" + _TAG_BODY + r")%>", re.S),
    re.compile(r"<jsp:directive\.include\b(" + _TAG_BODY + r")/?>", re.S),
    re.compile(r"<jsp:include\b(" + _TAG_BODY + r")/?>", re.S),
)
SET_SELF_CLOSING_RE = re.compile(r"<c:set\b(" + _TAG_BODY + r")/>", re.S)
SET_WITH_BODY_RE = re.compile(r"<c:set\b(" + _TAG_BODY + r")(?<!/)>(.*?)</c:set\s*>", re.S)
# Scriptlets and JSP action tags. These are blanked before reading HTML tags, because the
# editor puts <c:if ...>aria-describedby="..."</c:if> INSIDE an input's opening tag, and the
# ">" each <c:if> carries would otherwise end that tag early.
JSP_TAG_RE = re.compile(r"<%.*?%>|</?[a-zA-Z][\w-]*:[\w-]+\b" + _TAG_BODY + r"/?>", re.S)
ELEMENT_RE = re.compile(r"<([a-zA-Z][\w-]*)\b(" + _TAG_BODY + r")>", re.S)
# Double-quoted, single-quoted or unquoted, as HTML allows all three.
ATTR_RE = re.compile(r"([^\s\"'<>/=]+)\s*=\s*(?:\"([^\"]*)\"|'([^']*)'|([^\s\"'=<>`]+))")
VAR_RE = re.compile(r"^\$\{\s*(\w+)\s*\}$")
LITERAL_ID_RE = re.compile(r"^[A-Za-z][\w:.-]*$")


def _spaces(m: re.Match) -> str:
    return re.sub(r"[^\n]", " ", m.group(0))


def strip_comments(text: str) -> str:
    return SCRIPT_RE.sub(_spaces, COMMENT_RE.sub(_spaces, text))


def parse_attrs(text: str) -> list[tuple[str, str]]:
    """(lowercased name, value) for every attribute, however its value is quoted."""
    attrs = []
    for m in ATTR_RE.finditer(text):
        value = next(g for g in m.groups()[1:] if g is not None)
        attrs.append((m.group(1).lower(), value))
    return attrs


def _line(text: str, offset: int) -> int:
    return text[:offset].count("\n") + 1


def collect_files(root: str) -> tuple[list[str], list[tuple[str, int, str]]]:
    """The editor plus everything it includes, as paths relative to JSP_ROOT.

    Also returns a finding for every include that couldn't be followed, so a file the page
    renders is never silently left unchecked.
    """
    base = os.path.join(root, JSP_ROOT)
    webapp = os.path.join(root, WEBAPP_ROOT)
    order: list[str] = []
    problems: list[tuple[str, int, str]] = []
    pending = [EDITOR]
    while pending:
        rel = pending.pop(0)
        if rel in order:
            continue
        order.append(rel)
        with open(os.path.join(base, rel), encoding="utf-8", errors="replace") as handle:
            text = strip_comments(handle.read())
        for pattern in INCLUDE_RES:
            for m in pattern.finditer(text):
                attrs = dict(parse_attrs(m.group(1)))
                target = attrs.get("file", attrs.get("page"))
                line = _line(text, m.start())
                if not target or "${" in target or "<%" in target:
                    problems.append((rel, line, "include with a computed or missing path "
                                     "can't be followed, so it wasn't checked"))
                    continue
                if target.startswith("/"):
                    path = os.path.normpath(os.path.join(webapp, target.lstrip("/")))
                else:
                    path = os.path.normpath(os.path.join(base, os.path.dirname(rel), target))
                if not os.path.isfile(path):
                    problems.append((rel, line, 'included file "%s" not found, so it wasn\'t '
                                     "checked" % target))
                    continue
                pending.append(os.path.relpath(path, base).replace(os.sep, "/"))
    return order, problems


def collect_variables(text: str) -> dict[str, set[str | None]]:
    """Every value each <c:set> variable can take; None marks one set from an expression."""
    found: dict[str, set[str | None]] = {}
    sets = [(dict(parse_attrs(m.group(1))), None) for m in SET_SELF_CLOSING_RE.finditer(text)]
    sets += [(dict(parse_attrs(m.group(1))), m.group(2)) for m in SET_WITH_BODY_RE.finditer(text)]
    for attrs, body in sets:
        var = attrs.get("var")
        if not var:
            continue
        value = attrs.get("value", body)
        value = value.strip() if value is not None else None
        found.setdefault(var, set()).add(
            value if value is not None and LITERAL_ID_RE.match(value) else None)
    return found


def scan(root: str) -> tuple[list[str], list[tuple[str, int, str]]]:
    """Return the files read and (file, line, message) for every finding, in file order."""
    base = os.path.join(root, JSP_ROOT)
    files, findings = collect_files(root)
    elements = []  # (file, line, tag, attrs as list of (name, value))
    variables: dict[str, set[str | None]] = {}
    for rel in files:
        with open(os.path.join(base, rel), encoding="utf-8", errors="replace") as handle:
            text = strip_comments(handle.read())
        for var, values in collect_variables(text).items():
            variables.setdefault(var, set()).update(values)
        cleaned = JSP_TAG_RE.sub(_spaces, text)
        for m in ELEMENT_RE.finditer(cleaned):
            elements.append((rel, _line(cleaned, m.start()), m.group(1).lower(),
                             parse_attrs(m.group(2))))

    ids: set[str] = set()
    for _rel, _line_no, _tag, attrs in elements:
        for name, value in attrs:
            if name == "id" and "${" not in value:
                ids.add(value)

    help_paragraphs = []
    linked: set[str] = set()
    for rel, line, tag, attrs in elements:
        classes = next((v.split() for n, v in attrs if n == "class"), [])
        hidden = tag == "input" and any(
            n == "type" and v.strip().lower() == "hidden" for n, v in attrs)
        if "help-text" in classes and "page-help" not in classes:
            own_id = next((v for n, v in attrs if n == "id"), None)
            help_paragraphs.append((rel, line, own_id))
        for name, value in attrs:
            if name != "aria-describedby":
                continue
            if hidden:
                findings.append((rel, line, "aria-describedby on a hidden input is never "
                                 "announced; put it on the control the user reaches"))
                continue
            var = VAR_RE.match(value.strip())
            if var:
                values = variables.get(var.group(1))
                if not values or None in values:
                    findings.append((rel, line, 'aria-describedby="%s" can\'t be resolved to '
                                     "an id: set it with a literal <c:set> value" % value))
                tokens = sorted(v for v in (values or ()) if v is not None)
            else:
                tokens = value.split()
            for token in tokens:
                if "${" in token or "<%" in token:
                    findings.append((rel, line, 'aria-describedby="%s" can\'t be resolved to '
                                     "an id" % value))
                    continue
                if token not in ids:
                    findings.append((rel, line, 'aria-describedby="%s" names no element '
                                     "in the editor" % token))
                linked.add(token)

    for rel, line, own_id in help_paragraphs:
        if not own_id or "${" in own_id:
            findings.append((rel, line, "help text has no literal id, so no field can "
                             "point at it"))
        elif own_id not in linked:
            findings.append((rel, line, 'id="%s" has no aria-describedby pointing at it'
                             % own_id))
    findings.sort(key=lambda f: (files.index(f[0]), f[1]))
    return files, findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 on any finding (CI runs with this)")
    args = parser.parse_args()

    if not os.path.isfile(os.path.join(args.root, JSP_ROOT, EDITOR)):
        print("MISSING  %s" % os.path.join(JSP_ROOT, EDITOR), file=sys.stderr)
        return 1

    files, findings = scan(args.root)
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
          % (EDITOR, len(files)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
