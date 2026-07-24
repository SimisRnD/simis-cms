"""check-xml-well-formed.py: the fixed list of build-critical XML files."""

from conftest import run_tool, write

TOOL = "check-xml-well-formed.py"

GOOD = '<?xml version="1.0" encoding="UTF-8"?>\n<root><child/></root>\n'
BAD = '<?xml version="1.0" encoding="UTF-8"?>\n<root><child></root>\n'  # mismatched tag


def seed_all_good(repo):
    for rel in (
        "docker/app/conf/server.xml",
        "src/main/webapp/META-INF/context.xml",
        "src/main/webapp/WEB-INF/web.xml",
    ):
        write(repo, rel, GOOD)


def test_well_formed_files_pass_strict(repo):
    seed_all_good(repo)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_malformed_file_fails_strict(repo):
    seed_all_good(repo)
    write(repo, "src/main/webapp/META-INF/context.xml", BAD)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "context.xml" in (r.stdout + r.stderr)
