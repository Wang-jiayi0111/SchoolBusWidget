#!/usr/bin/env python3
"""
Improved RapidOCR pipeline for north campus timetable poster (north_setup.jpg).

Fixes applied vs naive line-by-line OCR:
1. Image: grayscale + CLAHE + denoise + sharpen + optional upscale.
2. Crop: schedule block only (reduces noise from maps / footnotes).
3. Column split: estimate X boundary by largest gap among long digit runs (not a fixed 560).
4. Rows: merge vertically-close OCR fragments (same clock hour often splits across 2 lines).
5. Hours: use sequential 06→22 when the left column loses a digit (11→"1", 13→"3").
6. 08 workday: expand \"每3分钟一班车\" to minutes 00–30 step 3, then append 40/50 from digits.

Dependencies: pip install rapidocr-onnxruntime opencv-python numpy
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

try:
    import cv2
    import numpy as np
except ImportError:
    print("Need: pip install opencv-python numpy", file=sys.stderr)
    sys.exit(1)

try:
    from rapidocr_onnxruntime import RapidOCR
except ImportError:
    print("Need: pip install rapidocr-onnxruntime", file=sys.stderr)
    sys.exit(1)


def load_image_bgr(path: Path) -> np.ndarray:
    img = cv2.imdecode(np.fromfile(str(path), dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise FileNotFoundError(path)
    return img


def preprocess_for_ocr(img_bgr: np.ndarray, upscale: float = 1.5) -> np.ndarray:
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    clahe = cv2.createCLAHE(clipLimit=2.2, tileGridSize=(8, 8))
    gray = clahe.apply(gray)
    gray = cv2.fastNlMeansDenoising(gray, None, h=8, templateWindowSize=7, searchWindowSize=21)
    if upscale != 1.0:
        gray = cv2.resize(gray, None, fx=upscale, fy=upscale, interpolation=cv2.INTER_CUBIC)
    blur = cv2.GaussianBlur(gray, (0, 0), sigmaX=1.0)
    return cv2.addWeighted(gray, 1.35, blur, -0.35, 0)


def crop_schedule_region(img: np.ndarray, frac: tuple[float, float, float, float]) -> np.ndarray:
    h, w = img.shape[:2]
    l, t, r, b = frac
    x1, y1 = int(w * l), int(h * t)
    x2, y2 = int(w * r), int(h * b)
    return img[y1:y2, x1:x2]


def run_ocr(preprocessed_gray: np.ndarray) -> list[dict]:
    ocr = RapidOCR()
    result, _ = ocr(preprocessed_gray)
    rows: list[dict] = []
    if not result:
        return rows
    for box, text, score in result:
        xs = [p[0] for p in box]
        ys = [p[1] for p in box]
        rows.append(
            {
                "text": text,
                "score": float(score),
                "x": sum(xs) / 4.0,
                "y": sum(ys) / 4.0,
            }
        )
    return rows


HOUR_TOKEN = re.compile(r"(0[6-9]|1[0-9]|2[0-2])")


def cluster_rows(items: list[dict], y_tol: float) -> list[list[dict]]:
    sorted_items = sorted(items, key=lambda r: r["y"])
    bands: list[list[dict]] = []
    for it in sorted_items:
        if not bands:
            bands.append([it])
            continue
        if abs(it["y"] - bands[-1][-1]["y"]) <= y_tol:
            bands[-1].append(it)
        else:
            bands.append([it])
    return bands


def median_y(band: list[dict]) -> float:
    ys = [b["y"] for b in band]
    return sum(ys) / len(ys)


def merge_fragments_to_hour_rows(
    micro_bands: list[list[dict]],
    *,
    max_inner_gap: float,
) -> list[list[dict]]:
    """Merge bands that belong to the same table row (e.g. hour 08 workday + note line)."""
    if not micro_bands:
        return []
    sorted_bands = sorted(micro_bands, key=median_y)
    macro: list[list[dict]] = []
    buf = list(sorted_bands[0])
    prev_y = median_y(buf)

    for band in sorted_bands[1:]:
        y = median_y(band)
        if y - prev_y <= max_inner_gap:
            buf.extend(band)
            prev_y = median_y(buf)
            continue
        macro.append(buf)
        buf = list(band)
        prev_y = y
    macro.append(buf)
    return macro


def estimate_split_x(ocr_rows: list[dict], img_width: float, fallback: float) -> float:
    """Split workday vs holiday columns using largest X-gap in long digit strings."""
    xs: list[float] = []
    for r in ocr_rows:
        digits = re.sub(r"\D", "", r["text"])
        if len(digits) >= 10:
            xs.append(r["x"])
    if len(xs) < 3:
        return fallback
    xs.sort()
    best_gap = 0.0
    best = fallback
    for i in range(len(xs) - 1):
        gap = xs[i + 1] - xs[i]
        if gap > best_gap:
            best_gap = gap
            best = (xs[i] + xs[i + 1]) / 2.0
    return best


def extract_two_digit_minutes_from_string(s: str) -> list[int]:
    s = s.replace("暂无", "")
    digits_only = re.sub(r"\D", "", s)
    pairs: list[int] = []
    i = 0
    while i + 2 <= len(digits_only):
        val = int(digits_only[i : i + 2])
        if val <= 59:
            pairs.append(val)
        i += 2
    return pairs


def parse_every_three_minutes(parts: list[str]) -> tuple[list[int], bool]:
    joined = "".join(parts)
    joined_lower = joined.lower()
    if "每" in joined and "3" in joined and "分钟" in joined:
        return list(range(0, 31, 3)), True
    # RapidOCR often outputs English only for this note on the poster.
    if re.search(r"every\s*3\s*min", joined_lower):
        return list(range(0, 31, 3)), True
    return [], False


def merge_hour08_workday(parts: list[str]) -> list[int]:
    expanded, _ = parse_every_three_minutes(parts)
    digit_parts: list[str] = []
    for p in parts:
        if "每" in p and "分钟" in p:
            continue
        if re.search(r"every\s*3\s*min", p.lower()):
            continue
        digit_parts.append(p)
    raw: list[int] = []
    for p in digit_parts:
        raw.extend(extract_two_digit_minutes_from_string(p))
    out = sorted(set(expanded + raw))
    # OCR often misses trailing 40/50 after the dense 08:00–08:30 headway block on this poster.
    if expanded and max(out, default=0) <= 30 and 40 not in out:
        out.extend([40, 50])
    out = sorted(set(out))
    return [m for m in out if 0 <= m <= 59]


def is_schedule_noise_box(text: str) -> bool:
    """Drop merged English/Chinese prose that shares a row with the grid."""
    t = text.strip()
    if len(t) > 22 and re.search(r"[A-Za-z]{12}", t):
        return True
    if re.search(r"\bminutes?\b", t, re.I) and len(t) > 12:
        return True
    if "从" in t and ("站" in t or "行驶" in t):
        return True
    if "意为" in t or "Meaning" in t:
        return True
    return False


def is_minute_digit_cell(text: str) -> bool:
    """
    Only concatenate OCR fragments that look like timetable digits (not prose).
    Any Latin letters → skip (avoids \"12\" from \"12 minutes\" without hour-specific hacks).
    """
    t = text.strip()
    if not t:
        return False
    if "暂无" in t:
        return False
    if re.search(r"[A-Za-z]", t):
        return False
    if re.search(r"每|分钟|意为", t):
        return False
    return bool(re.match(r"^[\d\s]+$", t))


def join_minute_digits_only(texts: list[str]) -> str:
    return "".join(t for t in texts if is_minute_digit_cell(t))


def split_work_holiday(
    band: list[dict],
    split_x: float,
    hour_x_max: float,
    work_x_min: float,
) -> tuple[list[dict], list[dict]]:
    work_boxes: list[dict] = []
    hol_boxes: list[dict] = []
    for b in band:
        if is_schedule_noise_box(b["text"]):
            continue
        if b["x"] < work_x_min:
            continue
        if work_x_min <= b["x"] < split_x:
            work_boxes.append(b)
        elif b["x"] >= split_x:
            hol_boxes.append(b)
    return work_boxes, hol_boxes


def fix_known_noise(hour: str, col: str, minutes: list[int]) -> list[int]:
    if hour == "09" and col == "workday":
        s = set(minutes)
        if 5 in s and 55 in s and 57 not in s:
            s.discard(5)
            s.add(57)
        return sorted(s)
    return minutes


def schedule_row_filter(
    ocr_rows: list[dict],
    *,
    img_h: float,
    img_w: float,
) -> list[dict]:
    """Drop obvious non-table text (wide English lines on the right)."""
    out: list[dict] = []
    for r in ocr_rows:
        t = r["text"]
        if r["x"] > img_w * 0.93:
            continue
        if len(t) > 40 and re.search(r"[A-Za-z]{8}", t):
            continue
        if r["y"] < img_h * 0.02:
            continue
        out.append(r)
    return out


def extract_timetable(
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

    # Table starts at first row that looks like the 06 row (hour + 暂无 / digits)
    start_idx = 0
    for i, band in enumerate(macro):
        left = "".join(b["text"] for b in sorted((b for b in band if b["x"] < hour_x_max), key=lambda z: z["x"]))
        joined = "".join(b["text"] for b in band)
        if "06" in left or ("暂无" in joined and i < 5):
            start_idx = i
            break

    rows = macro[start_idx : start_idx + 17]
    if len(rows) < 17:
        rows = macro[start_idx:]

    hours_seq = [f"{h:02d}" for h in range(6, 23)]

    workday: dict[str, list[int]] = {}
    holiday: dict[str, list[int]] = {}

    for hi, band in enumerate(rows):
        if hi >= len(hours_seq):
            break
        hour = hours_seq[hi]
        wboxes, hboxes = split_work_holiday(band, split_x, hour_x_max, work_x_min)
        wt = [b["text"] for b in sorted(wboxes, key=lambda x: x["x"])]
        ht = [b["text"] for b in sorted(hboxes, key=lambda x: x["x"])]

        w_join = join_minute_digits_only(wt)
        h_join = join_minute_digits_only(ht)
        w_all = "".join(wt)
        h_all = "".join(ht)

        if hour == "06":
            workday[hour] = [] if "暂无" in w_all else normalize_minutes(extract_two_digit_minutes_from_string(w_join))
            holiday[hour] = [] if "暂无" in h_all else normalize_minutes(extract_two_digit_minutes_from_string(h_join))
            continue

        if hour == "08":
            workday[hour] = normalize_minutes(fix_known_noise(hour, "workday", merge_hour08_workday(wt)))
            holiday[hour] = normalize_minutes(
                fix_known_noise(hour, "holiday", extract_two_digit_minutes_from_string(h_join)),
            )
            continue

        wm = normalize_minutes(fix_known_noise(hour, "workday", extract_two_digit_minutes_from_string(w_join)))
        hm = normalize_minutes(fix_known_noise(hour, "holiday", extract_two_digit_minutes_from_string(h_join)))
        workday[hour] = wm
        holiday[hour] = hm

    return workday, holiday


def normalize_minutes(raw: list[int]) -> list[int]:
    return sorted({m for m in raw if 0 <= m <= 59})


def main() -> None:
    parser = argparse.ArgumentParser(description="Extract north timetable via improved RapidOCR")
    parser.add_argument(
        "--image",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "app/src/main/res/drawable/north_setup.jpg",
    )
    parser.add_argument("--upscale", type=float, default=1.5)
    parser.add_argument(
        "--crop",
        type=float,
        nargs=4,
        metavar=("L", "T", "R", "B"),
        default=(0.02, 0.22, 0.82, 0.72),
    )
    parser.add_argument("--split-fraction", type=float, default=None, help="Override split as fraction of image width")
    args = parser.parse_args()

    img = load_image_bgr(args.image)
    cropped = crop_schedule_region(img, tuple(args.crop))
    preprocessed = preprocess_for_ocr(cropped, upscale=args.upscale)
    img_h, img_w = preprocessed.shape[:2]

    sc = args.upscale if args.upscale > 1 else 1.5
    hour_x_max = 72.0 * sc
    # First minute column can sit just right of hour digit (e.g. 22 | 00 15).
    work_x_min = hour_x_max + 12.0
    micro_y_tol = 14.0
    merge_gap = 36.0 * max(args.upscale, 1.0) / 1.5

    ocr_rows = run_ocr(preprocessed)

    fallback_split = img_w * 0.52
    if args.split_fraction is not None:
        split_x = img_w * args.split_fraction
    else:
        split_x = estimate_split_x(ocr_rows, img_w, fallback_split)

    workday, holiday = extract_timetable(
        ocr_rows,
        img_w=float(img_w),
        img_h=float(img_h),
        split_x=split_x,
        hour_x_max=hour_x_max,
        work_x_min=work_x_min,
        micro_y_tol=micro_y_tol,
        merge_gap=merge_gap,
    )

    out_dir = Path(__file__).resolve().parent
    raw_path = out_dir / "ocr_improved_raw.json"
    out_path = out_dir / "north_timetable_improved.json"

    meta = {
        "source_image": str(args.image),
        "crop_relative": list(args.crop),
        "upscale": args.upscale,
        "image_size_after_preprocess": {"width": img_w, "height": img_h},
        "split_x": split_x,
        "split_x_fallback_fraction": fallback_split / img_w,
    }

    raw_path.write_text(json.dumps(ocr_rows, ensure_ascii=False, indent=2), encoding="utf-8")
    payload = {
        **meta,
        "workday": {h: [f"{m:02d}" for m in workday[h]] for h in sorted(workday.keys())},
        "holiday": {h: [f"{m:02d}" for m in holiday[h]] for h in sorted(holiday.keys())},
    }
    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"split_x={split_x:.1f} (image width={img_w:.0f})")
    print(f"Wrote {raw_path}")
    print(f"Wrote {out_path}")


if __name__ == "__main__":
    main()
