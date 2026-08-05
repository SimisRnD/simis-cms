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
<jsp:useBean id="blogPostList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="blogList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="blogPostReviewStatusMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%-- Filters (GET so the criteria live in the URL and paging preserves them) --%>
<form method="get" autocomplete="off" class="margin-bottom-10">
  <div class="grid-x grid-margin-x">
    <div class="cell medium-4">
      <label>Search
        <input type="text" name="q" placeholder="post title" value="<c:out value='${q}'/>">
      </label>
    </div>
    <div class="cell medium-4">
      <label>Blog
        <select name="blogId">
          <option value="">All</option>
          <c:forEach items="${blogList}" var="blog">
            <option value="${blog.id}" <c:if test="${blogId == blog.id}">selected</c:if>><c:out value="${blog.name}" /></option>
          </c:forEach>
        </select>
      </label>
    </div>
    <div class="cell medium-4">
      <label>Status
        <select name="status">
          <option value="">All</option>
          <option value="published" <c:if test="${status == 'published'}">selected</c:if>>Published</option>
          <option value="draft" <c:if test="${status == 'draft'}">selected</c:if>>Draft</option>
          <%-- Archived posts are excluded from every other option above by default (issue #427);
               this is the only way to see them in the admin list. --%>
          <option value="archived" <c:if test="${status == 'archived'}">selected</c:if>>Archived</option>
        </select>
      </label>
    </div>
  </div>
  <button type="submit" class="button small primary radius"><i class="fa fa-filter"></i> Filter</button>
  <a href="${widgetContext.uri}" class="button small secondary radius">Clear</a>
</form>
<div id="bulkActionsBar" class="callout radius" style="display:none;padding:10px 15px;margin-bottom:10px;">
  <span id="bulkSelectedCount"></span>
  <button type="button" class="button tiny radius" id="bulkPublishBtn">Publish</button>
  <button type="button" class="button tiny radius" id="bulkUnpublishBtn">Unpublish</button>
  <button type="button" class="button tiny radius" id="bulkArchiveBtn">Archive</button>
  <button type="button" class="button tiny radius" id="bulkMoveBtn">Move</button>
  <button type="button" class="button tiny alert radius" id="bulkDeleteBtn">Delete</button>
</div>
<table class="unstriped">
  <thead>
    <tr>
      <th width="24"><input type="checkbox" id="selectAllBlogPosts" aria-label="Select all blog posts on this page"></th>
      <th>Title</th>
      <th width="160">Blog</th>
      <th width="120" class="text-center">Status</th>
      <th width="160" class="text-center">Published</th>
      <th width="80" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${blogPostList}" var="blogPost">
      <c:set var="postBlog" value="${null}" />
      <c:forEach items="${blogList}" var="blog">
        <c:if test="${blog.id == blogPost.blogId}"><c:set var="postBlog" value="${blog}" /></c:if>
      </c:forEach>
      <tr>
        <td><input type="checkbox" class="blogPostRowCheckbox" value="${blogPost.id}" data-title="${fn:escapeXml(blogPost.title)}" aria-label="Select ${fn:escapeXml(blogPost.title)}"></td>
        <td>
          <c:choose>
            <c:when test="${!empty postBlog}">
              <a href="${ctx}/blog-editor?blogUniqueId=${postBlog.uniqueId}&returnPage=/admin/blog-posts&blogPostId=${blogPost.id}"><c:out value="${blogPost.title}" /></a>
            </c:when>
            <c:otherwise>
              <c:out value="${blogPost.title}" />
            </c:otherwise>
          </c:choose>
          <c:if test="${!empty blogPostReviewStatusMap[blogPost.id]}">
            <br /><a href="${ctx}/admin/blog-post-review?blogPostId=${blogPost.id}" class="secondary label">
              <i class="fa fa-clipboard-check"></i> <c:out value="${blogPostReviewStatusMap[blogPost.id]}" />
            </a>
          </c:if>
        </td>
        <td>
          <c:if test="${!empty postBlog}"><c:out value="${postBlog.name}" /></c:if>
        </td>
        <td class="text-center">
          <c:choose>
            <c:when test="${!empty blogPost.archived}">
              <span class="label secondary radius">Archived</span>
            </c:when>
            <c:when test="${!empty blogPost.published}">
              <span class="label success radius">Published</span>
            </c:when>
            <c:otherwise>
              <span class="label radius">Draft</span>
            </c:otherwise>
          </c:choose>
        </td>
        <td class="text-center">
          <c:if test="${!empty blogPost.published}">
            <fmt:formatDate pattern="yyyy-MM-dd" value="${blogPost.published}" />
          </c:if>
        </td>
        <td class="text-center">
          <c:if test="${!empty postBlog}">
            <a href="${ctx}/blog-editor?blogUniqueId=${postBlog.uniqueId}&returnPage=/admin/blog-posts&blogPostId=${blogPost.id}"><i class="fa fa-edit"></i></a>
          </c:if>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty blogPostList}">
      <tr>
        <td colspan="6">No blog posts match the current filters</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
<%-- Bulk action reveal modals -- selection is scoped to the blog posts currently checked on this
     page (see the JS below); each is populated at open time with the live selection, not just a
     count. Mirrors calendar-event-list.jsp/web-page-list.jsp's bulk reveal modals (issue #427
     pattern). --%>
<div class="reveal" id="bulkPublishReveal" role="dialog" aria-modal="true" aria-labelledby="bulkPublishRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkPublishRevealTitle">Publish <span id="bulkPublishCount">0</span> Blog Post(s)</h4>
  <p class="help-text">A post still awaiting review approval will be reported as a per-row failure and left unpublished; the rest of the selection is still processed.</p>
  <ul id="bulkPublishList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkPublish"/>
    <input type="submit" class="button radius" value="Publish Posts"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkUnpublishReveal" role="dialog" aria-modal="true" aria-labelledby="bulkUnpublishRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkUnpublishRevealTitle">Unpublish <span id="bulkUnpublishCount">0</span> Blog Post(s)</h4>
  <p class="help-text">Unpublished posts are taken out of live view and marked as drafts. Any prior review approval is cleared, so the post must be re-approved before it can be published again.</p>
  <ul id="bulkUnpublishList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkUnpublish"/>
    <input type="submit" class="button radius" value="Unpublish Posts"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkArchiveReveal" role="dialog" aria-modal="true" aria-labelledby="bulkArchiveRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkArchiveRevealTitle">Archive <span id="bulkArchiveCount">0</span> Blog Post(s)</h4>
  <p class="help-text">Archived posts are hidden from this list by default. They can still be found with the Archived status filter.</p>
  <ul id="bulkArchiveList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkArchive"/>
    <input type="submit" class="button radius" value="Archive Posts"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkMoveReveal" role="dialog" aria-modal="true" aria-labelledby="bulkMoveRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkMoveRevealTitle">Move <span id="bulkMoveCount">0</span> Blog Post(s)</h4>
  <ul id="bulkMoveList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkMove"/>
    <label for="bulkMoveBlogId">Destination blog <span class="required">*</span>
      <select id="bulkMoveBlogId" name="blogId" required>
        <c:forEach items="${blogList}" var="blog">
          <option value="${blog.id}"><c:out value="${blog.name}" /></option>
        </c:forEach>
      </select>
    </label>
    <input type="submit" class="button radius" value="Move Posts"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkDeleteReveal" role="dialog" aria-modal="true" aria-labelledby="bulkDeleteRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkDeleteRevealTitle">Delete <span id="bulkDeleteCount">0</span> Blog Post(s)</h4>
  <p class="help-text">This permanently removes the selected blog posts. This cannot be undone.</p>
  <ul id="bulkDeleteList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkDelete"/>
    <input type="submit" class="button alert radius" value="Delete Posts"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<script nonce="${cspNonce}">
  (function () {
    var $selectAll = $('#selectAllBlogPosts');
    var $rows = $('.blogPostRowCheckbox');
    var $bar = $('#bulkActionsBar');
    var $count = $('#bulkSelectedCount');

    function selected() {
      return $rows.filter(':checked');
    }

    function refresh() {
      var n = selected().length;
      $count.text(n + (n === 1 ? ' post selected  ' : ' posts selected  '));
      $bar.toggle(n > 0);
      $selectAll.prop('indeterminate', n > 0 && n < $rows.length);
      $selectAll.prop('checked', n > 0 && n === $rows.length);
    }

    // Populates one bulk modal's hidden blogPostId fields and visible title list from the
    // currently checked rows, so the admin sees exactly what is about to be affected before
    // confirming.
    function populateBulkModal(revealId, listId, countId) {
      var $reveal = $('#' + revealId);
      var $form = $reveal.find('form');
      var $list = $('#' + listId);
      $form.find('input[name="blogPostId"]').remove();
      $list.empty();
      selected().each(function () {
        var $checkbox = $(this);
        $form.append($('<input type="hidden" name="blogPostId">').val($checkbox.val()));
        $list.append($('<li>').text($checkbox.data('title')));
      });
      $('#' + countId).text(selected().length);
      $reveal.foundation('open');
    }

    $selectAll.on('change', function () {
      $rows.prop('checked', this.checked);
      refresh();
    });
    $rows.on('change', refresh);

    $('#bulkPublishBtn').on('click', function () { populateBulkModal('bulkPublishReveal', 'bulkPublishList', 'bulkPublishCount'); });
    $('#bulkUnpublishBtn').on('click', function () { populateBulkModal('bulkUnpublishReveal', 'bulkUnpublishList', 'bulkUnpublishCount'); });
    $('#bulkArchiveBtn').on('click', function () { populateBulkModal('bulkArchiveReveal', 'bulkArchiveList', 'bulkArchiveCount'); });
    $('#bulkMoveBtn').on('click', function () { populateBulkModal('bulkMoveReveal', 'bulkMoveList', 'bulkMoveCount'); });
    $('#bulkDeleteBtn').on('click', function () { populateBulkModal('bulkDeleteReveal', 'bulkDeleteList', 'bulkDeleteCount'); });

    refresh();
  })();
</script>
