/**
 * P5: Media Library Panel Component
 * Sidebar panel in overlay editor for browsing, uploading, and selecting media
 *
 * Features:
 * - Drag-drop upload
 * - File browser with pagination (50 per page)
 * - Search by name/tags
 * - Filter by type
 * - Click to insert media into editor
 *
 * Integrates with: overlay-editor-pane.js (P2)
 */

class MediaLibraryPanel {
  constructor(editorPane) {
    this.editorPane = editorPane;
    this.panelElement = null;
    this.currentPage = 0;
    this.pageSize = 50;
    this.currentFilter = { type: null, search: '', tags: '' };
    this.isLoading = false;
  }

  /**
   * Initialize media panel
   * Create UI and attach listeners
   */
  init() {
    this.createPanelUI();
    this.attachEventListeners();
    this.loadMedia();
  }

  /**
   * Create panel HTML structure
   */
  createPanelUI() {
    this.panelElement = document.createElement('div');
    this.panelElement.className = 'media-library-panel';
    this.panelElement.innerHTML = `
      <div class="media-panel-header">
        <h3>Media Library</h3>
        <button class="media-panel-close" title="Close media library">&times;</button>
      </div>

      <div class="media-panel-upload">
        <div class="upload-zone" id="dropZone">
          <div class="upload-icon">📁</div>
          <p>Drag files here or click to browse</p>
          <input type="file" id="mediaFileInput" multiple accept="image/*,.pdf" style="display: none;">
        </div>
        <div class="upload-progress" id="uploadProgress" style="display: none;">
          <div class="progress-bar"></div>
          <span class="progress-text">0%</span>
        </div>
      </div>

      <div class="media-panel-controls">
        <input type="text" id="mediaSearch" placeholder="Search by name or tags..." class="search-input">

        <div class="filter-group">
          <label for="typeFilter">Type:</label>
          <select id="typeFilter" class="filter-select">
            <option value="">All Files</option>
            <option value="image">Images</option>
            <option value="pdf">PDFs</option>
          </select>
        </div>

        <input type="text" id="tagsFilter" placeholder="Filter by tags..." class="filter-input">
      </div>

      <div class="media-panel-content">
        <div class="media-list" id="mediaList">
          <div class="loading">Loading media...</div>
        </div>

        <div class="media-pagination" id="mediaPagination">
          <button class="pagination-btn prev" disabled>← Previous</button>
          <span class="pagination-info">Page 1</span>
          <button class="pagination-btn next">Next →</button>
        </div>
      </div>
    `;

    // Insert panel into overlay
    const overlay = document.querySelector('.split-pane-wrapper') ||
                   document.querySelector('.overlay-editor-container');
    overlay.appendChild(this.panelElement);
  }

  /**
   * Attach event listeners
   */
  attachEventListeners() {
    const dropZone = this.panelElement.querySelector('#dropZone');
    const fileInput = this.panelElement.querySelector('#mediaFileInput');
    const searchInput = this.panelElement.querySelector('#mediaSearch');
    const typeFilter = this.panelElement.querySelector('#typeFilter');
    const tagsFilter = this.panelElement.querySelector('#tagsFilter');
    const closeBtn = this.panelElement.querySelector('.media-panel-close');
    const prevBtn = this.panelElement.querySelector('.pagination-btn.prev');
    const nextBtn = this.panelElement.querySelector('.pagination-btn.next');

    // Drag-drop
    dropZone.addEventListener('dragover', (e) => {
      e.preventDefault();
      dropZone.classList.add('dragover');
    });

    dropZone.addEventListener('dragleave', () => {
      dropZone.classList.remove('dragover');
    });

    dropZone.addEventListener('drop', (e) => {
      e.preventDefault();
      dropZone.classList.remove('dragover');
      this.handleFileUpload(e.dataTransfer.files);
    });

    // File button
    dropZone.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', (e) => this.handleFileUpload(e.target.files));

    // Search and filter
    searchInput.addEventListener('input', (e) => {
      this.currentFilter.search = e.target.value;
      this.currentPage = 0;
      this.loadMedia();
    });

    typeFilter.addEventListener('change', (e) => {
      this.currentFilter.type = e.target.value || null;
      this.currentPage = 0;
      this.loadMedia();
    });

    tagsFilter.addEventListener('input', (e) => {
      this.currentFilter.tags = e.target.value;
      this.currentPage = 0;
      this.loadMedia();
    });

    // Pagination
    prevBtn.addEventListener('click', () => {
      if (this.currentPage > 0) {
        this.currentPage--;
        this.loadMedia();
      }
    });

    nextBtn.addEventListener('click', () => {
      this.currentPage++;
      this.loadMedia();
    });

    // Close panel
    closeBtn.addEventListener('click', () => this.destroy());
  }

  /**
   * Load media from API
   */
  async loadMedia() {
    if (this.isLoading) return;

    this.isLoading = true;
    const mediaList = this.panelElement.querySelector('#mediaList');
    mediaList.innerHTML = '<div class="loading">Loading...</div>';

    try {
      // Build query string
      const params = new URLSearchParams({
        page: this.currentPage,
        size: this.pageSize,
        ...(this.currentFilter.type && { type: this.currentFilter.type }),
        ...(this.currentFilter.search && { search: this.currentFilter.search }),
        ...(this.currentFilter.tags && { tags: this.currentFilter.tags })
      });

      // Fetch from API
      const response = await fetch(`/api/media/list?${params}`);
      if (!response.ok) throw new Error(`Failed to load media: ${response.status}`);

      const data = await response.json();

      // Render media items
      this.renderMediaList(data.items);
      this.updatePagination(data.page, data.total);

    } catch (error) {
      console.error('Error loading media:', error);
      mediaList.innerHTML = `<div class="error">Error loading media: ${error.message}</div>`;
    } finally {
      this.isLoading = false;
    }
  }

  /**
   * Render media items as grid
   */
  renderMediaList(items) {
    const mediaList = this.panelElement.querySelector('#mediaList');

    if (items.length === 0) {
      mediaList.innerHTML = '<div class="empty">No media found</div>';
      return;
    }

    const html = items.map(item => `
      <div class="media-item" data-asset-id="${item.assetId}" title="${item.altText}">
        <div class="media-thumbnail">
          ${item.assetType === 'image' ? `<img src="/api/media/${item.assetId}/thumbnail" alt="${item.assetName}">` : `<div class="pdf-icon">📄</div>`}
        </div>
        <div class="media-info">
          <div class="media-name">${this.escapeHtml(item.assetName)}</div>
          <div class="media-size">${this.formatFileSize(item.fileSize)}</div>
          <div class="media-tags">${item.tags ? `Tags: ${item.tags}` : ''}</div>
        </div>
        <button class="media-insert-btn">Insert</button>
      </div>
    `).join('');

    mediaList.innerHTML = html;

    // Attach click handlers to insert buttons
    mediaList.querySelectorAll('.media-insert-btn').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const assetId = btn.closest('.media-item').dataset.assetId;
        const item = items.find(i => i.assetId === assetId);
        this.insertMedia(item);
      });
    });
  }

  /**
   * Update pagination UI
   */
  updatePagination(currentPage, totalItems) {
    const totalPages = Math.ceil(totalItems / this.pageSize);
    const paginationInfo = this.panelElement.querySelector('.pagination-info');
    const prevBtn = this.panelElement.querySelector('.pagination-btn.prev');
    const nextBtn = this.panelElement.querySelector('.pagination-btn.next');

    paginationInfo.textContent = `Page ${currentPage + 1} of ${Math.max(1, totalPages)}`;
    prevBtn.disabled = currentPage === 0;
    nextBtn.disabled = currentPage >= totalPages - 1;
  }

  /**
   * Handle file upload
   */
  async handleFileUpload(files) {
    if (files.length === 0) return;

    const uploadProgress = this.panelElement.querySelector('#uploadProgress');
    uploadProgress.style.display = 'block';

    for (let file of files) {
      try {
        // TODO: Prompt for alt text (AI suggestion?)
        const altText = prompt(`Alt text for ${file.name}:`, file.name);
        if (!altText) continue;

        // Upload via API
        const formData = new FormData();
        formData.append('file', file);
        formData.append('altText', altText);

        const response = await fetch('/api/media/upload', {
          method: 'POST',
          body: formData
        });

        if (!response.ok) {
          throw new Error(`Upload failed: ${response.status}`);
        }

        // Refresh media list
        this.loadMedia();

      } catch (error) {
        alert(`Upload failed: ${error.message}`);
      }
    }

    uploadProgress.style.display = 'none';
  }

  /**
   * Insert media into editor
   */
  insertMedia(item) {
    const quill = this.editorPane.quill;
    const selection = quill.getSelection();

    if (!selection) {
      alert('Click in the editor first to place the cursor');
      return;
    }

    // Insert based on type
    if (item.assetType === 'image') {
      // Insert image embed (Quill Embed module)
      const index = selection.index;
      quill.insertEmbed(index, 'image', {
        url: `/api/media/${item.assetId}`,
        alt: item.altText
      });
      quill.setSelection(index + 1);
    } else if (item.assetType === 'pdf') {
      // Insert link to PDF
      const index = selection.index;
      quill.insertText(index, item.assetName, { link: `/api/media/${item.assetId}` });
      quill.setSelection(index + item.assetName.length);
    }

    // Mark editor as dirty
    this.editorPane.setDirty(true);
  }

  /**
   * Helper: Format file size for display
   */
  formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 10) / 10 + ' ' + sizes[i];
  }

  /**
   * Helper: Escape HTML in text
   */
  escapeHtml(text) {
    const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return text.replace(/[&<>"']/g, c => map[c]);
  }

  /**
   * Cleanup
   */
  destroy() {
    if (this.panelElement) {
      this.panelElement.remove();
    }
  }
}

// Export for use
if (typeof module !== 'undefined' && module.exports) {
  module.exports = MediaLibraryPanel;
}
