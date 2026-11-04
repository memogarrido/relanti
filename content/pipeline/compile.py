"""Validate content/src and compile it into the question pack the apps load.

Output: content/packs/relanti-b.json, in the format agreed with the app
(format "relanti-pack", schemaVersion 1). See content/README.md.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

from . import boe
from .model import load_dir, load_sources, read_yaml

SCHEMA_VERSION = 1
PACK = "packs/relanti-b.json"
ETAPA_CODES = ["SE", "PR", "VE", "AD", "PA", "AL", "VH", "FH", "PS", "PP", "EC", "EX"]
ITEM_ID = re.compile(r"^(?P<tramo>[A-Z]{2}-1\d\d)-\d\d$")
TRAMO_ID = re.compile(r"^(?P<etapa>[A-Z]{2})-1\d\d$")
MIN_ITEMS_PER_TRAMO = 8
MAX_EXPLANATION = 220
MAX_TRUCO = 200

ATTRIBUTION = {
    "boe": "Basado en datos de la Agencia Estatal Boletín Oficial del Estado",
    "boeUrl": "https://www.boe.es",
    "consolidatedNotice": (
        "Las preguntas se basan en textos consolidados del BOE, de carácter meramente informativo. "
        "Solo la publicación oficial en el BOE tiene validez jurídica. Relanti no es una publicación "
        "oficial ni está vinculada a la DGT."
    ),
}


@dataclass
class Ctx:
    root: Path
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def err(self, where: str, msg: str) -> None:
        self.errors.append(f"{where}: {msg}")

    def warn(self, where: str, msg: str) -> None:
        self.warnings.append(f"{where}: {msg}")


def _short_hash(*parts: str, n: int = 10) -> str:
    return hashlib.sha256("␟".join(parts).encode("utf-8")).hexdigest()[:n]


def fmt_value(fact: dict) -> str:
    v = fact["value"]
    if isinstance(v, float):
        s = f"{v:g}".replace(".", ",")
    else:
        s = str(v)
    unit = fact.get("unit")
    return f"{s} {unit}" if unit else s


def reviewed_path(root: Path) -> Path:
    return root / "boe" / "reviewed.json"


def load_reviewed(root: Path) -> dict:
    p = reviewed_path(root)
    return json.loads(p.read_text("utf-8")) if p.exists() else {}


# ----------------------------------------------------------------- sources

SPELLED = ["cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once",
           "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve", "veinte"]


def value_tokens(v) -> set[str]:
    """Ways a number is written in BOE text: 120, 0,5, 3.500, doce."""
    if isinstance(v, float) and not v.is_integer():
        return {f"{v:g}".replace(".", ",")}
    n = int(v)
    out = {str(n), f"{n:,}".replace(",", ".")}
    if 0 <= n < len(SPELLED):
        out.add(SPELLED[n])
    return out


def quote_has_value(quotes: list[str], v) -> bool:
    toks = value_tokens(v)
    for q in quotes:
        words = set(re.findall(r"[\wáéíóúñ]+(?:[.,]\d+)*", q.lower()))
        if toks & words:
            return True
    return False


def resolve_source(ctx: Ctx, where: str, src: dict | None, laws: dict, reviewed: dict, cited: dict) -> dict | None:
    if not src:
        ctx.err(where, "missing source")
        return None
    law_key, block_id = src.get("law"), src.get("block")
    if law_key not in laws:
        ctx.err(where, f"unknown law {law_key!r}")
        return None
    boe_id = laws[law_key]["boe"]
    snap = boe.load_block(ctx.root, boe_id, str(block_id))
    if snap is None:
        ctx.err(where, f"{law_key}#{block_id} not in content/boe snapshot (run: python -m pipeline sync)")
        return None
    key = f"{law_key}#{block_id}"
    cited.setdefault(key, []).append(where)
    rev = reviewed.get(key)
    if rev is None:
        ctx.err(where, f"{key} has never been reviewed (python -m pipeline accept {key})")
    elif rev["fingerprint"] != snap["fingerprint"]:
        ctx.err(where, f"{key} changed in the BOE since it was reviewed on {rev['reviewedOn']}; update the content, then accept")
    for q in src.get("quote", []) or []:
        if boe.normalise(q) not in boe.normalise(snap["text"]):
            ctx.err(where, f"quote not found in {key}: {q[:60]!r}")
    return {
        "law": law_key,
        "boe": boe_id,
        "block": str(block_id),
        "ref": src.get("ref") or snap.get("titulo"),
        "url": snap["url"],
        "fingerprint": snap["fingerprint"],
        "checkedOn": rev["reviewedOn"] if rev else None,
        "note": src.get("note"),
    }


# ----------------------------------------------------------------- options

def place_answer(item_id: str, correct: str, wrong: list[str], keep_order: bool) -> tuple[list[str], int]:
    """Deterministic answer position, so the right option isn't always A and never moves between builds."""
    if keep_order:
        return [correct, *wrong], 0
    pos = int(hashlib.sha256(item_id.encode()).hexdigest(), 16) % (len(wrong) + 1)
    opts = list(wrong)
    opts.insert(pos, correct)
    return opts, pos


FACT_REF = re.compile(r"\{fact:([a-z0-9_.\-]+)\}")


def subst(ctx: Ctx, where: str, text: str, facts: dict, used: set) -> str:
    def rep(m):
        fid = m.group(1)
        if fid not in facts:
            ctx.err(where, f"unknown fact {fid}")
            return m.group(0)
        used.add(fid)
        return fmt_value(facts[fid])

    return FACT_REF.sub(rep, text or "")


# ----------------------------------------------------------------- build

def build(root: Path, write: bool) -> int:
    ctx = Ctx(root)
    sources = load_sources(root)
    laws = sources["laws"]
    reviewed = load_reviewed(root)
    cited: dict[str, list[str]] = {}

    # Etapas and tramos
    etapas_src = read_yaml(root / "src" / "etapas.yaml")["etapas"]
    etapas, tramos = [], {}
    for i, e in enumerate(etapas_src, 1):
        if e["code"] not in ETAPA_CODES:
            ctx.err("etapas.yaml", f"unknown etapa {e['code']}")
        etapas.append({k: e.get(k) for k in ("code", "name", "topic", "landscape", "free")} | {"order": i})
        for j, t in enumerate(e.get("tramos", []), 1):
            m = TRAMO_ID.match(t["code"])
            if not m or m["etapa"] != e["code"]:
                ctx.err("etapas.yaml", f"bad tramo code {t['code']} in {e['code']}")
            tramos[t["code"]] = {"code": t["code"], "etapa": e["code"], "order": j, "title": t["title"], "truco": t.get("truco")}
    if [e["code"] for e in etapas] != ETAPA_CODES:
        ctx.err("etapas.yaml", f"etapas must be exactly {ETAPA_CODES} in order")

    # Facts
    facts: dict[str, dict] = {}
    for p, doc in load_dir(root, "facts"):
        for f in doc.get("facts", []):
            w = f"facts/{p.name}:{f.get('id')}"
            if f["id"] in facts:
                ctx.err(w, "duplicate fact id")
            src = resolve_source(ctx, w, f.get("source"), laws, reviewed, cited)
            quotes = (f.get("source") or {}).get("quote") or []
            if not quotes:
                ctx.err(w, "a fact needs a quote: from the BOE text that contains its value")
            elif not quote_has_value(quotes, f["value"]):
                ctx.err(w, f"value {f['value']} does not appear in its quote")
            facts[f["id"]] = {
                "id": f["id"],
                "value": f["value"],
                "unit": f.get("unit"),
                "label": f["label"],
                "group": f.get("group"),
                "source": src,
            }

    # Trucos
    trucos: dict[str, dict] = {}
    for p, doc in load_dir(root, "trucos"):
        for t in doc.get("trucos", []):
            w = f"trucos/{p.name}:{t.get('id')}"
            if t["id"] in trucos:
                ctx.err(w, "duplicate truco id")
            if t.get("kind") not in ("reading", "memory", "novedad"):
                ctx.err(w, "kind must be reading, memory or novedad")
            used: set = set()
            body = subst(ctx, w, t["body"], facts, used)
            if len(body) > MAX_TRUCO:
                ctx.err(w, f"body is {len(body)} chars, max {MAX_TRUCO} (two lines)")
            trucos[t["id"]] = {
                "id": t["id"],
                "kind": t["kind"],
                "title": t["title"],
                "body": body,
                "facts": sorted(used) or None,
                "source": resolve_source(ctx, w, t.get("source"), laws, reviewed, cited),
            }
    for t in tramos.values():
        if t["truco"] and t["truco"] not in trucos:
            ctx.err("etapas.yaml", f"{t['code']} truco {t['truco']} not defined")

    # Items: hand-written plus template rows
    raw_items: list[tuple[str, dict]] = []
    for p, doc in load_dir(root, "items"):
        for it in doc.get("items", []):
            raw_items.append((f"items/{p.name}:{it.get('id')}", it))
    for p, doc in load_dir(root, "templates"):
        for tpl in doc.get("templates", []):
            for row in tpl["rows"]:
                raw_items.append((f"templates/{p.name}:{tpl['id']}:{row.get('id')}", expand_template(ctx, tpl, row, facts)))

    items: list[dict] = []
    seen: set[str] = set()
    for w, it in raw_items:
        iid = it.get("id", "")
        m = ITEM_ID.match(iid)
        if not m:
            ctx.err(w, "id must look like SE-101-01")
            continue
        if iid in seen:
            ctx.err(w, "duplicate item id")
        seen.add(iid)
        tramo = it.get("tramo") or m["tramo"]
        if tramo != m["tramo"]:
            ctx.err(w, f"id prefix does not match tramo {tramo}")
        if tramo not in tramos:
            ctx.err(w, f"unknown tramo {tramo}")
        used = set(it.get("facts", []) or [])
        q = subst(ctx, w, it["question"], facts, used)
        correct = subst(ctx, w, str(it["correct"]), facts, used)
        wrong = [subst(ctx, w, str(x), facts, used) for x in it["wrong"]]
        expl = subst(ctx, w, it["explanation"], facts, used)
        if len(wrong) != 2:
            ctx.err(w, "exam format is 3 options: one correct, two wrong")
        if len({correct, *wrong}) != len(wrong) + 1:
            ctx.err(w, "options must be distinct")
        if len(expl) > MAX_EXPLANATION:
            ctx.err(w, f"explanation is {len(expl)} chars, max {MAX_EXPLANATION}")
        if it.get("truco") and it["truco"] not in trucos:
            ctx.err(w, f"unknown truco {it['truco']}")
        options, answer = place_answer(iid, correct, wrong, bool(it.get("keep_order")))
        src = resolve_source(ctx, w, it.get("source"), laws, reviewed, cited)
        item = {
            "id": iid,
            "tramo": tramo,
            "question": q,
            "options": options,
            "answer": answer,
            "explanation": expl,
            "image": it.get("image"),
            "novedad": bool(it.get("novedad", False)),
            "truco": it.get("truco"),
            "template": it.get("template"),
            "facts": sorted(used) or None,
            "source": src,
        }
        item["rev"] = _short_hash(q, *options, str(answer))
        if item["image"] and not (root / "media" / item["image"]).exists():
            ctx.err(w, f"image {item['image']} not in content/media/")
        items.append(item)

    # Only tramos that have content go in the pack; each needs a full round.
    counts: dict[str, int] = {}
    for it in items:
        counts[it["tramo"]] = counts.get(it["tramo"], 0) + 1
    pack_tramos = []
    for t in tramos.values():
        n = counts.get(t["code"], 0)
        if n == 0:
            continue
        if n < MIN_ITEMS_PER_TRAMO:
            ctx.err(t["code"], f"has {n} items, needs at least {MIN_ITEMS_PER_TRAMO} for one round")
        pack_tramos.append(t)
    items.sort(key=lambda i: (list(tramos).index(i["tramo"]) if i["tramo"] in tramos else 999, i["id"]))

    # Stable ids: every id ever shipped stays in ids.lock and is never reused.
    check_id_lock(ctx, root, [i["id"] for i in items], write)

    for w in ctx.warnings:
        print("warning:", w, file=sys.stderr)
    if ctx.errors:
        for e in ctx.errors:
            print("error:", e, file=sys.stderr)
        print(f"{len(ctx.errors)} errors", file=sys.stderr)
        return 1

    used_tr = {t["truco"] for t in pack_tramos} | {i["truco"] for i in items if i["truco"]}
    body = {
        "attribution": ATTRIBUTION,
        "sources": {k: {"boe": v["boe"], "title": v["title"], "url": f"https://www.boe.es/buscar/act.php?id={v['boe']}"} for k, v in laws.items()},
        "etapas": etapas,
        "tramos": pack_tramos,
        "trucos": [t for k, t in trucos.items() if k in used_tr],
        "facts": list(facts.values()),
        "items": items,
    }
    content_hash = _short_hash(json.dumps(body, ensure_ascii=False, sort_keys=True), n=16)
    pack_path = root / PACK
    prev = json.loads(pack_path.read_text("utf-8")) if pack_path.exists() else {}
    version = prev.get("contentVersion", 0)
    if prev.get("contentHash") != content_hash:
        version += 1
    pack = {
        "format": "relanti-pack",
        "schemaVersion": SCHEMA_VERSION,
        "contentVersion": version,
        "contentHash": content_hash,
        "licence": "B",
        "placeholder": False,
        **body,
    }
    text = json.dumps(pack, ensure_ascii=False, indent=1) + "\n"
    summary = f"{len(etapas)} etapas, {len(pack_tramos)} tramos, {len(items)} items, {len(facts)} facts, {len(body['trucos'])} trucos, {len(cited)} BOE blocks cited"
    if write:
        pack_path.parent.mkdir(parents=True, exist_ok=True)
        pack_path.write_text(text, "utf-8")
        print(f"wrote {PACK} v{version}: {summary}")
        return 0
    if prev.get("contentHash") != content_hash:
        print(f"error: {PACK} is stale; run python -m pipeline build and commit it", file=sys.stderr)
        return 1
    print(f"ok: {summary}")
    return 0


def expand_template(ctx: Ctx, tpl: dict, row: dict, facts: dict) -> dict:
    """One template row becomes one item. Numbers come from the fact table, so a changed
    fact regenerates the text while the item keeps its id."""
    w = f"template {tpl['id']}:{row.get('id')}"
    fid = row["fact"]
    if fid not in facts:
        ctx.err(w, f"unknown fact {fid}")
        return {"id": row.get("id", ""), "question": "", "correct": "?", "wrong": ["", ""], "explanation": ""}
    fact = facts[fid]
    params = {k: v for k, v in row.items() if k not in ("id", "fact", "wrong", "source")}
    fill = lambda s: s.format(**params, value=fmt_value(fact)) if s else s
    correct = fmt_value(fact)
    wrong = row.get("wrong") or tpl.get("wrong")
    if wrong is None:
        # Default distractors: the closest other values in the same unit, from this template's rows.
        pool = sorted({facts[r["fact"]]["value"] for r in tpl["rows"] if r["fact"] in facts and facts[r["fact"]]["value"] != fact["value"]})
        pool.sort(key=lambda v: (abs(v - fact["value"]), v))
        wrong_vals = pool[:2]
        if len(wrong_vals) < 2:
            ctx.err(w, "not enough distinct values for distractors; give wrong: explicitly")
        wrong = [fmt_value({"value": v, "unit": fact.get("unit")}) for v in wrong_vals]
    else:
        wrong = [fmt_value({"value": v, "unit": fact.get("unit")}) if isinstance(v, (int, float)) else fill(v) for v in wrong]
    src = row.get("source") or tpl.get("source") or (
        {k: fact["source"][k] for k in ("law", "block", "ref")} if fact.get("source") else None
    )
    return {
        "id": row.get("id", ""),
        "tramo": tpl.get("tramo"),
        "question": fill(tpl["question"]),
        "correct": correct,
        "wrong": wrong,
        "explanation": fill(row.get("explanation") or tpl["explanation"]),
        "truco": row.get("truco") or tpl.get("truco"),
        "template": tpl["id"],
        "facts": [fid],
        "source": src,
    }


def check_id_lock(ctx: Ctx, root: Path, ids: list[str], write: bool) -> None:
    p = root / "src" / "ids.lock"
    lock: dict[str, str] = {}
    if p.exists():
        for line in p.read_text("utf-8").splitlines():
            if line.strip() and not line.startswith("#"):
                iid, status = line.split()[:2]
                lock[iid] = status
    current = set(ids)
    for iid in current:
        if lock.get(iid) == "retired":
            ctx.err(iid, "this id was retired and can't be reused; pick a new number")
    new = sorted(current - set(lock))
    gone = sorted(i for i, s in lock.items() if s == "active" and i not in current)
    if not new and not gone:
        return
    if not write:
        ctx.err("ids.lock", f"out of date ({len(new)} new, {len(gone)} removed); run python -m pipeline build")
        return
    for i in new:
        lock[i] = "active"
    for i in gone:
        print(f"warning: {i} removed from content, marked retired in ids.lock", file=sys.stderr)
        lock[i] = "retired"
    lines = ["# Every item id ever built. Ids are never renumbered or reused; removed items become retired."]
    lines += [f"{i} {s}" for i, s in sorted(lock.items())]
    p.write_text("\n".join(lines) + "\n", "utf-8")
