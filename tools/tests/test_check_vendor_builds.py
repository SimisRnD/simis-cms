"""Covers tools/check-vendor-builds.py.

Two rules carry the whole tool and both are easy to get subtly wrong, so most of these tests are
about their edges: an unminified file only counts as dead weight when its minified twin is ALSO
packaged (otherwise a runtime loader has nothing to fall back on), and a referenced asset missing
from the WAR is a 404 rather than a saving.
"""

import subprocess
import sys
import zipfile
from pathlib import Path

TOOL = Path(__file__).resolve().parent.parent / "check-vendor-builds.py"


def build(tmp_path: Path, *, jsp_refs, war_files) -> tuple[Path, Path]:
    root = tmp_path / "repo"
    web = root / "src" / "main" / "webapp"
    (web / "WEB-INF" / "jsp").mkdir(parents=True)
    (web / "WEB-INF" / "jsp" / "main.jsp").write_text(
        "\n".join(f'<link href="${{ctx}}{ref}" />' for ref in jsp_refs))
    # check-vendor-builds delegates the served-set derivation to check-source-maps, which follows
    # @import/url() out of served stylesheets, so those must exist on disk to be walked.
    for rel in war_files:
        p = web / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("x")

    war = tmp_path / "app.war"
    with zipfile.ZipFile(war, "w") as zf:
        for rel in war_files:
            zf.writestr(rel, "x")
    return root, war


def run(root: Path, war: Path) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, str(TOOL), "--war", str(war), "--root", str(root)],
                          capture_output=True, text=True)


def test_a_minified_file_that_is_served_is_accepted(tmp_path):
    root, war = build(tmp_path,
                      jsp_refs=["/javascript/vendor/lib.min.js"],
                      war_files=["javascript/vendor/lib.min.js"])
    result = run(root, war)
    assert result.returncode == 0, result.stdout + result.stderr


def test_an_unminified_twin_of_a_packaged_minified_file_is_reported(tmp_path):
    """The 17.8 MB case: mermaid.js beside the mermaid.min.js the pages actually load."""
    root, war = build(tmp_path,
                      jsp_refs=["/javascript/vendor/lib.min.js"],
                      war_files=["javascript/vendor/lib.min.js", "javascript/vendor/lib.js"])
    result = run(root, war)
    assert result.returncode == 1
    assert "UNSWEPT     javascript/vendor/lib.js" in result.stdout


def test_an_unminified_file_with_no_minified_twin_is_left_alone(tmp_path):
    """ace's mode-*.js and codemirror's themes: loaded by path at runtime, no twin to fall back on.

    Reporting these would invite deleting them, which breaks the editor with a 404 no test covers.
    """
    root, war = build(tmp_path,
                      jsp_refs=["/javascript/vendor/lib.min.js"],
                      war_files=["javascript/vendor/lib.min.js", "javascript/vendor/mode-xml.js"])
    result = run(root, war)
    assert result.returncode == 0, result.stdout + result.stderr


def test_an_unminified_file_that_is_itself_served_is_left_alone(tmp_path):
    """jsuites.css and jquery.gridmanager.js really are the served build, twin or no twin."""
    root, war = build(tmp_path,
                      jsp_refs=["/javascript/vendor/lib.js"],
                      war_files=["javascript/vendor/lib.js", "javascript/vendor/lib.min.js"])
    result = run(root, war)
    assert result.returncode == 0, result.stdout + result.stderr


def test_a_referenced_asset_absent_from_the_war_is_reported(tmp_path):
    """The dangerous direction, and the one nothing else in the build checks."""
    root, war = build(tmp_path,
                      jsp_refs=["/javascript/vendor/lib.min.js", "/css/vendor/theme/one-dark.css"],
                      war_files=["javascript/vendor/lib.min.js"])
    result = run(root, war)
    assert result.returncode == 1
    assert "MISSING     css/vendor/theme/one-dark.css" in result.stdout


def test_a_servlet_served_path_is_not_reported_missing(tmp_path):
    """/css/custom/* is generated from the database by StylesheetServlet; there is no such file."""
    root, war = build(tmp_path,
                      jsp_refs=["/javascript/vendor/lib.min.js", "/css/custom/stylesheet.css"],
                      war_files=["javascript/vendor/lib.min.js"])
    result = run(root, war)
    assert result.returncode == 0, result.stdout + result.stderr


def test_a_fontawesome_path_is_not_reported_missing(tmp_path):
    """main.jsp builds this directory name with ${font:fontawesome()}, so the scanner cannot see it."""
    root, war = build(
        tmp_path,
        jsp_refs=["/javascript/vendor/lib.min.js",
                  "/css/fontawesome-free-6.1.1-web/css/all.min.css"],
        war_files=["javascript/vendor/lib.min.js"])
    result = run(root, war)
    assert result.returncode == 0, result.stdout + result.stderr
