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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="menuTabList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="webPageList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="webPageMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="standardPages" class="java.util.HashMap" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th colspan="9"><strong>In Navigation Menu</strong></th>
    </tr>
    <tr>
      <%-- The bulk-selection checkbox column (issue #427) only applies to the "All Web Pages"
           rows below -- these rows show the same WebPage records again, so they are not
           independently selectable (see the class javadoc). A blank leading <td> is added to
           every row in this section so the table stays column-aligned with the header. --%>
      <th width="24"><input type="checkbox" id="selectAllPages" aria-label="Select all web pages on this page"></th>
      <th width="45"></th>
      <th width="60"></th>
      <th>Title</th>
      <th>Link</th>
      <th>Keywords, Description</th>
      <th>Modified</th>
      <th>Scheduled/Expires</th>
      <th>Views (30d)</th>
    </tr>
  </thead>
  <tbody>
  <c:forEach items="${menuTabList}" var="menuTab">
    <tr>
      <td></td>
      <c:choose>
        <c:when test="${menuTab.link eq '/'}">
          <td></td>
          <td><span class="success label">live</span></td>
        </c:when>
        <c:when test="${fn:contains(standardPages, menuTab.link)}">
          <td>
            <%--<a href="${ctx}${menuTab.link}"><i class="fa fa-check-circle"></i></a>--%>
            <a href="${ctx}/admin/web-page?webPage=${menuTab.link}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
          </td>
          <td><span class="success label">live</span></td>
        </c:when>
        <c:when test="${fn:contains(webPageMap, menuTab.link)}">
          <td>
              <%--<a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPageId=${group.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(webPage.link)}" />?');"><i class="fa fa-remove"></i></a>--%>
            <a href="${ctx}/admin/web-page?webPageId=${webPageMap[menuTab.link].id}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
          </td>
          <td>
            <c:choose>
              <c:when test="${webPageMap[menuTab.link].draft}"><span class="warning label">draft</span></c:when>
              <c:when test="${!empty webPageMap[menuTab.link].redirectUrl}"><span class="primary label">301</span></c:when>
              <c:when test="${empty webPageMap[menuTab.link].pageXml}">
                <span class="alert label">404</span>
              </c:when>
              <c:otherwise><span class="success label">live</span></c:otherwise>
            </c:choose>
            <c:if test="${webPageMap[menuTab.link].scheduled}">
              <br /><span class="secondary label"><i class="fa fa-clock"></i> scheduled</span>
            </c:if>
            <c:if test="${webPageMap[menuTab.link].expiringSoon}">
              <br /><span class="secondary label"><i class="fa fa-hourglass-end"></i> expiring</span>
            </c:if>
          </td>
        </c:when>
        <c:otherwise>
          <td>
            <a href="${ctx}${menuTab.link}"><i class="fa fa-plus"></i></a>
          </td>
          <td>
            <span class="alert label">404</span>
          </td>
        </c:otherwise>
      </c:choose>
      <td>
        <c:out value="${menuTab.name}" />
      </td>
      <td><a href="${ctx}${menuTab.link}"><c:out value="${menuTab.link}" /></a></td>
      <c:choose>
        <c:when test="${fn:contains(webPageMap, menuTab.link)}">
          <td>
            <c:if test="${!empty webPageMap[menuTab.link].keywords}">
              <small class="subheader">{<c:out value="${webPageMap[menuTab.link].keywords}" />}</small>
            </c:if>
            <c:if test="${!empty webPageMap[menuTab.link].keywords && !empty webPageMap[menuTab.link].description}">
              <br />
            </c:if>
            <c:if test="${!empty webPageMap[menuTab.link].description}">
              <small class="subheader"><c:out value="${webPageMap[menuTab.link].description}" /></small>
            </c:if>
          </td>
          <td>
            <small><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuTab.link].modified}" /></small>
          </td>
          <td>
            <c:if test="${!empty webPageMap[menuTab.link].publishAt}">
              <small>Publishes: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuTab.link].publishAt}" /></small>
            </c:if>
            <c:if test="${!empty webPageMap[menuTab.link].publishAt && !empty webPageMap[menuTab.link].expiresAt}"><br /></c:if>
            <c:if test="${!empty webPageMap[menuTab.link].expiresAt}">
              <small>Expires: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuTab.link].expiresAt}" /></small>
            </c:if>
          </td>
        </c:when>
        <c:otherwise>
          <td></td>
          <td></td>
          <td></td>
        </c:otherwise>
      </c:choose>
      <td>
        <c:if test="${fn:contains(webPageMap, menuTab.link)}">
          <fmt:formatNumber value="${empty webPageViewCountMap[webPageMap[menuTab.link].id] ? 0 : webPageViewCountMap[webPageMap[menuTab.link].id]}" />
        </c:if>
      </td>
    </tr>
    <c:forEach items="${menuTab.menuItemList}" var="menuItem">
      <tr>
        <td></td>
        <c:choose>
          <c:when test="${fn:contains(standardPages, menuItem.link)}">
            <td>
              <a href="${ctx}/admin/web-page?webPage=${menuItem.link}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
            </td>
            <td><span class="success label">live</span></td>
          </c:when>
          <c:when test="${fn:contains(webPageMap, menuItem.link)}">
            <td>
              <%--<a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPageId=${group.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(webPage.link)}" />?');"><i class="fa fa-remove"></i></a>--%>
              <a href="${ctx}/admin/web-page?webPageId=${webPageMap[menuItem.link].id}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
            </td>
            <td>
            <c:choose>
              <c:when test="${webPageMap[menuItem.link].draft}"><span class="warning label">draft</span></c:when>
              <c:when test="${!empty webPageMap[menuItem.link].redirectUrl}"><span class="primary label">301</span></c:when>
              <c:when test="${fn:startsWith(menuItem.link, '/directory/')}"><span class="success label">live</span></c:when>
              <c:when test="${empty webPageMap[menuItem.link].pageXml}"><span class="alert label">404</span></c:when>
              <c:otherwise><span class="success label">live</span></c:otherwise>
            </c:choose>
            <c:if test="${webPageMap[menuItem.link].scheduled}">
              <br /><span class="secondary label"><i class="fa fa-clock"></i> scheduled</span>
            </c:if>
            <c:if test="${webPageMap[menuItem.link].expiringSoon}">
              <br /><span class="secondary label"><i class="fa fa-hourglass-end"></i> expiring</span>
            </c:if>
            </td>
          </c:when>
          <c:when test='${fn:contains(menuItem.link, "#")}'>
            <td></td>
            <td></td>
          </c:when>
          <c:otherwise>
            <td>
              <a href="${ctx}${menuItem.link}"><i class="fa fa-plus"></i></a>
            </td>
            <td>
              <c:choose>
                <c:when test="${fn:startsWith(menuItem.link, '/directory/')}"><span class="success label">live</span></c:when>
                <c:otherwise><span class="alert label">404</span></c:otherwise>
              </c:choose>
            </td>
          </c:otherwise>
        </c:choose>
        <td>
          <i class="fa fa-angle-right"></i>
          <c:out value="${menuItem.name}" />
        </td>
        <td><a href="${ctx}${menuItem.link}"><c:out value="${menuItem.link}" /></a></td>
        <c:choose>
          <c:when test="${fn:contains(webPageMap, menuItem.link)}">
            <td>
              <c:if test="${!empty webPageMap[menuItem.link].keywords}">
                <small class="subheader">{<c:out value="${webPageMap[menuItem.link].keywords}" />}</small>
              </c:if>
              <c:if test="${!empty webPageMap[menuItem.link].description}">
                <br /><small class="subheader"><c:out value="${webPageMap[menuItem.link].description}" /></small>
              </c:if>
            </td>
            <td>
              <small><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuItem.link].modified}" /></small>
            </td>
            <td>
              <c:if test="${!empty webPageMap[menuItem.link].publishAt}">
                <small>Publishes: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuItem.link].publishAt}" /></small>
              </c:if>
              <c:if test="${!empty webPageMap[menuItem.link].publishAt && !empty webPageMap[menuItem.link].expiresAt}"><br /></c:if>
              <c:if test="${!empty webPageMap[menuItem.link].expiresAt}">
                <small>Expires: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPageMap[menuItem.link].expiresAt}" /></small>
              </c:if>
            </td>
          </c:when>
          <c:otherwise>
            <td></td>
            <td></td>
            <td></td>
          </c:otherwise>
        </c:choose>
        <td>
          <c:if test="${fn:contains(webPageMap, menuItem.link)}">
            <fmt:formatNumber value="${empty webPageViewCountMap[webPageMap[menuItem.link].id] ? 0 : webPageViewCountMap[webPageMap[menuItem.link].id]}" />
          </c:if>
        </td>
      </tr>
    </c:forEach>
  </c:forEach>
  <tr>
    <td colspan="9">
      <strong>All Web Pages</strong>
      <small class="subheader">Every page record in the system, including the ones already shown above in the navigation menu.</small>
      <br />
      <small class="subheader">
        <fmt:formatNumber value="${webPageTotalCount}" /> total &ndash;
        <fmt:formatNumber value="${webPageLiveCount}" /> live,
        <fmt:formatNumber value="${webPageDraftCount}" /> draft,
        <fmt:formatNumber value="${webPageRedirectCount}" /> redirects,
        <fmt:formatNumber value="${webPageBrokenCount}" /> broken
      </small>
    </td>
  </tr>
  <tr>
    <td colspan="9">
      <form method="get" autocomplete="off" class="grid-x grid-margin-x align-bottom">
        <div class="cell medium-5">
          <label>Search
            <input type="text" name="q" value="<c:out value='${q}'/>" placeholder="Title, link, or keywords" />
          </label>
        </div>
        <div class="cell medium-4">
          <label>Status
            <select name="status">
              <option value="" ${empty status ? 'selected' : ''}>All</option>
              <option value="draft" ${status eq 'draft' ? 'selected' : ''}>Draft</option>
              <option value="redirect" ${status eq 'redirect' ? 'selected' : ''}>Redirect (301)</option>
              <option value="broken" ${status eq 'broken' ? 'selected' : ''}>Broken (no content)</option>
              <option value="live" ${status eq 'live' ? 'selected' : ''}>Live</option>
              <%-- Archived pages are excluded from every other option above by default (issue #427);
                   this is the only way to see them in the admin list. --%>
              <option value="archived" ${status eq 'archived' ? 'selected' : ''}>Archived</option>
            </select>
          </label>
        </div>
        <div class="cell medium-3">
          <input type="submit" class="button radius" value="Filter" />
          <c:if test="${!empty q || !empty status}">
            <a href="${widgetContext.uri}" class="button radius secondary">Clear</a>
          </c:if>
        </div>
      </form>
    </td>
  </tr>
  <tr>
    <td colspan="9" style="padding:0;">
      <div id="bulkActionsBar" class="callout radius" style="display:none;padding:10px 15px;margin:0;">
        <span id="bulkSelectedCount"></span>
        <button type="button" class="button tiny radius" id="bulkPublishBtn">Publish</button>
        <button type="button" class="button tiny radius" id="bulkUnpublishBtn">Unpublish</button>
        <button type="button" class="button tiny radius" id="bulkArchiveBtn">Archive</button>
        <%-- Issue #427 review fix: bulkDelete is admin-only server-side (WebPageListWidget#post), same
             as the single-item "Delete Page" button on web-page-form.jsp -- a content-manager must
             never even see this affordance, not just have it silently rejected on click. --%>
        <c:if test="${userSession.hasRole('admin')}">
          <button type="button" class="button tiny alert radius" id="bulkDeleteBtn">Delete</button>
        </c:if>
      </div>
    </td>
  </tr>
  <c:forEach items="${webPageList}" var="webPage">
    <tr>
      <td><input type="checkbox" class="pageRowCheckbox" value="${webPage.id}" data-title="${fn:escapeXml(webPage.title)}" aria-label="Select ${fn:escapeXml(webPage.title)}"></td>
      <td nowrap="true">
        <%--<a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPageId=${group.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(webPage.link)}" />?');"><i class="fa fa-remove"></i></a>--%>
        <a href="${ctx}/admin/web-page?webPageId=${webPage.id}&returnPage=/admin/web-pages"><i class="fa fa-edit"></i></a>
        <c:if test="${userSession.hasRole('admin')}">
          <a href="${ctx}/admin/web-page-designer?webPage=${webPage.link}&returnPage=/admin/web-pages"><i class="fa fa-code"></i></a>
        </c:if>
      </td>
      <td>
        <c:choose>
          <c:when test="${!empty webPage.archived}"><span class="label secondary radius">archived</span></c:when>
          <c:when test="${webPage.draft}"><span class="warning label">draft</span></c:when>
          <c:when test="${!empty webPage.redirectUrl}"><span class="primary label">301</span></c:when>
          <c:when test="${fn:contains(standardPages, webPage.link)}">
            <span class="success label">live</span>
          </c:when>
          <c:when test="${fn:startsWith(webPage.link, '/directory/')}"><span class="success label">live</span></c:when>
          <c:when test="${empty webPage.pageXml}"><span class="alert label">404</span></c:when>
          <c:otherwise><span class="success label">live</span></c:otherwise>
        </c:choose>
        <c:if test="${webPage.scheduled}">
          <br /><span class="secondary label"><i class="fa fa-clock"></i> scheduled</span>
        </c:if>
        <c:if test="${webPage.expiringSoon}">
          <br /><span class="secondary label"><i class="fa fa-hourglass-end"></i> expiring</span>
        </c:if>
        <c:if test="${!empty webPageReviewStatusMap[webPage.id]}">
          <br /><span class="secondary label"><i class="fa fa-clipboard-check"></i> <c:out value="${webPageReviewStatusMap[webPage.id]}" /></span>
        </c:if>
      </td>
      <td>
        <c:out value="${webPage.title}" />
      </td>
      <td>
        <a href="${ctx}${webPage.link}"><c:out value="${webPage.link}" /></a>
        <c:if test="${!empty webPage.redirectUrl}">
          <i class="fa fa-long-arrow-right"></i> <c:out value="${webPage.redirectUrl}" />
        </c:if>
      </td>
      <td>
        <c:if test="${!empty webPage.keywords}">
          <small class="subheader">{<c:out value="${webPage.keywords}" />}</small>
        </c:if>
        <c:if test="${!empty webPage.description}">
          <br /><small class="subheader"><c:out value="${webPage.description}" /></small>
        </c:if>
      </td>
      <td>
        <small><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPage.modified}" /></small>
      </td>
      <td>
        <c:if test="${!empty webPage.publishAt}">
          <small>Publishes: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPage.publishAt}" /></small>
        </c:if>
        <c:if test="${!empty webPage.publishAt && !empty webPage.expiresAt}"><br /></c:if>
        <c:if test="${!empty webPage.expiresAt}">
          <small>Expires: <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${webPage.expiresAt}" /></small>
        </c:if>
      </td>
      <td>
        <fmt:formatNumber value="${empty webPageViewCountMap[webPage.id] ? 0 : webPageViewCountMap[webPage.id]}" />
      </td>
    </tr>
  </c:forEach>
  <c:if test="${empty webPageList}">
      <tr>
        <td colspan="9">No web pages were found</td>
      </tr>
  </c:if>
  </tbody>
</table>
<a class="button radius primary" href="${ctx}/admin/web-page?returnPage=/admin/web-pages">Add a Web Page <i class="fa fa-arrow-circle-right"></i></a>
<%-- Bulk action reveal modals -- selection is scoped to the "All Web Pages" rows currently checked
     on this page (see the JS below); each is populated at open time with the live selection, not
     just a count. Mirrors calendar-event-list.jsp's bulk reveal modals (issue #427/PR #911 pattern). --%>
<div class="reveal" id="bulkPublishReveal" role="dialog" aria-modal="true" aria-labelledby="bulkPublishRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkPublishRevealTitle">Publish <span id="bulkPublishCount">0</span> Web Page(s)</h4>
  <ul id="bulkPublishList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkPublish"/>
    <input type="submit" class="button radius" value="Publish Pages"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkUnpublishReveal" role="dialog" aria-modal="true" aria-labelledby="bulkUnpublishRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkUnpublishRevealTitle">Unpublish <span id="bulkUnpublishCount">0</span> Web Page(s)</h4>
  <p class="help-text">Unpublished pages are taken out of live view and marked as drafts.</p>
  <ul id="bulkUnpublishList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkUnpublish"/>
    <input type="submit" class="button radius" value="Unpublish Pages"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkArchiveReveal" role="dialog" aria-modal="true" aria-labelledby="bulkArchiveRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkArchiveRevealTitle">Archive <span id="bulkArchiveCount">0</span> Web Page(s)</h4>
  <p class="help-text">Archived pages are hidden from this list by default. They can still be found with the Archived status filter.</p>
  <ul id="bulkArchiveList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkArchive"/>
    <input type="submit" class="button radius" value="Archive Pages"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkDeleteReveal" role="dialog" aria-modal="true" aria-labelledby="bulkDeleteRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkDeleteRevealTitle">Delete <span id="bulkDeleteCount">0</span> Web Page(s)</h4>
  <p class="help-text">This permanently removes the selected web pages. This cannot be undone.</p>
  <ul id="bulkDeleteList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkDelete"/>
    <input type="submit" class="button alert radius" value="Delete Pages"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<script nonce="${cspNonce}">
  (function () {
    var $selectAll = $('#selectAllPages');
    var $rows = $('.pageRowCheckbox');
    var $bar = $('#bulkActionsBar');
    var $count = $('#bulkSelectedCount');

    function selected() {
      return $rows.filter(':checked');
    }

    function refresh() {
      var n = selected().length;
      $count.text(n + (n === 1 ? ' page selected  ' : ' pages selected  '));
      $bar.toggle(n > 0);
      $selectAll.prop('indeterminate', n > 0 && n < $rows.length);
      $selectAll.prop('checked', n > 0 && n === $rows.length);
    }

    // Populates one bulk modal's hidden webPageId fields and visible title list from the currently
    // checked rows, so the admin sees exactly what is about to be affected before confirming.
    function populateBulkModal(revealId, listId, countId) {
      var $reveal = $('#' + revealId);
      var $form = $reveal.find('form');
      var $list = $('#' + listId);
      $form.find('input[name="webPageId"]').remove();
      $list.empty();
      selected().each(function () {
        var $checkbox = $(this);
        $form.append($('<input type="hidden" name="webPageId">').val($checkbox.val()));
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
    $('#bulkDeleteBtn').on('click', function () { populateBulkModal('bulkDeleteReveal', 'bulkDeleteList', 'bulkDeleteCount'); });

    refresh();
  })();
</script>