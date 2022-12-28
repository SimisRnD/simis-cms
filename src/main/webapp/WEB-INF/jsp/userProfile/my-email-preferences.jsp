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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="user" class="com.simisinc.platform.domain.model.User" scope="request"/>
<jsp:useBean id="title" class="java.lang.String" scope="request"/>
<jsp:useBean id="message" class="java.lang.String" scope="request"/>
<jsp:useBean id="mailingList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="subscribedLists" class="java.util.ArrayList" scope="request"/>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<c:if test="${!empty subtitle}">
  <p><c:out value="${subtitle}"/></p>
</c:if>
<c:choose>
  <c:when test="${empty mailingList}">
    <p>We currently do not have any email lists to subscribe to.</p>
  </c:when>
  <c:otherwise>
    <form method="post" onsubmit="return false;">
      <div class="full-container">
        <div class="grid-x grid-margin-x">
          <c:forEach items="${mailingList}" var="mailingListItem" varStatus="status">
            <div class="small-12 cell">
              <input type="checkbox" id="mailingListCheck${mailingListItem.id}" name="mailingListCheck${mailingListItem.id}" value="${mailingListItem.id}"<c:if test="${fn:contains(subscribedLists, mailingListItem.id)}"> checked</c:if>/><label class="no-gap" for="mailingListCheck${mailingListItem.id}"><c:out value="${mailingListItem.title}"/></label>
              <span id="mailingListAdded${mailingListItem.id}" class="label success" style="display:none">subscribed</span>
              <span id="mailingListRemoved${mailingListItem.id}" class="label warning" style="display:none">unsubscribed</span>
              <c:if test="${!empty mailingListItem.description}">
              <p class="platform-indent"><c:out value="${mailingListItem.description}"/></p>
              </c:if>
            </div>
          </c:forEach>
        </div>
      </div>
    </form>
    <script type="text/javascript">
      $(document).ready(function () {
        $('[id^=mailingListCheck]').click(function () {
          var mailingListId = $(this).val();
          if ($(this).prop("checked") === true) {
            $.getJSON("${ctx}/json/mailingList?token=${userSession.formToken}&command=subscribe&id=" + encodeURIComponent(mailingListId), function(data) {
              if (data.status !== undefined && data.status === '0') {
                $('#mailingListRemoved' + mailingListId).hide();
                $('#mailingListAdded' + mailingListId).show();
              }
            });
          } else if ($(this).prop("checked") === false) {
            $.getJSON("${ctx}/json/mailingList?token=${userSession.formToken}&command=unsubscribe&id=" + encodeURIComponent(mailingListId), function(data) {
              if (data.status !== undefined && data.status === '0') {
                $('#mailingListAdded' + mailingListId).hide();
                $('#mailingListRemoved' + mailingListId).show();
              }
            });
          }
        });
      });
    </script>
  </c:otherwise>
</c:choose>

