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
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="json" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/ace-1.32.0/ace.js" type="text/javascript" charset="utf-8"></script>
<script src="${ctx}/javascript/ace-1.32.0/mode-json.js" type="text/javascript" charset="utf-8"></script>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form specific --%>
  <input type="hidden" name="collectionId" value="${collection.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- The editor --%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-9 cell">
      <p>
        <textarea name="json" data-gutter="1" rows="24" data-editor="json"><c:out value="${json}"/></textarea>
      </p>
    </div>
    <div class="small-12 hide-for-small-only medium-3 cell">
      <div id="information" class="callout secondary" style="overflow:scroll">
        <h4>JSON Array</h4>
        <h4>Custom Fields</h4>
        <dl>
          <dt>label</dt>
          <dd>display label</dd>
          <dt>name</dt>
          <dd>form name</dd>
          <dt>type</dt>
          <dd>text, textarea, html, list</dd>
          <dt>list</dt>
          <dt>comma-separated values or name=value</dt>
        </dl>
      </div>
    </div>
  </div>
  <p><input type="submit" class="button radius success" value="Save"/></p>
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
      // $('#information').height($(window).height() - Math.round(rect.top + scrollTop + 108));
      return $('.ace_editor').height($(window).height() - Math.round(rect.top + scrollTop + 75));
    }
    $(window).resize(resizeEditor);
    resizeEditor();
  });
</script>