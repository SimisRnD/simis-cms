## With Tomcat
## CATALINA_HOME:   /usr/local/tomcat
# ant package
# docker build -f docker/app/Dockerfile -t simis-cms:latest .
# docker image ls
# docker run -p 80:8080 --env-file=.env simis-cms:latest

# Tomcat 11 (Jakarta EE / jakarta.servlet). The application targets Servlet 6.1; it will not
# run on the Tomcat 9 base this image used previously, which serves the javax.* namespace.
FROM tomcat:11.0-jdk21@sha256:543dd74d992458d558875de8493399a718270b361b16e4ea6750371a13dda077

# STIG hardening. Overlay the hardened server.xml (shutdown port disabled, HTTP TRACE off,
# server banner suppressed, no error-page disclosure, autoDeploy off, access logging), add
# setenv.sh (it APPENDS to CATALINA_OPTS below, so it composes with the Application Insights
# agent flags), and remove the bundled default web applications (manager, host-manager, docs,
# examples) that the STIG requires be absent -- webapps.dist is unchanged on the Tomcat 11
# base and still contains exactly those. Coverage & rule mapping:
# docker-tomcat9-stig-hardening.md (private).
#
# Carried onto Tomcat 11 during the jakarta.servlet migration. Every server.xml edit was
# re-verified against stock Tomcat 11 and remains valid. The two items the migration left
# open were resolved on the <Connector> in server.xml (see that file for the reasoning):
#   - discardFacades="true": the Tomcat 11 replacement for the RECYCLE_FACADES system
#     property (since removed from setenv.sh); on by default in 11, set explicitly to assert it.
#   - maxPartCount / maxPartHeaderSize: new Tomcat 11 multipart limits with no Tomcat 9
#     equivalent, pinned to the documented Tomcat 11 defaults (50 / 512).
# Still open: the STIG rule-ID mapping has NOT been re-done for a Tomcat 11 baseline.
COPY ./docker/app/conf/server.xml /usr/local/tomcat/conf/server.xml
COPY ./docker/app/conf/setenv.sh /usr/local/tomcat/bin/setenv.sh
RUN chmod 0750 /usr/local/tomcat/bin/setenv.sh && rm -rf /usr/local/tomcat/webapps.dist

# Application Insights Java agent (APM / operational telemetry). Baked into the image because App Service
# for Containers cannot auto-instrument a custom container. The agent auto-instruments Tomcat/JDBC/JVM/HTTP
# and is INERT until APPLICATIONINSIGHTS_CONNECTION_STRING is supplied at deploy (from Key Vault). With no
# connection string it logs a single startup notice ("No connection string provided") and self-disables --
# the JVM and app are unaffected, so local/CI runs (which have no connection string) still boot normally.
# Pinned + SHA-256 verified for supply-chain integrity; it auto-loads applicationinsights.json from /opt.
ARG APPLICATIONINSIGHTS_VERSION=3.7.9
ARG APPLICATIONINSIGHTS_SHA256=4ab7a442bf9defc7475d026c6b49042793ebb010c391a1db91ae07da0c04d848
RUN curl -fsSL -o /opt/applicationinsights-agent.jar \
      "https://repo1.maven.org/maven2/com/microsoft/azure/applicationinsights-agent/${APPLICATIONINSIGHTS_VERSION}/applicationinsights-agent-${APPLICATIONINSIGHTS_VERSION}.jar" \
    && echo "${APPLICATIONINSIGHTS_SHA256}  /opt/applicationinsights-agent.jar" | sha256sum -c -
COPY ./docker/app/applicationinsights.json /opt/applicationinsights.json

# [STIG] Pre-extract the WAR at build time instead of letting Tomcat unpack it at startup.
# With a read-only root filesystem, Tomcat cannot write the exploded ROOT/ into webapps/ at
# boot -- so the app is exploded here, once, and server.xml sets unpackWARs="false". The
# extracted tree is identical to the WAR's contents (precompiled JSPs included). This removes
# the last startup write to webapps/ and lets that directory stay read-only.
COPY ./target/simis-cms.war /tmp/ROOT.war
RUN mkdir -p /usr/local/tomcat/webapps/ROOT \
    && (cd /usr/local/tomcat/webapps/ROOT && jar xf /tmp/ROOT.war) \
    && rm /tmp/ROOT.war

# [STIG] Run Tomcat as a non-root user. The stock image runs as root, which is a standing,
# citable finding independent of any base-image choice. A fixed high UID/GID avoids collision
# with host accounts and lets an orchestrator assert runAsNonRoot / runAsUser without guessing.
#
# What must be writable, and why -- each of these was confirmed by running the container, not
# assumed:
#   work/    Jasper's scratch directory. JSPs are precompiled, but Tomcat still writes here.
#   temp/    java.io.tmpdir for the JVM.
#   logs/    catalina.out and the STIG access log (AU-3) -- an unwritable logs/ would silently
#            lose the per-request audit trail this profile depends on.
#   webapps/ (no longer a runtime write -- the WAR is pre-extracted above and unpackWARs is
#            false, so webapps/ is read-only under the read-only root filesystem).
#   /opt     (no longer a runtime write -- the App Insights agent's selfDiagnostics is routed to
#            the console in applicationinsights.json, so nothing is written beside the jar).
#   /opt/simis  CMS_PATH, the file store. Created here on purpose: docker seeds a NAMED volume
#            from the image path, so creating it owned by the runtime user is what makes the
#            mounted web-data volume writable. Without this the app boots and /healthz fails
#            its "file store writable" check.
#
#   conf/Catalina/localhost  Tomcat's per-host deployment directory. HostConfig.beforeStart
#            creates this at startup even with autoDeploy="false"; without it Tomcat logs
#            "SEVERE ... Unable to create directory for deployment" on every boot. The app
#            still serves, but a SEVERE on a hardened image invites review questions and can
#            mask a real error later. It is pre-created and chowned rather than making the
#            whole of conf/ writable -- one directory of write surface instead of the entire
#            configuration tree, which stays read-only (server.xml included).
#
# setenv.sh is chowned as well as chmod 0750: catalina.sh sources it only `if [ -r ]`, so a
# root-owned 0750 file would be silently unreadable to this user and the JVM options would
# vanish without an error. That failure mode is called out in the private STIG runbook.
#
# The read-only root filesystem is enforced at the container-runtime layer (docker-compose.yaml:
# read_only + tmpfs for work/temp/logs), paired with this image's pre-extraction and
# console-routed agent log. SecurityListener (enabled in server.xml) and dropped capabilities
# (compose) complete the runtime hardening.
#
# /opt/jna is the one deliberate writable+executable surface. Argon2 password hashing loads a
# JNA native library, which JNA extracts to a temp dir and then mmap-executes. Docker forces
# `noexec` on tmpfs, so the general scratch dirs (work/temp/logs) cannot host it -- setenv.sh
# points -Djna.tmpdir here, a mounted volume (volumes are exec), keeping noexec everywhere else.
# The stronger long-term posture is a system-installed libargon2 with argon2-jvm-nolibs, which
# would need no writable-exec surface at all; tracked as a follow-up.
ARG APP_UID=10001
ARG APP_GID=10001
RUN groupadd --gid ${APP_GID} simis \
    && useradd --uid ${APP_UID} --gid ${APP_GID} --home-dir /usr/local/tomcat \
               --shell /usr/sbin/nologin --no-create-home simis \
    && mkdir -p /opt/simis /opt/jna /usr/local/tomcat/work /usr/local/tomcat/temp /usr/local/tomcat/logs \
                /usr/local/tomcat/conf/Catalina/localhost \
    && chown -R simis:simis \
         /opt \
         /usr/local/tomcat/work \
         /usr/local/tomcat/temp \
         /usr/local/tomcat/logs \
         /usr/local/tomcat/webapps \
         /usr/local/tomcat/conf/Catalina \
         /usr/local/tomcat/bin/setenv.sh
USER simis

ENV CATALINA_OPTS="-javaagent:/opt/applicationinsights-agent.jar -XX:InitialRAMPercentage=10 -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=80 -XX:+ExitOnOutOfMemoryError"
# 8080 is deliberately unprivileged -- a non-root process cannot bind <1024.
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=3 CMD curl -f --max-time 5 http://localhost:8080/healthz || exit 1
CMD ["catalina.sh", "run"]
