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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="cacheSummaryList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <h6>What this page shows</h6>
  <p>In-memory application caches (Caffeine) -- content/config caches (site properties, page content, collections, stylesheets, apps, table of contents), security caches (login/IP/app rate limiting), and a general-purpose object cache. Clearing one is immediate and needs no restart; the next request simply reloads what it needs from the database.</p>
  <p>Hit/miss/eviction counts accumulate from the moment the application started, so they'll read low right after a deploy. "Last Cleared" only tracks an explicit clear from this page -- a cache that has silently expired and reloaded on its own (see below) still shows &#8212; there.</p>
</div>

<p>
  <a href="#" data-confirm-post="Clear ALL caches? This cannot be undone." data-post-url="${widgetContext.uri}?command=clearAll&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" class="button alert">Clear All Caches</a>
</p>

<div style="overflow-x: auto">
<table class="unstriped">
  <thead>
    <tr>
      <th>Cache</th>
      <th>Entries</th>
      <th>Hits</th>
      <th>Misses</th>
      <th>Hit Rate</th>
      <th>Evictions</th>
      <th>Last Cleared</th>
      <th>Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${cacheSummaryList}" var="cacheSummary">
      <tr>
        <td><c:out value="${cacheSummary.name}" /></td>
        <td><fmt:formatNumber value="${cacheSummary.estimatedSize}" /></td>
        <td><fmt:formatNumber value="${cacheSummary.hitCount}" /></td>
        <td><fmt:formatNumber value="${cacheSummary.missCount}" /></td>
        <td>
          <c:choose>
            <c:when test="${cacheSummary.hitCount + cacheSummary.missCount == 0}">&#8212;</c:when>
            <c:otherwise><fmt:formatNumber value="${cacheSummary.hitRate * 100}" maxFractionDigits="1" />%</c:otherwise>
          </c:choose>
        </td>
        <td>
          <c:choose>
            <c:when test="${cacheSummary.evictionCount > 0}"><span class="label warning radius"><fmt:formatNumber value="${cacheSummary.evictionCount}" /></span></c:when>
            <c:otherwise>0</c:otherwise>
          </c:choose>
        </td>
        <td>
          <c:choose>
            <c:when test="${cacheSummary.neverCleared}">&#8212;</c:when>
            <c:otherwise><span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${cacheSummary.lastClearedAt}' />"><c:out value="${date:relative(cacheSummary.lastClearedAt)}" /></span></c:otherwise>
          </c:choose>
        </td>
        <td>
          <a href="#" data-confirm-post="Clear the ${fn:escapeXml(cacheSummary.name)} cache?" data-post-url="${widgetContext.uri}?command=clearCache&cache=${fn:escapeXml(cacheSummary.name)}&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" class="button tiny secondary">Clear</a>
        </td>
      </tr>
    </c:forEach>
  </tbody>
</table>
</div>

<h5>When to worry</h5>
<div class="callout warning radius">
  <p><strong>Evictions</strong> aren't automatically a problem -- Caffeine drops the least-recently-used entries once a cache hits its size cap, which is normal for a cache that's actually being used. Worth a look only if a cache with a high eviction count also has a low hit rate: that combination means entries are being pushed out before they get reused, so the size cap may be too small for real traffic.</p>
  <p><strong>A low hit rate isn't automatically bad</strong> either -- some of these caches (the rate-limit ones especially) are naturally low-hit by design. It's only worth investigating for the content/config caches (site properties, page content, collections, stylesheets), where a persistently low hit rate suggests the cache isn't helping much for the traffic pattern this site actually gets.</p>
  <p>If content you just edited still looks stale after a normal page reload, <strong>Clear</strong> the specific cache rather than <strong>Clear All Caches</strong> -- it's just as immediate and doesn't discard everything else that's still warm and useful.</p>
</div>

<h5>For Azure</h5>
<div class="callout radius">
  <p>These caches live in each instance's own memory, not anywhere shared. Clicking Clear (or Clear All) here only clears whichever Azure App Service instance happens to answer that click -- on more than one instance, the others aren't touched by it.</p>
  <p>Every cache on this page now has a time-based backstop, so a value that's gone stale on a non-writing instance self-heals on its own: the content/config caches (site properties, page content, collections, stylesheets, apps, table of contents) refresh within about a minute of being accessed and fully expire within 5 minutes even if nothing accesses them; the rate-limit and object caches were already bounded (15 minutes to 24 hours, matching what each one is actually used for). None of them stay stale forever, even without a manual Clear on every instance.</p>
  <p>That bound doesn't make this a distributed cache -- an edit still won't be instantly visible on every instance the moment you save it, just within the window above. If that gap ever becomes a real problem (e.g. for something that needs to be consistent immediately across instances), that would call for a proper shared/distributed cache, which is a separate, larger piece of work from what's here today.</p>
</div>
