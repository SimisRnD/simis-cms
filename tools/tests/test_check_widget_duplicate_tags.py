"""check-widget-duplicate-tags.py: repeated preference tags inside one <widget>.

``addWidgetPreferences`` builds the preference map with ``Map.put``, so a tag written
twice in the same ``<widget>`` block silently keeps only the last value. Nothing in the
parse, the JSP compile, or the test run notices.

The exit codes carry the other half of the contract. A duplicate is exit 1 under
--strict; a layout directory that has been renamed out from under the check is exit 2.
Collapsing those two into one code would let a gate that can no longer find its input
land in CI looking exactly like a gate that found a real defect.
"""

from conftest import run_tool, write

TOOL = "check-widget-duplicate-tags.py"
LAYOUT = "src/main/webapp/WEB-INF/web-layouts/admin/dashboard-layout.xml"

CLEAN = """<?xml version="1.0" encoding="UTF-8"?>
<page>
  <section>
    <column>
      <widget name="content">
        <title>Reports</title>
        <icon>chart</icon>
      </widget>
    </column>
  </section>
</page>
"""

DUPLICATE = """<?xml version="1.0" encoding="UTF-8"?>
<page>
  <section>
    <column>
      <widget name="content">
        <title>Reports</title>
        <icon>chart</icon>
        <title>Orders</title>
      </widget>
    </column>
  </section>
</page>
"""


def test_clean_layout_passes_strict(repo):
    write(repo, LAYOUT, CLEAN)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "no duplicate direct-child preference tags" in r.stdout


def test_duplicate_tag_fails_strict(repo):
    write(repo, LAYOUT, DUPLICATE)
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "DUPLICATE" in r.stdout and "<title> appears 2 times" in r.stdout


def test_duplicate_tag_reported_but_exit_zero_without_strict(repo):
    write(repo, LAYOUT, DUPLICATE)
    r = run_tool(TOOL, repo)
    assert r.returncode == 0
    assert "DUPLICATE" in r.stdout


def test_repeated_child_one_level_deeper_is_not_a_duplicate(repo):
    # <field> entries inside one <fields> container are the intentional mechanism for
    # repeatable structured data, not the Map.put collision this tool looks for.
    write(repo, LAYOUT, """<?xml version="1.0" encoding="UTF-8"?>
<page><section><column>
  <widget name="form">
    <fields>
      <field name="email"/>
      <field name="name"/>
    </fields>
  </widget>
</column></section></page>
""")
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 0, r.stdout + r.stderr


def test_missing_layout_directory_exits_two(repo):
    # The reason this file exists. The layout tree is this check's entire input; if it
    # has moved, the check has measured nothing. Exit 2 says so, and -- crucially -- is
    # NOT the exit 1 that test_duplicate_tag_fails_strict pins to a real finding.
    r = run_tool(TOOL, repo, "--strict")
    assert r.returncode == 2, r.stdout + r.stderr
    assert "error:" in r.stderr
    assert "web-layouts" in r.stderr


def test_missing_layout_directory_exits_two_without_strict_too(repo):
    # Report-only mode still cannot report on a tree it cannot find.
    r = run_tool(TOOL, repo)
    assert r.returncode == 2, r.stdout + r.stderr
