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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="content" class="com.simisinc.platform.domain.model.cms.Content" scope="request"/>
<jsp:useBean id="isDraft" class="java.lang.String" scope="request"/>
<script src="${ctx}/javascript/tinymce-6.1.2/tinymce.min.js"></script>
<script>
  $(window).on('resize', function () {
    setTimeout(function () {
      var container = document.getElementsByClassName("tox-tinymce")[0];
      var rect = container.getBoundingClientRect(),
        scrollLeft = window.pageXOffset || document.documentElement.scrollLeft,
        scrollTop = window.pageYOffset || document.documentElement.scrollTop;
      var newHeight = $(window).height() - Math.round(rect.top + scrollTop + 106);
      $('.tox-tinymce').height(newHeight);
    }, 100);
  });

  tinymce.init({
    selector: 'textarea',
    branding: false,
    width: '100%',
    height: '100%',
    resize: false,
    setup: function (ed) {
      ed.on('init', function(args) {
        $(window).trigger('resize');
      });
    },
    menubar: false,
    relative_urls : false,
    convert_urls : true,
    content_css: ['${ctx}/css/${font:fontawesome()}/css/all.min.css'],
    noneditable_class: 'tinymce-noedit',
    browser_spellcheck: true,
    plugins: 'advlist autolink lists link image charmap preview anchor searchreplace visualblocks code media table wordcount fontawesome',
    toolbar: 'link image media table | undo redo | blocks | bold italic backcolor | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent hr anchor | fontawesome removeformat visualblocks code',

    image_class_list: [
      {title: 'None', value: ''},
      {title: 'Image Left/Wrap Text Right', value: 'image-left'},
      {title: 'Image Right/Wrap Text left', value: 'image-right'},
      {title: 'Image Center On Line', value: 'image-center'}
    ],

    link_class_list: [
      {title: 'None', value: ''},
      {title: 'Button', value: 'button'},
      {title: 'Button Primary', value: 'button primary'},
      {title: 'Button Primary Radius', value: 'button primary radius'},
      {title: 'Button Primary Round', value: 'button primary round'},
      {title: 'Button Secondary', value: 'button secondary'},
      {title: 'Button Secondary Radius', value: 'button secondary radius'},
      {title: 'Button Secondary Round', value: 'button secondary round'},
      {title: 'Button Box', value: 'button box'},
      {title: 'Button Box Radius', value: 'button box radius'},
      {title: 'Button Box Round', value: 'button box round'},
      {title: 'Call to Action', value: 'button call-to-action'}
    ],
    extended_valid_elements: 'span[*]',
    file_picker_types: 'file image media',
    // link_default_target: '_blank',
    file_picker_callback: function (callback, value, meta) {
        FileBrowser(value, meta.filetype, function (fileUrl) {
            callback(fileUrl);
        });
    },
    images_upload_url: '${ctx}/image-upload?widget=imageUpload1&token=${userSession.formToken}', // return { "location": "folder/sub-folder/new-location.png" }
    automatic_uploads: true
    // paste_word_valid_elements: "p,a,b,strong,i,em,h1,h2,h3,h4,h5,ol,ul,li"
    // paste_retain_style_properties: "color"
    // paste_as_text: true
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
<%@include file="../page_messages.jspf" %>
<p>
  <small>
    <c:out value="${content.uniqueId}" />
    <c:if test="${isDraft eq 'true'}">
      <span class="label warning">Draft</span>
    </c:if>
  </small>
</p>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}" />
  <input type="hidden" name="token" value="${userSession.formToken}" />
  <%-- Form Content --%>
  <input type="hidden" name="uniqueId" value="${content.uniqueId}" />
  <input type="hidden" name="returnPage" value="${returnPage}" />
  <p>
    <textarea name="content"><c:out value="${contentHtml}"/></textarea>
  </p>
  <div class="button-container">
    <c:choose>
      <c:when test="${content.id eq -1}">
        <input type="submit" class="button radius primary" name="save" value="Save" />
      </c:when>
      <c:otherwise>
        <input type="submit" class="button radius success" name="save" value="Publish Immediately" />
        <input type="submit" class="button radius warning" name="save" value="Save as Draft" />
        <c:if test="${isDraft eq 'true'}">
          <input type="submit" class="button radius alert" name="save" value="Remove this Draft" />
        </c:if>
      </c:otherwise>
    </c:choose>
    <a href="${returnPage}" class="button radius secondary">Cancel</a>
  </div>
</form>
