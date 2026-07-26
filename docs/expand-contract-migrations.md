# Expand/Contract Migration Pattern

**Audience:** Developers working on database schema changes in a rolling-deployment environment  
**Goal:** Ensure zero-downtime migrations by decoupling schema changes from application code changes

## Problem: Why Normal Migrations Break Rolling Deployments

In a rolling deployment with two instances, migrations and code deployments don't happen atomically:

```
Time  Old Instance        New Instance        Issue
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
0:00  v1.0 running        (starting upgrade)
0:05  v1.0 running        Migration: DROP COLUMN status_flag
      v1.0 code reads     (NEW schema has no column)
      status_flag         v1.0 CRASHES ❌
                          SQL: "SELECT status_flag FROM..." → ERROR
0:10  (restarted)         v1.1 running
      Migration: DROP...  (no longer needed)
      v1.1 code reads
      from new schema     ✓
```

The old instance's code still expects `status_flag` to exist, but the new instance dropped it. Result: crashes during the drain window.

## Solution: Expand/Contract Pattern

Split destructive changes into **three phases**, allowing a full deployment cycle between each:

### Phase 1: EXPAND — Add new structure alongside old (v1.0 → v1.1)

**Migration:**
```sql
-- UPGRADE_20260726.1010__add_status_enum.sql
ALTER TABLE orders ADD COLUMN status_enum VARCHAR(50) DEFAULT 'pending';
-- Old code still reads status_flag; new code reads status_enum
```

**Code:**
```java
// v1.1: Read BOTH columns, new field takes precedence
String status = rs.getString("status_enum");
if (status == null || status.isEmpty()) {
  status = rs.getBoolean("status_flag") ? "active" : "pending";
}
```

**Result:** v1.0 and v1.1 coexist peacefully. Both instances run fine.

### Phase 2: DEPLOY — Release code using new structure (v1.1 → v1.2)

No migration needed. Just deploy v1.2 code:

```java
// v1.2: Drop the compat read; only read new column
String status = rs.getString("status_enum");
```

All instances are now running v1.2 code. No instance reads `status_flag` anymore.

### Phase 3: CONTRACT — Remove old structure (after v1.2 is stable, v1.2 → v1.3)

Once ALL instances are running v1.2+, a REPEAT migration removes the old column:

```sql
-- REPEAT_z20260730_cleanup_status_flag.sql
ALTER TABLE orders DROP COLUMN status_flag;
```

This runs every deployment on all instances. It's idempotent; dropping a nonexistent column is harmless.

**Result:** Schema cleaned up. No regression risk since no code reads the old column.

## When to Use Expand/Contract

**Use expand/contract when:**
- ✓ Removing or renaming a column
- ✓ Changing a column type or constraint in a breaking way
- ✓ Removing a table that application code queries
- ✓ Renaming an important column

**Don't use expand/contract for:**
- ✗ Adding a new nullable column (non-breaking; deploy code + migration together)
- ✗ Adding an index (non-breaking; can happen before code)
- ✗ Non-functional schema updates (metadata, comments)

## Practical Example: Adding a Timestamp Column

**Step 1: Expand (v1.0 → v1.1)**
```sql
-- UPGRADE_20260726.1000__add_created_at.sql
ALTER TABLE users ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
```

Code in v1.1:
```java
// Accept both old (no created_at) and new (with created_at)
timestamp = rs.getTimestamp("created_at");
if (timestamp == null) {
  timestamp = Instant.now(); // Fallback for existing rows
}
```

**Step 2: Code-only deploy (v1.1 → v1.2)**
No schema change. Code update:
```java
// v1.2: Only read created_at (it's guaranteed to exist now)
timestamp = rs.getTimestamp("created_at");
```

All instances running v1.2. No instance falls back.

**Step 3: Contract (cleanup, after stable v1.2)**
```sql
-- REPEAT_z20260730_cleanup_old_timestamps.sql
-- If we had an old timestamp column, drop it here
-- (In this example, there wasn't one, so no contract phase needed)
```

## Detection: CI Gate

The CI pipeline checks for violations:

```bash
python3 tools/detect_unsafe_migrations.py
```

This catches:
```
❌ UNSAFE MIGRATIONS DETECTED
Migration version 20260726.1015:
  SQL destructive patterns:
    UPGRADE_...: DROP COLUMN
  Java removals in same commit:
    field removal: status_flag
  → Use expand/contract pattern...
```

**Fix:** Split into two commits, two PRs:
1. PR #1: Expand migration + compat code (read both columns)
2. PR #2 (after #1 is deployed): Cleanup code (read new column only)
3. Later PR (after v1.1 + v1.2 stable): Contract migration (remove old column)

## Checklist for PR Review

- [ ] **Destructive migration?** (DROP COLUMN, DROP TABLE, RENAME, type change)
  - If yes → Split into expand + contract
- [ ] **Java field removed?** Same version as migration?
  - If yes → Use expand phase (add code compat read)
- [ ] **Old column still readable by v-1?**
  - Expand: Yes (both columns available)
  - Contract: No longer needed (all instances on new code)
- [ ] **REPEAT migration?** For cleanup phase?
  - Add if dropping old structure after stable deployment

## Troubleshooting

**CI says "unsafe migration" but I only added a column:**
- Adding a nullable column is safe, no expand/contract needed
- If the CI still complains, check for field removals in the same PR

**How long between expand and contract?**
- Minimum: 1 full deployment cycle (rolling deploy completes)
- Recommended: 1-2 weeks (let the expanded schema stabilize in production)
- Safer: Monthly cleanup migrations (batch them together)

**Web-only instances (CMS_NODE_TYPE=web) skip migrations. Is that safe?**
- Yes. Primary instances run all migrations. Web instances wait and then proceed.
- See DEPLOYMENT.md §4.0 for migration lock behavior.

---

**See Also:**
- `tools/detect_unsafe_migrations.py` — CI gate implementation
- `.github/workflows/ant.yml` — Where the gate runs
- DEPLOYMENT.md §4.0 — Multi-instance migration locking
