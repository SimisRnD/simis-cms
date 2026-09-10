<%--
  ~ Copyright 2022 SimIS Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="wiki" class="com.simisinc.platform.domain.model.cms.Wiki" scope="request"/>
<jsp:useBean id="wikiPage" class="com.simisinc.platform.domain.model.cms.WikiPage" scope="request"/>
<jsp:useBean id="content" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/ace-1.32.0/ace.js" type="text/javascript" charset="utf-8"></script>
<script src="${ctx}/javascript/ace-1.32.0/mode-xml.js" type="text/javascript" charset="utf-8"></script>
<%--<script src="${ctx}/javascript/ace-1.32.0/ext-language_tools.js" type="text/javascript" charset="utf-8"></script>--%>
<style>
  #pageXml {
    right: 0;
  }
</style>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:choose>
  <c:when test="${wikiPage.id eq -1}">
    <h3>Create new page</h3>
  </c:when>
  <c:otherwise>
    <h3>Editing <c:out value="${wikiPage.title}" /></h3>
  </c:otherwise>
</c:choose>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="wikiUniqueId" value="<c:out value="${wiki.uniqueId}" />"/>
  <%-- wikiPageId (not pageUniqueId) is authoritative for save: it is the numeric id of the exact
       record this editor was actually opened against (-1 for a genuinely new page), set
       server-side by WikiEditorWidget.execute(). A client-typed title/slug is never trusted to
       decide which page gets saved -- see WikiEditorWidget.post() for why. --%>
  <input type="hidden" name="wikiPageId" value="${wikiPage.id}"/>
  <input type="hidden" name="returnPage" value="${returnPage}" />
  <%-- The editor --%>
  <%--<c:if test="${wikiPage.id ne -1}">--%>
    <input type="text" name="title" value="<c:out value="${wikiPage.title}" />">
  <%--</c:if>--%>
  <%--<hr />--%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-9 cell">
      <p>
        <textarea id="content" name="content" data-gutter="1" rows="24" data-editor="markdown"><c:out value="${content}"/></textarea>
      </p>
      <div class="button-container">
        <input type="submit" class="button radius success" value="Save"/>
        <button type="button" id="wikiPreviewToggle" class="button radius hollow">Preview</button>
        <c:choose>
          <c:when test="${!empty returnPage}">
            <a href="${returnPage}" class="button radius secondary">Cancel</a>
          </c:when>
          <c:otherwise>

          </c:otherwise>
        </c:choose>
      </div>
    </div>
    <div class="small-12 hide-for-small-only medium-3 cell">
      <ul class="tabs" data-tabs id="wikiEditorSideTabs">
        <li class="tabs-title is-active"><a href="#wikiHelpPanel" aria-selected="true">Help</a></li>
        <li class="tabs-title"><a href="#wikiPreviewPanel">Preview</a></li>
      </ul>
      <div class="tabs-content" data-tabs-content="wikiEditorSideTabs">
        <div class="tabs-panel is-active" id="wikiHelpPanel">
          <div class="callout secondary" style="height:65vh;overflow:scroll">
            <%-- https, and rel on a target=_blank: commonmark.org 301s http to https anyway, and an
                 opener-less new tab cannot reach back into this editor via window.opener. --%>
            <p><a target="_blank" rel="noopener noreferrer" href="https://commonmark.org/help">CommonMark Help</a>
              &mdash; the markdown syntax this editor uses</p>
            # Title<br />
            <br />
            ## List of things<br />
            * Bullet 1<br />
            * Bullet 2<br />
            <br />
            ## Numbered list of things<br />
            1. Item 1<br />
            2. Item 2<br />
            <br />
            [[Link to another page]]<br />
            [External web link](http://www.example.com)<br />
            <br />
            **Bold** and _italicized_ text<br />
            ~~Strikethrough text~~<br />
            <br />
            ```javascript<br />
            var text = "";<br />
            var text2 = "";<br />
            ```<br />
            <br />
            | Header | Header |<br />
            |--------|--------|<br />
            | Cell   | Cell   |
          </div>
        </div>
        <div class="tabs-panel" id="wikiPreviewPanel">
          <div class="callout secondary markdown-body" id="wikiPreviewContent" style="height:65vh;overflow:scroll">
            <em>Click Preview to render the current content.</em>
          </div>
        </div>
      </div>
    </div>
  </div>
</form>
<script nonce="${cspNonce}">
  // Hook up ACE editor to all textareas with data-editor attribute
  $(function() {
    $('textarea[data-editor]').each(function() {
      var textarea = $(this);
      var mode = textarea.data('editor');
      var editDiv = $('<div>', {
        // position: 'absolute',
        // right: 0,
        height: textarea.height() - 65,
        'class': textarea.attr('class')
      }).insertBefore(textarea);
      textarea.css('display', 'none');
      // ace.require("ace/ext/language_tools");
      var editor = ace.edit(editDiv[0]);
      editor.renderer.setShowGutter(textarea.data('gutter'));
      editor.setOptions({
        showPrintMargin: false,
        tabSize: 2,
        useSoftTabs: true
      });
      editor.getSession().setValue(textarea.val());
      editor.getSession().setMode("ace/mode/" + mode);
      editor.getSession().setUseWrapMode(true);
      editor.setTheme("ace/theme/github");
      textarea.closest('form').submit(function() {
        textarea.val(editor.getSession().getValue());
      });
      editor.focus();

      // Preview: render the editor's current (unsaved) buffer through the same server-side
      // markdown path the live page uses, via the widget action framework. Submitted as a real
      // POST body (not a GET query string) since a long page's content can otherwise silently
      // exceed a typical servlet-container/proxy request-line-length limit -- request.getParameter()
      // reads POST body form params the same as a query string, so this is purely a transport
      // change; see WikiEditorWidget.post()'s delegation to action() for the one Java-side change
      // it required (a real POST is dispatched to post(), not action() -- see WebContainerContext).
      // "widget" (which widget on the page the action targets) and "token" (the CSRF form token,
      // checked uniformly for every targeted request) are still required or PageServlet 404s
      // before dispatch.
      if (textarea.attr('id') === 'content') {
        $('#wikiPreviewToggle').on('click', function() {
          var previewPanel = $('#wikiPreviewContent');
          previewPanel.html('<em>Rendering&#8230;</em>');
          $('a[href="#wikiPreviewPanel"]').trigger('click');
          var body = new URLSearchParams();
          body.append('action', 'preview');
          body.append('widget', '${widgetContext.uniqueId}');
          body.append('token', '${userSession.formToken}');
          body.append('wikiUniqueId', '${js:escape(wiki.uniqueId)}');
          body.append('content', editor.getSession().getValue());
          fetch('${widgetContext.uri}', { method: 'POST', body: body })
            .then(function(resp) { return resp.json(); })
            .then(function(data) {
              if (data.error) {
                previewPanel.html('<em>' + data.error + '</em>');
                return;
              }
              previewPanel.html(data.html);
            })
            .catch(function() {
              previewPanel.html('<em>Preview failed to load.</em>');
            });
        });
      }
    });
  });
</script>
