<%--
  ~ Copyright 2026 SimIS Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<div id="sc-media-panel" class="media-library-panel" role="region" aria-label="Media library panel">
  <!-- Panel Header -->
  <div class="media-panel-header">
    <h3>Media Library</h3>
    <button class="media-panel-close" aria-label="Close media library" tabindex="0">
      <i class="fa fa-times"></i>
    </button>
  </div>

  <!-- Upload Area -->
  <div class="media-panel-upload" id="media-upload-area" role="button" tabindex="0" aria-label="Drop files to upload or click to select">
    <div class="upload-content">
      <i class="fa fa-cloud-upload"></i>
      <p>Drop files here or <span class="upload-link">click to select</span></p>
    </div>
    <input type="file" id="media-file-input" multiple style="display: none;" accept="image/*,.pdf,.svg+xml" />
  </div>

  <!-- Search -->
  <div class="media-panel-search">
    <input type="text" id="media-search-input" placeholder="Search files..." class="search-input" tabindex="0" />
    <button class="search-clear" id="media-search-clear" aria-label="Clear search" style="display: none;">
      <i class="fa fa-times"></i>
    </button>
  </div>

  <!-- File Grid -->
  <div class="media-panel-grid" id="media-grid" role="listbox">
    <div class="loading" style="display: none;">
      <p>Loading files...</p>
    </div>
    <div class="empty" style="display: none;">
      <p>No files found</p>
    </div>
    <div class="error" style="display: none;" role="alert">
      <i class="fa fa-exclamation-triangle"></i>
      <p></p>
    </div>
    <div class="files" role="presentation"></div>
  </div>

  <!-- Pagination -->
  <div class="media-panel-pagination" id="media-pagination" style="display: none;">
    <button class="prev-page" aria-label="Previous page" tabindex="0">
      <i class="fa fa-chevron-left"></i>
    </button>
    <span class="pagination-info">
      <span class="current-page">1</span> / <span class="total-pages">1</span>
    </span>
    <button class="next-page" aria-label="Next page" tabindex="0">
      <i class="fa fa-chevron-right"></i>
    </button>
  </div>
</div>

<style nonce="${cspNonce}">
  .media-library-panel {
    position: fixed;
    right: 20px;
    bottom: 80px;
    width: 360px;
    max-height: 600px;
    background: white;
    border: 1px solid #ddd;
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    display: none;
    flex-direction: column;
    z-index: 1000;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }

  /* Closed by default (above); the toolbar's Media Library button toggles this class to open it. */
  .media-library-panel.open {
    display: flex;
  }

  .media-panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    border-bottom: 1px solid #eee;
  }

  .media-panel-header h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
  }

  .media-panel-close {
    background: none;
    border: none;
    font-size: 20px;
    cursor: pointer;
    padding: 4px;
    color: #666;
  }

  .media-panel-close:hover {
    color: #000;
  }

  .media-panel-close:focus {
    outline: 2px solid #0066cc;
    outline-offset: 2px;
  }

  .media-panel-upload {
    padding: 12px;
    border: 2px dashed #ccc;
    border-radius: 6px;
    text-align: center;
    cursor: pointer;
    margin: 12px;
    transition: border-color 0.2s, background-color 0.2s;
  }

  .media-panel-upload:hover {
    border-color: #0066cc;
    background-color: #f5f9ff;
  }

  .media-panel-upload:focus-within {
    outline: 2px solid #0066cc;
    outline-offset: -2px;
  }

  .media-panel-upload.drag-over {
    border-color: #0066cc;
    background-color: #e6f2ff;
  }

  .upload-content {
    pointer-events: none;
  }

  .upload-content i {
    font-size: 28px;
    color: #0066cc;
    display: block;
    margin-bottom: 6px;
  }

  .upload-content p {
    margin: 0;
    font-size: 13px;
    color: #666;
  }

  .upload-link {
    color: #0066cc;
    font-weight: 500;
  }

  .media-panel-search {
    position: relative;
    padding: 0 12px 12px;
  }

  .search-input {
    width: 100%;
    padding: 8px 32px 8px 12px;
    border: 1px solid #ccc;
    border-radius: 4px;
    font-size: 14px;
  }

  .search-input:focus {
    outline: none;
    border-color: #0066cc;
    box-shadow: 0 0 0 2px rgba(0, 102, 204, 0.1);
  }

  .search-clear {
    position: absolute;
    right: 20px;
    top: 8px;
    background: none;
    border: none;
    cursor: pointer;
    color: #999;
    padding: 4px;
  }

  .search-clear:hover {
    color: #333;
  }

  .media-panel-grid {
    flex: 1;
    overflow-y: auto;
    padding: 12px;
  }

  .media-panel-grid .files {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .media-file-item {
    aspect-ratio: 1;
    border: 1px solid #ddd;
    border-radius: 4px;
    overflow: hidden;
    cursor: pointer;
    transition: border-color 0.2s, box-shadow 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f9f9f9;
  }

  .media-file-item:hover {
    border-color: #0066cc;
    box-shadow: 0 2px 8px rgba(0, 102, 204, 0.15);
  }

  .media-file-item:focus {
    outline: 2px solid #0066cc;
    outline-offset: -2px;
  }

  .media-file-thumbnail {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .media-file-icon {
    font-size: 24px;
    color: #999;
  }

  .media-panel-pagination {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border-top: 1px solid #eee;
    font-size: 13px;
  }

  .media-panel-pagination button {
    background: none;
    border: 1px solid #ddd;
    width: 32px;
    height: 32px;
    border-radius: 4px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #666;
  }

  .media-panel-pagination button:hover {
    border-color: #0066cc;
    color: #0066cc;
  }

  .media-panel-pagination button:focus {
    outline: 2px solid #0066cc;
    outline-offset: -2px;
  }

  .media-panel-pagination button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .loading, .empty, .error {
    text-align: center;
    padding: 32px 16px;
    color: #999;
    font-size: 14px;
  }

  .loading i {
    display: block;
    font-size: 24px;
    margin-bottom: 12px;
    animation: spin 1s linear infinite;
  }

  .error {
    color: #c0392b;
  }

  .error i {
    display: block;
    font-size: 24px;
    margin-bottom: 12px;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  @media (max-width: 600px) {
    .media-library-panel {
      width: 100%;
      height: 100%;
      max-height: none;
      bottom: 0;
      right: 0;
      border-radius: 0;
    }

    .media-panel-grid .files {
      grid-template-columns: repeat(2, 1fr);
    }
  }
</style>

<script nonce="${cspNonce}">
(function() {
  const panel = document.getElementById('sc-media-panel');
  const grid = document.getElementById('media-grid');
  const searchInput = document.getElementById('media-search-input');
  const searchClearBtn = document.getElementById('media-search-clear');
  const closeBtn = document.querySelector('.media-panel-close');
  const uploadArea = document.getElementById('media-upload-area');
  const fileInput = document.getElementById('media-file-input');
  const pagination = document.getElementById('media-pagination');
  const toggleBtn = document.getElementById('sc-editor-media-library');

  let currentPage = 0;
  const pageSize = 12;
  let allAssets = [];
  let filteredAssets = [];
  let lastFocusedElement = null;

  // Load initial files
  loadFiles();

  // Event listeners
  closeBtn.addEventListener('click', () => {
    hideMediaLibrary();
  });

  if (toggleBtn) {
    toggleBtn.addEventListener('click', () => {
      if (isOpen()) {
        hideMediaLibrary();
      } else {
        showMediaLibrary();
      }
    });
  }

  searchInput.addEventListener('input', (e) => {
    searchClearBtn.style.display = e.target.value ? '' : 'none';
    currentPage = 0;
    loadFiles(e.target.value);
  });

  searchClearBtn.addEventListener('click', () => {
    searchInput.value = '';
    searchClearBtn.style.display = 'none';
    currentPage = 0;
    loadFiles();
  });

  uploadArea.addEventListener('click', () => fileInput.click());
  uploadArea.addEventListener('dragover', (e) => {
    e.preventDefault();
    uploadArea.classList.add('drag-over');
  });
  uploadArea.addEventListener('dragleave', () => {
    uploadArea.classList.remove('drag-over');
  });
  uploadArea.addEventListener('drop', (e) => {
    e.preventDefault();
    uploadArea.classList.remove('drag-over');
    handleFiles(e.dataTransfer.files);
  });

  fileInput.addEventListener('change', (e) => {
    handleFiles(e.target.files);
  });

  function loadFiles(search = '') {
    const gridContent = grid.querySelector('.files');
    const loading = grid.querySelector('.loading');
    const empty = grid.querySelector('.empty');

    loading.style.display = '';
    gridContent.style.display = 'none';
    empty.style.display = 'none';
    hideErrorState();

    const params = new URLSearchParams({
      limit: pageSize,
      offset: currentPage * pageSize
    });
    if (search) {
      params.append('search', search);
    }

    fetch('/visual-editor/media?' + params)
      .then(r => {
        if (!r.ok) {
          throw new Error('Request failed with status ' + r.status);
        }
        return r.json();
      })
      .then(data => {
        filteredAssets = data.assets || [];
        allAssets = data.assets || [];
        renderFiles();
        updatePagination(data.total || 0);
        loading.style.display = 'none';
        if (filteredAssets.length === 0) {
          empty.style.display = '';
        } else {
          gridContent.style.display = '';
        }
      })
      .catch(err => {
        console.error('Error loading media library files:', err);
        loading.style.display = 'none';
        gridContent.style.display = 'none';
        empty.style.display = 'none';
        showErrorState('Unable to load files. Please try again.');
      });
  }

  function showErrorState(message) {
    const errorEl = grid.querySelector('.error');
    errorEl.querySelector('p').textContent = message;
    errorEl.style.display = '';
  }

  function hideErrorState() {
    grid.querySelector('.error').style.display = 'none';
  }

  function renderFiles() {
    const gridContent = grid.querySelector('.files');
    gridContent.innerHTML = '';

    filteredAssets.forEach(asset => {
      const item = document.createElement('div');
      item.className = 'media-file-item';
      item.role = 'option';
      item.tabIndex = 0;
      item.setAttribute('data-asset-id', asset.assetId);

      if (asset.mimeType && asset.mimeType.startsWith('image/')) {
        const img = document.createElement('img');
        img.src = asset.storagePath || '';
        img.className = 'media-file-thumbnail';
        img.alt = asset.altText || asset.assetName;
        item.appendChild(img);
      } else {
        const icon = document.createElement('i');
        icon.className = 'fa fa-file media-file-icon';
        item.appendChild(icon);
      }

      item.addEventListener('click', () => handleSelectFile(asset));
      item.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          handleSelectFile(asset);
        }
      });

      gridContent.appendChild(item);
    });
  }

  function handleSelectFile(asset) {
    // Dispatch event that editor toolbar can listen for
    const event = new CustomEvent('media-selected', {
      detail: { asset: asset }
    });
    document.dispatchEvent(event);
  }

  function handleFiles(files) {
    // File upload implementation - to be completed in next task
    console.log('Files selected:', files.length);
  }

  function updatePagination(total) {
    const totalPages = Math.ceil(total / pageSize);
    if (totalPages <= 1) {
      pagination.style.display = 'none';
    } else {
      pagination.style.display = 'flex';
      pagination.querySelector('.current-page').textContent = currentPage + 1;
      pagination.querySelector('.total-pages').textContent = totalPages;

      const prevBtn = pagination.querySelector('.prev-page');
      const nextBtn = pagination.querySelector('.next-page');
      prevBtn.disabled = currentPage === 0;
      nextBtn.disabled = currentPage >= totalPages - 1;

      prevBtn.onclick = () => {
        if (currentPage > 0) {
          currentPage--;
          loadFiles(searchInput.value);
        }
      };
      nextBtn.onclick = () => {
        if (currentPage < totalPages - 1) {
          currentPage++;
          loadFiles(searchInput.value);
        }
      };
    }
  }

  function isOpen() {
    return panel.classList.contains('open');
  }

  function showMediaLibrary() {
    lastFocusedElement = document.activeElement;
    panel.classList.add('open');
    searchInput.focus();
  }

  function hideMediaLibrary() {
    panel.classList.remove('open');
    if (lastFocusedElement && typeof lastFocusedElement.focus === 'function') {
      lastFocusedElement.focus();
    }
    lastFocusedElement = null;
  }

  // Expose panel control functions to global scope
  window.showMediaLibrary = showMediaLibrary;
  window.hideMediaLibrary = hideMediaLibrary;

  // ── Keyboard operability: Escape closes the panel, Tab/Shift+Tab stay trapped inside it while
  // open. Follows the same convention as platform-editor.js's width/widget pickers (document-level
  // keydown, gated on the picker's own open state).
  function getFocusableElements() {
    const nodes = panel.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
    return Array.prototype.filter.call(nodes, el => !el.disabled && el.offsetParent !== null);
  }

  document.addEventListener('keydown', (e) => {
    if (!isOpen()) return;

    if (e.key === 'Escape') {
      e.preventDefault();
      hideMediaLibrary();
      return;
    }

    if (e.key !== 'Tab') return;

    const focusable = getFocusableElements();
    if (focusable.length === 0) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement;

    if (e.shiftKey) {
      if (active === first || !panel.contains(active)) {
        e.preventDefault();
        last.focus();
      }
    } else {
      if (active === last || !panel.contains(active)) {
        e.preventDefault();
        first.focus();
      }
    }
  });
})();
</script>
