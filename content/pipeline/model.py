"""Loading of the YAML sources in content/src."""
from __future__ import annotations

from pathlib import Path

import yaml


def read_yaml(p: Path):
    with p.open(encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def load_sources(root: Path) -> dict:
    return read_yaml(root / "src" / "sources.yaml")


def load_dir(root: Path, name: str) -> list[tuple[Path, object]]:
    d = root / "src" / name
    return [(p, read_yaml(p)) for p in sorted(d.glob("*.yaml"))]
