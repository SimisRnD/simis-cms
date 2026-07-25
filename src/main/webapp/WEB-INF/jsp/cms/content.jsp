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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="contentHtml" class="java.lang.String" scope="request"/>
<jsp:useBean id="videoBackgroundUrl" class="java.lang.String" scope="request"/>
<jsp:useBean id="isDraft" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa <c:out value="${icon}"/>"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<div class="platform-content-container">
  <c:if test="${showEditor eq 'true' && !empty uniqueId}">
    <div class="platform-content-editor">
      <%-- The review affordance is chosen by ContentReviewCommand.offerFor(), so separation of duties
           is reflected here as well as enforced on the action: a submitter is never shown Approve. --%>
      <c:choose>
        <c:when test="${reviewOffer eq 'publish'}">
          <c:if test="${isDraft eq 'true'}">
            <a class="hollow button small warning" href="${widgetContext.uri}?action=publish&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" onclick="return confirm('Publish this content?');">DRAFT</a>
          </c:if>
        </c:when>
        <c:when test="${reviewOffer eq 'submit'}">
          <a class="hollow button small warning" href="${widgetContext.uri}?action=submitForReview&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" onclick="return confirm('Submit this content for review?');">SUBMIT FOR REVIEW</a>
        </c:when>
        <c:when test="${reviewOffer eq 'awaiting'}">
          <span class="label warning" title="Another reviewer must approve this change">AWAITING REVIEW</span>
        </c:when>
        <c:when test="${reviewOffer eq 'decide'}">
          <%-- The release-authority reference travels with the approval and is recorded in the audit
               trail ("cleared per PA case ...", "CO email dated ..."), which is what makes the trail
               exportable assessment evidence rather than just a timestamp. A GET form produces the
               same request shape as the action links beside it. --%>
          <form method="post" action="${widgetContext.uri}" class="platform-content-review-form">
            <input type="hidden" name="action" value="approve"/>
            <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
            <input type="hidden" name="token" value="${userSession.formToken}"/>
            <input type="text" name="releaseReference" maxlength="255"
                   placeholder="Release authority (e.g. cleared per PA case 2026-114)"
                   title="Optional: the approval authority to record in the audit trail"/>
            <input type="password" name="stepUpCredential" maxlength="255"
                   placeholder="Your password or authenticator code"
                   title="Re-authentication required to approve content"/>
            <button type="submit" class="hollow button small success"
                    onclick="return confirm('Approve and publish this content?');">APPROVE</button>
          </form>
          <a class="hollow button small alert" href="${widgetContext.uri}?action=reject&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" onclick="return confirm('Return this content to the author?');">REJECT</a>
        </c:when>
      </c:choose>
      <a class="hollow button small secondary" href="${ctx}/content-editor?uniqueId=${uniqueId}&returnPage=${returnPage}"><i class="${font:fas()} fa-edit"></i></a>
    </div>
  </c:if>
  <c:choose>
    <c:when test="${!empty videoBackgroundUrl}">
      <div class="video-background">
        <video autoplay muted loop>
          <source src="<c:out value="${videoBackgroundUrl}" />" type="video/mp4">
        </video>
        <div class="video-background-content">
          <div class="platform-content">${contentHtml}</div>
        </div>
      </div>
    </c:when>
    <c:otherwise>
      <div class="platform-content">${contentHtml}</div>
    </c:otherwise>
  </c:choose>
</div>
