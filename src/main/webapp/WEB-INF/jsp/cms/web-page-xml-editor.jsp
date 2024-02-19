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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<script src="${ctx}/javascript/ace-1.32.4/ace.js" type="text/javascript" charset="utf-8"></script>
<script src="${ctx}/javascript/ace-1.32.4/mode-xml.js" type="text/javascript" charset="utf-8"></script>
<%--<script src="${ctx}/javascript/ace-1.32.4/ext-language_tools.js" type="text/javascript" charset="utf-8"></script>--%>
<style>
  #pageXml {
    right: 0;
  }
  .ace_editor {
    border: 1px solid #ccc;
  }
</style>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<small><c:out value="${webPage.link}" /></small>
<c:if test="${empty webPage.pageXml}">
  <p class="subheader">Page layout does not exist! Choose a template or design the page yourself...</p>
</c:if>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="webPage" value="<c:out value="${webPage.link}" />"/>
  <input type="hidden" name="returnPage" value="${returnPage}" />
  <%-- The editor --%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-8 large-7 cell">
      <p>
        <textarea id="pageXml" name="pageXml" data-gutter="1" rows="24" data-editor="xml"><c:out value="${webPage.pageXml}"/></textarea>
      </p>
      <div class="button-container">
        <input type="submit" class="button radius success no-gap" value="Save"/>
        <c:choose>
          <c:when test="${!empty returnPage}">
            <a href="${returnPage}" class="button radius secondary no-gap">Cancel</a>
          </c:when>
          <c:when test="${!empty webPage.link}">
            <a href="${ctx}${webPage.link}" class="button radius secondary no-gap">Cancel</a>
          </c:when>
          <c:otherwise>

          </c:otherwise>
        </c:choose>
      </div>
    </div>
    <div class="small-12 hide-for-small-only medium-4 large-5 cell">
      <div id="information" class="callout secondary" style="overflow:scroll">
        <div class="platform-content-container">
        <ul id="accordion0" class="accordion" data-accordion data-allow-all-closed="true" data-multi-expand="false">
          <%-- Widgets --%>
          <li class="accordion-item is-active" data-accordion-item>
            <a href="#" class="accordion-title accordion-title-level-1">Widgets</a>
            <div id="accordion3-content" class="accordion-content" data-tab-content>
              <ul id="accordion-3" class="accordion" data-accordion data-allow-all-closed="true">
<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Breadcrumbs</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="breadcrumbs">
  <links>
    <link name="Previous Page Title" value="/link" />
    <link name="Page Title" value="" />
  </links>
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Content</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="content">
  <uniqueId>content-unique-id</uniqueId>
  <html>&lt;![CDATA[ Any HTML content ]]&gt;</html>
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Content Accordion</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="contentAccordion">
  <uniqueId>content-unique-id</uniqueId>
  <html>&lt;![CDATA[ Any HTML content ]]&gt;</html>
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Content Cards</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="contentCards">
  <uniqueId>content-unique-id</uniqueId>
  <html>&lt;![CDATA[ Any HTML content ]]&gt;</html>
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Content Gallery</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="contentGallery">
  <uniqueId>content-unique-id</uniqueId>
  <html>&lt;![CDATA[ Any HTML content ]]&gt;</html>
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Content Reveal</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="contentReveal">
  <uniqueId>content-unique-id</uniqueId>
  <html>&lt;![CDATA[ Any HTML content ]]&gt;</html>

  uniqueId, html, attach=left, animate=up|down|left|right|fade, revealClass
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Content Slider</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="contentSlider">
  <uniqueId>content-unique-id</uniqueId>
  <html>&lt;![CDATA[ Any HTML content ]]&gt;</html>

  uniqueId, html, showControls, showPagination, loop, autoplayDelay=1000
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Content Tabs</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="contentTabs">
  tabs[name, linkId, link, contentUniqueId, isActive]
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Button</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="button">
  name, link, buttonClass, leftIcon, icon
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Link</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="link">
  name, link, class, property
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Card</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="card">
  title, icon, link, linkTitle, linkIcon, class
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Menu</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="menu" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Progress Card</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="progressCard">
  label, value, maxLabel, progress, maxValue, textColor=#|theme., subheaderColor=#|theme., progressColor=#|theme., remainderColor=#|theme.
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Statistic Card</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="statisticCard">
  label, value, icon, link, iconColor=#|theme., view=default|vertical
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Logo</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="logo">
  maxWidth, maxHeight, view=standard,color,white
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Copyright</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="copyright" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Email Subscribe</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="emailSubscribe" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Form</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="form">
  formUniqueId, icon, title, subtitle, buttonName, useCaptcha, successTitle, successMessage, fields=field[name, value, placeholder, required]
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Blog Post List</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="blogPostList">
  <!-- <title>Featured</title> -->
  <blogUniqueId>blog</blogUniqueId>
  <limit>1</limit>
  <showPaging>false</showPaging>
  <view>cards</view>
  <!-- <view>featured</view> -->
  <!-- <view>overview</view> -->
  <!-- <view>titles</view> -->
  <showReadMore>true</showReadMore>
  <showDate>true</showDate>
  <showAuthor>true</showAuthor>
  <showTags>false</showTags>
</v>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Blog Post</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="blogPost" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Blog Post Name</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="blogPostName" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Product Browser</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="productBrowser">
  <view>cardSlider</view>
  <button>Shop</button>
  <showControls>false</showControls>
  <showLeftControl>false</showLeftControl>
  <showRightControl>false</showRightControl>
  <products>
    <product uniqueId="" image="" />
    <product uniqueId="" image="" />
  </products>
  <!-- <limit>5</limit> -->
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Product Name</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="productName" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Product Image</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="productImage" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Product Description</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="productDescription" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Add to Cart</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="addToCart" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Cart</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="cart" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Remote Content</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="remoteContent">
  title, url, startTag, endTag
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Search Form</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="searchForm" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Table of Contents</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="tableOfContents">
  links[name, value]
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Album Gallery</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="albumGallery" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Photo Gallery</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="photoGallery" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">File List</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="fileList" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">File List by Folder</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="fileListByFolder" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">File List by Year</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="fileListByYear" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">File Drop Zone</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="fileDropZone" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Calendar</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="calendar" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Upcoming Calendar Events</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="upcomingCalendarEvents" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Calendar Event Details</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="calendarEventDetails" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Map</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="map">
  <coordinates>0.0, 0.0</coordinates>
  <!-- <latitude>0.0</latitude> -->
  <!-- <longitude>0.0</longitude> -->
  <mapHeight>290</mapHeight>
  <mapZoomLevel>12</mapZoomLevel>
  <showMarker>false</showMarker>
  <markerTitle>Title</markerTitle>
  <markerText>A caption for the title</markerText>
</widget>
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Instagram Photos</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="instagram" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Social Media Links</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="socialMediaLinks" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Wiki</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="wiki" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Web Page Content Search Results</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="webPageSearchResults" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Web Page Title Search Results</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="webPageTitleSearchResults" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Blog Post Search Results</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="blogPostSearchResults" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Calendar Search Results</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="calendarSearchResults" />
</textarea>
  </div>
</li>

<li class="accordion-item" data-accordion-item>
  <a href="#" class="accordion-title accordion-title-level-2">Items Search Results</a>
  <div class="accordion-content" data-tab-content>
<textarea>
<widget name="itemsSearchResults" />
</textarea>
  </div>
</li>

              </ul>
            </div>
          </li>
          <%-- Layout --%>
          <li class="accordion-item" data-accordion-item>
            <a href="#" class="accordion-title accordion-title-level-1">Layout Attributes</a>
            <div id="accordion2-content" class="accordion-content" data-tab-content>
              <ul id="accordion-2" class="accordion" data-accordion data-allow-all-closed="true">
                <li class="accordion-item" data-accordion-item>
                  <a href="#" class="accordion-title accordion-title-level-2">Page Attributes</a>
                  <div class="accordion-content" data-tab-content>
                    <dl>
                      <dd><strong>page</strong> class="full-page"</dd>
                      <dd><strong>section</strong> id="" class="grid-x grid-margin-x platform-no-margin align-middle align-center" hr="true"</dd>
                      <dd><strong>column</strong> id="" class="small-12 cell text-center callout radius round" hr="true"</dd>
                      <dd><strong>widget</strong> id="" name="" hr="true"</dd>
                    </dl>
                  </div>
                </li>
                <li class="accordion-item" data-accordion-item>
                  <a href="#" class="accordion-title accordion-title-level-2">Section Attributes</a>
                  <div class="accordion-content" data-tab-content>
                    <dl>
                      <dd><strong>page</strong> class="full-page"</dd>
                      <dd><strong>section</strong> id="" class="grid-x grid-margin-x platform-no-margin align-middle align-center" hr="true"</dd>
                      <dd><strong>column</strong> id="" class="small-12 cell text-center callout radius round" hr="true"</dd>
                      <dd><strong>widget</strong> id="" name="" hr="true"</dd>
                    </dl>
                  </div>
                </li>
              </ul>
            </div>
          </li>
          <%-- Editor --%>
          <li class="accordion-item" data-accordion-item>
            <a href="#" class="accordion-title accordion-title-level-1">Editor Shortcuts</a>
            <div id="accordion1-content" class="accordion-content" data-tab-content>
              <ul id="accordion-1" class="accordion" data-accordion data-allow-all-closed="true">
                <li class="accordion-item" data-accordion-item>
                  <a href="#" class="accordion-title accordion-title-level-2">Comment/Uncomment a Line</a>
                  <div class="accordion-content" data-tab-content>
                    CTRL+/ or CMD+/
                  </div>
                </li>
                <li class="accordion-item" data-accordion-item>
                  <a href="#" class="accordion-title accordion-title-level-2">Duplicate a Line</a>
                  <div class="accordion-content" data-tab-content>
                    CTRL+SHIFT+D or CMD+SHIFT+D
                  </div>
                </li>
                <li class="accordion-item" data-accordion-item>
                  <a href="#" class="accordion-title accordion-title-level-2">Delete a Line</a>
                  <div class="accordion-content" data-tab-content>
                    CTRL+D or CMD+D
                  </div>
                </li>
              </ul>
            </div>
          </li>
        </ul>
      </div>
    </div>
  </div>
</form>
<script>
  // Hook up ACE editor to all textareas with data-editor attribute
  $(function() {
    $('textarea[data-editor]').each(function() {
      var textarea = $(this);
      var mode = textarea.data('editor');
      var editDiv = $('<div>', {
        // position: 'absolute',
        // right: 0,
        height: textarea.height() - 40,
        'class': textarea.attr('class')
      }).insertBefore(textarea);
      textarea.css('display', 'none');
      ace.require("ace/ext/language_tools");
      var editor = ace.edit(editDiv[0]);
      editor.renderer.setShowGutter(textarea.data('gutter'));
      editor.setOptions({
        showPrintMargin: false,
        tabSize: 2,
        useSoftTabs: true
      });
      editor.getSession().setValue(textarea.val());
      editor.getSession().setMode("ace/mode/" + mode);
      editor.getSession().setUseWrapMode(true);
      editor.setTheme("ace/theme/eclipse");
      textarea.closest('form').submit(function() {
        textarea.val(editor.getSession().getValue());
      })
    });
  });
  $(document).ready(function () {
    function resizeEditor() {
      var container = document.getElementsByClassName("ace_editor")[0];
      var rect = container.getBoundingClientRect(),
        scrollLeft = window.pageXOffset || document.documentElement.scrollLeft,
        scrollTop = window.pageYOffset || document.documentElement.scrollTop;
      $('#information').height($(window).height() - Math.round(rect.top + scrollTop + 108));
      return $('.ace_editor').height($(window).height() - Math.round(rect.top + scrollTop + 75));
    }
    $(window).resize(resizeEditor);
    resizeEditor();
  });
</script>
