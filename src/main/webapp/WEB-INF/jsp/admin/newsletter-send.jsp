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
  Pick a mailing list and a published blog post below to queue a notification email to everyone
  currently subscribed to that list. This is a one-shot broadcast tool for a single post, not a
  general email composer -- there's no free-text subject or body, and no scheduling. It's the same
  underlying mechanism as the <strong>"Notify subscribers"</strong> checkbox on the blog editor;
  this page is here for a post that was already published without checking that box.
</p>

<h5>How it works</h5>
<ul>
  <li>Only people currently <strong>subscribed to, and not unsubscribed from,</strong> the chosen
    list receive it -- pending (unconfirmed double opt-in) and quarantined addresses are skipped.</li>
  <li>Every email includes an unsubscribe link specific to that person and that list, so an
    unsubscribe from one newsletter doesn't affect their other list subscriptions.</li>
  <li>Sending happens in the <strong>background, not instantly</strong>. Clicking "Send Newsletter"
    only queues the job -- the page won't wait for delivery, and reloading it afterward won't show
    a progress bar. For a large list, expect delivery to finish over several minutes, not seconds
    (see Timing below).</li>
  <li>Which delivery path is used depends on the site's <strong>Mailing List Settings</strong>:
    <c:choose>
      <c:when test="${mailChimpEnabled}">
        this site currently sends via <strong>MailChimp</strong> -- clicking send creates and
        immediately sends a real MailChimp Campaign to everyone MailChimp has tagged with this
        list. MailChimp handles its own delivery and retries from that point on; this admin page
        has no further visibility into it once the campaign is sent.
      </c:when>
      <c:otherwise>
        this site currently sends via its own configured <strong>SMTP server</strong> -- clicking
        send creates one queued row per recipient, and a background job sends a batch of 25 every
        minute (see Timing below).
      </c:otherwise>
    </c:choose>
  </li>
</ul>

<h5>Timing (SMTP path)</h5>
<p>
  When sending via this site's own SMTP server (not MailChimp), delivery isn't immediate: a
  scheduled job sends <strong>25 emails per minute</strong>. A 500-person list takes roughly 20
  minutes to fully deliver, start to finish. A failed send is retried automatically up to
  <strong>3 times</strong> before being given up on -- no admin action is needed for a transient
  failure to resolve itself, only for a persistent one.
</p>

<h5>Common problems and how to fix them</h5>
<ul>
  <li><strong>Nothing sends, and there's no error on this page.</strong> Almost always a delivery
    path that's configured but broken (a wrong SMTP password, an expired MailChimp API key), not
    something this page can detect at click-time -- see "What to check if delivery seems stuck"
    below.</li>
  <li><strong>A subscriber says they never received it, but others did.</strong> Check they weren't
    already unsubscribed or quarantined (bounced/complained addresses stop receiving mail
    automatically) -- see their status on
    <a href="${ctx}/admin/mailing-lists">Mailing Lists</a>. Otherwise, ask them to check spam/junk
    -- this is the most common cause and isn't something this admin can fix from here.</li>
  <li><strong>Trying to send the same post again does nothing new.</strong> There's no duplicate
    guard on this page -- if you send the same list/post combination twice, subscribers get it
    twice. Re-sending isn't blocked, so double-check before resubmitting rather than relying on the
    system to catch it.</li>
</ul>

<h5>What to check if delivery seems stuck</h5>
<p>
  There's currently <strong>no dedicated page here showing a send's delivery status</strong>
  (sent/failed/skipped counts) -- that's a real gap, not something hidden elsewhere in the admin.
  What IS available today:
</p>
<ul>
  <li>The <a href="${ctx}/admin/audit-log">Audit Log</a> records that a send was
    <strong>queued</strong> (event type <code>newsletter.enqueue</code>) and how many recipients
    it targeted at that moment -- it does <strong>not</strong> record final per-recipient delivery
    outcomes for the SMTP path, since those happen later, asynchronously, outside this request.</li>
  <li>For the SMTP path, a failed send (after all 3 retries) is only visible in the
    <strong>application server logs</strong> ("Newsletter send error" entries) -- if a send seems
    to have stalled or subscribers report widespread non-delivery, that's the next place to look,
    which typically means asking whoever has server access.</li>
  <li>For the MailChimp path, check the campaign's status directly in the
    <strong>MailChimp dashboard</strong> -- delivery and bounce reporting live there, not here.</li>
  <li>If nothing was configured at all when you tried to send, see the warning below -- that's the
    one failure mode this page actively catches before you click send.</li>
</ul>

<c:if test="${!sendMethodConfigured}">
  <div class="callout radius alert" style="margin-bottom:20px">
    <p style="margin-bottom:0">
      <i class="fa fa-exclamation-triangle"></i> <strong>No email delivery method is configured.</strong>
      Sending now would queue recipients that can never actually be delivered. Set up SMTP on
      <a href="${ctx}/admin/mail-properties">Email Settings</a>, or enable MailChimp on
      <a href="${ctx}/admin/mailing-list-properties">Mailing List Settings</a>, before sending.
    </p>
  </div>
</c:if>

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
