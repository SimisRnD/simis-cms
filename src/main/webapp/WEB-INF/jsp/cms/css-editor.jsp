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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="stylesheet" class="com.simisinc.platform.domain.model.cms.Stylesheet" scope="request"/>
<script src="${ctx}/javascript/ace-1.4.12/ace.js" type="text/javascript" charset="utf-8"></script>
<script src="${ctx}/javascript/ace-1.4.12/mode-css.js" type="text/javascript" charset="utf-8"></script>
<%--<script src="${ctx}/javascript/ace-1.4.12/ext-language_tools.js" type="text/javascript" charset="utf-8"></script>--%>
<style>
  #stylesheetCss {
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
  <input type="hidden" name="id" value="${stylesheet.id}"/>
  <input type="hidden" name="webPageId" value="${stylesheet.webPageId}"/>
  <input type="hidden" name="returnPage" value="${returnPage}" />
  <%-- The editor --%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-7 cell">
      <p>
        <textarea id="stylesheetCss" name="css" data-gutter="1" rows="24" data-editor="css"><c:out value="${stylesheet.css}"/></textarea>
      </p>
      <p class="no-gap">
        <input type="submit" class="button radius success no-gap" value="Save"/>
        <c:if test="${!empty returnPage}">
          <a href="${returnPage}" class="button radius secondary no-gap">Cancel</a>
        </c:if>
      </p>
    </div>
    <div class="small-12 hide-for-small-only medium-4 cell">
      <div id="information" style="overflow:scroll;padding-right:20px">
        <h4>Typography</h4>
        <h1>H1 Header</h1>
        <h2>H2 Header</h2>
        <h3>H3 Header</h3>
        <h4>H4 Header</h4>
        <h5>H5 Header</h5>
        <p class="no-gap">This is body text and there is also <strong>bold text</strong>.</p>
        <hr />
        <h4>Reference Elements</h4>
        <div class="callout">Callout</div>
        <div class="primary callout">Primary Callout</div>
        <div class="secondary callout">Secondary Callout</div>
        <div class="success callout">Success Callout</div>
        <div class="warning callout">Warning Callout</div>
        <div class="alert callout">Alert Callout</div>
        <div>
          <a class="button" href="#">Default</a>
          <a class="button primary" href="#">Primary</a>
          <a class="button secondary" href="#">Secondary</a>
          <a class="button success" href="#">Success</a>
          <a class="button alert" href="#">Alert</a>
          <a class="button warning" href="#">Warning</a>
          <a class="button call-to-action" href="#">Call to Action</a>
        </div>
        <div>
          <span class="label">Default Label</span>
          <span class="primary label">Primary Label</span>
          <span class="secondary label">Secondary Label</span>
          <span class="success label">Success Label</span>
          <span class="alert label">Alert Label</span>
          <span class="warning label">Warning Label</span>
        </div>
        <div>
          <span class="primary badge">1</span>
          <span class="secondary badge">2</span>
          <span class="success badge">3</span>
          <span class="alert badge">A</span>
          <span class="warning badge">B</span>
        </div>
        <div class="primary progress" role="progressbar" tabindex="0" aria-valuenow="25" aria-valuemin="0" aria-valuetext="25 percent" aria-valuemax="100">
          <div class="progress-meter" style="width: 25%">
            <p class="progress-meter-text">25%</p>
          </div>
        </div>
        <div class="warning progress">
          <div class="progress-meter" style="width: 50%">
            <p class="progress-meter-text">50%</p>
          </div>
        </div>
        <div class="alert progress">
          <div class="progress-meter" style="width: 75%">
            <p class="progress-meter-text">75%</p>
          </div>
        </div>
        <div class="success progress" role="progressbar" tabindex="0" aria-valuenow="100" aria-valuemin="0" aria-valuetext="100 percent" aria-valuemax="100">
          <div class="progress-meter" style="width: 100%">
            <p class="progress-meter-text">100%</p>
          </div>
        </div>
        <table>
          <thead>
          <tr>
            <th>Table Header</th>
            <th>Table Header</th>
          </tr>
          </thead>
          <tbody>
          <tr>
            <td>Content Goes Here</td>
            <td>Content Goes Here</td>
          </tr>
          </tbody>
        </table>
        <ul class="pagination" role="navigation" aria-label="Pagination">
          <li class="disabled">Previous <span class="show-for-sr">page</span></li>
          <li class="current"><span class="show-for-sr">You're on page</span> 1</li>
          <li><a href="#0" aria-label="Page 2">2</a></li>
          <li><a href="#0" aria-label="Page 3">3</a></li>
          <li><a href="#0" aria-label="Page 4">4</a></li>
          <li class="ellipsis" aria-hidden="true"></li>
          <li><a href="#0" aria-label="Page 12">12</a></li>
          <li><a href="#0" aria-label="Page 13">13</a></li>
          <li><a href="#0" aria-label="Next page">Next <span class="show-for-sr">page</span></a></li>
        </ul>

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
