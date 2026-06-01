#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from extract_north_timetable import (
    cluster_rows,
    crop_schedule_region,
    estimate_split_x,
    extract_timetable,
    load_image_bgr,
    median_y,
    merge_fragments_to_hour_rows,
    preprocess_for_ocr,
    run_ocr,
    schedule_row_filter,
)

SOUTH_CROP = (0.01, 0.24, 1.00, 0.91)


def extract_south_timetable(
    ocr_rows: list[dict],
    *,
    img_w: float,
    img_h: float,
    split_x: float,
    hour_x_max: float,
    work_x_min: float,
    micro_y_tol: float,
    merge_gap: float,
) -> tuple[dict[str, list[int]], dict[str, list[int]]]:
    filtered = schedule_row_filter(ocr_rows, img_h=img_h, img_w=img_w)
    micro = cluster_rows(filtered, y_tol=micro_y_tol)
    macro = merge_fragments_to_hour_rows(micro, max_inner_gap=merge_gap)
    macro.sort(key=median_y)

    start_idx = 0
    for i, band in enumerate(macro):
        left = "".join(b["text"] for b in sorted((b for b in band if b["x"] < hour_x_max), key=lambda z: z["x"]))
        joined = "".join(b["text"] for b in band)
        if "07" in left or ("Hour" in joined and i < 3):
            start_idx = i + (1 if "Hour" in joined else 0)
            if "07" in left:
                start_idx = i
            break

    rows = macro[start_idx : start_idx + 16]
    hours_seq = [f"{h:02d}" for h in range(7, 23)]

    workday: dict[str, list[int]] = {}
    holiday: dict[str, list[int]] = {}

    from extract_north_timetable import (
        extract_two_digit_minutes_from_string,
        fix_known_noise,
        join_minute_digits_only,
        normalize_minutes,
        split_work_holiday,
    )

    for hi, band in enumerate(rows):
        if hi >= len(hours_seq):
            break
        hour = hours_seq[hi]
        wboxes, hboxes = split_work_holiday(band, split_x, hour_x_max, work_x_min)
        wt = [b["text"] for b in sorted(wboxes, key=lambda x: x["x"])]
        ht = [b["text"] for b in sorted(hboxes, key=lambda x: x["x"])]
        w_join = join_minute_digits_only(wt)
        h_join = join_minute_digits_only(ht)

        if hour == "08":
            workday[hour] = normalize_minutes(
                fix_known_noise(hour, "workday", extract_two_digit_minutes_from_string(w_join)),
            )
            holiday[hour] = normalize_minutes(
                fix_known_noise(hour, "holiday", extract_two_digit_minutes_from_string(h_join)),
            )
            continue

        workday[hour] = normalize_minutes(fix_known_noise(hour, "workday", extract_two_digit_minutes_from_string(w_join)))
        holiday[hour] = normalize_minutes(fix_known_noise(hour, "holiday", extract_two_digit_minutes_from_string(h_join)))

    return workday, holiday


def main() -> None:
    import argparse
    import json

    import cv2

    parser = argparse.ArgumentParser(description="Extract south timetable via improved RapidOCR")
    parser.add_argument(
        "--image",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "app/src/main/res/drawable/south_setup.jpg",
    )
    parser.add_argument(
        "--crop",
        type=float,
        nargs=4,
        metavar=("L", "T", "R", "B"),
        default=SOUTH_CROP,
    )
    parser.add_argument("--upscale", type=float, default=1.5)
    args = parser.parse_args()
    out_dir = Path(__file__).resolve().parent

    img = load_image_bgr(args.image)
    cropped = crop_schedule_region(img, tuple(args.crop))
    pre = preprocess_for_ocr(cropped, upscale=args.upscale)
    cropped_path = out_dir / "south_debug_cropped.png"
    preprocessed_path = out_dir / "south_debug_preprocessed.png"
    cv2.imwrite(str(cropped_path), cropped)
    cv2.imwrite(str(preprocessed_path), pre)
    h, w = pre.shape[:2]
    sc = args.upscale if args.upscale > 1 else 1.5
    rows = run_ocr(pre)
    split_x = estimate_split_x(rows, w, w * 0.66)
    wd, hol = extract_south_timetable(
        rows,
        img_w=float(w),
        img_h=float(h),
        split_x=split_x,
        hour_x_max=72 * sc,
        work_x_min=84 * sc,
        micro_y_tol=14.0,
        merge_gap=36.0,
    )

    raw_path = out_dir / "south_ocr_raw.json"
    out_path = out_dir / "south_timetable_improved.json"
    meta = {
        "source_image": str(args.image),
        "crop_relative": list(args.crop),
        "upscale": args.upscale,
        "image_size_after_preprocess": {"width": w, "height": h},
        "split_x": split_x,
        "split_x_fallback_fraction": 0.66,
    }
    raw_path.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    payload = {
        **meta,
        "workday": {hh: [f"{m:02d}" for m in wd[hh]] for hh in sorted(wd.keys())},
        "holiday": {hh: [f"{m:02d}" for m in hol[hh]] for hh in sorted(hol.keys())},
    }
    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"size={w}x{h} split={split_x:.0f} wd={sum(len(v) for v in wd.values())} hol={sum(len(v) for v in hol.values())}")
    print(f"Wrote {cropped_path}")
    print(f"Wrote {preprocessed_path}")
    print(f"Wrote {raw_path}")
    print(f"Wrote {out_path}")

    from compare_poster_to_golden import compare, load_json

    golden_path = out_dir / "south_timetable_golden.json"
    if golden_path.is_file():
        diffs = compare(load_json(golden_path), payload, range(7, 23))
        if diffs:
            print(f"\nCompare vs golden: MISMATCH ({len(diffs)} hour column(s))")
            for block in diffs:
                print(block)
            print(f"\nGolden (authoritative): {golden_path}")
        else:
            print(f"\nCompare vs golden: OK — matches {golden_path.name}")
    else:
        print(f"\nCompare vs golden: skipped ({golden_path.name} not found)")


if __name__ == "__main__":
    main()
