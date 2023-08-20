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
<jsp:useBean id="webContainer" class="com.simisinc.platform.domain.model.cms.WebContainer" scope="request"/>
<script src="${ctx}/javascript/ace-1.24.1/ace.js" type="text/javascript" charset="utf-8"></script>
<script src="${ctx}/javascript/ace-1.24.1/mode-xml.js" type="text/javascript" charset="utf-8"></script>
<%--<script src="${ctx}/javascript/ace-1.24.1/ext-language_tools.js" type="text/javascript" charset="utf-8"></script>--%>
<style>
  #containerXml {
    right: 0;
  }
  .ace_editor {
    border: 1px solid #ccc;
  }
</style>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%--<small><c:out value="${webPage.link}" /></small>--%>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="name" value="${webContainer.name}"/>
  <input type="hidden" name="returnPage" value="${returnPage}" />
  <%-- The editor --%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-9 cell">
      <p>
      <c:choose>
        <c:when test="${webContainer.draft}">
          <textarea id="containerXml" name="containerXml" data-gutter="1" rows="24" data-editor="xml"><c:out value="${webContainer.draftXml}"/></textarea>
        </c:when>
        <c:otherwise>
          <textarea id="containerXml" name="containerXml" data-gutter="1" rows="24" data-editor="xml"><c:out value="${webContainer.containerXml}"/></textarea>
        </c:otherwise>
      </c:choose>
      </p>
      <div class="button-container">
        <input type="submit" class="button radius success no-gap" value="Save"/>
        <c:if test="${!empty returnPage}">
          <a href="${returnPage}" class="button radius secondary no-gap">Cancel</a>
        </c:if>
      </div>
    </div>
    <div class="small-12 hide-for-small-only medium-3 cell">
      <div id="information" class="callout secondary" style="overflow:scroll">
        <h4>Widgets</h4>
        <dl>
          <dt>systemAlert</dt>
          <dd></dd>
          <dt>mainMenu</dt>
          <dd></dd>
          <dt>button</dt>
          <dd>name, link</dd>
          <dt>link</dt>
          <dd>name, link, class, property</dd>
          <dt>logo</dt>
          <dd>maxWidth, maxHeight, view=standard,color,white</dd>
          <dt>searchForm</dt>
          <dd>placeholder, expand=true</dd>
          <dt>content</dt>
          <dd>html</dd>
          <dt>emailSubscribe</dt>
          <dd>view=inline</dd>
          <dt>socialMediaLinks</dt>
          <dd></dd>
        </dl>
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
        height: textarea.height() - 40,
        'class': textarea.attr('class')
      }).insertBefore(textarea);
      textarea.css('display', 'none');
      ace.require("ace/ext/language_tools");
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
      editor.setTheme("ace/theme/eclipse");
      textarea.closest('form').submit(function() {
        textarea.val(editor.getSession().getValue());
      })
    });
  });
  $(document).ready(function () {
    function resizeEditor() {
      var container = document.getElementsByClassName("ace_editor")[0];
      var rect = container.getBoundingClientRect(),
        scrollLeft = window.pageXOffset || document.documentElement.scrollLeft,
        scrollTop = window.pageYOffset || document.documentElement.scrollTop;
      $('#information').height($(window).height() - Math.round(rect.top + scrollTop + 108));
      return $('.ace_editor').height($(window).height() - Math.round(rect.top + scrollTop + 75));
    }
    $(window).resize(resizeEditor);
    resizeEditor();
  });
</script>
