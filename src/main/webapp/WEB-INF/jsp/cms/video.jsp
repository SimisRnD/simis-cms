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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="title" class="java.lang.String" scope="request"/>
<jsp:useBean id="aspectRatio" class="java.lang.String" scope="request"/>
<jsp:useBean id="provider" class="java.lang.String" scope="request"/>
<jsp:useBean id="embedUrl" class="java.lang.String" scope="request"/>
<jsp:useBean id="thumbnailUrl" class="java.lang.String" scope="request"/>
<jsp:useBean id="videoPageUrl" class="java.lang.String" scope="request"/>
<jsp:useBean id="consentGiven" class="java.lang.String" scope="request"/>
<c:set var="videoWidgetId" value="video-widget${widgetContext.uniqueId}"/>
<%-- aspectRatio is free-form (see VideoWidget#execute); any value not recognized below simply
     falls through to the 16:9 default, so it never needs sanitizing to be safe here. --%>
<c:choose>
  <c:when test="${aspectRatio eq '4:3'}"><c:set var="aspectRatioCss" value="4 / 3"/></c:when>
  <c:when test="${aspectRatio eq '1:1'}"><c:set var="aspectRatioCss" value="1 / 1"/></c:when>
  <c:when test="${aspectRatio eq '9:16'}"><c:set var="aspectRatioCss" value="9 / 16"/></c:when>
  <c:otherwise><c:set var="aspectRatioCss" value="16 / 9"/></c:otherwise>
</c:choose>
<c:choose>
  <%-- Gate 1: no analytics consent yet -- VideoWidget#execute never even populates embedUrl/
       provider/thumbnailUrl without consent, so there is nothing here that identifies the video or
       could cause a request to YouTube/Vimeo, only a static placeholder (issue #428 / #366) --%>
  <c:when test="${consentGiven ne 'true'}">
    <div class="platform-video-widget-consent-placeholder" role="note" style="aspect-ratio: <c:out value="${aspectRatioCss}"/>;">
      <i class="fa fa-video" aria-hidden="true"></i>
      <p>This video is hidden until analytics cookies are accepted.</p>
    </div>
  </c:when>
  <%-- Consent is present, but no videoUrl preference was set, or it didn't match a recognized
       YouTube/Vimeo URL --%>
  <c:when test="${empty embedUrl}">
    <div class="platform-video-widget-placeholder" role="img" style="aspect-ratio: <c:out value="${aspectRatioCss}"/>;"
         aria-label="<c:out value="${empty title ? 'No video configured' : title}"/>">
      <i class="fa fa-video" aria-hidden="true"></i>
    </div>
  </c:when>
  <%-- Gate 2: consent is present and the video is recognized, but the iframe is still not embedded
       until the visitor clicks --%>
  <c:otherwise>
    <div id="${videoWidgetId}" class="platform-video-widget" style="aspect-ratio: <c:out value="${aspectRatioCss}"/>;"
         data-embed-url="<c:out value="${embedUrl}"/>" data-video-title="<c:out value="${title}"/>">
      <button type="button" class="platform-video-play-button"
              <c:if test="${provider eq 'youtube'}">style="background-image: url('<c:out value="${thumbnailUrl}"/>');"</c:if>
              aria-label="Play video<c:if test="${!empty title}">: <c:out value="${title}"/></c:if>">
        <i class="fa fa-play-circle" aria-hidden="true"></i>
      </button>
    </div>
    <style nonce="${cspNonce}">
      #${videoWidgetId} {
        position: relative;
        width: 100%;
        overflow: hidden;
        background: #000;
      }
      #${videoWidgetId} .platform-video-play-button {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        border: 0;
        padding: 0;
        margin: 0;
        cursor: pointer;
        background-color: #222;
        background-size: cover;
        background-position: center;
        background-repeat: no-repeat;
        color: #fff;
        font-size: 3.5rem;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      #${videoWidgetId} .platform-video-play-button:hover,
      #${videoWidgetId} .platform-video-play-button:focus {
        color: #fff;
        opacity: 0.9;
      }
      #${videoWidgetId} iframe {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        border: 0;
      }
    </style>
    <script nonce="${cspNonce}">
      (function () {
        var container = document.getElementById('${videoWidgetId}');
        if (!container) {
          return;
        }
        var playButton = container.querySelector('.platform-video-play-button');
        <c:if test="${provider eq 'vimeo'}">
        // Vimeo has no static thumbnail URL the way YouTube does (img.youtube.com/vi/{id}/hqdefault.jpg
        // needs no API call) -- fetch one from Vimeo's public, CORS-enabled oEmbed endpoint directly
        // from the browser. This is intentionally NOT a server-side fetch -- see VideoWidget's class
        // Javadoc and the PR description for why (issues #784, #760).
        fetch('https://vimeo.com/api/oembed.json?url=' + encodeURIComponent('${js:escape(videoPageUrl)}'))
          .then(function (response) { return response.ok ? response.json() : null; })
          .then(function (data) {
            if (data && data.thumbnail_url && playButton) {
              playButton.style.backgroundImage = "url('" + data.thumbnail_url + "')";
            }
          })
          .catch(function () {
            // The thumbnail is a nice-to-have; the click-to-play button still works without it.
          });
        </c:if>
        if (playButton) {
          playButton.addEventListener('click', function () {
            var iframe = document.createElement('iframe');
            iframe.src = container.getAttribute('data-embed-url');
            iframe.title = container.getAttribute('data-video-title') || 'Video';
            iframe.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share');
            iframe.allowFullscreen = true;
            container.innerHTML = '';
            container.appendChild(iframe);
          });
        }
      })();
    </script>
  </c:otherwise>
</c:choose>
