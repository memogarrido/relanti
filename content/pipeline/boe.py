"""Client for the BOE consolidated legislation open-data API.

Docs: https://www.boe.es/datosabiertos/documentos/APIconsolidada.pdf

Endpoints used (base https://www.boe.es/datosabiertos/api/legislacion-consolidada):
  /id/{law}/metadatos          XML or JSON, has fecha_actualizacion (AAAAMMDDTHHmmSSZ)
  /id/{law}/texto/indice       XML or JSON, one entry per block: id, titulo, fecha_actualizacion, url
  /id/{law}/texto/bloque/{id}  XML only, <bloque> with one <version> per amendment

Snapshots are written to content/boe/<law>/ as:
  index.json            metadata + every block's id, titulo, fecha_actualizacion
  blocks/<block>.json   latest version's text, its dates, the amending norm and a fingerprint

The fingerprint is sha256 over the normalised text of the latest version
(NFC, whitespace collapsed, footnote blockquotes removed), so formatting-only
changes in the API do not trigger reviews, but any wording change does.
"""
from __future__ import annotations

import hashlib
import json
import re
import time
import unicodedata
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

API = "https://www.boe.es/datosabiertos/api/legislacion-consolidada"
UA = "relanti-content-pipeline/1.0 (+https://github.com/memogarrido/relanti)"


def _get(url: str, accept: str, retries: int = 4) -> bytes:
    req = urllib.request.Request(url, headers={"Accept": accept, "User-Agent": UA})
    delay = 2.0
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt == retries:
                raise RuntimeError(f"GET {url} failed: {e}") from e
            time.sleep(delay)
            delay *= 2
    raise AssertionError


def normalise(text: str) -> str:
    text = unicodedata.normalize("NFC", text)
    text = text.replace(" ", " ")
    return re.sub(r"\s+", " ", text).strip()


def fingerprint(text: str) -> str:
    return "sha256:" + hashlib.sha256(normalise(text).encode("utf-8")).hexdigest()


def _text_of(el: ET.Element) -> list[str]:
    """Paragraph-ish lines of an element, skipping footnote blockquotes and images."""
    out: list[str] = []
    for child in el:
        tag = child.tag.lower()
        if tag in ("blockquote", "img"):
            continue
        if tag == "table":
            for tr in child.iter("tr"):
                cells = [normalise("".join(td.itertext())) for td in tr if td.tag in ("td", "th")]
                out.append(" | ".join(cells))
            continue
        t = normalise("".join(child.itertext()))
        if t:
            out.append(t)
    return out


def law_metadata(law: str) -> dict:
    raw = json.loads(_get(f"{API}/id/{law}/metadatos", "application/json"))
    data = raw.get("data", raw)
    if isinstance(data, list):
        data = data[0]
    return data


def law_index(law: str) -> list[dict]:
    raw = json.loads(_get(f"{API}/id/{law}/texto/indice", "application/json"))
    data = raw.get("data", raw)
    blocks = data.get("bloque", data) if isinstance(data, dict) else data
    if isinstance(blocks, dict):
        blocks = [blocks]
    return [
        {"id": b["id"], "titulo": b.get("titulo", ""), "fecha_actualizacion": str(b.get("fecha_actualizacion", ""))}
        for b in blocks
    ]


def block(law: str, block_id: str) -> dict:
    xml = _get(f"{API}/id/{law}/texto/bloque/{block_id}", "application/xml")
    root = ET.fromstring(xml)
    b = root.find(".//bloque")
    if b is None:
        raise RuntimeError(f"{law}#{block_id}: no <bloque> in response")
    versions = b.findall("version")
    if not versions:
        raise RuntimeError(f"{law}#{block_id}: no <version>")
    today = time.strftime("%Y%m%d")
    when = lambda v: v.get("fecha_vigencia") or v.get("fecha_publicacion") or ""
    ordered = sorted(versions, key=when)
    in_force = [v for v in ordered if when(v) <= today] or ordered[:1]
    latest = in_force[-1]
    upcoming = [v for v in ordered if when(v) > today]
    text = "\n".join(_text_of(latest))
    out = {
        "law": law,
        "id": b.get("id"),
        "tipo": b.get("tipo"),
        "titulo": b.get("titulo"),
        "versions": len(versions),
        "latest": {
            "id_norma": latest.get("id_norma"),
            "fecha_publicacion": latest.get("fecha_publicacion"),
            "fecha_vigencia": latest.get("fecha_vigencia"),
        },
        "text": text,
        "fingerprint": fingerprint(text),
        "url": f"https://www.boe.es/buscar/act.php?id={law}#{b.get('id')}",
    }
    if upcoming:
        # Amendment already published but not yet in force: flag it for a "Novedad" truco.
        u = upcoming[-1]
        out["upcoming"] = {
            "id_norma": u.get("id_norma"),
            "fecha_publicacion": u.get("fecha_publicacion"),
            "fecha_vigencia": u.get("fecha_vigencia"),
            "text": "\n".join(_text_of(u)),
        }
    return out


# ---------------------------------------------------------------- snapshots

def snapshot_dir(root: Path, law: str) -> Path:
    return root / "boe" / law


def load_index(root: Path, law: str) -> dict | None:
    p = snapshot_dir(root, law) / "index.json"
    return json.loads(p.read_text("utf-8")) if p.exists() else None


def load_block(root: Path, law: str, block_id: str) -> dict | None:
    p = snapshot_dir(root, law) / "blocks" / f"{block_id}.json"
    return json.loads(p.read_text("utf-8")) if p.exists() else None


def write_json(p: Path, data) -> None:
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(data, ensure_ascii=False, indent=1, sort_keys=False) + "\n", "utf-8")


def sync_law(root: Path, law: str, only_changed: bool = True, pause: float = 0.25) -> list[str]:
    """Refresh the snapshot of one law. Returns ids of blocks whose fingerprint changed or are new."""
    meta = law_metadata(law)
    idx = law_index(law)
    old = load_index(root, law) or {"blocks": []}
    old_dates = {b["id"]: b["fecha_actualizacion"] for b in old["blocks"]}
    changed: list[str] = []
    for b in idx:
        prev = load_block(root, law, b["id"])
        if only_changed and prev and old_dates.get(b["id"]) == b["fecha_actualizacion"]:
            continue
        fresh = block(law, b["id"])
        if not prev or prev["fingerprint"] != fresh["fingerprint"]:
            changed.append(b["id"])
        write_json(snapshot_dir(root, law) / "blocks" / f"{b['id']}.json", fresh)
        time.sleep(pause)
    write_json(
        snapshot_dir(root, law) / "index.json",
        {
            "law": law,
            "titulo": meta.get("titulo"),
            "fecha_actualizacion": meta.get("fecha_actualizacion"),
            "blocks": idx,
        },
    )
    return changed
