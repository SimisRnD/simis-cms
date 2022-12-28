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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="imageList" class="java.util.ArrayList" scope="request"/>
<%-- Include the formatting for when TinyMCE uses an iFrame to open the image browser --%>
<%-- All of Foundation.css would override colors and stuff when using the browser directly --%>
<style>
  body {
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", "Liberation Sans", sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol", "Noto Color Emoji";
    font-size: 1rem;
    font-weight: 400;
    line-height: 1.5;
    -webkit-text-size-adjust: 100%;
    -webkit-tap-highlight-color: transparent;
  }

  .grid-container {
    padding-right: 0.625rem;
    padding-left: 0.625rem;
    max-width: 75rem;
    margin: 0 auto;
  }

  .grid-x {
    display: -webkit-box;
    display: -webkit-flex;
    display: -ms-flexbox;
    display: flex;
    -webkit-box-orient: horizontal;
    -webkit-box-direction: normal;
    -webkit-flex-flow: row wrap;
    -ms-flex-flow: row wrap;
    flex-flow: row wrap;
  }

  .cell {
    -webkit-box-flex: 0;
    -webkit-flex: 0 0 auto;
    -ms-flex: 0 0 auto;
    flex: 0 0 auto;
    min-height: 0px;
    min-width: 0px;
    width: 100%;
  }

  .small-up-1 > .cell {
    width: 100%;
  }

  .small-up-2 > .cell {
    width: 50%;
  }

  .small-up-3 > .cell {
    width: 33.33333%;
  }

  .small-up-4 > .cell {
    width: 25%;
  }

  .small-up-5 > .cell {
    width: 20%;
  }

  .small-up-6 > .cell {
    width: 16.66667%;
  }

  .small-up-7 > .cell {
    width: 14.28571%;
  }

  .small-up-8 > .cell {
    width: 12.5%;
  }

  @media print, screen and (min-width: 40em) {
    .grid-margin-x.small-up-1 > .cell {
      width: calc(100% - 1.25rem);
    }

    .grid-margin-x.small-up-2 > .cell {
      width: calc(50% - 1.25rem);
    }

    .grid-margin-x.small-up-3 > .cell {
      width: calc(33.33333% - 1.25rem);
    }

    .grid-margin-x.small-up-4 > .cell {
      width: calc(25% - 1.25rem);
    }

    .grid-margin-x.small-up-5 > .cell {
      width: calc(20% - 1.25rem);
    }

    .grid-margin-x.small-up-6 > .cell {
      width: calc(16.66667% - 1.25rem);
    }

    .grid-margin-x.small-up-7 > .cell {
      width: calc(14.28571% - 1.25rem);
    }

    .grid-margin-x.small-up-8 > .cell {
      width: calc(12.5% - 1.25rem);
    }

    .grid-margin-x.medium-up-1 > .cell {
      width: calc(100% - 1.875rem);
    }

    .grid-margin-x.medium-up-2 > .cell {
      width: calc(50% - 1.875rem);
    }

    .grid-margin-x.medium-up-3 > .cell {
      width: calc(33.33333% - 1.875rem);
    }

    .grid-margin-x.medium-up-4 > .cell {
      width: calc(25% - 1.875rem);
    }

    .grid-margin-x.medium-up-5 > .cell {
      width: calc(20% - 1.875rem);
    }

    .grid-margin-x.medium-up-6 > .cell {
      width: calc(16.66667% - 1.875rem);
    }

    .grid-margin-x.medium-up-7 > .cell {
      width: calc(14.28571% - 1.875rem);
    }

    .grid-margin-x.medium-up-8 > .cell {
      width: calc(12.5% - 1.875rem);
    }
  }

  @media print, screen and (min-width: 64em) {
    .large-up-1 > .cell {
      width: 100%;
    }

    .large-up-2 > .cell {
      width: 50%;
    }

    .large-up-3 > .cell {
      width: 33.33333%;
    }

    .large-up-4 > .cell {
      width: 25%;
    }

    .large-up-5 > .cell {
      width: 20%;
    }

    .large-up-6 > .cell {
      width: 16.66667%;
    }

    .large-up-7 > .cell {
      width: 14.28571%;
    }

    .large-up-8 > .cell {
      width: 12.5%;
    }
  }
  .card {
    display: -webkit-box;
    display: -webkit-flex;
    display: -ms-flexbox;
    display: flex;
    -webkit-box-orient: vertical;
    -webkit-box-direction: normal;
    -webkit-flex-direction: column;
    -ms-flex-direction: column;
    flex-direction: column;
    -webkit-box-flex: 1;
    -webkit-flex-grow: 1;
    -ms-flex-positive: 1;
    flex-grow: 1;
    margin-bottom: 1rem;
    border: 1px solid #e6e6e6;
    border-radius: 0;
    background: #fefefe;
    -webkit-box-shadow: none;
    box-shadow: none;
    overflow: hidden;
    color: #0a0a0a;
  }

  .card > :last-child {
    margin-bottom: 0;
  }

  .card-section {
    -webkit-box-flex: 1;
    -webkit-flex: 1 0 auto;
    -ms-flex: 1 0 auto;
    flex: 1 0 auto;
    padding: 1rem;
  }

  .card-section > :last-child {
    margin-bottom: 0;
  }
</style>
<link rel="stylesheet" type="text/css" href="${ctx}/css/platform.css" />
<div class="grid-container">
  <c:if test="${empty imageList}">
    <p>No images were found.</p>
  </c:if>
  <div class="grid-x grid-margin-x small-up-2 medium-up-4 large-up-4">
    <c:forEach items="${imageList}" var="image" varStatus="status">
      <div class="cell card">
        <div class="image-browser">
          <img onclick="mySubmit(this.dataset.src)" data-src="${ctx}/assets/img/${image.url}"
               src="${ctx}/assets/img/${image.url}" alt="<c:out value="${image.filename}"/>">
        </div>
        <div class="card-section">
          <div>
            <small><c:out value="${image.filename}"/></small><br />
            <small style="color: #999999">${image.width}x${image.height}</small>
            <small style="color: #999999"><c:out value="${number:suffix(image.fileLength)}"/></small>
          </div>
        </div>
      </div>
    </c:forEach>
  </div>
</div>
<script>
  <c:choose>
    <c:when test="${!empty inputId}">
      <%-- Directly called by a web page --%>
      function mySubmit(itemUrl) {
        top.document.getElementById("<c:out value="${inputId}" />").value = itemUrl;
        var imagePreview = top.document.getElementById("<c:out value="${inputId}Preview" />");
        if (imagePreview) {
          imagePreview.src = itemUrl;
        }
        $('#imageBrowserReveal').foundation('close');
      }
    </c:when>
    <c:otherwise>
      <%-- Called by TinyMCE --%>
      function mySubmit(itemUrl) {
        window.parent.postMessage({
            mceAction: 'FileSelected',
            content: itemUrl
        }, '*');
      }
    </c:otherwise>
  </c:choose>
</script>
