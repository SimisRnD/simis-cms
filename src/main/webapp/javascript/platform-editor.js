(function () {
  'use strict';

  if (!document.body.classList.contains('page-edit-mode')) {
    return;
  }

  var toolbar = document.getElementById('sc-editor-toolbar');
  var pagePath = toolbar ? toolbar.dataset.pagePath : '';
  var ctx = toolbar ? (toolbar.dataset.ctx || '') : '';
  var layoutMode = toolbar ? toolbar.dataset.layoutMode === 'true' : false;

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

  // ── AJAX save (content) ──────────────────────────────────────────────────
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
        originalHtml = activeContent.innerHTML;
        originalHtml = activeContent.innerHTML; // update snapshot so Discard doesn't clobber
        saveBtn.disabled = false;
        saveBtn.textContent = 'Save Draft';
        markHasDraft();
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

  // ── Drag-to-reorder layout ───────────────────────────────────────────────

  var layoutDirty = false;
  var dragSrcEl = null;      // element being dragged
  var dragSrcType = null;    // 'section' or 'widget'
  var dropIndicator = null;

  function getToken() {
    return (typeof mainToken !== 'undefined') ? mainToken : '';
  }

  function markLayoutDirty() {
    layoutDirty = true;
    var dot = document.getElementById('sc-layout-dirty-dot');
    if (dot) dot.classList.add('sc-visible');
    var btn = document.getElementById('sc-save-layout-btn');
    if (btn) btn.disabled = false;
  }

  function markLayoutClean() {
    layoutDirty = false;
    var dot = document.getElementById('sc-layout-dirty-dot');
    if (dot) dot.classList.remove('sc-visible');
    var btn = document.getElementById('sc-save-layout-btn');
    if (btn) btn.disabled = true;
  }

  function markHasDraft() {
    if (toolbar) toolbar.dataset.hasDraft = 'true';
    var pub = document.getElementById('sc-publish-btn');
    var dis = document.getElementById('sc-discard-draft-btn');
    if (pub) pub.style.display = '';
    if (dis) dis.style.display = '';
  }

  // Build the layout JSON from current DOM order.
  // Uses the data-editor-* original indices stored on each element.
  function buildLayoutJson() {
    var sections = [];
    document.querySelectorAll('[data-editor-section]').forEach(function (sectionEl) {
      var sIdx = parseInt(sectionEl.dataset.editorSection, 10);
      var columns = [];
      sectionEl.querySelectorAll(':scope > [data-editor-column]').forEach(function (colEl) {
        var parts = colEl.dataset.editorColumn.split('-');
        var cIdx = parseInt(parts[1], 10);
        var widgets = [];
        colEl.querySelectorAll(':scope > [data-editor-widget]').forEach(function (widgetEl) {
          var wParts = widgetEl.dataset.editorWidget.split('-');
          widgets.push(parseInt(wParts[2], 10));
        });
        columns.push({c: cIdx, widgets: widgets});
      });
      sections.push({s: sIdx, columns: columns});
    });
    return JSON.stringify({sections: sections});
  }

  function insertDragHandle(el, type) {
    var handle = document.createElement('span');
    handle.className = 'sc-editor-drag-handle sc-drag-handle-' + type;
    handle.textContent = '⠿';
    handle.setAttribute('aria-hidden', 'true');
    handle.setAttribute('draggable', 'false'); // handle is just visual; draggable is on el
    el.appendChild(handle);

    // mousedown on handle initiates the drag by setting draggable on parent
    handle.addEventListener('mousedown', function (e) {
      e.stopPropagation();
      el.setAttribute('draggable', 'true');
    });
    el.addEventListener('dragend', function () {
      el.removeAttribute('draggable');
    });
  }

  function insertMoveButtons(el, type) {
    var btns = document.createElement('div');
    btns.className = 'sc-move-btns';
    btns.setAttribute('aria-label', 'Move ' + type);
    btns.innerHTML =
      '<button type="button" aria-label="Move ' + type + ' up" title="Move up">▲</button>' +
      '<button type="button" aria-label="Move ' + type + ' down" title="Move down">▼</button>';
    el.appendChild(btns);

    var upBtn = btns.querySelector('button:first-child');
    var dnBtn = btns.querySelector('button:last-child');

    upBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      var prev = prevSibling(el, el.dataset.editorSection !== undefined ? 'section' : 'widget');
      if (prev) {
        el.parentNode.insertBefore(el, prev);
        markLayoutDirty();
        upBtn.focus();
      }
    });
    dnBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      var next = nextSibling(el, el.dataset.editorSection !== undefined ? 'section' : 'widget');
      if (next) {
        el.parentNode.insertBefore(next, el);
        markLayoutDirty();
        dnBtn.focus();
      }
    });
  }

  function prevSibling(el, type) {
    var attr = type === 'section' ? 'data-editor-section' : 'data-editor-widget';
    var sib = el.previousElementSibling;
    while (sib) {
      if (sib.hasAttribute(attr)) return sib;
      sib = sib.previousElementSibling;
    }
    return null;
  }

  function nextSibling(el, type) {
    var attr = type === 'section' ? 'data-editor-section' : 'data-editor-widget';
    var sib = el.nextElementSibling;
    while (sib) {
      if (sib.hasAttribute(attr)) return sib;
      sib = sib.nextElementSibling;
    }
    return null;
  }

  function ensureDropIndicator() {
    if (!dropIndicator) {
      dropIndicator = document.createElement('div');
      dropIndicator.className = 'sc-drop-indicator';
      dropIndicator.id = 'sc-drop-indicator';
    }
    return dropIndicator;
  }

  function removeDropIndicator() {
    if (dropIndicator && dropIndicator.parentNode) {
      dropIndicator.parentNode.removeChild(dropIndicator);
    }
  }

  // Attach HTML5 drag events to a draggable element (section or widget)
  function makeDraggable(el, type) {
    el.addEventListener('dragstart', function (e) {
      dragSrcEl = el;
      dragSrcType = type;
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', type); // required for Firefox
      // Defer so the browser captures the pre-dragging look
      setTimeout(function () { el.classList.add('sc-dragging'); }, 0);
    });

    el.addEventListener('dragend', function () {
      el.classList.remove('sc-dragging');
      el.removeAttribute('draggable');
      removeDropIndicator();
      dragSrcEl = null;
      dragSrcType = null;
    });

    el.addEventListener('dragover', function (e) {
      if (!dragSrcEl || dragSrcType !== type) return;
      if (dragSrcEl === el) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';

      var rect = el.getBoundingClientRect();
      var mid = rect.top + rect.height / 2;
      var ind = ensureDropIndicator();
      if (e.clientY < mid) {
        el.parentNode.insertBefore(ind, el);
      } else {
        if (el.nextElementSibling) {
          el.parentNode.insertBefore(ind, el.nextElementSibling);
        } else {
          el.parentNode.appendChild(ind);
        }
      }
    });

    el.addEventListener('dragleave', function (e) {
      // Only remove indicator if we're leaving entirely out of this element
      if (!el.contains(e.relatedTarget)) {
        removeDropIndicator();
      }
    });

    el.addEventListener('drop', function (e) {
      if (!dragSrcEl || dragSrcType !== type || dragSrcEl === el) return;
      e.preventDefault();
      e.stopPropagation();

      var ind = ensureDropIndicator();
      if (ind.parentNode) {
        ind.parentNode.insertBefore(dragSrcEl, ind);
      }
      removeDropIndicator();
      markLayoutDirty();
    });
  }

  // ── Confirm modal ────────────────────────────────────────────────────────

  var confirmCallback = null;

  function buildConfirmModal() {
    var overlay = document.createElement('div');
    overlay.id = 'sc-confirm-modal';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.innerHTML =
      '<div id="sc-confirm-modal-box">' +
        '<p id="sc-confirm-modal-msg"></p>' +
        '<div id="sc-confirm-modal-actions">' +
          '<button type="button" id="sc-confirm-cancel">Cancel</button>' +
          '<button type="button" id="sc-confirm-ok">Confirm</button>' +
        '</div>' +
      '</div>';
    document.body.appendChild(overlay);

    document.getElementById('sc-confirm-ok').addEventListener('click', function () {
      overlay.classList.remove('sc-visible');
      if (typeof confirmCallback === 'function') {
        var cb = confirmCallback;
        confirmCallback = null;
        cb();
      }
    });
    document.getElementById('sc-confirm-cancel').addEventListener('click', function () {
      overlay.classList.remove('sc-visible');
      confirmCallback = null;
    });
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) {
        overlay.classList.remove('sc-visible');
        confirmCallback = null;
      }
    });
    return overlay;
  }

  function showConfirm(msg, okLabel, isDanger, callback) {
    document.getElementById('sc-confirm-modal-msg').textContent = msg;
    var okBtn = document.getElementById('sc-confirm-ok');
    okBtn.textContent = okLabel || 'Confirm';
    okBtn.classList.toggle('sc-danger', !!isDanger);
    confirmCallback = callback;
    document.getElementById('sc-confirm-modal').classList.add('sc-visible');
    okBtn.focus();
  }

  // ── Publish draft ────────────────────────────────────────────────────────

  function buildPublishButton() {
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.id = 'sc-publish-btn';
    btn.className = 'button small success';
    btn.textContent = 'Publish';
    if (toolbar.dataset.hasDraft !== 'true') btn.style.display = 'none';
    btn.addEventListener('click', function () {
      showConfirm(
        'Publish this draft? The current live page will be replaced.',
        'Publish', false, doPublishDraft
      );
    });
    var exitBtn = document.getElementById('sc-editor-exit');
    if (exitBtn) toolbar.insertBefore(btn, exitBtn);
    else toolbar.appendChild(btn);
    return btn;
  }

  function buildDiscardDraftButton() {
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.id = 'sc-discard-draft-btn';
    btn.className = 'button small hollow secondary';
    btn.textContent = 'Discard Draft';
    if (toolbar.dataset.hasDraft !== 'true') btn.style.display = 'none';
    btn.addEventListener('click', function () {
      showConfirm(
        'Discard all draft changes? This cannot be undone.',
        'Discard', true, doDiscardDraft
      );
    });
    var exitBtn = document.getElementById('sc-editor-exit');
    if (exitBtn) toolbar.insertBefore(btn, exitBtn);
    else toolbar.appendChild(btn);
    return btn;
  }

  function doPublishDraft() {
    var btn = document.getElementById('sc-publish-btn');
    btn.disabled = true;
    btn.textContent = 'Publishing…';
    setToolbarStatus('Publishing…');

    var qs = new URLSearchParams();
    qs.append('action', 'publishDraft');
    qs.append('token', getToken());

    fetch(window.location.pathname + '?' + qs.toString(), {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'}
    })
    .then(function (resp) {
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      return resp.json();
    })
    .then(function (data) {
      if (data.success) {
        setToolbarStatus('Published! Reloading…');
        window.location.reload();
      } else {
        throw new Error(data.error || 'Publish failed');
      }
    })
    .catch(function (err) {
      setToolbarStatus('Publish failed: ' + err.message);
      btn.disabled = false;
      btn.textContent = 'Publish';
    });
  }

  function doDiscardDraft() {
    var btn = document.getElementById('sc-discard-draft-btn');
    btn.disabled = true;
    btn.textContent = 'Discarding…';
    setToolbarStatus('Discarding draft…');

    var qs = new URLSearchParams();
    qs.append('action', 'discardDraft');
    qs.append('token', getToken());

    fetch(window.location.pathname + '?' + qs.toString(), {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'}
    })
    .then(function (resp) {
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      return resp.json();
    })
    .then(function (data) {
      if (data.success) {
        setToolbarStatus('Draft discarded. Reloading…');
        window.location.reload();
      } else {
        throw new Error(data.error || 'Discard failed');
      }
    })
    .catch(function (err) {
      setToolbarStatus('Discard failed: ' + err.message);
      btn.disabled = false;
      btn.textContent = 'Discard Draft';
    });
  }

  // ── Save Layout button and AJAX post ─────────────────────────────────────

  function buildSaveLayoutButton() {
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.id = 'sc-save-layout-btn';
    btn.className = 'button small success';
    btn.disabled = true;
    btn.innerHTML = 'Save Layout<span id="sc-layout-dirty-dot" aria-hidden="true"></span>';
    btn.addEventListener('click', saveLayout);
    var exitBtn = document.getElementById('sc-editor-exit');
    if (exitBtn) toolbar.insertBefore(btn, exitBtn);
    else toolbar.appendChild(btn);
  }

  function saveLayout() {
    var btn = document.getElementById('sc-save-layout-btn');
    var token = getToken();
    if (!token) {
      setToolbarStatus('No session token');
      return;
    }

    btn.disabled = true;
    btn.textContent = 'Saving…';
    setToolbarStatus('Saving layout…');

    var qs = new URLSearchParams();
    qs.append('action', 'saveDraftLayout');
    qs.append('token', token);
    qs.append('layout', buildLayoutJson());

    fetch(window.location.pathname + '?' + qs.toString(), {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'}
    })
    .then(function (resp) {
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      return resp.json();
    })
    .then(function (data) {
      if (data.success) {
        markLayoutClean();
        btn.innerHTML = 'Save Layout<span id="sc-layout-dirty-dot" aria-hidden="true"></span>';
        btn.disabled = true;
        setToolbarStatus('Layout saved — reload to see');
        markHasDraft();
      } else {
        throw new Error(data.error || 'Unknown error');
      }
    })
    .catch(function (err) {
      setToolbarStatus('Layout save failed: ' + err.message);
      btn.innerHTML = 'Save Layout<span id="sc-layout-dirty-dot" aria-hidden="true"></span>';
      markLayoutDirty(); // re-enables button
    });
  }

  // ── Bootstrap ─────────────────────────────────────────────────────────────

  inlineToolbar = buildInlineToolbar();
  linkPrompt = buildLinkPrompt();
  buildConfirmModal();

  if (layoutMode) {
    buildSaveLayoutButton();      // inserts before Exit
    buildPublishButton();         // inserts before Exit (right of Save Layout)
    buildDiscardDraftButton();    // inserts before Exit (between Save Layout and Publish)
  }

  // Sections
  document.querySelectorAll('[data-editor-section]').forEach(function (el) {
    var idx = parseInt(el.dataset.editorSection, 10);
    insertHandle(el, 'section', 'Section ' + (idx + 1));
    if (layoutMode) {
      insertDragHandle(el, 'section');
      insertMoveButtons(el, 'section');
      makeDraggable(el, 'section');
    }
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
    if (layoutMode) {
      insertDragHandle(el, 'widget');
      insertMoveButtons(el, 'widget');
      makeDraggable(el, 'widget');
    }
  });

  // Click on a content block → activate inline editor
  document.addEventListener('click', function (e) {
    if (e.target.closest('.sc-editor-drag-handle, .sc-move-btns, .sc-editor-handle')) return;

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
