#!/usr/bin/env python3
"""Verify every vendored jar under lib/ against the committed SHA-256 manifest.

Background
----------
The shipped WAR is assembled from the committed ``lib/**/*.jar`` files, so a
tampered vendored jar goes straight into the artifact -- the classic attack on
vendored dependencies, and one nothing else in the pipeline would catch: the
drift check compares *versions*, not bytes, and the SBOM describes whatever the
jar claims to be. ``lib/PROVENANCE.sha256`` pins the exact bytes of every
vendored jar; this script verifies the tree against it in both directions:

  * MISMATCH  a jar's hash differs from the manifest        (tampered or swapped)
  * MISSING   the manifest lists a jar that is not on disk  (deleted)
  * UNLISTED  a jar exists on disk but not in the manifest  (added without provenance)

UNLISTED matters as much as MISMATCH: without it, adding a new unmanifested jar
would bypass the check entirely.

Modes
-----
Default verifies and reports; it exits 1 on any finding (there is no report-only
mode -- an integrity check that cannot fail is theater). ``--write`` regenerates
the manifest from the current tree; run it after any deliberate re-vendoring and
commit the result alongside the jar change, which makes every vendored-jar
change show up in review as a hash diff.

The manifest uses the standard ``sha256sum`` format (hash, two spaces, path
relative to the repository root, sorted), so ``sha256sum -c lib/PROVENANCE.sha256``
works as an independent cross-check on any GNU system.
"""

import argparse
import hashlib
import sys
from pathlib import Path

MANIFEST = "lib/PROVENANCE.sha256"


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def current_tree(root: Path) -> dict[str, str]:
    """Relative-posix-path -> sha256 for every jar under lib/, sorted."""
    jars = sorted(root.glob("lib/**/*.jar"))
    return {p.relative_to(root).as_posix(): sha256_of(p) for p in jars}


def read_manifest(root: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    manifest = root / MANIFEST
    if not manifest.exists():
        sys.exit(f"ERROR: {MANIFEST} not found -- generate it with --write")
    for n, line in enumerate(manifest.read_text().splitlines(), 1):
        if not line.strip():
            continue
        # sha256sum format: 64 hex chars, two spaces, path
        digest, sep, path = line.partition("  ")
        if not sep or len(digest) != 64:
            sys.exit(f"ERROR: {MANIFEST}:{n}: malformed line: {line!r}")
        entries[path] = digest
    return entries


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("root", nargs="?", default=".", help="repository root")
    ap.add_argument("--write", action="store_true",
                    help="regenerate the manifest from the current tree")
    args = ap.parse_args()
    root = Path(args.root).resolve()

    tree = current_tree(root)
    if not tree:
        sys.exit(f"ERROR: no jars found under {root}/lib -- wrong root?")

    if args.write:
        lines = [f"{digest}  {path}" for path, digest in tree.items()]
        (root / MANIFEST).write_text("\n".join(lines) + "\n")
        print(f"wrote {MANIFEST}: {len(tree)} jars")
        return 0

    manifest = read_manifest(root)
    findings = []
    for path, digest in manifest.items():
        if path not in tree:
            findings.append(f"MISSING   {path} (listed in manifest, not on disk)")
        elif tree[path] != digest:
            findings.append(f"MISMATCH  {path} (bytes differ from manifest)")
    for path in tree:
        if path not in manifest:
            findings.append(f"UNLISTED  {path} (on disk, not in manifest)")

    if findings:
        print(f"{len(findings)} provenance finding(s) against {MANIFEST}:")
        for f in sorted(findings):
            print(f"  {f}")
        print("If the change is deliberate (re-vendoring), regenerate with:")
        print(f"  python3 tools/check-vendored-provenance.py {args.root} --write")
        return 1

    print(f"OK: {len(tree)} vendored jars match {MANIFEST}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
