#!/usr/bin/env python3
"""Two things that must never appear in server code, both cheap to check and easy to reintroduce.

1. **A wildcard CORS origin.** ``Access-Control-Allow-Origin: *`` lets any site read the response.
   The filter echoes the configured ``site.url`` instead, and RestRequestFilterTest pins that -- but
   a test only covers the call site it names, and this header is set in three places. A wildcard is
   the usual shortcut when an integration will not connect, so it is worth refusing outright rather
   than relying on review to catch it.

2. **printStackTrace.** It writes to stdout rather than the configured logger, which means the trace
   bypasses log levels and formatting and lands in the container's output regardless of environment.
   That is "disable debug mode" in practice: a stack trace is internal structure, and the places
   that call this are XML loaders that fail on malformed config -- exactly when the output is most
   likely to be read by someone who should not see it.

Existing occurrences are allowlisted rather than fixed here, so the gate can land without a
behaviour change in the same commit. The list is the ratchet: it may shrink, never grow.

Reports findings always; exits 1 only with --strict, matching the other tools in this directory.
Run: python3 tools/check-server-hygiene.py [repo_root] [--strict]
"""
import os
import re
import sys

# Files that called printStackTrace when this gate was written. Fix them and delete the entry;
# do not add to this list -- that is the whole point of it existing.
PRINT_STACK_TRACE_ALLOWLIST = {
    "src/main/java/com/simisinc/platform/infrastructure/scheduler/SchedulerManager.java",
    "src/main/java/com/simisinc/platform/presentation/controller/XMLPageLoader.java",
    "src/main/java/com/simisinc/platform/presentation/controller/XMLFooterLoader.java",
    "src/main/java/com/simisinc/platform/presentation/controller/XMLWebPageTemplateLoader.java",
    "src/main/java/com/simisinc/platform/presentation/controller/XMLHeaderLoader.java",
    "src/main/java/com/simisinc/platform/presentation/controller/XMLJSONServiceLoader.java",
    "src/main/java/com/simisinc/platform/rest/controller/XMLServiceLoader.java",
}

# "*" as the allowed origin, however it is spelled -- addHeader/setHeader, single or double quotes.
WILDCARD_CORS = re.compile(
    r'(?:add|set)Header\s*\(\s*["\']Access-Control-Allow-Origin["\']\s*,\s*["\']\*["\']'
)
PRINT_STACK_TRACE = re.compile(r'\.printStackTrace\s*\(')


def java_files(root):
    for base in ("src/main/java",):
        for dirpath, _dirnames, filenames in os.walk(os.path.join(root, base)):
            for name in filenames:
                if name.endswith(".java"):
                    full = os.path.join(dirpath, name)
                    yield full, os.path.relpath(full, root)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    strict = "--strict" in sys.argv
    root = args[0] if args else "."
    failures = []
    scanned = 0
    stale_allowlist = set(PRINT_STACK_TRACE_ALLOWLIST)

    for full, rel in java_files(root):
        scanned += 1
        with open(full, encoding="utf-8", errors="replace") as handle:
            for number, line in enumerate(handle, start=1):
                if WILDCARD_CORS.search(line):
                    failures.append((rel, number, "wildcard Access-Control-Allow-Origin"))
                if PRINT_STACK_TRACE.search(line):
                    posix = rel.replace(os.sep, "/")
                    stale_allowlist.discard(posix)
                    if posix not in PRINT_STACK_TRACE_ALLOWLIST:
                        failures.append((rel, number, "printStackTrace -- use the logger"))

    print("Server hygiene check")
    print()
    print("  %d java file(s) scanned" % scanned)
    print("  %d printStackTrace site(s) allowlisted" % len(PRINT_STACK_TRACE_ALLOWLIST))
    # Only meaningful in a real tree. Under the synthetic repos the tool tests build, none of the
    # allowlisted files exist, and reporting all seven as stale would be noise rather than a signal.
    found_any_allowlisted = len(stale_allowlist) < len(PRINT_STACK_TRACE_ALLOWLIST)
    if stale_allowlist and found_any_allowlisted:
        print()
        print("  NOTE  %d allowlisted file(s) no longer call printStackTrace." % len(stale_allowlist))
        print("        Remove them from PRINT_STACK_TRACE_ALLOWLIST so the ratchet keeps its teeth:")
        for entry in sorted(stale_allowlist):
            print("          %s" % entry)
    print()
    if failures:
        for rel, number, why in sorted(failures):
            print("  FAIL  %s:%d  %s" % (rel, number, why))
        print()
        print("FAIL: %d new occurrence(s)." % len(failures))
        return 1 if strict else 0
    print("OK  no wildcard CORS origin, no unallowlisted printStackTrace.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
