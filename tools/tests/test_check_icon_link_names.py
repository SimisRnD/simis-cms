"""check-icon-link-names.py: icon-only controls must carry an accessible name.

A link or button holding nothing but a Font Awesome glyph announces as "link" and
nothing more. The public site is clean, so this only ever showed up signed in: the
homepage reaches 57 named controls signed out and 103 signed in, 35 of them
nameless. Auditing the public pages would never have found it, which is why the
count has to come from a tool rather than a grep -- the hand-grep that opened
issue 1962 over-counted by including commented-out markup and under-counted by
missing every <button> and every control with a JSP tag in its attributes.
"""

from conftest import run_tool, write

TOOL = "check-icon-link-names.py"
JSP = "src/main/webapp/WEB-INF/jsp/cms/widget.jsp"


def check(repo, *args):
    return run_tool(TOOL, repo, *args)


def test_missing_root_is_an_error(repo):
    result = check(repo)
    assert result.returncode == 1
    assert "MISSING" in result.stderr


def test_nameless_icon_link_is_a_finding(repo):
    write(repo, JSP, '<a href="/x"><i class="fa fa-edit"></i></a>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "cms/widget.jsp: line 1" in result.stdout


def test_non_strict_reports_but_exits_zero(repo):
    write(repo, JSP, '<a href="/x"><i class="fa fa-edit"></i></a>\n')
    result = check(repo)
    assert result.returncode == 0
    assert "FAIL" in result.stdout


def test_aria_label_is_a_name(repo):
    write(repo, JSP, '<a aria-label="Edit" href="/x"><i class="fa fa-edit"></i></a>\n')
    assert check(repo, "--strict").returncode == 0


def test_aria_labelledby_is_a_name(repo):
    write(repo, JSP, '<a aria-labelledby="t" href="/x"><i class="fa fa-edit"></i></a>\n')
    assert check(repo, "--strict").returncode == 0


def test_title_counts_as_a_name_deliberately(repo):
    """A title satisfies 4.1.2 even though it never appears on keyboard focus.

    Treating it as a failure would conflate a Level A gap with a quality issue, so
    the 33 controls named only this way are left alone on purpose.
    """
    write(repo, JSP, '<a title="Edit" href="/x"><i class="fa fa-edit"></i></a>\n')
    assert check(repo, "--strict").returncode == 0


def test_empty_name_attribute_is_still_nameless(repo):
    write(repo, JSP, '<a aria-label="" href="/x"><i class="fa fa-edit"></i></a>\n')
    assert check(repo, "--strict").returncode == 1


def test_screen_reader_span_is_a_name(repo):
    write(repo, JSP,
          '<a href="/x"><i class="fa fa-edit"></i><span class="show-for-sr">Edit</span></a>\n')
    assert check(repo, "--strict").returncode == 0


def test_nested_img_alt_is_a_name(repo):
    write(repo, JSP, '<a href="/x"><img src="a.png" alt="Edit"></a>\n')
    assert check(repo, "--strict").returncode == 0


def test_visible_text_is_a_name(repo):
    write(repo, JSP, '<a href="/x"><i class="fa fa-edit"></i> Edit</a>\n')
    assert check(repo, "--strict").returncode == 0


def test_buttons_are_checked_too(repo):
    """The opening grep only looked at <a>, which is how the site search button and
    the reveal trigger were both missed."""
    write(repo, JSP, '<button type="submit"><i class="fa fa-search"></i></button>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 1" in result.stdout


def test_svg_only_control_is_a_finding(repo):
    write(repo, JSP, '<a href="/x"><svg viewBox="0 0 1 1"><path d="M0 0"/></svg></a>\n')
    assert check(repo, "--strict").returncode == 1


def test_jsp_comment_is_not_a_finding(repo):
    """Nine of the grep's 64 were commented-out markup."""
    write(repo, JSP, '<%--<a href="/x"><i class="fa fa-edit"></i></a>--%>\n')
    assert check(repo, "--strict").returncode == 0


def test_html_comment_is_not_a_finding(repo):
    write(repo, JSP, '<!--<a href="/x"><i class="fa fa-edit"></i></a>-->\n')
    assert check(repo, "--strict").returncode == 0


def test_markup_built_inside_script_is_not_a_finding(repo):
    write(repo, JSP,
          '<script nonce="n">el.innerHTML = \'<a href="#"><i class="fa fa-edit"></i></a>\';</script>\n')
    assert check(repo, "--strict").returncode == 0


def test_jsp_tag_inside_an_attribute_does_not_hide_the_control(repo):
    """layout-header-renderer.jspf builds its href with <c:out/> mid-string. A naive
    [^>]* ends the opening tag at that tag's '>' and the control vanishes from the
    report entirely -- a silent miss, not a visible error."""
    write(repo, JSP,
          '<a class="b" href="/admin/x?name=<c:out value="${h.name}" />&amp;r=${p}">'
          '<i class="fa fa-code"></i></a>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 1" in result.stdout


def test_output_producing_tag_counts_as_text(repo):
    """<c:out> emits a name at render time, so the control is not icon-only."""
    write(repo, JSP, '<a href="/x"><i class="fa fa-edit"></i><c:out value="${n}"/></a>\n')
    assert check(repo, "--strict").returncode == 0


def test_structural_tag_around_the_icon_stays_transparent(repo):
    """A <c:if> wrapper emits nothing itself, so it must not be mistaken for text and
    mask a nameless control."""
    write(repo, JSP,
          '<a href="/x"><c:if test="${t}"><i class="fa fa-edit"></i></c:if></a>\n')
    assert check(repo, "--strict").returncode == 1


def test_line_numbers_survive_blanking(repo):
    write(repo, JSP,
          '<%--\n  a comment\n  spanning lines\n--%>\n<a href="/x"><i class="fa fa-x"></i></a>\n')
    result = check(repo, "--strict")
    assert "line 5" in result.stdout


def test_multiline_control_is_matched(repo):
    write(repo, JSP, '<a\n   class="b"\n   href="/x">\n  <i class="fa fa-edit"></i>\n</a>\n')
    assert check(repo, "--strict").returncode == 1


def test_jspf_files_are_scanned(repo):
    write(repo, "src/main/webapp/WEB-INF/jsp/layout-x.jspf",
          '<a href="/x"><i class="fa fa-code"></i></a>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "layout-x.jspf" in result.stdout


def test_backlog_allows_a_recorded_count(repo, monkeypatch):
    """A file recorded in BACKLOG at its current count does not fail."""
    write(repo, "src/main/webapp/WEB-INF/jsp/admin/list.jsp",
          '<a href="/x"><i class="fa fa-edit"></i></a>\n')
    # exercised through the real module so the CLI contract is what is tested
    import importlib.util
    from conftest import TOOLS_DIR
    spec = importlib.util.spec_from_file_location("chk", TOOLS_DIR / TOOL)
    chk = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(chk)
    found = chk.scan_tree(str(repo))
    assert found == {"admin/list.jsp": [1]}


def test_backlog_entries_all_still_exist(repo):
    """Every recorded path must be real, so the backlog cannot rot into fiction."""
    import importlib.util
    import os
    from conftest import TOOLS_DIR
    spec = importlib.util.spec_from_file_location("chk", TOOLS_DIR / TOOL)
    chk = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(chk)
    root = TOOLS_DIR.parent
    for rel in chk.BACKLOG:
        assert os.path.exists(os.path.join(root, chk.JSP_ROOT, rel)), rel


def test_repository_is_clean_outside_the_backlog():
    """The real tree must pass, which is what CI runs."""
    import importlib.util
    from conftest import TOOLS_DIR
    spec = importlib.util.spec_from_file_location("chk", TOOLS_DIR / TOOL)
    chk = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(chk)
    found = chk.scan_tree(str(TOOLS_DIR.parent))
    over = {r: h for r, h in found.items() if len(h) > chk.BACKLOG.get(r, 0)}
    assert over == {}
