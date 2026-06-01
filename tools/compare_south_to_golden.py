#!/usr/bin/env python3
"""Compare south_timetable_improved.json against south_timetable_golden.json."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from compare_poster_to_golden import compare_files

TOOLS = Path(__file__).resolve().parent
DEFAULT_GOLDEN = TOOLS / "south_timetable_golden.json"
DEFAULT_IMPROVED = TOOLS / "south_timetable_improved.json"
HOUR_RANGE = range(7, 23)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Diff south OCR parse (improved) vs poster golden (authoritative)",
    )
    parser.add_argument("--golden", type=Path, default=DEFAULT_GOLDEN)
    parser.add_argument("--improved", type=Path, default=DEFAULT_IMPROVED)
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()
    return compare_files(args.golden, args.improved, HOUR_RANGE, quiet=args.quiet)


if __name__ == "__main__":
    raise SystemExit(main())
