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
 * Copy-to-clipboard for any button marked <button class="copy-button" data-copy-target="elementId">.
 *
 * Handling is delegated from the document so it works no matter when this script runs relative to the
 * markup, and so a button rendered only after a form post still works without re-binding. There is no
 * inline handler anywhere: the site's Content-Security-Policy does not permit them, and an inline
 * onclick here would fail silently in the browser while looking correct in the source.
 *
 * The async clipboard API is unavailable in insecure contexts and can be refused by permissions policy,
 * so execCommand remains as a fallback rather than leaving the button doing nothing.
 */
(function () {
  'use strict';

  // Briefly change a button's label to confirm the action
  function flash(button, message) {
    var original = button.getAttribute('data-label') || button.textContent;
    button.setAttribute('data-label', original);
    button.textContent = message;
    setTimeout(function () { button.textContent = original; }, 1500);
  }

  function copyToClipboard(text, button) {
    var fallback = function () {
      var textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      try {
        document.execCommand('copy');
        flash(button, 'Copied');
      } catch (e) {
        // Nothing further to try; leave the value on screen to select by hand
      }
      document.body.removeChild(textarea);
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () { flash(button, 'Copied'); }, fallback);
      return;
    }
    fallback();
  }

  // Select the whole value on focus so it can be copied by hand when the clipboard is unavailable.
  // Delegated for the same reason as the click handler, and kept here rather than as an inline
  // onfocus attribute, which the Content-Security-Policy would block.
  document.addEventListener('focusin', function (event) {
    var field = event.target.closest('input.select-on-focus');
    if (field) {
      field.select();
    }
  });

  document.addEventListener('click', function (event) {
    var button = event.target.closest('.copy-button[data-copy-target]');
    if (!button) {
      return;
    }
    var target = document.getElementById(button.getAttribute('data-copy-target'));
    if (!target) {
      return;
    }
    // An input carries its text in value; anything else in its text content.
    var text = typeof target.value === 'string' ? target.value : target.textContent;
    copyToClipboard(text.trim(), button);
  });
})();
