#!/usr/bin/env python3
"""Compare poster OCR improved.json against golden.json (golden is authoritative)."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def compare(golden: dict, improved: dict, hour_range: range) -> list[str]:
    diffs: list[str] = []
    for col in ("workday", "holiday"):
        gold_col = golden.get(col, {})
        imp_col = improved.get(col, {})
        for hour in (f"{h:02d}" for h in hour_range):
            expected = gold_col.get(hour, [])
            actual = imp_col.get(hour, [])
            if expected != actual:
                diffs.append(
                    f"[{col}] {hour}:00\n"
                    f"  golden (expected): {expected}\n"
                    f"  improved (actual): {actual}",
                )
    return diffs


def compare_files(
    golden_path: Path,
    improved_path: Path,
    hour_range: range,
    *,
    quiet: bool = False,
) -> int:
    if not golden_path.is_file():
        print(f"golden not found: {golden_path}", file=sys.stderr)
        return 2
    if not improved_path.is_file():
        print(f"improved not found: {improved_path}", file=sys.stderr)
        return 2

    diffs = compare(load_json(golden_path), load_json(improved_path), hour_range)
    if not diffs:
        print(f"OK: {improved_path.name} matches {golden_path.name}")
        return 0

    if not quiet:
        print(f"MISMATCH: {len(diffs)} hour column(s) differ\n")
        print("\n\n".join(diffs))
        print(f"\nGolden file: {golden_path}")
        print(f"Improved file: {improved_path}")
    else:
        print(f"MISMATCH: {len(diffs)} hour column(s) — run without --quiet for details")
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Diff poster OCR improved vs golden")
    parser.add_argument("--golden", type=Path, required=True)
    parser.add_argument("--improved", type=Path, required=True)
    parser.add_argument("--hour-start", type=int, default=6)
    parser.add_argument("--hour-end", type=int, default=22, help="inclusive")
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()
    return compare_files(
        args.golden,
        args.improved,
        range(args.hour_start, args.hour_end + 1),
        quiet=args.quiet,
    )


if __name__ == "__main__":
    raise SystemExit(main())
