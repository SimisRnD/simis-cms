"""check-dependency-drift.py: pom versions vs vendored lib/build jar filenames."""

from conftest import make_pom, run_tool, write

TOOL = "check-dependency-drift.py"


def test_matching_versions_pass_strict(repo):
    make_pom(repo, deps=[("org.example", "widget", "1.2.3")])
    write(repo, "lib/build/widget-1.2.3.jar", "jar bytes")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_version_drift_fails_strict(repo):
    make_pom(repo, deps=[("org.example", "widget", "2.0.0")])
    write(repo, "lib/build/widget-1.2.3.jar", "jar bytes")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "DRIFT" in r.stdout


def test_drift_reported_but_exit_zero_without_strict(repo):
    make_pom(repo, deps=[("org.example", "widget", "2.0.0")])
    write(repo, "lib/build/widget-1.2.3.jar", "jar bytes")
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "DRIFT" in r.stdout


# --- artifactIds shared across groupIds (jackson 2.x com.fasterxml.* beside 3.x tools.jackson.*) ---

import io
import zipfile


def jar_bytes(group: str, artifact: str, version: str) -> bytes:
    """A jar carrying the META-INF/maven/<group>/<artifact>/pom.properties that Maven builds embed."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(f"META-INF/maven/{group}/{artifact}/pom.properties",
                   f"groupId={group}\nartifactId={artifact}\nversion={version}\n")
    return buf.getvalue()


def put_jar(repo, rel: str, data: bytes) -> None:
    p = repo / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(data)


def jackson_pair(repo, v2_pom: str, v3_pom: str) -> None:
    make_pom(repo, deps=[("com.fasterxml.jackson.core", "jackson-core", v2_pom),
                         ("tools.jackson.core", "jackson-core", v3_pom)])
    put_jar(repo, "lib/build/jackson/jackson-core-2.22.2.jar",
            jar_bytes("com.fasterxml.jackson.core", "jackson-core", "2.22.2"))
    put_jar(repo, "lib/build/jackson/jackson-core-3.2.2.jar",
            jar_bytes("tools.jackson.core", "jackson-core", "3.2.2"))


def test_a_shared_artifact_id_matching_on_both_lines_passes(repo):
    jackson_pair(repo, "2.22.2", "3.2.2")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_drift_on_the_line_that_is_not_listed_last_is_still_caught(repo):
    # Keyed by artifactId alone, the 3.x declaration overwrote the 2.x one and the 3.x jar
    # overwrote the 2.x jar, so a 2.x-only bump compared 3.2.2 with 3.2.2 and passed.
    jackson_pair(repo, "2.22.3", "3.2.2")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "pom 2.22.3" in r.stdout and "WAR ships 2.22.2" in r.stdout


def test_a_jar_whose_group_is_not_declared_is_vendored_only_not_drift(repo):
    # The pom declares only the 3.x toml; the 2.x toml jar ships beside it.
    make_pom(repo, deps=[("tools.jackson.dataformat", "jackson-dataformat-toml", "3.2.2")])
    put_jar(repo, "lib/build/jackson/jackson-dataformat-toml-2.22.2.jar",
            jar_bytes("com.fasterxml.jackson.dataformat", "jackson-dataformat-toml", "2.22.2"))
    put_jar(repo, "lib/build/jackson/jackson-dataformat-toml-3.2.2.jar",
            jar_bytes("tools.jackson.dataformat", "jackson-dataformat-toml", "3.2.2"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "VENDORED-ONLY" in r.stdout and "jackson-dataformat-toml 2.22.2" in r.stdout


def test_a_colliding_jar_that_records_no_group_is_ambiguous_and_fails_strict(repo):
    make_pom(repo, deps=[("com.fasterxml.jackson.core", "jackson-core", "2.22.2"),
                         ("tools.jackson.core", "jackson-core", "3.2.2")])
    put_jar(repo, "lib/build/jackson/jackson-core-2.22.2.jar", b"no metadata")
    put_jar(repo, "lib/build/jackson/jackson-core-3.2.2.jar",
            jar_bytes("tools.jackson.core", "jackson-core", "3.2.2"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "AMBIGUOUS" in r.stdout


def test_a_lone_jar_still_pairs_by_artifact_id_even_under_a_different_group(repo):
    # No collision: keep the old artifactId-only pairing, so a relocated groupId cannot make a
    # real drift disappear into VENDORED-ONLY.
    make_pom(repo, deps=[("jakarta.mail", "mail", "2.0")])
    put_jar(repo, "lib/build/mail/mail-1.6.jar", jar_bytes("com.sun.mail", "mail", "1.6"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "pom 2.0" in r.stdout
