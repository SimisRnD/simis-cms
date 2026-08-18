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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<%-- Handle image uploads --%>
<script nonce="${cspNonce}">
    function SavePhoto(e) {
        var file = e.files[0]; // similar to: document.getElementById("file").files[0]
        var formData = new FormData();
        formData.append("file", file);
        var xhr = new XMLHttpRequest();
        xhr.onreadystatechange = function() {
            if (this.readyState === 4) {
                if (this.status === 200) {
                    var fileData = JSON.parse(this.responseText);
                    document.getElementById("imageUrl").value = fileData.location;
                    document.getElementById("imageUrlPreview").src = fileData.location;
                } else {
                    document.getElementById("imageFile").value = "";
                    // Report what the server actually rejected (issue #1189). This used to always
                    // claim the file was not a .jpg or .png, which is wrong -- and actively
                    // misleading -- for a permission, storage, or size failure, all of which land
                    // here too.
                    var message = 'There was an error with the file. Make sure to use a .jpg or .png';
                    try {
                        var errorData = JSON.parse(this.responseText);
                        if (errorData && errorData.error) {
                            message = errorData.error;
                        }
                    } catch (ignored) {
                        // Not a JSON body (e.g. an HTML error page), so keep the generic message
                    }
                    alert(message);
                }
            }
        };
        xhr.open("POST", '${ctx}/image-upload?widget=imageUpload1&token=${userSession.formToken}');
        xhr.send(formData);
    }
    <c:if test="${userSession.hasRole('admin')}">
      function deletePage() {
          if (!confirm("Are you sure you want to DELETE this page?")) {
              return;
          }
          postAction('${widgetContext.uri}?action=deletePage&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPageId=${webPage.id}');
      }
    </c:if>
    <%-- Bound here instead of inline onchange=/onclick= -- CSP's script-src-attr isn't covered by
         the nonce above, so an inline handler attribute silently never fires (issue #1315). --%>
    document.addEventListener('DOMContentLoaded', function () {
      var imageFileInput = document.getElementById('imageFile');
      if (imageFileInput) {
        imageFileInput.addEventListener('change', function () {
          SavePhoto(this);
        });
      }
      var deletePageBtn = document.getElementById('deletePageBtn');
      if (deletePageBtn) {
        deletePageBtn.addEventListener('click', deletePage);
      }
    });
</script>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />
  <%-- Form specific --%>
  <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>" />
  <input type="hidden" name="id" value="${webPage.id}" />
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <div class="grid-x grid-padding-x">
    <div class="small-12 medium-6 cell">
      <label>Link <span class="required">*</span>
        <input type="text" placeholder="/example" name="link" value="<c:out value="${webPage.link}"/>" required>
      </label>
      <label>Redirect
        <input type="text" placeholder="/other/page" name="redirectUrl" value="<c:out value="${webPage.redirectUrl}"/>">
      </label>
      <label>Redirect Notes (optional)
        <input type="text" placeholder="Why does this redirect exist? Is it still needed?" name="redirectNotes" value="<c:out value="${webPage.redirectNotes}"/>">
      </label>
      <label>Title
        <input type="text" placeholder="Give it a title..." name="title" value="<c:out value="${webPage.title}"/>">
      </label>
      <label>Keywords
        <input type="text" placeholder="Comma-separated keywords..." name="keywords" value="<c:out value="${webPage.keywords}"/>">
      </label>
      <label>Description
        <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${webPage.description}"/>">
      </label>
      <label>Solution Type
        <select name="solutionType">
          <option value=""></option>
          <c:forEach items="${solutionTypeMap}" var="option">
            <option value="<c:out value="${option.key}" />"<c:if test="${webPage.solutionType eq option.key}"> selected</c:if>><c:out value="${option.value}" /></option>
          </c:forEach>
        </select>
      </label>
    </div>
    <div class="small-12 medium-6 cell">
      <label>Publish?
        <div class="switch large">
          <input class="switch-input" id="publish-yes-no" type="checkbox" name="publish" value="true"<c:if test="${!webPage.draft}"> checked</c:if>>
          <label class="switch-paddle" for="publish-yes-no">
            <span class="switch-active" aria-hidden="true">Yes</span>
            <span class="switch-inactive" aria-hidden="true">No</span>
          </label>
        </div>
      </label>
      <div class="grid-x grid-padding-x">
        <div class="small-12 medium-6 cell">
          <c:set var="publishAtFormatted"><c:if test="${!empty webPage.publishAt}"><fmt:formatDate pattern="yyyy-MM-dd'T'HH:mm" value="${webPage.publishAt}"/></c:if></c:set>
          <label>Go live at (optional)
            <input type="datetime-local" name="publishAt" value="${publishAtFormatted}">
          </label>
        </div>
        <div class="small-12 medium-6 cell">
          <c:set var="expiresAtFormatted"><c:if test="${!empty webPage.expiresAt}"><fmt:formatDate pattern="yyyy-MM-dd'T'HH:mm" value="${webPage.expiresAt}"/></c:if></c:set>
          <label>Expire at (optional)
            <input type="datetime-local" name="expiresAt" value="${expiresAtFormatted}">
          </label>
        </div>
      </div>
      <div class="grid-x grid-padding-x">
        <div class="small-12 medium-3 cell">
          <label>Show in Sitemap.xml?
            <div class="switch large">
              <input class="switch-input" id="sitemap-yes-no" type="checkbox" name="showInSitemap" value="true"<c:if
                test="${webPage.showInSitemap}"> checked</c:if>>
              <label class="switch-paddle" for="sitemap-yes-no">
                <span class="switch-active" aria-hidden="true">Yes</span>
                <span class="switch-inactive" aria-hidden="true">No</span>
              </label>
            </div>
          </label>
        </div>
        <div class="small-12 medium-3 cell">
          <label>Priority (0.0-1.0)
            <input type="text" name="sitemapPriority" value="<fmt:formatNumber value="${webPage.sitemapPriority}" />" />
          </label>
        </div>
        <div class="small-12 medium-3 cell">
          <label>Change Frequency
            <select name="sitemapChangeFrequency">
              <option value=""></option>
              <c:forEach items="${sitemapChangeFrequencyMap}" var="option">
                <option value="<c:out value="${option.key}" />"<c:if test="${webPage.sitemapChangeFrequency eq option.key}"> selected</c:if>><c:out value="${option.value}" /></option>
              </c:forEach>
            </select>
          </label>
        </div>
      </div>
      <label>Searchable?
        <div class="switch large">
          <input class="switch-input" id="searchable-yes-no" type="checkbox" name="searchable" value="true"<c:if test="${webPage.searchable}"> checked</c:if>>
          <label class="switch-paddle" for="searchable-yes-no">
            <span class="switch-active" aria-hidden="true">Yes</span>
            <span class="switch-inactive" aria-hidden="true">No</span>
          </label>
        </div>
      </label>
      <label>Internal page? <small class="subheader">(employee/staff-only -- lets other admins hide these from the main web pages list)</small>
        <div class="switch large">
          <input class="switch-input" id="internal-yes-no" type="checkbox" name="internal" value="true"<c:if test="${webPage.internal}"> checked</c:if>>
          <label class="switch-paddle" for="internal-yes-no">
            <span class="switch-active" aria-hidden="true">Yes</span>
            <span class="switch-inactive" aria-hidden="true">No</span>
          </label>
        </div>
      </label>
      <small>Open Graph Image</small>
      <img id="imageUrlPreview" src="<c:out value="${webPage.imageUrl}"/>" style="max-height: 150px; max-width: 150px"/>
      <input type="text" class="no-gap" placeholder="Local Image URL" id="imageUrl" name="imageUrl" value="<c:out value="${webPage.imageUrl}"/>">
      <label for="imageFile" class="button">Upload Image File...</label>
      <input type="file" id="imageFile" class="show-for-sr">
      <p>
        <a class="button small primary radius no-gap" data-open="imageBrowserReveal">Browse Images</a>
      </p>
    </div>
  </div>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Save" data-disable-on-submit="Saving..." />
    <c:choose>
      <c:when test="${!empty returnPage}">
        <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </c:when>
      <c:when test="${!empty webPage.link}">
        <a href="${ctx}${webPage.link}" class="button radius secondary">Cancel</a>
      </c:when>
      <c:otherwise>

      </c:otherwise>
    </c:choose>
    <c:if test="${userSession.hasRole('admin')}">
      <button type="button" class="button radius alert" id="deletePageBtn"><i class="fa fa-trash-o"></i> Delete Page</button>
    </c:if>
    <c:if test="${webPage.id > -1}">
      <a href="${ctx}/admin/web-page-versions?webPageId=${webPage.id}" class="button radius secondary"><i class="fa fa-history"></i> Version History</a>
    </c:if>
  </div>
</form>
<%-- No data-animation-in (issue #1318): Foundation's Motion-UI animateIn path leaves this
     display:none forever -- a CSS transition can't start on an element that's still display:none
     when the animation class is added, so the transitionend it waits for to reveal the element
     never fires. Omitting it uses Foundation's default, non-animated (and non-transitionend-
     dependent) open, which works. --%>
<div class="reveal large" id="imageBrowserReveal" data-reveal role="dialog" aria-modal="true" aria-label="Image Browser">
  <iframe id="imageBrowserFrame" title="Image Browser" style="width: 100%; height: 70vh; border: 0;"></iframe>
</div>
<script nonce="${cspNonce}">
    // Load the image browser in an iframe so its own nonce-valid script runs and can populate
    // the parent field via top.document. Injecting the fragment's HTML with .html() instead
    // stripped the nonce (issue #1207) and reinterpreted the fetched markup as HTML
    // (CodeQL js/xss-through-dom). The iframe src is a server-rendered constant; the fragment
    // itself closes this modal via top.jQuery once an image is selected.
    $('#imageBrowserReveal').on('open.zf.reveal', function () {
        document.getElementById('imageBrowserFrame').src = '${ctx}/image-browser?inputId=imageUrl&view=reveal';
    });
    $('#imageBrowserReveal').on('closed.zf.reveal', function () {
        document.getElementById('imageBrowserFrame').removeAttribute('src');
    });
</script>
