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
 * Color scheme toggle for the utility bar.
 *
 * The scheme itself is driven entirely by the data-theme attribute on <html>, which platform-tokens.css reads. The
 * server renders the site's configured default (theme.ui.mode) into that attribute, and a small inline script in the
 * document head applies any stored visitor preference before the first paint -- so this file never causes a flash and
 * is not needed for the page to render correctly. All it adds is the control.
 *
 * The visitor's choice is stored in localStorage rather than a cookie on purpose: it is a display preference that the
 * server never needs, so it stays out of the request and out of the analytics/privacy surface. Storage access is
 * wrapped because it throws in Safari private browsing and when a site is loaded in a partitioned third-party frame.
 */
(function () {
  'use strict';

  var STORAGE_KEY = 'simis-cms-color-scheme';
  var root = document.documentElement;

  function readStoredScheme() {
    try {
      var value = window.localStorage.getItem(STORAGE_KEY);
      return (value === 'light' || value === 'dark') ? value : null;
    } catch (e) {
      return null;
    }
  }

  function storeScheme(value) {
    try {
      window.localStorage.setItem(STORAGE_KEY, value);
    } catch (e) {
      // Preference is not persisted; the toggle still works for this page view
    }
  }

  // What the visitor is actually looking at right now, resolving "auto"
  // against the operating system setting.
  function effectiveScheme() {
    var mode = root.getAttribute('data-theme');
    if (mode === 'dark' || mode === 'light') {
      return mode;
    }
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }
    return 'light';
  }

  // Queried by class, not id: an administrator can place the widget more
  // than once (say, in the utility bar and the footer), and duplicate ids
  // would be invalid markup with only the first one ever updated.
  function announce(message) {
    var regions = document.querySelectorAll('.platform-color-scheme-status');
    for (var i = 0; i < regions.length; i++) {
      regions[i].textContent = message;
    }
  }

  // The accessible name describes the ACTION, not the state, so a screen
  // reader user knows what pressing it will do.
  function updateToggles() {
    var isDark = effectiveScheme() === 'dark';
    var label = isDark ? 'Switch to light theme' : 'Switch to dark theme';
    var toggles = document.querySelectorAll('.platform-color-scheme-toggle');
    for (var i = 0; i < toggles.length; i++) {
      toggles[i].setAttribute('aria-label', label);
      toggles[i].setAttribute('title', label);
    }
  }

  function applyScheme(scheme) {
    root.setAttribute('data-theme', scheme);
    storeScheme(scheme);
    updateToggles();
    announce(scheme === 'dark' ? 'Dark theme on' : 'Light theme on');
  }

  function onToggleClick(event) {
    var toggle = event.target.closest ? event.target.closest('.platform-color-scheme-toggle') : null;
    if (!toggle) {
      return;
    }
    event.preventDefault();
    applyScheme(effectiveScheme() === 'dark' ? 'light' : 'dark');
  }

  function init() {
    updateToggles();
    // Reveals the control. Until this runs the button is display:none, so a
    // visitor with scripting disabled never sees an inert toggle.
    root.className += (root.className ? ' ' : '') + 'platform-color-scheme-ready';
    document.addEventListener('click', onToggleClick);

    // While the site default is "auto" and the visitor has not chosen, the
    // OS can flip underneath us. The colors follow via CSS; the button's
    // accessible name has to be told.
    if (window.matchMedia) {
      var query = window.matchMedia('(prefers-color-scheme: dark)');
      var onChange = function () {
        if (!readStoredScheme()) {
          updateToggles();
        }
      };
      if (query.addEventListener) {
        query.addEventListener('change', onChange);
      } else if (query.addListener) {
        query.addListener(onChange);
      }
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
