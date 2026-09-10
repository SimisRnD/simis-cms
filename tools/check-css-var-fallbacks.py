#!/usr/bin/env python3
"""Every ``var(--sc-token, fallback)`` fallback must still equal the token's value.

Background
----------
A routed declaration writes the same value down twice::

    color: var(--sc-text-muted, #71767d);

Once as the token reference, once as the literal that applies if
``platform-tokens.css`` fails to load. Only the first is maintained. Repaint the
token and the literal beside it silently becomes a value the product no longer
uses -- with nothing tying the two together, and nothing rendering it, so no
amount of looking at the running product finds it.

That is exactly what happened. ``b940f342`` (#206) established the token layer;
``8da06161`` repainted it for Black Pearl and left four fallbacks in
``.platform-image-widget-placeholder`` on the old values -- ``--sc-surface-sunken``
``#f4f6f8`` (then ``#f4f5f7``), ``--sc-border`` ``#d7dce2`` (``#d9dce1``),
``--sc-text-muted`` ``#5b6470`` (``#71767d``) and ``--sc-radius-md`` ``6px``
(``4px``). The tell was that the same file used two of those tokens elsewhere
with the *current* value: platform.css disagreed with itself.

This is the failure mode of #1489 in a second location: a value written in one
place while it lives in another, with no link between them. #1489 was a comment
drifting from the declaration it described; this is a fallback drifting from the
token it backs. ``check-token-contrast.py`` closed the comment case for
``platform-tokens.css``. This closes the fallback case for every stylesheet that
consumes it.

Not a rendering bug, and this check should not be sold as one. While
``platform-tokens.css`` loads, the fallback never fires -- it is the documented
degradation path, dead until the day it is not. Stale values are latent, and the
point of a gate is to stop them accumulating unseen.

Why a separate tool
-------------------
``check-css-token-adoption.py`` is a ratchet: a per-file count compared to a
committed baseline, with ``--write`` to re-baseline, deliberately permitting a
count to fall. This is an exact-match invariant with nothing to ratchet and no
baseline to write, and folding it in would give one tool two incompatible
contracts and an ambiguous ``--write``. It does share that tool's premise --
fallbacks are worth keeping, so they are worth keeping *correct*.

``check-token-contrast.py`` evaluates colour values *inside*
``platform-tokens.css`` and needs a contrast implementation to do it. This
compares text *between* that file and its consumers, and needs no colour maths
at all -- ``6px`` vs ``4px`` is as much a finding here as one hex vs another.

What it checks
--------------
Light mode only, and by design. A fallback is a single literal; it cannot track a
token that repaints per theme, and the light ``:root`` is what a site gets when
nothing has opted in. Token-to-token indirection is resolved to its terminal
value, so ``--sc-fnd-black: var(--sc-fnd-ink)`` compares against ``#0a0a0a``.
Hex is normalised to six digits, so ``#fff`` matches ``#ffffff``.

Two things are legitimately not stale drift:

``SITE_THEME_JSPS`` -- tokens like ``--sc-button-primary-background-color`` are
emitted inline per request from the site's own theme settings and have no static
value to compare against. Their names are read from the JSPs that emit them
rather than hardcoded here, so adding a theme property keeps working and a
*typo* in a stylesheet still fails: an ``--sc-*`` token that is neither defined
in the token layer nor emitted by a JSP is a reference to nothing.

``ALLOWLIST`` -- a fallback that is a deliberate approximation rather than a copy:
a legible keyword standing in for a cubic-bezier, a translucent hairline instead
of a solid border, a softer shadow. Each entry was checked with ``git log -S``
against the token's history: none was ever the token's value, so none drifted
from anything. That is the bar for adding one -- if a fallback *was* the value
and the token moved, it is stale, and the fix is to update it.

``platform-tokens.css`` is excluded as the source of truth.
``foundation.tokens.min.css`` is excluded as generated -- its generator writes
each fallback *from* the token value, so it cannot drift by construction, and
its 285 fallbacks were all fresh when this was written; a hand edit to it is
already caught byte-for-byte by ``route-foundation-tokens.py --check``.
"""

import argparse
import re
import sys
from pathlib import Path

CSS_DIR = "src/main/webapp/css"
TOKENS = "platform-tokens.css"
DARK_BLOCK = ':root[data-theme="dark"]'

SITE_THEME_JSPS = (
    "src/main/webapp/WEB-INF/jsp/main.jsp",
)

# (token, fallback) pairs that are deliberate approximations, not stale copies.
ALLOWLIST = {
    ("--sc-motion-ease", "ease"):
        "a legible keyword standing in for the cubic-bezier",
    ("--sc-border", "rgba(0, 0, 0, 0.07)"):
        "a translucent hairline that suits an unknown backdrop better than the solid token",
    ("--sc-shadow-lg", "0 2px 10px rgba(0, 0, 0, 0.10)"):
        "a neutral-black shadow standing in for the token's tinted one",
}

COMMENT = re.compile(r"/\*.*?\*/", re.S)
# The final declaration in a block may legally omit its semicolon, and that is
# precisely the one a naive ";"-terminated pattern would drop.
DECL = re.compile(r"(--sc-[a-z0-9-]+)\s*:\s*([^;{}]+?)\s*(?=[;}])")
INDIRECT = re.compile(r"var\(\s*(--sc-[a-z0-9-]+)\s*\)\s*\Z")
OPEN = re.compile(r"var\(\s*(--sc-[a-z0-9-]+)\s*,")
HEX = re.compile(r"\A#[0-9a-fA-F]{3,8}\Z")


def uses(line: str):
    """(token, fallback) for each var() in a line, tracking nested parens.

    A fallback is arbitrary CSS and routinely contains its own parens --
    ``rgba(...)``, a cubic-bezier, a whole box-shadow. Matching to the first
    ``)`` would truncate those into nonsense and report drift that is not there,
    so scan to the paren that actually closes the ``var(``.
    """
    for m in OPEN.finditer(line):
        depth, i = 1, m.end()
        while i < len(line) and depth:
            depth += (line[i] == "(") - (line[i] == ")")
            i += 1
        if depth:
            continue  # the var() is split across lines; nothing to compare here
        yield m.group(1), line[m.end():i - 1].strip()


def light_tokens(text: str) -> dict[str, str]:
    """Token declarations from the light :root blocks, comments stripped.

    Everything before the dark block: the light values are split across more
    than one :root, and taking only the first would miss half of them.
    """
    text = COMMENT.sub("", text)
    cut = text.find(DARK_BLOCK)
    light = text if cut == -1 else text[:cut]
    return {m.group(1): m.group(2).strip() for m in DECL.finditer(light)}


def resolve(name: str, defs: dict[str, str]) -> str | None:
    """A token's terminal light-mode value, following var() indirection."""
    seen = set()
    while name in defs and name not in seen:
        seen.add(name)
        value = defs[name]
        hop = INDIRECT.match(value)
        if not hop:
            return value
        name = hop.group(1)
    return None


def site_theme_tokens(root: Path) -> set[str]:
    """Tokens emitted inline per request from the site's theme settings."""
    names = set()
    for rel in SITE_THEME_JSPS:
        p = root / rel
        if p.is_file():
            names.update(re.findall(r"(--sc-[a-z0-9-]+)\s*:", p.read_text(encoding="utf-8")))
    return names


def normalise(value: str) -> str:
    """Compare hex case- and length-insensitively; everything else verbatim."""
    if HEX.match(value):
        v = value.lower()
        return "#" + "".join(c * 2 for c in v[1:]) if len(v) == 4 else v
    return value


def consumers(root: Path) -> list[Path]:
    d = root / CSS_DIR
    return sorted(p for p in d.glob("platform*.css") if p.name != TOKENS) if d.is_dir() else []


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("root", nargs="?", default=".")
    args = ap.parse_args()
    root = Path(args.root)

    tokens_file = root / CSS_DIR / TOKENS
    if not tokens_file.is_file():
        print(f"MISSING  {CSS_DIR}/{TOKENS} -- nothing to check fallbacks against", file=sys.stderr)
        return 1

    defs = light_tokens(tokens_file.read_text(encoding="utf-8"))
    runtime = site_theme_tokens(root)
    stale, undefined = [], []
    checked = allowed = skipped = unparsed = 0

    for path in consumers(root):
        rel = path.relative_to(root).as_posix()
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if "var(" not in line:
                continue
            for name, fallback in uses(line):
                if "var(" in fallback:
                    unparsed += 1  # a fallback that is itself a token reference
                    continue
                if (name, fallback) in ALLOWLIST:
                    allowed += 1
                    continue
                value = resolve(name, defs)
                if value is None:
                    if name not in runtime:
                        undefined.append((rel, lineno, name))
                    else:
                        skipped += 1
                    continue
                if normalise(value) != normalise(fallback):
                    stale.append((rel, lineno, name, fallback, value))
                else:
                    checked += 1

    for rel, lineno, name, fallback, value in stale:
        print(f"STALE      {rel}:{lineno}  var({name}, {fallback}) -- the token is {value}",
              file=sys.stderr)
    for rel, lineno, name in undefined:
        print(f"UNDEFINED  {rel}:{lineno}  {name} is not a token and no JSP emits it",
              file=sys.stderr)

    if stale or undefined:
        if stale:
            print("\nA fallback is the value used if the token stylesheet fails to load. Point it\n"
                  f"at the token's current light-mode value in {CSS_DIR}/{TOKENS}, or add a\n"
                  "reason to ALLOWLIST if the difference is a deliberate approximation.",
                  file=sys.stderr)
        if undefined:
            print("\nAn --sc-* token that is neither declared nor emitted by a JSP resolves to\n"
                  "nothing: the fallback is all that ever renders. Check the name for a typo.",
                  file=sys.stderr)
        return 1

    note = f", {allowed} allowlisted, {skipped} site-theme"
    if unparsed:
        note += f", {unparsed} not compared"
    print(f"OK  {checked} var() fallbacks match their light-mode token{note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
