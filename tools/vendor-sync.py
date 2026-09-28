#!/usr/bin/env python3
"""Re-vendor the jars pom.xml has moved ahead of, from Maven Central, with verified checksums.

Background
----------
The shipped WAR is built by Ant from the committed ``lib/build/**/*.jar`` files; pom.xml only
drives the IDE, Dependabot and the SBOM. A merged Dependabot PR therefore moves the pom and
nothing else, and two gates stop it from merging half-done:

  * tools/check-dependency-drift.py --strict   the pom and the vendored jar disagree
  * tools/check-vendored-provenance.py         a jar's bytes are not in lib/PROVENANCE.sha256

Both are read-only reporters by design. This is the writer that completes the update (issue
2044): it fetches exactly the jars the drift gate reports, checks their bytes, puts them in
place, and records their hashes.

What it does
------------
  1. Refuses to start unless lib/PROVENANCE.sha256 already matches the tree. Everything it
     writes is recorded in that manifest, so any unexplained difference already there would be
     blessed along with the new jars -- which is why this never falls back on the provenance
     tool's --write, which regenerates the whole manifest from whatever is on disk.
  2. Computes the drift set with check-dependency-drift.py's own find_drift(), and skips its
     ALLOWLIST entries unconditionally: each one is there because a straight version swap
     breaks the build. Where two groups share an artifactId (jackson 2.x and 3.x), the jar is
     matched by the groupId it records, and a jar that cannot be matched is refused rather
     than guessed at -- replacing the wrong one would be silent.
  3. Downloads ``<artifactId>-<version>.jar`` for each drifted artifact from Maven Central
     (https://repo1.maven.org/maven2 only), and verifies it against the published .sha1, plus
     the .sha256 and .sha512 wherever Central publishes them. A .sha1 is required.
  4. Only when EVERY download has verified does it write anything: the new jar goes into the
     old jar's directory (so a directory holding only that jar is never emptied and removed),
     then the old jar is deleted.
  5. Updates lib/PROVENANCE.sha256 for exactly the jars it replaced, from the hashes of the
     verified bytes, after checking the tree on disk hashes to precisely that manifest.
  6. Re-runs both gates and exits non-zero if either would still fail.

It does not commit, and it does not resolve transitive dependencies: a new version that needs
a newer transitive jar will surface in ``ant -lib lib/tests ci-test``, not here.

Usage
-----
  python3 tools/vendor-sync.py [root] [--dry-run] [--mirror DIR]

  --dry-run     print the plan (artifacts, versions, URLs) and change nothing; no network
  --mirror DIR  read artifacts from a local directory in Maven repository layout instead of
                Maven Central (offline use and tests). Remote fetches only ever go to Central,
                so a checksum is never verified against the same untrusted host that served
                the jar.

Exit codes: 0 = nothing to do, or synced and both gates pass; 1 = a verification failure, a
refusal, or a gate that still fails; 2 = bad usage or an input this tool reads is missing.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

CENTRAL = "https://repo1.maven.org/maven2"
USER_AGENT = "simis-cms-vendor-sync (+https://github.com/SimisRnD/simis-cms)"
TIMEOUT_SECONDS = 60
MAX_JAR_BYTES = 256 * 1024 * 1024  # far above any vendored jar; stops a runaway download
# (published checksum extension, hashlib name, hex length). sha1 is the one Central always has.
CHECKSUMS = (("sha1", "sha1", 40), ("sha256", "sha256", 64), ("sha512", "sha512", 128))
# Maven coordinates are path segments of the download URL and of the file written to disk, so
# anything outside this set -- a slash, "..", an unresolved ${property} -- is refused outright.
SAFE_COORDINATE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")

TOOLS = Path(__file__).resolve().parent


def _load(name: str, filename: str):
    spec = importlib.util.spec_from_file_location(name, TOOLS / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


drift_tool = _load("check_dependency_drift", "check-dependency-drift.py")
provenance_tool = _load("check_vendored_provenance", "check-vendored-provenance.py")


class Refusal(Exception):
    """A condition this tool will not proceed past; nothing has been written when it is raised."""


def usage_error(message: str) -> "NoReturn":
    print("error: " + message, file=sys.stderr)
    sys.exit(2)


# --- planning ------------------------------------------------------------------------------


def plan_sync(root: Path):
    """Return (items, skipped): what would be re-vendored, and allowlisted drift left alone.

    Each item is a dict with artifact, group, old_version, new_version, old_path, new_path.
    Raises Refusal for a drifted artifact that cannot be safely turned into a download.
    """
    pom_path = root / "pom.xml"
    lib_build = root / "lib" / "build"
    if not pom_path.is_file():
        usage_error(f"{pom_path} not found -- wrong root?")
    if not lib_build.is_dir():
        usage_error(f"{lib_build} not found -- wrong root?")

    found = drift_tool.find_drift(drift_tool.pom_dependencies(str(pom_path)),
                                  drift_tool.vendored_jars(str(lib_build)))
    if found["ambiguous"]:
        raise Refusal("some vendored jars cannot be matched to a single pom declaration, so "
                      "which file to replace is unknown -- see check-dependency-drift.py's "
                      "AMBIGUOUS section:\n  " + "\n  ".join(
                          f"{os.path.basename(j['path'])}: {why}" for j, why in found["ambiguous"]))

    items, skipped = [], []
    lib_build_resolved = lib_build.resolve()
    for dep, jar in found["drift"]:
        artifact, group = dep["artifact"], dep["group"]
        pom_version, jar_version = dep["version"], jar["version"]
        if artifact in drift_tool.ALLOWLIST:
            skipped.append((artifact, pom_version, jar_version))
            continue
        for label, value in (("groupId", group), ("artifactId", artifact),
                             ("version", pom_version)):
            if not value or not SAFE_COORDINATE.match(value):
                raise Refusal(f"{artifact}: {label} {value!r} is not a plain Maven coordinate "
                              "(unresolved property, range, or path characters) -- fix pom.xml")
        if pom_version.endswith("-SNAPSHOT"):
            raise Refusal(f"{artifact}: {pom_version} is a SNAPSHOT; Maven Central does not "
                          "serve those and the WAR must not ship one")
        old_path = Path(jar["path"])
        new_path = old_path.with_name(f"{artifact}-{pom_version}.jar")
        if lib_build_resolved not in new_path.resolve().parents:
            raise Refusal(f"{artifact}: {new_path} would land outside lib/build")
        if new_path.exists() and new_path != old_path:
            raise Refusal(f"{artifact}: {new_path.name} already exists and is a different jar; "
                          "replacing it would lose it")
        if any(item["new_path"] == new_path for item in items):
            raise Refusal(f"{artifact}: two drifted jars would both become {new_path.name}")
        items.append({
            "artifact": artifact, "group": group,
            "old_version": jar_version, "new_version": pom_version,
            "old_path": old_path, "new_path": new_path,
        })
    return items, skipped


def artifact_relpath(item) -> str:
    """Path of the jar within a Maven repository, e.g. org/example/widget/2.0/widget-2.0.jar."""
    return "/".join(item["group"].split(".") + [
        item["artifact"], item["new_version"], f"{item['artifact']}-{item['new_version']}.jar"])


# --- fetching and verification -------------------------------------------------------------


def fetch(source: str, relpath: str, required: bool, limit: int) -> bytes | None:
    """Bytes of ``relpath`` from Maven Central (source is None) or a local mirror directory.

    Returns None for an optional file that is not published; raises Refusal otherwise.
    """
    if source is not None:
        path = Path(source) / relpath
        if not path.is_file():
            if required:
                raise Refusal(f"{relpath}: not found in mirror {source}")
            return None
        if path.stat().st_size > limit:
            raise Refusal(f"{relpath}: larger than {limit} bytes")
        return path.read_bytes()

    url = f"{CENTRAL}/{relpath}"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            data = response.read(limit + 1)
    except urllib.error.HTTPError as e:
        if e.code == 404 and not required:
            return None
        raise Refusal(f"{url}: HTTP {e.code}") from None
    except (urllib.error.URLError, OSError) as e:
        raise Refusal(f"{url}: {e}") from None
    if len(data) > limit:
        raise Refusal(f"{url}: larger than {limit} bytes")
    return data


def published_digest(text: bytes, relpath: str, ext: str, length: int) -> str:
    """The hex digest from a Maven checksum file ("<hex>" or "<hex>  <filename>")."""
    token = text.decode("ascii", "replace").strip().split()[0].lower() if text.strip() else ""
    if len(token) != length or not re.fullmatch(r"[0-9a-f]+", token):
        raise Refusal(f"{relpath}.{ext}: not a {ext} checksum: {text[:80]!r}")
    return token


def download_verified(item, source: str | None) -> tuple[bytes, list[str]]:
    """The jar's bytes, verified against every checksum Central publishes for it."""
    relpath = artifact_relpath(item)
    data = fetch(source, relpath, required=True, limit=MAX_JAR_BYTES)
    verified = []
    for ext, algorithm, length in CHECKSUMS:
        text = fetch(source, f"{relpath}.{ext}", required=(ext == "sha1"), limit=4096)
        if text is None:
            continue
        expected = published_digest(text, relpath, ext, length)
        actual = hashlib.new(algorithm, data).hexdigest()
        if actual != expected:
            raise Refusal(f"{relpath}: {ext} mismatch -- published {expected}, downloaded bytes "
                          f"hash to {actual}. Nothing has been written.")
        verified.append(ext)
    return data, verified


# --- writing -------------------------------------------------------------------------------


def write_jar(path: Path, data: bytes) -> None:
    """Write via a temp file in the same directory, so a jar is never left half-written."""
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".vendor-sync-", suffix=".tmp")
    try:
        with os.fdopen(fd, "wb") as fh:
            fh.write(data)
        os.replace(tmp, path)
    except BaseException:
        if os.path.exists(tmp):
            os.unlink(tmp)
        raise


def manifest_problems(root: Path) -> list[str]:
    """The provenance tool's findings for the tree as it stands (empty when clean)."""
    manifest = provenance_tool.read_manifest(root)
    tree = provenance_tool.current_tree(root)
    problems = [f"MISSING   {p}" for p in manifest if p not in tree]
    problems += [f"MISMATCH  {p}" for p in manifest if p in tree and tree[p] != manifest[p]]
    problems += [f"UNLISTED  {p}" for p in tree if p not in manifest]
    return sorted(problems)


def update_manifest(root: Path, before: dict[str, str], replaced: list[tuple[str, str, str]]):
    """Rewrite the manifest for the replaced jars only: (old_rel, new_rel, new_sha256)."""
    intended = dict(before)
    for old_rel, new_rel, digest in replaced:
        intended.pop(old_rel, None)
        intended[new_rel] = digest
    tree = provenance_tool.current_tree(root)  # hashes every jar now on disk, in manifest order
    if tree != intended:
        differing = sorted(set(tree.items()) ^ set(intended.items()))
        raise Refusal("the jars on disk no longer match what was verified, so the manifest was "
                      "NOT updated -- inspect lib/ before committing: "
                      + ", ".join(p for p, _ in differing[:5]))
    lines = [f"{digest}  {path}" for path, digest in tree.items()]
    (root / provenance_tool.MANIFEST).write_text("\n".join(lines) + "\n")


def run_gate(root: Path, tool: str, *args: str) -> tuple[int, str]:
    r = subprocess.run([sys.executable, str(TOOLS / tool), str(root), *args],
                       capture_output=True, text=True)
    return r.returncode, (r.stdout + r.stderr).strip()


# --- main ----------------------------------------------------------------------------------


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("root", nargs="?", default=".", help="repository root")
    ap.add_argument("--dry-run", action="store_true",
                    help="print the plan and change nothing (no network)")
    ap.add_argument("--mirror", metavar="DIR",
                    help="local Maven-layout directory to read artifacts from instead of Central")
    args = ap.parse_args()
    root = Path(args.root).resolve()
    if args.mirror is not None and not Path(args.mirror).is_dir():
        usage_error(f"--mirror {args.mirror} is not a directory")

    try:
        problems = manifest_problems(root)
        if problems:
            raise Refusal(
                f"{provenance_tool.MANIFEST} does not match the tree before any change -- "
                "resolve these first, or they would be recorded along with the new jars:\n  "
                + "\n  ".join(problems))
        before = provenance_tool.read_manifest(root)

        items, skipped = plan_sync(root)
        for artifact, pom_version, jar_version in skipped:
            print(f"SKIP  {artifact}: pom {pom_version}, ships {jar_version} -- allowlisted in "
                  "check-dependency-drift.py, never re-vendored automatically")
        if not items:
            print("Nothing to sync: every non-allowlisted vendored jar matches pom.xml.")
            return 0

        source = "Maven Central" if args.mirror is None else f"mirror {args.mirror}"
        print(f"{len(items)} jar(s) to re-vendor from {source}:")
        for item in items:
            print(f"  {item['artifact']}  {item['old_version']} -> {item['new_version']}  "
                  f"({item['old_path'].relative_to(root)} -> {item['new_path'].name})")
            print(f"      {CENTRAL if args.mirror is None else args.mirror}/{artifact_relpath(item)}")
        if args.dry_run:
            print("Dry run: nothing downloaded or written.")
            return 0

        # Every download is verified before any file is touched: one bad checksum aborts all.
        downloads = []
        for item in items:
            data, verified = download_verified(item, args.mirror)
            downloads.append((item, data))
            print(f"VERIFIED  {item['artifact']}-{item['new_version']}.jar "
                  f"({len(data)} bytes; {', '.join(verified)})")

        replaced = []
        for item, data in downloads:
            write_jar(item["new_path"], data)
            if item["old_path"] != item["new_path"] and item["old_path"].exists():
                item["old_path"].unlink()
            replaced.append((item["old_path"].relative_to(root).as_posix(),
                             item["new_path"].relative_to(root).as_posix(),
                             hashlib.sha256(data).hexdigest()))
        update_manifest(root, before, replaced)
        print(f"Updated {provenance_tool.MANIFEST} for {len(replaced)} jar(s).")
    except Refusal as e:
        print(f"REFUSED: {e}", file=sys.stderr)
        return 1

    failed = []
    for tool, gate_args in (("check-dependency-drift.py", ("--strict",)),
                            ("check-vendored-provenance.py", ())):
        code, output = run_gate(root, tool, *gate_args)
        print(f"{'PASS' if code == 0 else 'FAIL'}  {tool} {' '.join(gate_args)}".rstrip())
        if code != 0:
            failed.append(tool)
            print("      " + output.replace("\n", "\n      "))
    if failed:
        return 1
    print("Done. Review `git status lib/`, then run `ant -lib lib/tests ci-test` before committing.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
