<%--
  ~ Copyright 2026 SimIS Inc.
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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!empty blogPost}">
  <p>
    Post: <c:out value="${blogPost.title}" />
    &mdash; Status: <span class="label secondary"><c:out value="${reviewStatus}" /></span>
  </p>
  <%-- The review affordance is chosen by ContentReviewCommand.offerFor(), so separation of duties
       is reflected here as well as enforced on the action: a submitter is never shown Approve. --%>
  <c:choose>
    <c:when test="${reviewOffer eq 'publish'}">
      <a class="button warning" href="${widgetContext.uri}?action=publish&blogPostId=${blogPost.id}&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" data-confirm-href="Publish this post?">PUBLISH</a>
    </c:when>
    <c:when test="${reviewOffer eq 'submit'}">
      <a class="button warning" href="${widgetContext.uri}?action=submitForReview&blogPostId=${blogPost.id}&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" data-confirm-href="Submit this post for review?">SUBMIT FOR REVIEW</a>
    </c:when>
    <c:when test="${reviewOffer eq 'awaiting'}">
      <span class="label warning" title="Another reviewer must approve this change">AWAITING REVIEW</span>
    </c:when>
    <c:when test="${reviewOffer eq 'decide'}">
      <%-- The release-authority reference travels with the approval and is recorded in the audit
           trail ("cleared per PA case ...", "CO email dated ..."), which is what makes the trail
           exportable assessment evidence rather than just a timestamp. --%>
      <form method="post" action="${widgetContext.uri}" class="platform-content-review-form" data-confirm-submit="Approve and publish this post?">
        <input type="hidden" name="action" value="approve"/>
        <input type="hidden" name="blogPostId" value="${blogPost.id}"/>
        <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
        <input type="hidden" name="token" value="${userSession.formToken}"/>
        <label>Release authority (optional)
          <input type="text" name="releaseReference" maxlength="255"
                 placeholder="e.g. cleared per PA case 2026-114"
                 title="Optional: the approval authority to record in the audit trail"/>
        </label>
        <label>Re-authentication required to approve
          <input type="password" name="stepUpCredential" maxlength="255"
                 placeholder="Your password or authenticator code"/>
        </label>
        <button type="submit" class="button success">APPROVE</button>
      </form>
      <a class="button alert" href="${widgetContext.uri}?action=reject&blogPostId=${blogPost.id}&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" data-confirm-href="Return this post to the author?">REJECT</a>
    </c:when>
    <c:otherwise>
      <p class="small">There is no pending draft to review.</p>
    </c:otherwise>
  </c:choose>
</c:if>
<%@include file="../confirm_submit.jspf" %>
