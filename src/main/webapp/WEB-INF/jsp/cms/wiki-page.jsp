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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="contentHtml" class="java.lang.String" scope="request"/>
<jsp:useBean id="wiki" class="com.simisinc.platform.domain.model.cms.Wiki" scope="request"/>
<jsp:useBean id="wikiPage" class="com.simisinc.platform.domain.model.cms.WikiPage" scope="request"/>
<jsp:useBean id="wikiLinkPrefix" class="java.lang.String" scope="request"/>
<jsp:useBean id="mermaid" class="java.lang.String" scope="request"/>
<link rel="stylesheet" href="${ctx}/javascript/prism-1.30.0/prism.css">
<script src="${ctx}/javascript/prism-1.30.0/prism.min.js"></script>
<c:if test="${mermaid eq 'true'}">
<script src="${ctx}/javascript/mermaid-12.3.0/mermaid.min.js"></script>
<script nonce="${cspNonce}">
  (function () {
    // Mermaid styles a diagram with a style element inside the SVG and style attributes on its
    // shapes, and the site's style-src refuses both: diagrams drew in the browser's default black,
    // with their text measured unstyled. So each diagram is rendered to a string and inserted the
    // way the policy allows: its CSS in a style element carrying this script's nonce, and each
    // style attribute as a CSSOM write.
    var nonce = document.currentScript.nonce;
    mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' });

    // Mermaid lays a diagram out on a temporary element while it renders. For that window only,
    // the styles it sets are written the same way, so it measures what will be shown.
    function renderWithStylesAllowed(render) {
      var setAttribute = Element.prototype.setAttribute;
      var createElement = Document.prototype.createElement;
      var createElementNS = Document.prototype.createElementNS;
      function withNonce(element) {
        if (String(element.localName).toLowerCase() === 'style') {
          setAttribute.call(element, 'nonce', nonce);
        }
        return element;
      }
      Element.prototype.setAttribute = function (name, value) {
        if (this.style && String(name).toLowerCase() === 'style') {
          this.style.cssText = value;
          return;
        }
        return setAttribute.call(this, name, value);
      };
      Document.prototype.createElement = function () {
        return withNonce(createElement.apply(this, arguments));
      };
      Document.prototype.createElementNS = function () {
        return withNonce(createElementNS.apply(this, arguments));
      };
      function restore() {
        Element.prototype.setAttribute = setAttribute;
        Document.prototype.createElement = createElement;
        Document.prototype.createElementNS = createElementNS;
      }
      return render().then(function (result) {
        restore();
        return result;
      }, function (error) {
        restore();
        throw error;
      });
    }

    // Mermaid's SVG markup as nodes, its CSS in a nonce'd style element and each style attribute
    // written through the CSSOM. Parsing the markup still reports the style attributes it meets,
    // as mermaid's own DOMPurify pass does, but those are in a detached document, not the page.
    function toNodes(markup) {
      var svg = new DOMParser().parseFromString(markup, 'text/html').querySelector('svg');
      var css = '';
      Array.prototype.slice.call(svg.querySelectorAll('style')).forEach(function (style) {
        css += style.textContent;
        style.remove();
      });
      var parsed = [svg].concat(Array.prototype.slice.call(svg.querySelectorAll('*')));
      var inline = parsed.map(function (element) {
        var value = element.getAttribute('style');
        element.removeAttribute('style');
        return value;
      });
      var adopted = document.importNode(svg, true);
      var copies = [adopted].concat(Array.prototype.slice.call(adopted.querySelectorAll('*')));
      copies.forEach(function (element, i) {
        if (inline[i] && element.style) {
          element.style.cssText = inline[i];
        }
      });
      var style = document.createElement('style');
      style.setAttribute('nonce', nonce);
      style.textContent = css;
      var fragment = document.createDocumentFragment();
      fragment.appendChild(style);
      fragment.appendChild(adopted);
      return fragment;
    }

    document.addEventListener('DOMContentLoaded', function () {
      // The markdown renderer emits ```mermaid fences as <pre><code class="language-mermaid">
      var fences = Array.prototype.slice.call(document.querySelectorAll('pre > code.language-mermaid'));
      if (fences.length === 0) {
        return;
      }
      renderWithStylesAllowed(function () {
        return Promise.all(fences.map(function (code, i) {
          return mermaid.render('wiki-mermaid-' + i, code.textContent).then(function (result) {
            return result.svg;
          }, function () {
            // A diagram mermaid cannot parse stays as its source
            return null;
          });
        }));
      }).then(function (svgs) {
        svgs.forEach(function (svg, i) {
          if (svg !== null) {
            var diagram = document.createElement('div');
            diagram.className = 'mermaid';
            diagram.appendChild(toNodes(svg));
            fences[i].parentElement.replaceWith(diagram);
          }
        });
      });
    });
  })();
</script>
</c:if>
<c:choose>
  <c:when test="${wiki.startingPage eq wikiPage.id && !empty title}">
    <h2 class="widget-title no-gap"><c:out value="${title}" /></h2>
  </c:when>
  <c:otherwise>
    <h3 class="no-gap h4"><c:out value="${wikiPage.title}" /></h3>
  </c:otherwise>
</c:choose>
<p class="subheader no-gap">
  <small>
    <c:out value="${user:name(wikiPage.modifiedBy)}"/> edited this page <c:out value="${date:relative(wikiPage.modified)}" />
    <c:if test="${wiki.startingPage ne wikiPage.id}">
      <br /><a href="${wikiLinkPrefix}/Home"><i class="fa fa-angle-double-left"></i> Back to Home</a>
    </c:if>
  </small>
</p>
<hr />
<div class="platform-content-container">
  <c:if test="${showEditor eq 'true' && !empty wikiPage.uniqueId}">
    <div class="platform-content-editor"><a aria-label="Edit this wiki page" class="hollow button small secondary" href="${ctx}/wiki-editor?wikiUniqueId=${wiki.uniqueId}&pageUniqueId=${wikiPage.uniqueId}&returnPage=${returnPage}"><i aria-hidden="true" class="${font:fas()} fa-edit"></i></a></div>
  </c:if>
  <div class="markdown-body">${contentHtml}</div>
</div>
