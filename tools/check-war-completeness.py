#!/usr/bin/env python3
"""Report classes the shipped WAR references but does not contain.

Background
----------
``check-dependency-drift.py`` answers "does the vendored jar match the version
the pom declares?". It cannot answer "is the jar's own runtime actually in the
WAR?", because it only compares TOP-LEVEL pom dependencies -- a transitive that
nobody declared is invisible to it.

That gap shipped a real bug. ``okhttp`` is written in Kotlin, the pom carried an
explicit ``<exclusion>`` for ``kotlin-stdlib``, and no Kotlin jar existed
anywhere in the repository. Constructing an ``OkHttpClient`` -- the first thing
the Square SDK does -- threw::

    java.lang.NoClassDefFoundError: kotlin/jvm/internal/Intrinsics

The version numbers were all perfectly consistent. Nothing in CI noticed,
because Ant compiles against the vendored jars and the compiler never needed the
Kotlin runtime; only the JVM did, at request time.

What it does
------------
Explodes every ``WEB-INF/lib/*.jar`` from the built WAR into one class tree and
runs ``jdeps --missing-deps`` over it. Anything reported is a class that
something in the WAR references and that is present neither in the WAR nor in
the JDK.

Most such references are legitimate. Java libraries routinely reference optional
integrations they never load (jobrunr names MongoDB, okhttp names Android), and
a servlet container supplies ``jakarta.servlet`` at runtime rather than the WAR.
Those live in ALLOWLIST below, each with the reason it is expected. Anything NOT
allowlisted is a class the application can reach but the JVM cannot load.

Why the whole tree at once
--------------------------
Running jdeps per jar looks tidier but is wrong here: a jar containing
``module-info.class`` puts jdeps into module mode, where it aborts with
``FindException: Module ... not found`` on the first absent module. That is an
ABORT, not a finding -- an earlier draft of this script consumed those failures
and cheerfully reported zero missing classes for a WAR that was provably broken.
Exploding to a flat class tree (and dropping ``module-info.class``) keeps jdeps
on the classpath code path, so every jar is analysed in a single pass.

Modes
-----
Report-only by default: prints findings, always exits 0. Pass ``--strict`` (or
set ``STRICT=1``) to exit 1 when a non-allowlisted class is missing.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import argparse
import collections
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

# Package prefixes whose absence from the WAR is EXPECTED, each with the reason.
# Keyed on the missing package, matched as a dotted prefix. Add entries as:
#   "package.prefix": "why it is legitimately absent"
# Before adding one, confirm the reference really is optional or container-
# provided. If application code can reach it at runtime, it is a bug, not an
# allowlist entry.
ALLOWLIST: dict[str, str] = {
    # --- supplied by the servlet container, must NOT be in the WAR ---
    # NOTE: javax.servlet / javax.el are deliberately NOT allowlisted. Tomcat 11 supplies the
    # jakarta.* namespace and nothing under javax.*, so a surviving javax reference is a real
    # runtime failure and must be reported rather than waved through.
    "jakarta.servlet": "provided by Tomcat; bundling it breaks deployment",
    "jakarta.el": "provided by Tomcat (Expression Language)",
    "org.apache.catalina": "Tomcat internals, provided by the container",
    "org.apache.jasper": "Tomcat JSP engine, provided by the container",
    "org.apache.tomcat": "Tomcat internals, provided by the container",
    # --- optional integrations the app never configures ---
    "android": "okhttp's optional Android platform support; server-side only here",
    "org.conscrypt": "okhttp optional TLS provider; the JDK provider is used",
    "org.bouncycastle": "okhttp optional TLS provider; the JDK provider is used",
    "org.openjsse": "okhttp optional TLS provider; the JDK provider is used",
    "com.mongodb": "jobrunr optional MongoDB backend; this app uses PostgreSQL",
    "org.bson": "jobrunr optional MongoDB backend; this app uses PostgreSQL",
    "io.lettuce": "jobrunr optional Redis backend; not configured",
    "redis.clients": "jobrunr optional Redis backend; not configured",
    "org.elasticsearch": "jobrunr optional Elasticsearch backend; not configured",
    "io.micrometer": "optional metrics facade (jobrunr, amqp-client); not configured",
    "com.codahale.metrics": "amqp-client optional metrics; not configured",
    "io.opentelemetry": "amqp-client optional tracing; not configured",
    "io.netty": "amqp-client optional NIO transport; the blocking transport is used",
    "oracle.sql": "jobrunr optional Oracle support; this app uses PostgreSQL",
    "waffle.windows": "postgresql driver optional Windows SSPI auth; Linux deployment",
    "com.sun.jna": "optional native access (argon2 uses the no-libs variant)",
    "org.osgi": "OSGi metadata hooks; this app is not an OSGi container",
    "org.jboss": "flyway optional JBoss VFS support",
    "com.openhtmltopdf": "flexmark optional PDF renderer; not used",
    "org.nibor": "flexmark optional autolink extension; not used",
    "org.jaxen": "jdom2 optional XPath engine; not used",
    "org.joni": "json-schema-validator optional regex engine; not used",
    "org.jcodings": "json-schema-validator optional encoding support; not used",
    "com.ethlo": "json-schema-validator optional date-time validator; not used",
    "javax.enterprise": "johnzon optional CDI integration; no CDI container",
    "javax.ws": "johnzon optional JAX-RS integration; not used",
    "jakarta.json": "jobrunr optional Jakarta JSON-B binding; Jackson is used",
    # --- compile-time-only annotations, never loaded at runtime ---
    "javax.annotation": "compile-time annotations, not required at runtime",
    "org.checkerframework": "compile-time nullness annotations",
    "org.jspecify": "compile-time nullness annotations",
    "net.jcip": "compile-time concurrency annotations",
    "com.google.errorprone": "compile-time static-analysis annotations",
    "com.google.j2objc": "compile-time annotations",
    "com.google.common": "optional Guava usage in libraries that do not require it",
    "com.google.gson": "optional Gson usage; Jackson is configured",
    "com.google.protobuf": "optional protobuf support; not used",
    "org.apache.log4j": "legacy log4j 1.x bridge (flyway); slf4j is used",
    "org.apache.logging": "log4j2 bridge (flyway); slf4j is used",
    "org.apache.avalon": "legacy commons-logging bridge",
    "org.apache.commons.logging": "commons-logging bridge; slf4j is used",
    "org.slf4j.impl": "slf4j binding lookup; the binding is present",
    # --- optional metrics/ORM integrations in the connection pool ---
    "io.dropwizard": "HikariCP optional Dropwizard metrics; not configured",
    "io.prometheus": "HikariCP optional Prometheus metrics; not configured",
    "org.hibernate": "HikariCP optional Hibernate integration; this app uses plain JDBC",
    # --- optional codecs behind commons-compress' format registry ---
    "org.tukaani": "commons-compress optional XZ/LZMA codec; not used",
    "org.brotli": "commons-compress optional Brotli codec; not used",
    "com.github.luben": "commons-compress optional Zstandard codec; not used",
    # --- other optional library backends ---
    "org.apache.commons.digester": "commons-validator optional XML config loader; not used",
    "org.apache.commons.pool2": "jobrunr optional connection pooling; not used",
    "org.apache.pdfbox": "flexmark optional PDF output; not used",
    "org.apache.xml": "taglibs optional Xalan XPath engine; the JDK engine is used",
    "org.apache.xpath": "taglibs optional Xalan XPath engine; the JDK engine is used",
    "org.apache.xerces": "JSTL optional SAX parser; the JDK parser is used",
    "org.eclipse.tags": "JSTL's shaded Xalan, reached only by the <x:*> XML tags; those are unused",
    "com.google.re2j": "jsoup optional RE2 regex engine; java.util.regex is used",
    "com.sun.jna": "postgresql driver optional Windows SSPI auth; Linux deployment",
    "org.apache.http": "jobrunr optional Apache HttpClient transport; not used",
}

# Allowlist entries that apply to ONE library only.
#
# Use this instead of a global ALLOWLIST entry whenever a reference is harmless from a
# particular jar but would be a genuine runtime failure coming from anywhere else. A global
# "javax.servlet" entry, for instance, would also wave through a jar that really does depend
# on the pre-Jakarta namespace -- which Tomcat 11 does not provide.
#
# Format: (jar filename prefix, class-name prefix): reason
JAR_SCOPED_ALLOWLIST: dict[tuple[str, str], str] = {
    ("thymeleaf-", "javax.servlet"): (
        "thymeleaf ships both servlet bridges in one jar; this app builds "
        "JakartaServletWebApplication, so the javax path is never loaded "
        "(revisit if thymeleaf stops shipping both)"
    ),
}

_CLASS_RE = re.compile(r"^\s*(\S+)\s+->\s+(\S+)\s+not found\s*$")


def explode(war: str, dest: str) -> tuple[dict[str, str], int]:
    """Unpack WEB-INF/lib/*.jar into one class tree. Returns class->jar index."""
    owner: dict[str, str] = {}
    jars = 0
    with zipfile.ZipFile(war) as w:
        libs = [n for n in w.namelist()
                if n.startswith("WEB-INF/lib/") and n.endswith(".jar")]
        if not libs:
            sys.exit("error: no WEB-INF/lib/*.jar entries in %s" % war)
        jardir = os.path.join(dest, "_jars")
        os.makedirs(jardir, exist_ok=True)
        for name in libs:
            w.extract(name, jardir)
        for name in libs:
            jars += 1
            jarname = os.path.basename(name)
            path = os.path.join(jardir, name)
            try:
                with zipfile.ZipFile(path) as j:
                    for entry in j.namelist():
                        if not entry.endswith(".class"):
                            continue
                        # Multi-release overlays and module descriptors both push
                        # jdeps into module mode; neither is needed to answer
                        # "is this class present somewhere in the WAR".
                        if entry.startswith("META-INF/versions/"):
                            continue
                        if os.path.basename(entry) == "module-info.class":
                            continue
                        j.extract(entry, dest)
                        owner.setdefault(entry[:-len(".class")].replace("/", "."),
                                         jarname)
            except zipfile.BadZipFile:
                print("warning: %s is not a readable jar, skipped" % jarname)
    return owner, jars


def run_jdeps(tree: str) -> str:
    java_home = os.environ.get("JAVA_HOME")
    jdeps = os.path.join(java_home, "bin", "jdeps") if java_home else "jdeps"
    try:
        proc = subprocess.run([jdeps, "--missing-deps", "-cp", tree, tree],
                              capture_output=True, text=True)
    except (FileNotFoundError, NotADirectoryError):
        sys.exit("error: jdeps not found at '%s' -- set JAVA_HOME to a JDK 21 "
                 "installation (a JRE does not include jdeps)" % jdeps)
    # A crashed analysis must never read as a clean WAR. This is the exact trap
    # the per-jar version of this script fell into.
    if proc.returncode != 0:
        sys.exit("error: jdeps failed (exit %d)\n%s"
                 % (proc.returncode, proc.stderr.strip()))
    if "Exception in thread" in proc.stderr:
        sys.exit("error: jdeps aborted\n%s" % proc.stderr.strip())
    return proc.stdout


def allowed_for(name: str) -> str | None:
    """Reason this class is expected to be absent, or None. Longest prefix wins."""
    best, reason = "", None
    for prefix, why in ALLOWLIST.items():
        if (name == prefix or name.startswith(prefix + ".")) and len(prefix) > len(best):
            best, reason = prefix, why
    return reason


def matched_prefix(name: str) -> str:
    best = ""
    for prefix in ALLOWLIST:
        if (name == prefix or name.startswith(prefix + ".")) and len(prefix) > len(best):
            best = prefix
    return best


def allowed_for_jar(jar: str, name: str) -> tuple[str, str] | None:
    """Reason this class is expected to be absent *from this jar alone*, with its label.

    Returns None when nothing in JAR_SCOPED_ALLOWLIST covers this (jar, class) pair.
    """
    best, hit = "", None
    for (jar_prefix, class_prefix), why in JAR_SCOPED_ALLOWLIST.items():
        if not jar.startswith(jar_prefix):
            continue
        if (name == class_prefix or name.startswith(class_prefix + ".")) and len(class_prefix) > len(best):
            best, hit = class_prefix, ("%s* needs %s" % (jar_prefix, class_prefix), why)
    return hit


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--war", default="target/simis-cms.war")
    ap.add_argument("--strict", action="store_true",
                    default=os.environ.get("STRICT") == "1")
    args = ap.parse_args()

    if not os.path.exists(args.war):
        sys.exit("error: %s not found -- run `ant -lib lib/war package` first"
                 % args.war)

    tree = tempfile.mkdtemp(prefix="warcheck-")
    try:
        owner, jars = explode(args.war, tree)
        out = run_jdeps(tree)

        allowed: collections.Counter = collections.Counter()
        scoped: collections.Counter = collections.Counter()
        scoped_reasons: dict[str, str] = {}
        unexpected: dict[tuple[str, str], list[str]] = collections.defaultdict(list)
        for line in out.splitlines():
            m = _CLASS_RE.match(line)
            if not m:
                continue
            src, missing = m.group(1), m.group(2)
            if "." not in missing:
                continue
            jar = owner.get(src, owner.get(src.split("$")[0], "?"))
            # Match the allowlist against the FULL class name, never a truncated
            # group: "org.apache.jasper.*" is container-provided while
            # "org.apache.commons.digester.*" is an optional library, and both
            # collapse to "org.apache" if you group first and match second.
            reason = allowed_for(missing)
            if reason:
                allowed[matched_prefix(missing)] += 1
                continue
            # Fall back to a per-library exemption before calling it a finding
            hit = allowed_for_jar(jar, missing)
            if hit:
                label, why = hit
                scoped[label] += 1
                scoped_reasons[label] = why
            else:
                unexpected[(jar, ".".join(missing.split(".")[:3]))].append(missing)

        print("WAR completeness report  (%s, %d jars, %d classes)"
              % (args.war, jars, len(owner)))
        print("=" * 72)
        print()
        print("Classes referenced but not present in the WAR or the JDK.")
        print()

        if unexpected:
            print("UNEXPECTED -- reachable at runtime, absent from the artifact (%d):"
                  % len(unexpected))
            for (jar, pkg) in sorted(unexpected):
                names = unexpected[(jar, pkg)]
                print("  %-34s needs %-28s (%d refs)" % (jar, pkg, len(names)))
                for n in sorted(set(names))[:3]:
                    print("       e.g. %s" % n)
        else:
            print("UNEXPECTED: (none)")
        print()
        if scoped:
            print("JAR-SCOPED ALLOWLIST -- exempt for this library only (%d, %d refs):"
                  % (len(scoped), sum(scoped.values())))
            for label, count in sorted(scoped.items()):
                print("  %-46s %5d  %s" % (label, count, scoped_reasons[label]))
            print()
        print("ALLOWLISTED -- optional or container-provided (%d packages, %d refs):"
              % (len(allowed), sum(allowed.values())))
        for pkg, count in sorted(allowed.items()):
            print("  %-32s %5d  %s" % (pkg, count, allowed_for(pkg)))
        print()
        print("Summary: %d unexpected, %d allowlisted packages."
              % (len(unexpected), len(allowed)))

        if unexpected and args.strict:
            print()
            print("FAIL: the WAR is missing classes it can reach at runtime.")
            print("Vendor the missing jar into lib/build, or add an ALLOWLIST")
            print("entry in this script explaining why the reference is optional.")
            return 1
        return 0
    finally:
        shutil.rmtree(tree, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
