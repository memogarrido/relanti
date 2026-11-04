"""Relanti content pipeline.

  python -m pipeline sync [--full] [--law RGC]   refresh BOE snapshots in content/boe/
  python -m pipeline build                        validate content/src and write content/packs/relanti-b.json
  python -m pipeline check                        build in memory and fail if the committed pack is stale
  python -m pipeline watch [--report out.md]      compare live BOE with reviewed fingerprints
  python -m pipeline accept <LAW#block>...|--all  record the current snapshot of blocks as reviewed

Run from the content/ directory (cd content && python -m pipeline ...).
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent  # content/


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog="pipeline")
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("sync")
    s.add_argument("--full", action="store_true", help="refetch every block, not only changed ones")
    s.add_argument("--law", action="append", help="short key (RGC, LSV...); default all")
    sub.add_parser("build")
    sub.add_parser("check")
    w = sub.add_parser("watch")
    w.add_argument("--report", type=Path)
    w.add_argument("--offline", action="store_true", help="compare snapshots only, no network")
    a = sub.add_parser("accept")
    a.add_argument("blocks", nargs="*")
    a.add_argument("--all", action="store_true", help="accept every cited block")
    args = ap.parse_args(argv)

    if args.cmd == "sync":
        from . import boe
        from .model import load_sources

        laws = load_sources(ROOT)["laws"]
        keys = args.law or list(laws)
        for k in keys:
            law = laws[k]["boe"]
            changed = boe.sync_law(ROOT, law, only_changed=not args.full)
            print(f"{k} {law}: {len(changed)} blocks new or changed" + (f": {', '.join(changed[:20])}" if changed else ""))
        return 0
    if args.cmd in ("build", "check"):
        from .compile import build

        return build(ROOT, write=args.cmd == "build")
    if args.cmd == "watch":
        from .watch import watch

        return watch(ROOT, report=args.report, offline=args.offline)
    if args.cmd == "accept":
        from .watch import accept

        return accept(ROOT, args.blocks, all_cited=args.all)
    return 2


if __name__ == "__main__":
    sys.exit(main())
