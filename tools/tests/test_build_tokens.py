"""build-tokens.py: a theme is a handful of hue seeds, not 89 hand-picked colours.

These tests also carry the dark/auto parity guarantee that check-token-contrast.py
used to enforce. The failure mode is the same one that shipped twice before (issues
1492 and 1503): a value corrected in ``:root[data-theme="dark"]`` but not in the
duplicate ``:root[data-theme="auto"]``, leaving every ``theme.ui.mode=auto`` site on
the old value while both blocks look individually correct. It is now unrepresentable
-- both blocks are emitted from one table -- and these tests hold that line.
"""

import re
import shutil

import pytest
from conftest import TOOLS_DIR, run_tool, write

TOOL = "build-tokens.py"
CSS = "src/main/webapp/css/platform-tokens.css"

REPO_ROOT = TOOLS_DIR.parent
REAL_CSS = (REPO_ROOT / CSS).read_text(encoding="utf-8")


@pytest.fixture
def tokens(repo):
    """A repo tree holding the real stylesheet and the tool's OKLCH helper."""
    write(repo, CSS, REAL_CSS)
    dest = repo / "tools"
    dest.mkdir(parents=True, exist_ok=True)
    shutil.copy(TOOLS_DIR / "_oklch.py", dest / "_oklch.py")
    return repo


def out(r):
    return r.stdout + r.stderr


def css(repo):
    return (repo / CSS).read_text(encoding="utf-8")


def test_the_committed_stylesheet_matches_its_seeds(tokens):
    """The generated CSS is committed, so CI has to prove it is not stale."""
    r = run_tool(TOOL, tokens, "--check")
    assert r.returncode == 0, out(r)
    assert "OK" in out(r)


def test_a_hand_edited_colour_fails_the_check(tokens):
    """Editing the generated CSS instead of the seeds must not pass silently."""
    text = css(tokens).replace("--sc-brand: #c4441e;", "--sc-brand: #ff0000;", 1)
    (tokens / CSS).write_text(text, encoding="utf-8")
    r = run_tool(TOOL, tokens, "--check")
    assert r.returncode == 1
    assert "--sc-brand" in out(r)


def test_a_value_fixed_in_dark_but_not_auto_fails(tokens):
    """The historical bug, now caught by the generator rather than a parity check.

    Both blocks still look individually plausible; only the generator knows they
    are meant to be the same table.
    """
    text = css(tokens)
    hits = list(re.finditer(r"--sc-surface-raised: #[0-9a-f]{6};", text))
    assert len(hits) >= 3, "expected light, dark and auto declarations"
    dark = hits[1]
    broken = text[:dark.start()] + "--sc-surface-raised: #53575c;" + text[dark.end():]
    (tokens / CSS).write_text(broken, encoding="utf-8")
    r = run_tool(TOOL, tokens, "--check")
    assert r.returncode == 1, out(r)
    assert "--sc-surface-raised" in out(r)


def test_a_shadow_diverging_between_dark_and_auto_fails(tokens):
    """Shadows are not colours the seeds generate, so they are emitted verbatim into
    both blocks instead. This is precisely what drifted in issues 1492 and 1503."""
    text = css(tokens)
    hits = list(re.finditer(r"--sc-shadow-lg: [^;]+;", text))
    assert len(hits) >= 3
    auto = hits[-1]
    broken = text[:auto.start()] + "--sc-shadow-lg: 0 9px 9px rgba(0, 0, 0, 0.5);" + text[auto.end():]
    (tokens / CSS).write_text(broken, encoding="utf-8")
    r = run_tool(TOOL, tokens, "--check")
    assert r.returncode == 1, out(r)
    assert "--sc-shadow-lg" in out(r)


def test_write_restores_a_hand_edited_file(tokens):
    before = css(tokens)
    (tokens / CSS).write_text(before.replace("--sc-brand: #c4441e;",
                                             "--sc-brand: #ff0000;", 1), encoding="utf-8")
    assert run_tool(TOOL, tokens, "--write").returncode == 0
    assert css(tokens) == before
    assert run_tool(TOOL, tokens, "--check").returncode == 0


def test_moving_one_seed_repaints_a_whole_family(tokens):
    """The point of the seed layer: a theme is a few numbers, not ~89 colours.

    Moving the chrome hue must change every chrome token and leave the unrelated
    families alone -- otherwise the families are not really families.
    """
    tool = (TOOLS_DIR / TOOL).read_text(encoding="utf-8")
    patched, n = re.subn(r'"chrome": [\d.]+,', '"chrome": 30.0,', tool, count=1)
    assert n == 1, "chrome seed not found -- SEED_LIGHT's shape changed"
    path = tokens / "tools" / "moved_seed.py"
    path.write_text(patched, encoding="utf-8")

    r = run_tool(str(path), tokens, "--check")
    assert r.returncode == 1
    changed = {m.group(1) for m in re.finditer(r"(--sc-[a-z0-9-]+)\s+committed", out(r))}
    chrome = {t for t in changed if t.startswith("--sc-chrome")}
    assert len(chrome) >= 8, f"one seed should move the whole chrome ladder, moved {chrome}"
    assert not any(t.startswith(("--sc-fnd-success", "--sc-fnd-warning")) for t in changed), \
        f"moving chrome must not touch unrelated families: {changed}"

def test_chroma_scale_changes_saturation_without_touching_lightness(tokens):
    """CHROMA_SCALE is what lets a family be a colour rather than a tinted grey.

    The invariant that matters is LIGHTNESS: it carries contrast, so if changing saturation
    could shift L, a palette decision would stop being safe to make on judgement alone.

    Chroma is only asserted in aggregate. Per-token it can move either way: at the darkest
    chrome lightnesses the sRGB gamut allows very little chroma, so a large target clips and
    a smaller one may land higher. That clipping is the generator working correctly.
    """
    import re
    ns = {}
    exec(compile((TOOLS_DIR / "_oklch.py").read_text(encoding="utf-8"), "_oklch.py", "exec"), ns)
    hex_to_oklch = ns["hex_to_oklch"]

    tool = (TOOLS_DIR / TOOL).read_text(encoding="utf-8")
    halved, n = re.subn(r'("chrome": )[\d.]+(,\s*#\s*Nansemond)',
                        lambda m: m.group(1) + "0.70" + m.group(2), tool, count=1)
    assert n == 1, "chrome entry in CHROMA_SCALE not found -- its shape changed"
    path = tokens / "tools" / "half_chroma.py"
    path.write_text(halved, encoding="utf-8")

    r = run_tool(str(path), tokens, "--check")
    assert r.returncode == 1, out(r)
    moved = re.findall(r"(--sc-chrome[a-z-]*)\s+committed (#[0-9a-f]{6}), seeds give (#[0-9a-f]{6})",
                       out(r))
    assert len(moved) >= 8, f"halving chroma should move the whole ladder, moved {len(moved)}"

    for token, old, new in moved:
        l_old, _c, _h = hex_to_oklch(old)
        l_new, _c2, _h2 = hex_to_oklch(new)
        assert abs(l_new - l_old) < 0.01, (
            f"{token}: lightness moved {l_old:.4f} -> {l_new:.4f}; contrast would no longer be guaranteed"
        )

    mean_old = sum(hex_to_oklch(o)[1] for _t, o, _n in moved) / len(moved)
    mean_new = sum(hex_to_oklch(n)[1] for _t, _o, n in moved) / len(moved)
    assert mean_new < mean_old, f"halving the scale should desaturate overall: {mean_old:.4f} -> {mean_new:.4f}"


def test_only_the_named_family_is_affected(tokens):
    """A scale on one family must not leak into the others."""
    import re
    tool = (TOOLS_DIR / TOOL).read_text(encoding="utf-8")
    patched, n = re.subn(r'("chrome": )[\d.]+(,\s*#\s*Nansemond)',
                         lambda m: m.group(1) + "0.50" + m.group(2), tool, count=1)
    assert n == 1
    path = tokens / "tools" / "scoped.py"
    path.write_text(patched, encoding="utf-8")
    r = run_tool(str(path), tokens, "--check")
    changed = {m.group(1) for m in re.finditer(r"(--sc-[a-z0-9-]+)\s+committed", out(r))}
    assert changed, "expected some tokens to change"
    assert all(t.startswith("--sc-chrome") for t in changed), \
        f"chroma scale leaked outside the chrome family: {sorted(t for t in changed if not t.startswith('--sc-chrome'))}"
