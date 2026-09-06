#!/usr/bin/env python3
"""Check that docker/app/applicationinsights.json matches the pinned agent's schema.

Background
----------
``docker/app/applicationinsights.json`` configures the Application Insights Java
agent. The agent is baked into the image and auto-loads the file from ``/opt`` at
JVM startup (``docker/app/Dockerfile``: ``-javaagent:/opt/applicationinsights-agent.jar``).

Nothing in this repository reads, parses, or validates the file. It is not on the
Ant build's path, no test opens it, and no other gate looks at it, so a malformed or
misspelled value passes every check and reaches production unexamined.

What makes that dangerous is the failure mode. When the agent rejects the file it
does **not** stop the JVM -- it logs a startup failure and the application keeps
serving normally with telemetry silently switched off. Verified against the pinned
artifact (agent 3.7.9, SHA-256 4ab7a442...): with a config containing an invalid
``telemetryType``, the agent prints "startup failed" and the JVM still exits 0. The
site stays up; Live Metrics, request traces, and dependency spans just stop
arriving, with nothing in the application's own logs to say why.

Behaviour of agent 3.7.9 for each way this file can be wrong, established by running
the pinned jar against each case rather than from the documentation:

  ============================================  =====================================
  Config problem                                Agent 3.7.9 behaviour
  ============================================  =====================================
  Malformed JSON                                startup fails -- telemetry off
  ``telemetryType`` not an accepted value       startup fails -- telemetry off
  override missing ``percentage``               startup fails -- telemetry off
  ``percentage`` below 0                        startup fails -- telemetry off
  ``percentage`` above 100                      WARN, silently rounded down to 100
  unrecognized property name                    WARN, the property is ignored
  ============================================  =====================================

The last two are the quietest: the agent starts "successfully" and the setting is
simply not in effect. A misspelled ``"overides"`` or ``"telemetryTpye"`` costs
nothing at boot and shows up only as a bill that never went down.

The telemetryType trap
----------------------
The accepted values are **lowercase**: ``request``, ``dependency``, ``trace``,
``exception``. The Java enum ``Configuration$SamplingTelemetryType`` declares its
constants in the usual uppercase (``REQUEST``, ``DEPENDENCY``, ...), but those are
constant names, not the tokens Jackson accepts. Feeding the agent ``"DEPENDENCY"``
fails startup outright:

    Cannot deserialize value of type ...Configuration$SamplingTelemetryType from
    String "DEPENDENCY": not one of the values accepted for Enum class:
    [dependency, trace, exception, request]

This is an easy inversion to make -- reading the constant names out of the jar with
``javap`` gives the uppercase spelling, and it looks authoritative. PR #1906, which
introduced the ``sampling.overrides`` block, recorded its schema verification that
way ("Configuration$SamplingTelemetryType declares DEPENDENCY") while correctly
shipping lowercase ``"dependency"`` in the file. The value on disk is right; the note
describing it points at a spelling that would break the agent. That gap between what
was written down and what the agent accepts is the reason this check exists, and it
is why the check is case-sensitive rather than normalising before comparing.

What it does
------------
Validates ``docker/app/applicationinsights.json``:

  * it parses as JSON (reporting line and column when it does not);
  * ``sampling`` and each ``sampling.overrides`` entry use only property names the
    agent recognises;
  * each override's ``telemetryType`` is one of the four accepted values, matched
    case-sensitively;
  * each override has a ``percentage``, and every ``percentage`` is a number in
    0..100.

It also compares the agent version pinned in ``docker/app/Dockerfile`` against the
version this file's schema was verified against. The property lists below were read
out of agent 3.7.9 -- from the agent's own "known properties" errors, not from the
docs -- so a version bump means they need re-checking. The drift finding says so
rather than letting the check quietly assert a stale schema.

Scope is deliberately the ``sampling`` block, which is what PR #1906 added and what
was verified against the artifact. Other blocks (``role``, ``selfDiagnostics``, ...)
are checked for JSON validity only; enumerating the agent's entire configuration
surface here would mean guessing at property lists that were never verified, and a
gate that invents its own schema produces false failures on valid config.

Modes
-----
Default is REPORT-ONLY: it prints findings and exits 0. Pass ``--strict`` (or set
``STRICT=1``) to exit 1 when there is any finding.

Exit codes: 0 = clean (or report-only), 1 = findings under --strict, 2 = bad usage,
or the config file is missing.

This is a read-only reporter. It changes no files.
"""
from __future__ import annotations

import json
import os
import re
import sys

CONFIG_PATH = "docker/app/applicationinsights.json"
DOCKERFILE_PATH = "docker/app/Dockerfile"

# The agent release whose schema the constants below were read from. Cross-checked
# against the Dockerfile's ARG APPLICATIONINSIGHTS_VERSION so a bump is flagged.
VERIFIED_AGENT_VERSION = "3.7.9"

# Accepted telemetryType tokens, lowercase, exactly as the agent's own deserialization
# error enumerates them. NOT the uppercase Java constant names -- see the module
# docstring; the uppercase spellings fail agent startup.
TELEMETRY_TYPES = ("dependency", "exception", "request", "trace")

# Property names the agent recognises, quoted from its "known properties" warnings.
#   Configuration$Sampling         -- 4 known properties
#   Configuration$SamplingOverride -- 7 known properties
SAMPLING_KEYS = frozenset({"percentage", "requestsPerSecond", "limitPerSecond", "overrides"})
SAMPLING_OVERRIDE_KEYS = frozenset({
    "telemetryType", "attributes", "percentage", "id",
    "telemetryKind", "spanKind", "includingStandaloneTelemetry",
})


def _fail_usage(message: str):
    print(message, file=sys.stderr)
    raise SystemExit(2)


def pinned_agent_version(root_dir: str) -> str | None:
    """Read ARG APPLICATIONINSIGHTS_VERSION from the Dockerfile, or None if the
    Dockerfile or the ARG is absent (reported as a finding, not a crash)."""
    path = os.path.join(root_dir, DOCKERFILE_PATH)
    try:
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
    except OSError:
        return None
    m = re.search(r"^\s*ARG\s+APPLICATIONINSIGHTS_VERSION\s*=\s*(\S+)", text, re.MULTILINE)
    return m.group(1) if m else None


def _check_percentage(value, where: str, findings: list[tuple[str, str]]) -> None:
    """A percentage must be a number in 0..100. Below 0 fails agent startup; above
    100 is silently rounded down to 100, which is a typo that costs money quietly."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        findings.append(("BAD TYPE", f'{where}: "percentage" must be a number, found {json.dumps(value)}.'))
        return
    if value < 0:
        findings.append(("OUT OF RANGE", f'{where}: "percentage" is {value}; below 0 fails agent startup.'))
    elif value > 100:
        findings.append(("OUT OF RANGE", f'{where}: "percentage" is {value}; the agent rounds this down to 100.'))


def _check_override(entry, index: int, findings: list[tuple[str, str]]) -> None:
    where = f"sampling.overrides[{index}]"
    if not isinstance(entry, dict):
        findings.append(("BAD TYPE", f"{where}: expected an object, found {type(entry).__name__}."))
        return

    for key in sorted(set(entry) - SAMPLING_OVERRIDE_KEYS):
        findings.append((
            "UNKNOWN KEY",
            f'{where}: "{key}" is not a sampling-override property; '
            f"the agent warns once and ignores it. "
            f"Known: {', '.join(sorted(SAMPLING_OVERRIDE_KEYS))}.",
        ))

    if "telemetryType" in entry:
        value = entry["telemetryType"]
        if not isinstance(value, str):
            findings.append(("BAD TYPE", f'{where}: "telemetryType" must be a string, found {json.dumps(value)}.'))
        elif value not in TELEMETRY_TYPES:
            hint = ""
            if value.lower() in TELEMETRY_TYPES:
                hint = f' The accepted spelling is lowercase "{value.lower()}".'
            findings.append((
                "BAD VALUE",
                f'{where}: "telemetryType" is "{value}", which fails agent startup. '
                f"Accepted: {', '.join(TELEMETRY_TYPES)}.{hint}",
            ))

    if "percentage" not in entry:
        findings.append((
            "MISSING KEY",
            f'{where}: no "percentage"; a sampling override without one fails agent startup.',
        ))
    else:
        _check_percentage(entry["percentage"], where, findings)


def check(config, findings: list[tuple[str, str]]) -> None:
    """Validate the parsed config document, appending findings."""
    if not isinstance(config, dict):
        findings.append(("BAD TYPE", f"top level: expected an object, found {type(config).__name__}."))
        return

    sampling = config.get("sampling")
    if sampling is None:
        return
    if not isinstance(sampling, dict):
        findings.append(("BAD TYPE", f'"sampling": expected an object, found {type(sampling).__name__}.'))
        return

    for key in sorted(set(sampling) - SAMPLING_KEYS):
        findings.append((
            "UNKNOWN KEY",
            f'sampling: "{key}" is not a sampling property; the agent warns once and '
            f"ignores it. Known: {', '.join(sorted(SAMPLING_KEYS))}.",
        ))

    if "percentage" in sampling:
        _check_percentage(sampling["percentage"], "sampling", findings)

    overrides = sampling.get("overrides")
    if overrides is None:
        return
    if not isinstance(overrides, list):
        findings.append(("BAD TYPE", f'sampling.overrides: expected an array, found {type(overrides).__name__}.'))
        return
    for index, entry in enumerate(overrides):
        _check_override(entry, index, findings)


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    flags = {a for a in sys.argv[1:] if a.startswith("-")}

    if flags - {"--strict"} or len(args) > 1:
        _fail_usage(f"usage: {os.path.basename(sys.argv[0])} [ROOT] [--strict]")

    root_dir = args[0] if args else "."
    strict = "--strict" in flags or os.environ.get("STRICT") == "1"

    path = os.path.join(root_dir, CONFIG_PATH)
    # A check that silently skips a moved or renamed file is worse than no check.
    if not os.path.isfile(path):
        _fail_usage(f"ERROR: expected file not found: {CONFIG_PATH}")

    findings: list[tuple[str, str]] = []

    try:
        with open(path, encoding="utf-8") as fh:
            config = json.load(fh)
    except json.JSONDecodeError as exc:
        findings.append((
            "MALFORMED",
            f"line {exc.lineno}, column {exc.colno}: {exc.msg}. "
            "The agent refuses a malformed file and starts with telemetry disabled.",
        ))
        config = None
    except OSError as exc:
        _fail_usage(f"ERROR: could not read {CONFIG_PATH}: {exc}")

    if config is not None:
        check(config, findings)

    # The schema above was read from one specific agent release; say so if the image
    # now pins a different one, rather than asserting a schema nobody has re-checked.
    pinned = pinned_agent_version(root_dir)
    if pinned is None:
        findings.append((
            "VERSION",
            f"could not read ARG APPLICATIONINSIGHTS_VERSION from {DOCKERFILE_PATH}; "
            f"cannot confirm this check's schema matches the pinned agent.",
        ))
    elif pinned != VERIFIED_AGENT_VERSION:
        findings.append((
            "VERSION",
            f"{DOCKERFILE_PATH} pins agent {pinned}, but this check's schema was verified "
            f"against {VERIFIED_AGENT_VERSION}. Re-check the accepted telemetryType values and "
            f"property names against the new artifact, then update VERIFIED_AGENT_VERSION.",
        ))

    lines = [f"Application Insights config check ({CONFIG_PATH})", ""]
    if findings:
        for label, message in findings:
            lines.append(f"  {label:<13}{message}")
        lines.append("")
        lines.append(f"Summary: {len(findings)} finding(s).")
    else:
        lines.append(f"Summary: valid against agent {VERIFIED_AGENT_VERSION}.")
    print("\n".join(lines))

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as fh:
            fh.write("## Application Insights config\n\n")
            if findings:
                fh.write(f"**{len(findings)} finding(s) in `{CONFIG_PATH}`.**\n\n")
                fh.write("| Problem | Detail |\n|---|---|\n")
                for label, message in findings:
                    fh.write(f"| {label} | {message} |\n")
            else:
                fh.write(f"`{CONFIG_PATH}` is valid against agent {VERIFIED_AGENT_VERSION}.\n")

    if strict and findings:
        print(f"\nFAIL (--strict): {len(findings)} finding(s) in {CONFIG_PATH}.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
