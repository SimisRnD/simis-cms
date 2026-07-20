#!/usr/bin/env bash
#
# check-security-coverage.sh
#
# Enforce a minimum unit-test coverage floor on the security/privacy-critical
# classes that were hardened during the 2026 program. The intent is narrow: if a
# unit test protecting one of these classes is silently deleted or gutted, the
# class's coverage drops and this gate fails the build. It is NOT a project-wide
# coverage target -- only the explicitly listed classes are checked.
#
# It reads the JaCoCo CSV report produced by the `ci-test` Ant target
# (target/coverage-reports/jacoco.csv) and compares each listed class against a
# per-class floor.
#
# Why INSTRUCTION coverage (not line coverage):
#   The build compiles with debug="off" (see the `compile` target in build.xml),
#   so no line-number debug info is emitted and JaCoCo's LINE counters are all 0.
#   Instruction coverage is the finest-grained signal available in this build.
#
# Why this is a script and not a JaCoCo "check" rule:
#   The JaCoCo *Ant* integration ships coverage/report/instrument/dump/agent/
#   merge tasks only -- there is no `check` task (coverage-check goals exist only
#   in the Maven and Gradle plugins). So the floor is enforced here instead.
#
# Fail-closed by design:
#   - A listed class that is BELOW its floor          -> FAIL (regression).
#   - A listed class MISSING from the report entirely -> FAIL (class renamed or
#     removed, or the test that made it appear is gone). We would rather fail
#     loudly than silently stop guarding a class.
#   - A missing/empty CSV                              -> FAIL (report never ran).
#
# Usage:
#   .github/scripts/check-security-coverage.sh [path/to/jacoco.csv]
#
#   Default CSV path: target/coverage-reports/jacoco.csv (relative to CWD).
#   Env JACOCO_CSV        overrides the path (arg wins over env if both given).
#   Env COVERAGE_FLOOR_MIN raises every floor to at least this value. It can only
#                          make the gate STRICTER, never weaker -- handy for
#                          confirming the gate is live (set it to 0.999 and every
#                          class should fail).
#
# Exit codes: 0 = all pass, 1 = a class is below floor or missing, 2 = bad usage
#             / environment (e.g. CSV not found).

set -euo pipefail

# --- Security/privacy-critical classes and their instruction-coverage floors ---
#
# Format: "<fully.qualified.ClassName>,<floor 0..1>"
#
# The five classes hardened in the 2026 program sit at 77-99% instruction
# coverage today; a uniform 0.50 floor leaves generous headroom for benign
# refactors while still tripping if the guarding unit test disappears.
#
# NumberCommand is deliberately lower (0.30): its hardened numeric-filter methods
# ARE unit-tested, but the class also carries untested legacy numeric helpers
# that dilute the class-level ratio to ~40%. A 0.30 floor keeps regression
# protection on the tested paths without demanding coverage of the legacy code.
#
# DoNotTrackCommand.isDoNotTrack() -- the Do-Not-Track / Global Privacy Control
# honoring logic -- is covered by DoNotTrackCommandTest and shares the 0.50
# floor. The class arrived with PR #136 (feature/honor-dnt), so this entry is
# valid only once #136 is in the lineage; merging it before then fail-closes the
# gate on a missing class. (See the PR description for merge-order details.)
TARGETS='
com.simisinc.platform.application.IpAddressCommand,0.50
com.simisinc.platform.application.SecretCryptoCommand,0.50
com.simisinc.platform.application.UserPasswordCommand,0.50
com.simisinc.platform.application.admin.AnalyticsTrackingIdCommand,0.50
com.simisinc.platform.application.cms.UrlCommand,0.50
com.simisinc.platform.application.cms.NumberCommand,0.30
com.simisinc.platform.application.DoNotTrackCommand,0.50
'

CSV="${1:-${JACOCO_CSV:-target/coverage-reports/jacoco.csv}}"
FLOOR_MIN="${COVERAGE_FLOOR_MIN:-0}"

if [ ! -s "$CSV" ]; then
  echo "ERROR: JaCoCo CSV report not found or empty: $CSV" >&2
  echo "       Run the 'ci-test' Ant target first (it writes target/coverage-reports/jacoco.csv)." >&2
  exit 2
fi

# The whole comparison happens in awk: it reads the target list first (comma-
# separated, same delimiter as the CSV), then the JaCoCo CSV, builds each row's
# fully-qualified class name from PACKAGE (col 2, slash-separated) + CLASS
# (col 3), and computes instruction coverage = COVERED / (MISSED + COVERED).
awk -F',' -v floormin="$FLOOR_MIN" '
  # First stream: the target list.  "<fqcn>,<floor>"
  FNR == NR {
    line = $0
    gsub(/[ \t\r]/, "", line)
    if (line == "") next
    split(line, kv, ",")
    fqcn = kv[1]; fl = kv[2] + 0
    floor[fqcn] = fl
    order[++n] = fqcn
    next
  }
  # Second stream: the JaCoCo CSV.  Skip its header row.
  FNR == 1 { next }
  {
    pkg = $2; gsub(/\//, ".", pkg)
    fqcn = pkg "." $3
    if (fqcn in floor) {
      missed = $4 + 0; covered = $5 + 0; total = missed + covered
      ratio = (total > 0) ? covered / total : 0
      found[fqcn] = 1
      ratioOf[fqcn] = ratio
      coveredOf[fqcn] = covered
      totalOf[fqcn] = total
    }
  }
  END {
    rc = 0; below = 0; missing = 0; checked = 0
    printf "Security-critical unit-test coverage gate (JaCoCo INSTRUCTION coverage)\n"
    printf "  report: %s\n", CSVPATH
    if (floormin + 0 > 0)
      printf "  floor override active: every floor raised to at least %.1f%%\n", floormin * 100
    printf "\n"
    printf "  %-68s %9s %7s   %s\n", "CLASS", "COVERAGE", "FLOOR", "RESULT"
    printf "  %-68s %9s %7s   %s\n", "-----", "--------", "-----", "------"
    for (i = 1; i <= n; i++) {
      t = order[i]; checked++
      eff = floor[t]; if (floormin + 0 > eff) eff = floormin + 0
      if (!(t in found)) {
        printf "  %-68s %9s %6.1f%%   %s\n", t, "n/a", eff * 100, "MISSING"
        missing++; rc = 1
        continue
      }
      pct = ratioOf[t] * 100
      # +1e-9 tolerance so a class sitting exactly on its floor passes.
      if (ratioOf[t] + 1e-9 < eff) { result = "FAIL"; below++; rc = 1 }
      else { result = "PASS" }
      printf "  %-68s %7.1f%% %6.1f%%   %s  (%d/%d instr)\n",
             t, pct, eff * 100, result, coveredOf[t], totalOf[t]
    }
    printf "\n"
    if (rc == 0)
      printf "RESULT: PASS (%d checked, all at or above floor)\n", checked
    else
      printf "RESULT: FAIL (%d checked, %d below floor, %d missing from report)\n",
             checked, below, missing
    exit rc
  }
' CSVPATH="$CSV" <(printf '%s\n' "$TARGETS") "$CSV"
