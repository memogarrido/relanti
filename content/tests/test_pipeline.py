"""Pipeline tests. Run from content/: python -m unittest discover -s tests -v"""
from __future__ import annotations

import io
import json
import shutil
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

CONTENT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(CONTENT))

from pipeline import boe  # noqa: E402
from pipeline.compile import build, place_answer  # noqa: E402
from pipeline.watch import accept, watch  # noqa: E402


class Sandbox(unittest.TestCase):
    """Each test works on a throwaway copy of content/."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.root = self.tmp / "content"
        shutil.copytree(CONTENT, self.root, ignore=shutil.ignore_patterns("__pycache__", "tests"))

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def run_build(self, write=True):
        err = io.StringIO()
        with redirect_stdout(io.StringIO()), redirect_stderr(err):
            code = build(self.root, write=write)
        return code, err.getvalue()

    def pack(self):
        return json.loads((self.root / "packs" / "relanti-b.json").read_text("utf-8"))

    def edit(self, rel, old, new, count=1):
        p = self.root / rel
        text = p.read_text("utf-8")
        self.assertIn(old, text)
        p.write_text(text.replace(old, new, count), "utf-8")

    def block_path(self, boe_id, block):
        return self.root / "boe" / boe_id / "blocks" / f"{block}.json"


class TestPack(Sandbox):
    def test_committed_pack_is_current(self):
        code, err = self.run_build(write=False)
        self.assertEqual(code, 0, err)

    def test_pack_shape_matches_app_contract(self):
        p = self.pack()
        self.assertEqual(p["format"], "relanti-pack")
        self.assertEqual(p["schemaVersion"], 1)
        self.assertFalse(p["placeholder"])
        self.assertEqual([e["code"] for e in p["etapas"]], ["SE", "PR", "VE", "AD", "PA", "AL", "VH", "FH", "PS", "PP", "EC", "EX"])
        tramos = {t["code"] for t in p["tramos"]}
        trucos = {t["id"] for t in p["trucos"]}
        for it in p["items"]:
            self.assertEqual(len(it["options"]), 3, it["id"])
            self.assertIn(it["answer"], (0, 1, 2))
            self.assertIn(it["tramo"], tramos)
            self.assertTrue(it["source"]["boe"].startswith("BOE-A-"))
            self.assertTrue(it["source"]["fingerprint"].startswith("sha256:"))
            if it["truco"]:
                self.assertIn(it["truco"], trucos)
        self.assertIn("Boletín Oficial del Estado", p["attribution"]["boe"])

    def test_answer_position_is_stable_and_spread(self):
        a = place_answer("VE-101-01", "x", ["y", "z"], False)
        self.assertEqual(a, place_answer("VE-101-01", "x", ["y", "z"], False))
        positions = {place_answer(f"VE-101-{n:02d}", "x", ["y", "z"], False)[1] for n in range(1, 30)}
        self.assertEqual(positions, {0, 1, 2})


class TestFactsAndIds(Sandbox):
    def test_fact_change_regenerates_text_and_keeps_id(self):
        self.edit("src/facts/velocidad.yaml", "    value: 120\n", "    value: 110\n")
        # the value no longer matches its quote from the law, so the build refuses it...
        code, err = self.run_build()
        self.assertEqual(code, 1)
        self.assertIn("does not appear in its quote", err)
        # ...unless the law changed too. Simulate the BOE amending the table.
        bp = self.block_path("BOE-A-2003-23514", "a48")
        snap = json.loads(bp.read_text("utf-8"))
        snap["text"] = snap["text"].replace("Autopista y autovía | 120 |", "Autopista y autovía | 110 |")
        snap["fingerprint"] = boe.fingerprint(snap["text"])
        bp.write_text(json.dumps(snap, ensure_ascii=False), "utf-8")
        self.edit("src/facts/velocidad.yaml", '"Autopista y autovía | 120 | 90 | 100"', '"Autopista y autovía | 110 | 90 | 100"', count=-1)
        code, err = self.run_build()
        self.assertEqual(code, 1)
        self.assertIn("changed in the BOE since it was reviewed", err)
        with redirect_stdout(io.StringIO()):
            accept(self.root, ["RGC#a48"], all_cited=False)
        code, err = self.run_build()
        self.assertEqual(code, 0, err)
        item = next(i for i in self.pack()["items"] if i["id"] == "VE-101-01")
        self.assertIn("110 km/h", item["options"])
        self.assertEqual(item["options"][item["answer"]], "110 km/h")

    def test_removed_id_is_retired_and_cannot_return(self):
        self.edit("src/items/VE.yaml", "  - id: VE-101-13\n", "  - id: VE-101-14\n")
        code, err = self.run_build()
        self.assertEqual(code, 0, err)
        lock = (self.root / "src" / "ids.lock").read_text("utf-8")
        self.assertIn("VE-101-13 retired", lock)
        self.assertIn("VE-101-14 active", lock)
        self.edit("src/items/VE.yaml", "  - id: VE-101-14\n", "  - id: VE-101-13\n")
        code, err = self.run_build()
        self.assertEqual(code, 1)
        self.assertIn("retired", err)

    def test_duplicate_options_rejected(self):
        self.edit("src/items/PR.yaml", 'wrong: ["El que venga por la derecha", "El turismo, si va más rápido"]', 'wrong: ["El tranvía", "El turismo, si va más rápido"]')
        code, err = self.run_build()
        self.assertEqual(code, 1)
        self.assertIn("options must be distinct", err)


class TestWatch(Sandbox):
    def test_offline_watch_reports_changed_article_and_affected_items(self):
        bp = self.block_path("BOE-A-2003-23514", "a20")
        snap = json.loads(bp.read_text("utf-8"))
        snap["text"] = snap["text"].replace("0,5 gramos por litro", "0,2 gramos por litro")
        snap["fingerprint"] = boe.fingerprint(snap["text"])
        bp.write_text(json.dumps(snap, ensure_ascii=False), "utf-8")
        report = self.tmp / "r.md"
        with redirect_stderr(io.StringIO()):
            watch(self.root, report=report, offline=True)
        text = report.read_text("utf-8")
        self.assertIn("RGC#a20 · text changed", text)
        self.assertIn("FH-101-02", text)
        self.assertIn("alcohol.general.sangre", text)
        self.assertIn("+", text)  # a diff is included


class TestBoeParsing(unittest.TestCase):
    def test_latest_in_force_version_and_upcoming(self):
        xml = (
            '<response><data><bloque id="a1" tipo="precepto" titulo="Artículo 1">'
            '<version id_norma="A" fecha_publicacion="20031223" fecha_vigencia="20040124"><p>Viejo</p></version>'
            '<version id_norma="B" fecha_publicacion="20210101" fecha_vigencia="20210511"><p class="articulo">Nuevo  texto</p>'
            '<blockquote><p class="nota_pie">nota</p></blockquote></version>'
            '<version id_norma="C" fecha_publicacion="20990101" fecha_vigencia="20991231"><p>Futuro</p></version>'
            "</bloque></data></response>"
        ).encode()
        orig = boe._get
        boe._get = lambda url, accept, retries=4: xml
        try:
            b = boe.block("X", "a1")
        finally:
            boe._get = orig
        self.assertEqual(b["text"], "Nuevo texto")
        self.assertEqual(b["latest"]["id_norma"], "B")
        self.assertEqual(b["upcoming"]["id_norma"], "C")
        self.assertEqual(b["fingerprint"], boe.fingerprint("Nuevo   texto"))


if __name__ == "__main__":
    unittest.main()
