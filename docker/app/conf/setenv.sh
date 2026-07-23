#!/bin/sh
# SimIS CMS — STIG hardening: JVM system properties.
# Sourced by catalina.sh at startup; appends to any CATALINA_OPTS set in the image
# so it composes cleanly with other options (e.g. the memory/agent flags) rather
# than overwriting them. Coverage: docker-tomcat9-stig-hardening.md.
#
# This file currently sets no JVM properties. It is retained (rather than deleted)
# because the Dockerfile copies it and because it is the natural home for any future
# -D hardening flag.
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
