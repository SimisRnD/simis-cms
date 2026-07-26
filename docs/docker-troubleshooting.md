# Docker Setup & Troubleshooting Guide

**Last Updated:** July 26, 2026

---

## Quick Start (Working Configuration)

```bash
cd ~/dev/simis-cms

# Clean build with migration renames fixed
ant clean && ant build

# Force Docker image rebuild with new WAR
docker-compose down -v
docker-compose up -d --build

# Wait 45-60 seconds for Tomcat to initialize
# Check logs:
docker-compose logs app | tail -20
```

Expected output: `Server startup in [XX,XXX] milliseconds`

---

## Known Issues & Fixes

### 1. Flyway Migration Conflict: "Found more than one migration with version YYYYMMDD.####"

**Symptom:**
```
org.flywaydb.core.api.FlywayException: Found more than one migration with version 20260719.1004
Offenders:
-> com.simisinc.platform.infrastructure.database.upgrade.V20260719_1004__reencrypt_secret_properties (JDBC)
-> /usr/local/tomcat/webapps/ROOT/WEB-INF/classes/database/upgrade/2026/UPGRADE_20260719.1004__analytics_honor_dnt.sql (SQL)
```

**Cause:**  
Two migrations with identical version numbers. The analytics migrations were numbered 1004-1005 but another migration also used 1004.

**Solution:**  
Rename analytics migrations to sequential versions:
```bash
# In src/main/resources/database/upgrade/2026/
UPGRADE_20260719.1004__analytics_honor_dnt.sql → UPGRADE_20260719.1006__analytics_honor_dnt.sql
UPGRADE_20260719.1006__audit_log.sql → UPGRADE_20260719.1007__audit_log.sql
```

**Verify:** No duplicate version numbers across Java + SQL migrations:
```bash
find src/main -name "V20260719*.java" -o -name "UPGRADE_20260719*.sql" | \
  sed 's/.*\(V\|UPGRADE_\)\(.*\)__.*\.*/\1\2/' | sort | uniq -d
```
(Should return empty if no duplicates)

---

### 2. JSP Files Not Found: "Widget JSP not found, skipping include"

**Symptom:**
```
ERROR com.simisinc.platform.presentation.controller.WebContainerCommand - \
  Widget JSP not found, skipping include to avoid a render loop: /WEB-INF/jsp/cms/content.jsp
WARN com.simisinc.platform.presentation.controller.PageServlet - NO WIDGETS - PAGE WILL NOT RENDER
```

**Cause:**  
The build script compiled JSPs to classes but deleted the source `.jsp` files from the WAR. Tomcat needs the source files.

**Solution:**  
Modify `build.xml` (lines 271-276) to keep JSP source files:

```xml
<!-- BEFORE (wrong - deletes JSPs) -->
<delete>
  <fileset dir="${target.dir}/${project}/WEB-INF/jsp"/>
  <fileset dir="${target.dir}/${project}/WEB-INF/src"/>
  <fileset dir="${target.dir}/${project}/WEB-INF/compiled"/>
</delete>

<!-- AFTER (correct - keeps JSPs) -->
<delete>
  <fileset dir="${target.dir}/${project}/WEB-INF/src"/>
  <fileset dir="${target.dir}/${project}/WEB-INF/compiled"/>
</delete>
```

**Verify:** JSP files are in the built WAR:
```bash
jar tf target/simis-cms.war | grep "WEB-INF/jsp/cms/" | head -5
# Should list: WEB-INF/jsp/cms/content.jsp, etc.
```

---

### 3. Docker Image Caching: Old WAR Deployed Despite Rebuild

**Symptom:**  
After rebuilding the WAR locally, `docker-compose up` still serves the old version. WAR timestamp inside container is older than the rebuilt WAR on host.

**Cause:**  
`docker-compose up` uses cached Docker images. If the image was already built, it won't rebuild unless explicitly told to.

**Solution:**  
Always use `--build` flag to force image rebuild:
```bash
docker-compose down -v
docker-compose up -d --build
```

**Verify:** Check WAR timestamp in container:
```bash
docker exec simis-cms-app-1 ls -la /usr/local/tomcat/webapps/ROOT/WEB-INF/jsp/cms/content.jsp
# Should match: ls -la ~/dev/simis-cms/src/main/webapp/WEB-INF/jsp/cms/content.jsp
```

---

### 4. Fresh Database Initialization: "Could not acquire migration lock"

**Symptom:**
```
ERROR com.simisinc.platform.infrastructure.database.DB - \
  SQLException: ERROR: relation "distributed_lock" does not exist
WARN com.simisinc.platform.application.admin.DatabaseCommand - \
  Could not acquire migration lock; another node is migrating. Waiting for completion...
```

**Cause:**  
Fresh database initialization creates the schema in stages. The Flyway migration system tries to acquire a lock before the lock table is created.

**Solution:**  
Wait for app to fully initialize. Tomcat will retry and eventually succeed once all tables are created. Takes ~40-60 seconds on first run.

**Verify:** Check for successful server startup:
```bash
docker-compose logs app | grep "Server startup in"
# Should see: Server startup in [XXXXX] milliseconds
```

---

## Verification Checklist

After `docker-compose up -d --build`:

```bash
# 1. Check container health
docker ps | grep simis-cms
# Both app and db should be "Up" and "(healthy)"

# 2. Verify WAR extraction
docker exec simis-cms-app-1 ls -la /usr/local/tomcat/webapps/ROOT/WEB-INF/jsp | head -5
# Should list: admin, calendar, cms, dashboard, datasets, etc.

# 3. Check app startup
docker-compose logs app | tail -5 | grep -i "startup"
# Should see: "Server startup in [XX,XXX] milliseconds"

# 4. Verify database
docker-compose exec -T db psql -U simis -d simis-cms -c "SELECT COUNT(*) FROM flyway_history;"
# Should return: count >= 1

# 5. Test HTTP (expect 404 on root; this is OK)
curl -s http://localhost | head -20
# May return 404, but should not say "Widget JSP not found"
```

---

## Cleanup & Reset

If Docker state gets corrupted:

```bash
# Hard reset: remove all containers, images, and volumes
docker-compose down -v
docker system prune -a --volumes

# Rebuild from scratch
ant clean && ant build
docker-compose up -d --build
```

**Note:** This wipes the database. First run will take longer (~45-60 seconds) as it initializes the schema.

---

## Still Stuck?

1. **Read full build output:**
   ```bash
   ant clean && ant build 2>&1 | tee /tmp/build.log
   # Check for JSP compilation errors or migration conflicts
   ```

2. **Check app logs in detail:**
   ```bash
   docker-compose logs app | grep -i "error\|exception" | head -20
   ```

3. **Test database directly:**
   ```bash
   docker-compose exec -T db psql -U simis -d simis-cms -c "\dt"
   # List all tables; should see: distributed_lock, flyway_history, etc.
   ```

4. **Inspect WAR contents:**
   ```bash
   jar tf target/simis-cms.war | grep -c "WEB-INF/jsp"
   # Should be > 100 (if JSPs are present)
   ```

---

## What's NOT Fixed Yet

- **Root path (`http://localhost/`):** Returns 404 (CMS expects configured pages, not a root handler)
- **Finding test page URL:** Need to determine correct admin panel URL or configured test page
- **Full P2 testing scenarios:** Blocked until above is resolved

See `p2-status-and-blockers.md` for details on next steps.
