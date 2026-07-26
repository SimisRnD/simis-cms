/**
 * P4: Real-Time Preview Pane Component
 * Renders Delta JSON to HTML in real-time as user edits
 * Debounces updates to reduce re-renders
 *
 * Integrates with: overlay-editor-pane.js (P2)
 * Requires: quill.min.js, delta-renderer.js
 */

class OverlayPreviewPane {
  constructor(editorPane) {
    this.editorPane = editorPane; // Reference to P2 overlay editor
    this.previewContainer = null;
    this.renderTimeout = null;
    this.debounceMs = 300; // ms to wait before rendering
    this.maxDocSize = 102400; // 100KB — profile if larger
    this.lastRenderedDelta = null;
  }

  /**
   * Initialize preview pane
   * Call after overlay editor is created but before showing
   */
  init() {
    this.createPreviewLayout();
    this.attachEventListeners();
  }

  /**
   * Create the split-pane layout
   * Editor on left (50%), preview on right (50%)
   * Draggable divider between them
   */
  createPreviewLayout() {
    const overlay = document.querySelector('.overlay-editor-container');

    // Create wrapper for split layout
    const splitWrapper = document.createElement('div');
    splitWrapper.className = 'split-pane-wrapper';

    // Left: Editor pane (existing, move into wrapper)
    const editorPane = overlay.querySelector('.overlay-editor-pane');

    // Right: Preview pane (new)
    const previewPane = document.createElement('div');
    previewPane.className = 'split-pane-preview';
    previewPane.innerHTML = `
      <div class="preview-header">
        <div class="preview-title">Preview</div>
        <div class="preview-controls">
          <button class="preview-fullscreen-btn" title="Fullscreen preview">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
              <path d="M2 2v4h1V3h3V2H2zm9 0v1h3v3h1V2h-4zm-9 9v1h4v1H2v-2zm11 0v2h-4v-1h3v-1h1z"/>
            </svg>
          </button>
        </div>
      </div>
      <div class="preview-content"></div>
    `;

    // Resizable divider
    const divider = document.createElement('div');
    divider.className = 'split-pane-divider';
    divider.innerHTML = '<div class="divider-handle"></div>';

    // Arrange: Editor | Divider | Preview
    splitWrapper.appendChild(editorPane);
    splitWrapper.appendChild(divider);
    splitWrapper.appendChild(previewPane);

    // Replace overlay content with split layout
    overlay.querySelector('.overlay-editor-content').replaceWith(splitWrapper);

    this.previewContainer = previewPane.querySelector('.preview-content');
    this.fullscreenBtn = previewPane.querySelector('.preview-fullscreen-btn');

    this.makeResizable(divider);
    this.attachFullscreenListener();

    // Initial render
    this.render();
  }

  /**
   * Make divider draggable (resizes left/right panes)
   */
  makeResizable(divider) {
    let isResizing = false;

    divider.addEventListener('mousedown', () => {
      isResizing = true;
      divider.classList.add('resizing');
    });

    document.addEventListener('mousemove', (e) => {
      if (!isResizing) return;

      const splitWrapper = divider.parentElement;
      const editorPane = splitWrapper.querySelector('.overlay-editor-pane');
      const previewPane = splitWrapper.querySelector('.split-pane-preview');
      const wrapperWidth = splitWrapper.offsetWidth;
      const newLeftWidth = Math.max(200, Math.min(e.clientX - splitWrapper.getBoundingClientRect().left, wrapperWidth - 200));

      editorPane.style.width = newLeftWidth + 'px';
      previewPane.style.width = (wrapperWidth - newLeftWidth - 4) + 'px'; // 4px = divider width
    });

    document.addEventListener('mouseup', () => {
      isResizing = false;
      divider.classList.remove('resizing');
    });
  }

  /**
   * Attach event listeners
   * - Listen to Quill text-change events
   * - Debounce renders
   */
  attachEventListeners() {
    const quill = this.editorPane.quill;

    if (!quill) {
      console.warn('P4 Preview: Quill editor not found');
      return;
    }

    // Listen to text-change event
    quill.on('text-change', () => {
      this.debounceRender();
    });
  }

  /**
   * Debounce render: wait 300ms after last keystroke before rendering
   * Prevents excessive re-renders on fast typing
   */
  debounceRender() {
    clearTimeout(this.renderTimeout);
    this.renderTimeout = setTimeout(() => {
      this.render();
    }, this.debounceMs);
  }

  /**
   * Render current editor content to preview
   * Extracts Delta from Quill, converts to HTML, updates preview pane
   */
  render() {
    const quill = this.editorPane.quill;
    if (!quill) return;

    const delta = quill.getContents();

    // Skip if delta hasn't changed (optimization)
    if (this.deltaEquals(delta, this.lastRenderedDelta)) {
      return;
    }

    this.lastRenderedDelta = JSON.parse(JSON.stringify(delta)); // Deep copy

    const startTime = performance.now();

    try {
      // Convert Delta to HTML using DeltaRenderer
      const html = DeltaRenderer.render(delta);

      // Update preview pane
      this.previewContainer.innerHTML = html;

      const endTime = performance.now();
      const renderTime = endTime - startTime;

      // Log performance (remove in production)
      if (renderTime > 100) {
        console.warn(`P4 Preview: Slow render (${renderTime.toFixed(2)}ms)`, delta);
      }
    } catch (err) {
      console.error('P4 Preview: Render error', err);
      this.previewContainer.innerHTML = `<div class="preview-error">Error rendering preview</div>`;
    }
  }

  /**
   * Fullscreen preview mode
   * Hide editor, show preview at full width
   * ESC or button click to exit
   */
  attachFullscreenListener() {
    this.fullscreenBtn.addEventListener('click', () => {
      const splitWrapper = this.previewContainer.closest('.split-pane-wrapper');
      splitWrapper.classList.toggle('fullscreen-preview');
      this.fullscreenBtn.classList.toggle('active');
    });

    // ESC to exit fullscreen
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        const splitWrapper = this.previewContainer.closest('.split-pane-wrapper');
        if (splitWrapper && splitWrapper.classList.contains('fullscreen-preview')) {
          splitWrapper.classList.remove('fullscreen-preview');
          this.fullscreenBtn.classList.remove('active');
        }
      }
    });
  }

  /**
   * Helper: Check if two Deltas are equal
   * Avoids re-rendering identical content
   */
  deltaEquals(delta1, delta2) {
    if (!delta1 || !delta2) return false;
    return JSON.stringify(delta1) === JSON.stringify(delta2);
  }

  /**
   * Cleanup (call when overlay closes)
   */
  destroy() {
    clearTimeout(this.renderTimeout);
    this.previewContainer = null;
  }
}

// Export for use in overlay-editor-pane.js
if (typeof module !== 'undefined' && module.exports) {
  module.exports = OverlayPreviewPane;
}
