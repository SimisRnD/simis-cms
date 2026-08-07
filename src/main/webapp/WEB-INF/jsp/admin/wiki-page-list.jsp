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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="pageListWiki" class="com.simisinc.platform.domain.model.cms.Wiki" scope="request"/>
<jsp:useBean id="wikiPageList" class="java.util.ArrayList" scope="request"/>
<h5>Pages</h5>
<%--
  "New Page" no longer pre-computes a client-side slug or supplies a pageUniqueId -- the editor
  opens in a real blank/new state with just the typed title carried over, and the server alone
  generates the final, collision-checked uniqueId from that title at Save time (see
  WikiEditorWidget.execute()/post() and GenerateWikiPageUniqueIdCommand's dedupe loop). This is
  what makes the help text below true: a title matching an existing page can no longer be
  silently routed into editing (and overwriting) it.
--%>
<script nonce="${cspNonce}">
  function createWikiPage() {
    var title = document.getElementById("newWikiPageTitle").value.trim();
    if (!title) {
      return false;
    }
    var returnPage = encodeURIComponent("${widgetContext.uri}?wikiId=${pageListWiki.id}");
    window.location.href = "${ctx}/wiki-editor?wikiUniqueId=${pageListWiki.uniqueId}"
      + "&title=" + encodeURIComponent(title)
      + "&returnPage=" + returnPage;
    return false;
  }
  function deleteWikiPage(wikiPageId, title) {
    var returnPage = encodeURIComponent("${widgetContext.uri}?wikiId=${pageListWiki.id}");
    return confirmPostAction(
      "Are you sure you want to DELETE \"" + title + "\"? This cannot be undone.",
      "${widgetContext.uri}?action=deletePage&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&wikiPageId=" + wikiPageId + "&returnPage=" + returnPage);
  }
</script>
<form onsubmit="return createWikiPage();" class="margin-bottom-10">
  <div class="input-group">
    <input id="newWikiPageTitle" class="input-group-field" type="text" placeholder="New page title&#8230;" required>
    <div class="input-group-button">
      <button type="submit" class="button radius">New Page <i class="fa fa-plus"></i></button>
    </div>
  </div>
  <p class="help-text">If the title matches an existing page in this wiki, the new page is still
    created as a separate page -- a numbered suffix (e.g. "-2") is added to its URL so it can
    never silently open or overwrite the existing one.</p>
</form>
<table class="unstriped">
  <thead>
    <tr>
      <th width="55%">Title</th>
      <th width="20%">Modified</th>
      <th width="25%" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${wikiPageList}" var="wikiPage">
      <tr>
        <td>
          <c:out value="${wikiPage.title}" />
          <br /><small class="subheader"><c:out value="${wikiPage.uniqueId}" /></small>
        </td>
        <td>
          <c:if test="${!empty wikiPage.modified}">
            <small><c:out value="${date:relative(wikiPage.modified)}" /></small>
          </c:if>
        </td>
        <td class="text-center">
          <a href="${ctx}/${pageListWiki.uniqueId}/${wikiPage.uniqueId}" title="View"><i class="fa fa-eye"></i></a>
          <a href="${ctx}/wiki-editor?wikiUniqueId=${pageListWiki.uniqueId}&pageUniqueId=${wikiPage.uniqueId}&returnPage=${widgetContext.uri}%3FwikiId%3D${pageListWiki.id}" title="Edit"><i class="fa fa-edit"></i></a>
          <a href="#" title="Delete" onclick="return deleteWikiPage(${wikiPage.id}, '<c:out value="${js:escape(wikiPage.title)}" />');"><i class="fa fa-remove"></i></a>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty wikiPageList}">
      <tr>
        <td colspan="3">No pages yet -- create the first one above.</td>
      </tr>
    </c:if>
  </tbody>
</table>
