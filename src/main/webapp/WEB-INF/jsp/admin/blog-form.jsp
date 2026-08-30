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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blog" class="com.simisinc.platform.domain.model.cms.Blog" scope="request"/>
<jsp:useBean id="mailingLists" class="java.util.ArrayList" scope="request"/>
<c:choose>
  <c:when test="${blog.id eq -1}"><h2 class="h4">New Blog</h2></c:when>
  <c:otherwise><h2 class="h4">Update Blog</h2></c:otherwise>
</c:choose>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${blog.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>"/>
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <c:if test="${blog.id != -1}">
    <div class="callout radius warning">
      <p style="margin-bottom:0">
        <i class="fa fa-exclamation-triangle"></i> This category's Unique Id
        (<code><c:out value="${blog.uniqueId}"/></code>) is part of every one of its posts' URLs
        (<code>/<c:out value="${blog.uniqueId}"/>/post-unique-id</code>). Changing Name below
        regenerates that id -- with no automatic redirect -- so every existing post link in this
        category would break silently. If you rename it anyway, plan on manually adding entries on
        the <a href="${ctx}/admin/web-redirects">Web Redirects</a> page for the old post URLs
        afterward.
      </p>
    </div>
  </c:if>
  <%-- Form Content --%>
  <label>Name <span class="required">*</span>
    <input type="text" placeholder="Blog, News, Press Releases..." name="name" aria-describedby="blogNameHelpText" value="<c:out value="${blog.name}"/>" required>
  </label>
  <p class="help-text" id="blogNameHelpText">Also generates this category's Unique Id (shown in the
    blog list, and used in every post's URL) the first time it's saved -- see the warning above
    before renaming a category that already has posts in it.</p>
  <label>Description
    <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${blog.description}"/>">
  </label>
  <label>Feed title
    <input type="text" placeholder="Leave blank to use the site name and this blog's name" name="feedTitle" aria-describedby="blogFeedTitleHelpText" value="<c:out value="${blog.feedTitle}"/>">
    <p class="help-text" id="blogFeedTitleHelpText">What this blog's RSS/Atom feed calls itself in a reader, where it appears beside titles like "Dark Reading" and "Ars Technica". Leave blank and the feed is titled "<c:out value="${sitePropertyMap['site.name']}"/> - <c:out value="${blog.name}"/>". Set it to name the feed like a publication instead.</p>
  </label>
  <label>Mailing List
    <select name="mailingListId">
      <option value="-1">None</option>
      <c:forEach items="${mailingLists}" var="mailingList">
        <option value="${mailingList.id}" <c:if test="${mailingList.id == blog.mailingListId}">selected</c:if>><c:out value="${mailingList.title}" /></option>
      </c:forEach>
    </select>
    <small>When set, this blog's post editor defaults to notifying this list on publish. A page using
      the Email Subscribe widget with this category's Unique Id also lets visitors subscribe to just
      this category's updates, separately from any site-wide subscription option.</small>
  </label>
  <input id="enabled" type="checkbox" name="enabled" value="true" <c:if test="${blog.id == -1 || blog.enabled}">checked</c:if>/><label for="enabled">Online?</label>
  <c:if test="${blog.id != -1}">
    <p><a href="${ctx}/admin/blog-tags?blogId=${blog.id}">Manage this blog's tags...</a></p>
    <p class="help-text">These tags belong only to this category -- another category with a similar
      topic (e.g. also wanting an "Announcements" tag) needs its own separate tag record; tags are
      never shared across categories.</p>
  </c:if>
  <div class="button-container">
    <c:choose>
      <c:when test="${!empty returnPage}">
          <input type="submit" class="button radius success" value="Save"/>
          <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </c:when>
      <c:otherwise>
        <input type="submit" class="button radius success expanded" value="Save"/>
      </c:otherwise>
    </c:choose>
  </div>
</form>
