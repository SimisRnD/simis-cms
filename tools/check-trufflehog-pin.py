#!/usr/bin/env python3
"""Report TruffleHog steps whose scanner version is unpinned or disagrees with the action pin.

Background
----------
Pinning the action by SHA does not pin the scanner. ``trufflesecurity/trufflehog``'s
``action.yml`` is a composite action whose last step is::

    docker run ... "${IMAGE}:${VERSION}" git file:///tmp/ ...

``IMAGE`` defaults to ``ghcr.io/trufflesecurity/trufflehog`` and ``VERSION`` comes from a
``version`` input that **defaults to "latest"**. A workflow that sets the ``uses:`` SHA and
leaves ``version`` alone therefore re-pulls whatever tag ``latest`` points at on every run,
and the SHA comment describes only the wrapper.

That is not theoretical. Run 36359238465 pinned ``# v3.97.5`` and the scan it produced
reported ``trufflehog_version 3.97.9``. It is also what broke the monthly container publish
with no repository change (issue #2066): the 2026-09-01 scheduled run scanned base
``8f1d8003`` on 3.97.1 and reported nothing, and the 2026-10-01 run scanned that same base
on 3.97.9, whose reworked Postgres verification path emits an unverifiable connection string
as a finding instead of discarding it. A secret-scanning gate that can change verdict
without the repository changing cannot be reasoned about, and the gate in
``secret-scan-pr.yml`` is a required status check.

Setting the ``version`` input fixes that, and introduces a second thing to keep in step.
Dependabot's github-actions ecosystem updates *references to actions* -- the ``uses:`` line
and its SHA comment. It has no notion of a ``with:`` input, so it will move the wrapper and
leave the scanner behind. Nothing else would notice: the workflow would claim one version in
its SHA comment and run another, which is the exact failure the pin exists to prevent,
reintroduced one Dependabot PR later.

What it does
------------
For every TruffleHog step across ``.github/workflows/``, checks that the step declares a
``version`` input and that it equals the version in the ``uses:`` line's SHA comment.

Three findings are reported, and they are distinct problems:

  * ``unpinned``  -- no ``version`` input, so the scanner floats to ``latest``.
  * ``drift``     -- ``version`` disagrees with the SHA comment (the Dependabot-bump case).
  * ``uncommented`` -- the ``uses:`` SHA carries no ``# vX.Y.Z`` comment, so there is
    nothing to check the input against.

The fix for a drift finding is a one-line edit to the ``version`` input in the same PR that
bumps the SHA; the fix for an unpinned finding is to add the input. The comment is treated as
authoritative because it is what Dependabot rewrites.

Deliberately NOT checked: whether the version is the newest published, or whether the SHA
really is that tag. Keeping current is Dependabot's job, and verifying a SHA-to-tag mapping
needs the network -- every other gate in dependency-drift.yml runs offline on the runner's
stock interpreter, and this one does too.

Modes
-----
Default is REPORT-ONLY: it prints the findings and exits 0. Pass ``--strict`` (or set
``STRICT=1``) to exit 1 when any finding is reported.

Exit codes: 0 = consistent (or report-only), 1 = findings under --strict, 2 = bad usage, or
no TruffleHog step was found at all -- which means the scan was removed or this tool's
matching has gone stale, and either way silence would be the wrong answer.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import glob
import os
import re
import sys

WORKFLOW_GLOB = os.path.join(".github", "workflows", "*.y*ml")

ACTION = "trufflesecurity/trufflehog"

# The uses: line, with the SHA comment captured separately -- a YAML parser drops comments,
# which is precisely the half Dependabot rewrites, so this is matched textually.
USES_RE = re.compile(
    r"^(?P<indent>\s*)-?\s*uses:\s*" + re.escape(ACTION) + r"@(?P<ref>\S+)"
    r"(?:\s*#\s*v?(?P<version>\d+\.\d+\.\d+))?\s*$"
)

# A version input inside the step's with: block. Quotes are optional in YAML; strip them.
VERSION_INPUT_RE = re.compile(r"^\s*version:\s*[\"']?(?P<value>[^\"'#\s]+)[\"']?")

# Any line that starts a new step, or closes the step's block by dedenting.
NEXT_STEP_RE = re.compile(r"^\s*-\s")


def _fail(message: str):
    """Exit 2 -- a broken checkout or stale matching, distinct from finding drift."""
    print(message, file=sys.stderr)
    raise SystemExit(2)


def _step_version_input(lines, start: int, indent: int):
    """Return the version input declared by the step whose uses: line is at ``start``.

    Walks forward to the end of that step -- the next list item at the same or shallower
    indentation, or any content line indented less than the uses: line -- so a version input
    belonging to a later step is never credited to this one.
    """
    for line in lines[start + 1:]:
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        current_indent = len(line) - len(line.lstrip())
        if NEXT_STEP_RE.match(line) and current_indent <= indent:
            return None
        if current_indent < indent:
            return None
        match = VERSION_INPUT_RE.match(line)
        if match:
            return match.group("value")
    return None


def scan(root_dir: str):
    """Return (findings, steps_seen) for every TruffleHog step under .github/workflows/."""
    findings = []
    steps_seen = 0

    for path in sorted(glob.glob(os.path.join(root_dir, WORKFLOW_GLOB))):
        rel = os.path.relpath(path, root_dir)
        try:
            with open(path, encoding="utf-8") as fh:
                lines = fh.read().splitlines()
        except OSError as exc:
            _fail(f"ERROR: cannot read {rel}: {exc}")

        for number, line in enumerate(lines):
            match = USES_RE.match(line)
            if not match:
                continue
            steps_seen += 1
            indent = len(match.group("indent"))
            pinned = match.group("version")
            declared = _step_version_input(lines, number, indent)

            if pinned is None:
                findings.append((rel, number + 1, "uncommented", declared, None))
            elif declared is None:
                findings.append((rel, number + 1, "unpinned", None, pinned))
            elif declared != pinned:
                findings.append((rel, number + 1, "drift", declared, pinned))

    return findings, steps_seen


EXPLANATION = {
    "unpinned": (
        "no `version` input -- the scanner floats to `latest`",
        'add `version: "{pinned}"` to the step\'s `with:` block',
    ),
    "drift": (
        "`version` input does not match the pinned action version",
        "set the `version` input to {pinned}",
    ),
    "uncommented": (
        "the pinned SHA carries no `# vX.Y.Z` comment to check against",
        "annotate the `uses:` SHA with the release tag it points at",
    ),
}


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    flags = {a for a in sys.argv[1:] if a.startswith("-")}

    unknown = flags - {"--strict"}
    if unknown or len(args) > 1:
        print(f"usage: {os.path.basename(sys.argv[0])} [ROOT] [--strict]", file=sys.stderr)
        return 2

    root_dir = args[0] if args else "."
    strict = "--strict" in flags or os.environ.get("STRICT") == "1"

    findings, steps_seen = scan(root_dir)

    if steps_seen == 0:
        _fail(
            f"ERROR: no {ACTION} step found under .github/workflows/. Either the secret scan "
            "was removed, or this tool no longer recognises how it is declared."
        )

    lines = [
        "TruffleHog scanner pin (the `version` input vs the pinned action version)",
        "",
        f"  TruffleHog steps found : {steps_seen}",
        f"  findings               : {len(findings)}",
        "",
    ]
    for rel, number, kind, declared, pinned in findings:
        summary, fix = EXPLANATION[kind]
        lines.append(f"  {rel}:{number} -- {summary}")
        if kind == "drift":
            lines.append(f"      input declares {declared}, action pins {pinned}")
        lines.append(f"      Fix: {fix.format(pinned=pinned)}")
        lines.append("")

    if not findings:
        lines.append("Summary: every TruffleHog step pins its scanner to the version it claims.")
    else:
        lines.append(
            f"Summary: {len(findings)} TruffleHog step(s) do not pin the scanner they claim to run."
        )
    print("\n".join(lines))

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write("## TruffleHog scanner pin\n\n")
            if not findings:
                fh.write(
                    f"**Pinned.** All {steps_seen} TruffleHog step(s) run the scanner version "
                    "their action pin names.\n\n"
                )
            else:
                fh.write(
                    "**The scanner is not pinned to the version the workflow claims.** The action "
                    "SHA pins the wrapper; the `version` input pins the scanner, and Dependabot "
                    "only updates the former.\n\n"
                )
                fh.write("| Workflow | Line | Problem | `version` input | Action pin |\n")
                fh.write("|---|---|---|---|---|\n")
                for rel, number, kind, declared, pinned in findings:
                    fh.write(
                        f"| `{rel}` | {number} | {kind} | "
                        f"{'`' + declared + '`' if declared else '_absent_'} | "
                        f"{'`' + pinned + '`' if pinned else '_uncommented_'} |\n"
                    )
                fh.write("\n")

    if strict and findings:
        print(
            f"\nFAIL (--strict): {len(findings)} TruffleHog step(s) do not pin the scanner "
            "version they claim to run.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
