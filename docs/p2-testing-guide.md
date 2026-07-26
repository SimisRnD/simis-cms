# P2: Edit-on-Page Testing Guide

**Purpose:** Validate overlay editor functionality before launch  
**Timeline:** 1–2 hours for full manual testing  
**Audience:** QA, content editors, dev team

---

## Pre-Flight Checklist

Before testing, verify the build is clean:

```bash
# In ~/dev/simis-cms directory
cd ~/dev/simis-cms

# Clean build
ant clean

# Full build
ant build

# Check for errors in stdout
```

### File Verification

Confirm all files are in place:

```bash
# Check JavaScript
ls -la src/main/webapp/javascript/overlay-editor-pane.js
# Expected: 400 LOC, ~15KB

# Check CSS  
ls -la src/main/webapp/css/overlay-editor-pane.css
# Expected: 300 LOC, ~8KB

# Check JSP modifications
grep "data-editor-content" src/main/webapp/WEB-INF/jsp/cms/content.jsp
# Expected: attribute added to platform-content div

grep "overlay-editor-pane.css" src/main/webapp/WEB-INF/jsp/main.jsp
# Expected: link tag injected

grep "overlay-editor-pane.js" src/main/webapp/WEB-INF/jsp/main.jsp
# Expected: script tag injected
```

### Browser Requirements

- Chrome 90+ (primary test browser)
- Firefox 88+ (secondary)
- Safari 14+ (if available)
- Edge 90+ (if available)

---

## Manual Testing Scenarios

### Scenario 1: Page Load & Affordances

**Goal:** Verify overlay pane initializes and shows edit affordances

**Steps:**

1. Start the application server
   ```bash
   # If using Tomcat
   cd $TOMCAT_HOME/bin
   ./catalina.sh run
   
   # Or via your normal deployment method
   ```

2. Open a page that renders content in edit mode
   - Navigate to: `http://localhost:8080/admin/content-editor` (or your CMS admin page with content)
   - Look for a content region (paragraph, heading, etc.)
   - Ensure you're logged in as an editor

3. **Hover over content region**
   - Expected:
     - Faint blue outline appears (1px, barely visible)
     - Edit icon appears (blue circle with white pencil, 20–24px)
     - Cursor changes to pointer
   - If NOT visible:
     - Check browser console for errors (F12 → Console)
     - Verify `pageEditMode=true` in page source (Ctrl+F "pageEditMode")
     - Verify `data-editor-content` attribute exists on div (Ctrl+F "data-editor-content")

4. **Move cursor away**
   - Expected: Affordances fade (outline returns to faint, icon disappears)

---

### Scenario 2: Overlay Activation & Quill

**Goal:** Verify overlay shows and Quill editor initializes

**Steps:**

1. **Click on a content region** to activate
   - Expected:
     - Overlay appears with semi-transparent backdrop
     - Toolbar visible with format buttons (B, I, U, Link, Header, Bullets, Quote)
     - Quill editor initialized with content
     - "Save Draft" and "Discard" buttons visible
     - Status shows "Ready to edit" or "Unsaved changes"

2. **Verify Quill is ready**
   - Click in editor area
   - Type some text
   - Expected:
     - Text appears in editor
     - Cursor blinks normally
     - Status changes to "Unsaved changes"

3. **If overlay doesn't appear:**
   - Check browser console (F12 → Console) for JS errors
   - Look for: `OverlayEditorPane initialized` message
   - Verify Quill is loaded: `window.Quill` in console should exist
   - Check if formToken is available: `document.querySelector('[name="formToken"]')` should return an input

---

### Scenario 3: Formatting & Editing

**Goal:** Verify rich text formatting works

**Steps:**

1. **In the editor, select some text and apply formatting:**
   - Select word → Click "B" button → Text should become bold
   - Select word → Click "I" button → Text should become italic
   - Select word → Click "U" button → Text should underline

2. **Test link insertion:**
   - Click "Link" button
   - Prompt appears: "Enter link URL:"
   - Type: `https://example.com`
   - Click "OK"
   - Expected: Link inserted or selected text becomes a link

3. **Test headers:**
   - Click "Header" button
   - Expected: Current line becomes H2 header

4. **Test bullets:**
   - Click "Bullets" button
   - Type several items
   - Expected: Bullet list created

5. **Status should show: "Unsaved changes"**

---

### Scenario 4: Save Draft

**Goal:** Verify content saves to server and persists

**Steps:**

1. **Edit some content** (at least a few words)
   - Status should show "Unsaved changes"

2. **Click "Save Draft" button**
   - Expected:
     - Button text changes to "Saving…"
     - Status shows "Saving your changes…"
     - After 1–2 seconds, button shows "✓ Saved"
     - Status shows "Saved successfully ✓"

3. **Overlay should close** after 1 second

4. **Page updates with new content**
   - The rendered text on the page should reflect your edits
   - No page reload should occur

5. **Verify in browser network tab (F12 → Network):**
   - POST request to `?action=saveWidgetContent` should have:
     - Status 200 (success)
     - Response: `{"success":true,"html":"..."}`

---

### Scenario 5: Discard

**Goal:** Verify changes can be abandoned without saving

**Steps:**

1. **Edit some content** (change a word or two)
   - Status shows "Unsaved changes"

2. **Click "Discard" button**
   - Confirm dialog: "Discard unsaved changes?"
   - Click "Cancel" → Editor stays open
   - Click "OK" → Overlay closes

3. **After discard, verify:**
   - Overlay closed
   - Page shows original content (your edits are gone)
   - Nothing was saved to server

4. **Check network tab:**
   - No POST request should have been made

---

### Scenario 6: Error Handling

**Goal:** Verify graceful error recovery

**Steps:**

1. **Simulate a network error (Chrome DevTools):**
   - F12 → Network tab
   - Click "Offline" or throttle to "No Internet"
   - Edit some content
   - Click "Save Draft"

2. **Expected:**
   - Status shows error: "Error: Failed to fetch"
   - "Save Draft" button remains enabled
   - Overlay stays open (user doesn't lose work)
   - Editor still has the text

3. **Recovery:**
   - Go back online (DevTools → back to "Online")
   - Click "Save Draft" again
   - Expected: Saves successfully

4. **Verify in Network tab:**
   - First request fails (red, or status error)
   - Second request succeeds (status 200)

---

### Scenario 7: Keyboard Shortcuts

**Goal:** Verify keyboard navigation works

**Steps:**

1. **While overlay is open:**
   - Press `Escape` key
   - Confirm dialog: "Discard unsaved changes?"
   - Click "OK"
   - Expected: Overlay closes

2. **Edit something, then:**
   - Press `Ctrl+S` (Windows) or `Cmd+S` (Mac)
   - Expected: "Save Draft" action triggers (button shows "Saving…")
   - Content saves

3. **Keyboard focus:**
   - Press `Tab` to navigate through toolbar buttons
   - Each button should highlight with a focus ring
   - Press `Enter` on a button to activate it

---

### Scenario 8: Multiple Content Regions

**Goal:** Verify switching between editable regions

**Steps:**

1. **Load a page with multiple content regions** (if available)
   - Example: Page with title + subtitle + body paragraphs

2. **Click first region → Edit → "Unsaved changes"**

3. **Click different region**
   - Confirm dialog: "You have unsaved changes. Discard them?"
   - Click "OK"
   - First region closes, second region opens
   - First region reverts to original content

4. **Edit second region and save**
   - Expected: Only second region updates

---

### Scenario 9: Permission Checks

**Goal:** Verify non-editors can't edit

**Steps:**

1. **Log out, then log in as non-editor user** (if available)
   - Viewer role, Subscriber, etc.

2. **Load a page with content**
   - Expected:
     - No edit affordances (no outline, no icon)
     - Overlay doesn't activate on click
     - Edit button in header not visible

3. **Try accessing via URL directly:**
   - Manually type: `?action=getWidgetContent&uniqueId=X`
   - Expected: 400 or 403 error (or redirected to login)

---

### Scenario 10: Dark Mode (if enabled)

**Goal:** Verify styling in dark mode

**Steps:**

1. **Enable dark mode** (if site supports it)
   - Settings → Dark mode toggle

2. **Load page with content**
   - Hover over content → Affordances should be visible
   - Click to edit → Overlay should be readable
   - Toolbar buttons should contrast well
   - Text should be legible

3. **Verify:**
   - No white-on-white blending (buttons visible)
   - Dark text readable on dark backgrounds
   - Focus rings visible (blue outline)

---

## Success Criteria

✅ **All scenarios pass if:**

| Scenario | Pass Condition |
|----------|----------------|
| 1. Affordances | Edit icon and outline visible on hover |
| 2. Overlay | Quill initializes, toolbar shows, status accurate |
| 3. Formatting | Bold, italic, link, headers, lists all work |
| 4. Save | Content persists, page updates, no reload |
| 5. Discard | Changes abandoned, original content restored |
| 6. Errors | Network errors handled gracefully, retry works |
| 7. Keyboard | Escape closes, Ctrl+S saves, Tab navigates |
| 8. Multiple | Can switch regions without data loss |
| 9. Permissions | Non-editors see no affordances, API rejects |
| 10. Dark Mode | Readable, no contrast issues |

---

## Known Issues & Workarounds

### Issue: "Overlay doesn't appear on click"

**Possible causes:**
1. JavaScript not loaded
   - Fix: Hard refresh page (Ctrl+Shift+R or Cmd+Shift+R)
   - Check console for errors

2. `pageEditMode` not set
   - Fix: Ensure you're on a page with edit affordances
   - Check page source: `pageEditMode=true`

3. `data-editor-content` attribute missing
   - Fix: Verify content.jsp was modified correctly
   - Rebuild and redeploy

### Issue: "Save fails with 403 Forbidden"

**Possible causes:**
1. CSRF token mismatch
   - Fix: Verify formToken is in page
   - Check console: `document.querySelector('[name="formToken"]').value`

2. Permission denied
   - Fix: Verify logged-in user has editor role
   - Check UserSession in browser storage

### Issue: "Content doesn't update after save"

**Possible causes:**
1. Server returned success but HTML is empty
   - Fix: Check server logs for DeltaContentCommand errors
   - Verify Delta is valid JSON

2. Browser didn't parse response
   - Fix: Check Network tab response body
   - Look for error message in JSON

### Issue: "Quill editor not working (no cursor, no typing)"

**Possible causes:**
1. Quill library not loaded
   - Fix: Verify quill-2.0.3-snow.css and quill.js are loaded
   - Check Network tab for 200 status on both

2. Quill initialized on wrong element
   - Fix: Check console: `window.overlayEditor.state.quill`
   - Should show a Quill instance

---

## Browser Console Debugging

**Check these in console (F12 → Console):**

```javascript
// Verify component initialized
window.overlayEditor
// Should log: OverlayEditorPane initialized

// Verify Quill is available
window.Quill
// Should show Quill class definition

// Verify CSRF token
document.querySelector('[name="formToken"]').value
// Should show a token string (not empty)

// Check active state
window.overlayEditor.state.active
// Should be true when overlay is open, false when closed

// Check dirty state
window.overlayEditor.state.dirty
// Should be true if you've made edits

// Manually trigger save (for debugging)
window.overlayEditor.save()
// Check Network tab for POST request
```

---

## Test Report Template

After testing, document your findings:

```
# P2 Testing Report
Date: ______
Tester: ____
Browser: ____ (version ____)

## Pass/Fail Summary
- Scenario 1 (Affordances): [PASS/FAIL]
- Scenario 2 (Overlay): [PASS/FAIL]
- Scenario 3 (Formatting): [PASS/FAIL]
- Scenario 4 (Save): [PASS/FAIL]
- Scenario 5 (Discard): [PASS/FAIL]
- Scenario 6 (Errors): [PASS/FAIL]
- Scenario 7 (Keyboard): [PASS/FAIL]
- Scenario 8 (Multiple): [PASS/FAIL]
- Scenario 9 (Permissions): [PASS/FAIL]
- Scenario 10 (Dark Mode): [PASS/FAIL]

## Issues Found
[List any failures or unexpected behavior]

## Comments
[Any observations, UX feedback, performance notes]
```

---

## Next Steps

1. **Run through all 10 scenarios**
2. **Document any issues** in the test report
3. **Check browser console** for errors
4. **Review Network tab** to verify requests
5. **If failures:** Open GitHub issues or note fixes needed
6. **If all pass:** Ready for P2.6 (code review & launch)

---

## Questions During Testing?

If you hit issues, check:
1. Browser console (F12 → Console) for errors
2. Network tab (F12 → Network) for failed requests
3. Server logs (`logs/app.log` or Tomcat logs)
4. This guide's "Known Issues" section

Good luck! 🚀
