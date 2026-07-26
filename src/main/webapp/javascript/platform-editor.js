(function () {
  'use strict';

  if (!document.body.classList.contains('page-edit-mode')) {
    return;
  }

  var toolbar = document.getElementById('sc-editor-toolbar');
  var pagePath = toolbar ? toolbar.dataset.pagePath : '';
  var ctx = toolbar ? (toolbar.dataset.ctx || '') : '';

  // ── Shared inline toolbar + link prompt DOM ──────────────────────────────

  var inlineToolbar = null;
  var linkPrompt = null;
  var activeContent = null;     // the .platform-content div currently being edited
  var activeWidget = null;      // its [data-editor-widget] ancestor
  var savedSelection = null;    // Selection saved before link prompt opens
  var originalHtml = null;      // snapshot before editing begins (for Discard)
  var actionsBar = null;        // Save/Discard bar for the active widget

  function buildInlineToolbar() {
    var t = document.createElement('div');
    t.id = 'sc-inline-toolbar';
    t.setAttribute('role', 'toolbar');
    t.setAttribute('aria-label', 'Text format');
    t.style.display = 'none';
    t.innerHTML =
      '<button type="button" data-cmd="bold" title="Bold (Ctrl+B)"><b>B</b></button>' +
      '<button type="button" data-cmd="italic" title="Italic (Ctrl+I)"><i>I</i></button>' +
      '<button type="button" data-cmd="underline" title="Underline (Ctrl+U)"><u>U</u></button>' +
      '<div class="sc-inline-sep" aria-hidden="true"></div>' +
      '<button type="button" data-cmd="link" title="Insert / edit link">&#128279;</button>' +
      '<button type="button" data-cmd="unlink" title="Remove link">&#10006;</button>';
    document.body.appendChild(t);

    t.addEventListener('mousedown', function (e) {
      e.preventDefault(); // keep selection alive
      var btn = e.target.closest('button[data-cmd]');
      if (!btn) return;
      var cmd = btn.dataset.cmd;
      if (cmd === 'link') {
        openLinkPrompt();
      } else if (cmd === 'unlink') {
        document.execCommand('unlink', false, null);
      } else {
        document.execCommand(cmd, false, null);
      }
      updateToolbarState();
    });
    return t;
  }

  function buildLinkPrompt() {
    var p = document.createElement('div');
    p.id = 'sc-link-prompt';
    p.style.display = 'none';
    p.innerHTML =
      '<input type="text" placeholder="https://" aria-label="URL"/>' +
      '<button type="button">OK</button>' +
      '<button type="button" class="sc-cancel">Cancel</button>';
    document.body.appendChild(p);

    var input = p.querySelector('input');
    var ok = p.querySelector('button:not(.sc-cancel)');
    var cancel = p.querySelector('.sc-cancel');

    ok.addEventListener('click', function () {
      var url = input.value.trim();
      if (url) {
        restoreSelection();
        document.execCommand('createLink', false, url);
      }
      p.style.display = 'none';
      input.value = '';
    });
    cancel.addEventListener('click', function () {
      p.style.display = 'none';
      input.value = '';
      restoreSelection();
    });
    input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') ok.click();
      if (e.key === 'Escape') cancel.click();
    });
    return p;
  }

  function saveSelection() {
    var sel = window.getSelection();
    if (sel && sel.rangeCount) {
      savedSelection = sel.getRangeAt(0).cloneRange();
    }
  }

  function restoreSelection() {
    if (!savedSelection) return;
    var sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(savedSelection);
  }

  function openLinkPrompt() {
    saveSelection();
    // Check if cursor is on an existing link
    var sel = window.getSelection();
    var existing = '';
    if (sel && sel.rangeCount) {
      var node = sel.anchorNode;
      while (node && node !== activeContent) {
        if (node.nodeName === 'A') { existing = node.href; break; }
        node = node.parentNode;
      }
    }
    linkPrompt.querySelector('input').value = existing;
    positionNear(linkPrompt, inlineToolbar);
    linkPrompt.style.display = 'flex';
    linkPrompt.querySelector('input').focus();
  }

  function positionAboveSelection(el) {
    var sel = window.getSelection();
    if (!sel || !sel.rangeCount) return;
    var rect = sel.getRangeAt(0).getBoundingClientRect();
    var elH = el.offsetHeight || 32;
    el.style.top = (window.scrollY + rect.top - elH - 6) + 'px';
    el.style.left = (window.scrollX + rect.left) + 'px';
  }

  function positionNear(el, anchor) {
    var rect = anchor.getBoundingClientRect();
    el.style.top = (window.scrollY + rect.bottom + 4) + 'px';
    el.style.left = (window.scrollX + rect.left) + 'px';
  }

  function showInlineToolbar() {
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed || !activeContent) {
      inlineToolbar.style.display = 'none';
      return;
    }
    if (!activeContent.contains(sel.anchorNode)) {
      inlineToolbar.style.display = 'none';
      return;
    }
    updateToolbarState();
    inlineToolbar.style.display = 'flex';
    positionAboveSelection(inlineToolbar);
  }

  function updateToolbarState() {
    ['bold', 'italic', 'underline'].forEach(function (cmd) {
      var btn = inlineToolbar.querySelector('[data-cmd="' + cmd + '"]');
      if (btn) {
        btn.classList.toggle('sc-active', document.queryCommandState(cmd));
      }
    });
  }

  function hideInlineToolbar() {
    if (inlineToolbar) inlineToolbar.style.display = 'none';
  }

  // ── Actions bar (Save / Discard) ─────────────────────────────────────────

  function buildActionsBar(widgetEl) {
    var bar = document.createElement('div');
    bar.className = 'sc-inline-actions';
    bar.innerHTML =
      '<button type="button" class="button small success sc-save-btn">Save Draft</button>' +
      '<button type="button" class="button small hollow secondary sc-discard-btn">Discard</button>' +
      '<span class="sc-inline-save-msg" aria-live="polite"></span>';
    widgetEl.appendChild(bar);

    bar.querySelector('.sc-save-btn').addEventListener('click', function () {
      saveContentDraft(bar);
    });
    bar.querySelector('.sc-discard-btn').addEventListener('click', function () {
      discardEdit();
    });
    return bar;
  }

  function setStatus(bar, msg, isError) {
    var span = bar.querySelector('.sc-inline-save-msg');
    if (!span) return;
    span.textContent = msg;
    span.style.color = isError ? '#dc3545' : '#28a745';
  }

  // ── Activate / deactivate inline editing ─────────────────────────────────

  function activateEdit(contentEl) {
    if (activeContent === contentEl) return;
    if (activeContent) deactivateEdit(false);

    activeContent = contentEl;
    activeWidget = contentEl.closest('[data-editor-widget]');
    originalHtml = contentEl.innerHTML;

    contentEl.setAttribute('contenteditable', 'true');
    contentEl.setAttribute('spellcheck', 'true');
    if (activeWidget) activeWidget.classList.add('sc-editing');

    actionsBar = buildActionsBar(activeWidget || contentEl.parentElement);
    contentEl.focus();
    setToolbarStatus('Editing…');
  }

  function deactivateEdit(keepChanges) {
    if (!activeContent) return;
    if (!keepChanges && originalHtml !== null) {
      activeContent.innerHTML = originalHtml;
    }
    activeContent.removeAttribute('contenteditable');
    activeContent.removeAttribute('spellcheck');
    if (activeWidget) activeWidget.classList.remove('sc-editing');
    if (actionsBar && actionsBar.parentNode) actionsBar.parentNode.removeChild(actionsBar);
    hideInlineToolbar();
    actionsBar = null;
    activeContent = null;
    activeWidget = null;
    originalHtml = null;
    savedSelection = null;
    setToolbarStatus('');
  }

  function discardEdit() {
    deactivateEdit(false);
  }

  function setToolbarStatus(msg) {
    var el = document.getElementById('sc-editor-status');
    if (el) el.textContent = msg;
  }

  // ── AJAX save ─────────────────────────────────────────────────────────────

  function saveContentDraft(bar) {
    if (!activeContent) return;
    var uniqueId = activeContent.dataset.contentUniqueId;
    var widgetId = activeContent.dataset.widgetId;
    var pageUri = activeContent.dataset.pageUri || window.location.pathname;
    var token = (typeof mainToken !== 'undefined') ? mainToken : '';

    if (!uniqueId || !widgetId || !token) {
      setStatus(bar, 'Missing parameters', true);
      return;
    }

    var saveBtn = bar.querySelector('.sc-save-btn');
    saveBtn.disabled = true;
    saveBtn.textContent = 'Saving…';
    setStatus(bar, '');

    var qs = new URLSearchParams();
    qs.append('action', 'saveDraft');
    qs.append('widget', widgetId);
    qs.append('token', token);

    var body = new URLSearchParams();
    body.append('html', activeContent.innerHTML);

    fetch(pageUri + '?' + qs.toString(), {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      body: body.toString()
    })
    .then(function (resp) {
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      return resp.json();
    })
    .then(function (data) {
      if (data.success) {
        setStatus(bar, 'Saved');
        setToolbarStatus('Draft saved');
        originalHtml = activeContent.innerHTML; // update snapshot so Discard doesn't clobber
        saveBtn.disabled = false;
        saveBtn.textContent = 'Save Draft';
      } else {
        throw new Error(data.error || 'Unknown error');
      }
    })
    .catch(function (err) {
      setStatus(bar, 'Error: ' + err.message, true);
      setToolbarStatus('');
      saveBtn.disabled = false;
      saveBtn.textContent = 'Save Draft';
    });
  }

  // ── Handle overlay labels ─────────────────────────────────────────────────

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
    var pos = window.getComputedStyle(el).position;
    if (pos === 'static') {
      el.style.position = 'relative';
    }
    el.insertBefore(handle, el.firstChild);
  }

  // ── Bootstrap ─────────────────────────────────────────────────────────────

  inlineToolbar = buildInlineToolbar();
  linkPrompt = buildLinkPrompt();

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

  // Widgets
  document.querySelectorAll('[data-editor-widget]').forEach(function (el) {
    var isContentWidget = !!el.querySelector('[data-content-unique-id]');
    var href = isContentWidget ? null : (ctx + '/admin/web-page-designer?webPage=' + encodeURIComponent(pagePath));
    insertHandle(el, 'widget', isContentWidget ? 'Content' : 'Widget', href);
  });

  // Click on a content block → activate inline editor
  document.addEventListener('click', function (e) {
    var contentEl = e.target.closest('.platform-content[data-content-unique-id]');
    if (contentEl) {
      e.preventDefault();
      activateEdit(contentEl);
      return;
    }
    // Click outside the active editor (not on toolbar or actions bar) → deactivate
    if (activeContent) {
      var inToolbar = inlineToolbar && inlineToolbar.contains(e.target);
      var inPrompt = linkPrompt && linkPrompt.contains(e.target);
      var inActions = actionsBar && actionsBar.contains(e.target);
      var inContent = activeContent.contains(e.target);
      if (!inToolbar && !inPrompt && !inActions && !inContent) {
        deactivateEdit(true);
      }
    }
  });

  // Show/reposition toolbar on selection change
  document.addEventListener('selectionchange', function () {
    if (!activeContent) return;
    showInlineToolbar();
  });

  // Keyboard shortcut: Escape exits editor
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && activeContent) {
      deactivateEdit(true);
    }
  });

  // Hide floating toolbar when window scrolls (will reposition on next selection)
  window.addEventListener('scroll', hideInlineToolbar, {passive: true});
})();
