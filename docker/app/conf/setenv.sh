#!/bin/sh
# SimIS CMS — STIG hardening: JVM system properties.
# Sourced by catalina.sh at startup; appends to any CATALINA_OPTS set in the image
# so it composes cleanly with other options (e.g. the memory/agent flags) rather
# than overwriting them. Coverage: docker-tomcat9-stig-hardening.md.
#
# [STIG] Point JNA's native-library extraction at /opt/jna. Under the read-only root filesystem
# the general scratch dirs (work/temp/logs) are noexec tmpfs, but Argon2 password hashing loads a
# JNA native library that must be mmap-executed. /opt/jna is a mounted volume (exec, unlike tmpfs),
# so JNA can extract and load its .so there while everything else stays noexec/read-only. Without
# this the admin-user Flyway migration fails with UnsatisfiedLinkError ("failed to map segment").
CATALINA_OPTS="$CATALINA_OPTS -Djna.tmpdir=/opt/jna"
#
# Historical note, so the old flag is not reintroduced: Tomcat 9 required
#     -Dorg.apache.catalina.connector.RECYCLE_FACADES=true
# to allocate a fresh request/response facade per request (closing a class of
# cross-request information-disclosure bugs). That system property does not exist in
# Tomcat 11. It was renamed to the connector attribute `discardFacades` (added in
# 10.0.0-M1 / 9.0.31) AND its default was flipped to true, so on Tomcat 11 the
# protection is on by default. This profile sets discardFacades="true" explicitly on
# the <Connector> in server.xml, so the control is asserted there rather than here.
# Do not re-add the RECYCLE_FACADES system property -- Tomcat 11 does not read it.
