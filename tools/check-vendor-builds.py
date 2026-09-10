#!/usr/bin/env python3
"""Fail the build when the WAR ships a vendor build nothing loads, or drops one something needs.

Background
----------
``build.xml`` copies whole vendor directories, so every alternative build a package ships went
into the WAR: ``mermaid.js`` beside the ``mermaid.min.js`` the pages load, ``chart.umd.js``
beside ``chart.umd.min.js``, TinyMCE's 57 unminified twins, Swiper's React and Vue wrappers and
its whole ESM module tree. None of it is reachable, and none of it is noticeable, because a file
nobody requests costs nothing at request time. It only makes the artifact permanently bigger
(issue 1862).

Trimming that by hand is where the risk is, and the risk is asymmetric: dropping an unused build
costs nothing, while dropping one that turns out to be served breaks a page with a 404 that no
test exercises. So the trimming is expressed as excludes in ``build.xml`` and this gate checks
both directions against the assembled artifact.

  MISSING       something packaged references an asset the WAR does not contain. That is the
                dangerous direction: a page will 404 on it. Nothing else in the build checks
                this -- check-war-completeness.py answers the same question for Java classes
                and says nothing about static assets.

  UNSWEPT       an unminified file is packaged whose minified twin is ALSO packaged, and which
                nothing references. That is the debt this issue is about, and reporting it keeps
                build.xml's exclude list from going stale as vendors are upgraded: a new version
                that adds another such pair fails here rather than quietly adding weight.

The "has a packaged .min twin" rule is deliberately structural rather than a reachability
judgement. Reachability is what makes this dangerous to automate: ace loads ``mode-*.js`` and
TinyMCE loads ``plugin.min.js`` by path at runtime, so neither appears in any source file, and a
naive "not referenced, therefore delete" sweep would strip both. Requiring a minified twin means
the runtime loader always still finds something to load.

The served set has one known blind spot, which is why MISSING allows for it. main.jsp builds the
FontAwesome path with ``${font:fontawesome()}``, so the literal directory name never appears in
any source file; the scanner records the tail it can see. EL_BUILT_PREFIXES below records those
directories so their contents are never reported as missing or swept on that basis alone.

Usage:  check-vendor-builds.py --war target/simis-cms.war [--root .]
"""

import argparse
import importlib.util
import sys
import zipfile
from pathlib import Path

# Directories whose path is assembled in EL at render time, so the literal never appears in the
# source the scanner reads. FontCommand.fontawesome() returns one of these two names.
EL_BUILT_PREFIXES = (
    "css/fontawesome-free-6.1.1-web/",
    "css/fontawesome-pro-6.1.1-web/",
)

# Paths under /css or /javascript that a servlet answers rather than the WAR. StylesheetServlet
# is mapped to /css/custom/*, so the site's own stylesheet is generated from the database on
# request; there is no such file to package and its absence is not a defect.
SERVLET_SERVED_PREFIXES = ("css/custom/",)

MINIFIABLE = (".js", ".mjs", ".css")


def minified_twin(path: str) -> str | None:
    """`foo.js` -> `foo.min.js`; None for a file that is already minified or not code."""
    for ext in MINIFIABLE:
        if path.endswith(ext) and not path.endswith(".min" + ext):
            return path[: -len(ext)] + ".min" + ext
    return None


def load_served(root: Path) -> set[str]:
    """Reuse check-source-maps.py's served-set derivation rather than duplicating it."""
    spec = importlib.util.spec_from_file_location(
        "check_source_maps", Path(__file__).with_name("check-source-maps.py")
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.served_assets(root)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--war", required=True)
    parser.add_argument("--root", default=".")
    args = parser.parse_args()

    war = Path(args.war)
    if not war.is_file():
        print(f"WAR not found: {war}", file=sys.stderr)
        return 2

    root = Path(args.root)
    served = load_served(root)
    with zipfile.ZipFile(war) as archive:
        packaged = {i.filename for i in archive.infolist() if not i.is_dir()}
        sizes = {i.filename: i.file_size for i in archive.infolist() if not i.is_dir()}

    missing = sorted(
        rel
        for rel in served
        if rel.startswith(("css/", "javascript/"))
        and rel not in packaged
        and not rel.startswith(EL_BUILT_PREFIXES)
        and not rel.startswith(SERVLET_SERVED_PREFIXES)
        # The scanner records the visible tail of an EL-built path, which is not a real file.
        and "/" in rel.rstrip("/")[len("css/") :]
    )

    unswept = sorted(
        rel
        for rel in packaged
        if rel.startswith(("css/", "javascript/"))
        and (twin := minified_twin(rel))
        and twin in packaged
        and rel not in served
    )

    for rel in missing:
        print(f"MISSING     {rel} is referenced but not packaged")
    for rel in unswept:
        print(f"UNSWEPT     {rel} ({sizes[rel] // 1024} KB) has a packaged {minified_twin(rel)}")

    if missing or unswept:
        print(
            f"\n{len(missing)} missing, {len(unswept)} unswept "
            f"({sum(sizes[r] for r in unswept) / 1048576:.2f} MB). "
            "Add an <exclude> to BOTH filesets in build.xml, or, if the file really is served, "
            "reference it from a JSP so this gate can see it.",
            file=sys.stderr,
        )
        return 1

    print(f"OK  {len(packaged)} packaged entries; no unreachable vendor build, no missing asset.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
