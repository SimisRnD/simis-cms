/**
 * OverlayEditorPane — In-place content editing with Quill 2.x
 *
 * Enables editors to click on page content and edit inline without leaving the page.
 *
 * Features:
 * - Click to activate overlay on content regions (data-editor-content="unique-id")
 * - Quill 2.x rich text editor with toolbar
 * - Save Draft / Discard buttons with status feedback
 * - Dirty state tracking (unsaved changes indicator)
 * - Error handling and retry logic
 * - Keyboard shortcuts: Escape to close, Ctrl+S to save
 * - Accessibility: aria-live status, keyboard navigation, focus management
 *
 * @author claude
 * @created 7/26/26
 */

class OverlayEditorPane {
  constructor(options = {}) {
    this.options = {
      containerSelector: '.platform-content-container',
      editorSelector: 'textarea.overlay-editor',
      toolbarSelector: '.overlay-toolbar',
      actionBarSelector: '.overlay-action-bar',
      statusSelector: '.overlay-status',
      csrfTokenSelector: 'input[name="formToken"]',
      ...options
    };

    this.state = {
      active: false,
      dirty: false,
      saving: false,
      currentContentId: null,
      originalHtml: null,
      quill: null
    };

    this.init();
  }

  /**
   * Initialize overlay editor on page load.
   * Set up event listeners for content region activation.
   */
  init() {
    // Ensure Quill is available
    if (typeof Quill === 'undefined') {
      console.warn('OverlayEditorPane: Quill library not loaded');
      return;
    }

    // Attach click handlers to all editable content regions
    this.attachEditorListeners();

    // Attach keyboard shortcuts
    document.addEventListener('keydown', (e) => this.handleKeyboard(e));

    console.log('OverlayEditorPane initialized');
  }

  /**
   * Attach click and hover listeners to all content regions marked as editable.
   */
  attachEditorListeners() {
    document.querySelectorAll('[data-editor-content]').forEach((region) => {
      // Add hover affordance styling
      region.classList.add('overlay-content-editable');

      // Activate editor on click
      region.addEventListener('click', (e) => {
        // Don't activate if clicking on a link or button
        if (e.target.tagName === 'A' || e.target.tagName === 'BUTTON') {
          return;
        }
        this.activate(region);
        e.stopPropagation();
      });
    });
  }

  /**
   * Activate editor for a specific content region.
   * Fetch content from server, initialize Quill, show overlay.
   *
   * @param {HTMLElement} region - The content region to edit
   */
  async activate(region) {
    // Prevent multiple simultaneous edits
    if (this.state.active) {
      if (this.state.dirty) {
        if (!confirm('You have unsaved changes. Discard them?')) {
          return;
        }
      }
      this.close();
    }

    const contentId = region.getAttribute('data-editor-content');
    if (!contentId) {
      console.error('OverlayEditorPane: content region missing data-editor-content');
      return;
    }

    this.state.active = true;
    this.state.currentContentId = contentId;
    this.state.originalHtml = region.innerHTML;
    this.state.dirty = false;
    this.state.saving = false;

    // Show overlay UI
    this.showOverlay(region);

    // Fetch content from server
    try {
      const params = new URLSearchParams();
      params.append('action', 'getWidgetContent');
      params.append('uniqueId', contentId);

      const response = await fetch(`?${params.toString()}`, {
        method: 'GET',
        headers: { 'Accept': 'application/json' }
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      if (!data.success) {
        throw new Error(data.error || 'Failed to fetch content');
      }

      // Initialize Quill with fetched Delta
      this.initializeQuill(data.content, data.format);
      this.setStatus('Ready to edit', 'default');
    } catch (error) {
      console.error('OverlayEditorPane: Error fetching content:', error);
      this.setStatus(`Error: ${error.message}. Retry?`, 'error');
      this.close();
    }
  }

  /**
   * Show overlay UI with split-pane layout (editor + preview).
   * P4: Real-time preview with Delta renderer
   *
   * @param {HTMLElement} region - The content region being edited
   */
  showOverlay(region) {
    // Create overlay container if it doesn't exist
    let overlay = document.querySelector('.overlay-editor-pane');
    if (!overlay) {
      overlay = document.createElement('div');
      overlay.className = 'overlay-editor-pane';
      document.body.appendChild(overlay);

      overlay.insertAdjacentHTML('beforeend', `
        <div class="overlay-toolbar">
          <div class="overlay-toolbar-buttons">
            <button class="toolbar-btn" data-action="bold" title="Bold (Ctrl+B)">
              <i class="ti ti-bold"></i>
            </button>
            <button class="toolbar-btn" data-action="italic" title="Italic (Ctrl+I)">
              <i class="ti ti-italic"></i>
            </button>
            <button class="toolbar-btn" data-action="underline" title="Underline (Ctrl+U)">
              <i class="ti ti-underline"></i>
            </button>
            <div class="overlay-toolbar-separator"></div>
            <button class="toolbar-btn" data-action="link" title="Link">
              <i class="ti ti-link"></i>
            </button>
            <button class="toolbar-btn" data-action="header" title="Header">
              <i class="ti ti-heading"></i>
            </button>
            <button class="toolbar-btn" data-action="list" title="Bullet list">
              <i class="ti ti-list"></i>
            </button>
            <button class="toolbar-btn" data-action="blockquote" title="Quote">
              <i class="ti ti-quote"></i>
            </button>
            <div class="overlay-toolbar-separator"></div>
            <button class="toolbar-btn" data-action="undo" title="Undo (Ctrl+Z)">
              <i class="ti ti-arrow-back"></i>
            </button>
            <button class="toolbar-btn" data-action="redo" title="Redo (Ctrl+Shift+Z)">
              <i class="ti ti-arrow-forward"></i>
            </button>
            <div class="overlay-toolbar-separator"></div>
            <button class="toolbar-btn" data-action="fullscreen" title="Fullscreen preview">
              <i class="ti ti-fullscreen"></i>
            </button>
          </div>
        </div>

        <div class="overlay-split-pane">
          <div class="overlay-editor-pane-left">
            <div class="overlay-editor" placeholder="Edit content here..."></div>
          </div>
          <div class="overlay-split-divider"></div>
          <div class="overlay-editor-pane-right">
            <div class="overlay-preview-header">Preview</div>
            <div class="overlay-preview-content"></div>
          </div>
        </div>

        <div class="overlay-action-bar">
          <div class="overlay-status" aria-live="polite" aria-atomic="true">
            Ready to edit
          </div>
          <div class="overlay-actions">
            <button class="btn-primary" data-action="save">Save Draft</button>
            <button class="btn-secondary" data-action="discard">Discard</button>
          </div>
        </div>
      `);

      // Attach event listeners to buttons
      overlay.querySelectorAll('[data-action]').forEach((btn) => {
        btn.addEventListener('click', (e) => this.handleAction(e));
      });

      // Make divider resizable
      this.makeResizable(overlay.querySelector('.overlay-split-divider'));
    }

    overlay.classList.add('active');
  }

  /**
   * Make split divider draggable to resize panes.
   */
  makeResizable(divider) {
    let isResizing = false;

    divider.addEventListener('mousedown', () => {
      isResizing = true;
      divider.classList.add('resizing');
    });

    document.addEventListener('mousemove', (e) => {
      if (!isResizing) return;

      const paneLeft = divider.previousElementSibling;
      const paneRight = divider.nextElementSibling;
      const container = divider.parentElement;
      const containerWidth = container.offsetWidth;
      const newLeftWidth = Math.max(200, Math.min(e.clientX - container.getBoundingClientRect().left, containerWidth - 200));

      paneLeft.style.width = newLeftWidth + 'px';
      paneRight.style.width = (containerWidth - newLeftWidth - 4) + 'px'; // 4px = divider width
    });

    document.addEventListener('mouseup', () => {
      isResizing = false;
      divider.classList.remove('resizing');
    });
  }

  /**
   * Initialize Quill editor with content.
   * P4: Enable history module for undo/redo
   *
   * @param {string|object} content - Delta JSON string or parsed object
   * @param {number} format - Content format (0=HTML, 2=Delta)
   */
  initializeQuill(content, format) {
    const editorEl = document.querySelector('.overlay-editor');

    // Destroy existing Quill instance if any
    if (this.state.quill) {
      this.state.quill = null;
    }

    // Parse Delta from content
    let delta;
    if (typeof content === 'string') {
      try {
        delta = JSON.parse(content);
      } catch (e) {
        console.error('OverlayEditorPane: Failed to parse Delta JSON:', e);
        delta = { ops: [{ insert: content }] };
      }
    } else {
      delta = content;
    }

    // Initialize Quill with history module (for undo/redo)
    this.state.quill = new Quill(editorEl, {
      theme: 'snow',
      modules: {
        toolbar: false, // We handle toolbar manually
        history: { maxStack: 50, userOnly: true }
      },
      formats: ['bold', 'italic', 'underline', 'link', 'header', 'list', 'blockquote'],
      placeholder: 'Start typing...'
    });

    // Set content
    this.state.quill.setContents(delta, 'silent');

    // Track changes
    this.state.quill.on('text-change', () => {
      this.state.dirty = true;
      this.updateDirtyIndicator();
      this.updatePreview(); // P4: Re-render preview
      this.updateUndoRedoStates(); // P4: Update button states
    });

    // Update button states on selection change
    this.state.quill.on('selection-change', () => {
      this.updateUndoRedoStates();
    });

    // Focus editor
    this.state.quill.focus();
    this.updatePreview(); // Render initial preview
  }

  /**
   * Handle toolbar button clicks.
   * P4: Added undo, redo, fullscreen
   *
   * @param {Event} e - Click event
   */
  handleAction(e) {
    const btn = e.currentTarget;
    const action = btn.getAttribute('data-action');

    switch (action) {
      case 'bold':
        this.state.quill?.format('bold', !this.state.quill.getFormat().bold);
        break;
      case 'italic':
        this.state.quill?.format('italic', !this.state.quill.getFormat().italic);
        break;
      case 'underline':
        this.state.quill?.format('underline', !this.state.quill.getFormat().underline);
        break;
      case 'link':
        this.insertLink();
        break;
      case 'header':
        this.state.quill?.format('header', 2);
        break;
      case 'list':
        this.state.quill?.format('list', 'bullet');
        break;
      case 'blockquote':
        this.state.quill?.format('blockquote', true);
        break;
      case 'undo':
        this.state.quill?.history.undo();
        break;
      case 'redo':
        this.state.quill?.history.redo();
        break;
      case 'fullscreen':
        this.toggleFullscreenPreview();
        break;
      case 'save':
        this.save();
        break;
      case 'discard':
        this.discard();
        break;
    }

    this.state.quill?.focus();
  }

  /**
   * Insert a link at cursor position.
   */
  insertLink() {
    if (!this.state.quill) return;

    const url = prompt('Enter link URL:');
    if (url) {
      const range = this.state.quill.getSelection();
      if (range && range.length > 0) {
        this.state.quill.format('link', url);
      } else {
        this.state.quill.insertText(0, 'Link', { link: url });
      }
    }
  }

  /**
   * Save content to server.
   * POST /page?action=saveWidgetContent with form-encoded data.
   */
  async save() {
    if (!this.state.dirty) {
      this.setStatus('No changes to save', 'default');
      return;
    }

    if (this.state.saving) {
      return; // Prevent duplicate saves
    }

    this.state.saving = true;
    this.setSaveButtonState('saving');
    this.setStatus('Saving your changes...', 'saving');

    try {
      const delta = this.state.quill.getContents();
      const deltaJson = JSON.stringify(delta);

      const formToken = document.querySelector(this.options.csrfTokenSelector)?.value || '';

      // Build form data (servlet expects form parameters, not JSON body)
      const params = new URLSearchParams();
      params.append('action', 'saveWidgetContent');
      params.append('uniqueId', this.state.currentContentId);
      params.append('delta', deltaJson);
      params.append('token', formToken);

      const response = await fetch('?', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: params.toString()
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      if (!data.success) {
        throw new Error(data.error || 'Failed to save content');
      }

      // Success: update page, close overlay
      this.state.dirty = false;
      this.setSaveButtonState('saved');
      this.setStatus('Saved successfully ✓', 'success');

      // Update rendered HTML on page
      const region = document.querySelector(`[data-editor-content="${this.state.currentContentId}"]`);
      if (region && data.html) {
        region.innerHTML = '';
        region.insertAdjacentHTML('afterbegin', data.html);
      }

      // Close overlay after brief delay
      setTimeout(() => this.close(), 800);
    } catch (error) {
      console.error('OverlayEditorPane: Error saving content:', error);
      this.state.saving = false;
      this.setSaveButtonState('error');
      this.setStatus(`Error: ${error.message}. Retry?`, 'error');
    }
  }

  /**
   * Discard changes and close overlay without saving.
   */
  discard() {
    if (this.state.dirty) {
      if (!confirm('Discard unsaved changes?')) {
        return;
      }
    }

    this.close();
    this.setStatus('Changes discarded', 'default');
  }

  /**
   * P4: Render Delta to HTML preview.
   * Uses server-side compatible Delta renderer.
   */
  updatePreview() {
    if (!this.state.quill) return;

    const previewEl = document.querySelector('.overlay-preview-content');
    if (!previewEl) return;

    try {
      const delta = this.state.quill.getContents();
      const html = this.deltaToHtml(delta);
      previewEl.innerHTML = '';
      previewEl.insertAdjacentHTML('afterbegin', html);
    } catch (err) {
      console.error('OverlayEditorPane: Preview render error', err);
      previewEl.innerHTML = '';
      previewEl.textContent = 'Error rendering preview';
    }
  }

  /**
   * P4: Convert Quill Delta to HTML.
   * Must match server-side DeltaContentCommand.render() exactly.
   */
  deltaToHtml(delta) {
    if (!delta || !delta.ops) return '<p></p>';

    const ops = delta.ops;
    let html = '';
    let currentBlock = 'p';
    let currentBlockContent = [];

    for (let i = 0; i < ops.length; i++) {
      const op = ops[i];
      const insert = op.insert;
      const attributes = op.attributes || {};

      if (typeof insert === 'string' && insert.includes('\n')) {
        const lines = insert.split('\n');
        for (let j = 0; j < lines.length; j++) {
          const line = lines[j];

          if (j > 0) {
            html += this.emitBlock(currentBlock, currentBlockContent);
            currentBlockContent = [];
            currentBlock = 'p';
          }

          // Determine block type
          if (attributes.header) currentBlock = `h${Math.min(attributes.header, 6)}`;
          else if (attributes.list) currentBlock = 'li';
          else if (attributes.blockquote) currentBlock = 'blockquote';
          else if (attributes['code-block']) currentBlock = 'pre';
          else currentBlock = 'p';

          if (line) {
            currentBlockContent.push({ text: line, attributes });
          }
        }
      } else if (typeof insert === 'string') {
        currentBlockContent.push({ text: insert, attributes });
      }
    }

    if (currentBlockContent.length > 0 || currentBlock !== 'p') {
      html += this.emitBlock(currentBlock, currentBlockContent);
    }

    return html || '<p></p>';
  }

  /**
   * Emit a single block with inline formatting.
   */
  emitBlock(blockType, content) {
    const text = content.map(c => this.formatInline(c.text, c.attributes)).join('');

    if (blockType === 'li') {
      return `<ul><li>${text}</li></ul>`;
    } else if (blockType === 'pre') {
      const rawText = content.map(c => c.text).join('');
      return `<pre><code>${this.escapeHtml(rawText)}</code></pre>`;
    }

    return `<${blockType}>${text}</${blockType}>`;
  }

  /**
   * Apply inline formatting to text.
   */
  formatInline(text, attributes = {}) {
    let formatted = this.escapeHtml(text);

    if (attributes.code) formatted = `<code>${formatted}</code>`;
    if (attributes.bold) formatted = `<strong>${formatted}</strong>`;
    if (attributes.italic) formatted = `<em>${formatted}</em>`;
    if (attributes.underline) formatted = `<u>${formatted}</u>`;
    if (attributes.link) {
      const href = this.sanitizeUrl(attributes.link);
      formatted = `<a href="${href}">${formatted}</a>`;
    }

    return formatted;
  }

  /**
   * Escape HTML special characters.
   */
  escapeHtml(text) {
    const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return text.replace(/[&<>"']/g, c => map[c]);
  }

  /**
   * Sanitize URLs (prevent XSS).
   */
  sanitizeUrl(url) {
    if (!url) return '';
    const trimmed = url.trim().toLowerCase();
    if (trimmed.startsWith('javascript:') || trimmed.startsWith('data:')) return '#';
    return this.escapeHtml(url);
  }

  /**
   * P4: Update undo/redo button states.
   */
  updateUndoRedoStates() {
    if (!this.state.quill) return;

    const canUndo = this.state.quill.history?.stack?.undo?.length > 0;
    const canRedo = this.state.quill.history?.stack?.redo?.length > 0;

    const undoBtn = document.querySelector('[data-action="undo"]');
    const redoBtn = document.querySelector('[data-action="redo"]');

    if (undoBtn) {
      undoBtn.disabled = !canUndo;
      undoBtn.style.opacity = canUndo ? '1' : '0.5';
    }
    if (redoBtn) {
      redoBtn.disabled = !canRedo;
      redoBtn.style.opacity = canRedo ? '1' : '0.5';
    }
  }

  /**
   * P4: Toggle fullscreen preview mode.
   */
  toggleFullscreenPreview() {
    const overlay = document.querySelector('.overlay-editor-pane');
    if (!overlay) return;

    overlay.classList.toggle('fullscreen-preview');

    const fullscreenBtn = document.querySelector('[data-action="fullscreen"]');
    if (fullscreenBtn) {
      fullscreenBtn.classList.toggle('active');
    }
  }

  /**
   * Close overlay and restore page state.
   */
  close() {
    const overlay = document.querySelector('.overlay-editor-pane');
    if (overlay) {
      overlay.classList.remove('active');
    }

    // Destroy Quill
    if (this.state.quill) {
      this.state.quill = null;
    }

    this.state.active = false;
    this.state.dirty = false;
    this.state.currentContentId = null;
    this.state.originalHtml = null;
  }

  /**
   * Update Save button state.
   *
   * @param {string} state - 'default', 'saving', 'saved', 'error'
   */
  setSaveButtonState(state) {
    const btn = document.querySelector('[data-action="save"]');
    if (!btn) return;

    switch (state) {
      case 'saving':
        btn.textContent = 'Saving...';
        btn.disabled = true;
        break;
      case 'saved':
        btn.textContent = '✓ Saved';
        btn.disabled = false;
        break;
      case 'error':
        btn.textContent = 'Save Draft';
        btn.disabled = false;
        break;
      default:
        btn.textContent = 'Save Draft';
        btn.disabled = false;
    }
  }

  /**
   * Update dirty indicator.
   * Show "Unsaved changes" in status if content has been modified.
   */
  updateDirtyIndicator() {
    if (this.state.dirty) {
      this.setStatus('Unsaved changes', 'warning');
    }
  }

  /**
   * Set status message with color.
   *
   * @param {string} message - Status text
   * @param {string} type - 'default', 'warning', 'saving', 'success', 'error'
   */
  setStatus(message, type = 'default') {
    const status = document.querySelector('.overlay-status');
    if (!status) return;

    status.textContent = message;
    status.className = `overlay-status overlay-status-${type}`;
  }

  /**
   * Handle keyboard shortcuts.
   *
   * @param {KeyboardEvent} e - Keyboard event
   */
  handleKeyboard(e) {
    if (!this.state.active) return;

    // Escape to close
    if (e.key === 'Escape') {
      e.preventDefault();
      this.discard();
    }

    // Ctrl+S or Cmd+S to save
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
      e.preventDefault();
      this.save();
    }
  }
}

// Initialize on page load
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => {
    window.overlayEditor = new OverlayEditorPane();
  });
} else {
  window.overlayEditor = new OverlayEditorPane();
}
