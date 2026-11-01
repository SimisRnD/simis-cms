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


# --- test scope: pom <scope>test</scope> declarations against the vendored lib/tests jars ---
#
# Ant runs the suite from lib/tests, so a Dependabot bump to a test dependency changes the pom and
# nothing else. These findings are reported but never fail --strict: the tree carried several when
# the section was added, and vendor-sync.py does not re-vendor lib/tests. Report first, reconcile,
# then flip -- the same sequence the JS drift check follows.

import os
import subprocess
import sys

from conftest import TOOLS_DIR


def test_test_scope_drift_is_reported_but_does_not_fail_strict(repo):
    make_pom(repo, deps=[("org.mockito", "mockito-core", "5.24.0", "test")])
    write(repo, "lib/tests/mockito-core-5.7.0.jar", "jar bytes")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "TEST SCOPE" in r.stdout
    assert "mockito-core" in r.stdout and "pom 5.24.0" in r.stdout and "tests run 5.7.0" in r.stdout


def test_lib_build_drift_still_fails_strict_beside_test_scope_drift(repo):
    make_pom(repo, deps=[("org.example", "widget", "2.0.0"),
                         ("org.mockito", "mockito-core", "5.24.0", "test")])
    write(repo, "lib/build/widget-1.2.3.jar", "jar bytes")
    write(repo, "lib/tests/mockito-core-5.7.0.jar", "jar bytes")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "WAR ships 1.2.3" in r.stdout and "tests run 5.7.0" in r.stdout
    assert "1 un-allowlisted drift(s)" in r.stderr


def test_a_matching_test_jar_is_not_drift(repo):
    make_pom(repo, deps=[("org.testcontainers", "testcontainers", "1.21.4", "test")])
    write(repo, "lib/tests/testcontainers-1.21.4.jar", "jar bytes")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "Test scope (report-only): 0 drifted" in r.stdout


def test_test_jars_are_not_paired_with_compile_declarations(repo):
    # A lib/tests jar must not be read as the shipped copy of a compile dependency.
    make_pom(repo, deps=[("org.example", "widget", "2.0.0")])
    write(repo, "lib/build/widget-2.0.0.jar", "jar bytes")
    write(repo, "lib/tests/widget-1.0.0.jar", "jar bytes")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "tests run 1.0.0" not in r.stdout


def test_without_a_lib_tests_directory_the_section_says_so(repo):
    make_pom(repo, deps=[("org.mockito", "mockito-core", "5.24.0", "test")])
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "no lib/tests directory" in r.stdout


def test_a_test_jar_shared_by_two_groups_is_ambiguous_but_does_not_fail_strict(repo):
    # JUnit's junit-jupiter and Testcontainers' junit-jupiter share an artifactId, and the
    # vendored Testcontainers jar records no groupId -- exactly the case in the tree today.
    make_pom(repo, deps=[("org.junit.jupiter", "junit-jupiter", "6.1.3", "test"),
                         ("org.testcontainers", "junit-jupiter", "1.21.4", "test")])
    write(repo, "lib/tests/junit-jupiter-1.21.4.jar", "no metadata")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "junit-jupiter-1.21.4.jar" in r.stdout
    # Both declarations are listed as lacking a jar, and point back at the ambiguity.
    assert "junit-jupiter (org.junit.jupiter) 6.1.3" in r.stdout
    assert "see ambiguous" in r.stdout


def test_step_summary_reports_test_scope_drift_and_states_enforcement(repo, tmp_path):
    make_pom(repo, deps=[("org.example", "widget", "2.0.0"),
                         ("org.mockito", "mockito-core", "5.24.0", "test")])
    write(repo, "lib/build/widget-1.2.3.jar", "jar bytes")
    write(repo, "lib/tests/mockito-core-5.7.0.jar", "jar bytes")
    summary = tmp_path / "summary.md"
    env = {**os.environ, "GITHUB_STEP_SUMMARY": str(summary)}
    subprocess.run([sys.executable, str(TOOLS_DIR / TOOL), str(repo), "--strict"],
                   capture_output=True, text=True, env=env)
    text = summary.read_text()
    assert "fails the build" in text and "does not fail the build" not in text.split("###")[0]
    assert "| `mockito-core` | 5.24.0 | **5.7.0** |" in text
