# P4 & P5 Code Scaffolding — Ready for Implementation

**Status:** ✅ Ready for Monday 10am standup  
**Date:** July 26, 2026  
**Team:** Elizabeth (P4), Jordan (P5), Claude (support)

---

## What's Here

All scaffolding code is ready to review and integrate. Each file is ~300-500 LOC and represents the skeleton for the feature — ready for Elizabeth and Jordan to flesh out.

### P4: Real-Time Preview & Undo/Redo

#### Client-Side Components
1. **`p4-overlay-preview-pane.js`** (400 LOC)
   - Split-pane layout (50/50 resizable divider)
   - Fullscreen preview toggle
   - Real-time render on text-change (debounced 300ms)
   - **Next step:** Integrate with P2's `overlay-editor-pane.js`, test renderer latency

2. **`p4-delta-renderer.js`** (250 LOC)
   - Converts Quill Delta JSON to HTML
   - Must match server's `DeltaContentCommand.render()` exactly
   - Security: HTML escaping, URL sanitization, embed exclusion (CVE-2025-15056)
   - **Next step:** Test parity against server; add to test suite

3. **`p4-undo-redo.js`** (200 LOC)
   - Integrates Quill's built-in history module
   - UI buttons (Undo/Redo) with disabled state
   - Keyboard shortcuts (Ctrl+Z, Ctrl+Shift+Z)
   - **Next step:** Integrate into P2's toolbar

4. **`p4-tests.js`** (350 LOC)
   - Jest/Mocha test suite for renderer and undo/redo
   - Test cases: basic formatting, block types, links, security, edge cases
   - **Next step:** Run with Jest, verify renderer parity with server

---

### P5: Media Library & Publishing

#### Backend
1. **`P5-media-assets-schema.sql`** (100 LOC)
   - PostgreSQL schema: `media_assets` and `media_asset_usage` tables
   - Indexes for common queries
   - Soft-delete support (deleted_at column)
   - **Next step:** Run as Flyway migration (V20260726_1008__)

2. **`MediaApiController.java`** (150 LOC)
   - Spring Boot REST endpoints
   - POST `/api/media/upload` — Upload file + alt text
   - GET `/api/media/list` — Paginated list with filtering
   - DELETE `/api/media/{assetId}` — Soft-delete
   - POST `/api/media/{assetId}/alt-text` — Update alt text
   - **Next step:** Implement backing services, wire up to controllers

3. **`MediaUploadService.java`** (180 LOC)
   - Handles upload validation (file size, MIME type, alt text)
   - Stores file via `StorageProvider` (abstracts local/Azure)
   - Creates `MediaAsset` record
   - Logs to audit trail
   - **Next step:** Implement `StorageProvider` for local/Azure, wire up repository

4. **`MediaListService.java`** (150 LOC)
   - Queries, filters, and paginates media
   - Supports: type filter, search (name/tags), sorting
   - **Next step:** Implement full-text search, add tag filtering logic

#### Frontend
1. **`p5-media-panel.js`** (400 LOC)
   - Sidebar panel component (browser, search, upload)
   - Drag-drop file upload
   - Pagination (50 per page)
   - Click "Insert" to add media to editor
   - **Next step:** Integrate with P2's overlay, test API calls

---

## Design Decisions (Locked In)

All design questions have been answered and locked. See `DESIGN-DECISIONS-LOCKED.md` for full details.

| Aspect | Decision |
|--------|----------|
| P4 Preview Layout | Side-by-side with resizable divider |
| P4 Fullscreen | Yes, toggle button |
| P4 Latency | < 100ms target (debounce 300ms) |
| P4 Undo UI | Disabled button (grayed out when empty) |
| P5 Media Org | Flat + tags (no nested folders) |
| P5 Alt Text | Required + AI suggestion helper |
| P5 Search | By name + tags |
| P5 Upload | Drag-drop + button |

---

## Integration Checklist (Week 1)

### Elizabeth (P4)
- [ ] Review `p4-overlay-preview-pane.js`, integrate into P2
- [ ] Review `p4-delta-renderer.js`, implement missing logic
- [ ] Review `p4-undo-redo.js`, add to toolbar
- [ ] Review `p4-tests.js`, set up test framework
- [ ] Week 1 deliverable: Preview pane + undo/redo skeleton working

### Jordan (P5)
- [ ] Review schema, run migration
- [ ] Review `MediaApiController.java`, wire up routes
- [ ] Review `MediaUploadService.java`, implement `StorageProvider`
- [ ] Review `MediaListService.java`, add filtering logic
- [ ] Review `p5-media-panel.js`, integrate into P2
- [ ] Week 1 deliverable: Upload API + media panel skeleton working

### Claude (Support)
- [ ] Generate more detailed tests as needed
- [ ] Help debug renderer parity issues
- [ ] Pair on Azure Storage integration if needed
- [ ] Generate CSS for P4 preview and P5 media panel

---

## File Structure

```
docs/
├── scaffolding/
│   ├── README.md (this file)
│   ├── p4-overlay-preview-pane.js
│   ├── p4-delta-renderer.js
│   ├── p4-undo-redo.js
│   ├── p4-tests.js
│   ├── P5-media-assets-schema.sql
│   ├── MediaApiController.java
│   ├── MediaUploadService.java
│   ├── MediaListService.java
│   └── p5-media-panel.js
├── DESIGN-DECISIONS-LOCKED.md
├── p4-p5-execution-plan.md
└── ...
```

---

## Next Steps (Monday Morning)

1. **10am Standup:**
   - Review this scaffolding
   - Assign work for Week 1
   - Address any questions or concerns

2. **Week 1 (Weeks 1–2):**
   - Elizabeth: P4 integration, renderer testing
   - Jordan: P5 database, API endpoints
   - Claude: Generate tests, CSS, pair on blockers

3. **Week 2 (Weeks 3–8):**
   - Parallel development: Elizabeth on P4.1 + P4.2, Jordan on P5.1 + P5.3

---

## Stub Notes

These files are **not complete** — they are scaffolds with:
- ✅ Full method signatures
- ✅ Comments describing each section
- ✅ Placeholder/TODO comments for implementation details
- ❌ Not yet integrated into existing codebase
- ❌ Not yet tested (test files provided)
- ❌ Error handling is basic (enhance as needed)

**Purpose:** Give Elizabeth and Jordan a starting point to build from, not a finished feature.

---

## Tech Stack (Final)

- **P4:** JavaScript (vanilla), Quill 2.x (built-in history module), Jest/Mocha (tests)
- **P5 Backend:** Java Spring Boot, PostgreSQL, Azure Blob Storage (abstracted via StorageProvider)
- **P5 Frontend:** JavaScript (vanilla), HTML5 drag-drop, Fetch API
- **Database:** PostgreSQL 17, Flyway migrations
- **Testing:** Java unit tests (Service layer), JavaScript tests (renderer/undo/upload validation)

---

## Questions for Elizabeth & Jordan

1. **Elizabeth:** Where should P4 CSS go? (New file or add to existing `overlay-editor-pane.css`?)
2. **Jordan:** Where should `StorageProvider` implementation live? (New file `AzureStorageProvider.java`?)
3. **Both:** Any existing test framework preferences? (Jest for JS, JUnit for Java?)

---

## See Also

- `p4-p5-execution-plan.md` — Detailed 12-week execution plan
- `DESIGN-DECISIONS-LOCKED.md` — All design decisions (locked in)
- `p4-p5-planning.md` — Original planning document (risks, considerations, alternatives)

---

**Ready to launch. Let's go! 🚀**
