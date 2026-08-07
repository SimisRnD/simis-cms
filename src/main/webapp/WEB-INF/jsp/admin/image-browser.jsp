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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<%@ taglib prefix="image" uri="/WEB-INF/tlds/image-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="imageList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="query" class="java.lang.String" scope="request"/>
<jsp:useBean id="sortBy" class="java.lang.String" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h1><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h1>
</c:if>
<%@include file="../page_messages.jspf" %>
<form id="imageSearchForm" method="get" autocomplete="off" class="float-right">
  <div class="input-group no-gap width-auto">
    <input class="input-group-field" type="search" name="query" aria-label="Search images by filename"
           placeholder="<c:if test="${empty query}">Search filenames...</c:if>"<c:if test="${!empty query}"> value="<c:out value="${query}"/>"</c:if> autocomplete="off">
    <label for="imageSortBy" class="show-for-sr">Sort by</label>
    <select id="imageSortBy" name="sortBy" class="input-group-field" style="max-width:220px;" onchange="this.form.submit();">
      <option value="date" <c:if test="${sortBy eq 'date'}">selected</c:if>>Date (Newest First)</option>
      <option value="name" <c:if test="${sortBy eq 'name'}">selected</c:if>>Name (A-Z)</option>
      <option value="size" <c:if test="${sortBy eq 'size'}">selected</c:if>>Size (Largest First)</option>
    </select>
    <div class="input-group-button">
      <button type="submit" class="button search" aria-label="Search"><i class="fa fa-search" aria-hidden="true"></i></button>
    </div>
  </div>
</form>
<div style="clear: both;"></div>
<%-- Client-side only -- filters the usage badges already being computed lazily below, on whichever
     images are on the current page. This deliberately does NOT run a server-side query across the
     whole (possibly 200+ image) list: ImageUsageCommand's usage scan is meant to run for one image
     at a time on demand, not eagerly for a full list (see its class docs), so "Orphaned only" here
     only ever narrows what's already been fetched for this page, not the whole library. --%>
<c:if test="${!empty imageList}">
  <div id="usageFilterBar" class="button-group margin-bottom-10" style="clear:both;">
    <button type="button" class="button tiny primary radius usage-filter-btn" data-usage-filter="all">All (this page)</button>
    <button type="button" class="button tiny secondary radius usage-filter-btn" data-usage-filter="orphaned">Orphaned only</button>
    <button type="button" class="button tiny secondary radius usage-filter-btn" data-usage-filter="used">Used only</button>
  </div>
</c:if>
<div id="bulkActionsBar" class="callout radius" style="display:none;padding:10px 15px;margin-bottom:10px;">
  <span id="bulkSelectedCount"></span>
  <button type="button" class="button tiny alert radius" id="bulkDeleteBtn">Delete Selected</button>
</div>
<div class="grid-container" style="padding: 0;">
  <c:if test="${empty imageList}">
    <p>No images were found.</p>
  </c:if>
  <c:if test="${!empty imageList}">
    <label class="margin-bottom-10">
      <input type="checkbox" id="selectAllImages" aria-label="Select all images"> Select All
    </label>
  </c:if>
  <div class="grid-x grid-margin-x small-up-2 medium-up-3 large-up-5">
    <c:forEach items="${imageList}" var="image" varStatus="status">
      <div class="cell card" data-image-card-id="${image.id}">
        <div class="image-browser" style="position: relative;">
          <input type="checkbox" class="imageRowCheckbox" value="${image.id}"
                 data-filename="${fn:escapeXml(image.filename)}"
                 aria-label="Select <c:out value="${image.filename}"/>"
                 style="position:absolute; top: 5px; left: 5px; z-index: 1;">
          <c:set var="imageHref" value="/assets/img/${image.url}"/>
          <c:set var="mediaImageSrcset" value="${image:srcsetBatch(imageHref, imageVariantsByImageId)}"/>
          <img src="<c:out value="${ctx}${imageHref}"/>"
            <c:if test="${not empty mediaImageSrcset}"> srcset="<c:out value="${mediaImageSrcset}"/>" sizes="150px"</c:if>
            decoding="async"<c:if test="${!status.first}"> loading="lazy"</c:if>>
        </div>
        <div class="card-section">
          <div>
            <small><c:out value="${image.filename}"/></small><br />
            <small style="color: #999999">${image.width}x${image.height}</small>
            <small style="color: #999999"><c:out value="${number:suffix(image.fileLength)}"/></small><br />
            <small style="color: #999999"><fmt:formatDate pattern="yyyy-MM-dd" value="${image.created}" /></small><br />
            <small><a target="_blank" href="${ctx}/assets/img/${fn:escapeXml(image.url)}">Image Link</a></small><br />
            <small><span class="usage-badge label secondary" data-image-id="${image.id}">Checking usage&hellip;</span></small><br />
            <c:if test="${!empty imageTagsByImageId[image.id]}">
              <c:forEach items="${imageTagsByImageId[image.id]}" var="cardTag">
                <span class="label secondary" style="margin:1px;"><c:out value="${cardTag.name}"/></span>
              </c:forEach>
              <br/>
            </c:if>
            <button type="button" class="setFocalPointBtn button tiny secondary radius margin-top-5"
                    data-id="${image.id}" data-filename="${fn:escapeXml(image.filename)}"
                    data-url="${ctx}/assets/img/${fn:escapeXml(image.url)}"
                    data-focal-x="<c:out value="${image.focalX}"/>" data-focal-y="<c:out value="${image.focalY}"/>">
              <i class="fa fa-crosshairs"></i> Focal Point
            </button>
            <button type="button" class="setTagsBtn button tiny secondary radius margin-top-5"
                    data-id="${image.id}" data-filename="${fn:escapeXml(image.filename)}"
                    data-tag-ids="<c:forEach items="${imageTagsByImageId[image.id]}" var="cardTagId" varStatus="cardTagIdStatus">${cardTagId.id}<c:if test="${!cardTagIdStatus.last}">,</c:if></c:forEach>">
              <i class="fa fa-tag"></i> Tags
            </button>
            <button type="button" class="deleteImageBtn button tiny alert radius margin-top-5"
                    data-id="${image.id}" data-filename="${fn:escapeXml(image.filename)}">
              <i class="fa fa-remove"></i> Delete
            </button>
          </div>
        </div>
      </div>
    </c:forEach>
  </div>
</div>
<%@include file="../paging_control.jspf" %>
<%-- Bulk delete confirmation -- selection is scoped to the images currently checked; the list below
     is populated at open time (see the JS) with each selected image's real, freshly-checked usage,
     not just a filename, so the admin sees what deleting an in-use image will break before confirming. --%>
<div class="reveal" id="bulkDeleteReveal" role="dialog" aria-modal="true" aria-labelledby="bulkDeleteRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkDeleteRevealTitle">Delete <span id="bulkDeleteCount">0</span> Image(s)</h4>
  <p id="bulkDeleteUsageNotice" class="callout warning radius" style="display:none;padding:8px 12px;">
    One or more selected images are still in use -- see the list below.
  </p>
  <ul id="bulkDeleteList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkDelete"/>
    <input type="submit" class="button alert radius" value="Delete Images"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<%-- Focal point picker (issue #411 PR3) -- lets an admin mark where an image's subject is, so a
     server-generated square crop can center on it instead of a blind center crop. Populated fresh
     at open time from the clicked card's data-* attributes, same shared-modal shape as
     bulkDeleteReveal above. --%>
<div class="reveal large" id="focalPointReveal" role="dialog" aria-modal="true"
     aria-labelledby="focalPointRevealTitle" data-reveal data-close-on-click="true">
  <h4 id="focalPointRevealTitle">Set Focal Point</h4>
  <p>Click the image where the subject is, so a future square crop keeps it in frame.</p>
  <div id="focalPointImageWrap" style="position:relative; display:inline-block; max-width:100%;">
    <img id="focalPointImage" src="" alt="" style="display:block; max-width:100%; height:auto; cursor:crosshair;">
    <div id="focalPointMarker" style="position:absolute; width:20px; height:20px; margin:-10px 0 0 -10px;
         border:2px solid #fff; border-radius:50%; box-shadow:0 0 0 1px #000, 0 0 4px rgba(0,0,0,.6);
         pointer-events:none; left:50%; top:50%;"></div>
  </div>
  <div class="grid-x grid-margin-x margin-top-10">
    <div class="cell small-6">
      <label>Horizontal <input type="range" id="focalXRange" min="0" max="100" step="1" value="50"></label>
    </div>
    <div class="cell small-6">
      <label>Vertical <input type="range" id="focalYRange" min="0" max="100" step="1" value="50"></label>
    </div>
  </div>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="setFocalPoint"/>
    <input type="hidden" name="imageId" id="focalPointImageId" value=""/>
    <input type="hidden" name="focalX" id="focalXInput" value="50"/>
    <input type="hidden" name="focalY" id="focalYInput" value="50"/>
    <input type="submit" class="button radius" value="Save Focal Point"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<%-- Tag assignment (one shared modal, populated fresh at open time from the clicked card's
     data-tag-ids, same shape as focalPointReveal above). Existing tags are checkboxes; the free-text
     field finds-or-creates a tag by name and assigns it in the same save -- see
     AdminImageBrowserWidget#setTagsAction. --%>
<div class="reveal" id="tagsReveal" role="dialog" aria-modal="true" aria-labelledby="tagsRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="tagsRevealTitle">Set Tags</h4>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="setTags"/>
    <input type="hidden" name="imageId" id="tagsImageId" value=""/>
    <div id="tagsCheckboxList">
      <c:forEach items="${allImageTags}" var="modalTag">
        <%-- Deliberately NOT named "tagId" -- this form has no action attribute, so it POSTs to the
             current document URL, which still carries the page's own ?tagId= query-string filter
             (if one is active) as a GET param. Sharing the name with the filter select above would
             let servlet parameter-map merging silently re-add the filtered tag here regardless of
             this checkbox's checked state. --%>
        <label><input type="checkbox" name="assignTagId" value="${modalTag.id}" class="tagCheckbox" data-tag-id="${modalTag.id}"> <c:out value="${modalTag.name}"/></label>
      </c:forEach>
    </div>
    <label for="newTagName">New tag
      <input type="text" id="newTagName" name="newTagName" maxlength="255" placeholder="e.g. Homepage">
    </label>
    <input type="submit" class="button radius" value="Save Tags"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<script nonce="${cspNonce}">
  (function () {
    // Per-image usage lookups are cached client-side for the life of the page and computed on
    // demand -- never eagerly for the whole list -- see ImageUsageCommand's class docs for why.
    var usageCache = {};

    function checkImageUsage(imageId) {
      if (usageCache[imageId]) {
        return usageCache[imageId];
      }
      var promise = fetch('${widgetContext.uri}?checkUsage=true&imageId=' + encodeURIComponent(imageId), {
        credentials: 'same-origin'
      }).then(function (response) {
        return response.json();
      }).catch(function () {
        // Unknown on error -- never claim "orphaned" when the check itself failed
        return { orphaned: null, usages: [] };
      });
      usageCache[imageId] = promise;
      return promise;
    }

    function describeUsage(data) {
      return data.usages.map(function (u) {
        return u.label + ' (' + u.type + ')';
      }).join(', ');
    }

    function renderBadge(imageId, data) {
      var el = document.querySelector('.usage-badge[data-image-id="' + imageId + '"]');
      if (!el) {
        return;
      }
      var status = 'unknown';
      if (data.orphaned === null) {
        el.textContent = 'Usage unknown';
        el.className = 'usage-badge label secondary';
      } else if (data.orphaned) {
        el.textContent = 'Orphaned';
        el.className = 'usage-badge label warning';
        el.removeAttribute('title');
        status = 'orphaned';
      } else {
        el.textContent = 'Used (' + data.usages.length + ')';
        el.className = 'usage-badge label success';
        el.title = describeUsage(data);
        status = 'used';
      }
      var card = document.querySelector('.cell.card[data-image-card-id="' + imageId + '"]');
      if (card) {
        card.setAttribute('data-usage-status', status);
        applyUsageFilter(card);
      }
    }

    // Orphaned/Used filter (client-side only, this page only -- see the comment above
    // #usageFilterBar). A card not yet resolved (still "Checking usage...") is always shown, since
    // hiding it before its badge resolves would look like it silently vanished.
    var activeUsageFilter = 'all';

    function applyUsageFilter(card) {
      var status = card.getAttribute('data-usage-status');
      var hide = (activeUsageFilter === 'orphaned' && status !== 'orphaned' && status)
          || (activeUsageFilter === 'used' && status !== 'used' && status);
      card.style.display = hide ? 'none' : '';
    }

    document.querySelectorAll('.usage-filter-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        activeUsageFilter = btn.getAttribute('data-usage-filter');
        document.querySelectorAll('.usage-filter-btn').forEach(function (b) {
          b.classList.toggle('primary', b === btn);
          b.classList.toggle('secondary', b !== btn);
        });
        document.querySelectorAll('.cell.card[data-image-card-id]').forEach(applyUsageFilter);
      });
    });

    // Populate each row's badge lazily, one request at a time, after the page has already
    // rendered -- not blocking the initial page load and not run as part of the server-side
    // response for a 200+ image list.
    var badgeIds = Array.prototype.map.call(document.querySelectorAll('.usage-badge'), function (el) {
      return el.getAttribute('data-image-id');
    });
    (function next(i) {
      if (i >= badgeIds.length) {
        return;
      }
      var id = badgeIds[i];
      checkImageUsage(id).then(function (data) {
        renderBadge(id, data);
        next(i + 1);
      });
    })(0);

    // Single delete -- an AJAX usage pre-check builds the real confirmation message before
    // confirm() fires (falls back to the cached lazy-badge result when it already resolved).
    document.querySelectorAll('.deleteImageBtn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var id = btn.getAttribute('data-id');
        var filename = btn.getAttribute('data-filename');
        checkImageUsage(id).then(function (data) {
          var message = 'Delete "' + filename + '"?';
          if (data.orphaned === false && data.usages.length > 0) {
            message = 'This image is used on ' + describeUsage(data) + '. Delete anyway?';
          } else if (data.orphaned === null) {
            message = 'Usage could not be verified for "' + filename + '" (the check failed). Delete anyway?';
          }
          if (confirm(message)) {
            // Carries the current page (issue #498 slice 2) so the post-delete redirect returns
            // here instead of resetting to page 1 -- see AdminImageBrowserWidget#redirectWithQuery.
            // This URL is built fresh in JS rather than reusing window.location.search, so the page
            // number has to be baked in explicitly or postAction() has nothing to forward.
            postAction('${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&imageId=' + id + '&page=${recordPaging.pageNumber}');
          }
        });
      });
    });

    // Bulk select + delete
    var $selectAll = document.getElementById('selectAllImages');
    var rowCheckboxes = document.querySelectorAll('.imageRowCheckbox');
    var $bar = document.getElementById('bulkActionsBar');
    var $count = document.getElementById('bulkSelectedCount');

    function selected() {
      return Array.prototype.filter.call(rowCheckboxes, function (cb) {
        return cb.checked;
      });
    }

    function refresh() {
      var n = selected().length;
      $count.textContent = n + (n === 1 ? ' image selected  ' : ' images selected  ');
      $bar.style.display = n > 0 ? '' : 'none';
      if ($selectAll) {
        $selectAll.indeterminate = n > 0 && n < rowCheckboxes.length;
        $selectAll.checked = n > 0 && n === rowCheckboxes.length;
      }
    }

    if ($selectAll) {
      $selectAll.addEventListener('change', function () {
        rowCheckboxes.forEach(function (cb) {
          cb.checked = $selectAll.checked;
        });
        refresh();
      });
    }
    rowCheckboxes.forEach(function (cb) {
      cb.addEventListener('change', refresh);
    });

    var bulkDeleteBtn = document.getElementById('bulkDeleteBtn');
    if (bulkDeleteBtn) {
      bulkDeleteBtn.addEventListener('click', function () {
        var checked = selected();
        var ids = checked.map(function (cb) {
          return cb.value;
        });
        Promise.all(ids.map(checkImageUsage)).then(function (results) {
          var $reveal = $('#bulkDeleteReveal');
          var $form = $reveal.find('form');
          var $list = $('#bulkDeleteList');
          var $notice = $('#bulkDeleteUsageNotice');
          $form.find('input[name="imageId"]').remove();
          $list.empty();
          var anyInUse = false;
          checked.forEach(function (cb, idx) {
            $form.append($('<input type="hidden" name="imageId">').val(cb.value));
            var data = results[idx];
            var filename = cb.getAttribute('data-filename');
            var text = filename;
            if (data.orphaned === false) {
              anyInUse = true;
              text += ' -- used on ' + describeUsage(data);
            } else if (data.orphaned === null) {
              text += ' -- usage unknown';
            } else {
              text += ' -- orphaned';
            }
            $list.append($('<li>').text(text));
          });
          $('#bulkDeleteCount').text(ids.length);
          $notice.toggle(anyInUse);
          $reveal.foundation('open');
        });
      });
    }

    refresh();

    // Focal point picker
    var $focalReveal = $('#focalPointReveal');
    var focalImg = document.getElementById('focalPointImage');
    var focalMarker = document.getElementById('focalPointMarker');
    var focalXRange = document.getElementById('focalXRange');
    var focalYRange = document.getElementById('focalYRange');
    var focalXInput = document.getElementById('focalXInput');
    var focalYInput = document.getElementById('focalYInput');
    var focalImageIdInput = document.getElementById('focalPointImageId');

    function setFocalMarker(px, py) {
      px = Math.max(0, Math.min(100, px));
      py = Math.max(0, Math.min(100, py));
      focalMarker.style.left = px + '%';
      focalMarker.style.top = py + '%';
      focalXRange.value = Math.round(px);
      focalYRange.value = Math.round(py);
      focalXInput.value = px.toFixed(2);
      focalYInput.value = py.toFixed(2);
    }

    document.querySelectorAll('.setFocalPointBtn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        focalImageIdInput.value = btn.getAttribute('data-id');
        // Image.getUrl() (server-side) always produces webPath + "-" + id + "/" + an
        // application/x-www-form-urlencoded filename, so this can only ever contain the
        // characters below; reject anything else before it reaches an HTML-interpreting sink.
        var dataUrl = btn.getAttribute('data-url');
        if (/^[\w./%!'()~-]+$/.test(dataUrl)) {
          focalImg.src = dataUrl;
        }
        setFocalMarker(parseFloat(btn.getAttribute('data-focal-x')) || 50,
                       parseFloat(btn.getAttribute('data-focal-y')) || 50);
        $focalReveal.foundation('open');
      });
    });

    // getBoundingClientRect() + clientX/clientY, not offsetX/offsetY -- the marker div overlaps
    // the image, and offsetX/offsetY are relative to whatever element the browser decided was the
    // actual click target (mitigated by pointer-events:none on the marker regardless, but this is
    // the more standard, target-independent technique).
    focalImg.addEventListener('click', function (e) {
      var rect = focalImg.getBoundingClientRect();
      setFocalMarker(((e.clientX - rect.left) / rect.width) * 100,
                     ((e.clientY - rect.top) / rect.height) * 100);
    });

    [focalXRange, focalYRange].forEach(function (range) {
      range.addEventListener('input', function () {
        setFocalMarker(parseFloat(focalXRange.value), parseFloat(focalYRange.value));
      });
    });

    // Tag assignment
    var $tagsReveal = $('#tagsReveal');
    var tagsImageIdInput = document.getElementById('tagsImageId');
    var newTagNameInput = document.getElementById('newTagName');

    document.querySelectorAll('.setTagsBtn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        tagsImageIdInput.value = btn.getAttribute('data-id');
        newTagNameInput.value = '';
        var currentTagIds = (btn.getAttribute('data-tag-ids') || '').split(',').filter(Boolean);
        document.querySelectorAll('.tagCheckbox').forEach(function (cb) {
          cb.checked = currentTagIds.indexOf(cb.getAttribute('data-tag-id')) !== -1;
        });
        $tagsReveal.foundation('open');
      });
    });

    // Delete tag (Manage Tags panel, admin-only) -- confirm() first since this un-assigns the tag
    // from every image that carries it, not just one.
    document.querySelectorAll('.deleteImageTagBtn').forEach(function (btn) {
      btn.addEventListener('click', function (e) {
        e.preventDefault();
        var id = btn.getAttribute('data-id');
        var name = btn.getAttribute('data-name');
        if (confirm('Delete the tag "' + name + '"? It will be removed from every image that has it.')) {
          postAction('${widgetContext.uri}?command=deleteTag&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&imageTagId=' + id);
        }
      });
    });
  })();
</script>
