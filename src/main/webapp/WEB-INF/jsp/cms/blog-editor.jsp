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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blog" class="com.simisinc.platform.domain.model.cms.Blog" scope="request"/>
<jsp:useBean id="blogPost" class="com.simisinc.platform.domain.model.cms.BlogPost" scope="request"/>
<jsp:useBean id="tagList" class="java.util.ArrayList" scope="request"/>
<script src="${ctx}/javascript/tinymce-7.9.3/tinymce.min.js"></script>
<script nonce="${cspNonce}">
  tinymce.init({
    license_key: 'gpl',
    selector: 'textarea',
    branding: false,
    width: '100%',
    height: 300,
    menubar: false,
    relative_urls: false,
    convert_urls: true,
    browser_spellcheck: true,
    plugins: 'advlist autolink lists link image charmap preview anchor searchreplace visualblocks code insertdatetime media table wordcount',
    toolbar: 'link image media table | undo redo | blocks | bold italic backcolor | bullist numlist outdent indent hr | removeformat | visualblocks code',
    image_class_list: [
      {title: 'None', value: ''},
      {title: 'Image Left/Wrap Text Right', value: 'image-left'},
      {title: 'Image Right/Wrap Text left', value: 'image-right'},
      {title: 'Image Center On Line', value: 'image-center'}
    ],
    file_picker_types: 'file image media',
    // link_default_target: '_blank',
    file_picker_callback: function (callback, value, meta) {
        FileBrowser(value, meta.filetype, function (fileUrl, altText) {
            // A second argument populates other fields of the dialog TinyMCE is about to show. The
            // image plugin reads meta.alt into its "Alternative description" field; the file and
            // media dialogs have no such field and ignore it (#1373).
            callback(fileUrl, altText ? { alt: altText } : {});
        });
    },
    images_upload_url: '${ctx}/image-upload?widget=imageUpload1&token=${userSession.formToken}', // return { "location": "folder/sub-folder/new-location.png" }
    paste_data_images: true,
    automatic_uploads: true
  });

  function FileBrowser(value, type, callback) {
    // type will be: file, image, media
    var cmsType = 'image';
    if (type === 'media') {
      cmsType = 'video';
    } else if (type === 'file') {
      cmsType = 'file';
    }
    var cmsURL = '${ctx}/' + cmsType + '-browser';
    const instanceApi = tinyMCE.activeEditor.windowManager.openUrl({
        title: 'Browser',
        url: cmsURL,
        width: 850,
        height: 650,
        onMessage: function(dialogApi, details) {
            callback(details.content, details.altText);
            instanceApi.close();
        }
    });
    return false;
  }
</script>
<%-- Handle banner image uploads --%>
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
          // Report what the server actually rejected (issue #1189). This used to always claim the
          // file was not a .jpg or .png, which is wrong -- and actively misleading -- for a
          // permission, storage, or size failure, all of which land here too.
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
  <%-- Bound here instead of inline onchange= -- CSP's script-src-attr isn't covered by the nonce
       above, so an inline handler attribute silently never fires (issue #1350, same root cause as
       #1188 and the identical web page editor fix in #1315). --%>
  document.addEventListener('DOMContentLoaded', function () {
    var imageFileInput = document.getElementById('imageFile');
    if (imageFileInput) {
      imageFileInput.addEventListener('change', function () {
        SavePhoto(this);
      });
    }
    var notifySubscribers = document.getElementById('notifySubscribers');
    var notifyMailingListId = document.getElementById('notifyMailingListId');
    if (notifySubscribers && notifyMailingListId) {
      notifySubscribers.addEventListener('change', function () {
        notifyMailingListId.disabled = !this.checked;
      });
    }
  });
</script>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />
  <%-- Form values --%>
  <input type="hidden" name="id" value="${blogPost.id}"/>
  <input type="hidden" name="blogId" value="${blog.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>"/>
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Governed publish workflow status (issue #407, phase 2) -- only present when this post has a
       pending draft awaiting review; links to BlogPostReviewWidget's submit/approve/reject page. --%>
  <c:if test="${!empty blogPostReviewStatus}">
    <p>
      Review status: <a href="${ctx}/admin/blog-post-review?blogPostId=${blogPost.id}" class="secondary label">
        <i class="fa fa-clipboard-check"></i> <c:out value="${blogPostReviewStatus}" />
      </a>
    </p>
  </c:if>
  <%-- Form Content --%>
  <ul class="breadcrumbs">
    <li><a href="${ctx}/${blog.uniqueId}"><c:out value="${blog.name}"/></a></li>
    <c:choose>
      <c:when test="${!empty blogPost.uniqueId}">
        <li><a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}"><c:out value="${blogPost.title}"/></a></li>
      </c:when>
      <c:otherwise>
        <li>New Post</li>
      </c:otherwise>
    </c:choose>
    <li>Editor</li>
  </ul>
  <label>Title
    <input type="text" placeholder="Give it a title..." name="title" value="${html:toHtml(blogPost.title)}">
  </label>
  <label>Keywords
    <input type="text" placeholder="Provide optional keywords..." name="keywords" value="<c:out value="${blogPost.keywords}"/>">
  </label>
  <label>Description
    <input type="text" placeholder="Provide an optional description..." name="summary" value="<c:out value="${blogPost.summary}"/>">
  </label>
  <label>Source article link
    <input type="url" placeholder="https://... (leave blank for a normal post)" name="sourceUrl" value="<c:out value="${blogPost.sourceUrl}"/>">
    <small class="help-text">For a curated link post. When set, the headline and &ldquo;read the article&rdquo; go straight to the original, and feed subscribers get the same link. The post still keeps its own page.</small>
  </label>
  <p>
    <small>Write the post...</small>
    <textarea name="body"><c:out value="${blogPost.body}"/></textarea>
  </p>
  <c:if test="${!empty tagList}">
    <h3 class="margin-top-30 margin-bottom-20">Tags</h3>
    <div class="grid-container margin-top-20">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <div class="input-container">
            <c:forEach items="${tagList}" var="tag">
              <c:set var="contains" value="false" />
              <c:forEach var="thisTagId" items="${blogPost.tagIdList}">
                <c:if test="${thisTagId eq tag.id}">
                  <c:set var="contains" value="true" />
                </c:if>
              </c:forEach>
              <input id="tagId${tag.id}" type="checkbox" name="tagId" value="${tag.id}"<c:if test="${contains eq 'true'}"> checked</c:if> /><label for="tagId${tag.id}"><c:out value="${tag.name}" /></label>
            </c:forEach>
          </div>
        </div>
      </div>
    </div>
  </c:if>
  <div class="full-container">
    <div class="grid-x grid-margin-x callout box">
      <div class="auto cell text-right">
        <small>Banner Image</small>
      </div>
      <div class="small-6 cell">
        <input type="text" class="no-gap" placeholder="Local Image URL" id="imageUrl" name="imageUrl" value="<c:out value="${blogPost.imageUrl}"/>">
        <label for="imageFile" class="button">Upload Image File...</label>
        <input type="file" id="imageFile" class="show-for-sr">
      </div>
      <div class="small-2 cell">
        <img id="imageUrlPreview" src="<c:out value="${blogPost.imageUrl}"/>" style="max-height: 50px; max-width: 150px"/>
      </div>
      <div class="small-2 cell text-right">
        <a class="button small primary radius no-gap" data-open="imageBrowserReveal">Browse Images</a>
      </div>
    </div>
  </div>
  <input id="enabled" type="checkbox" name="enabled" value="true" <c:if test="${blogPost.id == -1 || !empty blogPost.published}">checked</c:if>/><label for="enabled">Publish it?</label>
  <div class="full-container" style="margin-top:10px">
    <input id="excludeFromFeed" type="checkbox" name="excludeFromFeed" value="true"<c:if test="${blogPost.excludeFromFeed}"> checked</c:if>/>
    <label for="excludeFromFeed">Leave this post out of the RSS feed?</label>
    <small>The post stays published, searchable, and at its own address -- it just is not pushed to feed subscribers. Use this instead of archiving, which hides a post everywhere.</small>
  </div>
  <c:if test="${!empty mailingLists}">
    <div class="full-container" style="margin-top:10px">
      <input id="notifySubscribers" type="checkbox" name="notifySubscribers" value="true" />
      <label for="notifySubscribers">Notify subscribers of a mailing list about this post?</label>
      <small>Only sent the moment this post is first published -- editing an already-published post won't re-notify anyone.</small>
      <select id="notifyMailingListId" name="notifyMailingListId" disabled>
        <option value="">Choose a mailing list...</option>
        <c:forEach items="${mailingLists}" var="mailingList">
          <option value="${mailingList.id}" <c:if test="${mailingList.id == blog.mailingListId}">selected</c:if>><c:out value="${mailingList.title}" /></option>
        </c:forEach>
      </select>
    </div>
  </c:if>
  <div class="full-container">
    <div class="grid-x grid-margin-x">
      <div class="medium-6 cell">
        <label>Display starting at a specific date/time?
          <div class="input-group">
            <span class="input-group-label"><i class="fa fa-calendar"></i></span>
            <input class="input-group-field" type="text" placeholder="Publish right away, or choose a specific date and time..." id="startDate" name="startDate" value="<c:out value="${date:formatDateTimeInput(blogPost.startDate)}"/>">
          </div>
        </label>
        <script nonce="${cspNonce}">
          $(function () {
            $('#startDate').fdatepicker({
              format: 'mm-dd-yyyy hh:ii',
              disableDblClickSelection: true,
              pickTime: true
            });
          });
        </script>
      </div>
      <div class="medium-6 cell">
        <label>Hide on a specific date/time?
          <div class="input-group">
            <span class="input-group-label"><i class="fa fa-calendar"></i></span>
            <input class="input-group-field" type="text" placeholder="" id="endDate" name="endDate" value="<c:out value="${date:formatDateTimeInput(blogPost.endDate)}"/>">
          </div>
        </label>
        <script nonce="${cspNonce}">
          $(function () {
            // yyyy-MM-dd HH:mm:ss.fffffffff
            $('#endDate').fdatepicker({
              format: 'mm-dd-yyyy hh:ii',
              disableDblClickSelection: true,
              pickTime: true
            });
          });
        </script>
      </div>
    </div>
  </div>
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