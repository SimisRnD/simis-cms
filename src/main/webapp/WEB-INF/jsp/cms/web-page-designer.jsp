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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<jsp:useBean id="widgetSchemaJson" class="java.lang.String" scope="request"/>
<link href="${ctx}/css/jquery-gridmanager-0.3.1/bootstrap.css" rel="stylesheet">
<link href="${ctx}/css/jquery-gridmanager-0.3.1/jquery.gridmanager.css" rel="stylesheet">
<script src="${ctx}/javascript/jquery-gridmanager-0.3.1/jquery-ui.js"></script>
<script src="${ctx}/javascript/jquery-gridmanager-0.3.1/bootstrap.js"></script>
<script src="${ctx}/javascript/jquery-gridmanager-0.3.1/jquery.gridmanager.js"></script>
<%-- Static, developer-authored file (not user input) -- safe to embed unescaped as a JSON script body.
     HTML-escaping it here would corrupt the JSON, since script content isn't entity-decoded. --%>
<script type="application/json" id="widget-schema-json"><c:out value="${widgetSchemaJson}" escapeXml="false"/></script>
<style>
  #designer-container {
    margin: auto;
    max-width: 1170px;
  }
  .margin-bottom-30 { margin-bottom: 30px !important; }

  .widget-picker-overlay {
    display: none;
    position: fixed;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0, 0, 0, 0.4);
    z-index: 2000;
  }
  .widget-picker-panel {
    background: #fff;
    max-width: 420px;
    max-height: 70vh;
    margin: 8vh auto;
    border-radius: 4px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .widget-picker-header {
    display: flex;
    align-items: center;
    padding: 0.75rem;
    border-bottom: 1px solid #ddd;
  }
  .widget-picker-header input {
    flex: 1;
    margin: 0;
  }
  .widget-picker-close {
    margin-left: 0.5rem;
    font-size: 1.5rem;
    line-height: 1;
    color: #666;
    text-decoration: none;
  }
  .widget-picker-list {
    overflow-y: auto;
    padding: 0.5rem 0.75rem 0.75rem;
  }
  .widget-picker-category {
    margin: 0.75rem 0 0.25rem;
    color: #666;
    text-transform: uppercase;
    font-size: 0.75rem;
  }
  .widget-picker-items {
    list-style: none;
    margin: 0;
  }
  .widget-picker-items li a {
    display: block;
    padding: 0.4rem 0.5rem;
    border-radius: 3px;
    color: inherit;
    text-decoration: none;
  }
  .widget-picker-items li a:hover,
  .widget-picker-items li a:focus {
    background: #f0f0f0;
  }
  .widget-picker-items li a i {
    width: 1.25rem;
    display: inline-block;
    text-align: center;
  }
  .widget-picker-empty {
    color: #666;
    padding: 0.5rem;
  }
</style>
<div id="designer-container">
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="${font:far()} ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
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
  <div class="button-container">
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
  </div>
</div>
<script nonce="${cspNonce}">

  // The widget-type picker (issue #532). Widget metadata (label/category/icon) comes from
  // widget-schema.json -- a curated, human-labeled catalog built for exactly this purpose but never
  // wired to anything until now (the modern composition-canvas "+Widget" picker lists raw internal
  // widget-library.xml names instead, with no icons/labels/categories -- this picker is deliberately
  // NOT mirroring that rougher UX, since the schema gives a strictly better one for the same intent).
  var widgetSchema = {};
  try {
    var schemaText = document.getElementById('widget-schema-json').textContent;
    widgetSchema = (JSON.parse(schemaText || '{}').widgets) || {};
  } catch (e) {
    widgetSchema = {};
  }

  var widgetPickerContainer = null;

  function buildWidgetPickerDom() {
    var overlay = $('<div>', {id: 'widget-picker-overlay', 'class': 'widget-picker-overlay'});
    var panel = $('<div>', {'class': 'widget-picker-panel'});
    var searchInput = $('<input>', {type: 'text', id: 'widget-picker-search', placeholder: 'Search widget types…'});
    var closeLink = $('<a>', {href: '#', 'class': 'widget-picker-close', 'aria-label': 'Close'}).html('&times;');
    closeLink.on('click', function (e) {
      e.preventDefault();
      closeWidgetPicker();
    });
    var header = $('<div>', {'class': 'widget-picker-header'}).append(searchInput).append(closeLink);
    var list = $('<div>', {id: 'widget-picker-list', 'class': 'widget-picker-list'});
    panel.append(header).append(list);
    overlay.append(panel);
    overlay.on('click', function (e) {
      if (e.target === overlay[0]) {
        closeWidgetPicker();
      }
    });
    $('body').append(overlay);
    searchInput.on('input', function () {
      renderWidgetPickerList($(this).val());
    });
    return overlay;
  }

  function renderWidgetPickerList(filterText) {
    var list = $('#widget-picker-list');
    list.empty();
    var filter = (filterText || '').toLowerCase();
    var byCategory = {};
    Object.keys(widgetSchema).sort().forEach(function (name) {
      var w = widgetSchema[name] || {};
      var label = w.label || name;
      if (filter && label.toLowerCase().indexOf(filter) === -1 && name.toLowerCase().indexOf(filter) === -1) {
        return;
      }
      var category = w.category || 'Other';
      if (!byCategory[category]) {
        byCategory[category] = [];
      }
      byCategory[category].push({name: name, label: label, icon: w.icon});
    });
    var categories = Object.keys(byCategory).sort();
    categories.forEach(function (category) {
      list.append($('<h6>', {'class': 'widget-picker-category'}).text(category));
      var ul = $('<ul>', {'class': 'widget-picker-items'});
      byCategory[category].forEach(function (item) {
        var link = $('<a>', {href: '#'})
          .append($('<i>', {'class': '${font:far()} ' + (item.icon || 'fa-cube')}))
          .append(' ' + item.label);
        link.on('click', function (e) {
          e.preventDefault();
          insertWidget(item.name, item.label);
          closeWidgetPicker();
        });
        ul.append($('<li>').append(link));
      });
      list.append(ul);
    });
    if (categories.length === 0) {
      list.append($('<p>', {'class': 'widget-picker-empty'}).text('No matching widget types.'));
    }
  }

  function openWidgetPicker(container) {
    widgetPickerContainer = container;
    if ($('#widget-picker-overlay').length === 0) {
      buildWidgetPickerDom();
    }
    renderWidgetPickerList('');
    $('#widget-picker-overlay').show();
    $('#widget-picker-search').val('').trigger('focus');
  }

  function closeWidgetPicker() {
    $('#widget-picker-overlay').hide();
    widgetPickerContainer = null;
  }

  $(document).on('keydown', function (e) {
    if (e.key === 'Escape' && $('#widget-picker-overlay').is(':visible')) {
      closeWidgetPicker();
    }
  });

  function insertWidget(widgetName, widgetLabel) {
    var container = widgetPickerContainer;
    if (!container) {
      return;
    }
    var gm = $("#mycanvas").data("gridmanager");
    var cTagOpen = '<!--' + gm.options.gmEditRegion + '-->',
      cTagClose = '<!--\/' + gm.options.gmEditRegion + '-->',
      elem = null;
    // The data-widget attribute must be on the <h4>, not (only) on the wrapping gm-content div:
    // gm.deinitCanvas() (called on Save) unwraps that div entirely, keeping only its children -- an
    // attribute placed solely on the wrapper is silently lost before the server ever sees it. Confirmed
    // live: the original hardcoded "prototype" insertion carried this same attribute on both the div and
    // the <h4> for exactly this reason; it looked redundant and wasn't.
    $(('.' + gm.options.gmToolClass + ':last'), container)
      .before(elem = $('<div>').addClass(gm.options.gmEditRegion + ' ' + gm.options.contentDraggableClass)
        .append(gm.options.controlContentElem + '<div class="' + gm.options.gmContentRegion + ' callout prototype" data-widget="' + widgetName + '"><h4 data-widget="' + widgetName + '">' + widgetLabel + '</h4><p>Write a description</p></div>')).before(cTagClose).prev().before(cTagOpen);
    gm.initNewContentElem(elem);
  }

  function widget_callback(container, btnElem) {
    openWidgetPicker(container);
  }

  $(document).ready(function () {

    $("#mycanvas").gridmanager({
        debug: 1,

        remoteURL: "${ctx}/admin/web-page-designer?widget=${widgetContext.uniqueId}&token=${userSession.formToken}&webPage=${js:escape(webPage.link)}",
        redirectURL: "${ctx}${js:escape(webPage.link)}",
        controlButtons: [[12], [7, 5], [8, 4], [9, 3], [3, 3, 3, 3], [4, 4, 4], [6, 6], [2, 8, 2], [3, 6, 3], [3, 9], [4, 8]],
        customControls: {
          global_col: [{callback: 'widget_callback', loc: 'top', iconClass: '${font:far()} fa-bars', title: 'Choose a Widget Type'}]
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
