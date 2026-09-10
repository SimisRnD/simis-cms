<%--
  ~ Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="violationList" class="java.util.ArrayList" scope="request"/>
<%@include file="../page_messages.jspf" %>
<c:if test="${!reportingEnabled}">
  <div class="callout warning">
    <p><i class="${font:fas()} fa-triangle-exclamation"></i> <strong>Report-only is off, so nothing is being collected.</strong></p>
    <p>Set a candidate policy in <a href="${ctx}/admin/security-properties">Security Settings</a> under
      &ldquo;CSP report-only policy&rdquo;. A good starting point is
      <code>default-src 'self'; connect-src 'self'</code>. It cannot block anything &mdash; the browser only
      reports what <em>would</em> have been blocked.</p>
  </div>
</c:if>
<c:choose>
  <c:when test="${empty violationList}">
    <c:if test="${reportingEnabled}">
      <div class="callout primary">
        <p><i class="${font:fas()} fa-circle-info"></i> No violations reported yet.</p>
        <p>Reports arrive as people browse. To find what a stricter policy would break, exercise the parts of the
          site that use third-party services &mdash; place a test order, submit a form with a captcha, open a page
          with an embedded video or a map &mdash; then come back here. Those are the paths whose hosts cannot be
          found by reading the code.</p>
      </div>
    </c:if>
  </c:when>
  <c:otherwise>
    <p class="help-text page-help">Each row is a host a candidate policy would have refused, and how many times. Add the ones
      that belong to services this site genuinely uses to the matching directive, then re-test. Rows shown as
      <code>'inline'</code> or <code>'eval'</code> are not hosts &mdash; they mean the policy would have blocked
      inline or evaluated code on that page.</p>
    <div class="table-scroll">
      <table class="unstriped">
        <thead>
          <tr>
            <th>Directive</th>
            <th>Blocked</th>
            <th>Add to policy as</th>
            <th class="text-right">Times</th>
            <th>Example page</th>
            <th>Last seen</th>
          </tr>
        </thead>
        <tbody>
        <c:forEach items="${violationList}" var="violation">
          <tr>
            <td><code><c:out value="${violation.effectiveDirective}"/></code></td>
            <td><c:out value="${violation.blockedHost}"/></td>
            <td><code><c:out value="${violation.sourceListEntry}"/></code></td>
            <td class="text-right"><c:out value="${violation.occurrences}"/></td>
            <td>
              <c:if test="${!empty violation.sampleDocumentPath}">
                <a href="${ctx}<c:out value="${violation.sampleDocumentPath}"/>"><c:out value="${violation.sampleDocumentPath}"/></a>
              </c:if>
            </td>
            <td><fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${violation.lastSeen}"/></td>
          </tr>
        </c:forEach>
        </tbody>
      </table>
    </div>
    <form method="post" action="${ctx}/admin/csp-violations">
      <%-- Required by controller --%>
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <button type="submit" class="button small secondary radius"><i class="${font:fas()} fa-trash"></i> Clear collected reports</button>
    </form>
    <p class="help-text page-help">Clear these once the policy has been updated from them, so the next round of testing starts
      from a clean slate. At most <c:out value="${maxDistinctViolations}"/> distinct directive/host pairs are ever stored; past that,
      counts keep rising but new hosts are refused.</p>
  </c:otherwise>
</c:choose>
