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
