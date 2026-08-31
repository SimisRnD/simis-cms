"""check-token-contrast.py: a ratio in a comment is a promise about a declaration.

The tool's tables are pinned to the real platform-tokens.css -- its CLAIMS
patterns quote that file's actual comments -- so the fixture starts from the
real file and each test mutates it, rather than inventing a synthetic stylesheet
the tables would not recognise.
"""

import importlib.util
import re
import subprocess
import sys

import pytest
from conftest import TOOLS_DIR, run_tool, write

TOOL = "check-token-contrast.py"
CSS = "src/main/webapp/css/platform-tokens.css"

REPO_ROOT = TOOLS_DIR.parent
REAL_CSS = (REPO_ROOT / CSS).read_text(encoding="utf-8")


@pytest.fixture
def tokens(repo):
    """A repo tree holding a copy of the real token stylesheet."""
    write(repo, CSS, REAL_CSS)
    return repo


def edit(repo, old: str, new: str, count: int = -1) -> None:
    """Rewrite part of the copied stylesheet, asserting the anchor still exists."""
    text = (repo / CSS).read_text(encoding="utf-8")
    assert old in text, f"anchor not in {CSS}: {old!r}"
    (repo / CSS).write_text(text.replace(old, new) if count < 0
                            else text.replace(old, new, count), encoding="utf-8")


def out(r):
    return r.stdout + r.stderr


# WAIVED is empty, and should stay that way -- every gap it held has been closed.
# The waiver paths still need covering, so these run a COPY of the tool carrying an
# injected entry. Asserting against whichever gap production happens to be carrying
# would make these tests fail as a reward for fixing one, which is how they broke
# the last two times a colour was repaired.

def tool_holding(tmp_path, key: str, reason: str = "held open for this test"):
    """A copy of the tool whose WAIVED table holds exactly the one given entry."""
    src = (TOOLS_DIR / TOOL).read_text(encoding="utf-8")
    patched, n = re.subn(r"^WAIVED = \{.*?^\}\n",
                         "WAIVED = {\n    %s: %r,\n}\n" % (key, reason),
                         src, count=1, flags=re.S | re.M)
    assert n == 1, "WAIVED table not found -- its shape changed"
    path = tmp_path / "tool_with_waiver.py"
    path.write_text(patched, encoding="utf-8")
    return path


def run_copy(tool_path, root, *args):
    return subprocess.run([sys.executable, str(tool_path), str(root), *args],
                          capture_output=True, text=True)


# -- the calculator --------------------------------------------------------
#
# Loaded directly rather than through the CLI: these are the reference points
# that decide whether every other verdict in the tool means anything, and they
# are worth asserting on the function rather than on a summary line.

def _module():
    spec = importlib.util.spec_from_file_location("check_token_contrast", TOOLS_DIR / TOOL)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def test_the_calculator_resolves_the_pass_fail_boundary():
    """#767676 passes AA on white and #777777 does not -- one step apart. A calculator
    that rounds past that boundary would have called the 4.4981:1 alert pairing a pass."""
    m = _module()
    assert round(m.ratio_of("#000000", "#ffffff"), 2) == 21.00
    assert round(m.ratio_of("#767676", "#ffffff"), 2) == 4.54
    assert round(m.ratio_of("#777777", "#ffffff"), 2) == 4.48


def test_the_ratio_is_symmetric():
    m = _module()
    assert m.ratio_of("#2c79be", "#ffffff") == m.ratio_of("#ffffff", "#2c79be")


def test_shorthand_and_alpha_hex_parse():
    m = _module()
    assert m.ratio_of("#fff", "#000") == m.ratio_of("#ffffff", "#000000")
    assert m.parse_colour("#00000080")[3] == pytest.approx(128 / 255)


def test_a_translucent_foreground_is_composited_over_its_background():
    """Contrast is undefined for a colour that is partly its own backdrop. --sc-border is
    rgba() in dark mode, so getting this wrong is one promotion away from mattering."""
    m = _module()
    # 50% white over black is 127.5, which lands between the two adjacent solid greys.
    translucent = m.ratio_of("rgba(255, 255, 255, 0.5)", "#000000")
    assert m.ratio_of("#7f7f7f", "#000000") < translucent < m.ratio_of("#808080", "#000000")
    # Fully transparent is indistinguishable from its backdrop.
    assert m.ratio_of("rgba(0, 0, 0, 0)", "#123456") == pytest.approx(1.0)


def test_the_bounds_reproduce_the_numbers_1489_derived_by_hand():
    """how_to_clear() is only worth printing if it agrees with the record. platform-tokens.css
    states that at #609ace "anything these tokens sit on has to stay at or under 0.0279
    relative luminance", and issue #1489 derived L >= 0.5993 for the opposite move."""
    m = _module()
    hint = m.how_to_clear("#609ace", "#53575c", 4.5)
    assert "background L <= 0.0279" in hint
    assert "foreground L >= 0.5993" in hint


def test_the_bounds_name_which_side_can_actually_move():
    """The distinction that stops someone applying #1489's fix to #1498. In dark the
    background is the darker side with room to move; in light it is the lighter side and
    already near white, so its bound lands on a colour that is no longer a surface."""
    m = _module()
    dark = m.how_to_clear("#979ca4", "#53575c", 4.5)          # #1489 shape
    light = m.how_to_clear("#71767d", "#f4f5f7", 4.5)         # #1498 shape
    assert "background L <= " in dark and "darker" in dark
    assert "background L >= 0.9827" in light and "~#fefefe" in light


def test_an_unreachable_bound_says_so_rather_than_naming_a_colour():
    """A mid grey on black is already on the darkest surface there is, so the bound comes
    out negative -- the tool must say the move does not exist rather than invent a hex."""
    m = _module()
    hint = m.how_to_clear("#555555", "#000000", 4.5)
    assert "background cannot reach L <= -0.0187" in hint
    assert "foreground L >= 0.1750 (lighter" in hint


def test_the_bounds_print_on_a_failure_but_not_on_a_pass(tokens, tmp_path):
    """Default output stays readable: a waived pairing's analysis lives in its ticket, a
    failing one needs the bounds in front of whoever just broke it."""
    # The waived-but-passing half needs a live waiver, and the committed table has
    # none by design, so this makes its own.
    tool = tool_holding(tmp_path, '(None, "--sc-fnd-on-accent", "--sc-fnd-alert")')
    edit(tokens, "--sc-fnd-alert: #cb4834;", "--sc-fnd-alert: #f0a090;")
    r = run_copy(tool, tokens)
    assert r.returncode == 0, out(r)
    assert "to clear it:" not in r.stdout
    assert "to clear it:" in run_copy(tool, tokens, "--verbose").stdout

    edit(tokens, "--sc-surface-raised: #3a3025;", "--sc-surface-raised: #53575c;")
    edit(tokens, "--sc-surface-overlay: #3a3025;", "--sc-surface-overlay: #53575c;")
    edit(tokens, "--sc-field-bg: #2f271e;", "--sc-field-bg: #53575c;")
    edit(tokens, "--sc-field-disabled-bg: #2f271e;", "--sc-field-disabled-bg: #53575c;")
    f = run_tool(TOOL, tokens)
    assert f.returncode == 1
    assert "to clear it: background L <= 0.0469" in out(f)


def test_self_test_flag_passes():
    r = run_tool(TOOL, REPO_ROOT, "--self-test")
    assert r.returncode == 0, out(r)


# -- the real file ---------------------------------------------------------

def test_the_committed_token_file_passes(tokens):
    r = run_tool(TOOL, tokens)
    assert r.returncode == 0, out(r)
    assert "OK" in r.stdout


def test_missing_token_file_fails(repo):
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "MISSING" in out(r)


# -- 1. pairings -----------------------------------------------------------

def test_a_brand_accent_used_as_a_surface_fails(tokens):
    """Issue #1489 exactly: --sc-surface-raised flattened to Anthracite. The four tokens
    that carry that value must all report, in both the dark and the auto block."""
    edit(tokens, "--sc-surface-raised: #3a3025;", "--sc-surface-raised: #53575c;")
    edit(tokens, "--sc-surface-overlay: #3a3025;", "--sc-surface-overlay: #53575c;")
    edit(tokens, "--sc-field-bg: #2f271e;", "--sc-field-bg: #53575c;")
    edit(tokens, "--sc-field-disabled-bg: #2f271e;", "--sc-field-disabled-bg: #53575c;")
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    text = out(r)
    assert "CONTRAST dark  --sc-text-muted" in text and "2.77:1" in text
    assert "CONTRAST dark  --sc-link " in text and "3.02:1" in text
    assert "CONTRAST auto  --sc-text-muted" in text


def test_a_focus_ring_under_the_non_text_floor_fails(tokens):
    """SC 1.4.11: a focus indicator is held to 3:1, not 4.5:1, and the table must not
    silently promote it to the text floor or demote the text tokens to 3:1."""
    edit(tokens, "  --sc-focus-ring: #e09b4a;\n", "  --sc-focus-ring: #2a2d33;\n", 1)
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    assert "--sc-focus-ring" in out(r) and "floor 3.0:1" in out(r)


def test_var_indirection_is_followed_through_two_levels(tokens):
    """--sc-fnd-surface is var(--sc-surface-raised) and --sc-fnd-white is
    var(--sc-fnd-surface); --sc-fnd-ink is var(--sc-text). A resolver that stopped at the
    first var() would silently check the light-mode value in a dark block."""
    edit(tokens, "--sc-surface-raised: #3a3025;", "--sc-surface-raised: #f4f5f7;")
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    assert "--sc-fnd-ink" in out(r) and "--sc-fnd-surface" in out(r)


def test_a_waived_pairing_reports_but_does_not_fail(tokens, tmp_path):
    """A waiver holds a known gap open with a reason instead of dropping it from the
    table: it reports on every run, so it cannot go quiet, and it never turns a red
    build green by silence."""
    tool = tool_holding(tmp_path, '(None, "--sc-fnd-on-accent", "--sc-fnd-alert")')
    edit(tokens, "--sc-fnd-alert: #cb4834;", "--sc-fnd-alert: #f0a090;")
    r = run_copy(tool, tokens)
    assert r.returncode == 0, out(r)
    assert "WAIVED" in r.stdout and "--sc-fnd-alert" in r.stdout
    assert "STALE" not in out(r)


def test_a_waiver_whose_pairing_now_passes_fails_the_run(tokens, tmp_path):
    """The failure mode the table was meant to avoid and did not: fixing the colour
    silences the entry instead of retiring it, so it sits there forever looking like a
    live exception. Both entries the table carried reached that state unnoticed -- one
    when the light placeholder was fixed, one when Foundation's alert moved to #cb4834
    and started computing 4.61:1 against an entry still claiming 4.4981:1."""
    tool = tool_holding(tmp_path, '(None, "--sc-fnd-on-accent", "--sc-fnd-alert")')
    # left unmodified, so the waived pairing passes and the waiver covers nothing
    r = run_copy(tool, tokens)
    assert r.returncode == 1, out(r)
    text = out(r)
    assert "STALE" in text
    assert "--sc-fnd-on-accent on --sc-fnd-alert" in text
    assert "WAIVED" not in r.stdout


def test_a_waiver_for_a_pairing_the_table_never_checks_is_dead_config():
    """The other way a waiver stops meaning anything: the key names a block, token or
    surface the PAIRINGS table does not put together, so it never matches and never
    prints. Exercised on the function because no edit to the stylesheet can produce it."""
    m = _module()
    errors = []
    m.WAIVED = {("light", "--sc-text-muted", "--sc-surface-nonexistent"): "reason"}
    m.check_waivers(set(), errors)
    assert len(errors) == 1
    assert "does not make" in errors[0] and "--sc-surface-nonexistent" in errors[0]


def test_the_committed_table_carries_no_dead_waivers(tokens):
    """The state this check exists to hold: the shipped table excuses nothing that has
    since been fixed, so a clean run reports neither a waiver nor a stale one."""
    r = run_tool(TOOL, tokens)
    assert r.returncode == 0, out(r)
    assert "STALE" not in out(r)
    assert "WAIVED" not in r.stdout


# -- 2. dark/auto parity ---------------------------------------------------

def test_a_token_fixed_in_only_the_dark_block_fails(tokens):
    """The whole point: a value corrected in one block leaves every theme.ui.mode=auto
    site on the old one, and both blocks otherwise look individually correct."""
    edit(tokens, "    --sc-surface-raised: #3a3025;", "    --sc-surface-raised: #53575c;")
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    assert "PARITY   --sc-surface-raised" in out(r)


def test_a_token_missing_from_the_auto_block_fails(tokens):
    edit(tokens, "    --sc-field-placeholder: #b3a894;\n", "", 1)
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    assert "PARITY   --sc-field-placeholder" in out(r)


def test_shadows_may_differ_between_the_dark_and_auto_blocks(tokens):
    """They already do on main -- pre-existing, unrelated to contrast, and exempted by
    name so the parity check does not have to be weakened to accommodate them."""
    edit(tokens, "  --sc-shadow-lg: 0 2px 6px rgba(0, 0, 0, 0.5);",
         "  --sc-shadow-lg: 0 3px 9px rgba(0, 0, 0, 0.6);")
    r = run_tool(TOOL, tokens)
    assert r.returncode == 0, out(r)


def test_nothing_reports_as_retirable_while_no_exemption_is_pending(tokens):
    """PARITY_PENDING is empty since PR #1492 converged md and lg, so a clean tree must
    say nothing about retirement. This is the state the retirement path reports from when
    it has no work to do; the path itself earned its keep by flagging md and lg on the
    first run after #1492 landed, which is how this test came to be rewritten."""
    r = run_tool(TOOL, tokens)
    assert r.returncode == 0, out(r)
    assert "retired" not in r.stdout


def test_a_dark_auto_divergence_outside_the_exempt_set_still_fails(tokens):
    """The exemptions are narrow. A token that is not exempt must still fail on divergence,
    or emptying PARITY_PENDING would have quietly widened what the check tolerates."""
    edit(tokens, '    --sc-shadow-lg: 0 2px 6px rgba(0, 0, 0, 0.5);',
         '    --sc-shadow-lg: 0 9px 9px rgba(0, 0, 0, 0.5);', count=1)
    r = run_tool(TOOL, tokens)
    assert r.returncode != 0, out(r)
    assert "PARITY" in out(r)


def test_the_parity_check_is_total_with_no_exemptions_left(tokens):
    """PR #1492 converged md and lg, PR #1503 converged sm, so both exemption sets are empty
    and every token declared in one block must match the other. Diverging either remaining
    shadow must now fail -- previously each was excused by name. (--sc-shadow-sm was the
    third case until issue 1590 removed the token as unconsumed.)"""
    original = (tokens / CSS).read_text(encoding="utf-8")
    for token in ("--sc-shadow-md", "--sc-shadow-lg"):
        hits = list(re.finditer(rf"{re.escape(token)}:\s*[^;]+;", original))
        assert len(hits) >= 3, f"expected light, dark and auto declarations of {token}"
        last = hits[-1]  # the auto block's copy
        broken = original[:last.start()] + f"{token}: 0 9px 9px rgba(0, 0, 0, 0.5);" + original[last.end():]
        (tokens / CSS).write_text(broken, encoding="utf-8")
        r = run_tool(TOOL, tokens)
        assert r.returncode != 0, f"{token} divergence was tolerated: {out(r)}"
        assert "PARITY" in out(r), out(r)
    (tokens / CSS).write_text(original, encoding="utf-8")


# -- 3. comment claims -----------------------------------------------------

def test_a_stale_comment_ratio_fails(tokens):
    """The defect's actual signature: the value moved, the comment did not."""
    edit(tokens, "text 11.63:1, muted 4.91:1", "text 6.67:1, muted 5.01:1")
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    text = out(r)
    assert "COMMENT" in text and "6.67:1" in text and "--sc-text" in text


def test_a_comment_ratio_is_checked_at_its_own_precision(tokens):
    """7.3:1 is a one-decimal claim about a 7.2775:1 value and is correct as written;
    demanding two decimals of a one-decimal number would fail the file for no reason."""
    r = run_tool(TOOL, tokens)
    assert r.returncode == 0, out(r)
    edit(tokens, "white text on it is 7.3:1", "white text on it is 7.4:1")
    assert run_tool(TOOL, tokens).returncode == 1


def test_a_floor_claim_may_be_exceeded_but_not_missed(tokens):
    """"all keep past 4.9:1 on it" is a bound over three pairings, not an equality."""
    edit(tokens, "all keep past 4.9:1 on it", "all keep past 4.5:1 on it")
    assert run_tool(TOOL, tokens).returncode == 0
    edit(tokens, "all keep past 4.5:1 on it", "all keep past 6.0:1 on it")
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    assert "at least 6.0:1" in out(r)


def test_the_duplicated_foundation_table_is_checked_in_both_blocks(tokens):
    """That summary block is copied verbatim into the dark and auto blocks; each copy is
    resolved against its own block's tokens, so breaking one copy reports one failure."""
    edit(tokens, "ink on surface ............. 11.63:1",
         "ink on surface ............. 11.11:1", 1)
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    assert out(r).count("comment says 11.11:1") == 1


def test_a_claim_whose_comment_disappears_fails(tokens):
    """Otherwise deleting a comment silently deletes its verification, and the tool
    reports OK over a file it is no longer really checking."""
    edit(tokens, "raised is 1.28:1 over surface", "raised is clearly over surface")
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    assert "STALE" in out(r)


# -- 4. registration -------------------------------------------------------

def test_a_new_unregistered_ratio_in_a_comment_fails(tokens):
    """A hand-computed number must not enter the file without being tied to a pairing --
    checking only what happens to be registered is how the stale comment survived."""
    edit(tokens, "  --sc-focus-ring: #e09b4a;\n",
         "  /* looks reassuring, means nothing: 9.99:1 */\n  --sc-focus-ring: #e09b4a;\n", 1)
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    assert "UNCLAIMED" in out(r) and "9.99:1" in out(r)


def test_a_wcag_floor_is_not_treated_as_a_measurement(tokens):
    """"at 4.5:1" in the header states the success criterion, not a token pairing."""
    r = run_tool(TOOL, tokens)
    assert r.returncode == 0, out(r)
    assert "UNCLAIMED" not in out(r)


# -- parsing traps ---------------------------------------------------------

def test_a_declaration_inside_a_comment_is_not_a_token(tokens):
    """This file's comments contain text like "--sc-surface: text 16.12:1, ..." which a
    naive declaration regex reads as a real declaration. Comments are blanked first, so a
    colour written inside one has no effect on any verdict."""
    edit(tokens, "  --sc-focus-ring: #e09b4a;\n",
         "  /* --sc-text-muted: #ff0000; --sc-surface-raised: #ff0000; */\n"
         "  --sc-focus-ring: #e09b4a;\n", 1)
    r = run_tool(TOOL, tokens)
    assert r.returncode == 0, out(r)


def test_a_missing_theme_block_fails(tokens):
    """A renamed or dropped selector must fail loudly, not quietly check two blocks."""
    edit(tokens, ':root[data-theme="auto"] {', ":root[data-theme=\"os\"] {")
    r = run_tool(TOOL, tokens)
    assert r.returncode == 1
    assert "PARSE" in out(r) and "auto" in out(r)

def test_a_rendered_measurement_must_be_registered_like_any_other_ratio(tokens):
    """A ratio measured against the real cascade is still a promise in a comment. It cannot
    be re-derived, but it must be declared -- otherwise the strictness that makes this gate
    worth having has a hole shaped like "I measured it in a browser"."""
    edit(tokens, "10.52:1 or better", "10.53:1 or better")
    r = run_tool(TOOL, tokens)
    assert r.returncode != 0, out(r)
    assert "UNCLAIMED" in out(r)


def test_a_rendered_entry_matching_nothing_is_reported_as_stale(tokens):
    """RENDERED_RATIOS is held to the same staleness rule as EXEMPT_RATIOS: an entry whose
    comment has been deleted or reworded is dead config, and dead config is how these tables
    stop being read."""
    edit(tokens, "is 4.97:1 (the dark .reveal", "is 4.97 to 1 (the dark .reveal")
    r = run_tool(TOOL, tokens)
    assert r.returncode != 0, out(r)
    assert "STALE" in out(r)
    assert "RENDERED_RATIOS" in out(r)
