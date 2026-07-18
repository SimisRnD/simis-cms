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
 * Draws the two-factor enrollment QR code from the otpauth:// URI carried in the page. This is a progressive
 * enhancement: if no QR encoder library is present, the container is hidden and the user relies on the setup key and
 * the tap-to-add link shown alongside it. When a compatible encoder (the qrcode-generator API) is loaded before this
 * script, a scannable code is rendered.
 */
(function () {
  'use strict';

  var container = document.querySelector('.mfa-qrcode[data-otpauth]');
  if (!container) {
    return;
  }

  var uri = container.getAttribute('data-otpauth');
  if (uri && typeof window.qrcode === 'function') {
    try {
      var qr = window.qrcode(0, 'M');        // type number 0 = auto-fit, error-correction level M
      qr.addData(uri);
      qr.make();
      container.innerHTML = qr.createImgTag(5, 8);   // 5px modules, 8px quiet-zone margin
      container.setAttribute('aria-label', 'Two-factor authentication setup QR code');
      return;
    } catch (e) {
      // Fall through and hide the empty container below
    }
  }

  // No encoder available (or rendering failed): hide the empty box; the setup key remains usable.
  container.style.display = 'none';
})();
