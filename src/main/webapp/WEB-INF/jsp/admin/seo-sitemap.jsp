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
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>

<p>
  This page manages the site's XML sitemap (<code>/sitemap.xml</code>) -- the file that tells
  search engines which pages exist and how recently each one changed. It's a different feature
  from the <strong>Navigation Menu</strong> editor (also confusingly called "Sitemap" in the admin
  menu) -- that one controls the site's visible nav links, this one controls what a search engine
  crawler sees.
</p>
<ul>
  <li>Only web pages with <strong>Show in Sitemap.xml?</strong> switched on below are included.</li>
  <li>Published items, blog posts, and wiki pages are included automatically and aren't listed here.</li>
  <li>A page must also be enabled and not in draft to appear, regardless of this setting.</li>
</ul>

<c:choose>
  <c:when test="${sitemapEnabled}">
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
        <i class="fa fa-exclamation-triangle"></i> The sitemap is currently <strong>disabled</strong>
        (<code>site.sitemap.xml</code>) -- <code>/sitemap.xml</code> returns a 404 regardless of the
        per-page settings below. Enable it from the site properties editor to start serving it.
      </p>
    </div>
  </c:otherwise>
</c:choose>

<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />

  <table class="unstriped">
    <thead>
      <tr>
        <th width="80">In Sitemap?</th>
        <th>Title</th>
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
          <td><a href="${ctx}${fn:escapeXml(webPage.link)}" target="_blank" rel="noopener"><c:out value="${webPage.link}" /></a></td>
          <td><small><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPage.modified}" /></small></td>
        </tr>
      </c:forEach>
      <c:if test="${empty webPageList}">
        <tr>
          <td colspan="4">No web pages were found</td>
        </tr>
      </c:if>
    </tbody>
  </table>

  <c:if test="${!empty webPageList}">
    <button type="submit" class="button radius primary">Save</button>
  </c:if>
</form>
