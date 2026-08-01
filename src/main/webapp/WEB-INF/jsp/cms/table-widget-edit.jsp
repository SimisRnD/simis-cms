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
<%-- tableData is a Map<String,Object> set by TableWidget in request scope: "headers" is a
     List<String>, "rows" is a List<List<String>> -- plain Java collections, not the raw Jackson
     JsonNode, because JSTL's <c:forEach> cannot iterate a JsonNode/ArrayNode. --%>

<div class="table-widget-edit-container">
  <!-- Toolbar -->
  <div class="table-toolbar">
    <button class="toolbar-btn add-row" title="Add row" aria-label="Add row to table">
      <i class="fa fa-plus"></i> Row
    </button>
    <button class="toolbar-btn add-col" title="Add column" aria-label="Add column to table">
      <i class="fa fa-plus"></i> Column
    </button>
    <div class="toolbar-separator"></div>
    <button class="toolbar-btn delete-row" title="Delete row" aria-label="Delete selected row">
      <i class="fa fa-trash"></i> Row
    </button>
    <button class="toolbar-btn delete-col" title="Delete column" aria-label="Delete selected column">
      <i class="fa fa-trash"></i> Column
    </button>
    <div class="toolbar-separator"></div>
    <button class="toolbar-btn save-table" title="Save table" aria-label="Save table changes">
      <i class="fa fa-save"></i> Save
    </button>
    <span class="table-save-status" aria-live="polite"></span>
  </div>

  <!-- Table -->
  <div class="table-edit-wrapper">
    <table class="data-table editable" role="table" id="edit-table">
      <thead>
        <tr role="row" class="header-row">
          <c:choose>
            <c:when test="${not empty tableData.headers}">
              <%-- The loop variable is deliberately NOT named "header": JSP EL reserves that
                   identifier for the implicit request-header Map (JSP.2.9), so a same-named
                   <c:forEach> variable is silently ignored -- ${header} always resolves to the
                   implicit object, never the loop value, regardless of scope. That previously
                   rendered the viewer's own request headers (including the JSESSIONID/userToken
                   session cookies) into every <th>, changing per request, instead of the actual
                   column header text. --%>
              <c:forEach items="${tableData.headers}" var="headerCell" varStatus="headerStatus">
                <th role="columnheader" scope="col" data-col="${headerStatus.index}">
                  <div class="header-cell" contenteditable="true" role="textbox" tabindex="0">
                    <c:out value="${headerCell}"/>
                  </div>
                </th>
              </c:forEach>
            </c:when>
            <c:otherwise>
              <th><div class="header-cell" contenteditable="true" role="textbox" tabindex="0">Column 1</div></th>
              <th><div class="header-cell" contenteditable="true" role="textbox" tabindex="0">Column 2</div></th>
            </c:otherwise>
          </c:choose>
        </tr>
      </thead>
      <tbody>
        <c:choose>
          <c:when test="${not empty tableData.rows}">
            <c:forEach items="${tableData.rows}" var="row" varStatus="rowStatus">
              <tr role="row" data-row="${rowStatus.index}">
                <c:forEach items="${row}" var="cell" varStatus="cellStatus">
                  <td role="cell" data-row="${rowStatus.index}" data-col="${cellStatus.index}">
                    <div class="cell-content" contenteditable="true" role="textbox" tabindex="0">
                      <c:out value="${cell}"/>
                    </div>
                  </td>
                </c:forEach>
              </tr>
            </c:forEach>
          </c:when>
          <c:otherwise>
            <tr data-row="0">
              <td data-row="0" data-col="0"><div class="cell-content" contenteditable="true" role="textbox" tabindex="0">Cell 1</div></td>
              <td data-row="0" data-col="1"><div class="cell-content" contenteditable="true" role="textbox" tabindex="0">Cell 2</div></td>
            </tr>
          </c:otherwise>
        </c:choose>
      </tbody>
    </table>
  </div>

  <!-- Hidden input for storing table data -->
  <input type="hidden" id="table-data-input" name="tableData" />
</div>

<style nonce="${cspNonce}">
  .table-widget-edit-container {
    margin: 20px 0;
    padding: 16px;
    border: 1px solid #ddd;
    border-radius: 4px;
    background: #fafafa;
  }

  .table-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
    padding-bottom: 12px;
    border-bottom: 1px solid #ddd;
  }

  .toolbar-btn {
    padding: 8px 12px;
    background: white;
    border: 1px solid #ccc;
    border-radius: 4px;
    cursor: pointer;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .toolbar-btn:hover {
    border-color: #0066cc;
    color: #0066cc;
  }

  .toolbar-btn:focus {
    outline: 2px solid #0066cc;
    outline-offset: -2px;
  }

  .toolbar-separator {
    width: 1px;
    background: #ddd;
  }

  .toolbar-btn.save-table {
    background: #0066cc;
    border-color: #0066cc;
    color: white;
  }

  .toolbar-btn.save-table:hover {
    background: #0052a3;
    color: white;
  }

  .toolbar-btn.save-table:disabled {
    opacity: 0.6;
    cursor: default;
  }

  .table-save-status {
    align-self: center;
    font-size: 13px;
    color: #666;
  }

  .table-edit-wrapper {
    overflow-x: auto;
    background: white;
    border: 1px solid #ddd;
    border-radius: 4px;
  }

  .data-table.editable {
    width: 100%;
    border-collapse: collapse;
    margin: 0;
  }

  .data-table.editable thead {
    background-color: #f0f0f0;
  }

  .data-table.editable th {
    padding: 0;
    border: 1px solid #ddd;
  }

  .data-table.editable td {
    padding: 0;
    border: 1px solid #ddd;
  }

  .header-cell,
  .cell-content {
    padding: 8px 12px;
    min-height: 24px;
    outline: none;
    display: block;
    word-wrap: break-word;
  }

  .header-cell:focus,
  .cell-content:focus {
    background-color: #e6f2ff;
    outline: 2px solid #0066cc;
    outline-offset: -2px;
  }

  .cell-content {
    cursor: text;
  }

  .cell-content:hover {
    background-color: #f9f9f9;
  }

  .data-table.editable tbody tr:hover {
    background-color: transparent;
  }
</style>

<script nonce="${cspNonce}">
(function() {
  const table = document.getElementById('edit-table');
  const tableDataInput = document.getElementById('table-data-input');
  const addRowBtn = document.querySelector('.add-row');
  const addColBtn = document.querySelector('.add-col');
  const deleteRowBtn = document.querySelector('.delete-row');
  const deleteColBtn = document.querySelector('.delete-col');
  const saveBtn = document.querySelector('.save-table');
  const saveStatus = document.querySelector('.table-save-status');

  let selectedCell = null;
  const cellOriginalValues = new WeakMap();

  function handleCellFocus(e) {
    cellOriginalValues.set(e.target, e.target.textContent);
  }

  // Keyboard navigation
  function handleCellKeydown(e) {
    const cell = e.target.closest('[contenteditable]');
    if (!cell) return;

    const cellDiv = e.target;
    const td = cellDiv.closest('td') || cellDiv.closest('th');
    if (!td) return;

    switch (e.key) {
      case 'Tab':
        e.preventDefault();
        selectNextCell(td, e.shiftKey);
        break;
      case 'Enter':
        if (e.ctrlKey) {
          // Allow Enter with Ctrl to create new line
          break;
        }
        e.preventDefault();
        // Blur to confirm edit
        cellDiv.blur();
        break;
      case 'Escape':
        e.preventDefault();
        if (cellOriginalValues.has(cellDiv)) {
          cellDiv.textContent = cellOriginalValues.get(cellDiv);
        }
        cellDiv.blur();
        saveTableData();
        break;
    }
  }

  function selectNextCell(currentTd, goBack = false) {
    let nextTd = goBack ?
        currentTd.previousElementSibling || currentTd.parentElement.previousElementSibling?.lastElementChild :
        currentTd.nextElementSibling || currentTd.parentElement.nextElementSibling?.firstElementChild;

    if (nextTd) {
      const editor = nextTd.querySelector('[contenteditable]');
      if (editor) {
        editor.focus();
        // Select all text
        const range = document.createRange();
        range.selectNodeContents(editor);
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
      }
    }
  }

  // Add event listeners to all editable cells
  function attachCellListeners() {
    table.querySelectorAll('[contenteditable]').forEach(cell => {
      cell.addEventListener('focus', handleCellFocus);
      cell.addEventListener('keydown', handleCellKeydown);
      cell.addEventListener('click', () => {
        selectedCell = cell.closest('td') || cell.closest('th');
      });
    });
  }

  // Add row
  addRowBtn.addEventListener('click', () => {
    const tbody = table.querySelector('tbody');
    const colCount = table.querySelector('thead tr').children.length;
    const newRow = document.createElement('tr');
    newRow.setAttribute('data-row', tbody.children.length);

    for (let i = 0; i < colCount; i++) {
      const td = document.createElement('td');
      td.setAttribute('role', 'cell');
      td.setAttribute('data-col', i);
      td.innerHTML = '<div class="cell-content" contenteditable="true" role="textbox" tabindex="0"></div>';
      newRow.appendChild(td);
    }

    tbody.appendChild(newRow);
    attachCellListeners();
    saveTableData();
  });

  // Add column
  addColBtn.addEventListener('click', () => {
    const headerRow = table.querySelector('thead tr');
    const colIndex = headerRow.children.length;
    const headerCell = document.createElement('th');
    headerCell.setAttribute('scope', 'col');
    headerCell.setAttribute('role', 'columnheader');
    headerCell.setAttribute('data-col', colIndex);
    headerCell.innerHTML = '<div class="header-cell" contenteditable="true" role="textbox" tabindex="0">New</div>';
    headerRow.appendChild(headerCell);

    table.querySelectorAll('tbody tr').forEach((row, rowIndex) => {
      const td = document.createElement('td');
      td.setAttribute('role', 'cell');
      td.setAttribute('data-row', rowIndex);
      td.setAttribute('data-col', colIndex);
      td.innerHTML = '<div class="cell-content" contenteditable="true" role="textbox" tabindex="0"></div>';
      row.appendChild(td);
    });

    attachCellListeners();
    saveTableData();
  });

  // Delete row
  deleteRowBtn.addEventListener('click', () => {
    if (selectedCell && selectedCell.tagName === 'TD') {
      const row = selectedCell.closest('tr');
      row.remove();
      selectedCell = null;
      attachCellListeners();
      saveTableData();
    }
  });

  // Delete column
  deleteColBtn.addEventListener('click', () => {
    if (selectedCell) {
      const colIndex = parseInt(selectedCell.getAttribute('data-col'));
      if (colIndex >= 0) {
        table.querySelectorAll('tr').forEach(row => {
          const cells = row.children;
          if (colIndex < cells.length) {
            cells[colIndex].remove();
          }
        });
        selectedCell = null;
        attachCellListeners();
        saveTableData();
      }
    }
  });

  // Save table data to hidden input
  function saveTableData() {
    const headers = [];
    const rows = [];

    // Get headers
    table.querySelectorAll('thead tr th .header-cell').forEach(cell => {
      headers.push(cell.textContent.trim() || '');
    });

    // Get rows
    table.querySelectorAll('tbody tr').forEach(row => {
      const rowData = [];
      row.querySelectorAll('td .cell-content').forEach(cell => {
        rowData.push(cell.textContent.trim() || '');
      });
      if (rowData.length > 0) {
        rows.push(rowData);
      }
    });

    const tableData = JSON.stringify({ headers, rows });
    tableDataInput.value = tableData;

    // Trigger data update event -- carries the latest serialized table data on every edit so any
    // listener has it available. The actual server save (below) is deliberately explicit, fired
    // from the Save button rather than on every keystroke/table-changed event, to avoid a network
    // request per character typed.
    const event = new CustomEvent('table-changed', {
      detail: { tableData: tableData }
    });
    document.dispatchEvent(event);
  }

  // ── Persist to the server ─────────────────────────────────────────────────
  //
  // Table data is stored as an ordinary widget preference in the page's draft layout XML, saved
  // through the same generic, already-tested mechanism every other widget's preferences go
  // through: PageServlet's "setWidgetPreferences" action, which calls
  // MutateLayoutCommand.setWidgetPreferences (itself gated by TableWidget.isValidTableData for
  // this widget's "tableData" preference specifically). platform-editor.js's own applyPrefs()/
  // mutatePage() use the identical action/params for the "⚙ Prefs" panel available on every
  // widget -- this widget's toolbar runs in its own script, outside that file's closure, so the
  // same request is built directly here rather than calling into it.
  function getWidgetPosition() {
    const container = table.closest('[data-editor-widget]');
    if (!container) return null;
    const parts = (container.dataset.editorWidget || '').split('-');
    if (parts.length !== 3) return null;
    return { s: parts[0], c: parts[1], w: parts[2] };
  }

  function saveToServer() {
    const position = getWidgetPosition();
    if (!position) {
      if (saveStatus) saveStatus.textContent = 'Unable to save: widget position not found';
      return;
    }
    // "mainToken" is the CSRF form token, declared as a page-global by main.jsp -- the same
    // source platform-editor.js's own getToken() reads.
    const token = (typeof mainToken !== 'undefined') ? mainToken : '';

    saveBtn.disabled = true;
    if (saveStatus) saveStatus.textContent = 'Saving…';

    const body = new URLSearchParams();
    body.append('action', 'setWidgetPreferences');
    body.append('token', token);
    body.append('s', position.s);
    body.append('c', position.c);
    body.append('w', position.w);
    body.append('prefs', JSON.stringify({ tableData: tableDataInput.value }));

    fetch(window.location.pathname, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString()
    })
      .then(resp => resp.json().catch(() => ({})).then(data => {
        if (!resp.ok || !data.success) {
          throw new Error((data && data.error) || ('HTTP ' + resp.status));
        }
        return data;
      }))
      .then(() => {
        if (saveStatus) saveStatus.textContent = 'Saved';
        // Reload to confirm the change persisted and to pick up the freshly saved draft, matching
        // every other in-canvas mutation's success behavior (see platform-editor.js).
        window.location.reload();
      })
      .catch(err => {
        saveBtn.disabled = false;
        if (saveStatus) saveStatus.textContent = 'Error: ' + err.message;
      });
  }

  if (saveBtn) {
    saveBtn.addEventListener('click', () => {
      saveTableData();
      saveToServer();
    });
  }

  // Initial setup
  attachCellListeners();

  // Auto-save on input
  table.addEventListener('input', saveTableData);
})();
</script>
