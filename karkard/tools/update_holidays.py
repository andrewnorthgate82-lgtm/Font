#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""بازسازی فایل data/holidays.json از روی کتابخانه holidays.

اصلاحات دستی (راستی‌آزمایی‌شده با time.ir) به‌صورت خودکار اعمال می‌شود:
  - شهادت حضرت علی (ع) در سال ۱۴۰۵: ۹ اسفند (نه ۱۰ اسفند)
  - ۷ روز تعطیلی عمومی ۱۰ تا ۱۶ اسفند ۱۴۰۴ (عزای عمومی)

مثال:
    pip install holidays hijri-converter jdatetime
    python tools/update_holidays.py --years 1404 1405 1406 1407
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

# اصلاحات دستی: (سال، ماه، روز) -> عنوان
CORRECTIONS = {
    (1405, 12, 10): None,  # حذف تاریخ اشتباه تخمینی کتابخانه
    (1405, 12, 9): "شهادت حضرت علی (ع)",
}
# تعطیلات ویژه (غیر تقویمی) که کتابخانه ممکن است نداشته باشد
SPECIAL = {
    (1404, 12, d): "تعطیلی عمومی (عزای عمومی)" for d in range(10, 17)
}


def clean_title(text: str) -> str:
    text = text.replace(" (تخمینی)", "").replace(" علیه السلام", " (ع)")
    text = text.replace(" علیه‌السلام", " (ع)").replace(" سلام الله علیها", " (س)")
    text = text.replace(" رسول اکرم (ص)", " رسول اکرم (ص)")
    text = text.replace("؛", " و ").replace("; ", " (مصادف با ").replace(";", " (مصادف با ")
    if "(مصادف با " in text:
        text += ")"
    fixes = {
        "جشن نوروز": "جشن نوروز",
        "عیدنوروز": "عیدنوروز",
        "روز جمهوری اسلامی": "روز جمهوری اسلامی",
        "روز طبیعت": "روز طبیعت",
        "رحلت حضرت امام خمینی": "رحلت حضرت امام خمینی (ره)",
        "قیام 15 خرداد": "قیام ۱۵ خرداد",
        "پیروزی انقلاب اسلامی": "پیروزی انقلاب اسلامی",
        "روز ملی شدن صنعت نفت ایران": "روز ملی شدن صنعت نفت ایران",
        "میلاد رسول اکرم و امام جعفر صادق (ع)": "میلاد رسول اکرم (ص) و امام جعفر صادق (ع)",
        "رحلت رسول اکرم و شهادت امام حسن مجتبی (ع)": "رحلت رسول اکرم (ص) و شهادت امام حسن مجتبی (ع)",
        "ولادت حضرت قائم عجل الله تعالی فرجه و جشن نیمه شعبان": "ولادت حضرت قائم (عج) و جشن نیمه شعبان",
        "مبعث رسول اکرم (ص)": "مبعث رسول اکرم (ص)",
        "ولادت امام علی (ع) و روز پدر": "ولادت امام علی (ع) و روز پدر",
    }
    for old, new in fixes.items():
        text = text.replace(old, new)
    return text.strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--years", type=int, nargs="+", required=True)
    ap.add_argument("--out", default=str(BASE_DIR / "data" / "holidays.json"))
    args = ap.parse_args()

    try:
        import holidays
        import jdatetime
    except ImportError:
        sys.exit("خطا: ابتدا این‌ها را نصب کنید: pip install holidays hijri-converter jdatetime")

    greg_years = []
    for y in args.years:
        greg_years += [y + 621, y + 622]
    ir = holidays.Iran(years=sorted(set(greg_years)))

    data = {}
    for d in sorted(ir.keys()):
        jd = jdatetime.date.fromgregorian(date=d)
        if jd.year not in args.years:
            continue
        data.setdefault(str(jd.year), {})[f"{jd.month}/{jd.day}"] = clean_title(ir[d])

    for (y, m, d), title in {**SPECIAL, **CORRECTIONS}.items():
        if y not in args.years:
            continue
        key = f"{m}/{d}"
        if title is None:
            data.get(str(y), {}).pop(key, None)
        else:
            data.setdefault(str(y), {})[key] = title

    for y in data:
        data[y] = dict(sorted(data[y].items(), key=lambda kv: tuple(map(int, kv[0].split("/")))))

    out = {
        "_meta": {
            "sources": ["کتابخانه holidays + اصلاحات دستی راستی‌آزمایی‌شده با time.ir"],
            "estimated_years": [y for y in args.years if y >= 1406],
            "note_fa": "کلیدها به صورت ماه/روز هستند. سال‌های تخمینی باید با تقویم رسمی کنترل شود.",
        },
        **{str(y): data.get(str(y), {}) for y in args.years},
    }
    Path(args.out).write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    for y in args.years:
        print(f"{y}: {len(data.get(str(y), {}))} روز تعطیل")
    print(f"✅ ذخیره شد: {args.out}")


if __name__ == "__main__":
    main()
