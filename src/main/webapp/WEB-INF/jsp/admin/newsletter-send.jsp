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
<jsp:useBean id="mailingLists" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="blogPosts" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>

<p>
  Pick a mailing list and a published blog post to queue a notification email to everyone
  currently subscribed to that list. Sending happens in the background over the next few
  minutes, not instantly -- the page won't wait for it, and re-checking this page won't show a
  progress bar.
</p>
<ul>
  <li>Only people currently subscribed to (and not unsubscribed from) the chosen list receive it.</li>
  <li>Every email includes an unsubscribe link specific to that person and that list.</li>
  <li>Emails go out via the site's own configured SMTP settings -- if those aren't set up, sends
    will fail quietly in the background rather than error here; check the Audit Log if subscribers
    report not receiving anything.</li>
</ul>

<c:if test="${empty mailingLists}">
  <div class="callout radius warning" style="margin-bottom:20px">
    <p style="margin-bottom:0">
      <i class="fa fa-exclamation-triangle"></i> No enabled mailing lists were found. Create or
      enable one from <a href="${ctx}/admin/mailing-lists">Mailing Lists</a> first.
    </p>
  </div>
</c:if>
<c:if test="${empty blogPosts}">
  <div class="callout radius warning" style="margin-bottom:20px">
    <p style="margin-bottom:0">
      <i class="fa fa-exclamation-triangle"></i> No published blog posts were found to notify
      subscribers about.
    </p>
  </div>
</c:if>

<c:if test="${!empty mailingLists && !empty blogPosts}">
  <form method="post">
    <%-- Required by controller --%>
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
    <input type="hidden" name="token" value="${userSession.formToken}" />

    <div class="grid-x grid-margin-x">
      <div class="medium-6 cell">
        <label>Mailing list
          <select name="mailingListId" id="mailingListId">
            <option value="">Choose a list...</option>
            <c:forEach items="${mailingLists}" var="mailingList">
              <option value="${mailingList.id}"><c:out value="${mailingList.title}" /> (<c:out value="${mailingList.memberCount}" /> members)</option>
            </c:forEach>
          </select>
        </label>
      </div>
      <div class="medium-6 cell">
        <label>Blog post
          <select name="blogPostId" id="blogPostId" onchange="NewsletterSendPreview.update(this)">
            <option value="">Choose a post...</option>
            <c:forEach items="${blogPosts}" var="blogPost">
              <option value="${blogPost.id}"
                  data-title="${fn:escapeXml(blogPost.title)}"
                  data-summary="${fn:escapeXml(blogPost.summary)}"><c:out value="${blogPost.title}" /></option>
            </c:forEach>
          </select>
        </label>
      </div>
    </div>

    <div id="newsletterPreview" class="callout radius" style="display:none; margin-bottom:20px">
      <p style="margin-bottom:4px"><strong>Preview</strong></p>
      <h5 id="newsletterPreviewTitle" style="margin-bottom:8px"></h5>
      <p id="newsletterPreviewSummary" style="margin-bottom:0"></p>
    </div>

    <button type="submit" class="button radius primary">Send Newsletter</button>
  </form>

  <script nonce="${cspNonce}">
    var NewsletterSendPreview = {
      update: function (select) {
        var preview = document.getElementById('newsletterPreview');
        var option = select.options[select.selectedIndex];
        if (!option.value) {
          preview.style.display = 'none';
          return;
        }
        document.getElementById('newsletterPreviewTitle').textContent = option.getAttribute('data-title') || '';
        document.getElementById('newsletterPreviewSummary').textContent = option.getAttribute('data-summary') || '';
        preview.style.display = '';
      }
    };
  </script>
</c:if>
