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


def test_doubled_hyphen_in_rest_services_comment_fails_strict(repo):
    """The exact failure shape from issue #412: a doubled hyphen inside an XML
    comment in a rest-services file. XMLServiceLoader.parseDocument() throws on
    this at every startup, and the throw is swallowed by addFile() with no log
    line, so the whole file's service list silently never registers."""
    seed_all_good(repo)
    write(
        repo,
        "src/main/webapp/WEB-INF/rest-services/rest-services.xml",
        '<?xml version="1.0" ?>\n'
        "<services>\n"
        "  <!-- deviates from the spec -- see rationale below -->\n"
        '  <service method="get" endpoint="pages" class="com.example.PagesListService" />\n'
        "</services>\n",
    )
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "rest-services.xml" in (r.stdout + r.stderr)


def test_nested_web_template_glob_is_matched(repo):
    """web-templates has filenames with spaces (e.g. "Banner with Blocks.xml") one
    directory below a template category (page/cms/, page/portal/, ...); confirm the
    ** glob actually walks that nesting instead of silently matching zero files."""
    seed_all_good(repo)
    write(
        repo,
        "src/main/webapp/WEB-INF/web-templates/page/cms/Bio Page.xml",
        BAD,
    )
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1
    assert "Bio Page.xml" in (r.stdout + r.stderr)
