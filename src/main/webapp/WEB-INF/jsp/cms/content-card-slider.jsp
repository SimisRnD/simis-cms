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
<jsp:useBean id="carouselClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="cardClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="smallCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="mediumCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="largeCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="loop" class="java.lang.String" scope="request"/>
<jsp:useBean id="autoplayDelay" class="java.lang.String" scope="request"/>
<c:if test="${showControls eq 'true' && fn:length(cardList) gt 1}">
<style>
  #swiper${widgetContext.uniqueId} { width: calc(100% - 80px); }
</style>
</c:if>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
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
  <div class="swiper-outer-container<c:if test="${!empty carouselClass}"> <c:out value="${carouselClass}" /></c:if>">
    <div id="swiper${widgetContext.uniqueId}" class="swiper">
      <div class="swiper-wrapper">
        <c:forEach items="${cardList}" var="card" varStatus="cardStatus">
          <div class="swiper-slide">
            <div class="<c:if test="${!empty cardClass}"> <c:out value="${cardClass}" /></c:if>">
              ${card}
            </div>
          </div>
        </c:forEach>
      </div>
    </div>
    <div id="swiper-pagination${widgetContext.uniqueId}" class="swiper-pagination"></div>
<c:if test="${autoplayDelay ne '-1' && fn:length(cardList) gt 1}">
    <%-- WCAG 2.2.2 (Pause, Stop, Hide): a keyboard-reachable control to stop the auto-advance. --%>
    <div class="swiper-autoplay-control text-center">
      <button type="button" id="swiper-autoplay-toggle${widgetContext.uniqueId}" class="button clear small swiper-autoplay-toggle" aria-pressed="false" aria-label="Pause automatic slide rotation">
        <i class="${font:fas()} fa-pause" aria-hidden="true"></i>
      </button>
    </div>
</c:if>
<c:if test="${showControls eq 'true' && fn:length(cardList) gt 1}">
  <c:if test="${showLeftControl eq 'true'}">
    <div id="swiper-button-prev${widgetContext.uniqueId}" class="swiper-button-prev"></div>
  </c:if>
  <c:if test="${showRightControl eq 'true'}">
    <div id="swiper-button-next${widgetContext.uniqueId}" class="swiper-button-next"></div>
  </c:if>
</c:if>
  </div>
</div>
<script nonce="${cspNonce}">
    var swiper${widgetContext.uniqueId} = new Swiper("#swiper${widgetContext.uniqueId}", {
        slidesPerView: <c:out value="${smallCardCount}" />,
        centerInsufficientSlides: true,
        spaceBetween: 15,
        breakpoints: {
            640: {
                slidesPerView: <c:out value="${mediumCardCount}" />,
                spaceBetween: 15
            },
            1024: {
                slidesPerView: <c:out value="${largeCardCount}" />,
                spaceBetween: 15
            }
        }
        <c:if test="${loop eq 'true'}">
          ,loop: true
        </c:if>
        <c:if test="${autoplayDelay ne '-1'}">
          ,autoplay: { delay: ${autoplayDelay}, stopOnLastSlide: true, disableOnInteraction: true }
        </c:if>
        <c:if test="${showPagination eq 'true'}">
          ,pagination: {
            el: '#swiper-pagination${widgetContext.uniqueId}',
          }
        </c:if>
        <c:if test="${showControls eq 'true' && fn:length(cardList) gt 1}">
          ,navigation: {
            <c:if test="${showLeftControl eq 'true'}">
            nextEl: '#swiper-button-next${widgetContext.uniqueId}'
            </c:if>
            <c:if test="${showLeftControl eq 'true' && showRightControl eq 'true'}">,</c:if>
            <c:if test="${showRightControl eq 'true'}">
            prevEl: '#swiper-button-prev${widgetContext.uniqueId}'
            </c:if>
          }
        </c:if>
    });
<c:if test="${loop eq 'true'}">
    // Swiper's loop-mode clone/position math (loopCreate/loopFix) runs once, synchronously,
    // inside new Swiper() above, using whatever width the container measures at that instant.
    // Unlike a plain resize, Swiper never re-validates that math on its own afterward -- its
    // built-in ResizeObserver path (onResize) is the only thing that does, and only calls the
    // loop-aware swiper.slideToLoop() (not the generic .update()) to correct it, and only if a
    // later size change is actually observed. If the container's true width isn't available yet
    // when this script runs (e.g. it's still inside a hidden/measuring-zero layout state), the
    // initial translate can be left stranded a slide-width off with nothing to correct it. Force
    // the same correction Swiper's own resize handler performs, once on window load (by which
    // point images/fonts have settled and the container has its final layout), so a stale
    // initial measurement can't strand the active slide outside the visible area.
    window.addEventListener('load', function () {
      var sw = swiper${widgetContext.uniqueId};
      if (!sw || sw.destroyed) {
        return;
      }
      sw.update();
      sw.slideToLoop(sw.realIndex, 0, false);
    });
</c:if>
<c:if test="${autoplayDelay ne '-1' && fn:length(cardList) gt 1}">
    // WCAG 2.2.2: give the visitor a way to stop the auto-advance, and honor the OS-level
    // reduced-motion preference (Swiper's autoplay is a JS timer, so a CSS media query alone
    // cannot stop it -- issue #1217).
    (function () {
      var sw = swiper${widgetContext.uniqueId};
      if (!sw || !sw.autoplay) {
        return;
      }
      var toggle = document.getElementById('swiper-autoplay-toggle${widgetContext.uniqueId}');
      var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
      var paused = false;
      function setPaused(next) {
        paused = next;
        if (paused) {
          sw.autoplay.stop();
        } else {
          sw.autoplay.start();
        }
        if (toggle) {
          toggle.setAttribute('aria-pressed', paused ? 'true' : 'false');
          toggle.setAttribute('aria-label', paused ? 'Resume automatic slide rotation' : 'Pause automatic slide rotation');
          var icon = toggle.querySelector('i');
          if (icon) {
            icon.classList.remove('fa-pause', 'fa-play');
            icon.classList.add(paused ? 'fa-play' : 'fa-pause');
          }
        }
      }
      // Start paused when the visitor prefers reduced motion, and react if they change that
      // OS setting without reloading the page.
      if (reduceMotion.matches) {
        setPaused(true);
      }
      reduceMotion.addEventListener('change', function (event) {
        setPaused(event.matches);
      });
      if (toggle) {
        toggle.addEventListener('click', function () {
          setPaused(!paused);
        });
      }
    })();
</c:if>
</script>
