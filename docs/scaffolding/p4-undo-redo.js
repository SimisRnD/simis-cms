/**
 * P4: Undo/Redo Integration
 * Integrates Quill's built-in history module
 * Provides UI buttons and keyboard shortcuts
 *
 * Requires: Quill with history module
 */

class UndoRedoManager {
  constructor(quill, overlayEditorPane) {
    this.quill = quill;
    this.overlayEditorPane = overlayEditorPane;
    this.undoBtn = null;
    this.redoBtn = null;
    this.maxHistory = 50; // Quill default

    // Enable Quill history module if not already enabled
    this.quill.history.maxStack = this.maxHistory;
  }

  /**
   * Initialize undo/redo UI
   * Add buttons to toolbar and attach listeners
   */
  init() {
    // Find toolbar or create one
    const toolbar = this.overlayEditorPane.overlay.querySelector('.ql-toolbar') ||
                   this.createToolbar();

    // Add undo/redo buttons to toolbar
    this.addButtons(toolbar);
    this.attachListeners();
    this.updateButtonStates();
  }

  /**
   * Add Undo/Redo buttons to toolbar
   */
  addButtons(toolbar) {
    // Create button group
    const btnGroup = document.createElement('div');
    btnGroup.className = 'ql-formats';

    // Undo button
    this.undoBtn = document.createElement('button');
    this.undoBtn.className = 'ql-undo';
    this.undoBtn.title = 'Undo (Ctrl+Z)';
    this.undoBtn.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
        <path d="M2 8a6 6 0 016-6 5.98 5.98 0 014.24 1.76l1.41-1.41A8 8 0 008 0C4 0 1 2.24 0 5.3V2H-2v6h6V6H2v2zm12 0a6 6 0 01-6 6 5.98 5.98 0 01-4.24-1.76l-1.41 1.41A8 8 0 008 16c4 0 7-2.24 8-5.3V14h2v-6h-6v2h4v-2z"/>
      </svg>
    `;

    // Redo button
    this.redoBtn = document.createElement('button');
    this.redoBtn.className = 'ql-redo';
    this.redoBtn.title = 'Redo (Ctrl+Shift+Z)';
    this.redoBtn.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
        <path d="M14 8a6 6 0 01-6 6 5.98 5.98 0 01-4.24-1.76l-1.41 1.41A8 8 0 008 16c4 0 7-2.24 8-5.3V14h2v-6h-6v2h4v-2zM2 8a6 6 0 016-6 5.98 5.98 0 014.24 1.76l1.41-1.41A8 8 0 008 0C4 0 1 2.24 0 5.3V2H-2v6h6V6H2v2z"/>
      </svg>
    `;

    btnGroup.appendChild(this.undoBtn);
    btnGroup.appendChild(this.redoBtn);
    toolbar.appendChild(btnGroup);

    // Attach click handlers
    this.undoBtn.addEventListener('click', (e) => {
      e.preventDefault();
      this.undo();
    });

    this.redoBtn.addEventListener('click', (e) => {
      e.preventDefault();
      this.redo();
    });
  }

  /**
   * Attach event listeners
   * - Keyboard shortcuts (Ctrl+Z, Ctrl+Shift+Z)
   * - Editor change events (update button states)
   */
  attachListeners() {
    document.addEventListener('keydown', (e) => {
      // Ctrl+Z or Cmd+Z
      if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
        e.preventDefault();
        this.undo();
      }

      // Ctrl+Shift+Z or Cmd+Shift+Z (or Ctrl+Y on some systems)
      if ((e.ctrlKey || e.metaKey) && (e.key === 'z' || e.key === 'y') && e.shiftKey) {
        e.preventDefault();
        this.redo();
      }
    });

    // Update button states when editor changes
    this.quill.on('text-change', () => {
      this.updateButtonStates();
    });

    // Update states when selection changes
    this.quill.on('selection-change', () => {
      this.updateButtonStates();
    });
  }

  /**
   * Perform undo
   */
  undo() {
    this.quill.history.undo();
    this.updateButtonStates();
  }

  /**
   * Perform redo
   */
  redo() {
    this.quill.history.redo();
    this.updateButtonStates();
  }

  /**
   * Update button disabled states
   * - Disable undo if stack is empty
   * - Disable redo if redo stack is empty
   */
  updateButtonStates() {
    const canUndo = this.quill.history.stack.undo.length > 0;
    const canRedo = this.quill.history.stack.redo.length > 0;

    if (this.undoBtn) {
      this.undoBtn.disabled = !canUndo;
      this.undoBtn.style.opacity = canUndo ? '1' : '0.5';
      this.undoBtn.style.cursor = canUndo ? 'pointer' : 'not-allowed';
    }

    if (this.redoBtn) {
      this.redoBtn.disabled = !canRedo;
      this.redoBtn.style.opacity = canRedo ? '1' : '0.5';
      this.redoBtn.style.cursor = canRedo ? 'pointer' : 'not-allowed';
    }
  }

  /**
   * Cleanup
   */
  destroy() {
    this.undoBtn = null;
    this.redoBtn = null;
  }
}

// Export for use
if (typeof module !== 'undefined' && module.exports) {
  module.exports = UndoRedoManager;
}
