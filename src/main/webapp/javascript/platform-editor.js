(function () {
  'use strict';

  if (!document.body.classList.contains('page-edit-mode')) {
    return;
  }

  var toolbar = document.getElementById('sc-editor-toolbar');
  var pagePath = toolbar ? toolbar.dataset.pagePath : '';
  var ctx = toolbar ? (toolbar.dataset.ctx || '') : '';

  function insertHandle(el, type, label, href) {
    var handle;
    if (href) {
      handle = document.createElement('a');
      handle.href = href;
      handle.title = 'Edit in XML designer';
    } else {
      handle = document.createElement('span');
    }
    handle.className = 'sc-editor-handle sc-editor-handle-' + type;
    handle.textContent = label;
    handle.setAttribute('aria-hidden', 'true');
    // Ensure the parent has a stacking context for absolute positioning
    var pos = window.getComputedStyle(el).position;
    if (pos === 'static') {
      el.style.position = 'relative';
    }
    el.insertBefore(handle, el.firstChild);
  }

  // Sections
  document.querySelectorAll('[data-editor-section]').forEach(function (el) {
    var idx = parseInt(el.dataset.editorSection, 10);
    insertHandle(el, 'section', 'Section ' + (idx + 1));
  });

  // Columns
  document.querySelectorAll('[data-editor-column]').forEach(function (el) {
    var parts = el.dataset.editorColumn.split('-');
    var colIdx = parseInt(parts[1], 10);
    insertHandle(el, 'column', 'Col ' + (colIdx + 1));
  });

  // Widgets — content widgets get no link (inline editor in PR2);
  // non-content widgets link to the XML designer as fallback
  document.querySelectorAll('[data-editor-widget]').forEach(function (el) {
    var isContentWidget = !!el.querySelector('[data-content-unique-id]');
    var href = isContentWidget ? null : (ctx + '/admin/web-page-designer?webPage=' + encodeURIComponent(pagePath));
    insertHandle(el, 'widget', isContentWidget ? 'Content' : 'Widget', href);
  });
})();
