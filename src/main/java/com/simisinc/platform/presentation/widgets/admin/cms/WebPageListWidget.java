/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.presentation.widgets.admin.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.application.cms.SaveWebPageCommand;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.XMLPageLoader;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.Page;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The full /admin/web-pages list is split into two sections: pages currently in the site
 * navigation menu (top), and the complete list of every {@link WebPage} record (bottom, labeled
 * "All Web Pages"). Only the bottom section supports search/status filtering (issue #497) -- the
 * nav-menu section is already organized by its own hierarchy and every page shown there also
 * appears again in the full list below, so filtering it separately would just be confusing.
 *
 * <p>The "All Web Pages" section also offers row checkboxes and a bulk-action toolbar (issue #427),
 * mirroring the bulk-action mechanics PR #911 shipped for /admin/calendars' calendar-event-list.jsp:
 * Publish/Unpublish reuse the exact single-item save path ({@link SaveWebPageCommand#saveWebPage},
 * the same one {@code WebPageFormWidget}'s publish checkbox uses, so the same domain events fire),
 * Archive is new to web pages entirely (a new {@code archived} column, mirroring
 * {@code CalendarEvent#archived}), and Delete reuses {@code WebPageFormWidget#action}'s single-item
 * delete path exactly, including its admin-only gate -- stricter than Publish/Unpublish/Archive's
 * admin-or-content-manager gate, matching what single-item delete already requires. There is no
 * "Move" action here: web pages have no category/collection/taxonomy field to move between (only
 * {@code template}/{@code solutionType}, neither a collection concept); page organization is
 * nav-menu placement via {@code MenuTab}/{@code MenuItem}, a hierarchy, not a flat bucket a bulk
 * action could reassign.
 *
 * @author matt rajkowski
 * @created 4/25/18 5:45 PM
 */
public class WebPageListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/web-page-list.jsp";

  // A crafted POST is the only thing this bounds -- normal usage never approaches it, since
  // selection is scoped to the current page. An id list over this cap is rejected outright, never
  // silently truncated. Mirrors CalendarEventListWidget.MAX_BULK_SELECTION exactly (issue #427).
  static final int MAX_BULK_SELECTION = 100;

  // Code-review fix (issue #427): per-row failure reasons were previously recorded only to the
  // audit log and never returned in the response. Only the first MAX_DETAIL_LINES failed/not-found
  // rows are spelled out inline in the aggregate message, to keep a large batch's message readable;
  // the rest are still counted and still fully detailed in the audit log.
  static final int MAX_DETAIL_LINES = 5;

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the menu tabs
    List<MenuTab> menuTabList = LoadMenuTabsCommand.findAllIncludeMenuItemList();
    context.getRequest().setAttribute("menuTabList", menuTabList);

    // Load the built in pages (just the ones which the pages use) -- needed before filtering the
    // "All Web Pages" list below, since a standard/built-in page is always "live" regardless of
    // whether it has stored page_xml.
    Map<String, Page> standardPages = new HashMap<String, Page>();
    XMLPageLoader xmlPageConfig = new XMLPageLoader(standardPages);
    xmlPageConfig.loadWidgetLibrary(context.getRequest().getServletContext(), "/WEB-INF/widgets/widget-library.xml");
    xmlPageConfig.addFile("/WEB-INF/web-layouts/page/page-layout.xml");
    xmlPageConfig.load(context.getRequest().getServletContext());
    context.getRequest().setAttribute("standardPages", standardPages);

    LOG.debug("Widgets: " + xmlPageConfig.getWidgetLibrary().size());
    LOG.debug("Standard pages: " + standardPages.size());

    // Load every web page (used to resolve nav-menu items to their record, e.g. draft/301 status)
    List<WebPage> webPageList = WebPageRepository.findAll();

    // Create a map of links to pages
    Map<String, WebPage> webPageMap = new HashMap<>();
    for (WebPage webPage : webPageList) {
      webPageMap.put(webPage.getLink(), webPage);
    }
    context.getRequest().setAttribute("webPageMap", webPageMap);

    // Status-count summary (issue #497): always computed over the full, unfiltered list so it
    // stays a stable "at a glance" total regardless of the active search/status filter below.
    // Every page falls into exactly one bucket, using the same live/broken/draft/redirect
    // derivation as the status filters further down.
    int webPageDraftCount = 0;
    int webPageRedirectCount = 0;
    int webPageLiveCount = 0;
    int webPageBrokenCount = 0;
    for (WebPage webPage : webPageList) {
      if (webPage.getDraft()) {
        webPageDraftCount++;
      } else if (StringUtils.isNotBlank(webPage.getRedirectUrl())) {
        webPageRedirectCount++;
      } else if (standardPages.containsKey(webPage.getLink())
          || webPage.getLink().startsWith("/directory/")
          || StringUtils.isNotBlank(webPage.getPageXml())) {
        webPageLiveCount++;
      } else {
        webPageBrokenCount++;
      }
    }
    context.getRequest().setAttribute("webPageTotalCount", webPageList.size());
    context.getRequest().setAttribute("webPageLiveCount", webPageLiveCount);
    context.getRequest().setAttribute("webPageDraftCount", webPageDraftCount);
    context.getRequest().setAttribute("webPageRedirectCount", webPageRedirectCount);
    context.getRequest().setAttribute("webPageBrokenCount", webPageBrokenCount);

    // Filter the "All Web Pages" list (search box + status dropdown)
    String searchTerm = context.getParameter("q");
    String status = context.getParameter("status");

    WebPageSpecification specification = new WebPageSpecification();
    if (StringUtils.isNotBlank(searchTerm)) {
      specification.setSearchTerm(searchTerm);
    }
    // issue #427: archived pages are excluded from the "All Web Pages" list by default -- "Archived"
    // is its own status option (rather than combined with draft/redirect/live/broken) since a page's
    // archived state is orthogonal to whether it's live/draft/redirected/broken. Mirrors
    // CalendarEventListWidget's identical status-dropdown handling.
    if ("archived".equals(status)) {
      specification.setArchivedOnly(true);
    } else {
      specification.setArchivedOnly(false);
      if ("draft".equals(status)) {
        specification.setDraft(true);
      } else if ("redirect".equals(status)) {
        specification.setHasRedirect(true);
      }
    }
    List<WebPage> filteredWebPageList = WebPageRepository.findAll(specification, null);

    // "live"/"broken" aren't stored columns -- they're derived the same way the JSP derives them
    // (draft/redirect already excluded a page from reaching here; a standard/built-in page or a
    // page under /directory/ is always live regardless of its stored page_xml).
    if ("broken".equals(status)) {
      List<WebPage> brokenList = new ArrayList<>();
      for (WebPage webPage : filteredWebPageList) {
        if (!webPage.getDraft() && StringUtils.isBlank(webPage.getRedirectUrl())
            && !standardPages.containsKey(webPage.getLink())
            && !webPage.getLink().startsWith("/directory/")
            && StringUtils.isBlank(webPage.getPageXml())) {
          brokenList.add(webPage);
        }
      }
      filteredWebPageList = brokenList;
    } else if ("live".equals(status)) {
      List<WebPage> liveList = new ArrayList<>();
      for (WebPage webPage : filteredWebPageList) {
        if (!webPage.getDraft() && StringUtils.isBlank(webPage.getRedirectUrl())
            && (standardPages.containsKey(webPage.getLink())
                || webPage.getLink().startsWith("/directory/")
                || StringUtils.isNotBlank(webPage.getPageXml()))) {
          liveList.add(webPage);
        }
      }
      filteredWebPageList = liveList;
    }
    context.getRequest().setAttribute("webPageList", filteredWebPageList);

    // Governed publish workflow status per page (#407), keyed by web_page_id -- only pages with a
    // pending draft carry an interesting label; a page with no draft is left out of the map and the
    // JSP falls back to its existing draft/live/redirect/broken derivation for those rows.
    Map<Long, String> webPageReviewStatusMap = new HashMap<>();
    for (WebPage webPage : filteredWebPageList) {
      if (webPage.hasDraftContent()) {
        webPageReviewStatusMap.put(webPage.getId(), ContentReviewCommand.listStatusLabel(webPage));
      }
    }
    context.getRequest().setAttribute("webPageReviewStatusMap", webPageReviewStatusMap);

    // Trailing 30-day view count per page (#497), keyed by web_page_id -- one bulk query for the
    // whole page set rather than a per-row lookup. This must cover every page in webPageList, not
    // just filteredWebPageList: the "In Navigation Menu" section above always renders from the
    // unfiltered webPageMap regardless of the active search/status filter, so scoping this query to
    // the filtered subset would silently show 0 views for a nav-menu page excluded by the filter. A
    // page absent from the map (no hits in the window, or a WebPage with a null/unset id) has zero
    // views; the JSP must treat a missing key as zero rather than blank.
    List<Long> webPageIdList = new ArrayList<>();
    for (WebPage webPage : webPageList) {
      webPageIdList.add(webPage.getId());
    }
    Map<Long, Long> webPageViewCountMap = WebPageHitRepository.countViewsByWebPageId(webPageIdList, 30);
    context.getRequest().setAttribute("webPageViewCountMap", webPageViewCountMap);

    // Echo the filter values back so the form keeps its state
    context.getRequest().setAttribute("q", searchTerm);
    context.getRequest().setAttribute("status", status);

    // Show the JSP
    context.setJsp(JSP);
    return context;
  }

  /**
   * Bulk actions selected from /admin/web-pages' checkbox + action-bar UI (issue #427, mirroring
   * the bulk-action mechanics PR #911 shipped for /admin/calendars' CalendarEventListWidget).
   * Unlike the calendar precedent, bulkDelete is gated more strictly than the other three commands
   * -- admin-only, matching {@code WebPageFormWidget#action}'s existing single-item delete gate,
   * rather than the calendar precedent's uniform admin-or-content-manager gate for every command.
   */
  public WidgetContext post(WidgetContext context) {

    String command = context.getParameter("command");

    if ("bulkDelete".equals(command)) {
      // Matches WebPageFormWidget#action's existing admin-only delete gate exactly -- stricter
      // than the admin-or-content-manager pairing below, so it is checked before renewing the
      // form token or resolving a selection.
      if (!context.hasRole("admin")) {
        LOG.warn("No permission to delete web pages");
        return context;
      }
      context.getUserSession().renewFormToken();
      return bulkDeleteAction(context);
    }

    // Publish/Unpublish/Archive match /admin/web-pages' own page access (admin,content-manager;
    // see admin-layout.xml) and WebPageFormWidget's publish/unpublish save path, which has no
    // additional in-code role check beyond that.
    if (!(context.hasRole("admin") || context.hasRole("content-manager"))) {
      LOG.warn("No permission to modify web pages");
      return context;
    }

    // Don't accept multiple form posts
    context.getUserSession().renewFormToken();

    if ("bulkPublish".equals(command)) {
      return bulkPublishAction(context, false);
    }
    if ("bulkUnpublish".equals(command)) {
      return bulkPublishAction(context, true);
    }
    if ("bulkArchive".equals(command)) {
      return bulkArchiveAction(context);
    }
    return context;
  }

  /**
   * Handles both bulkPublish ({@code draft == false}) and bulkUnpublish ({@code draft == true}) --
   * the same toggle {@code WebPageFormWidget#post}'s "publish" checkbox drives for a single page.
   * Reuses {@link SaveWebPageCommand#saveWebPage} rather than a raw repository update, so this
   * fires the exact same {@code WebPagePublishedEvent}/{@code WebPageUpdatedEvent} +
   * {@link PublishEventCachePurgeHandler} calls the single-item save already fires -- a raw
   * repository call would silently skip all of that.
   */
  private WidgetContext bulkPublishAction(WidgetContext context, boolean draft) {
    List<Long> webPageIds = resolveSelectedWebPageIds(context);
    if (webPageIds == null) {
      return rejectBulkSelection(context);
    }
    if (webPageIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    String eventType = draft ? "content.unpublish" : "content.publish";
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long webPageId : webPageIds) {
      WebPage webPageBean = WebPageRepository.findById(webPageId);
      if (webPageBean == null) {
        ++notFound;
        rowIssues.add("#" + webPageId + ": not found");
        continue;
      }
      webPageBean.setDraft(draft);
      // Matches WebPageFormWidget#post exactly (both createdBy and modifiedBy are set to the
      // acting user there too) -- SaveWebPageCommand.saveWebPage() only persists modifiedBy from
      // whatever this bean's createdBy carries, not this bean's own modifiedBy.
      webPageBean.setCreatedBy(context.getUserId());
      webPageBean.setModifiedBy(context.getUserId());

      WebPage result;
      String failureDetails = null;
      try {
        result = SaveWebPageCommand.saveWebPage(webPageBean);
      } catch (DataException e) {
        result = null;
        failureDetails = e.getMessage();
      }
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(webPageBean.getTitle() + " (#" + webPageBean.getId() + "): "
            + (failureDetails != null ? failureDetails : "save failed"));
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, eventType, outcome,
          "web_page", String.valueOf(webPageBean.getId()), webPageBean.getTitle(),
          failureDetails != null ? failureDetails : "(bulk)");
    }

    setBulkResultMessage(context, draft ? "unpublished" : "published", succeeded, webPageIds.size(), notFound, failed,
        rowIssues);
    context.setRedirect("/admin/web-pages");
    return context;
  }

  /**
   * Archiving is new to web pages entirely (issue #427) -- there is no single-item precedent to
   * reuse, so this is a plain repository save (archiving is not a publish-workflow event; no
   * WebPageArchivedEvent exists). It DOES take the page offline though -- see
   * PageServlet#isArchivedBlockedFromPublicAccess (the request-time gate; findByLink itself still
   * finds archived pages, deliberately, since admin flows like WebPageFormWidget need to) and the
   * archived exclusions added to WebPageRepository#search/SitemapServlet#webPageEntries -- so a
   * cache-purge signal is still fired here.
   */
  private WidgetContext bulkArchiveAction(WidgetContext context) {
    List<Long> webPageIds = resolveSelectedWebPageIds(context);
    if (webPageIds == null) {
      return rejectBulkSelection(context);
    }
    if (webPageIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    Timestamp now = new Timestamp(System.currentTimeMillis());
    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long webPageId : webPageIds) {
      WebPage webPage = WebPageRepository.findById(webPageId);
      if (webPage == null) {
        ++notFound;
        rowIssues.add("#" + webPageId + ": not found");
        continue;
      }
      webPage.setArchived(now);
      webPage.setModifiedBy(context.getUserId());
      WebPage result = WebPageRepository.save(webPage);
      String outcome = result != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (result != null) {
        ++succeeded;
        // Issue #427: archiving now takes the page offline (PageServlet#isArchivedBlockedFromPublicAccess),
        // so it needs the same cache-purge signal as publish/unpublish/delete.
        PublishEventCachePurgeHandler.onPageUpdated(result);
      } else {
        ++failed;
        rowIssues.add(webPage.getTitle() + " (#" + webPage.getId() + "): save failed");
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.archive", outcome,
          "web_page", String.valueOf(webPage.getId()), webPage.getTitle(), "(bulk)");
    }

    setBulkResultMessage(context, "archived", succeeded, webPageIds.size(), notFound, failed, rowIssues);
    context.setRedirect("/admin/web-pages");
    return context;
  }

  /**
   * Reuses {@code WebPageFormWidget#action}'s "deletePage" branch exactly, including the
   * try/catch -- {@link WebPageRepository#remove} returns {@code void}, not {@code boolean} (unlike
   * most other repositories' remove methods), so success is inferred by the absence of an
   * exception, and {@link PublishEventCachePurgeHandler#onPageDeleted} is fired per successfully
   * removed row, matching the single-item path.
   */
  private WidgetContext bulkDeleteAction(WidgetContext context) {
    List<Long> webPageIds = resolveSelectedWebPageIds(context);
    if (webPageIds == null) {
      return rejectBulkSelection(context);
    }
    if (webPageIds.isEmpty()) {
      return rejectEmptySelection(context);
    }

    int succeeded = 0;
    int notFound = 0;
    int failed = 0;
    List<String> rowIssues = new ArrayList<>();
    for (Long webPageId : webPageIds) {
      WebPage webPage = WebPageRepository.findById(webPageId);
      if (webPage == null) {
        ++notFound;
        rowIssues.add("#" + webPageId + ": not found");
        continue;
      }
      boolean removed;
      String failureDetails = null;
      try {
        WebPageRepository.remove(webPage);
        PublishEventCachePurgeHandler.onPageDeleted(webPage.getLink());
        removed = true;
      } catch (Exception e) {
        removed = false;
        failureDetails = e.getMessage();
      }
      String outcome = removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE;
      if (removed) {
        ++succeeded;
      } else {
        ++failed;
        rowIssues.add(webPage.getTitle() + " (#" + webPage.getId() + "): "
            + (failureDetails != null ? failureDetails : "delete failed"));
      }
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", outcome,
          "web_page", String.valueOf(webPage.getId()), webPage.getTitle(), "(bulk)");
    }

    setBulkResultMessage(context, "deleted", succeeded, webPageIds.size(), notFound, failed, rowIssues);
    context.setRedirect("/admin/web-pages");
    return context;
  }

  /**
   * Parses and dedupes the selected web page ids from the repeated {@code webPageId} hidden inputs
   * the bulk modals inject, silently dropping any non-numeric entry (a tampered value is not a
   * batch-ending error). Returns {@code null} when the list exceeds {@link #MAX_BULK_SELECTION} --
   * the whole request is then rejected rather than silently truncated, since truncation could apply
   * the action to a different subset of pages than the one the admin reviewed and confirmed.
   * Mirrors CalendarEventListWidget#resolveSelectedEventIds exactly.
   */
  private List<Long> resolveSelectedWebPageIds(WidgetContext context) {
    String[] rawIds = context.getParameterMap().get("webPageId");
    Set<Long> ids = new LinkedHashSet<>();
    if (rawIds != null) {
      for (String rawId : rawIds) {
        try {
          ids.add(Long.parseLong(rawId.trim()));
        } catch (NumberFormatException e) {
          // Dropped, not treated as a batch-ending error
        }
      }
    }
    if (ids.size() > MAX_BULK_SELECTION) {
      LOG.warn("Bulk web page action rejected: " + ids.size() + " ids exceeds MAX_BULK_SELECTION ("
          + MAX_BULK_SELECTION + ")");
      return null;
    }
    return new ArrayList<>(ids);
  }

  private WidgetContext rejectBulkSelection(WidgetContext context) {
    context.setErrorMessage("Too many web pages were selected (maximum " + MAX_BULK_SELECTION
        + "). Select fewer pages and try again.");
    context.setRedirect("/admin/web-pages");
    return context;
  }

  private WidgetContext rejectEmptySelection(WidgetContext context) {
    context.setErrorMessage("No web pages were selected");
    context.setRedirect("/admin/web-pages");
    return context;
  }

  /**
   * Sets the single aggregate result message every bulk action reports (page_messages.jspf renders
   * exactly one of success/warning/error). Mirrors CalendarEventListWidget#setBulkResultMessage.
   *
   * <p>{@code rowIssues} carries a human-readable "title (#id): reason" entry for every not-found
   * or failed row (issue #427 code-review finding: these reasons were previously recorded only to
   * the admin-only audit log, never returned to the caller). Only the first
   * {@link #MAX_DETAIL_LINES} are spelled out inline to keep the message readable; the full detail
   * for every row remains in the audit log regardless.
   */
  private void setBulkResultMessage(WidgetContext context, String verb, int succeeded, int totalSelected,
      int notFound, int failed, List<String> rowIssues) {
    StringBuilder sb = new StringBuilder();
    sb.append(succeeded).append(" of ").append(totalSelected).append(" selected web page")
        .append(totalSelected == 1 ? "" : "s").append(" ").append(verb).append(".");
    if (notFound > 0) {
      sb.append(" Not found: ").append(notFound).append(".");
    }
    if (failed > 0) {
      sb.append(" Failed: ").append(failed).append(".");
    }
    appendRowIssueDetails(sb, rowIssues);
    if (succeeded == 0) {
      context.setErrorMessage(sb.toString());
    } else if (succeeded != totalSelected) {
      context.setWarningMessage(sb.toString());
    } else {
      context.setSuccessMessage(sb.toString());
    }
  }

  /**
   * Appends up to {@link #MAX_DETAIL_LINES} "title (#id): reason" entries to the aggregate message,
   * summarizing any remainder by count only. See {@link #setBulkResultMessage} for why this exists.
   */
  private void appendRowIssueDetails(StringBuilder sb, List<String> rowIssues) {
    if (rowIssues == null || rowIssues.isEmpty()) {
      return;
    }
    sb.append(" (");
    int shown = Math.min(rowIssues.size(), MAX_DETAIL_LINES);
    for (int i = 0; i < shown; i++) {
      if (i > 0) {
        sb.append("; ");
      }
      sb.append(rowIssues.get(i));
    }
    if (rowIssues.size() > shown) {
      sb.append("; and ").append(rowIssues.size() - shown).append(" more");
    }
    sb.append(")");
  }
}
