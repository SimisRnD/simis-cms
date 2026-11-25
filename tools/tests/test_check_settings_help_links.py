"""check-settings-help-links.py: each settings field's help must be tied to its field.

The settings editor writes a field's help and the field's input in different places,
and only an aria-describedby on the input makes a screen reader announce the help.
45 of 116 field help paragraphs went without one, and the four logo color pickers
pointed at their help from a hidden input, which is never announced, so they looked
linked and weren't.
"""

from conftest import run_tool, write

TOOL = "check-settings-help-links.py"
EDITOR = "src/main/webapp/WEB-INF/jsp/admin/site-properties-editor.jsp"
PICKER = "src/main/webapp/WEB-INF/jsp/admin/logo-color-picker.jspf"

INPUT = (
    '<input aria-label="Port" type="text" name="${siteProperty.name}"\n'
    '    <c:if test="${siteProperty.name eq \'mail.port\'}"> aria-describedby="mailPortHelpText"</c:if>\n'
    '    />\n'
)
HELP = (
    '<c:if test="${siteProperty.name eq \'mail.port\'}">\n'
    '  <p class="help-text" id="mailPortHelpText">The SMTP port.</p>\n'
    '</c:if>\n'
)


def check(repo, *args):
    return run_tool(TOOL, repo, *args)


def test_missing_editor_is_an_error(repo):
    result = check(repo)
    assert result.returncode == 1
    assert "MISSING" in result.stderr


def test_linked_help_passes(repo):
    write(repo, EDITOR, INPUT + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout
    assert "OK" in result.stdout


def test_unlinked_help_is_a_finding(repo):
    write(repo, EDITOR,
          '<input aria-label="Port" type="text" name="mail.port" />\n' + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'line 3  id="mailPortHelpText" has no aria-describedby' in result.stdout


def test_non_strict_reports_but_exits_zero(repo):
    write(repo, EDITOR, '<input type="text" name="mail.port" />\n' + HELP)
    result = check(repo)
    assert result.returncode == 0
    assert "FAIL" in result.stdout


def test_help_with_no_id_is_a_finding(repo):
    write(repo, EDITOR, '<p class="help-text">Explains a field.</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 1  help text has no literal id" in result.stdout


def test_page_help_is_not_field_help(repo):
    write(repo, EDITOR, '<p class="help-text page-help">Explains the whole page.</p>\n')
    assert check(repo, "--strict").returncode == 0


def test_one_of_several_ids_in_a_list_counts(repo):
    write(repo, EDITOR,
          '<input type="text" name="x" aria-describedby="otherHelp mailPortHelpText" />\n'
          '<p id="otherHelp">Other.</p>\n' + HELP)
    assert check(repo, "--strict").returncode == 0


def test_a_hidden_input_does_not_link_and_is_a_finding(repo):
    write(repo, EDITOR,
          '<input type="hidden" name="mail.port" aria-describedby="mailPortHelpText" />\n'
          + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 1  aria-describedby on a hidden input" in result.stdout
    assert 'id="mailPortHelpText" has no aria-describedby' in result.stdout


def test_a_reference_to_a_missing_id_is_a_finding(repo):
    write(repo, EDITOR,
          '<input type="text" name="mail.port" aria-describedby="mailPortHelpTxt" />\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'aria-describedby="mailPortHelpTxt" names no element' in result.stdout


def test_a_variable_set_in_an_include_is_followed(repo):
    """logo-color-picker.jspf picks its help id with <c:set> and points at it with EL."""
    write(repo, EDITOR,
          '<%@include file="logo-color-picker.jspf" %>\n'
          '<p class="help-text" id="themeLogoColorHelpText">Header logo.</p>\n')
    write(repo, PICKER,
          '<c:set var="logoColorHelpTextId" value="themeLogoColorHelpText"/>\n'
          '<div role="group" aria-describedby="${logoColorHelpTextId}"></div>\n')
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout
    assert "2 file(s) checked" in result.stdout


def test_a_finding_in_an_include_names_the_include(repo):
    write(repo, EDITOR,
          '<%@include file="logo-color-picker.jspf" %>\n'
          '<p class="help-text" id="themeLogoColorHelpText">Header logo.</p>\n')
    write(repo, PICKER,
          '<c:set var="logoColorHelpTextId" value="themeLogoColorHelpText"/>\n'
          '<input type="hidden" name="theme.logo.color" aria-describedby="${logoColorHelpTextId}" />\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "admin/logo-color-picker.jspf: line 2  aria-describedby on a hidden input" in result.stdout


def test_a_comment_is_not_a_link(repo):
    write(repo, EDITOR,
          '<%-- e.g. aria-describedby="mailPortHelpText" --%>\n'
          '<!-- <input aria-describedby="mailPortHelpText"> -->\n'
          '<input type="text" name="mail.port" />\n' + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'id="mailPortHelpText" has no aria-describedby' in result.stdout


def test_a_script_body_is_not_a_link(repo):
    write(repo, EDITOR,
          '<script nonce="n">el.setAttribute("x", \'<input aria-describedby="mailPortHelpText">\');</script>\n'
          '<input type="text" name="mail.port" />\n' + HELP)
    assert check(repo, "--strict").returncode == 1


def test_a_tag_inside_an_attribute_value_does_not_end_the_element(repo):
    # value="<c:out value="..."/>" puts a ">" inside the input's opening tag, ahead of the
    # aria-describedby lines. Reading the tag only up to that ">" would drop the link.
    write(repo, EDITOR,
          '<input type="text" value="<c:out value="${siteProperty.value}"/>"\n'
          '    <c:if test="${siteProperty.name eq \'mail.port\'}"> aria-describedby="mailPortHelpText"</c:if>\n'
          '    />\n' + HELP)
    assert check(repo, "--strict").returncode == 0


def test_an_include_that_is_not_found_is_a_finding_not_a_skip(repo):
    """Skipping it would leave whatever that file renders unchecked, and pass."""
    write(repo, EDITOR, '<%@ include file="missing-picker.jspf" %>\n' + INPUT + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'line 1  included file "missing-picker.jspf" not found' in result.stdout


def test_an_include_with_a_computed_path_is_a_finding(repo):
    write(repo, EDITOR, '<jsp:include page="${pickerPage}" />\n' + INPUT + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 1  include with a computed or missing path" in result.stdout


def test_single_quoted_and_other_include_forms_are_followed(repo):
    write(repo, EDITOR,
          "<%@include file='logo-color-picker.jspf' %>\n"
          '<jsp:directive.include file="/WEB-INF/jsp/admin/other.jspf"/>\n'
          '<jsp:include page="third.jsp" />\n')
    write(repo, PICKER, '<p class="help-text" id="a">A.</p><div aria-describedby="a"></div>\n')
    write(repo, "src/main/webapp/WEB-INF/jsp/admin/other.jspf",
          '<p class="help-text" id="b">B.</p><div aria-describedby="b"></div>\n')
    write(repo, "src/main/webapp/WEB-INF/jsp/admin/third.jsp",
          '<p class="help-text">No id.</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "admin/third.jsp: line 1  help text has no literal id" in result.stdout
    assert result.stdout.count("  admin/") == 1


def test_a_single_quoted_hidden_input_is_still_hidden(repo):
    write(repo, EDITOR,
          "<input type='hidden' name='mail.port' aria-describedby='mailPortHelpText' />\n"
          + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 1  aria-describedby on a hidden input" in result.stdout


def test_an_unquoted_hidden_input_is_still_hidden(repo):
    write(repo, EDITOR,
          '<input type=hidden name=mail.port aria-describedby=mailPortHelpText>\n' + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 1  aria-describedby on a hidden input" in result.stdout


def test_single_quoted_help_and_link_are_read(repo):
    write(repo, EDITOR,
          "<input type='text' aria-describedby='portHelp' />\n"
          "<p class='help-text' id='portHelp'>Port.</p>\n"
          "<p class=help-text id=otherHelp>Other.</p>\n")
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'line 3  id="otherHelp" has no aria-describedby' in result.stdout
    assert "portHelp" not in result.stdout


def test_a_variable_that_is_never_set_is_a_finding(repo):
    write(repo, EDITOR,
          '<div role="group" aria-describedby="${logoColorHelpTextId}"></div>\n'
          '<p class="help-text" id="themeLogoColorHelpText">Header logo.</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'line 1  aria-describedby="${logoColorHelpTextId}" can\'t be resolved' in result.stdout


def test_a_variable_set_from_an_expression_is_a_finding(repo):
    write(repo, EDITOR,
          '<c:set var="helpId" value="${siteProperty.name}HelpText"/>\n'
          '<input type="text" aria-describedby="${helpId}" />\n' + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'line 2  aria-describedby="${helpId}" can\'t be resolved' in result.stdout


def test_an_expression_inside_a_list_is_a_finding(repo):
    write(repo, EDITOR,
          '<input type="text" aria-describedby="mailPortHelpText ${extraHelp}" />\n' + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "can't be resolved to an id" in result.stdout


def test_other_c_set_forms_are_followed(repo):
    write(repo, EDITOR,
          "<c:set value='mailPortHelpText' var='helpId'/>\n"
          '<c:set var="otherId">otherHelpText</c:set>\n'
          '<input type="text" aria-describedby="${helpId}" />\n'
          '<input type="text" aria-describedby="${otherId}" />\n'
          + HELP + '<p class="help-text" id="otherHelpText">Other.</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout


def test_repository_is_clean():
    """The real tree must pass, which is what CI runs."""
    from conftest import TOOLS_DIR
    result = run_tool(TOOL, TOOLS_DIR.parent, "--strict")
    assert result.returncode == 0, result.stdout
