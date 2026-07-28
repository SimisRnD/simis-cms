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
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="pageListWiki" class="com.simisinc.platform.domain.model.cms.Wiki" scope="request"/>
<jsp:useBean id="wikiPageList" class="java.util.ArrayList" scope="request"/>
<h5>Pages</h5>
<%--
  The slug computed here is only a preview for the URL the admin is about to land on -- the
  server remains authoritative: GenerateWikiPageUniqueIdCommand derives the real, final,
  collision-checked uniqueId from whatever title is actually saved. This intentionally does not
  need to match that logic exactly.
--%>
<script nonce="${cspNonce}">
  function slugPreview(title) {
    return title
      .toLowerCase()
      .replace(/&/g, "and")
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "") || "page";
  }
  function createWikiPage() {
    var title = document.getElementById("newWikiPageTitle").value.trim();
    if (!title) {
      return false;
    }
    var slug = slugPreview(title);
    var returnPage = encodeURIComponent("${widgetContext.uri}?wikiId=${pageListWiki.id}");
    window.location.href = "${ctx}/wiki-editor?wikiUniqueId=${pageListWiki.uniqueId}"
      + "&pageUniqueId=" + encodeURIComponent(slug)
      + "&title=" + encodeURIComponent(title)
      + "&returnPage=" + returnPage;
    return false;
  }
</script>
<form onsubmit="return createWikiPage();" class="margin-bottom-10">
  <div class="input-group">
    <input id="newWikiPageTitle" class="input-group-field" type="text" placeholder="New page title&#8230;" required>
    <div class="input-group-button">
      <button type="submit" class="button radius">New Page <i class="fa fa-plus"></i></button>
    </div>
  </div>
</form>
<table class="unstriped">
  <thead>
    <tr>
      <th width="60%">Title</th>
      <th width="20%">Modified</th>
      <th width="20%" class="text-center">Action</th>
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
