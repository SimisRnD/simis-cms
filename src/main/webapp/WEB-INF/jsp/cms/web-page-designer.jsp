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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="webPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<link href="${ctx}/css/jquery-gridmanager-0.3.1/bootstrap.css" rel="stylesheet">
<link href="${ctx}/css/jquery-gridmanager-0.3.1/jquery.gridmanager.css" rel="stylesheet">
<script src="${ctx}/javascript/jquery-gridmanager-0.3.1/jquery-ui.js"></script>
<script src="${ctx}/javascript/jquery-gridmanager-0.3.1/bootstrap.js"></script>
<script src="${ctx}/javascript/jquery-gridmanager-0.3.1/jquery.gridmanager.js"></script>
<style>
  #designer-container {
    margin: auto;
    max-width: 1170px;
  }
  .margin-bottom-30 { margin-bottom: 30px !important; }
</style>
<div id="designer-container">
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="${font:far()} ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p><small><c:out value="${webPage.link}"/></small></p>
<div id="mycanvas">
  <div class="row margin-bottom-30">
    <div class="column col-sm-12">
      <p>Write your content</p>
    </div>
  </div>
</div>
<%--
<div id="mycanvas">
  <div class="grid-x">
    <div class="cell small-12">
      <div class="primary callout">Write your content</div>
    </div>
  </div>
</div>
--%>
<div>
  <p>
    <c:choose>
      <c:when test="${!empty returnPage}">
        <a id="nextButton" href="${returnPage}" class="button radius secondary">Cancel</a>
      </c:when>
      <c:when test="${!empty webPage.link}">
        <a id="nextButton" href="${ctx}${webPage.link}" class="button radius secondary">Cancel</a>
      </c:when>
      <c:otherwise>

      </c:otherwise>
    </c:choose>
  </p>
</div>
</div>
<script>

  function widget_callback(container, btnElem) {
    // alert("The widget chooser is unavailable");
    // alert('custom control: ' + btnElem.toString());
    var gm = $("#mycanvas").data("gridmanager");
    // gm.addEditableAreaClick(container, btnElem);
    var cTagOpen = '<!--' + gm.options.gmEditRegion + '-->',
      cTagClose = '<!--\/' + gm.options.gmEditRegion + '-->',
      elem = null;
    $(('.' + gm.options.gmToolClass + ':last'), container)
      .before(elem = $('<div>').addClass(gm.options.gmEditRegion + ' ' + gm.options.contentDraggableClass)
        .append(gm.options.controlContentElem + '<div class="' + gm.options.gmContentRegion + ' callout prototype" data-widget="prototype"><h4 data-widget="prototype">Headline</h4><p>Write a description</p></div>')).before(cTagClose).prev().before(cTagOpen);
    gm.initNewContentElem(elem);
  }

  $(document).ready(function () {

    $("#mycanvas").gridmanager({
        debug: 1,

        remoteURL: "${ctx}/admin/web-page-designer?widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPage=${js:escape(webPage.link)}",
        redirectURL: "${ctx}${webPage.link}",
        controlButtons: [[12], [7, 5], [8, 4], [9, 3], [3, 3, 3, 3], [4, 4, 4], [6, 6], [2, 8, 2], [3, 6, 3], [3, 9], [4, 8]],
        customControls: {
          global_col: [{callback: 'widget_callback', loc: 'top', iconClass: '${font:far()} fa-bars'}]
        },
        rowButtonsPrepend: [
          {
            title: "Move",
            element: "a",
            btnClass: "gm-moveRow pull-left",
            iconClass: "${font:far()} fa-arrows "
          },
          {
            title: "New Column",
            element: "a",
            btnClass: "gm-addColumn pull-left  ",
            iconClass: "${font:far()} fa-columns"
          },
          {
            title: "Row Settings",
            element: "a",
            btnClass: "pull-right gm-rowSettings",
            iconClass: "${font:far()} fa-cog"
          }

        ],

        colButtonsAppend: [
          // {
          //   title: "Add Nested Row",
          //   element: "a",
          //   btnClass: "pull-left gm-addRow",
          //   iconClass: "${font:far()} fa-plus-square"
          // },
          {
            title: "Remove Column",
            element: "a",
            btnClass: "pull-right gm-removeCol",
            iconClass: "${font:far()} fa-trash"
          }
        ],

        colSelectEnabled: false,
        colButtonsPrepend: [
          {
            title: "Move",
            element: "a",
            btnClass: "gm-moveCol pull-left",
            iconClass: "${font:far()} fa-arrows "
          },
          {
            title: "Column Settings",
            element: "a",
            btnClass: "pull-right gm-colSettings",
            iconClass: "${font:far()} fa-cog"
          },
          {
            title: "Make Column Narrower",
            element: "a",
            btnClass: "gm-colDecrease pull-left",
            iconClass: "${font:far()} fa-arrow-left"
          },
          {
            title: "Make Column Wider",
            element: "a",
            btnClass: "gm-colIncrease pull-left",
            iconClass: "${font:far()} fa-arrow-right"
          }
        ],

        rowCustomClasses: ["align-center", "text-center", "grid-x", "padding-x", "margin-x"],
        colCustomClasses: ["text-center", "callout", "radius", "primary", "secondary", "box"]

        <%--
                // Foundation
                controlButtonClass: "tiny button",
                gmFloatLeft: "float-left",
                gmFloatRight: "float-right",
                gmBtnGroup: "button-group",
                gmDangerClass: "alert",
                addResponsiveClasses: true,
                rowClass: "grid-x",
                rowSelector: "div.grid-x",
                colClass: "cell",
                colDesktopClass: "large-",
                colTabletClass: "medium-",
                colPhoneClass: "small-",
                colDesktopSelector: "div[class*=large]",
                colTabletSelector: "div[class*=medium]",
                colPhoneSelector: "div[class*=small]",
                colMax: 12,
                colResizeStep: 1
        --%>
      }
    );
  });
</script>
