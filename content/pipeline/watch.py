"""Weekly BOE change check.

For every BOE block cited by an item, fact or truco, compare the live
consolidated text with the fingerprint recorded when we last reviewed it
(content/boe/reviewed.json). Writes a Markdown report listing each changed
article with its old and new text and the content ids that cite it.

`accept` records the current snapshot of blocks as reviewed, after the
affected content has been checked or updated.
"""
from __future__ import annotations

import datetime as dt
import difflib
import json
import sys
from pathlib import Path

from . import boe
from .compile import load_reviewed, reviewed_path
from .model import load_dir, load_sources


def cited_blocks(root: Path) -> dict[str, list[str]]:
    """{"RGC#a48": ["fact vmax.turismo.autopista", "item VE-101-03", ...]}"""
    out: dict[str, list[str]] = {}

    def add(src, who):
        if src and src.get("law") and src.get("block"):
            out.setdefault(f"{src['law']}#{src['block']}", []).append(who)

    facts = {}
    for _, doc in load_dir(root, "facts"):
        for f in doc.get("facts", []):
            facts[f["id"]] = f
            add(f.get("source"), f"fact {f['id']}")
    for _, doc in load_dir(root, "trucos"):
        for t in doc.get("trucos", []):
            add(t.get("source"), f"truco {t['id']}")
    for _, doc in load_dir(root, "items"):
        for it in doc.get("items", []):
            add(it.get("source"), f"item {it['id']}")
    for _, doc in load_dir(root, "templates"):
        for tpl in doc.get("templates", []):
            for row in tpl["rows"]:
                src = row.get("source") or tpl.get("source") or (facts.get(row["fact"], {}).get("source"))
                add(src, f"item {row['id']} (template {tpl['id']}, fact {row['fact']})")
    return out


def _diff(old: str, new: str) -> str:
    a = old.replace(". ", ".\n").splitlines()
    b = new.replace(". ", ".\n").splitlines()
    return "\n".join(difflib.unified_diff(a, b, "reviewed", "boe-now", lineterm="", n=1))


def watch(root: Path, report: Path | None, offline: bool) -> int:
    laws = load_sources(root)["laws"]
    reviewed = load_reviewed(root)
    cited = cited_blocks(root)
    changes = []
    upcoming = []
    for key, who in sorted(cited.items()):
        law_key, block_id = key.split("#", 1)
        boe_id = laws[law_key]["boe"]
        snap = boe.load_block(root, boe_id, block_id)
        live = snap if offline else boe.block(boe_id, block_id)
        if not offline:
            boe.write_json(boe.snapshot_dir(root, boe_id) / "blocks" / f"{block_id}.json", live)
        rev = reviewed.get(key)
        if live is None:
            changes.append((key, who, None, None, "block missing from BOE"))
            continue
        if not rev or rev["fingerprint"] != live["fingerprint"]:
            changes.append((key, who, rev, live, "text changed" if rev else "never reviewed"))
        if live.get("upcoming"):
            upcoming.append((key, who, live))

    today = dt.date.today().isoformat()
    lines = [f"# BOE change check · {today}", ""]
    if not changes and not upcoming:
        lines.append(f"No changes in the {len(cited)} cited BOE articles.")
    if changes:
        lines += [
            f"{len(changes)} of {len(cited)} cited BOE articles changed since review. "
            "Update the affected content in a PR, then run `python -m pipeline accept <LAW#block>`.",
            "",
        ]
    for key, who, rev, live, why in changes:
        lines.append(f"## {key} · {why}")
        if live:
            lines.append(f"[{live['titulo']}]({live['url']}) · in force since {live['latest']['fecha_vigencia']} · amended by {live['latest']['id_norma']}")
        if rev:
            lines.append(f"Last reviewed {rev['reviewedOn']}.")
        lines.append("")
        lines.append("Affected: " + ", ".join(sorted(set(who))))
        if rev and live and rev.get("text"):
            lines += ["", "```diff", _diff(rev["text"], live["text"]), "```"]
        lines.append("")
    for key, who, live in upcoming:
        u = live["upcoming"]
        lines += [
            f"## {key} · amendment published, in force {u['fecha_vigencia']}",
            f"{u['id_norma']} (published {u['fecha_publicacion']}) changes this article. Consider a Novedad truco now and updated items on the date.",
            "Affected: " + ", ".join(sorted(set(who))),
            "",
            "```diff",
            _diff(live["text"], u["text"]),
            "```",
            "",
        ]
    lines.append("_Texto consolidado del BOE, de carácter meramente informativo. Basado en datos de la Agencia Estatal Boletín Oficial del Estado (https://www.boe.es)._")
    text = "\n".join(lines) + "\n"
    if report:
        report.write_text(text, "utf-8")
    else:
        sys.stdout.write(text)
    print(f"changed={len(changes)} upcoming={len(upcoming)}", file=sys.stderr)
    return 0


def accept(root: Path, blocks: list[str], all_cited: bool) -> int:
    laws = load_sources(root)["laws"]
    reviewed = load_reviewed(root)
    keys = sorted(cited_blocks(root)) if all_cited else blocks
    today = dt.date.today().isoformat()
    for key in keys:
        law_key, block_id = key.split("#", 1)
        snap = boe.load_block(root, laws[law_key]["boe"], block_id)
        if snap is None:
            print(f"error: {key} has no snapshot; run python -m pipeline sync", file=sys.stderr)
            return 1
        prev = reviewed.get(key)
        if prev and prev["fingerprint"] == snap["fingerprint"]:
            continue
        reviewed[key] = {
            "fingerprint": snap["fingerprint"],
            "fecha_vigencia": snap["latest"]["fecha_vigencia"],
            "id_norma": snap["latest"]["id_norma"],
            "reviewedOn": today,
            "text": snap["text"],
        }
        print(f"accepted {key}")
    boe.write_json(reviewed_path(root), dict(sorted(reviewed.items())))
    return 0
