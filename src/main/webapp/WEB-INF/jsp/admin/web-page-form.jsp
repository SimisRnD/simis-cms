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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<%-- Handle image uploads --%>
<script>
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
                    alert('There was an error with the file. Make sure to use a .jpg or .png');
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
          window.location.href = '${widgetContext.uri}?action=deletePage&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPageId=${webPage.id}';
      }
    </c:if>
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
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
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
      <label>Title
        <input type="text" placeholder="Give it a title..." name="title" value="<c:out value="${webPage.title}"/>">
      </label>
      <label>Keywords
        <input type="text" placeholder="Comma-separated keywords..." name="keywords" value="<c:out value="${webPage.keywords}"/>">
      </label>
      <label>Description
        <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${webPage.description}"/>">
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
      <small>Open Graph Image</small>
      <img id="imageUrlPreview" src="<c:out value="${webPage.imageUrl}"/>" style="max-height: 150px; max-width: 150px"/>
      <input type="text" class="no-gap" placeholder="Local Image URL" id="imageUrl" name="imageUrl" value="<c:out value="${webPage.imageUrl}"/>">
      <label for="imageFile" class="button">Upload Image File...</label>
      <input type="file" id="imageFile" class="show-for-sr" onchange="SavePhoto(this)">
      <p>
        <a class="button small primary radius no-gap" data-open="imageBrowserReveal">Browse Images</a>
      </p>
    </div>
  </div>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Save" />
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
      <a class="button radius alert" href="javascript:deletePage()"><i class="fa fa-trash-o"></i> Delete Page</a>
    </c:if>
  </div>
</form>
<div class="reveal large" id="imageBrowserReveal" data-reveal data-animation-in="slide-in-down fast">
  <h3>Loading...</h3>
</div>
<script>
    $('#imageBrowserReveal').on('open.zf.reveal', function () {
        $('#imageBrowserReveal').html("<h3>Loading...</h3>");
        $.ajax({
            url: '${ctx}/image-browser?inputId=imageUrl&view=reveal',
            cache: false,
            dataType: 'html'
        }).done(function (content) {
            setTimeout(function () {
                $('#imageBrowserReveal').html(content);
                $('#imageBrowserReveal').trigger('resizeme.zf.trigger');
            }, 1000);
        });
    })
</script>
