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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="isDraft" class="java.lang.String" scope="request"/>
<jsp:useBean id="cardList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="card1" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
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
  <c:if test="${!empty card1}">
    <div id="gallery-image${widgetContext.uniqueId}">
      ${card1}
    </div>
    <c:if test="${cardList.size() gt 1}">
      <div class="platform-content">
        <div class="grid-x grid-margin-x">
          <c:forEach items="${cardList}" var="card" varStatus="cardStatus">
            <div id="image${widgetContext.uniqueId}${cardStatus.index}" class="cell small-3 text-center">
              <a href="javascript:changeImage${widgetContext.uniqueId}(${cardStatus.index})">${card}</a>
            </div>
          </c:forEach>
        </div>
      </div>
    </c:if>
  </c:if>
</div>
<script>
  function changeImage${widgetContext.uniqueId}(index) {
    var image = $('#gallery-image${widgetContext.uniqueId} img');
    var thumb = $('#image${widgetContext.uniqueId}' + index + ' img');
    image.attr("src",thumb.attr('src'));
  }
</script>
