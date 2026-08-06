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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blogList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="blogPostCount" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${userSession.hasRole('admin')}">
<script nonce="${cspNonce}">
  function deleteBlog(blogId) {
    if (!confirm("Are you sure you want to delete this blog and all of its posts?")) {
      return;
    }
    postAction('${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&id=' + blogId);
  }
</script>
</c:if>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<div class="callout primary radius">
  <p style="margin-bottom:0">
    A Blog here is a named container for a group of related posts -- despite the name, it works well
    as a <strong>category</strong> (for example, "Press Releases", "Product Updates", "Engineering
    Blog", or "Customer Stories"), each with its own URL segment, its own private tag vocabulary (see
    "Manage this blog's tags..." on the edit form), and an optional mailing-list association for
    update notifications. Setting one up takes two separate steps: (1) create the Blog record here
    with <a href="${ctx}/admin/blog?returnPage=/admin/blogs">Add a Blog</a>, then (2) separately
    create a <a href="${ctx}/admin/web-pages">Web Page</a> at the URL you want the category to live
    at, using the "Blog Posts - List" or "Blog Posts - Cards" template (open it with the page
    designer -- the <i class="fa fa-code"></i> icon, not the pencil -- to pick a template for a new,
    contentless page). These two records are not automatically linked beyond the URL segment
    matching by convention; visiting <code>/blog-editor?blogUniqueId=some-new-name</code> for a name
    with no existing Blog auto-creates the Blog record and the individual post/article page template,
    but never the listing page above -- that part is always a manual step.
  </p>
  <p style="margin-bottom:0">
    Posts within any category go through a draft/publish workflow of their own on
    <a href="${ctx}/admin/blog-posts">All Blog Posts</a>. By default (site property
    <code>blogPost.review.required</code>, off unless turned on from
    <a href="${ctx}/admin/site-properties">Site Settings</a>), an author can publish directly;
    turning it on requires every post to be submitted and approved before it can go live, regardless
    of which category it's in -- that setting applies platform-wide, not per category.
  </p>
</div>
<a class="button small radius primary" href="${ctx}/admin/blog?returnPage=/admin/blogs">Add a Blog <i class="fa fa-arrow-circle-right"></i></a>
<a class="button small radius secondary" href="${ctx}/admin/blog-posts">All Blog Posts <i class="fa fa-arrow-circle-right"></i></a>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th width="180">Unique Id</th>
      <th width="100" class="text-center"># of posts</th>
      <th width="100" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${blogList}" var="blog">
      <tr>
        <td>
          <c:out value="${blog.name}" />
          <c:if test="${!blog.enabled}"><span class="label warning">offline</span></c:if>
          <c:if test="${!empty blog.description}">
            <br /><small class="subheader"><c:out value="${blog.description}" /></small>
          </c:if>
        </td>
        <td>
          <small><c:out value="${blog.uniqueId}" /></small>
        </td>
        <td class="text-center">
          <fmt:formatNumber value="${empty blogPostCount[blog.id] ? 0 : blogPostCount[blog.id]}" />
        </td>
        <td class="text-center">
          <a href="${ctx}/admin/blog?blogId=${blog.id}&returnPage=/admin/blogs"><i class="${font:fas()} fa-edit"></i></a>
          <c:if test="${userSession.hasRole('admin')}">
            <a href="javascript:deleteBlog(${blog.id});"><i class="fa fa-remove"></i></a>
          </c:if>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty blogList}">
      <tr>
        <td colspan="4">No blogs were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
<h5>Common problems and how to fix them</h5>
<ul>
  <li><strong>A category's landing page shows no posts, or 404s.</strong> The most common cause is a
    mismatch: the Web Page's "Blog Posts - List"/"Blog Posts - Cards" widget has its own
    <code>blogUniqueId</code> preference, set independently when the page was created, and it has to
    match this Blog's actual Unique Id (the small text in the second column above) exactly, or the
    listing has nothing to show. A blank list right after creating a brand-new category is normal --
    "# of posts" reads 0 until posts are actually written, not an error.</li>
  <li><strong>Renaming a category changes its URL, silently, with no redirect.</strong> The Unique Id
    column above is generated automatically from Name and becomes part of every one of that
    category's post URLs (<code>/{blogUniqueId}/{postUniqueId}</code>). Renaming a Blog after posts
    already exist in it regenerates this id, and every existing link to those posts breaks with
    nothing to catch the old URLs. If you must rename a category that already has content, plan on
    manually adding entries on the <a href="${ctx}/admin/web-redirects">Web Redirects</a> page for
    the old post URLs afterward -- nothing does this automatically.</li>
  <li><strong>Tags don't carry over between categories.</strong> Each Blog's tags ("Manage this
    blog's tags..." on the edit form) are private to that Blog. Two categories that both want an
    "Announcements" tag need two separate tag records, one created under each category -- there is no
    shared, cross-category tag vocabulary to pick from.</li>
  <li><strong>Deleting a category deletes everything in it.</strong> Removing a Blog cascades to
    delete all of its posts and its entire tag vocabulary immediately, with no separate "this will
    also delete N posts" warning beyond the browser's confirm dialog -- double check a category
    actually has no content worth keeping before deleting it. Deleting a Blog also does not touch or
    clean up its landing Web Page, if one was created for it; that page keeps existing (now backed by
    nothing), and its "Add a Post" button will silently recreate a blank Blog with the same name if
    it's ever used again.</li>
  <li><strong>Planning for a lot of categories.</strong> This list and the "Blog" filter dropdown on
    <a href="${ctx}/admin/blog-posts">All Blog Posts</a> (and the "Move to a different blog" bulk
    action there) are plain, unfiltered pickers -- fine for a handful of categories, but worth
    flagging as a follow-up if the list grows large enough that finding one by scrolling or a filter
    dropdown becomes painful.</li>
</ul>
<p class="help-text">
  Blog and post data lives in the same Postgres database as everything else on the site, covered by
  normal database backup/restore -- there's nothing storage- or search-index-specific to know about
  here.
</p>
