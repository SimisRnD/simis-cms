# P4 & P5 Execution Plan — 3-Person Team (Elizabeth, Jordan, Claude)

**Date:** July 26, 2026  
**Team:** Elizabeth (dev), Jordan (dev), Claude (AI support & coordination)  
**Storage:** Azure (prod), Local disk (dev)  
**Stakeholders:** Approved ✅  
**Timeline Goal:** 12–15 weeks (option B parallel, adjusted for 3-person team)

---

## Team Structure & Responsibilities

### Elizabeth (Primary Developer)
- **Owner:** P4 (Real-Time Preview & Undo/Redo)
- **Support:** P5.2 (Image insertion) — shared with Jordan
- **Timeline:** Weeks 1–8 (P4 MVP), then Weeks 9–12 (P5.2)

### Jordan (Primary Developer)
- **Owner:** P5.1 (Media Library) + P5.3 (Publishing Workflow)
- **Support:** P5.2 (Image insertion) — shared with Elizabeth
- **Timeline:** Weeks 1–8 (P5.1 + P5.3 MVP), then Weeks 9–12 (P5.2)

### Claude (AI Support)
- **Roles:** Code generation, test writing, documentation, design guidance
- **Availability:** On-demand (realtime support during implementation)
- **Key responsibilities:**
  - Generate component skeleton code (forms, panels, utilities)
  - Write unit tests for logic (not UI)
  - Create API endpoint stubs
  - Answer architectural questions
  - Document decisions
  - Code review via pair programming

---

## Phase Breakdown (Realistic for 3 People)

### Phase 1: Foundation & Parallel Setup (Weeks 1–2)

**Elizabeth:**
- [ ] Design: Answer P4 design questions (preview layout, performance targets)
- [ ] Setup: Create overlay-preview-pane component skeleton
- [ ] Research: Test Delta renderer parity (client vs server)

**Jordan:**
- [ ] Design: Answer P5 design questions (media library structure, upload flow)
- [ ] Setup: Design media_assets database schema (with Claude)
- [ ] Setup: Create media upload endpoint skeleton

**Claude:**
- [ ] Generate: Component boilerplate (Elizabeth's preview pane, Jordan's media panel)
- [ ] Generate: Database migration SQL (media schema)
- [ ] Generate: API endpoint stubs (upload, list, delete)
- [ ] Document: Architectural decisions log

**Deliverables:** Design decisions locked in, code skeletons ready, team aligned

---

### Phase 2: P4 MVP — Elizabeth (Weeks 3–8)

**P4.1: Split-Pane Preview (Elizabeth)**

**Weeks 3–4:**
- [ ] Implement client-side Delta→HTML renderer (copy logic from `DeltaContentCommand.java`)
- [ ] Integrate into overlay (add preview pane next to editor)
- [ ] Render updates on text-change event
- **Deliverables:** Preview pane shows real-time rendering (no styling yet)

**Weeks 5–6:**
- [ ] Styling: Match server's CSS for preview content
- [ ] Responsive: Side-by-side layout, toggle fullscreen
- [ ] Performance: Debounce updates (300ms), profile with 100KB docs
- **Deliverables:** Production-ready preview pane

**P4.2: Undo/Redo (Elizabeth)**

**Weeks 7–8:**
- [ ] Enable Quill history module (built-in)
- [ ] Add keyboard shortcuts (Ctrl+Z, Ctrl+Shift+Z)
- [ ] Add toolbar buttons (Undo/Redo icons)
- [ ] Test: Multi-level undo, redo stack limits
- **Deliverables:** Full undo/redo with shortcuts + buttons

**Claude Support (Weeks 3–8):**
- [ ] Generate: Unit tests for renderer (test Delta parity)
- [ ] Generate: Keyboard shortcut tests
- [ ] Generate: CSS for preview pane
- [ ] Pair: Help with renderer parity debugging if needed

**Elizabeth Deliverables at Week 8:**
- ✅ Live preview pane (side-by-side, fullscreen toggle)
- ✅ Undo/Redo working with shortcuts & buttons
- ✅ Performance: preview latency < 100ms
- ✅ Tests: Renderer parity verified, undo/redo tested

---

### Phase 2: P5 MVP — Jordan (Weeks 3–8)

**P5.1: Media Library Panel (Jordan)**

**Weeks 3–5:**
- [ ] Database: Create media_assets schema (with Claude)
- [ ] Backend: Upload endpoint (`POST /api/media/upload`)
  - Validate file size (50MB limit)
  - Validate MIME types (images + PDF)
  - Store on Azure Blob Storage
  - Return asset metadata
- [ ] Backend: List endpoint (`GET /api/media/list`)
  - Filter by type, date, size
  - Search by filename
  - Pagination (50 per page)
- **Deliverables:** API endpoints complete

**Weeks 6–8:**
- [ ] Frontend: Media panel component (sidebar in overlay)
  - File browser (list view)
  - Upload drag-drop zone
  - Search/filter
  - Delete button
- [ ] UX: Error handling (upload failures, quota)
- [ ] Tests: Upload validation, file size limits
- **Deliverables:** Media library fully functional

**P5.3: Publishing Workflow Improvements (Jordan, Weeks 7–8)**

**Weeks 7–8 (parallel with P5.1 finish):**
- [ ] UI: Draft indicator badge (visual cue)
- [ ] UI: Submit for Review button
- [ ] UI: Publish to Live button
- [ ] UX: Show submission status (pending, approved, published)
- **Deliverables:** Publishing UI polished

**Claude Support (Weeks 3–8):**
- [ ] Generate: Database schema migrations
- [ ] Generate: Upload API endpoint skeleton
- [ ] Generate: List API endpoint skeleton
- [ ] Generate: Media panel React component (or equivalent)
- [ ] Generate: Upload validation tests
- [ ] Pair: Debug Azure Blob Storage integration if needed

**Jordan Deliverables at Week 8:**
- ✅ Media library fully functional (upload, browse, delete)
- ✅ Publishing UI refreshed (badges, status display)
- ✅ Azure storage integration working
- ✅ Tests: Upload validation, file limits

---

### Phase 3: Convergence — P5.2 Image Insertion (Weeks 9–12)

**Both Elizabeth & Jordan work together**

**Weeks 9–10:**
- [ ] Integrate media library into editor
  - Click "Insert Media" in toolbar
  - Opens Jordan's media library panel
  - Select image → inserts into editor
- [ ] Quill Image/Video modules configured
- [ ] Preview updates showing embedded media (from Elizabeth's P4)
- **Deliverables:** Image insertion working end-to-end

**Weeks 11–12:**
- [ ] Image metadata (alt text, caption)
- [ ] Image optimization (auto-resize large uploads)
- [ ] Tests: Integration tests (upload → insert → preview → save)
- **Deliverables:** P5.2 complete and tested

**Claude Support (Weeks 9–12):**
- [ ] Generate: Image insertion component
- [ ] Generate: Alt text/caption UI
- [ ] Generate: Image optimization utilities
- [ ] Generate: End-to-end integration tests
- [ ] Pair: Debug preview rendering with embedded images

**Elizabeth & Jordan Deliverables at Week 12:**
- ✅ Full image insertion workflow (library → editor → preview → save)
- ✅ Image metadata complete
- ✅ All tests passing
- ✅ Ready for launch

---

## Deferred (Not in MVP)

- **P5.4 Asset Dashboard** — Admin panel for all media files (defer to P6)
- **P5.3 Full Versioning** — Rollback UI (defer if time permits in Week 12)
- **Image Optimization Advanced** — WebP conversion, thumbnails (defer to P6)
- **Multi-Reviewer Publishing** — Keep single-reviewer for now

---

## Weekly Sync Schedule

**Mondays 10am:** Team standup (15 min)
- Elizabeth: What you built, what you're building, blockers
- Jordan: What you built, what you're building, blockers
- Claude: Support priorities, design questions, dependencies

**Wednesdays 2pm:** Design/Architecture (30 min)
- Review decisions, discuss open questions
- Claude pair-programs on blocking issues

**Fridays 4pm:** Demo & Retrospective (30 min)
- Show working features
- Discuss learnings, adjust plan if needed

---

## Week-by-Week Milestones

| Week | Elizabeth | Jordan | Claude | Status |
|------|-----------|--------|--------|--------|
| 1–2 | P4 design + setup | P5 design + setup | Scaffolding code | Foundation |
| 3–4 | P4.1 preview renderer | P5.1 upload endpoint | Test generation | Feature dev |
| 5–6 | P4.1 styling & perf | P5.1 UI, P5.3 start | Support pair | Converging |
| 7–8 | P4.2 undo/redo | P5.1/P5.3 finish | Test coverage | Phase 1 done |
| 9–10 | P5.2 setup, preview | P5.2 library panel | Integration setup | Phase 2 start |
| 11–12 | P5.2 metadata, tests | P5.2 tests | Polish, docs | MVP done |
| 13–15 | Buffer/polish/docs | Buffer/polish/docs | Launch prep | Optional |

---

## Tech Decisions (Locked In)

| Decision | Choice | Why |
|----------|--------|-----|
| Storage | Azure Blob Storage (prod), local disk (dev) | Stakeholder approved, cloud-scale |
| Preview layout | Side-by-side with resizable divider | Standard UX, works on desktop |
| Undo depth | 50 edits (Quill default) | Balance memory vs usability |
| Image limits | 50MB, images + PDF only | Safe defaults, can adjust later |
| Publishing | Single reviewer (keep P2 model) | MVP; multi-reviewer in future |

---

## Open Design Questions (Answer This Week)

### P4 (Elizabeth)
1. Preview side-by-side or below editor?
2. Should fullscreen preview toggle exist?
3. What's target latency (< 100ms, < 300ms)?
4. Undo button disabled when stack empty? (yes/no/icon-gray)

### P5 (Jordan)
1. Media library: nested folders or flat + tags?
2. Image alt text: required or optional field?
3. Search/filter: by name only or tags too?
4. Upload: drag-drop, button, or both?

**Decision deadline:** Friday (end of Week 1)

---

## Success Criteria (End of Week 12)

✅ **P4 Complete:**
- Real-time preview working (< 100ms latency)
- Undo/Redo fully functional (shortcuts + buttons)
- Unit tests passing (renderer parity verified)
- Ready for production

✅ **P5.1 Complete:**
- Media upload working (to Azure)
- Media browser fully functional
- Upload validation tested

✅ **P5.2 Complete:**
- Image insertion end-to-end
- Images embed and preview correctly
- Alt text / metadata working

✅ **P5.3 Complete (MVP):**
- Publishing UI polished
- Draft/Pending/Published badges showing
- Single-reviewer workflow stable

✅ **All tests passing**

✅ **Documentation complete**

---

## Resource Allocation Matrix

| Task | Elizabeth | Jordan | Claude | Hours/Week |
|------|-----------|--------|--------|-----------|
| Feature dev | 80% | 80% | — | — |
| Code review | 10% | 10% | — | — |
| Design/Questions | 10% | 10% | — | — |
| Support/Pairing | — | — | 40 hrs | — |
| Code generation | — | — | 30 hrs | — |
| Testing | — | — | 10 hrs | — |

**Claude allocation:** ~50 hours/week (intensive support)

---

## Risk Mitigation

### Risk: Renderer Parity (Medium)
**Mitigation:** 
- Week 3: Full test matrix (Delta test cases from server tests)
- Week 4: Compare server + client output side-by-side
- If mismatch found: Extract common spec, sync both

### Risk: Azure Integration (Medium)
**Mitigation:**
- Week 3: Proof-of-concept (upload + retrieve one file)
- Week 4: Scale test (10 files, 50MB each)
- Have fallback plan (local storage if Azure fails)

### Risk: Team Overload (Low)
**Mitigation:**
- Weekly syncs to surface blockers early
- Claude available for pairing on hard problems
- Can defer P5.3 if P5.1 + P5.2 takes longer

### Risk: Scope Creep (Medium)
**Mitigation:**
- Freeze features Weeks 1–12 (defer to P6)
- Weekly retros to catch scope drift
- Document deferred items clearly

---

## Launch Criteria (Week 12)

**Launch when:**
- ✅ All 10 unit tests passing
- ✅ Integration tests passing (upload → insert → save)
- ✅ Manual testing: P4 preview latency < 100ms
- ✅ Manual testing: P5.2 image insert works end-to-end
- ✅ Code review sign-off from both Elizabeth & Jordan
- ✅ Azure storage tested in staging environment
- ✅ Documentation complete (API docs, component docs)

**No-go if:**
- Renderer parity issues unfixed
- Azure integration unstable
- Critical security issues in upload endpoint

---

## Post-MVP Roadmap (Week 13+)

**Optional polish (if time + energy):**
- P5.4 Asset dashboard (admin panel)
- Multi-reviewer publishing workflow
- Image optimization (WebP, thumbnails)
- Search/filter enhancements
- Rollback UI for versions

---

## Next Actions (This Week)

1. **Elizabeth:**
   - [ ] Answer P4 design questions (1 hour)
   - [ ] Review preview pane skeleton code (Claude will generate)
   - [ ] Plan Week 3 renderer work

2. **Jordan:**
   - [ ] Answer P5 design questions (1 hour)
   - [ ] Review media schema + API skeleton (Claude will generate)
   - [ ] Decide: Azure connection string, storage account setup

3. **Claude:**
   - [ ] Generate P4 preview component skeleton
   - [ ] Generate P5 media API endpoints skeleton
   - [ ] Create database migration SQL
   - [ ] Generate unit test boilerplate
   - [ ] Create weekly sync calendar

**Decision Deadline:** Friday EOD  
**Kick-off:** Monday 10am standup  
**First code:** Monday afternoon

---

## Communication

- **Daily:** Slack updates (async)
- **Monday 10am:** Team standup (15 min sync)
- **Wednesday 2pm:** Design/Architecture (30 min)
- **Friday 4pm:** Demo & Retro (30 min)
- **Emergency:** Claude available for real-time pairing

---

## Success Definition

**At end of Week 12:**
- P4 (Preview + Undo/Redo) fully shipped & working
- P5.1 (Media Library) fully shipped & working
- P5.2 (Image Insertion) fully shipped & working
- P5.3 (Publishing UI) fully shipped & working
- All code tested, reviewed, documented
- Team confident in quality
- Ready to demo to stakeholders

**If achieved:** 🎉 3-person team shipped 2 major features in 12 weeks
