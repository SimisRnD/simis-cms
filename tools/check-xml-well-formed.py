#!/usr/bin/env python3
"""Check that the XML configuration files the application ships are well-formed.

Background
----------
Several XML files are read only by Tomcat, at container startup -- not by the Ant
build, the test suite, or any other CI gate. A malformed one therefore passes every
existing check and fails only when the container boots, where it surfaces as:

    SEVERE  Cannot start server, server instance is not configured

which names neither the file nor the reason.

The specific trap is that **XML forbids a doubled hyphen inside a comment body**. It
is easy to type in an explanatory comment and invisible on review. It has reached
`main` at least once (PR #178, in ``META-INF/context.xml``, caught only by a manual
container rehearsal -- the incident that motivated the deploy smoke test), and was
reproduced again while editing ``server.xml`` (PR #221), caught by that smoke test.

The smoke test catches this correctly but expensively: build the WAR, build the
image, start PostgreSQL, start Tomcat, wait for a healthcheck to fail -- minutes, and
the message does not name the cause. This check does the same job in about a second
and names the file, line, and column. It is the cheap first gate; the smoke test
remains the thorough last one.

What it does
------------
Parses each XML file below with a strict parser and reports any that are not
well-formed. ``xml.dom.minidom`` is used deliberately: it rejects a doubled hyphen in
a comment, which is exactly the failure mode this guards against and which some
lenient parsers tolerate.

  * docker/app/conf/server.xml       -- Tomcat server config (STIG overlay)
  * src/main/webapp/META-INF/context.xml
  * src/main/webapp/WEB-INF/web.xml
  * src/main/webapp/WEB-INF/tlds/*.tld

This is a structural well-formedness check, not schema validation. It confirms the
XML parses; it does not check it against the servlet or taglib schema.

Modes
-----
Default is REPORT-ONLY: it prints findings and exits 0. Pass ``--strict`` (or set
``STRICT=1``) to exit 1 when any file is malformed.

Exit codes: 0 = all well-formed (or report-only), 1 = a file is malformed under
--strict, 2 = bad usage, or a listed file is missing.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import glob
import os
import sys
import xml.dom.minidom
import xml.parsers.expat

# Files and globs to check, relative to the repository root. Globs are expanded; a
# literal path that does not exist is an error (exit 2), because a check that
# silently skips a moved or renamed file is worse than no check.
TARGETS = [
    "docker/app/conf/server.xml",
    "src/main/webapp/META-INF/context.xml",
    "src/main/webapp/WEB-INF/web.xml",
    "src/main/webapp/WEB-INF/tlds/*.tld",
]


def _fail_usage(message: str):
    print(message, file=sys.stderr)
    raise SystemExit(2)


def resolve(root_dir: str) -> list[str]:
    """Expand TARGETS to concrete file paths under root_dir. A literal (non-glob)
    target that matches nothing is a hard error; a glob matching nothing is allowed
    (a project may legitimately carry no .tld files)."""
    files: list[str] = []
    for target in TARGETS:
        pattern = os.path.join(root_dir, target)
        matches = sorted(glob.glob(pattern))
        is_glob = any(ch in target for ch in "*?[")
        if not matches and not is_glob:
            _fail_usage(f"ERROR: expected file not found: {target}")
        files.extend(matches)
    return files


def check(path: str) -> str | None:
    """Return an error string if the file is not well-formed, else None."""
    try:
        xml.dom.minidom.parse(path)
        return None
    except xml.parsers.expat.ExpatError as exc:
        # ExpatError carries 1-based line and column; surface both.
        return f"line {exc.lineno}, column {exc.offset + 1}: {xml.parsers.expat.ErrorString(exc.code)}"
    except OSError as exc:
        return f"could not read: {exc}"


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    flags = {a for a in sys.argv[1:] if a.startswith("-")}

    if flags - {"--strict"} or len(args) > 1:
        _fail_usage(f"usage: {os.path.basename(sys.argv[0])} [ROOT] [--strict]")

    root_dir = args[0] if args else "."
    strict = "--strict" in flags or os.environ.get("STRICT") == "1"

    files = resolve(root_dir)
    bad: list[tuple[str, str]] = []
    for path in files:
        err = check(path)
        rel = os.path.relpath(path, root_dir)
        if err:
            bad.append((rel, err))

    lines = [f"XML well-formedness check ({len(files)} files)", ""]
    if bad:
        for rel, err in bad:
            lines.append(f"  MALFORMED  {rel}")
            lines.append(f"             {err}")
        lines.append("")
        lines.append(f"Summary: {len(bad)} of {len(files)} malformed.")
    else:
        lines.append(f"Summary: all {len(files)} well-formed.")
    report = "\n".join(lines)
    print(report)

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write("## XML well-formedness\n\n")
            if bad:
                fh.write(f"**{len(bad)} of {len(files)} malformed.**\n\n")
                fh.write("| File | Problem |\n|---|---|\n")
                for rel, err in bad:
                    fh.write(f"| `{rel}` | {err} |\n")
            else:
                fh.write(f"All {len(files)} files well-formed.\n")

    if strict and bad:
        print(f"\nFAIL (--strict): {len(bad)} malformed XML file(s).", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
