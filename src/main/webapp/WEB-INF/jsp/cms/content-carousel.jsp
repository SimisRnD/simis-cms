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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="isDraft" class="java.lang.String" scope="request"/>
<jsp:useBean id="display" class="java.lang.String" scope="request"/>
<jsp:useBean id="carouselClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="carouselTitle" class="java.lang.String" scope="request"/>
<jsp:useBean id="carouselSize" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<div class="platform-content-container"<c:if test="${showEditor eq 'true' && !empty uniqueId}"> data-simis-content-id="${uniqueId}"</c:if>>
  <c:if test="${showEditor eq 'true' && !empty uniqueId}">
    <div class="platform-content-editor">
      <c:if test="${isDraft eq 'true'}">
        <a class="hollow button small warning" href="${widgetContext.uri}?action=publish&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" data-confirm-href="Publish this content?">DRAFT</a>
      </c:if>
      <a class="hollow button small secondary" href="${ctx}/content-editor?uniqueId=${uniqueId}&returnPage=${returnPage}"><i class="${font:fas()} fa-edit"></i></a>
    </div>
    <%@include file="../confirm_submit.jspf" %>
  </c:if>
  <div id="orbit${widgetContext.uniqueId}" class="orbit<c:if test="${!empty carouselClass}"> <c:out value="${carouselClass}" /></c:if>" role="region"<c:if test="${!empty carouselTitle}"> aria-label="<c:out value="${carouselTitle}" />"</c:if> data-orbit<c:if test="${!empty dataOptions}"> <c:out value="${dataOptions}" /></c:if>>
    <div class="orbit-wrapper">
      <c:if test="${showControls eq 'true' && fn:length(cardList) gt 1}">
      <div class="orbit-controls">
        <c:if test="${showLeftControl eq 'true'}">
          <button class="orbit-previous"><span class="show-for-sr">Previous Slide</span>&#9664;&#xFE0E;</button>
        </c:if>
        <c:if test="${showRightControl eq 'true'}">
          <button class="orbit-next"><span class="show-for-sr">Next Slide</span>&#9654;&#xFE0E;</button>
        </c:if>
      </div>
      </c:if>
      <ul class="orbit-container">
        <c:forEach items="${cardList}" var="card" varStatus="cardStatus">
          <li class="<c:if test="${cardStatus.first}">is-active </c:if>orbit-slide">
            <figure class="orbit-figure">
              <c:choose>
                <c:when test="${display eq 'images'}"><img class="orbit-image" ${card} <c:choose><c:when test="${cardStatus.first}">loading="eager"</c:when><c:otherwise>loading="lazy" decoding="async"</c:otherwise></c:choose> /></c:when>
                <c:when test="${carouselSize eq 'large'}"><img class="orbit-image" src="${ctx}/images/widgets/image-640-480.png" alt="background image" width="640" height="480" <c:choose><c:when test="${cardStatus.first}">loading="eager"</c:when><c:otherwise>loading="lazy" decoding="async"</c:otherwise></c:choose> /></c:when>
                <c:when test="${carouselSize eq 'medium'}"><img class="orbit-image" src="${ctx}/images/widgets/image-1952-850.png" alt="background image" width="1952" height="850" <c:choose><c:when test="${cardStatus.first}">loading="eager"</c:when><c:otherwise>loading="lazy" decoding="async"</c:otherwise></c:choose> /></c:when>
                <c:when test="${carouselSize eq 'tiny'}"><img class="orbit-image" src="${ctx}/images/widgets/image-2034-690.png" alt="background image" width="2034" height="690" <c:choose><c:when test="${cardStatus.first}">loading="eager"</c:when><c:otherwise>loading="lazy" decoding="async"</c:otherwise></c:choose> /></c:when>
                <c:otherwise><img class="orbit-image" src="${ctx}/images/widgets/image-640-240.png" alt="background image" width="640" height="240" <c:choose><c:when test="${cardStatus.first}">loading="eager"</c:when><c:otherwise>loading="lazy" decoding="async"</c:otherwise></c:choose>></c:otherwise>
              </c:choose>
              <c:if test="${display eq 'text'}">
              <figcaption class="orbit-caption">
                ${card}
              </figcaption>
              </c:if>
            </figure>
          </li>
        </c:forEach>
      </ul>
    </div>
    <c:if test="${showBullets eq 'true' && fn:length(cardList) gt 1}">
    <nav class="orbit-bullets">
      <c:forEach items="${cardList}" var="card" varStatus="cardStatus">
        <button data-slide="${cardStatus.index}"<c:if test="${cardStatus.first}"> class="is-active"</c:if>><span class="show-for-sr">Slide ${cardStatus.count}</span><c:if test="${cardStatus.first}"><span class="show-for-sr">Current Slide</span></c:if></button>
      </c:forEach>
    </nav>
    </c:if>
  </div>
  <c:if test="${fn:length(cardList) gt 1}">
    <%-- WCAG 2.2.2 (Pause, Stop, Hide): Foundation Orbit auto-advances by default (autoPlay: true)
         and the widget never disables it, so give the visitor a keyboard-reachable control to stop
         the timer. Wired to Orbit's timer in the script below (issue #1225). --%>
    <div class="orbit-autoplay-control text-center">
      <button type="button" id="orbit-autoplay-toggle${widgetContext.uniqueId}" class="button clear small orbit-autoplay-toggle" aria-pressed="false" aria-label="Pause automatic slide rotation">
        <i class="${font:fas()} fa-pause" aria-hidden="true"></i>
      </button>
    </div>
  </c:if>
</div>
<c:if test="${fn:length(cardList) gt 1}">
<script nonce="${cspNonce}">
  // WCAG 2.2.2: give the visitor a way to stop Foundation Orbit's auto-advance, and honor the
  // OS-level reduced-motion preference (Orbit's timer is a JS timer, so a CSS media query alone
  // cannot stop it -- issue #1225). $(document).foundation() runs at the bottom of main.jsp during
  // parse, before any ready handler, so Orbit is already initialised by the time this runs.
  $(function () {
    var orbit = $('#orbit${widgetContext.uniqueId}').data('zfPlugin');
    if (!orbit || !orbit.timer) {
      return;
    }
    var toggle = document.getElementById('orbit-autoplay-toggle${widgetContext.uniqueId}');
    var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    var paused = false;
    function setPaused(next) {
      paused = next;
      if (paused) {
        orbit.timer.pause();
        // Match Orbit's own clicked-to-pause state so it does not restart the timer on
        // mouseleave or a slide change.
        orbit.$element.data('clickedOn', true);
      } else {
        orbit.$element.data('clickedOn', false);
        orbit.timer.start();
      }
      if (toggle) {
        toggle.setAttribute('aria-pressed', paused ? 'true' : 'false');
        toggle.setAttribute('aria-label', paused ? 'Resume automatic slide rotation' : 'Pause automatic slide rotation');
        var icon = toggle.querySelector('i');
        if (icon) {
          icon.classList.toggle('fa-pause', !paused);
          icon.classList.toggle('fa-play', paused);
        }
      }
    }
    if (toggle) {
      toggle.addEventListener('click', function () {
        setPaused(!paused);
      });
    }
    // Start paused when the visitor prefers reduced motion, and honor a live change.
    if (reduceMotion.matches) {
      setPaused(true);
    }
    reduceMotion.addEventListener('change', function (event) {
      setPaused(event.matches);
    });
  });
</script>
</c:if>
