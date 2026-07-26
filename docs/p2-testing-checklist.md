# P2 Testing Checklist (Quick Reference)

Print this out or use it as a quick guide while testing.

---

## Pre-Flight

- [ ] Build is clean (`ant clean && ant build`)
- [ ] No build errors in stdout
- [ ] overlay-editor-pane.js exists (400 LOC)
- [ ] overlay-editor-pane.css exists (300 LOC)
- [ ] Server is running
- [ ] Logged in as editor/admin

---

## Scenario 1: Affordances
- [ ] Hover over content → outline appears (faint blue, 1px)
- [ ] Edit icon appears (blue circle, white pencil, ~20px)
- [ ] Cursor changes to pointer
- [ ] Move away → affordances fade

**Console check:** No errors in F12 → Console

---

## Scenario 2: Overlay Activation
- [ ] Click content → overlay appears (semi-transparent backdrop)
- [ ] Toolbar visible (B, I, U, Link, H, Bullets, Quote buttons)
- [ ] Quill editor initialized with content
- [ ] "Save Draft" button visible (blue)
- [ ] "Discard" button visible (dark gray)
- [ ] Status shows message

**Console check:** Type `window.overlayEditor.state.active` → should be `true`

---

## Scenario 3: Formatting
- [ ] Type text → appears in editor
- [ ] Select text → click B → bold applied
- [ ] Select text → click I → italic applied
- [ ] Select text → click U → underline applied
- [ ] Click Link → prompt → enter URL → link inserted
- [ ] Click Header → line becomes heading
- [ ] Click Bullets → bullet list created
- [ ] Status shows "Unsaved changes"

---

## Scenario 4: Save Draft
- [ ] Edit content
- [ ] Click "Save Draft"
- [ ] Button shows "Saving…"
- [ ] Status shows "Saving your changes…"
- [ ] After 1–2 seconds: button shows "✓ Saved"
- [ ] Status shows "Saved successfully ✓"
- [ ] Overlay closes
- [ ] Page updates with new content (no reload)

**Network check (F12 → Network):**
- [ ] POST request to `?action=saveWidgetContent`
- [ ] Status: 200
- [ ] Response: `{"success":true,"html":"..."}`

---

## Scenario 5: Discard
- [ ] Edit content → status shows "Unsaved changes"
- [ ] Click "Discard"
- [ ] Confirm dialog appears
- [ ] Click "OK"
- [ ] Overlay closes
- [ ] Page shows original content (edits gone)
- [ ] No POST request in Network tab

---

## Scenario 6: Error Handling
- [ ] Turn on DevTools offline mode (F12 → Network → Offline)
- [ ] Edit and click "Save Draft"
- [ ] Status shows error message
- [ ] "Save Draft" button still enabled
- [ ] Overlay still open (content preserved)
- [ ] Go back online (DevTools → Online)
- [ ] Click "Save Draft" again
- [ ] Saves successfully

**Network check:**
- [ ] First POST fails (red or error)
- [ ] Second POST succeeds (200)

---

## Scenario 7: Keyboard Shortcuts
- [ ] Overlay open → press Escape → closes with confirm dialog
- [ ] Edit content → press Ctrl+S (or Cmd+S) → "Saving…" triggers
- [ ] Press Tab → focus highlights each toolbar button
- [ ] Press Enter on toolbar button → action triggers

---

## Scenario 8: Multiple Regions (if available)
- [ ] Click first region → Edit → status "Unsaved changes"
- [ ] Click different region → confirm dialog
- [ ] First region closes (reverts to original)
- [ ] Second region opens
- [ ] Edit second region and save successfully

---

## Scenario 9: Permissions
- [ ] Log out, log in as non-editor (if available)
- [ ] Load page with content
- [ ] Hover over content → NO affordances visible
- [ ] Click on content → overlay does NOT appear
- [ ] Edit button not visible in header

---

## Scenario 10: Dark Mode (if enabled)
- [ ] Enable dark mode
- [ ] Load page with content
- [ ] Hover → affordances visible (good contrast)
- [ ] Click → overlay readable
- [ ] Toolbar buttons visible (no white-on-white blending)
- [ ] Text legible
- [ ] Focus rings visible (blue outline)

---

## Issues Found

| Scenario | Issue | Notes |
|----------|-------|-------|
| | | |
| | | |
| | | |

---

## Test Result

**Overall Status:** [ ] PASS [ ] FAIL

**Date:** ___________  
**Tester:** ___________  
**Browser:** ___________ (version: ___)

**Notes:**
```




```

---

## Quick Debug Commands (F12 → Console)

```javascript
// Verify component loaded
window.overlayEditor

// Check if overlay is active
window.overlayEditor.state.active

// Check if content is dirty
window.overlayEditor.state.dirty

// Check CSRF token
document.querySelector('[name="formToken"]').value

// Verify Quill loaded
window.Quill
```

---

## If You Get Stuck

1. **Refresh page:** Ctrl+Shift+R (hard refresh)
2. **Check console:** F12 → Console → look for red errors
3. **Check Network:** F12 → Network → look for failed requests
4. **Check server logs:** `logs/app.log` or Tomcat logs
5. **Refer to Testing Guide:** `docs/p2-testing-guide.md`

---

**Ready to test!** 🚀
