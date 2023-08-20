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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="wiki" class="com.simisinc.platform.domain.model.cms.Wiki" scope="request"/>
<jsp:useBean id="wikiPage" class="com.simisinc.platform.domain.model.cms.WikiPage" scope="request"/>
<jsp:useBean id="content" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/ace-1.24.1/ace.js" type="text/javascript" charset="utf-8"></script>
<script src="${ctx}/javascript/ace-1.24.1/mode-xml.js" type="text/javascript" charset="utf-8"></script>
<%--<script src="${ctx}/javascript/ace-1.24.1/ext-language_tools.js" type="text/javascript" charset="utf-8"></script>--%>
<style>
  #pageXml {
    right: 0;
  }
</style>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
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
  <input type="hidden" name="pageUniqueId" value="<c:out value="${wikiPage.uniqueId}" />"/>
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
      <div class="callout secondary" style="height:65vh;overflow:scroll">
        <p><a target="_blank" href="http://commonmark.org/help">CommonMark Help</a></p>
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
  </div>
</form>
<script>
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
    });
  });
</script>
