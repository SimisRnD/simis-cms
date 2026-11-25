#!/usr/bin/env python3
"""Keep every field's help text tied to the field it describes, in every template.

A field's help is a ``class="help-text"`` element, and the field points at it with
``aria-describedby``. That pointer is the only thing that makes a screen reader
announce the help when the user reaches the field. Without it the help is just a
paragraph nearby, read only by someone who goes looking for it. That fails WCAG
1.3.1 Info and Relationships and 3.3.2 Labels or Instructions, both Level A in WCAG
2.0, so both in scope for Section 508 as written.

The pointer is easy to forget because nothing breaks without it: the page renders
the same. The settings editor alone had 45 of 116 field help paragraphs without
one (PR 2178), and 13 more turned up in 8 other forms (PR 2179).

What counts as a finding
------------------------
Each ``.jsp`` is read together with every file it includes (``<%@ include
file=... %>``, ``<jsp:directive.include file=.../>`` or ``<jsp:include page=.../>``),
because an id and the pointer to it can sit in different files. A ``.jspf`` that
no ``.jsp`` includes is read on its own.

Help that isn't linked, counted per file against BACKLOG:

  * a ``help-text`` element with an id that no ``aria-describedby`` or
    ``aria-labelledby`` points at. An id built from an expression, such as
    ``emailHelpText${widgetContext.uniqueId}``, counts as linked only when a
    pointer is built from exactly the same expression.
  * a ``help-text`` element with no id, right after a field: after an
    ``<input>``, ``<select>`` or ``<textarea>``, or after a ``</label>``, with
    only whitespace, JSP tags, line breaks, hidden inputs and ``<div>`` edges
    between. A further help paragraph right after such help counts too, since
    it describes the same field. Nothing can point at help that has no id.
    A button between a field and its help ends the search, so help placed
    after a field's button isn't caught; that keeps a link styled as help,
    like "Forgot your password?" after the password's Show button, from
    being reported.

Most ``help-text`` elements with no id aren't field help at all: list and details
pages use the class for captions, dialogs for their descriptions, checkout for its
"Edit" links. Those are left alone, as are ``page-help`` paragraphs, which
describe a whole page, and help inside a ``<label>``, which is already part of the
field's name. The settings editor is the exception (ALL_FIELD_HELP): every help
paragraph there describes one field, so every one needs an id.

Broken links, which always fail:

  * an ``aria-describedby`` on ``<input type="hidden">``. A hidden input is
    never focused or announced, so help attached to it reaches no one.
  * a pointer naming an id that doesn't exist, which is usually a typo.
  * a pointer that can't be resolved to an id. ``${name}`` is followed through
    ``<c:set var="name" ...>`` (either attribute order or quote style, with
    ``value=`` or a tag body); anything else must match an element id built
    from the same expression.
  * an include that can't be found, or whose path is computed, so part of the
    page went unread. LAYOUT_INCLUDES records the one exception.

Attributes are read whether double-quoted, single-quoted or unquoted. Comments and
``<script>`` bodies are blanked first, preserving line numbers, so prose that
mentions an attribute is not a link.

BACKLOG works like ``check-icon-link-names.py``'s: a per-file count of help still
to be linked. A count ABOVE the recorded number fails; a count BELOW is reported
as a note, not a failure, so a PR that links help is never blocked and merge order
is not load-bearing. Broken links have no backlog.

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

# Help still to be linked, by path relative to JSP_ROOT: field help with no id, or an id no
# pointer names. Debt with a deadline, not an exemption -- a count ABOVE the recorded number
# fails, a count BELOW is only a note, so a PR that links help is never blocked.
BACKLOG: dict[str, int] = {
    "admin/bot-list-form.jsp": 2,
    "admin/calendar-event-form.jsp": 7,
    "admin/calendar-form.jsp": 1,
    "admin/capability-grants.jsp": 1,
    "admin/content-form.jsp": 1,
    "admin/dataset-sync.jsp": 1,
    "admin/dataset-upload-form.jsp": 1,
    "admin/folder-files-list.jsp": 1,
    "admin/form-definition-form.jsp": 3,
    "admin/form-field-form.jsp": 4,
    "admin/group-form.jsp": 1,
    "admin/integration-registry.jsp": 1,
    "admin/role-capabilities-form.jsp": 2,
    "admin/sitemap-editor.jsp": 1,
    "admin/social-media-link-form.jsp": 1,
    "admin/user-form.jsp": 2,
    "admin/web-redirect-form.jsp": 1,
    "admin/webhook-subscription-form.jsp": 1,
    "calendar/full-calendar.jsp": 3,
    "cms/web-page-templates.jsp": 1,
    "mailinglists/email-subscribe-inline-form.jsp": 1,
    "mailinglists/email-subscribe-vertical-form.jsp": 2,
}

# Files where every help-text element describes one field, so every one needs an id however
# it is laid out. The settings editor writes all its help below the field loop, away from the
# inputs, so the "right after a field" test below would see none of it.
ALL_FIELD_HELP = {
    "admin/site-properties-editor.jsp",
}

# Computed includes that are expected, by (file, path expression). main.jsp is the layout:
# ${PageBody} is whichever page body the request renders, and every body JSP is read on its own
# as its own entry, so nothing goes unread. Checked in both directions: an entry whose file no
# longer has that include is reported, so this can't outlive what it excuses.
LAYOUT_INCLUDES = {
    ("main.jsp", "${PageBody}"),
}

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
# Scriptlets and JSP action tags. These are blanked before reading HTML tags, because templates
# put <c:if ...>aria-describedby="..."</c:if> INSIDE an input's opening tag, and the ">" each
# <c:if> carries would otherwise end that tag early.
JSP_TAG_RE = re.compile(r"<%.*?%>|</?[a-zA-Z][\w-]*:[\w-]+\b" + _TAG_BODY + r"/?>", re.S)
# Any HTML start or end tag, read whole.
TAG_RE = re.compile(r"<(/?)([a-zA-Z][\w-]*)\b(" + _TAG_BODY + r")>", re.S)
# Double-quoted, single-quoted or unquoted, as HTML allows all three.
ATTR_RE = re.compile(r"([^\s\"'<>/=]+)\s*=\s*(?:\"([^\"]*)\"|'([^']*)'|([^\s\"'=<>`]+))")
VAR_RE = re.compile(r"^\$\{\s*(\w+)\s*\}$")
LITERAL_ID_RE = re.compile(r"^[A-Za-z][\w:.-]*$")

POINTERS = ("aria-describedby", "aria-labelledby")
CONTROLS = ("input", "select", "textarea")
NOT_FIELDS = ("hidden", "submit", "button", "reset", "image")
VOID = ("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source",
        "track", "wbr")

LINK = "link"      # help not linked yet; counted against BACKLOG
BROKEN = "broken"  # always a failure


def _spaces(m: re.Match) -> str:
    return re.sub(r"[^\n]", " ", m.group(0))


def strip_comments(text: str) -> str:
    return SCRIPT_RE.sub(_spaces, COMMENT_RE.sub(_spaces, text))


def parse_attrs(text: str, offset: int = 0) -> list[tuple[str, str, int, int]]:
    """(lowercased name, value, value start, value end) for every attribute.

    The positions are offsets into the text the attributes were read from, plus offset, so a
    value read from blanked text can be looked up in the original."""
    attrs = []
    for m in ATTR_RE.finditer(text):
        group = next(g for g in (2, 3, 4) if m.group(g) is not None)
        attrs.append((m.group(1).lower(), m.group(group),
                      offset + m.start(group), offset + m.end(group)))
    return attrs


def attr_map(text: str) -> dict[str, str]:
    return {name: value for name, value, _s, _e in parse_attrs(text)}


def _line(text: str, offset: int) -> int:
    return text[:offset].count("\n") + 1


def normalize(expression: str) -> str:
    return re.sub(r"\s+", " ", expression).strip()


def split_tokens(value: str) -> list[str]:
    """Split an id list on whitespace, but not inside ${...} or a <tag ...>."""
    tokens, current, depth = [], "", 0
    i = 0
    while i < len(value):
        ch = value[i]
        if value.startswith("${", i):
            depth += 1
            current += "${"
            i += 2
            continue
        if ch == "<":
            depth += 1
        elif ch in "}>" and depth:
            depth -= 1
        if ch.isspace() and not depth:
            if current:
                tokens.append(current)
            current = ""
        else:
            current += ch
        i += 1
    if current:
        tokens.append(current)
    return tokens


def is_expression(value: str) -> bool:
    return "${" in value or "<" in value


def read(root: str, rel: str) -> str:
    with open(os.path.join(root, JSP_ROOT, rel), encoding="utf-8", errors="replace") as handle:
        return strip_comments(handle.read())


def includes_of(root: str, rel: str, text: str, used_layout: set):
    """Yield (path relative to JSP_ROOT, or None) and a problem message for each include."""
    base = os.path.join(root, JSP_ROOT)
    webapp = os.path.join(root, WEBAPP_ROOT)
    for pattern in INCLUDE_RES:
        for m in pattern.finditer(text):
            attrs = attr_map(m.group(1))
            target = attrs.get("file", attrs.get("page"))
            line = _line(text, m.start())
            if target and (rel, normalize(target)) in LAYOUT_INCLUDES:
                used_layout.add((rel, normalize(target)))
                continue
            if not target or is_expression(target):
                yield None, (rel, line, BROKEN, "include with a computed or missing path "
                             "can't be followed, so it wasn't checked")
                continue
            if target.startswith("/"):
                path = os.path.normpath(os.path.join(webapp, target.lstrip("/")))
            else:
                path = os.path.normpath(os.path.join(base, os.path.dirname(rel), target))
            if not os.path.isfile(path):
                yield None, (rel, line, BROKEN, 'included file "%s" not found, so it wasn\'t '
                             "checked" % target)
                continue
            inside = os.path.relpath(path, base).replace(os.sep, "/")
            if inside.startswith("../"):
                continue  # outside the templates (a static asset); nothing to check
            yield inside, None


def collect_unit(root: str, entry: str, texts: dict, used_layout: set):
    """The entry plus everything it includes, and a finding for each include not followed."""
    order, problems, pending = [], [], [entry]
    while pending:
        rel = pending.pop(0)
        if rel in order:
            continue
        order.append(rel)
        if rel not in texts:
            texts[rel] = read(root, rel)
        for inside, problem in includes_of(root, rel, texts[rel], used_layout):
            if problem:
                problems.append(problem)
            else:
                pending.append(inside)
    return order, problems


def collect_variables(text: str) -> dict[str, set]:
    """Every value each <c:set> variable can take; None marks one set from an expression."""
    found: dict[str, set] = {}
    sets = [(attr_map(m.group(1)), None) for m in SET_SELF_CLOSING_RE.finditer(text)]
    sets += [(attr_map(m.group(1)), m.group(2)) for m in SET_WITH_BODY_RE.finditer(text)]
    for attrs, body in sets:
        var = attrs.get("var")
        if not var:
            continue
        value = attrs.get("value", body)
        value = value.strip() if value is not None else None
        found.setdefault(var, set()).add(
            value if value is not None and LITERAL_ID_RE.match(value) else None)
    return found


class Element:
    """One HTML start tag, with each attribute's value as written (EL and JSP tags kept)."""

    def __init__(self, rel, line, tag, attrs, raw_text):
        self.rel, self.line, self.tag = rel, line, tag
        self.attrs = [(name, value, raw_text[start:end]) for name, value, start, end in attrs]

    def get(self, name):
        return next((v for n, v, _r in self.attrs if n == name), None)

    def raw(self, name):
        return next((r for n, _v, r in self.attrs if n == name), None)


def tokenize(rel: str, text: str):
    """Start tags, end tags and the text between them, in document order."""
    cleaned = JSP_TAG_RE.sub(_spaces, text)
    tokens, last = [], 0
    for m in TAG_RE.finditer(cleaned):
        if m.start() > last:
            tokens.append(("text", cleaned[last:m.start()], None))
        tag = m.group(2).lower()
        if m.group(1):
            tokens.append(("end", tag, None))
        else:
            attrs = parse_attrs(m.group(3), m.start(3))
            tokens.append(("start", tag, Element(rel, _line(cleaned, m.start()), tag, attrs, text)))
        last = m.end()
    if last < len(cleaned):
        tokens.append(("text", cleaned[last:], None))
    return tokens


def classes(element: Element) -> list[str]:
    return (element.get("class") or "").split()


def is_help(element: Element) -> bool:
    names = classes(element)
    return "help-text" in names and "page-help" not in names and element.tag != "label"


def end_of(tokens, i) -> int:
    """Index of the end tag closing the start tag at i, or i if it isn't closed."""
    tag, depth = tokens[i][1], 0
    for j in range(i, len(tokens)):
        kind, name, _e = tokens[j]
        if name != tag:
            continue
        if kind == "start":
            depth += 1
        elif kind == "end":
            depth -= 1
            if depth == 0:
                return j
    return i


def label_spans(tokens) -> list[tuple[int, int]]:
    """(start, end) token indexes of every label that plainly closes.

    Labels can't nest, so a label closes at the next </label>. One that meets another <label>
    first, which is how a <c:choose> with a label in each branch reads once the JSP tags are
    gone, gets no span: treating everything after it as inside a label would hide real help."""
    spans = []
    for i, (kind, name, _e) in enumerate(tokens):
        if kind != "start" or name != "label":
            continue
        for j in range(i + 1, len(tokens)):
            if tokens[j][1] == "label" and tokens[j][0] in ("start", "end"):
                if tokens[j][0] == "end":
                    spans.append((i, j))
                break
    return spans


def contains_control(tokens, i, j) -> bool:
    return any(kind == "start" and (name in CONTROLS or name == "label")
               for kind, name, _e in tokens[i + 1:j])


def follows_field(tokens, i, help_ends: dict) -> bool:
    """Whether the help starting at token i comes right after a field."""
    j = i - 1
    while j >= 0:
        kind, name, element = tokens[j]
        if kind == "text":
            if name.replace("&nbsp;", "").strip():
                return False
        elif kind == "start":
            if name in ("br", "wbr", "div"):
                pass
            elif name == "input":
                kind_of = (element.get("type") or "text").strip().lower()
                if kind_of != "hidden":
                    return kind_of not in NOT_FIELDS
            else:
                return name in ("select", "textarea")
        else:
            if name in ("label", "select", "textarea"):
                return True
            if name != "div":
                return help_ends.get(j, False)
        j -= 1
    return False


def scan_unit(root: str, files: list[str], texts: dict):
    """(file, line, kind, message) for every finding in one entry and its includes."""
    findings = []
    variables: dict[str, set] = {}
    elements, helps = [], []
    for rel in files:
        text = texts[rel]
        for var, values in collect_variables(text).items():
            variables.setdefault(var, set()).update(values)
        tokens = tokenize(rel, text)
        help_ends: dict[int, bool] = {}
        labels = label_spans(tokens)
        for i, (kind, name, element) in enumerate(tokens):
            if kind != "start":
                continue
            elements.append(element)
            if not is_help(element):
                continue
            j = end_of(tokens, i) if name not in VOID else i
            if contains_control(tokens, i, j):
                continue  # a container holding its own controls, such as a list of checkboxes
            field = follows_field(tokens, i, help_ends)
            help_ends[j] = field
            if element.raw("id") is None and any(start < i < end for start, end in labels):
                continue  # inside the field's label, so already part of its name
            helps.append((element, field, rel in ALL_FIELD_HELP))

    literal_ids = {e.get("id") for e in elements if e.get("id") and not is_expression(e.raw("id"))}
    expression_ids = {normalize(e.raw("id")) for e in elements
                      if e.get("id") is not None and is_expression(e.raw("id"))}
    linked: set[str] = set()
    for element in elements:
        hidden = element.tag == "input" and (element.get("type") or "").strip().lower() == "hidden"
        for name, _value, raw in element.attrs:
            if name not in POINTERS:
                continue
            if hidden and name == "aria-describedby":
                findings.append((element.rel, element.line, BROKEN, "aria-describedby on a "
                                 "hidden input is never announced; put it on the control the "
                                 "user reaches"))
                continue
            for token in split_tokens(raw):
                var = VAR_RE.match(token)
                if var and variables.get(var.group(1)) and None not in variables[var.group(1)]:
                    targets = sorted(variables[var.group(1)])
                elif is_expression(token):
                    if normalize(token) in expression_ids:
                        linked.add(normalize(token))
                    else:
                        findings.append((element.rel, element.line, BROKEN, '%s="%s" can\'t be '
                                         "resolved to an id: use a literal id, a literal "
                                         "<c:set> value, or the same expression as the "
                                         "element's id" % (name, normalize(raw))))
                    continue
                else:
                    targets = [token]
                for target in targets:
                    if target not in literal_ids:
                        findings.append((element.rel, element.line, BROKEN, '%s="%s" names no '
                                         "element in this template or its includes"
                                         % (name, target)))
                    linked.add(target)

    for element, field, all_field_help in helps:
        own = element.raw("id")
        if own is None or not own.strip():
            if all_field_help:
                findings.append((element.rel, element.line, LINK, "help text has no id, so no "
                                 "field can point at it"))
            elif field:
                findings.append((element.rel, element.line, LINK, "help text right after a "
                                 "field has no id, so the field can't point at it"))
        elif normalize(own) not in linked:
            findings.append((element.rel, element.line, LINK, 'id="%s" has no '
                             "aria-describedby pointing at it" % normalize(own)))
    return findings


def scan(root: str):
    """Return the files read and every finding, de-duplicated, in file and line order."""
    base = os.path.join(root, JSP_ROOT)
    every = []
    for dirpath, _dirs, names in os.walk(base):
        for name in names:
            if name.endswith((".jsp", ".jspf")):
                every.append(os.path.relpath(os.path.join(dirpath, name), base).replace(os.sep, "/"))
    every.sort()
    texts: dict[str, str] = {}
    used_layout: set = set()
    findings: set = set()
    read_files: set[str] = set()
    units = [rel for rel in every if rel.endswith(".jsp")]
    for entry in units:
        files, problems = collect_unit(root, entry, texts, used_layout)
        read_files.update(files)
        findings.update(problems)
        findings.update(scan_unit(root, files, texts))
    for orphan in [rel for rel in every if rel not in read_files]:
        files, problems = collect_unit(root, orphan, texts, used_layout)
        read_files.update(files)
        findings.update(problems)
        findings.update(scan_unit(root, files, texts))
    for rel, target in sorted(LAYOUT_INCLUDES - used_layout):
        if rel in texts:
            findings.add((rel, 0, BROKEN, 'LAYOUT_INCLUDES lists an include of "%s" that is '
                          "no longer there; remove the entry" % target))
    return sorted(read_files), sorted(findings)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 on any finding (CI runs with this)")
    args = parser.parse_args()

    if not os.path.isdir(os.path.join(args.root, JSP_ROOT)):
        print("MISSING  %s" % JSP_ROOT, file=sys.stderr)
        return 1

    files, findings = scan(args.root)
    links: dict[str, list] = {}
    for rel, line, kind, message in findings:
        if kind == LINK:
            links.setdefault(rel, []).append((line, message))
    over = {rel: hits for rel, hits in links.items() if len(hits) > BACKLOG.get(rel, 0)}
    broken = [f for f in findings if f[2] == BROKEN]
    under = [(rel, n, len(links.get(rel, [])))
             for rel, n in sorted(BACKLOG.items()) if len(links.get(rel, [])) < n]

    if over or broken:
        shown = broken + [(rel, line, LINK, message) for rel, hits in over.items()
                          for line, message in hits]
        print("FAIL  %d help text problem(s) in %d template(s)"
              % (len(shown), len({f[0] for f in shown})))
        for rel, line, _kind, message in sorted(shown):
            allowed = BACKLOG.get(rel, 0)
            suffix = "  (backlog allows %d in this file)" % allowed if allowed else ""
            print("  %s: line %d  %s%s" % (rel, line, message, suffix))
        print()
        print("A field's help is announced with the field only when the field points at it.")
        print("Give the help an id, then point the field's input at it:")
        print('  <input type="text" name="fromPath" aria-describedby="fromPathHelpText">')
        print('  <p class="help-text" id="fromPathHelpText">...</p>')
        print("In a loop or a widget, build both from the same expression, such as")
        print('id="emailHelpText${widgetContext.uniqueId}". If the text isn\'t help for the field')
        print("before it, it doesn't need an id; check it really isn't.")
        return 1 if args.strict else 0

    if under:
        print("NOTE  backlog is now ahead of the recorded counts -- lower BACKLOG in %s"
              % os.path.basename(__file__))
        for rel, was, now in under:
            print("  %s: %d -> %d" % (rel, was, now))

    remaining = sum(len(h) for h in links.values())
    print("OK  every field's help text is linked to its field, in %d template(s) "
          "(%d in the backlog)" % (len(files), remaining))
    return 0


if __name__ == "__main__":
    sys.exit(main())
