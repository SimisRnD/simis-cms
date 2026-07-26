# Visual Editor P2: Edit-on-Page Overlay — Design & Implementation

**Phase:** P2 (Edit-on-page overlay)  
**Issue:** #257  
**Status:** Planning (Reference tour → OverlayEditorPane impl → Testing)  
**Owner:** Liz  
**Timeline:** 4–6 weeks

---

## Overview

P2 enables content editors to edit page content in place without navigating to a separate CMS admin interface. The real rendered page IS the editing canvas (JSP preview model).

**Key constraint:** This is the "weakest-supported phase" per scoping study — UX must be pressure-tested against the live v2 reference implementation before committing full scope.

---

## Reference Tour Scope (Phase P2.0)

Before implementing the full overlay, conduct a live UX pressure-test against an existing editing pattern to validate:

1. **Overlay placement** — fixed above, floating, or fixed bottom?
2. **Toolbar attachment** — integrated or detached?
3. **Activation affordance** — single-click, double-click, or context menu?
4. **Save/discard clarity** — clear enough to not confuse users?

### Test Pages

Pick 2–3 real pages from v2 reference (if available) or staging:

| Page | Content Type | Why | Test Goal |
|------|--------------|-----|-----------|
| `/news/article` | Rich text (Quill Delta) | Most common editor workflow | Verify overlay doesn't hide headline/text |
| `/products/widget-showcase` | Mixed widgets (text + image + CTA) | Layout complexity | Test overlay on multi-widget page |
| `/about` | Simple text + sidebar | Minimal content | Test on simple page (edge case) |

### Test Scenarios

1. **Activation**
   - User hovers over content region → sees edit affordance (outline? icon?)
   - User clicks → overlay appears and Quill activates
   - User can see toolbar and content simultaneously

2. **Editing**
   - User types in Quill editor
   - User applies formatting (bold, italic, link)
   - Toolbar is accessible (not cut off, not hidden by browser UI)

3. **Saving**
   - User clicks "Save Draft" → button shows "Saving…"
   - Server stores Delta, responds with rendered HTML
   - Overlay closes, page updates with new content
   - No page reload (smooth UX)

4. **Error recovery**
   - User clicks "Save Draft" on slow network
   - Request times out or errors
   - Error message appears in-page (not alert)
   - User can retry or discard

5. **Discard**
   - User clicks "Discard" → original HTML restored immediately
   - No server call
   - Overlay closes

### Success Criteria

- ✓ User can find and click edit affordance in < 3 seconds
- ✓ Overlay doesn't obscure critical page content (headline, primary text)
- ✓ Save/discard buttons are always visible
- ✓ Error recovery doesn't require page reload
- ✓ WCAG 2.1 AA compliance (keyboard nav, screen reader feedback)

### Feedback Loop

- 2-person usability test (content editor + observer)
- Measure time-to-edit, error recovery friction
- Capture video or screenshots
- Document "works well" vs "confusing" observations
- **Gate:** Stakeholder approval on UX before proceeding to full impl

---

## UX Design Decisions

### Overlay Placement

**Option A: Fixed above content** (preferred for simplicity)
```
┌─ Page header
├─ [Floating toolbar + Save/Discard buttons]
├─ [Quill editor container] (replaces rendered HTML)
├─ [Content below editor]
└─ Page footer
```

**Option B: Floating toolbar** (mobile-friendly but complexity)
```
┌─ Page header
├─ [Rendered page content]
│   ├─ [Quill editor overlay, centered]
│   └─ [Floating toolbar, fixed position]
└─ Page footer
```

**Decision:** Start with Option A (fixed above). Simplest to implement, avoids z-index layers. Test in reference tour; iterate if needed.

### Activation

**Single-click on content region**
- Content region has `data-editor-content="[unique-id]"` attribute
- Regions marked with faint blue outline on hover (admin view only)
- Click → activate Quill for that region
- Double-click reserved for future (P4 canvas mode)

### Toolbar

**Inherit from Quill Snow theme** (existing platform-editor.js model)
- Format buttons: Bold, Italic, Underline, Code
- Link button: opens link modal
- Header dropdown: H2, H3, body text
- List buttons: bullet, ordered
- Source view: show/hide Delta JSON (for debugging)

**Action bar (below or beside toolbar):**
- "Save Draft" button (primary CTA, blue)
- "Discard" button (secondary, gray)
- Status message (aria-live region): "Saved", "Saving…", "Error: [msg]"

### Affordances

**Inactive state (user not hovering):**
- Rendered page looks normal
- Faint outline on editable content regions (barely visible, like 1px light gray)
- Tooltip on hover: "Click to edit" (aria-label for a11y)

**Active hover:**
- Outline becomes darker (2px solid blue)
- "Edit" icon appears top-left of region (small pencil or ✎)
- Cursor changes to pointer

**Active edit:**
- Overlay covers full-width content area
- Original HTML hidden temporarily
- Quill editor takes up full width (responsive)
- Toolbar pinned at top of overlay
- Action bar (Save/Discard) at bottom

---

## Content Fetch & Save Flow

### Fetch Content (GET)

```
User clicks edit region
  → Collect data-editor-content="X"
  → POST to /page?action=getWidgetContent&uniqueId=X
  ↓
Server (PageServlet.java):
  ├─ Find Content by unique ID
  ├─ Check EditorPermissionCommand.canEditContent(user)
  ├─ Return { success: true, format: 2, content: "[Delta JSON]" }
  ↓
Client (OverlayEditorPane.js):
  ├─ Parse Delta JSON
  ├─ Quill.setContents(delta, 'silent')
  └─ Display overlay, focus editor
```

### Save Content (POST)

```
User clicks "Save Draft"
  → Collect Quill Delta: quill.getContents()
  → POST to /page?action=saveWidgetContent
     Body: { uniqueId, delta: "[JSON]" }
  ↓
Server (PageServlet.java):
  ├─ Check CSRF token (userSession.formToken)
  ├─ Check EditorPermissionCommand.canEditContent(user)
  ├─ Call SaveContentCommand.saveSafeDeltaContent():
  │   ├─ Validate Delta structure (no injection)
  │   ├─ Persist to Content.draftContent (format=2)
  │   ├─ Render Delta to HTML via DeltaContentCommand
  │   ├─ Log audit event: SaveAuditEventCommand.log("EDIT_CONTENT", uniqueId, actor, hash)
  │   └─ Return { success: true, html: "[rendered HTML]" }
  ↓
Client (OverlayEditorPane.js):
  ├─ Show "Saving…" state
  ├─ On success:
  │   ├─ Replace rendered HTML with response
  │   ├─ Mark page as having unsaved draft
  │   ├─ Close overlay
  │   └─ Show toast: "Saved"
  └─ On error:
      ├─ Show error message in status region
      ├─ Keep overlay open (user can retry)
      └─ Log to console (not to user)
```

### Discard Content

```
User clicks "Discard"
  → Restore original HTML from snapshot
  → Close overlay
  → No server call
  → draftContent left untouched (user can re-edit later)
```

---

## Format Stamp Logic

**Key principle:** All P2 edits use format=2 (Quill Delta JSON). Legacy format=0 (HTML) is never overwritten by P2.

**Persistence:**
```java
// In SaveContentCommand.saveSafeDeltaContent():
Content draft = Content.loadByUniqueId(uniqueId);
draft.setDraftContent(deltaJson);         // Always format=2
draft.setDraftContentFormat(2);
draft.setModifiedBy(userId);
draft.save();
```

**Publishing (later, in governed flow):**
```java
// In publish step (requires approval):
if (isDraft && draft.getDraftContent() != null) {
  published.setContent(draft.getDraftContent());
  published.setContentFormat(draft.getDraftContentFormat()); // Promotes 2 → published
  published.setDraftContent(null);
}
```

**Migration fallback:** If content exists as format=0 (legacy HTML), render it as-is until user edits. Then convert to Delta for draftContent.

---

## Permission Model

**Who can edit?**
- Role: `content-editor`, `content-manager`, or `admin`
- Check: `EditorPermissionCommand.canEditContent(userSession)` → boolean
- If false: Don't show edit affordances, return 403 on POST

**Who can see edit affordances?**
- Only users with `canEditContent` permission
- Public visitors: no overlay UI at all
- Logged-in non-editors: no overlay UI

**Draft visibility:**
- Only editors can see "Page has draft changes" indicator
- Published page shows to all visitors (no change)
- Draft preview: internal link `/page?draft=true` (future; not P2)

---

## Audit Trail

Every edit is logged via `SaveAuditEventCommand`:

```java
SaveAuditEventCommand.log(
  category: "CONTENT_EDIT",
  eventType: "EDIT_CONTENT",
  outcome: "SUCCESS",
  targetType: "CONTENT",
  targetId: uniqueId,
  targetLabel: "[page title] - [content region name]",
  details: "Format: 2, words: X, links: Y"
);
```

**Tamper-evidence chain:**
```
Old content hash: SHA256(oldDelta) → stored as previous_hash
New content hash: SHA256(newDelta) → stored as record_hash
Audit log entry: { ..., previous_hash: "abc...", record_hash: "def..." }
```

On publish, governance flow verifies chain hasn't been tampered with.

---

## File Structure

### New Files
- `src/main/webapp/javascript/overlay-editor-pane.js` — Overlay activation, Quill lifecycle
- `src/main/webapp/css/overlay-editor-pane.css` — Styling, positioning
- `docs/visual-editor-p2-design.md` — This doc

### Modified Files
- `src/main/java/com/simisinc/platform/presentation/controller/PageServlet.java` — Add handlers for getWidgetContent, saveWidgetContent
- `src/main/java/com/simisinc/platform/application/cms/SaveContentCommand.java` — Enhance to support saveSafeDeltaContent()
- `src/main/webapp/WEB-INF/jsp/cms/view-page.jsp` — Inject overlay-editor-pane.js, add data-editor-content attributes to content regions

### Test Files
- `src/test/java/.../cms/SaveContentCommandTest.java` — Unit tests for Delta persistence, format stamps
- `src/test/java/.../presentation/OverlayEditorPaneTest.js` — Frontend tests for activation, save/discard

---

## Implementation Checklist

### Phase P2.0: Reference Tour
- [ ] Identify 2–3 test pages (real or staging)
- [ ] Create wireframe/mockup of overlay placement options
- [ ] Conduct 2-person usability test
- [ ] Document feedback and UX decisions
- [ ] Get stakeholder sign-off on overlay design

### Phase P2.1: OverlayEditorPane Component
- [ ] Create `overlay-editor-pane.js` with:
  - [ ] Quill lifecycle management (init, focus, blur)
  - [ ] Fetch content from server
  - [ ] Save/discard logic
  - [ ] Dirty state tracking
  - [ ] Error handling and retry
- [ ] Create `overlay-editor-pane.css`:
  - [ ] Overlay container styling
  - [ ] Toolbar styling (inherit Quill Snow theme)
  - [ ] Affordance styling (blue outlines, icons)
  - [ ] Responsive layout (mobile-first)
- [ ] Add ARIA labels for a11y

### Phase P2.2: PageServlet Handlers
- [ ] Add `getWidgetContent` handler (GET):
  - [ ] Permission check (canEditContent)
  - [ ] Fetch Content by unique ID
  - [ ] Return { success, format, content }
- [ ] Add `saveWidgetContent` handler (POST):
  - [ ] CSRF token validation
  - [ ] Permission check (canEditContent)
  - [ ] Call SaveContentCommand.saveSafeDeltaContent()
  - [ ] Return rendered HTML or error
- [ ] Error responses (400, 403, 500) with clear messages

### Phase P2.3: SaveContentCommand Enhancement
- [ ] Implement `saveSafeDeltaContent()` method:
  - [ ] Validate Delta structure (no injection vectors)
  - [ ] Persist to Content.draftContent (format=2)
  - [ ] Render Delta to HTML via DeltaContentCommand
  - [ ] Save audit event with hash chain
  - [ ] Return rendered HTML
- [ ] Add format stamp enforcement

### Phase P2.4: View Page Integration
- [ ] Add data-editor-content attributes to content regions in view-page.jsp
- [ ] Inject overlay-editor-pane.js into page footer
- [ ] Initialize OverlayEditorPane with page/content metadata
- [ ] Test affordance visibility (role-based)

### Phase P2.5: Testing & Accessibility
- [ ] Unit tests for SaveContentCommand (Delta, format stamps, audit)
- [ ] Frontend tests for OverlayEditorPane (activation, save/discard, errors)
- [ ] Integration tests (fetch → edit → save → publish flow)
- [ ] E2E Selenium tests (full UX flow on staging)
- [ ] Accessibility audit (WCAG 2.1 AA):
  - [ ] Keyboard navigation (Tab through toolbar, Escape to close)
  - [ ] Screen reader (aria-live status, aria-label affordances)
  - [ ] Color contrast (overlay border, affordance icons)
- [ ] Browser compatibility (Chrome, Firefox, Safari, Edge)

### Phase P2.6: Code Review & Launch
- [ ] Code review (permissions, security, format stamps, audit trail)
- [ ] Merge to main
- [ ] Prepare launch notes (what's new, how to use, known limitations)

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| **Format stamp corruption** — draftContentFormat doesn't match actual content | Always write format=2 at save boundary; audit trail with hash verification; test roundtrip (save → fetch → verify) |
| **XSS via Delta injection** — malicious Delta ops execute as HTML | Validate Delta structure before persist; use DeltaContentCommand renderer only; CSP headers on response |
| **Permission bypass** — non-editor sees edit affordances | Check canEditContent before showing UI and before server-side action; code review checklist |
| **Audit trail incomplete** — miss some edits or lose chain hash | Log at command boundary (SaveContentCommand), not servlet layer; capture user, timestamp, both hashes |
| **Undo doesn't work** — user clicks Discard but HTML doesn't restore | Store original HTML in overlay state before activation; test discard in E2E suite |
| **Overlay obscures page** — editor can't see full content while editing | Reference tour pressure-test (goal of P2.0); iterate on placement based on feedback |
| **Performance regression** — save takes > 2s, user clicks multiple times | Optimize DeltaContentCommand rendering; add debounce on Save button; show progress indicator |

---

## Success Criteria

**P2 is complete when:**

- ✓ Content editors can activate overlay with single click
- ✓ Quill editor displays correctly and is fully functional
- ✓ Save Draft persists Delta to draftContent with format=2
- ✓ Rendered HTML updates on page after save (no reload)
- ✓ Discard restores original HTML without server call
- ✓ Draft changes persist through page refresh and publish cycle
- ✓ Audit trail captures all edits with hash chain
- ✓ WCAG 2.1 AA compliance (keyboard, screen reader, color contrast)
- ✓ E2E tests pass on staging (3+ pages, multiple content regions)
- ✓ Zero governance bypasses (all changes in draft only, audit-logged)
- ✓ Reference tour feedback incorporated (UX validates against v2 reference)

---

## See Also

- [Quill 2.x Delta Format](https://quilljs.com/docs/delta/)
- [DeltaContentCommand.java](../src/main/java/com/simisinc/platform/application/cms/DeltaContentCommand.java) — Delta rendering
- [EditorPermissionCommand.java](../src/main/java/com/simisinc/platform/application/cms/EditorPermissionCommand.java) — Permission checks
- [SaveAuditEventCommand.java](../src/main/java/com/simisinc/platform/application/audit/SaveAuditEventCommand.java) — Audit trail
- [Visual Editor Program Milestone](https://github.com/SimisRnD/simis-cms/milestone/6) — All phases

