# Session July 26, 2026 — Infrastructure Fixes

**Date:** July 26, 2026  
**Session Focus:** Docker setup debugging + P2 testing preparation  
**Outcome:** Fixed 3 blocking issues; P2 ready for manual testing

---

## Changes Made This Session

### 1. Fixed Flyway Migration Version Conflicts

**Files Modified:**
```
src/main/resources/database/upgrade/2026/
  - UPGRADE_20260719.1004__analytics_honor_dnt.sql
    → Renamed to: UPGRADE_20260719.1006__analytics_honor_dnt.sql
  
  - UPGRADE_20260719.1006__audit_log.sql
    → Renamed to: UPGRADE_20260719.1007__audit_log.sql
```

**Why:**
- Two migrations with version 1004 caused Flyway to error during init
- Analytics migrations were numbered incorrectly (1004, 1005)
- Fixed by sequencing as: 1004 (Java), 1005 (retention), 1006 (honor_dnt), 1007 (audit)

**Verification:**
```bash
find src/main -name "V20260719*.java" -o -name "UPGRADE_20260719*.sql" | \
  sed 's/.*\(V\|UPGRADE_\)\(.*\)__.*\.*/\1\2/' | sort | uniq -d
# Returns empty (no duplicates)
```

---

### 2. Fixed JSP Files Not Packaged in WAR

**File Modified:**
```
build.xml (lines 271-276)
```

**Change:**
```xml
BEFORE:
<!-- Cleanup -->
<delete>
  <fileset dir="${target.dir}/${project}/WEB-INF/jsp"/>      ← WRONG: deletes JSPs
  <fileset dir="${target.dir}/${project}/WEB-INF/src"/>
  <fileset dir="${target.dir}/${project}/WEB-INF/compiled"/>
</delete>

AFTER:
<!-- Cleanup -->
<!-- Keep JSPs in WAR for Tomcat to serve; delete only intermediate compile artifacts -->
<delete>
  <fileset dir="${target.dir}/${project}/WEB-INF/src"/>
  <fileset dir="${target.dir}/${project}/WEB-INF/compiled"/>
</delete>
```

**Why:**
- Build script compiled JSPs to `.class` files but deleted the source `.jsp` files
- Tomcat needs the JSP source files to serve pages
- Fixed by keeping JSP directory but deleting only temporary compile artifacts

**Verification:**
```bash
jar tf target/simis-cms.war | grep "WEB-INF/jsp/" | wc -l
# Should show: 100+ (many JSP files)

docker exec simis-cms-app-1 ls -la /usr/local/tomcat/webapps/ROOT/WEB-INF/jsp | head
# Should list: admin/, calendar/, cms/, dashboard/, etc.
```

---

### 3. Forced Docker Image Rebuild

**Commands Used:**
```bash
# Instead of:
docker-compose up -d

# Used:
docker-compose down -v
docker-compose up -d --build
```

**Why:**
- Docker caches built images; `up -d` won't rebuild unless `--build` is specified
- WAR was rebuilt locally but old image was still deployed in Docker
- Fixed by forcing image rebuild with `--build` flag

**Verification:**
```bash
# WAR timestamp on host:
ls -la ~/dev/simis-cms/target/simis-cms.war
# Example: Jul 26 11:58 simis-cms.war

# WAR timestamp in container (should match):
docker exec simis-cms-app-1 ls -la /usr/local/tomcat/webapps/ROOT/WEB-INF/jsp/cms/content.jsp
# Should show: Jul 26 11:58 (same as host)
```

---

## Results

### Before Session
- ✗ Flyway migration conflict → Database init blocked
- ✗ JSP files missing from WAR → "Widget JSP not found" errors
- ✗ Old WAR still deployed → Docker not updated

### After Session
- ✅ Flyway migrations numbered correctly
- ✅ JSP files packaged in WAR
- ✅ Docker image rebuilt with new WAR
- ✅ Tomcat starts successfully (~36 seconds)
- ✅ App is running and accessible

### Outstanding (Not P2-related)
- ⏳ Root path (`http://localhost/`) returns 404 — CMS configuration issue
  - App expects configured pages, not a "root" handler
  - Need to find correct admin/test page URL
  - Not blocking P2 feature (just need to find where to test)

---

## How to Build/Deploy Going Forward

```bash
cd ~/dev/simis-cms

# Standard workflow:
ant clean && ant build

# Deploy locally (with full rebuild):
docker-compose down -v
docker-compose up -d --build

# Or just restart without rebuild:
docker-compose down
docker-compose up -d

# Check status:
docker ps | grep simis-cms
docker-compose logs app | tail -20
```

---

## Files NOT Modified (Already Complete)

**P2 Implementation (merged):**
- `src/main/webapp/javascript/overlay-editor-pane.js` ← No changes
- `src/main/webapp/css/overlay-editor-pane.css` ← No changes
- `src/main/webapp/WEB-INF/jsp/cms/content.jsp` ← No changes
- `src/main/webapp/WEB-INF/jsp/main.jsp` ← No changes

(No P2 code modifications needed this session; only infrastructure fixes)

---

## Next Steps

1. **Find test page URL** — Determine where to access CMS admin panel
2. **Run P2 test scenarios** — Use `p2-testing-guide.md` (10 scenarios)
3. **Document results** — Update `p2-testing-checklist.md`
4. **P2.6: Launch** — Announce feature to team

See `visual-editor-program-status.md` for full roadmap.
