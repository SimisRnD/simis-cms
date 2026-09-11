/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Gives every <style> element the Ace editor injects the page's CSP nonce (issue #1999).
 *
 * Ace ships its CSS inside its JavaScript and injects it as <style> elements, which a style-src
 * without 'unsafe-inline' refuses unless they carry the nonce. It builds every one of them through
 * its lib/dom module's createElement -- the CSS its modules register while ace.js loads is queued
 * and only inserted once an editor exists -- so wrapping that one function covers all of it,
 * themes and the popups it loads later included.
 *
 * Load it with the page's nonce, right after ace.js and before any editor is created:
 *   <script nonce="${cspNonce}" src="${ctx}/javascript/ace-csp-nonce.js"></script>
 * The nonce is read from that tag, so one file serves every page.
 */
(function () {
  var script = document.currentScript;
  var nonce = script && script.nonce;
  if (!nonce || !window.ace) {
    return;
  }
  var dom = window.ace.require('ace/lib/dom');
  var createElement = dom.createElement;
  dom.createElement = function (tag) {
    var element = createElement.apply(this, arguments);
    if (String(tag).toLowerCase() === 'style') {
      element.nonce = nonce;
    }
    return element;
  };
})();
