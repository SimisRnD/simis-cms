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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webPageList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>

<div class="callout primary radius">
  <h6>What this page shows</h6>
  <p>
    This page manages the site's XML sitemap (<code>/sitemap.xml</code>) -- the file that tells
    search engines which pages exist and how recently each one changed. It's a different feature
    from the <strong>Navigation Menu</strong> editor (also confusingly called "Sitemap" in the admin
    menu) -- that one controls the site's visible nav links, this one controls what a search engine
    crawler sees. Per-page priority and change-frequency aren't set here -- those remain on each
    page's own edit form; this page only bulk-manages the include/exclude flag.
  </p>
  <ul>
    <li>Only web pages with <strong>Show in Sitemap.xml?</strong> switched on below are included.</li>
    <li>Published items, blog posts, and wiki pages are included automatically and aren't listed here.</li>
    <li>A page must also be enabled to appear, regardless of this setting -- draft status has no effect: a
      page with a pending edit still shows its last-published content in the sitemap (and everywhere else
      public) until that edit is actually published.</li>
  </ul>
</div>

<c:choose>
  <c:when test="${sitemapActuallyServed}">
    <div class="callout radius success" style="margin-bottom:20px">
      <p style="margin-bottom:0">
        <i class="fa fa-check-circle"></i> The sitemap is enabled and being served.
        <a href="${ctx}/sitemap.xml" target="_blank" rel="noopener">Preview the live sitemap.xml <i class="fa fa-external-link"></i></a>
      </p>
    </div>
  </c:when>
  <c:otherwise>
    <div class="callout radius warning" style="margin-bottom:20px">
      <p style="margin-bottom:0">
        <i class="fa fa-exclamation-triangle"></i>
        <c:choose>
          <c:when test="${!sitemapEnabled}">
            The sitemap is currently <strong>disabled</strong> (<code>site.sitemap.xml</code>) --
            <code>/sitemap.xml</code> returns a 404 regardless of the per-page settings below. Enable it
            from the site properties editor to start serving it.
          </c:when>
          <c:when test="${!siteUrlConfigured}">
            <code>site.url</code> isn't configured yet, so <code>/sitemap.xml</code> returns a 404 even
            though the sitemap toggle itself is on. Set the site URL in Site Settings first.
          </c:when>
          <c:otherwise>
            The site is currently <strong>offline</strong> (<code>site.online</code>) --
            <code>/sitemap.xml</code> returns a 404 until the site is taken online, the same as every
            other public page. Turning the sitemap toggle on by itself is not enough while the site
            is offline.
          </c:otherwise>
        </c:choose>
      </p>
    </div>
  </c:otherwise>
</c:choose>

<c:if test="${pagesMissingDescription > 0}">
  <h5>Page descriptions</h5>
  <div class="callout warning radius">
    <p><i class="fa fa-exclamation-triangle" aria-hidden="true"></i>
      <strong><c:out value="${pagesMissingDescription}" /></strong>
      <c:choose><c:when test="${pagesMissingDescription == 1}">page has</c:when><c:otherwise>pages have</c:otherwise></c:choose>
      no description of their own. Those pages still render a <code>&lt;meta name="description"&gt;</code>,
      but the value falls back to the site-wide description, so they describe the company instead of the
      page. Search engines may ignore a description that doesn't match the content. The affected rows are
      marked below; set a description on each from its own page settings.</p>
  </div>
</c:if>

<h5>Best practices</h5>
<div class="callout radius">
  <p>Before relying on this page's green "enabled and being served" status, actually open the
    <a href="${ctx}/sitemap.xml" target="_blank" rel="noopener">preview link</a> -- it's the only way to
    confirm search engines are seeing what you expect, since the count and content depend on which pages
    are enabled, published, and individually flagged for inclusion, not just the toggle above.</p>
</div>

<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />

  <table class="unstriped">
    <thead>
      <tr>
        <th width="80">In Sitemap?</th>
        <th>Title</th>
        <th width="120">Description</th>
        <th>Link</th>
        <th>Modified</th>
      </tr>
    </thead>
    <tbody>
      <c:forEach items="${webPageList}" var="webPage">
        <tr>
          <td>
            <%-- Carries what this row showed at render time, so post() can tell "the admin left
                 this alone" apart from "the admin explicitly turned this off" -- see
                 SeoSitemapWidget.post() for why that distinction matters. --%>
            <input type="hidden" name="renderedShowInSitemap_${webPage.id}" value="${webPage.showInSitemap}" />
            <input type="checkbox" id="showInSitemap_${webPage.id}" name="showInSitemap_${webPage.id}" value="true"<c:if
                test="${webPage.showInSitemap}"> checked</c:if> />
          </td>
          <td><label for="showInSitemap_${webPage.id}"><c:out value="${webPage.title}" /></label></td>
          <%-- Flagged in words as well as colour, so the warning does not depend on colour alone. --%>
          <td><c:choose>
            <c:when test="${empty fn:trim(webPage.description)}"><small class="alert-text"><i class="fa fa-exclamation-triangle"
              aria-hidden="true"></i> Missing</small></c:when>
            <c:otherwise><small>Set</small></c:otherwise>
          </c:choose></td>
          <td><a href="${ctx}${fn:escapeXml(webPage.link)}" target="_blank" rel="noopener"><c:out value="${webPage.link}" /></a></td>
          <td><small><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPage.modified}" /></small></td>
        </tr>
      </c:forEach>
      <c:if test="${empty webPageList}">
        <tr>
          <td colspan="5">No web pages were found</td>
        </tr>
      </c:if>
    </tbody>
  </table>

  <c:if test="${!empty webPageList}">
    <button type="submit" class="button radius primary">Save</button>
  </c:if>
</form>
