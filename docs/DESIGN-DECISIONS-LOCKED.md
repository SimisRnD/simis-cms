# P4 & P5 Design Decisions — LOCKED

**Date:** July 26, 2026  
**Decided By:** Elizabeth (product authority)  
**Status:** Ready for implementation

---

## P4: Real-Time Preview & Undo/Redo

### Preview Layout
- **Decision:** Side-by-side split with resizable divider
- **Why:** Standard pattern (Markdown editors, Figma, VS Code all use this). Users control the split ratio.
- **Implementation:** 50/50 default, user drags divider to resize
- **Responsive:** On mobile/tablet, collapse to single-pane (preview or editor toggle)

### Fullscreen Preview
- **Decision:** Yes, include fullscreen toggle button
- **Why:** Helps users review final layout without editor visual noise
- **Implementation:** Icon button (↗ or maximize) in preview header, toggles fullscreen mode
- **Behavior:** ESC or click toggle to exit fullscreen

### Preview Latency Target
- **Decision:** < 100ms render time (debounce at 300ms)
- **Why:** Feels "live" to users without overwhelming the browser with re-renders
- **Implementation:** 
  - Debounce text-change events at 300ms
  - Render completes in < 100ms (target, not guaranteed)
  - If render takes > 100ms, profile and optimize

### Undo/Redo Button UI
- **Decision:** Disabled button (grayed out when stack empty)
- **Why:** Standard UX pattern. Users recognize disabled state immediately.
- **Implementation:** 
  - Button opacity: 0.5 when disabled
  - Cursor: not-allowed
  - Tooltip: "Nothing to undo" / "Nothing to redo" when empty
  - Always clickable (no aria-disabled, so screen readers see it)

---

## P5: Media Library & Publishing Workflow

### Media Organization
- **Decision:** Flat structure + tags (no nested folders)
- **Why:** Simpler, easier to search. Tags allow flexible organization without deep hierarchies.
- **Implementation:**
  - All media in one list (paginated, 50 per page)
  - Each asset has: name, type (image/pdf), size, date, tags, alt-text
  - Search: by name or tag (full-text on both)
  - Filter: by type, date range, size range

### Image Alt Text
- **Decision:** Required field + AI suggestion helper
- **Why:** 
  - Compliance (508 / WCAG 2.1)
  - SEO improvement
  - AI helper reduces friction (users can accept, edit, or skip)
- **Implementation:**
  - Upload form: alt-text field is required (form validation blocks save)
  - On upload: Show AI-suggested alt text (generated server-side or client-side)
  - User can: Accept, edit, or clear (if they clear, validation fails and blocks save)
  - Error message: "Alt text is required for accessibility and SEO"

### Search & Filter
- **Decision:** Search by name + tags; filter by type, date, size
- **Why:** Covers 90% of user search needs without overcomplicating UI
- **Implementation:**
  - Search box: "Search by name or tag"
  - Filters: Dropdown for type (image, PDF, all); date picker (last 7 days, 30 days, all); size slider (0–50MB)
  - Results: Live update as user types/filters
  - Pagination: 50 per page

### Upload Method
- **Decision:** Both drag-drop + button
- **Why:** Accessibility + convenience. Users with different workflows can use what works for them.
- **Implementation:**
  - Drag-drop zone: Large, obvious, shows "Drag files here or click to browse"
  - Upload button: Inside the zone or as fallback
  - Multiple files: Support batch upload
  - Progress: Show upload % per file, not just a spinner
  - Validation: File size (50MB limit), MIME types (images + PDF only)

---

## Publishing Workflow (P5.3)

### Status Indicators
- **Decision:** Badge + status text (Draft, Pending Review, Published)
- **Implementation:**
  - Draft: Gray badge "Draft"
  - Pending Review: Yellow badge "Pending Review"
  - Published: Green badge "Published"
  - Last updated: Timestamp below

### Publishing Actions
- **Decision:** Three-button workflow (Save Draft, Submit for Review, Publish to Live)
- **Implementation:**
  - Save Draft: Always available (blue button)
  - Submit for Review: Available when draft exists and not already pending (orange button)
  - Publish to Live: Available when approved by reviewer (green button, admin/reviewer only)
  - Discard: Available when draft exists (dark gray button)

---

## Summary Table

| Aspect | P4 | P5 |
|--------|----|----|
| Preview Layout | Side-by-side + resizable | — |
| Fullscreen | Yes | — |
| Latency | < 100ms (debounce 300ms) | — |
| Undo UI | Disabled (grayed) | — |
| Media Org | — | Flat + tags |
| Alt Text | — | Required + AI suggestion |
| Search | — | Name + tags |
| Upload | — | Drag-drop + button |
| Publish Badges | — | Draft, Pending, Published |

---

## No Further Design Questions

All decisions above are final and ready for implementation. No committee review needed.

**Next:** Generate code scaffolding (Monday morning ready).
