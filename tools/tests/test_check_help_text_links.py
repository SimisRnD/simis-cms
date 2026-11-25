"""check-help-text-links.py: each field's help must be tied to its field, in every template.

Only an aria-describedby on the input makes a screen reader announce a field's help.
The settings editor had 45 of 116 field help paragraphs without one, and its four
logo color pickers pointed at their help from a hidden input, which is never
announced, so they looked linked and weren't. 13 more turned up in 8 other forms.
"""

import contextlib
import importlib.util
import io
import os
import sys
from unittest import mock

from conftest import TOOLS_DIR, run_tool, write

TOOL = "check-help-text-links.py"
FORM = "src/main/webapp/WEB-INF/jsp/admin/example-form.jsp"  # not in the real BACKLOG
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


def load():
    spec = importlib.util.spec_from_file_location("chk", TOOLS_DIR / TOOL)
    chk = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(chk)
    return chk


def run_main(chk, repo):
    """main() with --strict against repo, returning (exit status, stdout)."""
    out = io.StringIO()
    with mock.patch.object(sys, "argv", [TOOL, str(repo), "--strict"]), \
            contextlib.redirect_stdout(out):
        status = chk.main()
    return status, out.getvalue()


def test_missing_template_root_is_an_error(repo):
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
    assert "line 1  help text has no id, so no field can point at it" in result.stdout


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
    assert "in 2 template(s)" in result.stdout


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
          '<input type="text" name="x">\n<p class="help-text">No id.</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "admin/third.jsp: line 2  help text right after a field has no id" in result.stdout
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


# --- Every template, not just the settings editor -------------------------------------------

def test_every_template_is_checked_not_just_the_settings_editor(repo):
    write(repo, FORM, INPUT.replace(" aria-describedby", " data-x") + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'admin/example-form.jsp: line 5  id="mailPortHelpText" has no aria-describedby' \
        in result.stdout


def test_help_right_after_an_input_needs_an_id(repo):
    write(repo, FORM, '<input type="text" name="fromPath">\n'
                      '<p class="help-text">Starts with a /.</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 2  help text right after a field has no id, so the field can't point at it" \
        in result.stdout


def test_help_right_after_a_label_needs_an_id(repo):
    # The label holds the input; the help follows it, past the grid's closing div.
    write(repo, FORM, '<div class="cell">\n'
                      '  <label>Enabled <input type="checkbox" name="enabled"></label>\n'
                      '</div>\n'
                      '<br/>\n'
                      '<small class="help-text">Disabled redirects are kept.</small>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 5  help text right after a field" in result.stdout


def test_help_after_a_select_or_textarea_needs_an_id(repo):
    write(repo, FORM, '<select name="a"><option>1</option></select>\n'
                      '<p class="help-text">A.</p>\n'
                      '<textarea name="b"></textarea>\n'
                      '<p class="help-text">B.</p>\n')
    result = check(repo, "--strict")
    assert "line 2  help text right after a field" in result.stdout
    assert "line 4  help text right after a field" in result.stdout


def test_a_second_paragraph_of_field_help_needs_an_id_too(repo):
    write(repo, FORM, '<input type="text" name="uniqueId" aria-describedby="uniqueIdHelpText">\n'
                      '<p class="help-text" id="uniqueIdHelpText">Leave blank to generate.</p>\n'
                      '<p class="help-text">Some access rules match on it.</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 3  help text right after a field" in result.stdout


def test_a_caption_is_not_field_help(repo):
    # List pages, dialogs and checkout use the class for captions; those need no id.
    write(repo, FORM, '<h4 id="bulkDeleteTitle">Delete 3 pages</h4>\n'
                      '<p class="help-text">This cannot be undone.</p>\n'
                      '<span class="help-text float-right"><a href="/cart">Edit</a></span>\n'
                      '<p>Some prose.</p>\n'
                      '<p class="help-text">A note on the prose.</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout


def test_help_after_a_button_or_a_hidden_input_is_not_field_help(repo):
    write(repo, FORM, '<input type="hidden" name="token" value="x">\n'
                      '<p class="help-text">After a hidden input only.</p>\n'
                      '<input type="text" name="password"><button type="button">Show</button>\n'
                      '<p class="help-text"><a href="/forgot-password">Forgot it?</a></p>\n'
                      '<input type="submit" value="Save">\n'
                      '<p class="help-text">Saving notifies subscribers.</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout


def test_help_inside_the_fields_label_is_already_part_of_its_name(repo):
    write(repo, FORM, '<label>Organizer\n'
                      '  <input type="text" name="organizerName">\n'
                      '  <p class="help-text">Who runs the event.</p>\n'
                      '</label>\n')
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout


def test_help_that_holds_its_own_controls_is_a_container_not_help(repo):
    write(repo, FORM, '<input type="text" name="email">\n'
                      '<p class="help-text">\n'
                      '  <label><input type="checkbox" value="1"> News</label>\n'
                      '</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout


def test_jsp_tags_between_the_field_and_its_help_dont_hide_it(repo):
    write(repo, FORM, '<input type="text" name="url">\n'
                      '<c:if test="${showHelp}">\n'
                      '  <p class="help-text">Signed POST.</p>\n'
                      '</c:if>\n')
    result = check(repo, "--strict")
    assert "line 3  help text right after a field" in result.stdout


# --- Ids built from expressions ------------------------------------------------------------

def test_an_id_built_from_an_expression_is_linked_by_the_same_expression(repo):
    # cms/form.jsp builds both halves from the widget and field name.
    write(repo, FORM,
          '<input type="file" aria-describedby="help-${widgetContext.uniqueId}<c:out value="${formField.name}"/>">\n'
          '<span id="help-${widgetContext.uniqueId}<c:out value="${formField.name}"/>" class="help-text">Max 5 MB.</span>\n')
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout


def test_an_expression_built_id_nothing_points_at_is_a_finding(repo):
    write(repo, FORM, '<input type="text" id="email${widgetContext.uniqueId}">\n'
                      '<p class="help-text" id="emailHelpText${widgetContext.uniqueId}"></p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'line 2  id="emailHelpText${widgetContext.uniqueId}" has no aria-describedby' \
        in result.stdout


def test_a_pointer_built_from_a_different_expression_is_a_finding(repo):
    write(repo, FORM, '<input type="text" aria-describedby="emailHelp${widgetContext.uniqueId}">\n'
                      '<p class="help-text" id="emailHelpText${widgetContext.uniqueId}"></p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'aria-describedby="emailHelp${widgetContext.uniqueId}" can\'t be resolved' in result.stdout
    assert 'id="emailHelpText${widgetContext.uniqueId}" has no aria-describedby' in result.stdout


def test_aria_labelledby_counts_as_a_link(repo):
    # A captcha prompt styled as help text labels its field.
    write(repo, FORM, '<p class="help-text" id="captchaPrompt">Enter the text you see:</p>\n'
                      '<input type="text" name="captcha" aria-labelledby="captchaPrompt">\n')
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout


def test_aria_labelledby_naming_a_missing_id_is_a_finding(repo):
    write(repo, FORM, '<input type="text" name="captcha" aria-labelledby="captchaPromt">\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'aria-labelledby="captchaPromt" names no element' in result.stdout


# --- Includes ------------------------------------------------------------------------------

def test_an_include_nobody_includes_is_read_on_its_own(repo):
    write(repo, "src/main/webapp/WEB-INF/jsp/cms/orphan.jspf", INPUT.replace(" aria-describedby", " data-x") + HELP)
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'cms/orphan.jspf: line 5  id="mailPortHelpText" has no aria-describedby' in result.stdout


def test_ids_in_the_including_page_resolve_pointers_in_the_include(repo):
    write(repo, FORM, '<%@ include file="part.jspf" %>\n' + HELP)
    write(repo, "src/main/webapp/WEB-INF/jsp/admin/part.jspf", INPUT)
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout


def test_the_layouts_page_body_include_is_expected(repo):
    write(repo, "src/main/webapp/WEB-INF/jsp/main.jsp",
          '<main><jsp:include page="${PageBody}" flush="true"/></main>\n')
    result = check(repo, "--strict")
    assert result.returncode == 0, result.stdout


def test_another_computed_include_in_the_layout_is_still_a_finding(repo):
    write(repo, "src/main/webapp/WEB-INF/jsp/main.jsp",
          '<jsp:include page="${PageBody}"/>\n<jsp:include page="${FooterBody}"/>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "main.jsp: line 2  include with a computed or missing path" in result.stdout


def test_a_layout_exemption_that_outlived_its_include_is_a_finding(repo):
    write(repo, "src/main/webapp/WEB-INF/jsp/main.jsp", '<main></main>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert 'LAYOUT_INCLUDES lists an include of "${PageBody}" that is no longer there' in result.stdout


# --- Backlog -------------------------------------------------------------------------------

def test_the_backlog_allows_its_recorded_count(repo):
    chk = load()
    write(repo, FORM, '<input type="text" name="a">\n<p class="help-text">A.</p>\n')
    with mock.patch.object(chk, "BACKLOG", {"admin/example-form.jsp": 1}):
        status, out = run_main(chk, repo)
    assert status == 0, out
    assert "(1 in the backlog)" in out


def test_one_above_the_backlog_fails(repo):
    chk = load()
    write(repo, FORM, '<input type="text" name="a">\n<p class="help-text">A.</p>\n'
                      '<input type="text" name="b">\n<p class="help-text">B.</p>\n')
    with mock.patch.object(chk, "BACKLOG", {"admin/example-form.jsp": 1}):
        status, out = run_main(chk, repo)
    assert status == 1
    assert "(backlog allows 1 in this file)" in out


def test_falling_below_the_backlog_is_a_note_not_a_failure(repo):
    chk = load()
    write(repo, FORM, '<input type="text" name="a">\n<p class="help-text">A.</p>\n')
    with mock.patch.object(chk, "BACKLOG", {"admin/example-form.jsp": 3}):
        status, out = run_main(chk, repo)
    assert status == 0, out
    assert "NOTE  backlog is now ahead of the recorded counts" in out
    assert "admin/example-form.jsp: 3 -> 1" in out


def test_broken_links_have_no_backlog(repo):
    chk = load()
    write(repo, FORM, '<input type="text" aria-describedby="missingHelp">\n')
    with mock.patch.object(chk, "BACKLOG", {"admin/example-form.jsp": 5}):
        status, out = run_main(chk, repo)
    assert status == 1
    assert 'aria-describedby="missingHelp" names no element' in out


def test_backlog_entries_all_still_exist():
    """Every recorded path must be real, so the backlog cannot rot into fiction."""
    chk = load()
    for rel in chk.BACKLOG:
        assert os.path.exists(os.path.join(TOOLS_DIR.parent, chk.JSP_ROOT, rel)), rel


def test_a_label_split_across_jsp_branches_does_not_hide_later_help(repo):
    # Once the JSP tags are blanked, two branches read as two <label>s and one </label>. Treating
    # the rest of the file as inside a label would excuse every help paragraph after it.
    write(repo, FORM, '<c:choose><c:when test="${a}"><label>One</c:when>\n'
                      '<c:otherwise><label class="x">Two</c:otherwise></c:choose>\n'
                      '<input type="text" name="a"></label>\n'
                      '<input type="text" name="b">\n'
                      '<p class="help-text">About b.</p>\n')
    result = check(repo, "--strict")
    assert result.returncode == 1
    assert "line 5  help text right after a field" in result.stdout
