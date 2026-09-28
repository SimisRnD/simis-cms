"""vendor-sync.py: re-vendor drifted jars from a Maven-layout source, verified, atomically.

Every test uses --mirror (a local directory in Maven repository layout) or --dry-run, so the
suite never touches the network.
"""

from __future__ import annotations

import hashlib
from pathlib import Path

from conftest import make_pom, run_tool

TOOL = "vendor-sync.py"
MANIFEST = "lib/PROVENANCE.sha256"


def put(root: Path, rel: str, data: bytes) -> Path:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(data)
    return p


def vendored_repo(repo: Path, deps, jars) -> None:
    """A repo whose pom declares ``deps`` and whose lib/ holds ``jars``, with a clean manifest."""
    make_pom(repo, deps=deps)
    for rel, data in jars.items():
        put(repo, rel, data)
    r = run_tool("check-vendored-provenance.py", repo, "--write")
    assert r.returncode == 0, r.stdout + r.stderr


def publish(mirror: Path, group: str, artifact: str, version: str, data: bytes,
            sha1: str | None = "auto", sha256: str | None = None) -> None:
    """Lay out <artifact>-<version>.jar (and its checksum files) the way Maven Central does."""
    base = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.jar"
    put(mirror, base, data)
    if sha1 is not None:
        digest = hashlib.sha1(data).hexdigest() if sha1 == "auto" else sha1
        put(mirror, base + ".sha1", digest.encode())
    if sha256 is not None:
        digest = hashlib.sha256(data).hexdigest() if sha256 == "auto" else sha256
        put(mirror, base + ".sha256", f"{digest}  {artifact}-{version}.jar\n".encode())


def empty_mirror(repo: Path) -> str:
    """A mirror directory with nothing published in it."""
    (repo / "m2").mkdir(exist_ok=True)
    return str(repo / "m2")


def snapshot(repo: Path) -> dict[str, bytes]:
    """Every file under lib/, for asserting that a refused run changed nothing."""
    return {p.relative_to(repo).as_posix(): p.read_bytes()
            for p in sorted((repo / "lib").rglob("*")) if p.is_file()}


def test_nothing_to_sync_when_versions_match(repo):
    vendored_repo(repo, [("org.example", "widget", "1.0")],
                  {"lib/build/widget/widget-1.0.jar": b"old"})
    before = snapshot(repo)
    r = run_tool(TOOL, repo, "--mirror", empty_mirror(repo))
    assert r.returncode == 0, r.stdout + r.stderr
    assert "Nothing to sync" in r.stdout
    assert snapshot(repo) == before


def test_dry_run_prints_the_plan_and_changes_nothing(repo):
    vendored_repo(repo, [("org.example", "widget", "2.0")],
                  {"lib/build/widget/widget-1.0.jar": b"old"})
    before = snapshot(repo)
    # No --mirror: a dry run must not need a source at all, because it fetches nothing.
    r = run_tool(TOOL, repo, "--dry-run")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "widget  1.0 -> 2.0" in r.stdout
    assert "https://repo1.maven.org/maven2/org/example/widget/2.0/widget-2.0.jar" in r.stdout
    assert snapshot(repo) == before


def test_sync_replaces_the_jar_in_place_and_both_gates_pass(repo):
    # widget is alone in its directory: replacing it must not delete the directory.
    vendored_repo(repo, [("org.example", "widget", "2.0")],
                  {"lib/build/widget/widget-1.0.jar": b"old", "lib/build/other/keep-3.1.jar": b"k"})
    publish(repo / "m2", "org.example", "widget", "2.0", b"new widget bytes")
    r = run_tool(TOOL, repo, "--mirror", str(repo / "m2"))
    assert r.returncode == 0, r.stdout + r.stderr
    assert not (repo / "lib/build/widget/widget-1.0.jar").exists()
    assert (repo / "lib/build/widget/widget-2.0.jar").read_bytes() == b"new widget bytes"
    manifest = (repo / MANIFEST).read_text()
    assert "lib/build/widget/widget-2.0.jar" in manifest
    assert "widget-1.0.jar" not in manifest
    assert hashlib.sha256(b"new widget bytes").hexdigest() in manifest
    assert run_tool("check-dependency-drift.py", repo, "--strict").returncode == 0
    assert run_tool("check-vendored-provenance.py", repo).returncode == 0


def test_updated_manifest_is_exactly_what_the_provenance_tool_would_write(repo):
    vendored_repo(repo, [("org.example", "widget", "2.0"), ("org.example", "alpha", "1.0")],
                  {"lib/build/widget/widget-1.0.jar": b"old", "lib/build/a/alpha-1.0.jar": b"a",
                   "lib/compile/zeta-9.jar": b"z"})
    publish(repo / "m2", "org.example", "widget", "2.0", b"new")
    assert run_tool(TOOL, repo, "--mirror", str(repo / "m2")).returncode == 0
    ours = (repo / MANIFEST).read_bytes()
    assert run_tool("check-vendored-provenance.py", repo, "--write").returncode == 0
    assert (repo / MANIFEST).read_bytes() == ours


def test_one_bad_checksum_aborts_every_download_before_anything_is_written(repo):
    vendored_repo(repo, [("org.example", "alpha", "2.0"), ("org.example", "beta", "2.0")],
                  {"lib/build/x/alpha-1.0.jar": b"a1", "lib/build/x/beta-1.0.jar": b"b1"})
    publish(repo / "m2", "org.example", "alpha", "2.0", b"a2")          # verifies
    publish(repo / "m2", "org.example", "beta", "2.0", b"b2", sha1="0" * 40)  # does not
    before = snapshot(repo)
    r = run_tool(TOOL, repo, "--mirror", str(repo / "m2"))
    assert r.returncode == 1
    assert "sha1 mismatch" in r.stderr
    # alpha verified, but must not have been written either: the run is all or nothing.
    assert snapshot(repo) == before


def test_a_missing_sha1_is_refused(repo):
    vendored_repo(repo, [("org.example", "widget", "2.0")],
                  {"lib/build/widget/widget-1.0.jar": b"old"})
    publish(repo / "m2", "org.example", "widget", "2.0", b"new", sha1=None)
    before = snapshot(repo)
    r = run_tool(TOOL, repo, "--mirror", str(repo / "m2"))
    assert r.returncode == 1
    assert "widget-2.0.jar.sha1: not found" in r.stderr
    assert snapshot(repo) == before


def test_a_published_sha256_is_verified_as_well(repo):
    vendored_repo(repo, [("org.example", "widget", "2.0")],
                  {"lib/build/widget/widget-1.0.jar": b"old"})
    publish(repo / "m2", "org.example", "widget", "2.0", b"new", sha256="f" * 64)
    before = snapshot(repo)
    r = run_tool(TOOL, repo, "--mirror", str(repo / "m2"))
    assert r.returncode == 1
    assert "sha256 mismatch" in r.stderr
    assert snapshot(repo) == before


def test_allowlisted_drift_is_never_re_vendored(repo):
    # jackson-coreutils is in check-dependency-drift.py's ALLOWLIST: 1.8 breaks the build.
    vendored_repo(repo, [("com.github.fge", "jackson-coreutils", "1.8")],
                  {"lib/build/json/jackson-coreutils-1.0.jar": b"held"})
    publish(repo / "m2", "com.github.fge", "jackson-coreutils", "1.8", b"would break")
    before = snapshot(repo)
    r = run_tool(TOOL, repo, "--mirror", str(repo / "m2"))
    assert r.returncode == 0, r.stdout + r.stderr
    assert "SKIP  jackson-coreutils" in r.stdout
    assert snapshot(repo) == before


def test_a_manifest_that_is_already_wrong_is_refused_before_any_change(repo):
    vendored_repo(repo, [("org.example", "widget", "2.0")],
                  {"lib/build/widget/widget-1.0.jar": b"old"})
    put(repo, "lib/build/stray/unexplained-1.0.jar", b"?")  # UNLISTED: would be blessed
    publish(repo / "m2", "org.example", "widget", "2.0", b"new")
    before = snapshot(repo)
    r = run_tool(TOOL, repo, "--mirror", str(repo / "m2"))
    assert r.returncode == 1
    assert "UNLISTED  lib/build/stray/unexplained-1.0.jar" in r.stderr
    assert snapshot(repo) == before


def test_coordinates_that_are_not_plain_are_refused(repo):
    for version in ("${missing.version}", "2.0/../../evil", "[1.0,2.0)"):
        vendored_repo(repo, [("org.example", "widget", version)],
                      {"lib/build/widget/widget-1.0.jar": b"old"})
        before = snapshot(repo)
        r = run_tool(TOOL, repo, "--mirror", empty_mirror(repo))
        assert r.returncode == 1, version
        assert "not a plain Maven coordinate" in r.stderr, version
        assert snapshot(repo) == before, version


def test_a_snapshot_version_is_refused(repo):
    vendored_repo(repo, [("org.example", "widget", "2.0-SNAPSHOT")],
                  {"lib/build/widget/widget-1.0.jar": b"old"})
    r = run_tool(TOOL, repo, "--mirror", empty_mirror(repo))
    assert r.returncode == 1
    assert "SNAPSHOT" in r.stderr


def test_a_missing_manifest_is_a_usage_error_not_a_finding(repo):
    make_pom(repo, deps=[("org.example", "widget", "2.0")])
    put(repo, "lib/build/widget/widget-1.0.jar", b"old")
    r = run_tool(TOOL, repo, "--dry-run")
    assert r.returncode == 2
