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
  var widthPicker = null;
  var classPickerApplyFn = null;
  var prefsPanel = null;
  var prefsPanelTarget = null;
  var widgetPicker = null;
  var widgetPickerTarget = null;
  var widgetNames = [];
  var activeContent = null;     // the .platform-content div currently being edited
  var activeWidget = null;      // its [data-editor-widget] ancestor
  var savedSelection = null;    // Selection saved before link prompt opens
  var originalHtml = null;      // snapshot before editing begins (for Discard)
  var actionsBar = null;        // Save/Discard bar for the active widget
  var activeImageWidget = null; // {s, c, w, el, prefKey} widget armed for the next Media Library file click (#772)

  // Quill inline editor state
  var activeQuill = null;         // active Quill instance
  var quillHost = null;           // widget element hosting the active Quill editor
  var quillContentEl = null;      // .platform-content element that was replaced
  var quillUniqueId = null;       // content uniqueId being edited
  var quillDirty = false;         // true if user has made changes
  var quillActionsBar = null;     // Save/Discard bar for the Quill editor

  // ── Quill inline rich text editor (P5 Slice 1) ───────────────────────────

  function getUniqueIdFromWidget(widgetEl) {
    try {
      var prefs = JSON.parse(widgetEl.dataset.editorWidgetPrefs || '{}');
      return prefs.uniqueId || null;
    } catch (e) { return null; }
  }

  function buildQuillActionsBar(anchorEl) {
    var bar = document.createElement('div');
    bar.className = 'sc-quill-actions';
    var saveBtn = document.createElement('button');
    saveBtn.type = 'button';
    saveBtn.className = 'sc-save-btn';
    saveBtn.textContent = 'Save Draft';
    saveBtn.addEventListener('click', function () { saveQuillDraft(bar); });
    var discardBtn = document.createElement('button');
    discardBtn.type = 'button';
    discardBtn.className = 'sc-discard-btn';
    discardBtn.textContent = 'Discard';
    discardBtn.addEventListener('click', function () { deactivateQuill(false); });
    bar.appendChild(saveBtn);
    bar.appendChild(discardBtn);
    anchorEl.parentNode.insertBefore(bar, anchorEl.nextSibling);
    return bar;
  }

  function activateQuill(widgetEl, uniqueId) {
    if (activeContent) deactivateEdit(false);
    if (activeQuill) deactivateQuill(false);

    var contentEl = widgetEl.querySelector('.platform-content');
    if (!contentEl) return;

    quillHost = widgetEl;
    quillContentEl = contentEl;
    quillUniqueId = uniqueId;
    quillDirty = false;

    // Build a container div and insert after contentEl; hide contentEl
    var container = document.createElement('div');
    container.className = 'sc-quill-editor-container';
    contentEl.parentNode.insertBefore(container, contentEl.nextSibling);
    contentEl.style.display = 'none';

    var quill = new Quill(container, {
      theme: 'snow',
      placeholder: 'Start writing…',
      modules: {
        toolbar: [
          ['bold', 'italic', 'code'],
          [{ header: [1, 2, 3, 4, 5, 6, false] }],
          [{ list: 'ordered' }, { list: 'bullet' }],
          ['blockquote', 'code-block'],
          ['link'],
          ['clean']
        ]
      }
    });
    activeQuill = quill;

    quill.on('text-change', function () { quillDirty = true; });

    // Fetch stored content from server
    fetch(window.location.pathname + '?action=getWidgetContent&uniqueId=' + encodeURIComponent(uniqueId))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (!data.success) return;
        if (data.format === 2 && data.content) {
          try { quill.setContents(JSON.parse(data.content), 'silent'); } catch (e) {}
        } else if (data.content) {
          quill.clipboard.dangerouslyPasteHTML(data.content, 'silent');
        }
        quillDirty = false;
      })
      .catch(function () {
        quill.clipboard.dangerouslyPasteHTML(contentEl.innerHTML, 'silent');
        quillDirty = false;
      });

    quillActionsBar = buildQuillActionsBar(container);
    widgetEl.classList.add('sc-quill-editing');
    setToolbarStatus('Rich text editing…');
    quill.focus();
  }

  function deactivateQuill(keepChanges) {
    if (!activeQuill) return;
    var container = activeQuill.container && activeQuill.container.closest('.sc-quill-editor-container');
    // Always restore visibility -- keepChanges only reflects whether the caller already wrote
    // new content into quillContentEl (save) or left it untouched (discard); either way the
    // editor session is ending and the underlying element must reappear.
    if (quillContentEl) {
      quillContentEl.style.display = '';
    }
    if (container) {
      // Quill auto-creates its own .ql-toolbar as container's previous sibling when the
      // toolbar module is configured as an array rather than a container element; it isn't
      // inside container, so removing container alone leaves it orphaned in the page.
      var toolbarEl = container.previousElementSibling;
      if (toolbarEl && toolbarEl.classList.contains('ql-toolbar') && toolbarEl.parentNode) {
        toolbarEl.parentNode.removeChild(toolbarEl);
      }
      if (container.parentNode) container.parentNode.removeChild(container);
    }
    if (quillActionsBar && quillActionsBar.parentNode) quillActionsBar.parentNode.removeChild(quillActionsBar);
    if (quillHost) quillHost.classList.remove('sc-quill-editing');
    activeQuill = null;
    quillHost = null;
    quillContentEl = null;
    quillUniqueId = null;
    quillDirty = false;
    quillActionsBar = null;
    setToolbarStatus('');
  }

  function saveQuillDraft(bar) {
    if (!activeQuill || !quillUniqueId) return;
    var saveBtn = bar.querySelector('.sc-save-btn');
    saveBtn.disabled = true;
    saveBtn.textContent = 'Saving…';
    var token = toolbar ? (toolbar.dataset.token || '') : '';
    // token is on the main toolbar if present; fall back to the global form token if set
    if (!token && typeof mainToken !== 'undefined') token = mainToken;

    var delta = JSON.stringify(activeQuill.getContents());
    var body = new URLSearchParams();
    body.append('action', 'saveWidgetContent');
    body.append('token', token);
    body.append('uniqueId', quillUniqueId);
    body.append('delta', delta);

    fetch(window.location.pathname, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString()
    })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (data.success) {
          if (quillContentEl && data.html !== undefined) {
            quillContentEl.innerHTML = data.html;
          }
          quillDirty = false;
          saveBtn.disabled = false;
          saveBtn.textContent = 'Save Draft';
          setToolbarStatus('Draft saved');
          markHasDraft();
          // Close the editor and show updated content
          deactivateQuill(true);
        } else {
          throw new Error(data.error || 'Save failed');
        }
      })
      .catch(function (err) {
        saveBtn.disabled = false;
        saveBtn.textContent = 'Save Draft';
        setToolbarStatus('Error: ' + err.message);
      });
  }

  // ─────────────────────────────────────────────────────────────────────────

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
        // Purely informational (#258) -- never blocks or delays the save above, which already
        // succeeded. Reuse the existing transient-status convention (setStatus/#sc-editor-status)
        // rather than adding a new floating-toast component.
        if (data.a11yFindings && data.a11yFindings.length) {
          var count = data.a11yFindings.length;
          setStatus(bar, 'Draft saved — ' + count + ' accessibility issue' + (count === 1 ? '' : 's') + ' found');
          setToolbarStatus('Draft saved — ' + count + ' accessibility issue' + (count === 1 ? '' : 's') + ' found');
        } else {
          setStatus(bar, 'Saved');
          setToolbarStatus('Draft saved');
        }
        originalHtml = activeContent.innerHTML;
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
  //
  // Column/widget lookups can't assume a fixed nesting depth under their section/column:
  // layout-body-renderer.jspf only puts columns directly under the data-editor-section element
  // for grid/platform-no-margin/admin sections -- a default (no cssClass) section on a normal
  // page nests them two levels deeper (.full-container > .grid-container > .grid-x > column), so
  // a ':scope >' query silently matched zero columns there and Save Layout persisted an empty
  // section. closest() ties each candidate back to its true nearest ancestor instead.
  function buildLayoutJson() {
    var sections = [];
    document.querySelectorAll('[data-editor-section]').forEach(function (sectionEl) {
      var sIdx = parseInt(sectionEl.dataset.editorSection, 10);
      var columns = [];
      sectionEl.querySelectorAll('[data-editor-column]').forEach(function (colEl) {
        if (colEl.closest('[data-editor-section]') !== sectionEl) return;
        var parts = colEl.dataset.editorColumn.split('-');
        var cIdx = parseInt(parts[1], 10);
        var widgets = [];
        colEl.querySelectorAll('[data-editor-widget]').forEach(function (widgetEl) {
          if (widgetEl.closest('[data-editor-column]') !== colEl) return;
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
      // dragstart bubbles: a widget's dragstart also reaches its enclosing section's
      // listener. Ignore any dragstart that didn't originate on this exact element, or
      // the bubbled event overwrites dragSrcEl/dragSrcType with the wrong element/type.
      if (e.target !== el) return;
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
      // A widget's drop is persisted by indexing into its *original* column's widget list at its
      // *original* own-column index (see SaveDraftLayoutCommand.saveDraftLayout) -- that is only
      // valid for a same-column reorder. Reject cross-column drops here rather than accept a drop
      // that would error or silently attach the wrong widget.
      if (type === 'widget' && dragSrcEl.closest('[data-editor-column]') !== el.closest('[data-editor-column]')) return;
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
      // Same-column-only guard, mirrored from dragover above.
      if (type === 'widget' && dragSrcEl.closest('[data-editor-column]') !== el.closest('[data-editor-column]')) return;
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

  // ── Preview link (#419) ──────────────────────────────────────────────────

  function buildPreviewLinkButton() {
    var btn = document.createElement('button');
    btn.type = 'button';
    btn.id = 'sc-preview-link-btn';
    btn.className = 'button small hollow secondary';
    btn.textContent = 'Get Preview Link';
    if (toolbar.dataset.hasDraft !== 'true') btn.style.display = 'none';
    btn.addEventListener('click', doGeneratePreviewLink);
    var exitBtn = document.getElementById('sc-editor-exit');
    if (exitBtn) toolbar.insertBefore(btn, exitBtn);
    else toolbar.appendChild(btn);
    return btn;
  }

  function doGeneratePreviewLink() {
    var btn = document.getElementById('sc-preview-link-btn');
    btn.disabled = true;
    var originalLabel = btn.textContent;
    btn.textContent = 'Generating…';
    setToolbarStatus('Generating preview link…');

    // Preserve the page's current query string (e.g. ?collectionId=5) so the generated link shows
    // the reviewer the same content the editor was looking at, not just the bare path -- pagePath
    // alone never carries it server-side. previewToken is stripped so a caller can't smuggle in a
    // second, conflicting token value.
    var originalQuery = new URLSearchParams(window.location.search);
    originalQuery.delete('previewToken');

    var qs = new URLSearchParams();
    qs.append('action', 'generatePreviewLink');
    qs.append('token', getToken());
    qs.append('originalQuery', originalQuery.toString());

    fetch(window.location.pathname + '?' + qs.toString(), {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'}
    })
    .then(function (resp) {
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      return resp.json();
    })
    .then(function (data) {
      if (!data.success) throw new Error(data.error || 'Could not create the preview link');
      var url = window.location.origin + data.link;
      copyTextToClipboard(url).then(function () {
        setToolbarStatus('Preview link copied to clipboard (expires ' + new Date(data.expiresAt).toLocaleString() + ')');
      }, function () {
        setToolbarStatus('Preview link: ' + url);
      });
      btn.disabled = false;
      btn.textContent = originalLabel;
    })
    .catch(function (err) {
      setToolbarStatus('Could not create preview link: ' + err.message);
      btn.disabled = false;
      btn.textContent = originalLabel;
    });
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

  // ── Structural mutations (add / remove section, column, widget) ─────────────

  function mutatePage(action, params) {
    var qs = new URLSearchParams();
    qs.append('action', action);
    qs.append('token', getToken());
    if (params) {
      Object.keys(params).forEach(function (k) {
        if (params[k] !== undefined) qs.append(k, String(params[k]));
      });
    }
    return fetch(window.location.pathname + '?' + qs.toString(), {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'}
    })
    .then(function (resp) {
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      return resp.json();
    })
    .then(function (data) {
      if (!data.success) throw new Error(data.error || 'Mutation failed');
      return data;
    });
  }

  function insertMutateButtons(el, type, s, c, w) {
    var btns = document.createElement('div');
    btns.className = 'sc-mutate-btns';

    function addBtn(label, cls, onClick) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = cls;
      btn.title = label;
      btn.textContent = label;
      btn.addEventListener('click', function (e) {
        e.stopPropagation();
        onClick();
      });
      btns.appendChild(btn);
    }

    if (type === 'section') {
      addBtn('+ Section', 'sc-mutate-btn-add', function () {
        setToolbarStatus('Adding section…');
        mutatePage('addSection', {after: s}).then(function () {
          markHasDraft();
          window.location.reload();
        }).catch(function (err) { setToolbarStatus('Error: ' + err.message); });
      });
      addBtn('✕ Section', 'sc-mutate-btn-remove', function () {
        showConfirm('Remove this section and all its content?', 'Remove', true, function () {
          setToolbarStatus('Removing section…');
          mutatePage('removeSection', {s: s}).then(function () {
            markHasDraft();
            window.location.reload();
          }).catch(function (err) { setToolbarStatus('Error: ' + err.message); });
        });
      });
      // Style trigger — references its own button for popover positioning
      var styleTriggerBtn = document.createElement('button');
      styleTriggerBtn.type = 'button';
      styleTriggerBtn.className = 'sc-mutate-btn-style sc-width-trigger';
      styleTriggerBtn.title = 'Section style';
      styleTriggerBtn.textContent = '⊞ Style';
      styleTriggerBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        openWidthPicker(styleTriggerBtn, SECTION_PRESETS, el.dataset.editorSectionClass || '', function (cls) {
          closeWidthPicker();
          setToolbarStatus('Updating section style…');
          mutatePage('setSectionClass', {s: s, 'class': cls}).then(function () {
            markHasDraft();
            window.location.reload();
          }).catch(function (err) { setToolbarStatus('Error: ' + err.message); });
        });
      });
      btns.appendChild(styleTriggerBtn);
    } else if (type === 'column') {
      addBtn('+ Column', 'sc-mutate-btn-add', function () {
        setToolbarStatus('Adding column…');
        mutatePage('addColumn', {s: s, after: c}).then(function () {
          markHasDraft();
          window.location.reload();
        }).catch(function (err) { setToolbarStatus('Error: ' + err.message); });
      });
      addBtn('✕ Column', 'sc-mutate-btn-remove', function () {
        showConfirm('Remove this column and all its widgets?', 'Remove', true, function () {
          setToolbarStatus('Removing column…');
          mutatePage('removeColumn', {s: s, c: c}).then(function () {
            markHasDraft();
            window.location.reload();
          }).catch(function (err) { setToolbarStatus('Error: ' + err.message); });
        });
      });
      // Width trigger — references its own button for popover positioning
      var widthTriggerBtn = document.createElement('button');
      widthTriggerBtn.type = 'button';
      widthTriggerBtn.className = 'sc-mutate-btn-width sc-width-trigger';
      widthTriggerBtn.title = 'Column width';
      widthTriggerBtn.textContent = '⇔ Width';
      widthTriggerBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        openWidthPicker(widthTriggerBtn, COL_PRESETS, el.className, function (cls) {
          closeWidthPicker();
          setToolbarStatus('Updating column width…');
          mutatePage('setColumnClass', {s: s, c: c, 'class': cls}).then(function () {
            markHasDraft();
            window.location.reload();
          }).catch(function (err) { setToolbarStatus('Error: ' + err.message); });
        });
      });
      btns.appendChild(widthTriggerBtn);
      // Add-widget trigger for this column (appends after last existing widget)
      var addWidgetColBtn = document.createElement('button');
      addWidgetColBtn.type = 'button';
      addWidgetColBtn.className = 'sc-mutate-btn-add-widget sc-widget-trigger';
      addWidgetColBtn.title = 'Add widget to column';
      addWidgetColBtn.textContent = '+ Widget';
      addWidgetColBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        var widgetEls = el.querySelectorAll(':scope > [data-editor-widget]');
        openWidgetPicker(addWidgetColBtn, s, c, widgetEls.length - 1);
      });
      btns.appendChild(addWidgetColBtn);
    } else if (type === 'widget') {
      // Prefs trigger — references its own button for panel positioning
      var prefsTriggerBtn = document.createElement('button');
      prefsTriggerBtn.type = 'button';
      prefsTriggerBtn.className = 'sc-mutate-btn-prefs sc-prefs-trigger';
      prefsTriggerBtn.title = 'Widget preferences';
      prefsTriggerBtn.textContent = '⚙ Prefs';
      prefsTriggerBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        openPrefsPanel(prefsTriggerBtn, s, c, w, el.dataset.editorWidgetPrefs || '{}');
      });
      btns.appendChild(prefsTriggerBtn);
      // Image trigger — arms this widget as the target for the next Media Library file click.
      // Only rendered for an actual "image" widget (see IMAGE_WIDGET_TYPE below): that widget's
      // one editable image preference is its render source, so there is nothing to guess here.
      if (el.dataset.editorWidgetName === IMAGE_WIDGET_TYPE) {
        var imageTriggerBtn = document.createElement('button');
        imageTriggerBtn.type = 'button';
        imageTriggerBtn.className = 'sc-mutate-btn-image sc-image-trigger';
        imageTriggerBtn.title = 'Choose image from Media Library';
        imageTriggerBtn.textContent = '🖼 Image';
        imageTriggerBtn.addEventListener('click', function (e) {
          e.stopPropagation();
          if (activeImageWidget && activeImageWidget.el === el) {
            disarmImageWidget();
            setToolbarStatus('');
            return;
          }
          armImageWidget(el, s, c, w, IMAGE_WIDGET_PREF_KEY);
        });
        btns.appendChild(imageTriggerBtn);
      }
      // Add-widget-after trigger for this widget position
      var addWidgetAfterBtn = document.createElement('button');
      addWidgetAfterBtn.type = 'button';
      addWidgetAfterBtn.className = 'sc-mutate-btn-add-widget sc-widget-trigger';
      addWidgetAfterBtn.title = 'Add widget after this one';
      addWidgetAfterBtn.textContent = '+ Widget';
      addWidgetAfterBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        openWidgetPicker(addWidgetAfterBtn, s, c, w);
      });
      btns.appendChild(addWidgetAfterBtn);
      addBtn('✕ Widget', 'sc-mutate-btn-remove', function () {
        showConfirm('Remove this widget?', 'Remove', true, function () {
          setToolbarStatus('Removing widget…');
          mutatePage('removeWidget', {s: s, c: c, w: w}).then(function () {
            markHasDraft();
            window.location.reload();
          }).catch(function (err) { setToolbarStatus('Error: ' + err.message); });
        });
      });
    }

    el.appendChild(btns);
  }

  // ── Class picker popover (shared by column width + section style) ─────────────

  var COL_PRESETS = [
    {label: '1/1', value: 'small-12 cell'},
    {label: '1/2', value: 'small-12 medium-6 cell'},
    {label: '2/3', value: 'small-12 medium-8 cell'},
    {label: '1/3', value: 'small-12 medium-4 cell'},
    {label: '3/4', value: 'small-12 large-9 cell'},
    {label: '1/4', value: 'small-12 large-3 cell'},
  ];

  var SECTION_PRESETS = [
    {label: 'Default',   value: ''},
    {label: 'Centered',  value: 'align-center'},
    {label: 'Compact',   value: 'grid-x grid-padding-x'},
    {label: 'Cmpct Ctr', value: 'grid-x grid-padding-x align-center'},
    {label: 'Margins',   value: 'grid-x grid-margin-x'},
    {label: 'No Margin', value: 'platform-no-margin'},
  ];

  function buildWidthPicker() {
    var picker = document.createElement('div');
    picker.id = 'sc-width-picker';
    picker.setAttribute('role', 'dialog');
    picker.setAttribute('aria-label', 'Class picker');
    picker.style.display = 'none';

    var grid = document.createElement('div');
    grid.id = 'sc-width-presets';
    picker.appendChild(grid);

    var customRow = document.createElement('div');
    customRow.id = 'sc-width-custom-row';
    var input = document.createElement('input');
    input.type = 'text';
    input.id = 'sc-width-custom-input';
    input.placeholder = 'Custom classes…';
    input.setAttribute('aria-label', 'Custom class');
    var applyBtn = document.createElement('button');
    applyBtn.type = 'button';
    applyBtn.id = 'sc-width-custom-apply';
    applyBtn.textContent = 'Apply';
    applyBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      if (classPickerApplyFn) classPickerApplyFn(input.value.trim());
    });
    input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') { e.stopPropagation(); if (classPickerApplyFn) classPickerApplyFn(input.value.trim()); }
      if (e.key === 'Escape') closeWidthPicker();
    });
    customRow.appendChild(input);
    customRow.appendChild(applyBtn);
    picker.appendChild(customRow);

    document.body.appendChild(picker);
    return picker;
  }

  function openWidthPicker(triggerEl, presets, currentClass, applyFn) {
    classPickerApplyFn = applyFn;

    // Rebuild preset buttons for this invocation's preset list
    var grid = document.getElementById('sc-width-presets');
    grid.innerHTML = '';
    presets.forEach(function (preset) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'sc-width-preset-btn';
      btn.dataset.classValue = preset.value;
      btn.title = preset.value || '(default)';
      btn.textContent = preset.label;
      btn.addEventListener('click', function (e) {
        e.stopPropagation();
        if (classPickerApplyFn) classPickerApplyFn(preset.value);
      });
      grid.appendChild(btn);
    });

    var input = document.getElementById('sc-width-custom-input');
    if (input) input.value = currentClass || '';

    document.querySelectorAll('.sc-width-preset-btn').forEach(function (btn) {
      btn.classList.toggle('sc-active', btn.dataset.classValue === currentClass);
    });

    widthPicker.style.display = 'block';

    // Position below the trigger, clamped to viewport
    var rect = triggerEl.getBoundingClientRect();
    var pw = 216;
    var left = window.scrollX + rect.left;
    if (left + pw > window.innerWidth - 8) left = window.innerWidth - pw - 8;
    widthPicker.style.top = (window.scrollY + rect.bottom + 4) + 'px';
    widthPicker.style.left = Math.max(8, left) + 'px';
  }

  function closeWidthPicker() {
    if (widthPicker) widthPicker.style.display = 'none';
    classPickerApplyFn = null;
  }

  // ── Widget preferences panel ──────────────────────────────────────────────

  function buildPrefsPanel() {
    var panel = document.createElement('div');
    panel.id = 'sc-prefs-panel';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-label', 'Widget preferences');
    panel.style.display = 'none';

    var header = document.createElement('div');
    header.id = 'sc-prefs-header';
    var title = document.createElement('span');
    title.textContent = 'Widget Prefs';
    var closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.id = 'sc-prefs-close';
    closeBtn.textContent = '×';
    closeBtn.setAttribute('aria-label', 'Close');
    closeBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      closePrefsPanel();
    });
    header.appendChild(title);
    header.appendChild(closeBtn);
    panel.appendChild(header);

    var textarea = document.createElement('textarea');
    textarea.id = 'sc-prefs-textarea';
    textarea.setAttribute('aria-label', 'Preferences JSON');
    textarea.setAttribute('spellcheck', 'false');
    textarea.setAttribute('autocomplete', 'off');
    panel.appendChild(textarea);

    var error = document.createElement('div');
    error.id = 'sc-prefs-error';
    error.setAttribute('aria-live', 'polite');
    panel.appendChild(error);

    var footer = document.createElement('div');
    footer.id = 'sc-prefs-footer';
    var resetBtn = document.createElement('button');
    resetBtn.type = 'button';
    resetBtn.id = 'sc-prefs-reset';
    resetBtn.textContent = 'Reset';
    resetBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      if (prefsPanelTarget) textarea.value = prefsPanelTarget.original;
      error.textContent = '';
    });
    var applyBtn = document.createElement('button');
    applyBtn.type = 'button';
    applyBtn.id = 'sc-prefs-apply';
    applyBtn.textContent = 'Apply';
    applyBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      applyPrefs();
    });
    footer.appendChild(resetBtn);
    footer.appendChild(applyBtn);
    panel.appendChild(footer);

    panel.addEventListener('click', function (e) { e.stopPropagation(); });
    document.body.appendChild(panel);
    return panel;
  }

  function openPrefsPanel(triggerEl, s, c, w, currentPrefsJson) {
    prefsPanelTarget = {s: s, c: c, w: w, original: currentPrefsJson};

    var textarea = document.getElementById('sc-prefs-textarea');
    var error = document.getElementById('sc-prefs-error');
    try {
      textarea.value = JSON.stringify(JSON.parse(currentPrefsJson), null, 2);
    } catch (e) {
      textarea.value = currentPrefsJson;
    }
    if (error) error.textContent = '';

    prefsPanel.style.display = 'flex';

    var rect = triggerEl.getBoundingClientRect();
    var pw = 300;
    var left = window.scrollX + rect.left;
    if (left + pw > window.innerWidth - 8) left = window.innerWidth - pw - 8;
    prefsPanel.style.top = (window.scrollY + rect.bottom + 4) + 'px';
    prefsPanel.style.left = Math.max(8, left) + 'px';

    textarea.focus();
  }

  function closePrefsPanel() {
    if (prefsPanel) prefsPanel.style.display = 'none';
    prefsPanelTarget = null;
  }

  function applyPrefs() {
    if (!prefsPanelTarget) return;
    var textarea = document.getElementById('sc-prefs-textarea');
    var error = document.getElementById('sc-prefs-error');
    var raw = textarea.value.trim();
    if (!raw) { if (error) error.textContent = 'Enter at least one key/value pair.'; return; }
    try {
      JSON.parse(raw);
    } catch (e) {
      if (error) error.textContent = 'Invalid JSON: ' + e.message;
      return;
    }
    var target = prefsPanelTarget;
    closePrefsPanel();
    setToolbarStatus('Saving preferences…');
    mutatePage('setWidgetPreferences', {s: target.s, c: target.c, w: target.w, prefs: raw}).then(function () {
      markHasDraft();
      window.location.reload();
    }).catch(function (err) { setToolbarStatus('Error: ' + err.message); });
  }

  // ── Active image widget (media library click-to-replace, issue #772) ────────
  //
  // The "image" widget (ImageWidget/widget-library.xml) is the one widget type in this codebase
  // whose rendered <img> is driven directly by an editable preference rather than a
  // domain-entity reference (product/item/blog post, etc.) -- so the target widget type and its
  // preference key are both known constants, not something to infer from arbitrary widgets'
  // preference JSON (the prior heuristic here false-positived on e.g. the remoteContent widget's
  // unrelated "url" preference and silently corrupted it).
  var IMAGE_WIDGET_TYPE = 'image';
  var IMAGE_WIDGET_PREF_KEY = 'imageUrl';

  function armImageWidget(el, s, c, w, prefKey) {
    if (activeImageWidget && activeImageWidget.el) {
      activeImageWidget.el.classList.remove('sc-image-armed');
    }
    activeImageWidget = {s: s, c: c, w: w, el: el, prefKey: prefKey};
    el.classList.add('sc-image-armed');
    setToolbarStatus('Choose a file in the Media Library to set this widget\'s "' + prefKey + '" image.');
    if (typeof window.showMediaLibrary === 'function') {
      window.showMediaLibrary();
    }
  }

  function disarmImageWidget() {
    if (activeImageWidget && activeImageWidget.el) {
      activeImageWidget.el.classList.remove('sc-image-armed');
    }
    activeImageWidget = null;
  }

  // Applies the selected media asset to the armed widget's preference via the already-tested
  // POST /visual-editor/media/widget-update (MediaApiController#handleWidgetUpdate), then updates
  // the widget's rendered <img> in place when one is found, falling back to this file's usual
  // reload-on-mutation pattern otherwise.
  function applyImageToWidget(target, asset) {
    if (!target.el || !document.body.contains(target.el)) {
      setToolbarStatus('That widget is no longer on the page.');
      disarmImageWidget();
      return;
    }
    var token = getToken();
    if (!token) {
      setToolbarStatus('No session token');
      return;
    }

    setToolbarStatus('Applying image…');

    var body = new URLSearchParams();
    body.append('assetId', asset.assetId);
    body.append('pagePath', pagePath);
    body.append('prefKey', target.prefKey);
    body.append('sectionIdx', String(target.s));
    body.append('columnIdx', String(target.c));
    body.append('widgetIdx', String(target.w));
    body.append('token', token);

    fetch('/visual-editor/media/widget-update', {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      body: body.toString()
    })
      .then(function (resp) {
        return resp.json().catch(function () { return {}; }).then(function (data) {
          if (!resp.ok || !data.success) {
            throw new Error((data && data.error) || ('HTTP ' + resp.status));
          }
          return data;
        });
      })
      .then(function (data) {
        // data.asset.storagePath is the internal FileSystemCommand-relative disk path, not a
        // browser URL (see mediaAssetUrl above) -- the widget's persisted imageUrl preference is
        // now the real serving route too (MediaApiController#handleWidgetUpdate), so the in-place
        // DOM update must match what was actually saved rather than reverting to the broken path.
        var newUrl = data.asset && data.asset.assetId ? mediaAssetUrl(data.asset) : null;
        var updatedInPlace = false;
        if (newUrl && target.el && document.body.contains(target.el)) {
          var img = target.el.querySelector('img');
          if (img) {
            img.src = newUrl;
            updatedInPlace = true;
          }
          // Keep the cached prefs JSON in sync so a subsequent openPrefsPanel call on this same
          // widget sees the value that was just applied.
          try {
            var prefs = JSON.parse(target.el.dataset.editorWidgetPrefs || '{}');
            prefs[target.prefKey] = newUrl;
            target.el.dataset.editorWidgetPrefs = JSON.stringify(prefs);
          } catch (ex) { /* ignore -- cosmetic cache only */ }
        }
        markHasDraft();
        disarmImageWidget();
        if (updatedInPlace) {
          setToolbarStatus('Image updated');
        } else {
          setToolbarStatus('Image updated — reloading…');
          window.location.reload();
        }
      })
      .catch(function (err) {
        setToolbarStatus('Error: ' + err.message);
      });
  }

  // Builds the same public, browser-servable URL the media panel's own thumbnails use
  // (GET /visual-editor/media/file/{assetId} -- storagePath is an internal disk-relative path,
  // not a URL, per MediaApiController's handleServeFile), absolutized so it's meaningful once
  // pasted somewhere other than this page.
  function mediaAssetUrl(asset) {
    return window.location.origin + '/visual-editor/media/file/' + encodeURIComponent(asset.assetId);
  }

  // Copies text to the clipboard, following the same async-clipboard-with-execCommand-fallback
  // pattern already established by mfa-qrcode.js's copyToClipboard (that file is scoped to the MFA
  // settings page and not loaded here, so the technique is replicated rather than shared).
  function copyTextToClipboard(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text);
    }
    return new Promise(function (resolve, reject) {
      var textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      try {
        if (document.execCommand('copy')) {
          resolve();
        } else {
          reject(new Error('execCommand returned false'));
        }
      } catch (e) {
        reject(e);
      } finally {
        document.body.removeChild(textarea);
      }
    });
  }

  // No-selection click path (#772/#431): with no widget armed, clicking a file is not a replace
  // action -- copy its URL and confirm via this editor's existing transient-status convention
  // (the same aria-live #sc-editor-status element applyImageToWidget already uses above for
  // "Image updated" etc.), rather than introducing a new floating-toast component.
  function copyAssetUrlToClipboard(asset) {
    var url = mediaAssetUrl(asset);
    copyTextToClipboard(url).then(function () {
      setToolbarStatus('Copied image URL to clipboard');
    }, function () {
      setToolbarStatus('Could not copy URL — ' + url);
    });
  }

  // The media library panel dispatches this on every file click, whether or not a widget is
  // currently armed. With a widget armed this is a replace action; with nothing armed, clicking a
  // file copies its URL instead (issue #772/#431's "no context" behavior).
  document.addEventListener('media-selected', function (e) {
    var asset = e.detail && e.detail.asset;
    if (!asset || !asset.assetId) {
      setToolbarStatus('Selected file is missing an asset id.');
      return;
    }
    if (!activeImageWidget) {
      copyAssetUrlToClipboard(asset);
      return;
    }
    applyImageToWidget(activeImageWidget, asset);
  });

  // The panel dispatches this from hideMediaLibrary() whenever it closes -- via its own close
  // button, the toolbar's open/close toggle, or Escape -- not only on a successful file pick.
  // Without this, closing the panel without choosing a file left activeImageWidget armed, so the
  // *next* time the panel was opened for any unrelated reason, the next file clicked would silently
  // overwrite that stale widget's image (issue #772 review finding). applyImageToWidget's own
  // success path already disarms before the panel would typically close, so this is a no-op then.
  document.addEventListener('media-library-closed', function () {
    if (activeImageWidget) {
      disarmImageWidget();
      setToolbarStatus('');
    }
  });

  // ── Widget picker ─────────────────────────────────────────────────────────

  function buildWidgetPicker() {
    var p = document.createElement('div');
    p.id = 'sc-widget-picker';
    p.style.display = 'none';
    p.innerHTML =
      '<div class="sc-picker-header"><span>Add Widget</span>' +
      '<button type="button" class="sc-picker-close" title="Close">&#215;</button></div>' +
      '<input type="text" id="sc-widget-search" placeholder="Search widget…" autocomplete="off">' +
      '<ul id="sc-widget-list"></ul>';
    document.body.appendChild(p);

    p.querySelector('.sc-picker-close').addEventListener('click', function () {
      closeWidgetPicker();
    });

    var searchInput = p.querySelector('#sc-widget-search');
    searchInput.addEventListener('input', function () {
      filterWidgetList(searchInput.value);
    });
    searchInput.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') closeWidgetPicker();
    });

    return p;
  }

  function filterWidgetList(query) {
    if (!widgetPicker) return;
    var list = widgetPicker.querySelector('#sc-widget-list');
    if (!list) return;
    var q = query.trim().toLowerCase();
    list.querySelectorAll('li').forEach(function (li) {
      li.style.display = (!q || li.dataset.name.toLowerCase().indexOf(q) !== -1) ? '' : 'none';
    });
  }

  function openWidgetPicker(triggerEl, s, c, after) {
    if (!widgetPicker) return;
    widgetPickerTarget = {s: s, c: c, after: after};

    var list = widgetPicker.querySelector('#sc-widget-list');
    list.innerHTML = '';
    widgetNames.forEach(function (name) {
      var li = document.createElement('li');
      li.dataset.name = name;
      li.textContent = name;
      li.addEventListener('click', function () {
        var target = widgetPickerTarget;
        closeWidgetPicker();
        setToolbarStatus('Adding widget…');
        mutatePage('addWidget', {s: target.s, c: target.c, after: target.after, widgetName: name}).then(function () {
          markHasDraft();
          window.location.reload();
        }).catch(function (err) { setToolbarStatus('Error: ' + err.message); });
      });
      list.appendChild(li);
    });

    var searchInput = widgetPicker.querySelector('#sc-widget-search');
    searchInput.value = '';
    filterWidgetList('');

    var rect = triggerEl.getBoundingClientRect();
    var pw = 220;
    var left = Math.min(rect.left, window.innerWidth - pw - 8);
    widgetPicker.style.left = Math.max(8, left) + 'px';
    widgetPicker.style.top = (rect.bottom + 4) + 'px';
    widgetPicker.style.display = 'flex';

    setTimeout(function () { searchInput.focus(); }, 0);
  }

  function closeWidgetPicker() {
    if (widgetPicker) widgetPicker.style.display = 'none';
    widgetPickerTarget = null;
  }

  // ── Bootstrap ─────────────────────────────────────────────────────────────

  inlineToolbar = buildInlineToolbar();
  linkPrompt = buildLinkPrompt();
  buildConfirmModal();
  widthPicker = buildWidthPicker();
  prefsPanel = buildPrefsPanel();
  widgetPicker = buildWidgetPicker();
  widgetNames = JSON.parse((toolbar && toolbar.dataset.widgetNames) || '[]');

  // Close width picker on outside click
  document.addEventListener('click', function (e) {
    if (widthPicker && widthPicker.style.display !== 'none') {
      if (!widthPicker.contains(e.target) && !e.target.closest('.sc-width-trigger')) {
        closeWidthPicker();
      }
    }
    if (prefsPanel && prefsPanel.style.display !== 'none') {
      if (!prefsPanel.contains(e.target) && !e.target.closest('.sc-prefs-trigger')) {
        closePrefsPanel();
      }
    }
    if (widgetPicker && widgetPicker.style.display !== 'none') {
      if (!widgetPicker.contains(e.target) && !e.target.closest('.sc-widget-trigger')) {
        closeWidgetPicker();
      }
    }
  });

  if (layoutMode) {
    buildSaveLayoutButton();      // inserts before Exit
    buildPreviewLinkButton();     // inserts before Exit (right of Save Layout)
    buildPublishButton();         // inserts before Exit (right of Preview Link)
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
      insertMutateButtons(el, 'section', idx, -1, -1);
    }
  });

  // Columns
  document.querySelectorAll('[data-editor-column]').forEach(function (el) {
    var parts = el.dataset.editorColumn.split('-');
    var colSectionIdx = parseInt(parts[0], 10);
    var colIdx = parseInt(parts[1], 10);
    insertHandle(el, 'column', 'Col ' + (colIdx + 1));
    if (layoutMode) {
      insertMutateButtons(el, 'column', colSectionIdx, colIdx, -1);
    }
  });

  // Widgets
  document.querySelectorAll('[data-editor-widget]').forEach(function (el) {
    var wParts = el.dataset.editorWidget.split('-');
    var wSIdx = parseInt(wParts[0], 10);
    var wCIdx = parseInt(wParts[1], 10);
    var wIdx = parseInt(wParts[2], 10);
    var isContentWidget = !!el.querySelector('[data-content-unique-id]');
    var href = isContentWidget ? null : (ctx + '/admin/web-page-designer?webPage=' + encodeURIComponent(pagePath));
    insertHandle(el, 'widget', isContentWidget ? 'Content' : 'Widget', href);
    if (layoutMode) {
      insertDragHandle(el, 'widget');
      insertMoveButtons(el, 'widget');
      makeDraggable(el, 'widget');
      insertMutateButtons(el, 'widget', wSIdx, wCIdx, wIdx);
      // In layout mode, mark content widgets that have a uniqueId preference for Quill activation
      var uid = getUniqueIdFromWidget(el);
      if (uid) el.dataset.editorQuillId = uid;
    }
  });

  // Double-click a content widget in layout mode → activate Quill rich text editor
  document.addEventListener('dblclick', function (e) {
    if (!layoutMode) return;
    if (e.target.closest('.sc-editor-drag-handle, .sc-move-btns, .sc-mutate-btns, .sc-editor-handle, .sc-quill-actions')) return;
    var widgetEl = e.target.closest('[data-editor-quill-id]');
    if (widgetEl && !activeQuill) {
      e.preventDefault();
      activateQuill(widgetEl, widgetEl.dataset.editorQuillId);
      return;
    }
  });

  // Click on a content block → activate inline editor (P2; not used when Quill is active)
  document.addEventListener('click', function (e) {
    if (e.target.closest('.sc-editor-drag-handle, .sc-move-btns, .sc-mutate-btns, .sc-editor-handle')) return;

    // If Quill is active, clicks outside it close it (with dirty check)
    if (activeQuill) {
      var inQuillEditor = quillHost && quillHost.contains(e.target);
      var inQuillBar = quillActionsBar && quillActionsBar.contains(e.target);
      if (!inQuillEditor && !inQuillBar) {
        if (quillDirty && !confirm('Discard unsaved changes?')) return;
        deactivateQuill(false);
      }
      return;
    }

    var contentEl = e.target.closest('.platform-content[data-content-unique-id]');
    if (contentEl) {
      // In layout mode, single-click on content is suppressed — use double-click for Quill instead
      if (layoutMode) return;
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

  // Keyboard shortcuts: Escape and Ctrl+S
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      if (widgetPicker && widgetPicker.style.display !== 'none') { closeWidgetPicker(); return; }
      if (prefsPanel && prefsPanel.style.display !== 'none') { closePrefsPanel(); return; }
      if (widthPicker && widthPicker.style.display !== 'none') { closeWidthPicker(); return; }
      if (activeQuill) {
        if (quillDirty && !confirm('Discard unsaved changes?')) return;
        deactivateQuill(false);
        return;
      }
      if (activeContent) { deactivateEdit(true); return; }
      if (activeImageWidget) { disarmImageWidget(); setToolbarStatus(''); }
    }
    // Ctrl+S (or Cmd+S) saves the active Quill editor
    if ((e.ctrlKey || e.metaKey) && e.key === 's' && activeQuill && quillActionsBar) {
      e.preventDefault();
      saveQuillDraft(quillActionsBar);
    }
  });

  // Hide floating toolbar when window scrolls (will reposition on next selection)
  window.addEventListener('scroll', hideInlineToolbar, {passive: true});
})();
