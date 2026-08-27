"""generate-db-vex.py: the guards that stop a regeneration from deleting the suppression set.

The rest of this suite drives each tool through its real CLI. This one cannot: the
generator's input is GitHub code-scanning alerts, and the workflow comment in
tools-tests.yml records that as the reason it was left uncovered.

What that reasoning missed is that the tool's dangerous behaviour is not in the part that
needs the API. Its input is remote and can legitimately be empty; an empty input produces a
structurally valid document asserting nothing; and the documented invocation used to
redirect stdout over the real file, so the shell truncated it before the script ran. On
2026-08-26, after every alert had been dismissed, a regeneration would have replaced 54
not_affected statements with none and exited 0 (issue #1463).

So these tests cover write_document() and existing_statement_count() directly. They are pure
functions over a path, they are where a destructive write is refused, and they need no
network at all.
"""

import importlib.util
import json
from pathlib import Path

import pytest

TOOL = Path(__file__).resolve().parent.parent / "generate-db-vex.py"

_spec = importlib.util.spec_from_file_location("generate_db_vex", TOOL)
gen = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(gen)


def doc(n):
    """A document with n statements, shaped like the real one."""
    return {
        "@context": "https://openvex.dev/ns/v0.2.0",
        "statements": [
            {"vulnerability": {"name": "CVE-2026-%04d" % i}, "status": "not_affected"}
            for i in range(n)
        ],
    }


def seed(tmp_path, n):
    p = tmp_path / "vex.json"
    p.write_text(json.dumps(doc(n)))
    return p


def test_writes_when_there_is_no_existing_document(tmp_path):
    p = tmp_path / "nested" / "vex.json"
    assert gen.write_document(doc(3), str(p)) == 3
    assert len(json.loads(p.read_text())["statements"]) == 3


def test_refuses_an_empty_document_and_leaves_the_old_one_intact(tmp_path):
    """The 2026-08-26 case: alert source went empty, so the generator produced nothing."""
    p = seed(tmp_path, 54)
    with pytest.raises(SystemExit) as e:
        gen.write_document(doc(0), str(p))
    assert "empty" in str(e.value).lower()
    assert len(json.loads(p.read_text())["statements"]) == 54, "the existing document must survive"


def test_refuses_an_empty_document_even_with_allow_shrink(tmp_path):
    """--allow-shrink permits a smaller set, never an empty one -- zero is always a bug."""
    p = seed(tmp_path, 10)
    with pytest.raises(SystemExit):
        gen.write_document(doc(0), str(p), allow_shrink=True)
    assert len(json.loads(p.read_text())["statements"]) == 10


def test_refuses_to_shrink_the_statement_set(tmp_path):
    p = seed(tmp_path, 54)
    with pytest.raises(SystemExit) as e:
        gen.write_document(doc(53), str(p))
    msg = str(e.value)
    assert "54" in msg and "53" in msg, "the refusal should name both counts"
    assert len(json.loads(p.read_text())["statements"]) == 54


def test_allow_shrink_permits_a_deliberate_reduction(tmp_path):
    p = seed(tmp_path, 54)
    assert gen.write_document(doc(53), str(p), allow_shrink=True) == 53
    assert len(json.loads(p.read_text())["statements"]) == 53


def test_growing_and_holding_steady_are_always_allowed(tmp_path):
    p = seed(tmp_path, 10)
    assert gen.write_document(doc(11), str(p)) == 11
    assert gen.write_document(doc(11), str(p)) == 11


def test_an_unreadable_existing_document_does_not_block_a_write(tmp_path):
    """A corrupt file gives no trustworthy baseline, so shrink-detection cannot apply --
    but the zero guard still does, which is the one that matters."""
    p = tmp_path / "vex.json"
    p.write_text("{ this is not json")
    assert gen.existing_statement_count(str(p)) is None
    assert gen.write_document(doc(2), str(p)) == 2


def test_existing_statement_count_on_a_missing_file(tmp_path):
    assert gen.existing_statement_count(str(tmp_path / "absent.json")) is None


def test_write_is_atomic_and_leaves_no_temp_files(tmp_path):
    p = seed(tmp_path, 5)
    gen.write_document(doc(6), str(p))
    leftovers = [f.name for f in tmp_path.iterdir() if f.name.endswith(".tmp")]
    assert leftovers == [], "a temp file survived the rename: %s" % leftovers


def test_output_is_valid_json_with_a_trailing_newline(tmp_path):
    p = tmp_path / "vex.json"
    gen.write_document(doc(2), str(p))
    raw = p.read_text()
    assert raw.endswith("\n")
    json.loads(raw)


# --- Identifier form ------------------------------------------------------------------
# Trivy matches VEX identifiers by PURL and skips a mismatch in silence. A statement with a
# qualifier the scanned image does not carry suppresses nothing and warns about nothing, so
# nothing but a test or a full scan can tell the two apart. See package_purl().

def generated_ids(monkeypatch, tmp_path, alerts):
    """Run the generator end-to-end over `alerts` and return (product ids, subcomponent ids)."""
    import sys
    monkeypatch.setattr(gen, "fetch_alerts", lambda: alerts)
    out = tmp_path / "vex.json"
    monkeypatch.setattr(sys, "argv", ["generate-db-vex.py", "--output", str(out)])
    gen.main()
    d = json.loads(out.read_text())
    products = {p["@id"] for s in d["statements"] for p in s["products"]}
    subs = [sc["@id"] for s in d["statements"] for p in s["products"] for sc in p["subcomponents"]]
    return products, subs


def alert(cve, pkg, version="1.2.3-4"):
    return {
        "rule": {"id": cve},
        "tool": {"name": "Trivy"},
        "most_recent_instance": {"message": {"text":
            "Package: %s\nInstalled Version: %s\nFixed Version: \n" % (pkg, version)}},
    }


def test_package_purl_is_bare():
    assert gen.package_purl("libssh2-1") == "pkg:deb/debian/libssh2-1"


def test_product_purl_carries_no_qualifier():
    """Stripped by hand in 4a2bde1e, the commit that first made the scan gate enforce."""
    assert gen.PRODUCT_PURL == "pkg:oci/simis-cms-db"
    assert "?" not in gen.PRODUCT_PURL


def test_generated_identifiers_carry_no_version_and_no_qualifier(monkeypatch, tmp_path):
    """The 52718205 case, at the source instead of one statement at a time.

    The generator emitted `pkg:deb/debian/<pkg>@<version>?distro=debian-12`. The image is
    Debian 12.15, so `distro=debian-12` never matched -- every generated statement was
    skipped, and a regenerated document suppressed nothing at all.
    """
    products, subs = generated_ids(monkeypatch, tmp_path, [
        alert("CVE-2026-58050", "libssh2-1", "1.10.0-3+b1"),
        alert("CVE-2026-49014", "gdal-data", "3.13.2+dfsg-1.pgdg12+1"),
    ])
    assert subs == ["pkg:deb/debian/gdal-data", "pkg:deb/debian/libssh2-1"]
    assert products == {"pkg:oci/simis-cms-db"}
    for i in subs:
        assert "?" not in i, "qualifier in %s -- Trivy will skip this statement" % i
        assert "@" not in i.rsplit("/", 1)[-1], "version pin in %s" % i


def test_a_package_with_several_affected_versions_yields_one_bare_identifier(monkeypatch, tmp_path):
    """Bare identifiers are version-free, so the same package cannot appear twice."""
    _, subs = generated_ids(monkeypatch, tmp_path, [
        alert("CVE-2026-53613", "util-linux", "1:2.38.1-5+deb12u3"),
        alert("CVE-2026-53613", "util-linux", "1:2.38.1-5+deb12u2"),
    ])
    assert subs == ["pkg:deb/debian/util-linux"]


def test_impact_statement_does_not_depend_on_iteration_order(monkeypatch, tmp_path):
    """Packages are grouped in a set now; unsorted iteration would reorder the reasons
    joined into impact_statement and make every regeneration produce a spurious diff."""
    import sys
    alerts = [alert("CVE-2026-49014", p) for p in ("libgdal32", "gdal-data", "libaom3")]
    seen = set()
    for n in range(3):
        monkeypatch.setattr(gen, "fetch_alerts", lambda: alerts)
        out = tmp_path / ("vex%d.json" % n)
        monkeypatch.setattr(sys, "argv", ["generate-db-vex.py", "--output", str(out)])
        gen.main()
        d = json.loads(out.read_text())
        seen.add(json.dumps([s.get("impact_statement") for s in d["statements"]]))
    assert len(seen) == 1


# --- Policy vs. the committed document --------------------------------------------------
# The tables and the document are two copies of the same triage decisions, and nothing kept
# them together. Statements were added and enriched by hand while CVE_POLICY/PACKAGE_POLICY
# stood still, so by 2026-08-26 a regeneration silently downgraded eight CVEs to
# under_investigation -- which suppresses nothing -- and dropped the hand-written evidence
# from six more, all while exiting 0. The test below is the thing that was missing: it
# regenerates from the document's own contents and demands the statements come back
# identical, so the next hand-edit that skips the tables fails here instead of in a scan.

COMMITTED_VEX = TOOL.parent.parent / "docker" / "db" / "vex" / "simis-cms-db.openvex.json"


def committed_statements():
    return json.loads(COMMITTED_VEX.read_text())["statements"]


def alerts_describing(statements):
    """The alert set that a scan of the image the document describes would produce."""
    return [
        alert(s["vulnerability"]["name"], sc["@id"].rsplit("/", 1)[-1])
        for s in statements
        for p in s["products"]
        for sc in p["subcomponents"]
    ]


def regenerate(monkeypatch, tmp_path, alerts):
    import sys
    monkeypatch.setattr(gen, "fetch_alerts", lambda: alerts)
    out = tmp_path / "vex.json"
    monkeypatch.setattr(sys, "argv", ["generate-db-vex.py", "--output", str(out)])
    gen.main()
    return json.loads(out.read_text())["statements"]


def test_policy_reproduces_every_committed_statement(monkeypatch, tmp_path):
    """Feed the generator the document's own CVE/package set; it must rebuild it exactly.

    Every difference this catches is a real defect in one direction or the other: either a
    decision recorded by hand is missing from the tables, so regenerating loses it, or the
    tables have moved on and the committed document is stale.
    """
    want = committed_statements()
    got = regenerate(monkeypatch, tmp_path, alerts_describing(want))

    by_cve = {s["vulnerability"]["name"]: s for s in got}
    assert set(by_cve) == {s["vulnerability"]["name"] for s in want}
    for expected in want:
        cve = expected["vulnerability"]["name"]
        assert by_cve[cve] == expected, (
            "regenerating changes %s.\nIf a statement was edited by hand, put the same text "
            "in CVE_POLICY/CVE_ADDENDUM; if the tables are right, regenerate the document."
            % cve
        )


def test_no_committed_statement_regenerates_as_under_investigation(monkeypatch, tmp_path):
    """The specific 2026-08-26 failure, stated as the property that was violated.

    `under_investigation` is an honest status and the correct default for an untriaged CVE
    -- but it suppresses nothing in Trivy, so a statement that is not_affected in the
    document and under_investigation on regeneration is a silent un-suppression, and the
    scan gate fails on a CVE that was in fact triaged.
    """
    want = committed_statements()
    got = regenerate(monkeypatch, tmp_path, alerts_describing(want))
    downgraded = sorted(
        s["vulnerability"]["name"] for s in got if s["status"] == "under_investigation"
    )
    assert not downgraded, (
        "%d triaged CVEs would regenerate as under_investigation: %s"
        % (len(downgraded), ", ".join(downgraded))
    )


def test_a_cve_addendum_still_requires_every_package_to_be_covered(monkeypatch, tmp_path):
    """An addendum adds evidence to a package rule; it must not stand in for one.

    This is why the four GDAL-chain enrichments live in CVE_ADDENDUM rather than CVE_POLICY:
    a CVE_POLICY entry answers for every package on the CVE, so a newly affected package
    outside GDAL_CHAIN would quietly inherit a GDAL rationale. Here the per-package check
    still runs and an uncovered package still forces the whole statement down.
    """
    cve = sorted(gen.CVE_ADDENDUM)[0]
    covered = sorted(gen.GDAL_CHAIN)[0]
    got = regenerate(monkeypatch, tmp_path, [
        alert(cve, covered), alert(cve, "some-unanalysed-package"),
    ])
    assert [s["status"] for s in got] == ["under_investigation"]
    assert gen.CVE_ADDENDUM[cve] not in got[0]["impact_statement"]
