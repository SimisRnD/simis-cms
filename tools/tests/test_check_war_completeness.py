"""check-war-completeness.py: the exit codes that tell a broken run from a finding.

The exit-1 path (a non-allowlisted missing class under --strict) needs a real WAR and a
real jdeps, so CI's own war-completeness job covers it. What is cheap to cover, and what
this file covers, is the opposite half: the three ways the check can fail to run at all.

That half is the one with history. An earlier per-jar draft of this script let jdeps
ABORT and reported zero missing classes for a WAR that was provably broken. The class
tree keeps jdeps out of module mode, and these tests keep every remaining
cannot-run-at-all path on exit 2, where it can never be read as either a clean WAR
(exit 0) or a real finding (exit 1).
"""

import os
import subprocess
import sys
import zipfile

from conftest import TOOLS_DIR

TOOL = "check-war-completeness.py"


def run_war_tool(war, *args, java_home=None):
    """Drive the tool through its real CLI. It takes --war, not a positional root,
    so conftest.run_tool's signature does not fit."""
    env = dict(os.environ)
    if java_home is not None:
        env["JAVA_HOME"] = str(java_home)
    return subprocess.run(
        [sys.executable, str(TOOLS_DIR / TOOL), "--war", str(war), *args],
        capture_output=True, text=True, env=env,
    )


def make_war(path, jars=("dummy-1.0.jar",)):
    """A minimal WAR. Each named jar is a real (if empty) zip under WEB-INF/lib/."""
    with zipfile.ZipFile(path, "w") as w:
        w.writestr("WEB-INF/web.xml", "<web-app/>")
        for name in jars:
            inner = path.parent / name
            with zipfile.ZipFile(inner, "w") as j:
                j.writestr("com/example/Thing.class", "\xca\xfe\xba\xbe")
            w.write(inner, "WEB-INF/lib/" + name)
            inner.unlink()
    return path


def test_missing_war_exits_two(repo):
    r = run_war_tool(repo / "target" / "simis-cms.war", "--strict")
    assert r.returncode == 2, r.stdout + r.stderr
    assert "error:" in r.stderr
    assert "ant -lib lib/war package" in r.stderr


def test_war_with_no_bundled_jars_exits_two(repo):
    war = repo / "empty.war"
    make_war(war, jars=())
    r = run_war_tool(war, "--strict")
    assert r.returncode == 2, r.stdout + r.stderr
    assert "no WEB-INF/lib/*.jar entries" in r.stderr


def test_unusable_jdeps_exits_two(repo):
    # A JAVA_HOME with no bin/jdeps -- a JRE, or a stale path. The analysis cannot run,
    # so the answer is "broken" (2), never "clean" (0).
    war = repo / "app.war"
    make_war(war)
    jre = repo / "fake-jre"
    jre.mkdir()
    r = run_war_tool(war, "--strict", java_home=jre)
    assert r.returncode == 2, r.stdout + r.stderr
    assert "jdeps not found" in r.stderr


def test_report_only_mode_does_not_soften_a_broken_run(repo):
    # Without --strict a finding is exit 0. An unrunnable check is still exit 2.
    war = repo / "app.war"
    make_war(war)
    jre = repo / "fake-jre"
    jre.mkdir()
    r = run_war_tool(war, java_home=jre)
    assert r.returncode == 2, r.stdout + r.stderr
