"""Covers tools/check-source-maps.py.

The tool's whole value is the distinction between "packaged" and "served": the WAR carries vendor
originals no page loads, and counting their sourceMappingURL declarations would force ~4.9 MB of
maps back into the artifact to satisfy references no browser makes. Most of these tests are about
that line.
"""

import subprocess
import sys
import zipfile
from pathlib import Path

TOOL = Path(__file__).resolve().parent.parent / "check-source-maps.py"


def build(tmp_path: Path, *, jsp_refs, webapp_files, war_files) -> tuple[Path, Path]:
    """A synthetic repo root plus a WAR, so the tool is exercised through its real CLI."""
    root = tmp_path / "repo"
    web = root / "src" / "main" / "webapp"
    (web / "WEB-INF" / "jsp").mkdir(parents=True)
    (web / "WEB-INF" / "jsp" / "main.jsp").write_text(
        "\n".join(f'<link href="${{ctx}}{ref}" />' for ref in jsp_refs))
    for rel, content in webapp_files.items():
        p = web / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content)

    war = tmp_path / "app.war"
    with zipfile.ZipFile(war, "w") as zf:
        for rel, content in war_files.items():
            zf.writestr(rel, content)
    return root, war


def run(root: Path, war: Path) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, str(TOOL), "--war", str(war), "--root", str(root)],
                          capture_output=True, text=True)


def test_a_served_file_and_the_map_it_declares_are_accepted(tmp_path):
    root, war = build(
        tmp_path,
        jsp_refs=["/javascript/vendor/lib.min.js"],
        webapp_files={"javascript/vendor/lib.min.js": "x//# sourceMappingURL=lib.min.js.map"},
        war_files={"javascript/vendor/lib.min.js": "x//# sourceMappingURL=lib.min.js.map",
                   "javascript/vendor/lib.min.js.map": "{}"})
    result = run(root, war)
    assert result.returncode == 0, result.stdout
    assert "1 source map(s) packaged" in result.stdout


def test_a_served_declaration_that_does_not_resolve_fails(tmp_path):
    # The failure the old build.xml comment guarded against: devtools 404s on the map.
    root, war = build(
        tmp_path,
        jsp_refs=["/javascript/vendor/lib.min.js"],
        webapp_files={"javascript/vendor/lib.min.js": "x//# sourceMappingURL=lib.min.js.map"},
        war_files={"javascript/vendor/lib.min.js": "x//# sourceMappingURL=lib.min.js.map"})
    result = run(root, war)
    assert result.returncode == 1
    assert "MISSING" in result.stdout
    assert "lib.min.js.map" in result.stdout


def test_a_map_nothing_served_declares_fails(tmp_path):
    # Harmless to a browser, which is why 4.93 MB of them accumulated unnoticed.
    root, war = build(
        tmp_path,
        jsp_refs=["/javascript/vendor/lib.min.js"],
        webapp_files={"javascript/vendor/lib.min.js": "x"},
        war_files={"javascript/vendor/lib.min.js": "x",
                   "javascript/vendor/orphan.js.map": "{}"})
    result = run(root, war)
    assert result.returncode == 1
    assert "UNREFERENCED" in result.stdout
    assert "orphan.js.map" in result.stdout


def test_a_declaration_from_a_packaged_but_unserved_file_is_ignored(tmp_path):
    # The distinction the whole tool turns on. chart.umd.js ships beside the .min.js the pages
    # actually load; its declaration is inert and must not drag its map back into the WAR.
    root, war = build(
        tmp_path,
        jsp_refs=["/javascript/vendor/lib.min.js"],
        webapp_files={"javascript/vendor/lib.min.js": "x",
                      "javascript/vendor/lib.js": "x//# sourceMappingURL=lib.js.map"},
        war_files={"javascript/vendor/lib.min.js": "x",
                   "javascript/vendor/lib.js": "x//# sourceMappingURL=lib.js.map"})
    result = run(root, war)
    assert result.returncode == 0, result.stdout


def test_a_stylesheet_pulled_in_by_a_served_stylesheet_counts_as_served(tmp_path):
    root, war = build(
        tmp_path,
        jsp_refs=["/css/site.css"],
        webapp_files={"css/site.css": '@import url("vendor/dep.css");',
                      "css/vendor/dep.css": "a{}/*# sourceMappingURL=dep.css.map */"},
        war_files={"css/site.css": '@import url("vendor/dep.css");',
                   "css/vendor/dep.css": "a{}/*# sourceMappingURL=dep.css.map */"})
    result = run(root, war)
    assert result.returncode == 1, "the pulled-in stylesheet is served, so its declaration counts"
    assert "dep.css.map" in result.stdout


def test_an_inlined_map_needs_no_file(tmp_path):
    root, war = build(
        tmp_path,
        jsp_refs=["/javascript/vendor/lib.min.js"],
        webapp_files={"javascript/vendor/lib.min.js": "x//# sourceMappingURL=data:application/json;base64,e30="},
        war_files={"javascript/vendor/lib.min.js": "x//# sourceMappingURL=data:application/json;base64,e30="})
    result = run(root, war)
    assert result.returncode == 0, result.stdout


def test_a_root_with_no_asset_references_is_an_error_not_a_pass(tmp_path):
    # Otherwise pointing --root at the wrong place would silently report success.
    root, war = build(tmp_path, jsp_refs=[], webapp_files={}, war_files={"x.map": "{}"})
    result = run(root, war)
    assert result.returncode == 1
    assert "found no asset references" in result.stderr
