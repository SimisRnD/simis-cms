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
 * Interactions for the two-factor settings screen: draws the enrollment QR code from the otpauth:// URI (a
 * progressive enhancement over the setup key and tap-to-add link), and wires the copy/download buttons for the setup
 * key and the recovery codes. Button handling uses event delegation on the document so it works no matter when the
 * script runs relative to the widget markup.
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

  // Copy text to the clipboard, falling back to execCommand when the async API is unavailable or blocked
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
        // ignore
      }
      document.body.removeChild(textarea);
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () { flash(button, 'Copied'); }, fallback);
      return;
    }
    fallback();
  }

  // Collect the recovery codes shown on the page, one per line
  function recoveryCodesText() {
    var list = document.getElementById('mfa-recovery-codes');
    if (!list) {
      return '';
    }
    return Array.prototype.slice.call(list.querySelectorAll('code'))
      .map(function (code) { return code.textContent.trim(); })
      .join('\n');
  }

  function downloadRecoveryCodes(button) {
    var blob = new Blob([recoveryCodesText() + '\n'], { type: 'text/plain' });
    var url = URL.createObjectURL(blob);
    var link = document.createElement('a');
    link.href = url;
    link.download = 'simis-cms-recovery-codes.txt';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    flash(button, 'Downloaded');
  }

  // One delegated click handler for every button on the screen
  document.addEventListener('click', function (event) {
    var copyKey = event.target.closest('.mfa-copy[data-copy-target]');
    if (copyKey) {
      var target = document.getElementById(copyKey.getAttribute('data-copy-target'));
      if (target) {
        copyToClipboard(target.textContent.trim(), copyKey);
      }
      return;
    }
    var copyCodes = event.target.closest('.mfa-copy-codes');
    if (copyCodes) {
      copyToClipboard(recoveryCodesText(), copyCodes);
      return;
    }
    var download = event.target.closest('.mfa-download-codes');
    if (download) {
      downloadRecoveryCodes(download);
    }
  });

  // Draw the enrollment QR code, if the encoder library is loaded
  function renderQrCode() {
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
    container.style.display = 'none';
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', renderQrCode);
  } else {
    renderQrCode();
  }
})();
