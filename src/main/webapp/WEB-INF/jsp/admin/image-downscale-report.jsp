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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="h1"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="button-group margin-bottom-10">
  <a class="button tiny secondary radius" href="${widgetContext.uri}">All Images</a>
  <a class="button tiny secondary radius" href="${widgetContext.uri}?view=duplicates">Duplicates</a>
  <a class="button tiny primary radius" href="${widgetContext.uri}?view=downscale">Oversized</a>
</div>
<h2 class="h3">Oversized images</h2>
<p>
  Images over <c:out value="${number:suffix(downscaleMinFileLength)}"/> that are wider than
  <c:out value="${downscaleMaxWidth}"/>px. Each one that is used on a page can be replaced with a copy
  <c:out value="${downscaleMaxWidth}"/>px wide; a PNG with no transparency becomes a JPEG.
  <strong>Nothing has changed yet.</strong> This page only reports.
</p>
<p>
  Replacing an image adds the copy to the library as a new image, with the same alt text, focal point and tags,
  and switches content blocks, blog post bodies and page layouts (drafts included) to the copy.
  The original image and its file are kept as they are. Anything listed under <em>Keeps the original</em> is not changed.
</p>
<c:choose>
  <c:when test="${empty downscaleCandidates}">
    <p class="callout success radius">No oversized images. Every image over <c:out value="${number:suffix(downscaleMinFileLength)}"/> is <c:out value="${downscaleMaxWidth}"/>px wide or less, or is a GIF, WebP or SVG.</p>
  </c:when>
  <c:otherwise>
    <p id="downscaleSummary" aria-live="polite">
      <c:out value="${fn:length(downscaleCandidates)}"/> oversized image<c:if test="${fn:length(downscaleCandidates) != 1}">s</c:if>.
      Working out each new size, one image at a time&hellip;
    </p>
    <div class="table-scroll">
    <table class="unstriped" id="downscaleReport">
      <thead>
        <tr>
          <th>Image</th>
          <th>Now</th>
          <th>After</th>
          <th>Switched to the copy</th>
          <th>Keeps the original</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach items="${downscaleCandidates}" var="candidate">
          <c:set var="image" value="${candidate.image}"/>
          <tr class="downscale-row" data-image-id="${image.id}" data-file-length="<c:out value="${image.fileLength}"/>" data-replaceable="<c:out value="${empty candidate.skipReason}"/>">
            <td>
              <a href="/assets/img/${fn:escapeXml(image.url)}" target="_blank" rel="noopener"><c:out value="${image.filename}"/></a><br />
              <small class="u-color-muted">Image <c:out value="${image.id}"/></small>
            </td>
            <td>
              <c:out value="${image.width}"/>&times;<c:out value="${image.height}"/><br />
              <c:out value="${number:suffix(image.fileLength)}"/> <c:out value="${fn:substringAfter(image.fileType, '/')}"/>
            </td>
            <td class="downscale-after">
              <c:choose>
                <c:when test="${!empty candidate.skipReason}">
                  <em><c:out value="${candidate.skipReason}"/></em>
                </c:when>
                <c:otherwise>
                  <span class="u-color-muted">Waiting&hellip;</span>
                </c:otherwise>
              </c:choose>
            </td>
            <td>
              <c:forEach items="${candidate.rewritable}" var="reference">
                <c:out value="${reference.sourceType}"/>: <c:out value="${empty reference.label ? '(untitled)' : reference.label}"/>
                <small class="u-color-muted">
                  (<c:if test="${reference.spellings.encoded > 0}"><c:out value="${reference.spellings.encoded}"/> encoded</c:if><c:if test="${reference.spellings.encoded > 0 && reference.spellings.literal + reference.spellings.other > 0}">, </c:if><c:if test="${reference.spellings.literal > 0}"><c:out value="${reference.spellings.literal}"/> with literal spaces or characters</c:if><c:if test="${reference.spellings.literal > 0 && reference.spellings.other > 0}">, </c:if><c:if test="${reference.spellings.other > 0}"><c:out value="${reference.spellings.other}"/> with another filename</c:if>)
                </small><br />
              </c:forEach>
              <c:if test="${empty candidate.rewritable}"><span class="u-color-muted">None</span></c:if>
            </td>
            <td>
              <c:forEach items="${candidate.elsewhere}" var="usage">
                <c:out value="${usage.sourceType}"/>: <c:out value="${empty usage.label ? '(untitled)' : usage.label}"/><br />
              </c:forEach>
              <c:if test="${empty candidate.elsewhere}"><span class="u-color-muted">None</span></c:if>
            </td>
          </tr>
        </c:forEach>
      </tbody>
    </table>
    </div>
    <form method="post" id="replaceOversizedForm">
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <input type="hidden" name="command" value="replaceOversizedImages"/>
      <c:forEach items="${downscaleCandidates}" var="candidate">
        <c:if test="${empty candidate.skipReason}">
          <input type="hidden" name="imageId" value="${candidate.image.id}"/>
        </c:if>
      </c:forEach>
      <button type="submit" class="button alert radius" id="replaceOversizedBtn" disabled>Replace oversized images</button>
    </form>
  </c:otherwise>
</c:choose>
<c:if test="${!empty downscaleLeftAlone}">
  <h3 class="h4 margin-top-30">Over <c:out value="${number:suffix(downscaleMinFileLength)}"/>, left alone</h3>
  <p>These are not wider than <c:out value="${downscaleMaxWidth}"/>px, or are a format that is not resized. They are listed so the report accounts for every large file.</p>
  <div class="table-scroll">
  <table class="unstriped">
    <thead>
      <tr>
        <th>Image</th>
        <th>Size</th>
        <th>Why</th>
      </tr>
    </thead>
    <tbody>
      <c:forEach items="${downscaleLeftAlone}" var="image">
        <tr>
          <td><a href="/assets/img/${fn:escapeXml(image.url)}" target="_blank" rel="noopener"><c:out value="${image.filename}"/></a></td>
          <td><c:out value="${image.width}"/>&times;<c:out value="${image.height}"/>, <c:out value="${number:suffix(image.fileLength)}"/> <c:out value="${fn:substringAfter(image.fileType, '/')}"/></td>
          <td>
            <c:choose>
              <c:when test="${image.fileType ne 'image/png' && image.fileType ne 'image/jpeg'}">Not a PNG or JPEG</c:when>
              <c:otherwise><c:out value="${downscaleMaxWidth}"/>px wide or less</c:otherwise>
            </c:choose>
          </td>
        </tr>
      </c:forEach>
    </tbody>
  </table>
  </div>
</c:if>
<script nonce="${cspNonce}">
  (function () {
    var rows = Array.prototype.slice.call(document.querySelectorAll('.downscale-row'));
    var summary = document.getElementById('downscaleSummary');
    var form = document.getElementById('replaceOversizedForm');
    var button = document.getElementById('replaceOversizedBtn');
    if (!rows.length || !form) {
      return;
    }

    function formatBytes(bytes) {
      if (bytes >= 1048576) {
        return (bytes / 1048576).toFixed(1) + ' MB';
      }
      return Math.round(bytes / 1024) + ' KB';
    }

    function setAfter(row, text, isNote) {
      var cell = row.querySelector('.downscale-after');
      cell.textContent = '';
      var node = document.createElement(isNote ? 'em' : 'span');
      node.textContent = text;
      cell.appendChild(node);
    }

    var replaceable = 0;
    var before = 0;
    var after = 0;

    // One at a time: each preview decodes a full-size image on the server
    function next(index) {
      if (index >= rows.length) {
        summary.textContent = rows.length + ' oversized image' + (rows.length === 1 ? '' : 's') + '. '
            + replaceable + ' can be replaced, taking them from ' + formatBytes(before) + ' to ' + formatBytes(after) + '.'
            + ' Nothing has changed yet.';
        if (replaceable > 0) {
          button.disabled = false;
          button.textContent = 'Replace ' + replaceable + ' image' + (replaceable === 1 ? '' : 's');
        }
        return;
      }
      var row = rows[index];
      if (row.getAttribute('data-replaceable') !== 'true') {
        next(index + 1);
        return;
      }
      summary.textContent = 'Working out each new size, ' + (index + 1) + ' of ' + rows.length + '...';
      fetch('${widgetContext.uri}?downscalePreview=true&imageId=' + encodeURIComponent(row.getAttribute('data-image-id')), {
        credentials: 'same-origin'
      }).then(function (response) {
        return response.json();
      }).then(function (data) {
        if (data.skip) {
          setAfter(row, data.skip, true);
          disableImage(row);
        } else {
          setAfter(row, data.width + '×' + data.height + ', ' + formatBytes(data.fileLength) + ' '
              + data.fileType.replace('image/', ''), false);
          replaceable++;
          before += parseInt(row.getAttribute('data-file-length'), 10);
          after += data.fileLength;
        }
      }).catch(function () {
        setAfter(row, 'The new size could not be worked out; this image will be left alone', true);
        disableImage(row);
      }).then(function () {
        next(index + 1);
      });
    }

    // An image whose copy would not be smaller is not sent with the form; the job would skip it anyway
    function disableImage(row) {
      var input = form.querySelector('input[name="imageId"][value="' + row.getAttribute('data-image-id') + '"]');
      if (input) {
        input.parentNode.removeChild(input);
      }
    }

    form.addEventListener('submit', function (event) {
      if (!confirm('Replace ' + replaceable + ' image' + (replaceable === 1 ? '' : 's') + '? Each gets a resized copy, '
          + 'and the pages listed above are switched to it. The originals are kept.')) {
        event.preventDefault();
      }
    });

    next(0);
  })();
</script>
