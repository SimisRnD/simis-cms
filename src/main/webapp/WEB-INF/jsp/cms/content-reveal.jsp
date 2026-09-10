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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="isDraft" class="java.lang.String" scope="request"/>
<jsp:useBean id="card1" class="java.lang.String" scope="request"/>
<jsp:useBean id="card2" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<div class="platform-content-container"<c:if test="${showEditor eq 'true' && !empty uniqueId}"> data-simis-content-id="${uniqueId}"</c:if>>
  <c:if test="${showEditor eq 'true' && !empty uniqueId}">
    <div class="platform-content-editor">
      <c:if test="${isDraft eq 'true'}">
        <a class="hollow button small warning" href="${widgetContext.uri}?action=publish&widget=${widgetContext.uniqueId}&token=${userSession.formToken}" data-confirm-href="Publish this content?">DRAFT</a>
      </c:if>
      <a aria-label="Edit this content" class="hollow button small secondary" href="${ctx}/content-editor?uniqueId=${uniqueId}&returnPage=${returnPage}"><i aria-hidden="true" class="${font:fas()} fa-edit"></i></a>
    </div>
    <%@include file="../confirm_submit.jspf" %>
  </c:if>
  <c:if test="${!empty card1}">
    <c:if test="${!empty card2 && useIcon eq 'true'}">
      <div class="float-right">
        <button aria-labelledby="reveal-button${widgetContext.uniqueId}" class="reveal-button" data-toggle="modal${widgetContext.uniqueId}"><i aria-hidden="true" class="${font:fal()} fa-plus-circle"></i></button>
      </div>
    </c:if>
    <button id="reveal-button${widgetContext.uniqueId}" class="reveal-button-text" data-toggle="modal${widgetContext.uniqueId}"><div class="button-reveal-content">${card1}</div></button>
    <c:if test="${!empty card2}">
      <div class="reveal<c:if test="${!empty size}"> <c:out value="${size}" /></c:if>" id="modal${widgetContext.uniqueId}"
           <%-- aria-labelledby points at the trigger button rendered just above, whose text is
                card1 -- the same value that serves as this dialog's visible title. A role="dialog"
                with aria-modal but no name announces only as "dialog", which is what a screen
                reader user got here (WCAG 4.1.2). Every other Reveal in the codebase names itself
                from an h4 inside it; this widget has no heading element to point at, and the
                trigger already carries a stable per-widget id, so it is the natural source. The
                button is emitted unconditionally inside the same c:if that guards card1, and this
                dialog additionally requires card2, so the reference can never dangle. --%>
           role="dialog" aria-modal="true" aria-labelledby="reveal-button${widgetContext.uniqueId}"
           data-reveal
           data-reset-on-close="true"
           <%-- No data-animation-in/data-animation-out (issue #1320, same as #1318): Foundation's
                Motion-UI animateIn path leaves this display:none forever -- a CSS transition can't
                start on an element that's still display:none when the animation class is added, so
                the transitionend it waits for to reveal the element never fires. The "animate"
                preference is kept (still shown in the widget's Animation dropdown) so existing
                configurations don't break, but it no longer drives any data-animation-in/out
                attribute -- every direction now uses Foundation's default, non-animated open,
                which works. --%>
           <c:if test="${attach eq 'left'}">
             data-h-offset="0"
           </c:if>
           data-multiple-opened="true"
           <c:if test="${!empty revealClass}">
           data-additional-overlay-classes="<c:out value="${revealClass}" />"
           </c:if>
           data-close-on-click="true">
          ${card2}
        <button class="close-button" data-close aria-label="Close reveal" type="button">
          <span aria-hidden="true"><i class="${font:fal()} fa-circle-xmark"></i></span>
        </button>
      </div>
    </c:if>
  </c:if>
</div>
<script nonce="${cspNonce}">
  // Attach to the modal display event; auto-play first modal video
  document.addEventListener("DOMContentLoaded", function () {
    $('#modal${widgetContext.uniqueId}').on('open.zf.reveal', function () {
      let revealButtonElement = document.getElementById("reveal-button${widgetContext.uniqueId}");
      let revealContainerElement = document.getElementById("modal${widgetContext.uniqueId}");
      if (revealButtonElement && revealContainerElement) {
        let videoElements = revealContainerElement.getElementsByTagName('video')
        if (videoElements && videoElements.length > 0) {
          let thisVideoElement = videoElements.item(0);
          thisVideoElement.play();
        }
      }
    })
  });
</script>
