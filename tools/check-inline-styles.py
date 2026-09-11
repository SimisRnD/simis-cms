#!/usr/bin/env python3
"""Fail when a JSP renders inline style the page's CSP would refuse (issue #1999).

The enforced Content-Security-Policy carries ``style-src 'self' 'unsafe-inline'``, which
SecurityScorecard reports as "Content Security Policy Contains 'unsafe-*' Directive". It
cannot be removed while the page renders inline style attributes, and it cannot be worked
around: a CSP nonce applies to a ``<style>`` element, never to a ``style=`` attribute, and
the only other way to permit attributes is ``'unsafe-hashes'`` -- itself an unsafe-* keyword.
So clearing the finding means the page emits none.

The same directive governs ``<style>`` elements, which a nonce *can* authorize: once
``'unsafe-inline'`` goes, a ``<style>`` without ``nonce="${cspNonce}"`` stops applying. Nothing shows
that today -- ``'unsafe-inline'`` still admits it -- so a missing nonce fails here as well.

351 existed across 122 templates when this was added, recorded per file as a backlog that could
only shrink. It reached zero on 2026-09-11, so the backlog is gone and the check is absolute: any
inline style attribute in a template fails. Same shape as ``check-icon-link-names.py``, whose JSP
neutraliser this reuses -- so commented-out markup is not counted, and line numbers stay true.

When replacing an attribute, keep its precedence. An inline style outranks every selector, so a
plain class carrying the same declaration can lose to an existing rule it used to beat -- e.g.
``style="margin-bottom:0"`` beats ``.callout p { margin-bottom: 1rem }``, and ``.u-mb-0`` does
not. Utilities that replace inline styles therefore declare ``!important`` -- except for a property
a script writes, which stays a normal declaration so the script still wins (see the u-* comment in
platform.css). A value computed at render time goes through ``css:register``, which serves it from
the page's one nonced ``<style>`` element (``PageStyleRules``).

What this does not cover
------------------------
Templates are one of three sources, and CSP is page-wide, so this reaching zero is necessary
but not sufficient to drop ``'unsafe-inline'``:

  * stored content -- the HTML sanitizer (``HtmlCommand``) has dropped ``style`` since #2001, but
    content records and blog posts are cleaned when they are saved, so any saved before that
    still carry it until they are re-saved (page-XML HTML is re-cleaned on every render);
  * script -- markup built as a string (``innerHTML``, ``$('<div style=...>')``) or
    ``setAttribute('style', ...)`` is refused the same way. ``<script>`` bodies are blanked
    before scanning, so none of it is counted here; a report-only trial of the stricter
    policy is what finds it. (``el.style.x = ...`` is CSSOM, not an attribute, and is allowed.)
    A ``<style>`` element a script creates needs its nonce set in script (``el.nonce``) -- also
    beyond this scan.

Email templates (``WEB-INF/email-templates``) are out of scope on purpose: mail clients strip
``<style>`` and need inline styles, and no email is rendered under the page's CSP.

Exit codes: 0 = nothing found (or report-only), 1 = a finding under --strict, 2 = the template
tree is missing or too small to be the real one. See issue #1999.

@author elizabeth houser
"""

import argparse
import importlib.util
import os
import re
import sys

JSP_ROOT = os.path.join("src", "main", "webapp", "WEB-INF", "jsp")
# Fewer than this means the path or the walk is wrong, not that the tree is clean.
MIN_FILES = 50
STYLE_ATTR_RE = re.compile(r"""\sstyle\s*=\s*["']""", re.I)
# A <style> element's start tag -- not <styled-...> -- and the nonce it has to carry.
STYLE_ELEMENT_RE = re.compile(r"<style(?=[\s>/])([^>]*)>", re.I)
NONCE_RE = re.compile(r"""\bnonce\s*=\s*["']\$\{cspNonce\}["']""")
# A <style> element's body is CSS, not markup: a comment in it can mention "<style>" or style="".
STYLE_BODY_RE = re.compile(r"(<style(?=[\s>/])[^>]*>)(.*?)(</style\s*>)", re.I | re.S)
_ICON_GATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-icon-link-names.py")


def _blank_out():
    spec = importlib.util.spec_from_file_location("_icon_gate", _ICON_GATE)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.blank_out


def _blank_style_body(m):
    """Keep a <style> element's tags and blank its CSS, preserving line numbers."""
    return m.group(1) + re.sub(r"[^\n]", " ", m.group(2)) + m.group(3)


def scan_tree(root):
    """Return (files scanned, {path: [lines with a style attribute]}, {path: [lines with a
    <style> element missing the nonce]}), paths relative to WEB-INF/jsp."""
    blank_out = _blank_out()
    base = os.path.join(root, JSP_ROOT)
    found = {}
    unnonced = {}
    scanned = 0
    for dirpath, _dirs, files in os.walk(base):
        for name in sorted(files):
            if not name.endswith((".jsp", ".jspf")):
                continue
            scanned += 1
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8", errors="replace") as handle:
                text = STYLE_BODY_RE.sub(_blank_style_body, blank_out(handle.read()))
            rel = os.path.relpath(path, base).replace(os.sep, "/")
            lines = [text[:m.start()].count("\n") + 1 for m in STYLE_ATTR_RE.finditer(text)]
            if lines:
                found[rel] = lines
            bare = [text[:m.start()].count("\n") + 1 for m in STYLE_ELEMENT_RE.finditer(text)
                    if not NONCE_RE.search(m.group(1))]
            if bare:
                unnonced[rel] = bare
    return scanned, found, unnonced


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--strict", action="store_true", default=os.environ.get("STRICT") == "1",
                        help="exit 1 on any finding (CI runs with this)")
    args = parser.parse_args(argv)

    if not os.path.isdir(os.path.join(args.root, JSP_ROOT)):
        print("MISSING  %s (run from the repository root)" % JSP_ROOT, file=sys.stderr)
        return 2

    scanned, found, unnonced = scan_tree(args.root)
    if scanned < MIN_FILES:
        print("MISSING  scanned only %d templates under %s -- check the path" % (scanned, JSP_ROOT),
              file=sys.stderr)
        return 2
    if found:
        total = sum(len(hits) for hits in found.values())
        print("FAIL  %d inline style attribute(s), in %d file(s)" % (total, len(found)))
        for rel, hits in sorted(found.items()):
            print("  jsp/%s: line%s %s" % (rel, "" if len(hits) == 1 else "s",
                                       ", ".join(str(n) for n in hits)))
        print()
        print("Move the declaration into a stylesheet. If it replaces an inline style, keep its")
        print("precedence with !important -- a plain class can lose to a rule the attribute beat --")
        print("unless a script writes the same property. A value computed at render time goes through")
        print("css:register, which serves it from the page's nonced <style>. See issue #1999.")
    if unnonced:
        if found:
            print()
        total = sum(len(hits) for hits in unnonced.values())
        print('FAIL  %d <style> element(s) without nonce="${cspNonce}", in %d file(s)'
              % (total, len(unnonced)))
        for rel, hits in sorted(unnonced.items()):
            print("  jsp/%s: line%s %s" % (rel, "" if len(hits) == 1 else "s",
                                       ", ".join(str(n) for n in hits)))
        print()
        print('Give it the page\'s nonce: <style nonce="${cspNonce}">. Without it the element applies')
        print("only while style-src keeps 'unsafe-inline'. See issue #1999.")
    if found or unnonced:
        return 1 if args.strict else 0

    print("OK  no inline style attributes, and every <style> element carries the nonce, in %d templates"
          % scanned)
    return 0


if __name__ == "__main__":
    sys.exit(main())
