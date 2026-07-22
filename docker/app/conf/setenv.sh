#!/bin/sh
# SimIS CMS — STIG hardening: JVM system properties.
# Sourced by catalina.sh at startup; appends to any CATALINA_OPTS set in the image
# so it composes cleanly with other options (e.g. the memory/agent flags) rather
# than overwriting them. Coverage: docker-tomcat9-stig-hardening.md.
#
# ---------------------------------------------------------------------------
# ACTION REQUIRED — RECYCLE_FACADES is inert on the Tomcat 11 base.
#
# The previous hardening set:
#     -Dorg.apache.catalina.connector.RECYCLE_FACADES=true
# to allocate a fresh request/response facade per request instead of recycling,
# closing a class of cross-request information-disclosure bugs.
#
# That system property does not exist in Tomcat 11. Verified against the
# apache-tomcat-11.0.24 distribution: zero occurrences of RECYCLE_FACADES in any
# lib/*.jar or in webapps/docs/config/systemprops.html, while control literals
# checked the same way (STRICT_SERVLET_COMPLIANCE, org.apache.catalina.connector)
# are present. Tomcat 11 therefore never reads this flag.
#
# It is commented out rather than left set, because a flag the container ignores
# would let the runbook keep claiming a control that is not actually applied.
#
# To resolve before this is treated as compliance evidence:
#   1. Confirm against Tomcat's own 10.x changelog why the property was removed --
#      whether the protective behaviour became unconditional (control satisfied by
#      default, nothing further needed) or the recycling behaviour itself changed
#      (control needs a different mitigation).
#   2. Update docker-tomcat9-stig-hardening.md with the finding and the rule-ID
#      mapping for the Tomcat 11 baseline.
#
# CATALINA_OPTS="$CATALINA_OPTS -Dorg.apache.catalina.connector.RECYCLE_FACADES=true"
# ---------------------------------------------------------------------------
