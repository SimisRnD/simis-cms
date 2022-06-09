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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blog" class="com.simisinc.platform.domain.model.cms.Blog" scope="request"/>
<jsp:useBean id="blogPost" class="com.simisinc.platform.domain.model.cms.BlogPost" scope="request"/>
<script src="${ctx}/javascript/tinymce-5.7.0/tinymce.min.js"></script>
<script>
  tinymce.init({
    selector: 'textarea',
    branding: false,
    width: '100%',
    height: 300,
    menubar: false,
    relative_urls: false,
    convert_urls: true,
    browser_spellcheck: true,
    plugins: [
      'advlist autolink lists link image charmap print preview anchor textcolor hr',
      'searchreplace visualblocks code',
      'insertdatetime media table paste help wordcount'
    ],
    toolbar: 'link | image | media | table | undo redo |  formatselect | bold italic backcolor  | bullist numlist outdent indent hr | removeformat | visualblocks code | help',
    image_class_list: [
      {title: 'None', value: ''},
      {title: 'Image Left/Wrap Text Right', value: 'image-left'},
      {title: 'Image Right/Wrap Text left', value: 'image-right'},
      {title: 'Image Center On Line', value: 'image-center'}
    ],
    file_picker_types: 'file image media',
    // default_link_target: '_blank',
    file_picker_callback: function (callback, value, meta) {
        FileBrowser(value, meta.filetype, function (fileUrl) {
            callback(fileUrl);
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
            callback(details.content);
            instanceApi.close();
        }
    });
    return false;
  }
</script>
<%-- Handle banner image uploads --%>
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
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
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
  <p>
    <small>Write the post...</small>
    <textarea name="body"><c:out value="${blogPost.body}"/></textarea>
  </p>
  <div class="full-container">
    <div class="grid-x grid-margin-x callout box">
      <div class="auto cell text-right">
        <small>Banner Image</small>
      </div>
      <div class="small-6 cell">
        <input type="text" class="no-gap" placeholder="Local Image URL" id="imageUrl" name="imageUrl" value="<c:out value="${blogPost.imageUrl}"/>">
        <label for="imageFile" class="button">Upload Image File...</label>
        <input type="file" id="imageFile" class="show-for-sr" onchange="SavePhoto(this)">
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
  <div class="full-container">
    <div class="grid-x grid-margin-x">
      <div class="medium-6 cell">
        <label>Display starting at a specific date/time?
          <div class="input-group">
            <span class="input-group-label"><i class="fa fa-calendar"></i></span>
            <input class="input-group-field" type="text" placeholder="Publish right away, or choose a specific date and time..." id="startDate" name="startDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${blogPost.startDate}" />">
          </div>
        </label>
        <script>
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
            <input class="input-group-field" type="text" placeholder="" id="endDate" name="endDate" value="<fmt:formatDate pattern="MM-dd-yyyy HH:mm" value="${blogPost.endDate}" />">
          </div>
        </label>
        <script>
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
  <c:choose>
    <c:when test="${!empty returnPage}">
      <p>
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </p>
    </c:when>
    <c:otherwise>
      <p><input type="submit" class="button radius success expanded" value="Save"/></p>
    </c:otherwise>
  </c:choose>
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