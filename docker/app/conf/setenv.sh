#!/bin/sh
# SimIS CMS — DISA Tomcat 9 STIG hardening: JVM system properties.
# Sourced by catalina.sh at startup; appends to any CATALINA_OPTS set in the image
# so it composes cleanly with other options (e.g. the memory/agent flags) rather
# than overwriting them. Coverage: docker-tomcat9-stig-hardening.md.
#
# [STIG] RECYCLE_FACADES=true — allocate a fresh request/response facade per request
# instead of recycling, closing a class of cross-request information-disclosure bugs.
# Runtime note: this changes behaviour for any code that (incorrectly) retains a request/
# response reference past the request — such code would now fail fast instead of reading
# stale data. SimIS is a standard per-request servlet app, but validate the upload,
# streaming-export, and scheduled-job paths in the docker rehearsal / deploy smoke test
# before production. To disable, comment out the line below.
CATALINA_OPTS="$CATALINA_OPTS -Dorg.apache.catalina.connector.RECYCLE_FACADES=true"
