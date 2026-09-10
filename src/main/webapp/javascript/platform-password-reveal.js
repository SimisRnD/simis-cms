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
 * Show/hide toggle for a password input.
 *
 * This behaviour was written for the admin secret fields and lived inline in
 * site-properties-editor.jsp, which meant it existed on exactly one screen. It is the same
 * behaviour a visitor wants when typing a password they cannot see, so it moved here rather
 * than being written a second time; the markup and the CSS class names are unchanged, so the
 * admin screen behaves exactly as it did.
 *
 * Delegated rather than bound per element, for two reasons: inline handlers are blocked by the
 * site-wide CSP, and fields that arrive later (a step-up prompt rendered after a validation
 * bounce) then work without re-binding.
 */
(function () {
  'use strict';

  // jQuery is loaded ahead of this file in main.jsp. Guard anyway -- a page that somehow loads
  // this without it should do nothing rather than throw and take the rest of the page's scripts
  // down with it.
  if (typeof $ === 'undefined') {
    return;
  }

  var SHOW = 'Show the value while typing';
  var HIDE = 'Hide the value';

  // The auth forms render their button hidden and it is revealed here, so a visitor with no
  // JavaScript is not left looking at a control that cannot do anything. The admin secret fields
  // predate this and render their button visible; they are behind a login on a screen that already
  // depends on JavaScript, so they are left as they are rather than changed for symmetry.
  $(function () {
    $('.password-field [data-reveal-secret]').prop('hidden', false);
  });

  $(document).on('click', '[data-reveal-secret]', function () {
    var button = $(this);
    // .secret-field is the admin secret wrapper; .password-field is the block-level variant used
    // by the sign-in, registration and set-password forms. .first() so a sibling input -- the
    // admin "Expires" date, or a confirmation field -- is never the one retyped.
    var input = button.closest('.secret-field, .password-field').find('input').first();
    if (input.length === 0) {
      return;
    }
    var revealed = input.attr('type') === 'text';
    var label = revealed ? SHOW : HIDE;
    input.attr('type', revealed ? 'password' : 'text');
    button.attr('aria-pressed', revealed ? 'false' : 'true');
    button.attr('aria-label', label);
    button.attr('title', label);
    button.find('i').attr('class', revealed ? 'fa fa-eye' : 'fa fa-eye-slash');
    // Return the caret to where the person was typing; the click moved focus to the button.
    input.trigger('focus');
  });
})();
