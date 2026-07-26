# P4 & P5 Planning: Real-Time Preview, Undo/Redo & Media Library

**Date:** July 26, 2026  
**Status:** Research & Planning Complete  
**Next Step:** Go/No-Go Decision

---

## Executive Summary

### Scope Overview

| Phase | Feature | Complexity | Timeline | Priority |
|-------|---------|-----------|----------|----------|
| **P4** | Real-time preview + Undo/Redo | Medium | 24–36 days | High value |
| **P5** | Media library + Publishing workflow | High | 90–160+ days | Strategic |

### Key Recommendation: Run P4 + P5.1 + P5.3 in Parallel

**Rationale:** P5 can be split into subphases. Media library (P5.1) and publishing workflow (P5.3) don't depend on P4. Image insertion (P5.2) waits for both P4 and P5.1. This parallelism reduces total timeline from 18–25 weeks → **12–15 weeks**.

### Risk Level: **MEDIUM** ✅

**Green:** P2 foundation solid, Quill integrated, no major unknowns  
**Yellow:** Client-side renderer parity, performance untested  
**Red:** Must complete P2 testing first; CVE-2025-15056 needs mitigation

---

## P4: Real-Time Preview & Undo/Redo

### Feature Breakdown

#### 4.1: Split-Pane Preview
**What:** Side-by-side editor (left) + live preview (right)
- User types in Quill editor
- Preview updates in real-time (client-side Delta → HTML)
- No server round-trip needed
- Toggle fullscreen vs split view

**User Journey:**
1. Click edit → overlay opens in split-pane mode
2. Type/format text → preview updates instantly (lag < 100ms)
3. See final rendered output before saving
4. Save → publish rendered HTML

**Why:** Editors see exactly what users will see. Reduces draft/publish surprises.

#### 4.2: Undo/Redo
**What:** Revert/advance through edit history
- Quill has native history module (undo stack built-in)
- Add Ctrl+Z / Cmd+Z keyboard shortcut
- Add visual Undo/Redo buttons in toolbar
- Limit undo depth to last 50 edits (memory efficiency)

**User Journey:**
1. Edit content → make mistake
2. Press Ctrl+Z → reverts to previous state
3. Press Ctrl+Y → redoes change
4. Or click toolbar Undo/Redo buttons

**Why:** Reduces frustration; editors feel safe experimenting.

#### 4.3: Draft Comparison (Optional, can defer)
**What:** Show diff between current draft and last saved version
- Click "View Changes" → side-by-side diff
- Highlight added/removed/modified text
- Useful for reviewing own changes before save

**User Journey:**
1. Edit content extensively
2. Click "View Changes" → see what changed since last save
3. Confirm changes look good → save

**Why:** Confidence before committing; helpful for accidental edits.

### Technical Approach

#### Client-Side Preview Renderer

**Implementation Pattern:**
```javascript
// In overlay-editor-pane.js, on content change:
quill.on('text-change', (delta, oldDelta, source) => {
  if (source === 'user') {
    // Convert Delta to HTML (same logic as server)
    const html = renderDeltaToHtml(delta);
    updatePreviewPane(html);
  }
});

// renderDeltaToHtml() must exactly match server's DeltaContentCommand.render()
function renderDeltaToHtml(delta) {
  // Bold: <strong>
  // Italic: <em>
  // Link: <a href="...">
  // Header: <h1> through <h6>
  // Lists: <ul>, <ol>, <li>
  // Quote: <blockquote>
  // Everything else: <p>
  // (See: src/main/java/DeltaContentCommand.java lines 45–95)
}
```

**Critical:** Client renderer must produce **identical HTML** to server. Any mismatch = preview shows one thing, saved content shows another.

**Mitigation:**
- Extract `DeltaContentCommand.render()` logic into shared JSON spec
- Implement client-side renderer that follows spec exactly
- Unit test both implementations against same Delta test suite
- Add visual regression tests (render on both client/server, compare)

#### Quill History Module

**Implementation:**
```javascript
// Quill 2.x has history module built-in
const quill = new Quill('#editor', {
  modules: {
    history: {
      delay: 1000,      // Batch changes within 1s
      maxStack: 50,     // Keep last 50 changes
      userOnly: true    // Only track user changes (not API changes)
    }
  }
});

// Add keyboard shortcuts
quill.keyboard.addBinding({
  key: 'Z',
  shiftKey: false,
  ctrlKey: true,  // Ctrl+Z on Windows/Linux, Cmd+Z on Mac
  handler: () => {
    quill.history.undo();
    return true;
  }
});

quill.keyboard.addBinding({
  key: 'Z',
  shiftKey: true,
  ctrlKey: true,  // Ctrl+Shift+Z
  handler: () => {
    quill.history.redo();
    return true;
  }
});
```

**UI:** Add toolbar buttons:
- Undo icon (left arrow) → `quill.history.undo()`
- Redo icon (right arrow) → `quill.history.redo()`
- Disable buttons when undo/redo stack empty

### Design Questions (Open)

1. **Preview Layout**
   - Side-by-side split (50/50)?
   - Preview on right vs bottom?
   - Resizable divider?
   - Fullscreen preview mode?
   - **Recommendation:** Side-by-side with resizable divider; fullscreen toggle button

2. **Preview Performance**
   - How large can documents get before preview lags?
   - Throttle updates (debounce 300ms)?
   - Virtualized rendering for huge docs?
   - **Recommendation:** Test with 100KB+ documents; add debounce if lag > 100ms

3. **Undo Depth**
   - How many edits to keep (50? 100? unlimited)?
   - Trade-off: memory vs usability
   - **Recommendation:** Start with 50, measure memory impact

4. **Draft Comparison UI**
   - Side-by-side diff view?
   - Highlight colors (green=added, red=removed)?
   - Word-level or line-level diff?
   - **Recommendation:** Defer to P4.2 (post-MVP); complex feature

### Risk Assessment

#### Medium Risk: Renderer Parity ⚠️
**Problem:** Client-side Delta→HTML must match server exactly. Mismatch = confusing UX.
**Mitigation:**
- Extract server logic to JSON spec
- Implement client using same spec
- Unit test both against identical test data
- Visual regression tests (server render vs client render comparison)
**Impact if fails:** Users see different content after save (high severity)

#### Low Risk: Performance
**Problem:** Live preview updates might lag on slow devices/large documents.
**Mitigation:**
- Debounce preview updates (300ms)
- Profile with 100KB+ documents
- Virtual scrolling if needed
**Impact if fails:** Slight lag; not a blocker (users can disable preview if too slow)

#### Low Risk: History Management
**Problem:** Quill history module is stable; low risk.
**Mitigation:** None needed; Quill handles it.
**Impact:** Very low risk

### Effort Estimate

- **4.1 Preview:** 12–18 days (renderer logic, integration, testing)
- **4.2 Undo/Redo:** 3–5 days (Quill module setup, shortcuts, UI)
- **4.3 Comparison:** 6–10 days (defer to P4.2)
- **Total P4 MVP (4.1 + 4.2):** **24–36 days**
- **With comparison (P4.1 + P4.2 + P4.3):** 30–45 days

---

## P5: Media Library & Publishing Workflow

### Feature Breakdown (Phases)

#### 5.1: Media Library Panel
**What:** Browse/upload images, videos, files
- File browser with filtering (by type, date, size)
- Drag-drop upload
- Delete/rename files
- Tags/categories for organization

**User Journey:**
1. Click "Media" button in overlay
2. Browse uploaded files
3. Search/filter by name or tag
4. Select file
5. Insert into editor (in P5.2)

#### 5.2: Image/Media Insertion into Editor
**What:** Drag media from library into editor
- Quill Embed modules for images, videos
- Lightbox/preview on hover
- Resize/crop in editor
- Alt text / caption support

**Dependencies:** Requires P5.1 (media library exists) + P4 (preview shows embedded media)

**User Journey:**
1. Open media library (P5.1)
2. Drag image → drops into editor
3. Preview updates showing embedded image
4. Click image → edit alt text / caption
5. Save

#### 5.3: Publishing Workflow Improvements
**What:** Governance over draft → publish pipeline
- Draft indicator (visual badge)
- Submit for Review button
- Reviewer approval UI
- Publish to live button
- Version history/rollback

**User Journey:**
1. Edit content → save as draft
2. Click "Submit for Review"
3. Reviewer sees "Pending Review" badge
4. Reviewer approves with release authority
5. Reviewer clicks "Publish"
6. Content goes live; version saved

**Note:** Approval workflow partially exists (see P2 governance notes). P5.3 improves UI/UX.

#### 5.4: Asset Management Dashboard
**What:** Admin panel to manage all media assets
- View all uploaded files across all content
- Bulk delete unused files
- Storage quota monitoring
- Usage analytics (which files used where)

**User Journey:**
1. Admin → Media Dashboard
2. View all files with usage counts
3. Delete orphaned files
4. Monitor storage usage vs quota

### Technical Approach

#### 5.1: Media Library Implementation

**Database Schema:**
```sql
CREATE TABLE media_assets (
  asset_id BIGINT PRIMARY KEY,
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(1024) NOT NULL,
  file_size BIGINT,
  mime_type VARCHAR(100),
  uploaded_by BIGINT REFERENCES users(user_id),
  created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  tags VARCHAR(255),  -- comma-separated or JSON array
  alt_text TEXT,
  UNIQUE(file_path)
);

CREATE TABLE asset_usage (
  usage_id BIGINT PRIMARY KEY,
  asset_id BIGINT REFERENCES media_assets(asset_id),
  content_id BIGINT REFERENCES content(content_id),
  UNIQUE(asset_id, content_id)
);
```

**File Storage:**
- Dev: Local disk `/opt/simis/files/media/`
- Prod: Azure Storage (container `media-assets`)
- Relative URLs in editor: `/media/uploads/filename.jpg`

**Upload Endpoint:**
```
POST /api/media/upload
  file: multipart/form-data
  tags: ?tags=nature,landscape
  → {assetId, fileName, filePath, url}
```

#### 5.2: Quill Image Module

**Implementation:**
```javascript
const quill = new Quill('#editor', {
  modules: {
    toolbar: ['bold', 'italic', 'image', 'video'],
    imageResize: true,  // Allow resizing
  },
  blotOptions: {
    image: {
      allowedStyles: ['width', 'height'],
      align: 'center'  // Default alignment
    }
  }
});

quill.on('selection-change', (range, oldRange, source) => {
  if (range && range.length === 0) {
    const [block, offset] = quill.getLine(range.index);
    if (block && block.statics.blotName === 'image') {
      showImageMenu(block);  // Edit alt text, caption
    }
  }
});

// Insert image from media library
function insertImageFromLibrary(assetId, url, fileName) {
  const index = quill.getSelection().index;
  quill.insertEmbed(index, 'image', {
    url: url,
    alt: fileName,
    assetId: assetId
  });
  quill.setSelection(index + 1);
}
```

#### 5.3: Publishing Workflow

**Existing State:** P2 already has draft/publish separation. P5.3 improves UI.

**Current Flow (from P2):**
1. Save Draft → persists to Content.draft_content
2. Submit for Review → sets draft_status='submitted'
3. Reviewer Approves → requires re-auth, release authority
4. Publish → copies draft_content → content, updates modified date

**P5.3 Improvements:**
- Visual badges (Draft, Pending Review, Published)
- Timeline view (show all versions with dates)
- One-click rollback to prior version
- Pre-publish preview (show what will go live)

### Design Questions (Open)

1. **Media Storage**
   - Local filesystem or cloud (Azure Storage)?
   - CDN/caching for images?
   - **Recommendation:** Local dev, cloud prod; use CDN for perf

2. **File Upload Limits**
   - Max file size (10MB? 100MB? 1GB)?
   - Allowed types (images only? video? PDF?)?
   - Storage quota per user/org?
   - **Recommendation:** Start with 50MB limit, images + PDF only

3. **Media Library Organization**
   - Folder hierarchy?
   - Tags vs categories?
   - Search/filter?
   - **Recommendation:** Flat structure with tags; full-text search on filename

4. **Image Optimization**
   - Auto-resize large uploads?
   - Generate thumbnails?
   - WebP conversion?
   - **Recommendation:** Auto-resize to max 2000px; defer WebP to P5.4

5. **Publishing Approval**
   - Single reviewer or multiple?
   - Can editor publish their own drafts?
   - Approval timeout (expire if not reviewed in X days)?
   - **Recommendation:** Single reviewer per P2; defer multi-reviewer to future

### Risk Assessment

#### High Risk: Storage Strategy ⚠️⚠️
**Problem:** Unclear if local or cloud; impacts infra and cost.
**Mitigation:** Decide storage backend before P5.1 starts.
**Impact if fails:** Major refactor mid-phase; schedule slip

#### Medium Risk: Image Optimization
**Problem:** Large images slow down preview and page loads.
**Mitigation:** Auto-resize on upload; measure performance impact.
**Impact if fails:** Performance regression

#### Low Risk: Publishing Workflow
**Problem:** P2 already has most logic; P5.3 is mostly UI.
**Mitigation:** None; low risk.
**Impact:** Low

### Effort Estimate

- **P5.1 Media Library:** 30–40 days (upload, storage, browser UI)
- **P5.2 Image Insertion:** 20–30 days (Quill integration, metadata, preview)
- **P5.3 Publish Workflow:** 15–25 days (UI, versioning, rollback)
- **P5.4 Asset Dashboard:** 10–15 days (admin panel, analytics)
- **Total P5 (all phases):** **90–160+ days**

**Recommendation:** Implement in order: P5.1 → P5.2 → P5.3 → (P5.4 defer to P6)

---

## Sequencing Options

### Option A: Sequential (P4 → P5) — 18–25 weeks
```
Weeks 1–5:   P4 (preview + undo/redo)
Weeks 6–25:  P5 (media + publishing)
```
**Pros:** Simple; less parallel coordination  
**Cons:** Slow; media library needed by editors now

### Option B: Parallel (Recommended) — 12–15 weeks
```
Weeks 1–8:   P4 + P5.1 + P5.3 (in parallel)
Weeks 9–11:  P5.2 (depends on P4 + P5.1)
Weeks 12–15: P5.4 (optional polish)
```
**Pros:** 30% faster; P5.1 unblocked; P5.3 adds governance early  
**Cons:** Requires 3-team coordination; more complex

### Option C: MVP-First (Recommended Alternative) — 8–12 weeks
```
Weeks 1–5:   P4 MVP (preview + undo/redo only)
Weeks 6–8:   P5.1 MVP (basic media library)
Weeks 9–12:  P5.2 (image insertion)
(P5.3 + P5.4 defer to next quarter)
```
**Pros:** Launch core features fast; polish later  
**Cons:** Publishing workflow improvements delayed; no admin dashboard

**Team Recommendation:** Start with **Option B** (parallel). If resource-constrained, switch to **Option C** (MVP-first).

---

## Go/No-Go Decision Criteria

### ✅ GO if ALL are true:

- [x] P2 implementation complete and merged
- [ ] P2 testing complete or only minor issues found
- [ ] Team bandwidth: 2–3 developers available full-time
- [ ] Storage backend decided (local dev, Azure prod)
- [ ] Stakeholder approval of phasing and timeline
- [ ] Performance testing plan for preview (< 100ms latency)
- [ ] Security review of media upload endpoint done

### 🛑 NO-GO if ANY are true:

- [ ] P2 testing reveals major architectural issues
- [ ] CVE-2025-15056 (Quill export) cannot be mitigated
- [ ] Team unavailable for 3–6 months
- [ ] Simultaneous editor conflicts discovered in P2
- [ ] Storage costs prohibitive (Azure)

### Yellow Flags — Proceed with Caution:

- [ ] Client-side renderer parity seems risky (MITIGATE: test matrix)
- [ ] Performance testing shows preview lags (MITIGATE: debounce, virtualize)
- [ ] Media upload permissions unclear (MITIGATE: align with P2 role model)

---

## Dependencies & Blockers

### P4 Dependencies
- P2 must be complete (Quill editor foundation)
- `DeltaContentCommand.render()` must be stable (server renderer)
- No breaking changes to Delta JSON format

### P5 Dependencies
- **P5.1:** Standalone; no P4/P5 dependency
- **P5.2:** Requires P5.1 (media library) + P4 (preview must show images)
- **P5.3:** Requires P2 (draft/publish workflow exists)
- **P5.4:** Requires P5.1 + P5.2 (need files to show usage)

### Known Blockers
- **CVE-2025-15056 (Quill Export API):** Export feature in Quill has XSS hole. **Mitigation:** Use only safe renderer (`DeltaContentCommand`), disable export in UI.
- **Single-Editor Assumption:** P2 assumes one editor at a time. **If multi-user editing discovered:** P4/P5 timeline +50%.

---

## Recommendation

**Proceed with P4 & P5 under Option B (Parallel) sequencing:**

1. **P2 Launch (1–2 weeks):** Test and release P2
2. **Parallel Phase (6–8 weeks):** Run P4 + P5.1 + P5.3 concurrently
3. **P5.2 (2–3 weeks):** Image insertion (depends on both)
4. **P5.4 (2 weeks optional):** Asset management dashboard

**Timeline:** 12–15 weeks total (vs 18–25 weeks sequential)

**Risk:** MEDIUM (mitigable; no red blockers if P2 succeeds)

**Go/No-Go:** **Recommend GO** pending:
- [ ] P2 testing complete
- [ ] Storage backend approved
- [ ] Team bandwidth confirmed

---

## Next Steps

1. **Decision:** Review criteria above; make go/no-go call
2. **Approval:** Get stakeholder sign-off on timeline & sequencing
3. **Planning:** Assign teams to P4 vs P5 phases
4. **Kick-off:** Schedule design sessions for preview UX & media UI
5. **P2 Launch:** Complete P2 testing (in parallel with above)

---

## Open Questions for Design Sessions

### P4 Design Session
- [ ] Should preview be resizable? Fixed 50/50 split?
- [ ] Should fullscreen preview button exist?
- [ ] What's the acceptable latency for live preview update?
- [ ] Undo/Redo: visual indicator when undo stack empty?

### P5 Design Session
- [ ] Media library: nested folders or flat + tags?
- [ ] Image alt text: required or optional?
- [ ] Max upload size? Allowed types?
- [ ] Storage: local dev or cloud from day 1?
- [ ] Publishing: single reviewer or team-based approval?

---

## References

- P2 implementation details: `p2-integration-complete.md`
- P2 testing guide: `p2-testing-guide.md`
- Quill documentation: https://quilljs.com/docs/modules
- DeltaContentCommand implementation: `src/main/java/DeltaContentCommand.java` (lines 45–95)
