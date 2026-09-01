"""check-vendored-provenance.py: manifest verification in both directions.

A MISMATCH / MISSING / UNLISTED finding is exit 1. An unreadable manifest or an empty
jar tree is exit 2, because neither is evidence about the jars: an integrity check
that could not read its own inputs has proved nothing, and must not be able to look
like one that caught a tampered jar.
"""

from conftest import run_tool, write

TOOL = "check-vendored-provenance.py"


def seed(repo):
    write(repo, "lib/build/alpha-1.0.jar", "alpha bytes")
    write(repo, "lib/war/beta-2.0.jar", "beta bytes")


def test_write_then_clean_verify(repo):
    seed(repo)
    assert run_tool(TOOL, repo, "--write").returncode == 0
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "OK: 2 vendored jars" in r.stdout


def test_missing_manifest_exits_two_with_hint(repo):
    seed(repo)
    r = run_tool(TOOL, repo)
    assert r.returncode == 2, r.stdout + r.stderr
    assert "--write" in (r.stdout + r.stderr)


def test_tampered_jar_is_mismatch(repo):
    seed(repo)
    run_tool(TOOL, repo, "--write")
    (repo / "lib/build/alpha-1.0.jar").write_text("alpha bytes TAMPERED")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "MISMATCH" in r.stdout and "alpha-1.0.jar" in r.stdout


def test_new_jar_is_unlisted(repo):
    seed(repo)
    run_tool(TOOL, repo, "--write")
    write(repo, "lib/build/gamma-3.0.jar", "gamma bytes")
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "UNLISTED" in r.stdout and "gamma-3.0.jar" in r.stdout


def test_deleted_jar_is_missing(repo):
    seed(repo)
    run_tool(TOOL, repo, "--write")
    (repo / "lib/war/beta-2.0.jar").unlink()
    r = run_tool(TOOL, repo)
    assert r.returncode == 1
    assert "MISSING" in r.stdout and "beta-2.0.jar" in r.stdout


def test_malformed_manifest_line_exits_two(repo):
    # A manifest that cannot be parsed says nothing about the jars on disk.
    seed(repo)
    write(repo, "lib/PROVENANCE.sha256", "not-a-sha  lib/build/alpha-1.0.jar\n")
    r = run_tool(TOOL, repo)
    assert r.returncode == 2, r.stdout + r.stderr
    assert "malformed line" in (r.stdout + r.stderr)


def test_no_jars_under_lib_exits_two(repo):
    # Pointed at the wrong root, or lib/ moved. Verifying zero jars is not a pass.
    r = run_tool(TOOL, repo)
    assert r.returncode == 2, r.stdout + r.stderr
    assert "no jars found" in (r.stdout + r.stderr)
