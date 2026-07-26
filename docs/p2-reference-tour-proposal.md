# P2.0 Reference Tour Proposal — UX Design & Test Plan

**Status:** Design proposal for stakeholder review  
**Prepared for:** P2.0 Reference Tour phase  
**Goal:** Validate overlay placement, toolbar design, and activation UX before full implementation

---

## Executive Summary

This proposal outlines 3 representative test pages, 2 overlay placement options, and design recommendations to pressure-test the P2 edit-on-page UX. The goal is to answer key questions:

1. **Does the overlay placement obscure critical content?**
2. **Is the activation affordance clear enough to find and click?**
3. **Are Save/Discard actions obvious without confusion?**
4. **Is the toolbar layout usable on mobile?**

**Recommended approach:** Test both placement options on real pages with 2–3 content editors, gather feedback, iterate, then commit to implementation.

---

## Test Page Selection

### Page 1: News Article (Simple, Single Content Region)

**Why:** Most common content editor workflow

**Characteristics:**
- Single headline (H1)
- Publication metadata (date, author)
- Main body content (rich text, multiple paragraphs)
- Featured image (optional)
- Sidebar widgets (comments, related articles)

**Test scenario:** Editor clicks on body text to edit. Overlay should:
- Not hide headline or publication date
- Leave sidebar visible for context
- Center on main content region

**Example:** A blog post like "New Feature Announcement" or "Case Study"

---

### Page 2: Multi-Widget Showcase (Complex, Multiple Content Regions)

**Why:** Tests overlay on layout with multiple editable regions

**Characteristics:**
- Hero section (image + CTA)
- Featured products grid (3–4 columns)
- Content section with text + image (left-right layout)
- Video embed widget
- Testimonials carousel
- Call-to-action banner

**Test scenario:** Editor clicks on text within one widget (e.g., product title in grid). Overlay should:
- Target only the clicked region (not entire widget)
- Not break grid layout (no horizontal scrolling)
- Keep other widgets visible for reference

**Example:** A product showcase page or homepage

---

### Page 3: Simple Static Page (Minimal, Edge Case)

**Why:** Tests edge case with minimal content

**Characteristics:**
- Page title (H1)
- Short body text (1–2 paragraphs)
- No sidebar
- Minimal widgets

**Test scenario:** Editor clicks on body text. Overlay should:
- Not appear to "zoom in" awkwardly
- Show toolbar clearly even with small content area
- Feel proportional to page size

**Example:** About page, Contact info page, or simple instructions page

---

## Overlay Placement Options

### Option A: Fixed Toolbar Above Content (Recommended)

**Layout:**
```
┌─ Page Header (nav, logo)
├─ [Page Title / Breadcrumbs]
├─ [Floating Toolbar + Status]
│  ├─ Format buttons (Bold, Italic, Link, Header)
│  ├─ Status indicator (✓ Saved | ⚠ Unsaved | ⏳ Saving…)
│  └─ Action buttons (Save Draft, Discard)
├─ [Quill Editor Container]
│  ├─ Rich text editor (full-width)
│  └─ Rendered content (temporarily replaced)
├─ [Sidebar] (visible, not obscured)
└─ Page Footer
```

**Pros:**
- ✓ Toolbar always visible (no scrolling needed)
- ✓ Doesn't obscure page content
- ✓ Clear separation between editing and reading
- ✓ Simplest to implement (no z-index layers)
- ✓ Mobile-friendly (toolbar stacks above content)

**Cons:**
- Takes vertical space (reduces visible content area)
- Requires page scroll if content is tall

**Best for:** Articles with longer body text, multi-paragraph content

---

### Option B: Floating Toolbar (Mobile-First Alternative)

**Layout:**
```
┌─ Page Header (nav, logo)
├─ [Page Title]
├─ [Rendered page content]
│  ├─ [Quill Editor Overlay, centered]
│  │  └─ Semi-transparent backdrop behind editor
│  ├─ [Floating Toolbar, fixed position]
│  │  ├─ Format buttons
│  │  ├─ Status indicator
│  │  └─ Action buttons (Save, Discard)
│  └─ [Page content partially visible behind]
├─ [Sidebar] (dimmed/visible)
└─ Page Footer
```

**Pros:**
- ✓ Doesn't take up vertical space
- ✓ Feels like an "overlay experience" (modal-like)
- ✓ Good on mobile (toolbar floats on top)
- ✓ Can position toolbar on right side (doesn't block text)

**Cons:**
- ✗ Toolbar can obscure text if not positioned carefully
- ✗ Z-index layering complexity
- ✗ Semi-transparent backdrop can make page feel "disconnected"
- ✗ Harder to implement (CSS/JS positioning edge cases)

**Best for:** Short content, mobile-heavy sites

---

## Design Recommendation: **Option A (Fixed Toolbar)**

**Rationale:**

1. **Simpler to implement** — No z-index wars, no backdrop layering
2. **Clearer separation** — Editor mode looks distinct from reading mode
3. **Less likely to obscure content** — Toolbar is always above, never overlapping text
4. **Better for accessibility** — Screen reader can navigate toolbar → content in logical order
5. **Mobile-friendly** — Toolbar naturally stacks above content on narrow screens

**For P2.0, we test Option A first.** If feedback suggests mobile toolbar is crowded, we can iterate to a collapsible toolbar or side-panel variant in P2.1.

---

## Activation Affordances

### Visual Indicators (Inactive State)

**For admin/editor users only:**

```
Rendered content region (data-editor-content="unique-id")
  ├─ Faint blue outline: 1px solid #4285F4, opacity 0.2
  ├─ On hover:
  │   ├─ Outline becomes solid: 2px solid #4285F4
  │   ├─ Edit icon (pencil) appears top-right: 20–24px, prominent
  │   ├─ Icon background: blue circle (#4285F4) with white pencil
  │   ├─ Cursor changes to pointer
  │   └─ Tooltip shows: "Click to edit"
  └─ Public visitors: no outline or icon visible
```

**Rationale:**
- Faint outline makes regions discoverable without cluttering UI
- Solid outline on hover confirms click target
- Edit icon is large and unmissable (20–24px is prominent without being intrusive)
- Blue circle background ensures visibility even if content region has colored background
- Tooltip provides additional confirmation

### Activation Interaction

**On click:**
1. Content region outline becomes darker (#1976D2)
2. Toolbar appears above content
3. Quill editor initializes with focus
4. Previous page scroll position saved (for discard)

---

## Toolbar Design

### Toolbar Layout

**Single row (desktop):**
```
┌──────────────────────────────────────────────────────┐
│ [B I U] [Link] [Header ▼] [Bullets] [Quote]         │
├──────────────────────────────────────────────────────┤
│ Status: Saved ✓                 [Save Draft] [Discard]│
└──────────────────────────────────────────────────────┘
```

**Stacked (mobile, <768px):**
```
┌────────────────────┐
│ [B I U] [Link]     │
│ [Header ▼] [Bullets]│
│ [Quote]            │
├────────────────────┤
│ Status: Saved ✓    │
├────────────────────┤
│ [Save Draft]       │
│ [Discard]          │
└────────────────────┘
```

### Button Styling

**Format buttons (Bold, Italic, etc.):**
- Appearance: Icon buttons, light background
- Active state (format applied to selected text): blue background
- Hover state: slightly darker gray background
- Size: 32px × 32px (touch-friendly)

**Save Draft button:**
- Primary action (blue background #4285F4, white text)
- Size: 120px × 40px
- State: enabled (blue), saving (blue with spinner), saved (green checkmark + "Saved")
- High contrast, always visible

**Discard button:**
- Secondary action (dark gray background #5F5E5A, white text)
- Size: 100px × 40px
- Disabled if no changes made
- Dark styling ensures visibility (avoids white-on-white blending)

**Status indicator:**
- Positioned in action bar (bottom of overlay)
- Text + color: "Saved ✓" (green #2E7D32), "Saving…" (orange #F57C00), "Error: [msg]" (red #C62828)
- Aria-live region for screen reader announcements

**Action bar placement:**
- Separate section at bottom of overlay
- Floats above content (sticky positioning)
- Always visible even if content is tall
- Clear visual separation from toolbar above

---

## Content States & Interactions

### State 1: Inactive (Page Loaded)

**Visual:**
- Rendered page displays normally
- Content regions have faint blue outlines
- Edit icons hidden (appear on hover)
- Toolbar is not visible
- Page is fully interactive (no overlay)

**User action:** Hover over content region

---

### State 2: Hover (User Hovering)

**Visual:**
- Outline becomes solid and darker (#1976D2)
- Edit icon appears (pencil in top-left corner)
- Cursor changes to pointer
- Tooltip: "Click to edit"

**User action:** Click on content region

---

### State 3: Active (Editing)

**Visual:**
- Overlay appears with semi-transparent white background
- Toolbar is fully visible
- Quill editor active (cursor blinking in content)
- Save/Discard buttons highlighted
- Status indicator shows "Unsaved" (if changes made)

**Interactions:**
- User types, applies formatting
- Toolbar buttons respond to selection
- Save Draft button is clickable
- Discard button is clickable
- Escape key closes without saving (with dirty check)

**User action:** Click "Save Draft" or make changes → click "Save"

---

### State 4: Saving

**Visual:**
- Save Draft button shows spinner: "Saving…"
- Button is disabled (grayed out)
- Status region shows: "Saving…"
- Editor remains active (user can keep typing)

**Timeline:** Typical save = 200–500ms

**User action:** Server responds with success or error

---

### State 5: Saved

**Visual:**
- Save Draft button shows checkmark: "✓ Saved"
- Status region shows: "Saved" (green text)
- After 2 seconds, button returns to normal "Save Draft" state
- Overlay closes automatically
- Rendered content updates with new HTML

**Page indicator:** Page header shows "Draft changes saved" (if there are unsaved layout changes)

**User action:** Click on another region to edit, or navigate away

---

### State 6: Error

**Visual:**
- Status region shows: "Error: Server timeout. Try again?" (red text)
- Save Draft button remains active (user can retry)
- Overlay stays open (user doesn't lose work)

**User action:** Click "Save Draft" again, or "Discard" to abandon changes

---

## Mockup Layouts

Below are ASCII mockups for each test page with Option A (Fixed Toolbar) applied:

### Test Page 1: News Article with Fixed Toolbar Overlay

```
┌─────────────────────────────────────────┐
│ SimIS CMS                 [Nav links]   │
└─────────────────────────────────────────┘

New Feature Announcement
Published by Jane Doe on July 26, 2026

[Floating Toolbar]
┌────────────────────────────────────────────┐
│ [B I U Link] [H1 ▼] [Bullets] │ Saved ✓   │
│ [Save Draft] [Discard]                     │
└────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                      [Quill Editor]                          │
│                                                              │
│ We're excited to announce the launch of Edit-on-Page, our  │
│ new in-place content editing feature. With this release,   │
│ you can now edit page content directly without leaving the │
│ page.                                                        │
│                                                              │
│ Key benefits:                                               │
│ • Faster content updates                                    │
│ • Live preview of changes                                   │
│ • No context switching                                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘

[Sidebar - Visible]
┌──────────────┐
│ Related      │
│ Articles     │
│              │
│ • Article 1  │
│ • Article 2  │
│ • Article 3  │
└──────────────┘

┌─────────────────────────────────────────┐
│ Comments (0)                            │
└─────────────────────────────────────────┘
```

**Observation:** Toolbar doesn't obscure headline. Sidebar remains visible. Content area is clear for editing.

---

### Test Page 2: Multi-Widget Showcase with Fixed Toolbar Overlay

```
┌─────────────────────────────────────────┐
│ SimIS CMS                 [Nav links]   │
└─────────────────────────────────────────┘

Featured Products

[Floating Toolbar]
┌────────────────────────────────────────────┐
│ [B I U Link] [H1 ▼] [Bullets] │ Saved ✓   │
│ [Save Draft] [Discard]                     │
└────────────────────────────────────────────┘

[Quill Editor - focused on product title]
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│ Our Latest Innovation: Product Title Here                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘

[Other widgets still visible for reference]
┌──────────────────────────────────────────────────────────────┐
│ Product Grid (visible, not editable in this overlay)        │
│ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐            │
│ │ Product 1   │ │ Product 2   │ │ Product 3   │            │
│ │ [image]     │ │ [image]     │ │ [image]     │            │
│ │ $99         │ │ $149        │ │ $199        │            │
│ └─────────────┘ └─────────────┘ └─────────────┘            │
└──────────────────────────────────────────────────────────────┘
```

**Observation:** Toolbar targets one editable region (product title). Grid remains visible below. No horizontal scrolling. Editor is focused on the right content.

---

### Test Page 3: Simple Static Page with Fixed Toolbar Overlay

```
┌─────────────────────────────────────────┐
│ SimIS CMS                 [Nav links]   │
└─────────────────────────────────────────┘

About SimIS

[Floating Toolbar]
┌────────────────────────────────────────────┐
│ [B I U Link] [H1 ▼] [Bullets] │ Saved ✓   │
│ [Save Draft] [Discard]                     │
└────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                      [Quill Editor]                          │
│                                                              │
│ SimIS is a modern CMS built for organizations that need    │
│ secure, compliant content management without complexity.    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Observation:** Even with minimal content, toolbar placement feels natural. Proportions are balanced. No wasted space.

---

## Mobile Responsiveness

### Mobile (< 768px)

**Toolbar stacks vertically:**
```
┌────────────────┐
│ [B] [I] [U]    │
│ [Link] [H1 ▼]  │
│ [Bullets]      │
├────────────────┤
│ Status: Saved  │
├────────────────┤
│ [Save Draft]   │
│ [Discard]      │
└────────────────┘

[Editor content - full width]
┌────────────────┐
│ Page title     │
│                │
│ Content text   │
│ can wrap and   │
│ remain readable│
└────────────────┘
```

**Rationale:** Single-column layout on mobile. Toolbar buttons stack. Touch-friendly sizing (40px+ button heights).

---

## Accessibility Considerations

### Keyboard Navigation

**Focus order:**
1. Save Draft button (primary action)
2. Discard button (secondary)
3. Toolbar buttons (format, link, etc.)
4. Quill editor (content area)
5. Status region (announced via aria-live)

**Shortcuts:**
- `Escape` — Close overlay without saving (with dirty check)
- `Ctrl+S` / `Cmd+S` — Save Draft
- `Tab` — Navigate through toolbar buttons and editor

### Screen Reader Announcements

**Status region (aria-live="polite"):**
- On save start: "Saving your changes…"
- On save success: "Changes saved successfully"
- On error: "Error: Failed to save. Please try again."

**Toolbar buttons:**
- Each format button has aria-label: "Toggle bold" / "Toggle italic" etc.
- Status indicator has aria-label with current state

**Content regions:**
- Each editable region has aria-label: "Edit [content name]"
- Edit icon has role="button" and aria-describedby

### Color Contrast

**Minimum WCAG AA (4.5:1 for text, 3:1 for UI):**
- Toolbar background: #FFFFFF (white)
- Toolbar text: #212121 (dark gray)
- Active button background: #1976D2 (blue)
- Active button text: #FFFFFF (white)
- Outline color (focus): #4285F4 (brighter blue)
- Status text (saved): #2E7D32 (green)
- Status text (error): #C62828 (red)

**Tested in:** WAVE, Contrast Checker, axe DevTools

---

## Testing Protocol

### Usability Test Setup

**Participants:** 2–3 content editors (from real user base)

**Duration:** ~30 minutes per session

**Environment:** Staging site with test content, or local dev build

**Tasks:**
1. Find the "About" page (simple, single content region)
2. Edit the introductory paragraph
3. Apply formatting (bold, italic, link)
4. Save the changes
5. Navigate to another page (multi-widget)
6. Edit a product title in the widget
7. Encounter a simulated error (optional) and retry saving
8. Discard changes without saving

### Metrics to Capture

**Quantitative:**
- Time to activate editor (click to overlay appearance)
- Time to save (click Save to server response)
- Error recovery time (if applicable)
- Number of clicks to find edit affordance

**Qualitative (via observation or post-test interview):**
- Was the edit affordance obvious?
- Did the overlay obscure important content?
- Were Save/Discard buttons clear?
- Any confusion about toolbar placement?
- Any issues on mobile or narrow screens?
- Feeling of "smoothness" or "friction"?

### Success Criteria

- ✓ Participants can activate editor in < 3 seconds
- ✓ No participant is confused about where to click
- ✓ Toolbar placement doesn't obscure headline or critical content
- ✓ Save/Discard interaction is obvious (no misclicks)
- ✓ Error recovery doesn't require page reload
- ✓ Mobile toolbar is usable (buttons clickable, not crowded)

---

## Iteration Plan

### Based on Feedback

**If toolbar is too large on mobile:**
→ Implement collapsible toolbar (icon → dropdown menu)

**If floating toolbar Option B is strongly preferred:**
→ Prototype floating version and re-test

**If edit affordance is hard to find:**
→ Add persistent "Edit" button near each content region (instead of hover-only)

**If Save/Discard buttons are confusing:**
→ Change to "Save Draft" (primary) / "Cancel" (secondary)

**If overlay doesn't feel "native" enough:**
→ Test inline editing (expand-in-place) as alternative

---

## Next Steps (After P2.0 Feedback)

1. **Incorporate feedback** into design doc (`visual-editor-p2-design.md`)
2. **Get stakeholder sign-off** on final design
3. **Create detailed component specification** for developers
4. **Begin P2.1 implementation** (OverlayEditorPane component)

---

## Stakeholder Sign-Off

**Design approved by:**
- [ ] Product Manager / Strategy
- [ ] Content Editor (representative user)
- [ ] Accessibility Lead
- [ ] Engineering Lead

**Date approved:** _______________

**Comments/Feedback:**
```
[Space for reviewer notes]
```

---

## References

- [Quill 2.x Editor](https://quilljs.com/)
- [WCAG 2.1 AA Compliance](https://www.w3.org/WAI/WCAG21/quickref/)
- [Mobile-First Design Principles](https://www.nngroup.com/articles/mobile-first-design/)
- [Accessible Modals and Overlays](https://www.w3.org/WAI/ARIA/apg/patterns/dialogmodal/)

