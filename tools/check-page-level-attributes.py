#!/usr/bin/env python3
"""Report page-level request attributes PageServlet publishes that the per-widget reset wipes.

Background
----------
``PageServlet.service()`` computes a handful of values once per request and publishes
them with ``request.setAttribute(...)`` *before* it walks the page's sections, columns
and widgets. ``WebContainerCommand.processWidgets()`` then clears every request
attribute between widgets, so one widget's leftovers cannot bleed into the next one's
render:

    Enumeration<?> attributeNames = request.getAttributeNames();
    while (attributeNames.hasMoreElements()) {
      String name = (String) attributeNames.nextElement();
      if (!isPreservedAcrossWidgetReset(name)) {
        request.removeAttribute(name);
      }
    }

``isPreservedAcrossWidgetReset`` exempts three name prefixes plus an explicit
``PAGE_LEVEL_ATTRIBUTE_NAMES`` set. A page-level attribute whose name is in neither is
destroyed by the *first* widget on the page, long before main.jsp's EL -- or any later
widget -- reads it.

Nothing reports this. Removing a request attribute is legal, and EL that resolves to
nothing renders the empty string rather than raising. The page still returns 200 with a
correct-looking body, and the value is simply gone on every page that has at least one
widget -- which is every real page.

It has shipped broken twice:

  * ``cspNonce`` (issue #944): PageServlet set it at the top of the request and sent it
    in the Content-Security-Policy header, but the first widget's reset wiped the
    attribute, so every ``nonce="${cspNonce}"`` in the page rendered as ``nonce=""`` and
    matched no nonce in the header.
  * ``elearningPropertyMap`` (issue #1763) would have done the same to an admin-menu
    gate -- a Settings row hidden behind ``${elearningPropertyMap['elearning.enabled']}``
    would have read empty on every page and stayed hidden even with the module on. See
    ``WebContainerCommandTest.elearningPropertyMapSurvivesThePerWidgetReset``.

Both were found by hand. This check finds them in about a second.

What it does
------------
Parses ``PageServlet.java`` for every ``request.setAttribute("<name>", ...)`` that runs
before the first ``WebContainerCommand.processWidgets(...)`` call, resolving identifier
names (e.g. ``MASTER_WEB_PAGE``) against ``RequestConstants.java``. Each of those names
must either start with one of the prefixes ``isPreservedAcrossWidgetReset`` honours --
read out of ``WebContainerCommand.java`` rather than hardcoded here, so the two move
together -- or appear in ``PAGE_LEVEL_ATTRIBUTE_NAMES``.

There is deliberately no allowlist. An attribute published before the walk and not
preserved across it is dead on every page with a widget on it, so there is no correct
instance to record an exception for: the fix is always to add the name to the set.

Comments and string literals are handled by a Java-aware scanner, so prose in a comment
that mentions ``request.setAttribute(...)`` is not a finding.

Scope: attributes set directly on ``request`` in ``PageServlet.service()``. Session
attributes (``request.getSession().setAttribute``) are untouched by the reset, and an
attribute set from inside a helper method is out of reach of a text scan.

Modes
-----
Report-only by default. ``--strict`` (or ``STRICT=1``) exits 1 on any unpreserved name.
CI runs it with ``--strict``.

Exit codes: 0 = every published name is preserved (or report-only), 1 = a name is
unpreserved under --strict, 2 = bad usage, or the source files/anchors this check reads
are missing or unrecognisable (a gate that cannot find what it checks must fail loudly,
not pass quietly).

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import argparse
import os
import re
import sys

CONTROLLER = "src/main/java/com/simisinc/platform/presentation/controller"
PAGE_SERVLET = CONTROLLER + "/PageServlet.java"
WEB_CONTAINER = CONTROLLER + "/WebContainerCommand.java"
REQUEST_CONSTANTS = CONTROLLER + "/RequestConstants.java"

# The call that starts the section/column/widget walk. Everything published above the
# first one of these is page-level and has to survive the reset inside it.
WALK_CALL = "WebContainerCommand.processWidgets("

SET_NAME = "PAGE_LEVEL_ATTRIBUTE_NAMES"
PRESERVE_METHOD = "isPreservedAcrossWidgetReset"

# request.setAttribute(<name>, ...) -- <name> is either a "literal" or an IDENTIFIER.
SET_ATTRIBUTE = re.compile(
    r'(?<![\w.])request\s*\.\s*setAttribute\s*\(\s*(?:"([^"\\]*)"|([A-Za-z_$][\w$]*))\s*,')

STRING_LITERAL = re.compile(r'"([^"\\]*)"')
CONSTANT_DECL = re.compile(
    r'\bstatic\s+final\s+String\s+([A-Z][A-Z0-9_]*)\s*=\s*"([^"\\]*)"\s*;')
STARTS_WITH = re.compile(r'\bstartsWith\s*\(\s*"([^"\\]*)"\s*\)')
SET_DECL = re.compile(r'\bSet\s*<\s*String\s*>\s+' + SET_NAME + r'\b')


def fail(message: str) -> "NoReturn":
    """Exit 2: this check could not find what it measures.

    Distinct from exit 1 (a real finding) on purpose -- a gate whose anchors have been
    renamed out from under it must be visibly broken rather than quietly passing.
    """
    print("error: " + message, file=sys.stderr)
    sys.exit(2)


def strip_java_comments(source: str) -> str:
    """Blank out // and /* */ comments, preserving offsets and line numbers.

    String and character literals are tracked so that a // inside "http://..." is not
    mistaken for the start of a comment, and a quote inside a comment does not open a
    string.
    """
    out = list(source)
    i, n = 0, len(source)
    while i < n:
        c = source[i]
        if c == '"' or c == "'":
            quote = c
            i += 1
            while i < n:
                if source[i] == "\\":
                    i += 2
                    continue
                if source[i] == quote or source[i] == "\n":
                    i += 1
                    break
                i += 1
            continue
        if c == "/" and i + 1 < n and source[i + 1] == "/":
            while i < n and source[i] != "\n":
                out[i] = " "
                i += 1
            continue
        if c == "/" and i + 1 < n and source[i + 1] == "*":
            while i < n and not (source[i] == "*" and i + 1 < n and source[i + 1] == "/"):
                if source[i] != "\n":
                    out[i] = " "
                i += 1
            for _ in range(2):
                if i < n:
                    out[i] = " "
                    i += 1
            continue
        i += 1
    return "".join(out)


def read(root: str, rel: str) -> str:
    path = os.path.join(root, rel)
    if not os.path.isfile(path):
        fail("%s not found (run from the repository root)" % rel)
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def line_of(source: str, offset: int) -> int:
    return source.count("\n", 0, offset) + 1


def constant_values(source: str) -> dict[str, str]:
    """{CONSTANT_NAME: "attributeName"} from a constants class."""
    return {name: value for name, value in CONSTANT_DECL.findall(source)}


def declared_names(source: str) -> set[str]:
    """The string literals inside the PAGE_LEVEL_ATTRIBUTE_NAMES declaration."""
    decl = SET_DECL.search(source)
    if not decl:
        fail("the %s declaration was not found in %s" % (SET_NAME, WEB_CONTAINER))
    end = source.find(";", decl.start())
    if end < 0:
        fail("%s declaration is not terminated in %s" % (SET_NAME, WEB_CONTAINER))
    return set(STRING_LITERAL.findall(source[decl.start():end]))


def preserved_prefixes(source: str) -> tuple[str, ...]:
    """The name.startsWith("...") prefixes isPreservedAcrossWidgetReset honours."""
    start = source.find(PRESERVE_METHOD + "(String")
    if start < 0:
        fail("%s(String ...) not found in %s" % (PRESERVE_METHOD, WEB_CONTAINER))
    end = source.find("}", start)
    if end < 0:
        fail("%s body is not terminated in %s" % (PRESERVE_METHOD, WEB_CONTAINER))
    prefixes = tuple(STARTS_WITH.findall(source[start:end]))
    if not prefixes:
        fail("%s declares no startsWith(...) prefixes in %s -- this check reads them from "
             "the source and cannot verify the exemption without them"
             % (PRESERVE_METHOD, WEB_CONTAINER))
    return prefixes


def published_before_walk(source: str, constants: dict[str, str]):
    """[(line, name)] for each request.setAttribute above the first widget walk.

    An identifier that resolves to no known constant is yielded with a None name so the
    caller can report it rather than silently skip it.
    """
    boundary = source.find(WALK_CALL)
    if boundary < 0:
        fail("%s not found in %s -- this check cannot locate the widget walk it measures "
             "against" % (WALK_CALL, PAGE_SERVLET))
    found = []
    for match in SET_ATTRIBUTE.finditer(source, 0, boundary):
        literal, identifier = match.group(1), match.group(2)
        name = literal if literal is not None else constants.get(identifier)
        found.append((line_of(source, match.start()), name, identifier))
    return found


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("root", nargs="?", default=".")
    ap.add_argument("--strict", action="store_true",
                    default=os.environ.get("STRICT") == "1")
    args = ap.parse_args()

    page_servlet = strip_java_comments(read(args.root, PAGE_SERVLET))
    web_container = strip_java_comments(read(args.root, WEB_CONTAINER))
    constants = constant_values(strip_java_comments(read(args.root, REQUEST_CONSTANTS)))

    exempt = declared_names(web_container)
    prefixes = preserved_prefixes(web_container)
    published = published_before_walk(page_servlet, constants)

    missing = []      # published before the walk, not preserved -- the bug
    unresolved = []   # an identifier whose value this check could not determine
    by_prefix = []
    ok = []
    for line, name, identifier in published:
        if name is None:
            unresolved.append((line, identifier))
        elif name.startswith(prefixes):
            by_prefix.append((line, name))
        elif name in exempt:
            ok.append((line, name))
        else:
            missing.append((line, name))

    print("Page-level request attribute check")
    print("=" * 72)
    print()
    print("%s publishes %d attribute(s) before %s"
          % (os.path.basename(PAGE_SERVLET), len(published), WALK_CALL))
    print("%s exempts %d name(s) plus the prefixes %s"
          % (SET_NAME, len(exempt), ", ".join(repr(p) for p in prefixes)))
    print()

    if missing:
        print("UNPRESERVED (%d) -- wiped by the first widget's reset:" % len(missing))
        for line, name in missing:
            print("  %s:%d  %s" % (PAGE_SERVLET, line, name))
        print()
    if unresolved:
        print("UNRESOLVED (%d) -- name is an identifier with no constant in %s:"
              % (len(unresolved), os.path.basename(REQUEST_CONSTANTS)))
        for line, identifier in unresolved:
            print("  %s:%d  %s" % (PAGE_SERVLET, line, identifier))
        print()

    if ok:
        print("PRESERVED by %s (%d):" % (SET_NAME, len(ok)))
        for line, name in sorted(ok):
            print("  %-40s %s:%d" % (name, os.path.basename(PAGE_SERVLET), line))
        print()
    if by_prefix:
        print("PRESERVED by name prefix (%d):" % len(by_prefix))
        for line, name in sorted(by_prefix):
            print("  %-40s %s:%d" % (name, os.path.basename(PAGE_SERVLET), line))
        print()

    stale = sorted(name for name in exempt
                   if name not in {n for _, n, _ in published if n})
    if stale:
        print("NOTE: %d name(s) in %s are no longer published before the walk "
              "(harmless, but the set has drifted): %s"
              % (len(stale), SET_NAME, ", ".join(stale)))
        print()

    print("Summary: %d unpreserved, %d unresolved, %d preserved (%d by set, %d by prefix)."
          % (len(missing), len(unresolved), len(ok) + len(by_prefix), len(ok), len(by_prefix)))

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write("## Page-level request attributes\n\n")
            if missing or unresolved:
                fh.write("**%d attribute(s) published before the widget walk are not "
                         "preserved across it.**\n\n" % (len(missing) + len(unresolved)))
                fh.write("| Attribute | Published at |\n|---|---|\n")
                for line, name in missing:
                    fh.write("| `%s` | `%s:%d` |\n" % (name, PAGE_SERVLET, line))
                for line, identifier in unresolved:
                    fh.write("| `%s` (unresolved) | `%s:%d` |\n" % (identifier, PAGE_SERVLET, line))
            else:
                fh.write("All %d page-level attribute(s) survive the per-widget reset.\n"
                         % len(published))

    if args.strict and (missing or unresolved):
        print()
        print("FAIL: a page-level request attribute does not survive the per-widget reset.")
        print("The first widget on the page removes it, so main.jsp's EL and every later")
        print("widget read it as empty -- with no error anywhere (issue #944).")
        print("Add the name to %s in %s." % (SET_NAME, WEB_CONTAINER))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
