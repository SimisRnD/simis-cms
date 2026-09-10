"""check-server-hygiene.py: wildcard CORS origins and unallowlisted printStackTrace."""

from conftest import run_tool, write

TOOL = "check-server-hygiene.py"

JAVA_DIR = "src/main/java/com/simisinc/platform/rest/controller"


def java(body: str) -> str:
    return (
        "package com.simisinc.platform.rest.controller;\n\n"
        "public class Sample {\n" + body + "}\n"
    )


def test_clean_tree_passes_strict(repo):
    write(repo, f"{JAVA_DIR}/Sample.java", java('  void ok() { log("fine"); }\n'))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "no wildcard CORS origin" in r.stdout


def test_wildcard_cors_fails_strict(repo):
    write(repo, f"{JAVA_DIR}/Sample.java",
          java('  void cors() { r.addHeader("Access-Control-Allow-Origin", "*"); }\n'))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "wildcard Access-Control-Allow-Origin" in r.stdout


def test_wildcard_cors_via_setheader_and_single_quotes_also_caught(repo):
    write(repo, f"{JAVA_DIR}/Sample.java",
          java("  void cors() { r.setHeader('Access-Control-Allow-Origin', '*'); }\n"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1


def test_a_real_origin_is_not_flagged(repo):
    write(repo, f"{JAVA_DIR}/Sample.java",
          java('  void cors() { r.addHeader("Access-Control-Allow-Origin", siteUrl); }\n'))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_print_stack_trace_fails_strict(repo):
    write(repo, f"{JAVA_DIR}/Sample.java",
          java("  void oops(Exception e) { e.printStackTrace(); }\n"))
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "printStackTrace" in r.stdout


def test_allowlisted_print_stack_trace_passes(repo):
    # One of the seven files that already called it when the gate was written.
    write(repo,
          "src/main/java/com/simisinc/platform/presentation/controller/XMLPageLoader.java",
          "package com.simisinc.platform.presentation.controller;\n\n"
          "public class XMLPageLoader {\n  void oops(Exception e) { e.printStackTrace(); }\n}\n")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_findings_reported_but_exit_zero_without_strict(repo):
    write(repo, f"{JAVA_DIR}/Sample.java",
          java('  void cors() { r.addHeader("Access-Control-Allow-Origin", "*"); }\n'))
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "wildcard Access-Control-Allow-Origin" in r.stdout
