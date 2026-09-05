#!/usr/bin/env python3
"""Fail the build when the WAR's source maps and the files that declare them disagree.

A ``.map`` earns its place in the WAR only if some file that is ITSELF packaged points at it
with a ``sourceMappingURL`` comment. That is a narrow condition, and 53 of the 58 maps in the
source tree did not meet it: they belong to vendor originals no page loads -- ``chart.umd.js``
when the pages load ``chart.umd.min.js``, ``leaflet-src.js``, the unbundled swiper modules --
so they were 4.93 MB of dead weight a browser could never ask for (issue 1862).

Dropping them means build.xml now carries an explicit list of the few that stay, and a hand-kept
list rots. This gate checks it in both directions, against the assembled artifact rather than
against the source tree:

  MISSING    a packaged file declares a map that is not in the WAR. Devtools 404s on it. This is
             the failure the old build.xml comment was guarding against when it kept
             foundation.min.css.map, and the reason that guard cannot simply be deleted.

  UNREFERENCED  a map is packaged that nothing packaged declares. Harmless to a browser, which
             is exactly why it goes unnoticed -- it just makes the artifact bigger forever.

Both are failures. Reporting one and not the other would let the list drift in the other
direction until someone re-measures by hand, which is how it got to 4.93 MB in the first place.

"Served" is the operative word and it is narrower than "packaged". The WAR also contains vendor
originals no page ever loads -- ``chart.umd.js`` beside the ``chart.umd.min.js`` the pages use,
``leaflet-src.js``, every unbundled swiper module -- and each of those declares a map too. Those
declarations are inert: nothing fetches the file, so nothing fetches its map. Counting them would
force ~4.9 MB back into the WAR to satisfy references no browser makes.

So the served set is derived the same way the build.xml list was: every /css/ or /javascript/
asset referenced from a JSP, tag, Java source or XML, plus whatever a served stylesheet then
pulls in via @import or url(). Declarations from anything outside that set are ignored.

Usage:  check-source-maps.py --war target/simis-cms.war [--root .]
"""

import argparse
import posixpath
import re
import sys
import zipfile
from pathlib import Path

# `/*# sourceMappingURL=foo.js.map */` or `//# sourceMappingURL=foo.js.map`
DECLARATION = re.compile(rb"sourceMappingURL=([^\s*'\")]+)")

# Only these can carry a declaration; scanning the whole WAR would read every jar.
SCANNED_SUFFIXES = (".css", ".js", ".mjs")


# Served vendor files that declare a map their own distribution never shipped. These 404ed in
# devtools long before anything here excluded a map -- the file is simply not in the repository
# and never has been -- so they are upstream packaging artifacts rather than anything this build
# does. Listed rather than silently skipped, with the same rule the other gates use: an entry
# that stops being necessary fails the run, so the list cannot outlive the problem. Clearing one
# means either vendoring the map or stripping the comment from the vendored file.
# target -> (the served file that declares it, why it is not there)
NEVER_VENDORED = {
    "css/quill.snow.css.map": (
        "css/quill-2.0.3-snow.css",
        "Quill ships this map only in its source package, not in the dist file vendored here"),
    "javascript/foundation-6.8.1/maps/what-input.min.js.map": (
        "javascript/foundation-6.8.1/what-input-5.2.6.min.js",
        "points at a maps/ directory Foundation's bundle does not include"),
    "javascript/fullcalendar-6.1.10/moment.min.js.map": (
        "javascript/fullcalendar-6.1.10/moment-2.27.0.min.js",
        "the file was renamed when vendored, so the sibling map name no longer matches either"),
    "javascript/quill-2.0.3/quill.js.map": (
        "javascript/quill-2.0.3/quill.js",
        "same omission as the stylesheet above"),
}


ASSET_REF = re.compile(r"/((?:css|javascript)/[A-Za-z0-9_./@-]+\.(?:css|js|mjs))")
CSS_PULL = re.compile(r"""(?:@import\s+(?:url\()?["']?|url\(["']?)([A-Za-z0-9_./@-]+\.(?:css|js|mjs))""")
REFERRING_SUFFIXES = (".jsp", ".tag", ".java", ".xml", ".html")


def served_assets(root: Path) -> set[str]:
    """Every /css or /javascript asset a page can actually cause the browser to fetch."""
    web = root / "src" / "main" / "webapp"
    served: set[str] = set()
    for base in (root / "src" / "main" / "webapp", root / "src" / "main" / "java"):
        if not base.is_dir():
            continue
        for path in base.rglob("*"):
            if path.is_file() and path.suffix in REFERRING_SUFFIXES:
                served.update(ASSET_REF.findall(path.read_text(errors="ignore")))

    # A served stylesheet can pull in another file, which is then served too.
    changed = True
    while changed:
        changed = False
        for rel in list(served):
            f = web / rel
            if not f.is_file() or f.suffix != ".css":
                continue
            for target in CSS_PULL.findall(f.read_text(errors="ignore")):
                try:
                    pulled = str((f.parent / target).resolve().relative_to(web.resolve()))
                except (ValueError, OSError):
                    continue
                if pulled not in served:
                    served.add(pulled)
                    changed = True
    return served


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--war", type=Path, required=True, help="the assembled .war")
    parser.add_argument("--root", type=Path, default=Path("."), help="repository root")
    args = parser.parse_args()

    if not args.war.exists():
        print(f"ERROR: no such WAR: {args.war}", file=sys.stderr)
        return 1

    served = served_assets(args.root)
    if not served:
        print("ERROR: found no asset references in the source tree; --root is probably wrong",
              file=sys.stderr)
        return 1

    packaged: dict[str, bytes | None] = {}
    with zipfile.ZipFile(args.war) as zf:
        for info in zf.infolist():
            if info.is_dir():
                continue
            packaged[info.filename] = (zf.read(info.filename)
                                       if info.filename.endswith(SCANNED_SUFFIXES) else None)

    declared: dict[str, list[str]] = {}
    for name, data in packaged.items():
        if data is None or name not in served:
            continue
        for match in DECLARATION.finditer(data):
            target = match.group(1).decode("utf-8", "replace")
            if target.startswith("data:"):
                continue
            resolved = posixpath.normpath(posixpath.join(posixpath.dirname(name), target))
            declared.setdefault(resolved, []).append(name)

    packaged_maps = {n for n in packaged if n.endswith(".map")}
    missing = {t: d for t, d in declared.items()
               if t not in packaged and t not in NEVER_VENDORED}

    # A waiver that is no longer needed is itself a failure, so the list cannot outlive the
    # problem. Evaluated only where it applies: a waiver whose declaring file is not in this WAR
    # at all is dormant, not stale, which keeps the check meaningful against a synthetic tree.
    stale = []
    for target, (declarer, _) in sorted(NEVER_VENDORED.items()):
        if target in packaged:
            stale.append((target, "it is now packaged"))
        elif declarer in packaged and declarer in served and target not in declared:
            stale.append((target, f"{declarer} no longer declares it"))
    for target, why in stale:
        print(f"STALE WAIVER  {target} is listed in NEVER_VENDORED but {why}; remove the entry")
    unreferenced = sorted(packaged_maps - set(declared))

    for target, declarers in sorted(missing.items()):
        for declarer in sorted(declarers):
            print(f"MISSING       {declarer} is served and declares {target}, "
                  f"which the WAR does not contain")
    for name in unreferenced:
        print(f"UNREFERENCED  {name} is packaged but no served file declares it")

    problems = sum(len(d) for d in missing.values()) + len(unreferenced) + len(stale)
    if problems:
        print()
        print(f"{problems} problem(s). A packaged source map has to be declared by a file the "
              f"pages actually load, and such a declaration has to resolve to a packaged file.")
        print("Adjust the source-map filesets in build.xml (both the package and webapp targets).")
        return 1

    print(f"OK  {len(packaged_maps)} source map(s) packaged, each declared by a served file, "
          f"and every served declaration resolves ({len(served)} assets served, "
          f"{len(NEVER_VENDORED)} upstream omissions waived)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
