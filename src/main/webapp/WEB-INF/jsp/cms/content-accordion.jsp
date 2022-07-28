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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="isDraft" class="java.lang.String" scope="request"/>
<jsp:useBean id="accordionClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="innerAccordionClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="sectionList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="expandTopLevel" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<div class="platform-content-container">
  <c:if test="${showEditor eq 'true' && !empty uniqueId}">
    <div class="platform-content-editor">
      <c:if test="${isDraft eq 'true'}">
        <a class="hollow button small warning" href="${widgetContext.uri}?action=publish&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" onclick="return confirm('Publish this content?');">DRAFT</a>
      </c:if>
      <a class="hollow button small secondary" href="${ctx}/content-editor?uniqueId=${uniqueId}&returnPage=${returnPage}"><i class="${font:fas()} fa-edit"></i></a>
    </div>
  </c:if>
  <c:if test="${!empty sectionList}">
    <ul id="accordion0" class="accordion<c:if test="${!empty accordionClass}"> <c:out value="${accordionClass}" /></c:if>" data-accordion data-allow-all-closed="true"<c:if test="${expandTopLevel eq 'true'}"> data-multi-expand="true"</c:if>>
      <c:forEach items="${sectionList}" var="section" varStatus="sectionStatus">
        <c:if test="${!empty section.title}">
          <li id="${html:makeId(section.title)}" class="accordion-item<c:if test="${expandTopLevel eq 'true'}"> is-active</c:if>" data-accordion-item>
            <c:choose>
              <c:when test="${expandTopLevel eq 'true'}">
                <h5 class="accordion-header accordion-title-level-1"><c:out value="${section.title}" /></h5>
              </c:when>
              <c:otherwise>
                <a href="#" class="accordion-title accordion-title-level-1"><c:out value="${section.title}" /></a>
              </c:otherwise>
            </c:choose>            
            <div id="accordion${sectionStatus.count}-content" class="accordion-content" data-tab-content>
              <ul id="accordion${sectionStatus.count}" class="accordion<c:if test="${!empty innerAccordionClass}"> <c:out value="${innerAccordionClass}" /></c:if>" data-accordion data-allow-all-closed="true">
        </c:if>
        <c:forEach items="${section.contentList}" var="card" varStatus="cardStatus">
          <li id="${html:makeId(section.labelsList[cardStatus.index])}" class="accordion-item" data-accordion-item>
            <a href="#" class="accordion-title accordion-title-level-2">${section.labelsList[cardStatus.index]}</a>
            <div class="accordion-content" data-tab-content>
                ${card}
            </div>
          </li>
        </c:forEach>
        <c:if test="${!empty section.title}">
              </ul>
            </div>
          </li>
        </c:if>
      </c:forEach>
    </ul>
  </c:if>
</div>
