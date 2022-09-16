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
<%@ page import="static com.simisinc.platform.ApplicationInfo.VERSION" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="subFolder" class="com.simisinc.platform.domain.model.cms.SubFolder" scope="request"/>
<jsp:useBean id="fileList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="controlId" class="java.lang.String" scope="request"/>
<jsp:useBean id="showCaption" class="java.lang.String" scope="request"/>
<jsp:useBean id="isSticky" class="java.lang.String" scope="request"/>
<style>
  .swiper {
    border: 1px solid #cccccc;
    min-height: 450px;
  }
  .swiper-slide {
    text-align: center;
    font-size: 18px;
    min-height: 450px;
  }
  .swiper-slide img {
    width: 80%;
    height: auto;
    position: absolute;
    left: 50%;
    top: 45%;
    transform: translate(-50%, -50%);
  }
  .slider-caption {
    position: absolute;
    width: 85%;
    bottom: 16px;
    left: 50%;
    transform: translateX(-50%);
  }
</style>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<div class="platform-content"<c:if test="${isSticky eq 'true'}"> data-sticky-container</c:if>>
  <div<c:if test="${isSticky eq 'true'}"> class="sticky" data-sticky data-anchor="sticky-gallery" data-margin-top="14"</c:if>>
    <div class="slider-header text-center" id="slider-header${controlId}">
      <h4><c:out value="${subFolder.name}" /></h4>
    </div>
    <div id="swiper${widgetContext.uniqueId}" class="swiper">
      <div id="photo-gallery" class="swiper-wrapper">
      <c:forEach items="${fileList}" var="file">
        <div class="swiper-slide">
          <img data-src="${ctx}/assets/view/${file.url}" alt="Image" class="swiper-lazy">
          <div class="swiper-lazy-preloader"></div>
          <c:if test="${showCaption eq 'true'}"><p class="slider-caption"><c:out value="${file.title}"/></p></c:if>
        </div>
      </c:forEach>
      </div>
      <div class="swiper-pagination swiper-pagination-black"></div>
      <div class="swiper-button-next swiper-button-black"></div>
      <div class="swiper-button-prev swiper-button-black"></div>
    </div>
  </div>
</div>
<script>
  var swiper${widgetContext.uniqueId} = new Swiper('#swiper${widgetContext.uniqueId}', {
    preloadImages: false,
    lazy: true,
    autoplay: {
      delay: 5000
    },
    speed: 400,
    centeredSlides: true,
    loop: true,
    pagination: {
      el: '.swiper-pagination',
      clickable: true,
      type: 'fraction'
    },
    navigation: {
      nextEl: '.swiper-button-next',
      prevEl: '.swiper-button-prev'
    },
    keyboard: {
      enabled: true,
      onlyInViewport: false
    }
  });

  swiper${widgetContext.uniqueId}.on('slideChangeTransitionStart', function () {
    $('.slider-caption').hide();
  });

  swiper${widgetContext.uniqueId}.on('slideChangeTransitionEnd', function () {
    $('.slider-caption').show();
  });

  function showAlbum${controlId}(subFolderId) {
    $.getJSON("${ctx}/json/photoList?subFolderId=" + subFolderId, function( data ) {
      if (data.photoList === undefined) {
        alert('The album could not be loaded');
        return;
      }
      swiper${widgetContext.uniqueId}.removeAllSlides();
      document.getElementById("slider-header${controlId}").innerHTML = '<h4>' + data.title + '</h4>';
      var slides = "";
      for (var i = 0; i < data.photoList.length; i++) {
        var photo = data.photoList[i];
        slides += '<div class="swiper-slide"><img data-src="' + photo.url + '" class="swiper-lazy"><div class="swiper-lazy-preloader"></div><c:if test="${showCaption eq 'true'}"><p class="slider-caption">' + photo.title + '</p></c:if></div>';
      }
      swiper${widgetContext.uniqueId}.appendSlide(slides);
      swiper${widgetContext.uniqueId}.update();
      swiper${widgetContext.uniqueId}.slideToLoop(0);
    });
  }
</script>