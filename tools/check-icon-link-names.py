#!/usr/bin/env python3
"""Keep icon-only controls from shipping without an accessible name.

A link or button whose entire content is a Font Awesome glyph has no text for an
assistive technology to announce. Unless it carries ``aria-label``,
``aria-labelledby``, ``title``, or a screen-reader-only span, a screen reader
reads it as "link" and stops -- the user is told a control exists and never told
what it does. That fails WCAG 4.1.2 Name, Role, Value and 2.4.4 Link Purpose (In
Context), both Level A in WCAG 2.0, so both in scope for Section 508 as written.

This went unmeasured for a long time because the public site is clean. Tabbing
through the signed-out homepage reaches 57 controls, every one of them named. The
same page signed in with edit rights reaches 103, of which 35 are nameless -- all
of them the ``fa-code`` and ``fa-edit`` affordances the layout renderers emit
beside every container and widget. The defect is editor-facing, so no amount of
auditing the public pages would ever have surfaced it.

What counts as a finding
------------------------
An ``<a>`` or ``<button>`` whose content, once icon elements are removed, is
empty -- and which has no name from any of:

  * ``aria-label`` or ``aria-labelledby``
  * ``title``
  * text, including a ``show-for-sr`` / ``sr-only`` span, and including JSP tags
    such as ``<c:out>`` that emit text at render time
  * a nested ``<img alt="...">``

Icon elements are ``<i>``, ``<svg>`` and ``<span>``/``<em>`` carrying a ``fa``
class. Comments (``<%-- --%>``, ``<!-- -->``) and ``<script>`` bodies are blanked
first, preserving line numbers, so prose that merely shows markup in a help block
is not a finding -- ``admin/web-page-list.jsp`` and ``admin/blog-list.jsp`` both
explain the pencil-versus-designer icons that way.

``title`` deliberately counts as a name
---------------------------------------
33 controls are named only by ``title``. That satisfies 4.1.2, so they are not
findings here. They are still worth revisiting: a ``title`` tooltip appears on
hover and not on keyboard focus, so the name reaches a screen reader but not a
sighted keyboard user. Making that a finding would conflate a Level A failure
with a quality-of-implementation issue, so it is left alone on purpose.

BACKLOG works like ``check-inline-handlers.py``'s allowlist: a per-file count of
known, still-to-be-named controls. A count ABOVE the recorded number fails; a
count BELOW is reported as a note, not a failure, so a PR that improves matters
is never blocked and merge order is not load-bearing.

It is currently **empty** -- all 83 are named, so any finding at all is a
regression. Keep it that way if you can; an entry added here should come with a
plan to remove it.

This is a read-only reporter. It changes no files.

Exit status is 1 under --strict when a finding is reported.
"""
from __future__ import annotations

import argparse
import os
import re
import sys

JSP_ROOT = os.path.join("src", "main", "webapp", "WEB-INF", "jsp")

# Known, still-to-be-named controls, by path relative to JSP_ROOT. Empty: the layout and
# cms templates were named first, the admin console's own 54 followed, and the whole tree
# is clean. Anything added here later is debt with a deadline, not a permanent exemption --
# a count ABOVE the recorded number fails, a count BELOW is only a note, so a PR that
# improves matters is never blocked.
BACKLOG: dict[str, int] = {}

COMMENT_RE = re.compile(r"<%--.*?--%>|<!--.*?-->", re.S)
SCRIPT_RE = re.compile(r"<script\b.*?</script\s*>", re.S | re.I)
CONTROL_RE = re.compile(r"<(a|button)\b([^>]*)>(.*?)</\1\s*>", re.S | re.I)

# A JSP action tag carries its own ">", and these templates routinely put one INSIDE an
# attribute value -- layout-header-renderer.jspf builds its href with <c:out/> mid-string.
# Left alone, the "[^>]*" above ends the opening tag at that ">" and the control is silently
# skipped, which is a missed finding rather than a false one and so shows up as nothing at all.
# Neutralise the tags before parsing, distinguishing two kinds:
#   structural (c:if, c:forEach, jsp:*, closing tags) wrap content without emitting any, so they
#     blank to spaces and leave what they wrap visible;
#   output-producing (c:out, fmt:message, scriptlet expressions) DO emit text at render time, so
#     they collapse to a non-space sentinel that counts as an accessible name.
# Both preserve length, so reported line numbers stay true to the file.
JSP_TAG_RE = re.compile(r"<%=.*?%>|<%[^=@].*?%>|</?[a-zA-Z][\w-]*:[\w-]+\b.*?/?>", re.S)
STRUCTURAL = ("c:if", "c:choose", "c:when", "c:otherwise", "c:forEach", "c:forTokens",
              "c:set", "c:catch", "c:url", "c:param", "jsp:")
SENTINEL = "\x01"
ICON_RE = re.compile(
    r"<i\b[^>]*>.*?</i\s*>"
    r"|<svg\b[^>]*>.*?</svg\s*>"
    r"|<(?:span|em)\b[^>]*\bclass\s*=\s*[\"'][^\"']*\bfa[\w-]*\b[^\"']*[\"'][^>]*>\s*</(?:span|em)\s*>",
    re.S | re.I,
)
NAME_ATTRS = ("aria-label", "aria-labelledby", "title")


def _spaces(m: re.Match) -> str:
    return re.sub(r"[^\n]", " ", m.group(0))


def _neutralise_jsp(m: re.Match) -> str:
    """Blank a structural tag; collapse an output-producing one to a sentinel."""
    tag = m.group(0)
    name = re.match(r"</?([a-zA-Z][\w-]*:[\w-]+)", tag)
    structural = tag.startswith("</") or (
        name is not None and any(name.group(1).startswith(p) for p in STRUCTURAL))
    if structural:
        return _spaces(m)
    return re.sub(r"[^\n]", SENTINEL, tag)


def blank_out(text: str) -> str:
    """Neutralise comments, script bodies and JSP tags, preserving line numbers."""
    text = COMMENT_RE.sub(_spaces, text)
    text = SCRIPT_RE.sub(_spaces, text)
    return JSP_TAG_RE.sub(_neutralise_jsp, text)


def has_name(attrs: str, inner: str) -> bool:
    for attr in NAME_ATTRS:
        # The "not blank" character class has to exclude quotes as well as whitespace: a
        # plain \S matches the closing quote itself, so aria-label="" would run on into the
        # next attribute and read as a name.
        if re.search(r"\b%s\s*=\s*[\"'][^\"']*[^\s\"'][^\"']*[\"']" % re.escape(attr),
                     attrs, re.I):
            return True
    if re.search(r"<img\b[^>]*\balt\s*=\s*[\"'][^\"']*\S", inner, re.I):
        return True
    return False


def is_icon_only(inner: str) -> bool:
    """True when nothing but icons and whitespace sits inside the control."""
    remainder = ICON_RE.sub("", inner)
    remainder = re.sub(r"&nbsp;|&#160;", " ", remainder)
    return remainder.strip() == ""


def scan_text(text: str) -> list[int]:
    """Return the 1-based line numbers of nameless icon-only controls."""
    cleaned = blank_out(text)
    lines: list[int] = []
    for m in CONTROL_RE.finditer(cleaned):
        attrs, inner = m.group(2), m.group(3)
        if is_icon_only(inner) and not has_name(attrs, inner):
            lines.append(cleaned[: m.start()].count("\n") + 1)
    return lines


def scan_tree(root: str) -> dict[str, list[int]]:
    base = os.path.join(root, JSP_ROOT)
    found: dict[str, list[int]] = {}
    for dirpath, _dirs, files in os.walk(base):
        for name in sorted(files):
            if not name.endswith((".jsp", ".jspf")):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8", errors="replace") as handle:
                hits = scan_text(handle.read())
            if hits:
                found[os.path.relpath(path, base)] = hits
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 on any finding (CI runs with this)")
    args = parser.parse_args()

    base = os.path.join(args.root, JSP_ROOT)
    if not os.path.isdir(base):
        print("MISSING  %s" % JSP_ROOT, file=sys.stderr)
        return 1

    found = scan_tree(args.root)
    over = {rel: hits for rel, hits in found.items() if len(hits) > BACKLOG.get(rel, 0)}
    under = [(rel, n, len(found.get(rel, [])))
             for rel, n in sorted(BACKLOG.items()) if len(found.get(rel, [])) < n]

    if over:
        total = sum(len(h) for h in over.values())
        print("FAIL  %d icon-only control(s) with no accessible name, in %d file(s)"
              % (total, len(over)))
        for rel, hits in sorted(over.items()):
            allowed = BACKLOG.get(rel, 0)
            suffix = "  (backlog allows %d)" % allowed if allowed else ""
            print("  %s: line%s %s%s"
                  % (rel, "" if len(hits) == 1 else "s",
                     ", ".join(str(n) for n in hits), suffix))
        print()
        print("Give the control an aria-label naming what it does, and mark the glyph")
        print("decorative so it is not announced twice:")
        print('  <a class="..." aria-label="Edit page layout" href="...">')
        print('    <i class="fa-fw fa fa-code" aria-hidden="true"></i></a>')
        print("A screen-reader-only span works too. A title= also counts as a name, but it")
        print("never appears on keyboard focus, so prefer aria-label.")
        return 1 if args.strict else 0

    if under:
        print("NOTE  backlog is now ahead of the recorded counts -- lower BACKLOG in %s"
              % os.path.basename(__file__))
        for rel, was, now in under:
            print("  %s: %d -> %d" % (rel, was, now))

    remaining = sum(len(h) for h in found.values())
    print("OK  no unrecorded nameless icon-only controls (%d in the backlog)" % remaining)
    return 0


if __name__ == "__main__":
    sys.exit(main())
