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
<%@ taglib prefix="g" uri="http://granule.com/tags" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="webPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<link rel="stylesheet" href="${ctx}/javascript/codemirror-5.54.0/codemirror.css">
<link rel="stylesheet" href="${ctx}/javascript/codemirror-5.54.0/theme/one-dark.css">
<g:compress>
  <script src="${ctx}/javascript/codemirror-5.54.0/codemirror.js"></script>
  <script src="${ctx}/javascript/codemirror-5.54.0/xml-fold.js"></script>
  <script src="${ctx}/javascript/codemirror-5.54.0/closetag.js"></script>
  <script src="${ctx}/javascript/codemirror-5.54.0/matchtags.js"></script>
  <script src="${ctx}/javascript/codemirror-5.54.0/xml.js"></script>
</g:compress>
<style>
  .CodeMirror {
    border: 1px solid #4d4d4d;
    /*font-size: 12px !important;*/
  }
</style>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<small><c:out value="${webPage.link}" /></small>
<c:if test="${empty webPage.pageXml}">
  <p class="subheader">Page layout does not exist! Choose a template or design the page yourself...</p>
</c:if>
<div class="grid-x grid-padding-x">
  <div class="small-12 cell">
    <form method="post">
      <%-- Required by controller --%>
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <%-- Form values --%>
      <input type="hidden" name="webPage" value="<c:out value="${webPage.link}" />"/>
      <input type="hidden" name="returnPage" value="${returnPage}" />
      <%-- The editor --%>
      <p id="pageXmlContainer">
        <textarea id="pageXml" name="pageXml" data-gutter="1" rows="24"><c:out value="${webPage.pageXml}"/></textarea>
      </p>
      <div>
        <p>
          <input type="submit" class="button radius success" value="Save"/>
          <c:choose>
            <c:when test="${!empty returnPage}">
              <a href="${returnPage}" class="button radius secondary">Cancel</a>
            </c:when>
            <c:when test="${!empty webPage.link}">
              <a href="${ctx}${webPage.link}" class="button radius secondary">Cancel</a>
            </c:when>
            <c:otherwise>

            </c:otherwise>
          </c:choose>
        </p>
      </div>
    </form>
    <script>
      // Hook up the editor to the textarea
      var pageXml = document.getElementById("pageXml");
      var codeMirror = CodeMirror.fromTextArea(pageXml, {
        theme: 'one-dark',
        lineNumbers: true,
        lineWrapping: true,
        tabSize: 2,
        matchTags: {bothTags: true},
        autoCloseTags: true
      });
      // Resize to match window
      $(document).ready(function () {
        function resizeEditor() {
          var container = document.getElementsByClassName("CodeMirror")[0];
          var rect = container.getBoundingClientRect(),
            scrollLeft = window.pageXOffset || document.documentElement.scrollLeft,
            scrollTop = window.pageYOffset || document.documentElement.scrollTop;
          return $('.CodeMirror').height($(window).height() - Math.round(rect.top + scrollTop + 75));
        }
        $(window).resize(resizeEditor);
        resizeEditor();
      });
    </script>
  </div>
<%--
  <div class="small-4 cell">
    <ul class="accordion" data-accordion data-allow-all-closed="true">
      <c:forEach items="${widgetList}" var="widget">

      </c:forEach>
      <li class="accordion-item" data-accordion-item>
        <a href="#" class="accordion-title">Login and Registration</a>
        <div class="accordion-content" data-tab-content>

          <ul class="accordion" data-accordion data-allow-all-closed="true">
            <li class="accordion-item" data-accordion-item>
              <a href="#" class="accordion-title">login</a>
              <div class="accordion-content" data-tab-content>
                Hello 1
              </div>
            </li>
            <li class="accordion-item" data-accordion-item>
              <a href="#" class="accordion-title">register</a>
              <div class="accordion-content" data-tab-content>
                Hello 2
              </div>
            </li>

          </ul>

        </div>
      </li>

      <li class="accordion-item" data-accordion-item>
        <a href="#" class="accordion-title">Content Management</a>
        <div class="accordion-content" data-tab-content>

          <ul class="accordion" data-accordion data-allow-all-closed="true">
            <li class="accordion-item" data-accordion-item>
              <a href="#" class="accordion-title">content</a>
              <div class="accordion-content" data-tab-content>
                Hello 3
              </div>
            </li>
            <li class="accordion-item" data-accordion-item>
              <a href="#" class="accordion-title">contentTabs</a>
              <div class="accordion-content" data-tab-content>
                Hello 4
              </div>
            </li>
          </ul>

        </div>
      </li>

    </ul>
  </div>
  --%>
</div>