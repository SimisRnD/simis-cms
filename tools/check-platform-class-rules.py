#!/usr/bin/env python3
"""Fail when a JSP emits a ``platform-*`` class that no first-party stylesheet styles.

A class with no matching rule is invisible to every other check in this repository.
The JSP compiles, the CSS parses, the token and contrast gates pass, the WAR is
complete, and the page renders wrong. That is not hypothetical: it happened twice on
2026-09-10, hours apart, in the same file.

    .platform-external-indicator          added 8123fe644, dropped d4bdddaee, restored #1973
    a.platform-blog-overview-thumbnail    added 9392d3257, dropped a6c750456, restored #1990

Both were lost the same way -- a ``Merge branch 'main' into <feature-branch>`` commit
resolved ``platform.css`` by taking main's side, discarding what the branch had added --
and both shipped before anyone noticed by eye. See issue #1991.

**What counts as styled.** A class is styled if its name appears anywhere in
``platform.css`` or ``platform-tokens.css``. That is deliberately loose: this gate is
looking for a rule that vanished, not auditing selector quality, and a substring test
cannot be fooled by the thing it is guarding against.

**Why an allowlist rather than a clean sweep.** 31 classes are emitted today with no
first-party rule, and most are legitimate: semantic hooks a site's own stylesheet is
expected to style, and hooks only JavaScript reads. There is no mechanical way to tell
an intentional hook from a rule someone deleted, so the honest form is a recorded list
of what is accepted now, with anything new failing. Same shape as the BACKLOG in
``check-icon-link-names.py``.

The list is checked in both directions. An entry that becomes styled, or stops being
emitted, is reported so the list shrinks as things are fixed instead of quietly
accumulating.

@author elizabeth houser
"""

import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

JSP_ROOT = os.path.join("src", "main", "webapp", "WEB-INF", "jsp")
STYLESHEETS = (
    os.path.join("src", "main", "webapp", "css", "platform.css"),
    os.path.join("src", "main", "webapp", "css", "platform-tokens.css"),
)

# Reusing the icon gate's neutraliser rather than writing a second one: it already
# solves the JSP-tag-inside-an-attribute problem (a <c:out/> carries its own '>', so a
# naive [^>]* ends the opening tag early) and preserves line numbers.
_ICON_GATE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                          "check-icon-link-names.py")

CLASS_RE = re.compile(r"""class\s*=\s*"([^"]*)"|class\s*=\s*'([^']*)'""", re.I)
PREFIX = "platform-"

# class -> why it is accepted with no first-party rule.
ALLOWED = {
    # Read by JavaScript, never styled here.
    "platform-activity-icon": "JS hook",
    "platform-color-scheme-status": "JS hook",
    "platform-video-play-button": "JS hook",
    # Semantic hooks a site's own stylesheet is expected to style. Recorded as accepted
    # on 2026-09-10; not individually verified as intentional, only as pre-existing.
    "platform-admin-menu": "site-styled hook",
    "platform-blog-city": "site-styled hook",
    "platform-blog-date": "site-styled hook",
    "platform-blog-panel": "site-styled hook",
    "platform-blog-panel-byline": "site-styled hook",
    "platform-blog-panel-item": "site-styled hook",
    "platform-blog-panel-summary": "site-styled hook",
    "platform-blog-panel-tags": "site-styled hook",
    "platform-blog-panel-title": "site-styled hook",
    "platform-blog-panel-view-all": "site-styled hook",
    "platform-calendar-event-date": "site-styled hook",
    "platform-calendar-event-location": "site-styled hook",
    "platform-calendar-event-organizer": "site-styled hook",
    "platform-calendar-event-speaker": "site-styled hook",
    "platform-calendar-list-container": "site-styled hook",
    "platform-calendar-title": "site-styled hook",
    "platform-content-review-form": "site-styled hook",
    "platform-content-search-result": "site-styled hook",
    "platform-ecommerce-card": "site-styled hook",
    "platform-faq-answer": "site-styled hook",
    "platform-faq-container": "site-styled hook",
    "platform-faq-item": "site-styled hook",
    "platform-faq-question": "site-styled hook",
    "platform-folder-list-container": "site-styled hook",
    "platform-folder-name": "site-styled hook",
    "platform-preview-draft-banner": "site-styled hook",
    "platform-skip-link": "site-styled hook",
    "platform-video-widget": "site-styled hook",
    "platform-video-widget-consent-placeholder": "site-styled hook",
    "platform-video-widget-placeholder": "site-styled hook",
    "platform-weather": "site-styled hook",
}


def _load_blank_out():
    import importlib.util
    spec = importlib.util.spec_from_file_location("_icon_gate", _ICON_GATE)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.blank_out, module.SENTINEL


def emitted_classes(root):
    """Map platform-* class -> sorted list of "file:line" where it is emitted."""
    blank_out, sentinel = _load_blank_out()
    base = os.path.join(root, JSP_ROOT)
    found = {}
    for dirpath, _dirs, files in os.walk(base):
        for name in sorted(files):
            if not name.endswith((".jsp", ".jspf")):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, root)
            with open(path, encoding="utf-8", errors="replace") as handle:
                text = blank_out(handle.read())
            for match in CLASS_RE.finditer(text):
                value = match.group(1) or match.group(2) or ""
                line = text[:match.start()].count("\n") + 1
                for token in value.split():
                    # A token carrying the sentinel was built from a JSP expression, so
                    # its real name is not knowable here. Skipping is the only honest
                    # option: stripping the sentinel would invent a class name that is
                    # never emitted and fail on it.
                    if sentinel in token or not token.startswith(PREFIX):
                        continue
                    found.setdefault(token, []).append("%s:%d" % (rel, line))
    return found


def stylesheet_text(root):
    text = ""
    for rel in STYLESHEETS:
        path = os.path.join(root, rel)
        if not os.path.isfile(path):
            return None, rel
        with open(path, encoding="utf-8", errors="replace") as handle:
            text += handle.read()
    return text, None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--strict", action="store_true",
                        help="exit 1 on any finding (CI runs with this)")
    args = parser.parse_args(argv)

    if not os.path.isdir(os.path.join(args.root, JSP_ROOT)):
        print("MISSING  %s" % JSP_ROOT, file=sys.stderr)
        return 1
    css, missing = stylesheet_text(args.root)
    if css is None:
        print("MISSING  %s" % missing, file=sys.stderr)
        return 1

    found = emitted_classes(args.root)
    unstyled = {name: where for name, where in found.items() if name not in css}
    new = {name: where for name, where in unstyled.items() if name not in ALLOWED}
    now_styled = sorted(name for name in ALLOWED if name in css)
    gone = sorted(name for name in ALLOWED if name not in found)

    if new:
        print("FAIL  %d platform-* class(es) emitted with no rule in platform.css or "
              "platform-tokens.css" % len(new))
        for name in sorted(new):
            print("  %s" % name)
            for where in new[name][:4]:
                print("      %s" % where)
        print()
        print("Either the rule was lost -- check whether a merge into this branch took")
        print("main's side of platform.css and discarded it (issue #1991) -- or the class")
        print("is a deliberate hook for a site stylesheet or for JavaScript, in which case")
        print("add it to ALLOWED in %s with the reason." % os.path.basename(__file__))
        return 1 if args.strict else 0

    if now_styled or gone:
        print("NOTE  the allowlist is out of date -- edit ALLOWED in %s"
              % os.path.basename(__file__))
        for name in now_styled:
            print("  now styled, drop it:      %s" % name)
        for name in gone:
            print("  no longer emitted, drop:  %s" % name)

    print("OK  %d platform-* classes emitted, %d styled, %d accepted as unstyled"
          % (len(found), len(found) - len(unstyled), len(unstyled)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
