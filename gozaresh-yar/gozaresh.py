#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
گزارش‌یار هوشمند سازمانی — نسخه‌ی ابزار مستقل (رایانه‌ی شخصی)

این برنامه فایل‌های اکسل ماهانه‌ی نواحی/شهرستان‌ها را می‌خواند، داده‌ها را
پاک‌سازی و جمع‌بندی می‌کند و بر اساس «درخواست مدیر» گزارش رسمی می‌سازد.

اجرا:
    python3 gozaresh.py                  → اجرای تعاملی (پیشنهادی)
    python3 gozaresh.py --help           → راهنمای کامل پارامترها

نویسنده: گزارش‌یار هوشمند — بدون وابستگی به سرویس ابری؛ همه‌چیز روی رایانه‌ی شماست.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import html as _html
import json
import os
import re
import sys
import traceback
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

APP_NAME = "گزارش‌یار هوشمند سازمانی"
APP_VERSION = "1.0"

# ----------------------------------------------------------------------------
# وابستگی‌های خارجی (با پیام خطای راهنما)
# ----------------------------------------------------------------------------
# ماژول اختیاری هوش مصنوعی (اگر فایل ai_engine.py نبود، برنامه بدون آن کار می‌کند)
try:
    import ai_engine as ai_engine_module
    from ai_engine import AIEngine, AI_HELP, default_settings_path, load_settings, save_settings
except Exception:  # pragma: no cover
    ai_engine_module = None
    AIEngine = None
    AI_HELP = "ماژول ai_engine.py پیدا نشد؛ هوش مصنوعی در دسترس نیست."

    def default_settings_path(program_dir: str) -> str:      # جایگزین امن
        return os.path.join(program_dir, "ai-settings.json")

    def load_settings(path: str) -> dict:
        return {}

    def save_settings(path: str, data: dict) -> None:
        return None

try:
    import openpyxl
    from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
    from openpyxl.utils import get_column_letter
except ImportError:  # pragma: no cover
    sys.exit("کتابخانه‌ی openpyxl نصب نیست. اجرا کنید:  pip3 install openpyxl")


# ============================================================================
# ۰) تنظیمات پایه — با ویرایش همین بخش می‌توانید برنامه را سلیقه‌ای کنید
# ============================================================================

# نام همه‌ی نواحی/شهرستان‌های مورد انتظار (۳۲ مورد)
EXPECTED_COUNTIES: List[str] = [
    "آران و بیدگل", "امام حسین(ع)", "امام رضا(ع)", "امام صادق(ع)", "امام علی(ع)",
    "اردستان", "برخوار", "بویین و میاندشت", "تیران و کرون", "جرقویه", "چادگان",
    "خمینی شهر", "خوانسار", "خور و بیابانک", "درچه", "دهاقان", "سمیرم", "شاهین شهر",
    "شهرضا", "فریدن", "فریدون شهر", "فلاورجان", "کاشان", "کوهپایه", "گلپایگان",
    "لنجان", "مبارکه", "نایین", "نجف آباد", "نطنز", "ورزنه", "هرند",
]

# «حد انتظار» هر ناحیه بر مبنای فایل کارنامه (از فایل «تهیه کارنامه نواحی.xlsx» استخراج می‌شود)
CAREER_TEMPLATE_NAMES = ["تهیه کارنامه", "کارنامه نواحی", "کارنامه"]

# نام‌های ممکن هر شیت در فایل‌های اکسل (تطبیق تقریبی انجام می‌شود)
SHEET_KEYS: Dict[str, List[str]] = {
    "حضوری": ["سوادرسانه حضوری", "سواد رسانه حضوری", "حضوری"],
    "مجازی": ["سوادرسانه مجازی", "سواد رسانه مجازی", "لایو", "مجازی"],
    "گردان": ["توانمندسازی گردان", "توانمند سازی گردان", "گردان"],
    "خلاقانه": ["سواد رسانه خلاقانه", "سوادرسانه خلاقانه", "خلاقانه"],
    "تولیدات": ["تولیدات", "تولید"],
    "پایه": ["اطلاعات پایه", "داده پایه"],
}

SHEET_LABELS: Dict[str, str] = {
    "حضوری": "سواد رسانه حضوری",
    "مجازی": "سواد رسانه مجازی (لایو)",
    "گردان": "توانمندسازی گردان",
    "خلاقانه": "سواد رسانه خلاقانه",
    "تولیدات": "تولیدات رسانه‌ای",
}

# ستون‌های استاندارد و نام‌های ممکن آن‌ها در فایل‌ها
COLUMN_SYNONYMS: Dict[str, List[str]] = {
    "date": ["تاریخ", "تاریخ برگزاری", "تاریخ محتوای تولید شده", "تاریخ محتوا"],
    "county": ["نام ناحیه", "نام شهرستان", "ناحیه برگزار کننده",
               "نام ناحیه برگزار کننده", "برگزار کننده ناحیه", "ناحیه"],
    "org": ["برگزار کننده", "برگزارکننده", "نهاد برگزار کننده", "متولی",
            "سطح برگزارکننده"],
    "topic": ["موضوع", "عنوان", "سرفصل", "موضوع جلسه"],
    "teacher": ["نام سخنران / مدرس", "نام سخنران/مدرس", "نام مدرس", "سخنران", "مدرس",
                "نام سخنران", "نام تولید کننده محتوا", "تولید کننده محتوا"],
    "minutes": ["مدت (دقیقه)", "مدت", "مدت دقیقه", "زمان"],
    "people": ["تعداد نفرات", "تعداد افراد", "نفرات", "تعداد شرکت کنندگان",
               "تعداد نفرات / تعداد بازدید", "تعداد نفرات/تعداد بازدید", "تعداد بازدید"],
    "place": ["مکان برگزاری", "مکان", "محل برگزاری", "محل"],
    "platform": ["بستر برگزاری", "بستر", "سکوی برگزاری", "نحوه برگزاری"],
    "link": ["لینک مستندات", "لینک پست تلگرام", "لینک", "لینک پست", "نشانی"],
    "content": ["محتوای پست", "متن پست", "متن", "توضیحات"],
    "producer": ["تولید کننده", "تولیدکننده", "نام تولید کننده"],
    "production_kind": ["نوع تولید محتوا", "نوع تولید", "قالب تولید"],
    "pages": ["تعداد صفحات", "تعداد صفحه", "صفحات"],
    "class_kind": ["نوع کلاس", "نوع دوره", "نوع برگزاری"],
    "visits": ["تعداد بازدید", "بازدید", "ویو"],
    "members": ["اعضای انجمن", "تعداد اعضای انجمن"],
    "meeting": ["نشست انجمن", "تعداد نشست انجمن"],
}

# دسته‌بندی پیش‌فرض (طبق دستورالعمل پرامپت — قابل ویرایش)
DEFAULT_CATEGORIES: Dict[str, Dict[str, Any]] = {
    "crisis": {
        "title": "بحران و مسئله ویژه",
        "mode": "keywords",
        "keywords": ["بحران", "شایعه", "واکنش", "فوری", "ویژه", "اضطراری", "حادثه",
                     "زلزله", "سیل", "اعتراض", "اغتشاش", "تخریب", "هجمه"],
    },
    "campaign": {
        "title": "تولیدات و پویش‌های جریان‌ساز",
        "mode": "sheets",
        "sheets": ["تولیدات"],
    },
    "synergy": {
        "title": "هم‌افزایی و اقدامات مشترک",
        "mode": "keywords",
        "keywords": ["مشترک", "همکاری", "تفاهم‌نامه", "تفاهم نامه", "همراه با", "نهاد",
                     "سازمان", "سپاه", "آموزش‌وپرورش", "آموزش و پرورش", "دانشگاه",
                     "بسیج", "اداره", "شهرداری", "بیمه سلامت"],
    },
    "special": {
        "title": "برنامه‌ها و رویدادهای ویژه",
        "mode": "keywords",
        "keywords": ["همایش", "جشنواره", "دوره", "کارگاه", "رویداد", "مسابقه", "اردو",
                     "نمایشگاه", "یادواره", "مراسم"],
    },
    "routine": {
        "title": "اقدامات روتین (از گزارش حذف می‌شود)",
        "mode": "keywords",
        "keywords": ["بازدید", "نظارت", "جلسه", "هماهنگی", "حضور در", "پیگیری اداری",
                     "مکاتبه", "ابلاغ", "صورت‌جلسه"],
    },
}

# دسته‌هایی که به‌طور پیش‌فرض در گزارش نمی‌آیند (روتین)
DEFAULT_EXCLUDED = ["routine"]

# عنوان بخشِ باقی‌مانده‌ی اقدامات (اقداماتی که در دسته‌های انتخابی نمی‌گنجند)
OTHERS_TITLE = "سایر اقدامات و سیمای عمومی"

# حداکثر تعداد اقدام نمایش‌داده‌شده در هر بخش گزارش (بقیه در فایل پیوست «اقدامات»)
MAX_ITEMS_PER_SECTION = 8

# سخت‌گیری در حذف اقدامات روتین:
#   False → اگر یک اقدام هم‌زمان با کلیدواژه‌های روتین و یک دسته‌ی موضوعی (بحران/پویش/...)
#           تطبیق داشته باشد، «غیرروتین» شمرده می‌شود (پیش‌فرض؛ محافظه‌کارانه و ایمن).
#   True  → هر اقدام دارای کلیدواژه‌ی روتین حذف می‌شود، حتی اگر موضوع ویژه‌ای هم داشته باشد.
ROUTINE_STRICT = False


# ============================================================================
# ۱) ابزارهای عمومی
# ============================================================================

_FA_DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")


def fa_digits(text: Any) -> str:
    """تبدیل ارقام فارسی/عربی به لاتین."""
    return str(text).translate(_FA_DIGITS)


def to_en_digits(text: Any) -> str:
    return fa_digits(text)


def normalize_text(value: Any) -> str:
    """یکدست‌سازی متن فارسی: ارقام، ی/ك، نیم‌فاصله، فاصله‌های اضافه."""
    if value is None:
        return ""
    s = str(value)
    s = fa_digits(s)
    s = s.replace("ي", "ی").replace("ك", "ک").replace("ۀ", "ه").replace("أ", "ا")
    s = s.replace("\u200c", " ").replace("\u200f", "").replace("\u200e", "")
    s = unicodedata.normalize("NFKC", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def normalize_key(value: Any) -> str:
    """کلید مقایسه‌ی ساده (بدون فاصله/کاراکتر تزئینی، حذف پرانتز)."""
    s = normalize_text(value)
    s = s.replace("(", "").replace(")", "").replace(")", "").replace("(", "")
    s = s.replace("‌", "").replace(" ", "").replace("-", "").replace("_", "")
    s = s.replace("‏", "").replace("ي", "ی").replace("ك", "ک")
    return s


def county_label(rec) -> str:
    """نام ناحیه برای نمایش؛ رکوردهای بدون نام ناحیه «نامشخص» برچسب می‌گیرند."""
    return rec.county or "نامشخص"


def county_key(value: Any) -> str:
    """کلید مقایسه‌ی نام ناحیه (حذف «و»، فاصله، ع/علیه‌السلام ...)."""
    s = normalize_key(value)
    s = s.replace("و", "") if len(s) > 4 else s
    return s


def to_int(value: Any, default: int = 0) -> int:
    """تبدیل امن مقدار به عدد صحیح (با پشتیبانی ارقام فارسی و جداکننده)."""
    if value is None:
        return default
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, (int, float)):
        return int(round(value))
    s = fa_digits(value).strip()
    if not s:
        return default
    s = s.replace(",", "").replace("٬", "").replace(" ", "")
    m = re.search(r"-?\d+(?:\.\d+)?", s)
    if not m:
        return default
    try:
        return int(round(float(m.group(0))))
    except ValueError:
        return default


def to_float(value: Any, default: float = 0.0) -> float:
    try:
        s = fa_digits(value).strip().replace(",", "").replace("٬", "")
        return float(s)
    except Exception:
        return default


def persian_number(n: Any) -> str:
    """نمایش عدد با ارقام فارسی و جداکننده‌ی هزارگان."""
    try:
        return f"{int(round(float(n))):,}".replace(",", "٬").translate(
            str.maketrans("0123456789", "۰۱۲۳۴۵۶۷۸۹"))
    except Exception:
        return str(n)


def persian_percent(x: float, digits: int = 1) -> str:
    s = f"{x * 100:.{digits}f}".translate(str.maketrans("0123456789.", "۰۱۲۳۴۵۶۷۸۹٫"))
    return s + "٪"


def fa_num(n: Any) -> str:
    """ارقام فارسی بدون جداکننده‌ی هزارگان (برای سال، تاریخ، شماره)."""
    return str(n).translate(str.maketrans("0123456789", "۰۱۲۳۴۵۶۷۸۹"))


def fa_date(d: Optional[Tuple[int, int, int]]) -> str:
    if not d:
        return "—"
    return f"{fa_num(d[0])}/{fa_num(f'{d[1]:02d}')}/{fa_num(f'{d[2]:02d}')}"


def jalali_label(year: int, month: int) -> str:
    return f"{jalali_month_name(month)} {fa_num(year)}"


# مقادیر «سطح سازمانی» که جای نام ناحیه نوشته می‌شوند و نباید ناحیه شمرده شوند
ORG_LEVEL_WORDS = ["ناحیه", "حوزه", "استان", "ستاد", "ستاد استان", "قرارگاه",
                   "سپاه", "بسیج", "منطقه", "مرکز", "معاونت", "سازمان"]
_ORG_LEVEL_KEYS = {normalize_key(w) for w in ORG_LEVEL_WORDS}


def is_org_level(value: Any) -> bool:
    """آیا این مقدار، سطح سازمانی است (نه نام ناحیه)؟"""
    v = normalize_key(value)
    return bool(v) and (v in _ORG_LEVEL_KEYS or "استان" in v or "ستاد" in v)


# ---- تاریخ شمسی ----------------------------------------------------------
JALALI_MONTHS = ["فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                 "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"]


def is_jalali_leap(jy: int) -> bool:
    """تقریب دقیق کبیسه‌ی شمسی بر پایه‌ی الگوریتم ۳۳ ساله."""
    return ((jy + 12) % 33) % 4 == 1 or (jy % 33) in (1, 5, 9, 13, 17, 22, 26, 30)


def parse_shamsi(value: Any) -> Optional[Tuple[int, int, int]]:
    """تجزیه‌ی تاریخ شمسی از انواع قالب‌ها. خروجی: (سال، ماه، روز)."""
    if value is None:
        return None
    if isinstance(value, (_dt.datetime, _dt.date)):
        # تاریخ میلادی ذخیره‌شده در اکسل → تبدیل به شمسی
        g = value.date() if isinstance(value, _dt.datetime) else value
        jy, jm, jd = gregorian_to_jalali(g.year, g.month, g.day)
        return (jy, jm, jd)
    s = fa_digits(value).strip()
    if not s:
        return None
    s = s.replace("\\", "/").replace("-", "/").replace(".", "/")
    m = re.search(r"(\d{2,4})\s*/\s*(\d{1,2})\s*/\s*(\d{1,2})", s)
    if m:
        y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
        if y < 100:
            y += 1400
        if 1 <= mo <= 12 and 1 <= d <= 31:
            return (y, mo, d)
    m = re.search(r"(\d{4})\s*[/\-]?\s*(\d{1,2})\s*[/\-]?\s*(\d{1,2})", s)
    if m:
        return (int(m.group(1)), int(m.group(2)), int(m.group(3)))
    return None


def jalali_month_name(jm: int) -> str:
    if 1 <= jm <= 12:
        return JALALI_MONTHS[jm - 1]
    return str(jm)


def gregorian_to_jalali(gy: int, gm: int, gd: int) -> Tuple[int, int, int]:
    """تبدیل میلادی به شمسی (الگوریتم استاندارد)."""
    g_d_m = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334]
    gy2 = gy - 1600
    gm2 = gm - 1
    gd2 = gd - 1
    g_day_no = 365 * gy2 + (gy2 + 3) // 4 - (gy2 + 99) // 100 + (gy2 + 399) // 400
    g_day_no += g_d_m[gm2] + gd2
    if gm > 2 and ((gy % 4 == 0 and gy % 100 != 0) or gy % 400 == 0):
        g_day_no += 1
    j_day_no = g_day_no - 79
    j_np = j_day_no // 12053
    j_day_no %= 12053
    jy = 979 + 33 * j_np + 4 * (j_day_no // 1461)
    j_day_no %= 1461
    if j_day_no >= 366:
        jy += (j_day_no - 1) // 365
        j_day_no = (j_day_no - 1) % 365
    if j_day_no < 186:
        jm = 1 + j_day_no // 31
        jd = 1 + j_day_no % 31
    else:
        jm = 7 + (j_day_no - 186) // 30
        jd = 1 + (j_day_no - 186) % 30
    return int(jy), int(jm), int(jd)


# ---- استخراج لینک و شناسه تلگرام -----------------------------------------
TG_LINK_RE = re.compile(r"(?:https?://)?t\.me/(?:s/)?([A-Za-z0-9_]+)/(\d+)", re.I)
TG_ANY_RE = re.compile(r"(?:https?://)?t\.me/[A-Za-z0-9_/]+", re.I)
URL_RE = re.compile(r"https?://[^\s\"'<>]+|www\.[^\s\"'<>]+", re.I)


def extract_telegram_links(text: Any) -> List[str]:
    if text is None:
        return []
    s = str(text)
    return [m.group(0) for m in TG_ANY_RE.finditer(s)]


def telegram_post_id(url: str) -> Optional[int]:
    m = TG_LINK_RE.search(str(url))
    if m:
        return int(m.group(2))
    return None


def telegram_channel(url: str) -> Optional[str]:
    m = TG_LINK_RE.search(str(url))
    if m:
        return m.group(1)
    return None


# ---- تاریخ شمسی ↔ میلادی/یونیکس و فیلتر بازه ------------------------------
# اختلاف ساعت ایران با UTC (دقیقه) — برای اینکه تاریخ پست‌ها یک روز جابه‌جا نشود
TZ_OFFSET_MINUTES = 210


def jalali_to_gregorian(jy: int, jm: int, jd: int) -> Tuple[int, int, int]:
    """تبدیل تاریخ شمسی به میلادی (الگوریتم استاندارد جلالی)."""
    jy -= 979
    jm -= 1
    jd -= 1
    j_day_no = 365 * jy + (jy // 33) * 8 + ((jy % 33) + 3) // 4
    month_days = [31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29]
    for m in range(jm):
        j_day_no += month_days[m]
    j_day_no += jd
    g_day_no = j_day_no + 79
    gy = 1600 + 400 * (g_day_no // 146097)
    g_day_no %= 146097
    leap = True
    if g_day_no >= 36525:
        g_day_no -= 1
        gy += 100 * (g_day_no // 36524)
        g_day_no %= 36524
        if g_day_no >= 365:
            g_day_no += 1
        else:
            leap = False
    gy += 4 * (g_day_no // 1461)
    g_day_no %= 1461
    if g_day_no >= 366:
        leap = False
        g_day_no -= 1
        gy += g_day_no // 365
        g_day_no %= 365
    gd_m = [31, 29 if leap else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    gm = 0
    while g_day_no >= gd_m[gm]:
        g_day_no -= gd_m[gm]
        gm += 1
    return gy, gm + 1, g_day_no + 1


def jalali_days_in_month(jy: int, jm: int) -> int:
    if jm <= 6:
        return 31
    if jm <= 11:
        return 30
    return 30 if is_jalali_leap(jy) else 29


def jalali_to_unix(jy: int, jm: int, jd: int, end_of_day: bool = False,
                   tz_offset_minutes: int = TZ_OFFSET_MINUTES) -> int:
    """تبدیل تاریخ شمسی به زمان یونیکس (با لحاظ ساعت محلی)."""
    gy, gm, gd = jalali_to_gregorian(jy, jm, jd)
    base = _dt.datetime(gy, gm, gd, 23, 59, 59 if end_of_day else 0)
    if end_of_day:
        base = base.replace(hour=23, minute=59, second=59)
    else:
        base = base.replace(hour=0, minute=0, second=0)
    ts = int(base.timestamp()) - tz_offset_minutes * 60
    return ts + (0 if not end_of_day else 0)


def unix_to_jalali(ts: int, tz_offset_minutes: int = TZ_OFFSET_MINUTES) -> Tuple[int, int, int]:
    """تبدیل زمان یونیکس به تاریخ شمسی با ساعت محلی ایران."""
    g = _dt.datetime.utcfromtimestamp(ts + tz_offset_minutes * 60)
    return gregorian_to_jalali(g.year, g.month, g.day)


def unix_to_jalali_time(ts: int, tz_offset_minutes: int = TZ_OFFSET_MINUTES) -> str:
    g = _dt.datetime.utcfromtimestamp(ts + tz_offset_minutes * 60)
    jy, jm, jd = gregorian_to_jalali(g.year, g.month, g.day)
    return f"{jy}/{jm:02d}/{jd:02d} {g.hour:02d}:{g.minute:02d}"


def parse_range_bound(text: Any, default_year: int = 1405, is_end: bool = False
                      ) -> Tuple[Optional[int], str]:
    """
    تجزیه‌ی یک سرِ بازه‌ی زمانی. ورودی‌های مجاز:
      «1405/06/01» | «1405-6-1» | «شهریور» | «شهریور 1405» | «1405/06»
    خروجی: (زمان یونیکس مرز، برچسب خوانا)
    """
    if text is None or not normalize_text(text):
        return None, ""
    t = normalize_text(text)
    t = t.replace("\\", "/").replace("-", "/").replace(".", "/")
    y = default_year
    m = None
    d = None
    my = re.search(r"(1[34]\d{2})", t)
    if my:
        y = int(my.group(1))
    for i, name in enumerate(JALALI_MONTHS, start=1):
        if name in t:
            m = i
            break
    md = re.search(r"(?:^|\D)(\d{1,2})\s*/\s*(\d{1,2})(?:\s*/\s*(\d{1,2}))?", t)
    if md:
        if re.search(r"1[34]\d{2}\s*/", t) or len(md.group(1)) == 4:
            parts = re.findall(r"\d+", t)
            if len(parts) >= 3:
                y, m, d = int(parts[0]), int(parts[1]), int(parts[2])
            elif len(parts) == 2:
                y, m = int(parts[0]), int(parts[1])
        else:
            m, d = int(md.group(1)), int(md.group(2))
    if m is None:
        return None, ""
    if d is None:
        d = jalali_days_in_month(y, m) if is_end else 1
    if not (1 <= m <= 12):
        return None, ""
    d = max(1, min(d, jalali_days_in_month(y, m)))
    label = f"{d:02d} {jalali_month_name(m)} {y}"
    return jalali_to_unix(y, m, d, end_of_day=is_end), label


def resolve_date_range(from_text: Any, to_text: Any, default_year: int = 1405) -> Dict[str, Any]:
    """ساخت بازه‌ی زمانی از دو سر بازه؛ خروجی شامل مرزهای یونیکس و برچسب."""
    start, lbl1 = parse_range_bound(from_text, default_year, is_end=False)
    end, lbl2 = parse_range_bound(to_text, default_year, is_end=True)
    if start is not None and end is not None and end < start:
        start, end = end, start
        lbl1, lbl2 = lbl2, lbl1
    label = ""
    if lbl1 and lbl2:
        label = f"{lbl1} تا {lbl2}"
    elif lbl1:
        label = f"از {lbl1}"
    elif lbl2:
        label = f"تا {lbl2}"
    return {"start": start, "end": end, "label": label}


# ---- موضوع‌های رایج (برای تشخیص موضوع از متن پست) ---------------------------
TOPIC_VOCAB: List[str] = [
    "آموزش هوش مصنوعی", "سواد رسانه و فضای مجازی", "آسیب‌های شبکه‌های اجتماعی",
    "آموزش تولید محتوا", "امنیت سایبری", "جنگ شناختی", "سوادرسانه", "سواد رسانه",
    "تفکر نقادانه", "اعتیاد اینترنتی", "بازی‌های رایانه‌ای", "اخبار جعلی",
    "امنیت اطلاعات", "تربیت رسانه‌ای", "رسانه و خانواده", "تولید محتوا",
]

# ---- قواعد تشخیص «نوع فعالیت» از متن پست ------------------------------------
TYPE_RULES: List[Tuple[str, List[str]]] = [
    ("تولیدات", ["کلیپ", "موشن گرافیک", "موشن‌گرافیک", "پوستر", "اینفوگرافیک", "اسلاید",
                 "صفحه word", "بنر", "تیزر", "پادکست", "عکس نوشته", "طرح گرافیکی",
                 "تولید محتوا شد", "محتوای تولیدی"]),
    ("مجازی", ["لایو", "پخش زنده", "فضای مجازی", "آنلاین", "وبینار", "کلاس مجازی",
               "دوره مجازی", "سامانه", "شاد", "ایتا", "روبیکا"]),
    ("گردان", ["گردان", "توانمندسازی گردان", "توانمند سازی گردان", "حلقه‌های صالحین",
               "صالحین"]),
    ("خلاقانه", ["پویش", "کمپین", "مسابقه", "خلاقانه", "جشنواره", "هشتگ", "چالش"]),
]

# ---- الگوهای استخراج عددی/متنی از متن پست -----------------------------------
PEOPLE_PATTERNS = [
    r"(?:تعداد|حضور|شرکت)\s*(?:نفرات|افراد|شرکت\s*کنندگان)?\s*[:=]?\s*(\d{1,5})\s*(?:نفر|نفرات|فرد|مخاطب|دانش\s*آموز|دانش\s*آموزان|معلم|طلبه|دانشجو)",
    r"(\d{1,5})\s*(?:نفر|نفرات|فرد|مخاطب|دانش\s*آموز|دانش\s*آموزان|معلم|طلبه|دانشجو)",
    r"(?:با|و)\s*(?:حضور|مشارکت)\s*(\d{1,5})",
]
TEACHER_PATTERNS = [
    r"(?:مدرس|سخنران|استاد|با\s*تدریس|تدریس)\s*[:=]?\s*((?:آقای|خانم|استاد|دکتر|حجت\s*الاسلام|حجت‌الاسلام)?\s*[آ-ی]{2,}(?:\s+[آ-ی]{2,}){0,2})",
    r"(?:حجت\s*الاسلام|حجت‌الاسلام)\s+(?:و\s*المسلمین\s+)?((?:[آ-ی]{2,}\s+){1,2}[آ-ی]{2,})",
]
PLACE_PATTERNS = [
    r"(?:مکان|محل)\s*[:=]?\s*([آ-ی0-9\s]{3,40}?)(?=[،,.\n]|$)",
    r"(?:در|در\s*جمع)\s+((?:مسجد|دبیرستان|مدرسه|حسینیه|سالن|پایگاه|دانشگاه|اداره|کانون|مسجد\s*جامع|مصلی|امامزاده|مجتمع|هنرستان|دانشکده|ستاد|نمازخانه|کتابخانه)[آ-ی\s]{0,30})",
]
COUNTY_HINT_WORDS = ("ناحیه", "شهرستان", "حوزه")

# واژه‌هایی که نباید در نام مدرس/مکان بمانند
STOP_ENTITY_WORDS = {
    "در", "با", "و", "از", "به", "برای", "برگزار", "برگزارشد", "شد", "را", "که", "این",
    "آن", "های", "ها", "جمع", "حضور", "نفر", "نفری", "تاریخ", "روز", "طی", "جهت", "هم",
    "نیز", "مورد", "توسط", "اعضای", "دانشآموزان", "دانشآموز", "معلمان", "معلم",
    "افتتاح", "اجرا", "داشت", "بود", "شهرستان", "ناحیه", "حوزه", "منطقه", "پایان",
    "برگزارگردید", "برپا", "صورت", "گرفت", "همراه", "همراهی", "مشارکت", "کلاس", "جلسه",
}


def clean_entity(text: str, max_words: int = 3) -> str:
    """پاک‌سازی نام مدرس/مکان از واژه‌های اضافی و افعال."""
    words = [w for w in normalize_text(text).replace("،", " ").split()
             if normalize_key(w) not in STOP_ENTITY_WORDS]
    return " ".join(words[:max_words]).strip(" -،.")


def text_has(text: str, words: Sequence[str]) -> bool:
    for w in words:
        if normalize_key(w) and normalize_key(w) in normalize_key(text):
            return True
    return False


def first_number(text: str, patterns: Sequence[str]) -> int:
    for pat in patterns:
        m = re.search(pat, text)
        if m:
            try:
                return int(fa_digits(m.group(1)))
            except (ValueError, IndexError):
                continue
    return 0


def detect_tg_type(text: str) -> str:
    """تشخیص نوع فعالیت (نام شیت در فایل‌های اکسل) از متن پست."""
    for kind, words in TYPE_RULES:
        if text_has(text, words):
            return kind
    return "حضوری"


def detect_topic(text: str) -> str:
    best = ""
    for t in TOPIC_VOCAB:
        if normalize_key(t) in normalize_key(text) and len(t) > len(best):
            best = t
    if best:
        return best
    if text_has(text, ["همایش", "کارگاه", "نشست", "جلسه", "دوره", "گردهمایی", "اردو",
                       "پویش", "مسابقه", "همکاری", "واکنش", "شایعه"]):
        first = re.split(r"[.،!؟\n]", text.strip())[0]
        for sep in (" در ناحیه", " در شهرستان", " با حضور", " با مشارکت", " با شرکت",
                    " برگزار", " آغاز", " در بستر", " با همکاری", " در "):
            idx = first.find(sep)
            if 6 < idx < 70:
                first = first[:idx]
                break
        return first[:50].strip(" «»\"'")+ ("…" if len(first) > 50 else "")
    return ""


def detect_county(text: str) -> str:
    """تشخیص ناحیه از متن پست (بلندترین تطبیق از فهرست ۳۲ ناحیه)."""
    hay = county_key(text)
    best = ""
    for c in EXPECTED_COUNTIES:
        ck = county_key(c)
        if ck and ck in hay and len(ck) > len(county_key(best)):
            best = c
    return best


def read_telegram_posts(paths: Sequence[str]) -> Tuple[List[Dict[str, Any]], List[str]]:
    """
    خواندن خروجی JSON تلگرام (Telegram Desktop → Export chat history → JSON).
    خروجی: (فهرست پست‌ها، فهرست فایل‌های خوانده‌شده)
    """
    posts: List[Dict[str, Any]] = []
    files: List[str] = []
    for p in paths or []:
        p = os.path.abspath(os.path.expanduser(p))
        if os.path.isfile(p) and p.lower().endswith(".json"):
            files.append(p)
        elif os.path.isdir(p):
            for root, _dirs, fs in os.walk(p):
                files += [os.path.join(root, f) for f in fs if f.lower().endswith(".json")]

    for f in files:
        try:
            with open(f, "r", encoding="utf-8") as fh:
                data = json.load(fh)
        except Exception:
            continue
        msgs = data.get("messages", data) if isinstance(data, dict) else data
        if not isinstance(msgs, list):
            continue
        channel = str((data.get("name") if isinstance(data, dict) else "") or
                      os.path.splitext(os.path.basename(f))[0])
        username = ""
        if isinstance(data, dict):
            username = str(data.get("username") or "")
        if not username:
            mt = re.search(r"(?:https?://)?t\.me/(?:s/)?([A-Za-z0-9_]{3,})", channel)
            if mt:
                username = mt.group(1)
        for m in msgs:
            if not isinstance(m, dict) or m.get("type") == "service":
                continue
            text = m.get("text")
            if isinstance(text, list):
                text = "".join(t if isinstance(t, str) else str(t.get("text", "")) for t in text)
            if not text:
                continue
            try:
                ts = int(m.get("date_unixtime") or 0)
            except (TypeError, ValueError):
                ts = 0
            if not ts:
                continue
            posts.append({
                "id": int(m.get("id") or 0),
                "ts": ts,
                "text": normalize_text(text),
                "raw_text": str(text),
                "channel": channel,
                "username": username,
                "source_file": os.path.basename(f),
            })
    posts.sort(key=lambda x: x["ts"])
    return posts, files


def filter_posts(posts: Sequence[Dict[str, Any]], start: Optional[int],
                 end: Optional[int]) -> List[Dict[str, Any]]:
    out = []
    for p in posts:
        if start is not None and p["ts"] < start:
            continue
        if end is not None and p["ts"] > end:
            continue
        out.append(p)
    return out


def rule_record_from_post(post: Dict[str, Any], uid: int) -> Record:
    """تبدیل یک پست تلگرام به رکورد، با الگوهای قاعده‌محور (بدون هوش مصنوعی)."""
    text = post["text"]
    jy, jm, jd = unix_to_jalali(post["ts"])
    kind = detect_tg_type(text)
    people = first_number(text, PEOPLE_PATTERNS)
    teacher = ""
    for pat in TEACHER_PATTERNS:
        m = re.search(pat, text)
        if m:
            teacher = clean_entity(m.group(1), max_words=3)
            break
    place = ""
    for pat in PLACE_PATTERNS:
        m = re.search(pat, text)
        if m:
            place = clean_entity(m.group(1), max_words=5)
            break
    link = ""
    if post.get("username") and post.get("id"):
        link = f"https://t.me/{post['username']}/{post['id']}"
    return Record(
        county_raw=detect_county(text), county=detect_county(text),
        month_key=f"{jy}-{jm:02d}", sheet=kind, date_raw=None, date=(jy, jm, jd),
        topic=detect_topic(text), teacher=teacher, people=people, place=place,
        production_kind=(next((w for w in TYPE_RULES[0][1] if normalize_key(w) in normalize_key(text)), "")
                         if kind == "تولیدات" else ""),
        platform=("" if kind != "مجازی" else next(
            (x for x in ["تلگرام", "ایتا", "روبیکا", "اینستاگرام", "شاد", "سامانه‌نسرا", "واتساپ"]
             if normalize_key(x) in normalize_key(text)), "فضای مجازی")),
        link=link, content=text,
        source_file=post.get("source_file", ""), source_row=post.get("id", 0),
        post_id=post.get("id", 0), channel=post.get("channel", ""), uid=uid,
        origin="telegram",
    )


AI_MIN_CONFIDENCE = 25   # حداقل اطمینان مدل برای جایگزینی مقادیر قاعده‌محور


def apply_ai_extraction(rec: Record, ai_row: Dict[str, Any]) -> bool:
    """
    اعمال نتیجه‌ی هوش مصنوعی روی رکورد قاعده‌محور.
    فقط مقادیر معتبر و غیرخالی جایگزین می‌شوند؛ اگر مدل مقدار نداده باشد، مقدار قاعده‌محور می‌ماند.
    """
    changed = False
    seen: set = set()
    try:
        conf0 = int(float(fa_digits(str(ai_row.get("اطمینان", 0)))))
    except (AttributeError, TypeError, ValueError):
        conf0 = 0
    if conf0 and conf0 < AI_MIN_CONFIDENCE:
        return False  # مدل خودش اطمینان کافی نداده → داده‌ی قاعده‌محور حفظ می‌شود
    if isinstance(ai_row, dict):
        mapping = {"ناحیه": "county", "مدرس": "teacher", "مکان": "place", "موضوع": "topic"}
        for key, attr in mapping.items():
            val = normalize_text(ai_row.get(key) or "")
            if len(val) >= 3 and getattr(rec, attr, "") != val:
                if attr == "county":
                    val = resolve_county(val)
                    if val not in EXPECTED_COUNTIES:
                        continue
                if attr in ("teacher", "place"):
                    val = clean_entity(val, max_words=3 if attr == "teacher" else 5)
                    if len(val) < 3:
                        continue
                if attr == "teacher" and val in seen:
                    continue
                setattr(rec, attr, val)
                seen.add(val)
                changed = True
        people = to_int(ai_row.get("تعداد") or ai_row.get("people") or 0)
        if people > 0 and rec.people != people:
            rec.people = people
            changed = True
        kind = normalize_text(ai_row.get("نوع") or "")
        if kind in ("حضوری", "مجازی", "گردان", "خلاقانه", "تولیدات") and rec.sheet != kind:
            rec.sheet = kind
            changed = True
        try:
            conf = int(float(fa_digits(str(ai_row.get("اطمینان", 0)))))
        except (TypeError, ValueError):
            conf = 0
        rec.ai_confidence = max(0, min(100, conf))
    return changed


def build_telegram_dataset(posts: Sequence[Dict[str, Any]], log, ai=None,
                           tasks: Sequence[str] = (), fill_gaps_links: Sequence[str] = ()
                           ) -> Tuple[List[Record], Dict[str, Any]]:
    """
    ساخت رکوردها از پست‌های تلگرام (به‌همراه استفاده‌ی اختیاری از هوش مصنوعی).
    خروجی: (رکوردها، آمار)
    """
    stats: Dict[str, Any] = {"posts": len(posts), "records": 0, "ai_used": 0,
                             "no_county": 0, "no_data": [], "skipped_existing": 0}
    existing_ids = set()
    for link in fill_gaps_links:
        pid = telegram_post_id(link)
        if pid:
            existing_ids.add(pid)

    use_ai_extract = bool(ai) and "extract" in tasks
    ai_rows: Dict[int, Dict[str, Any]] = {}
    if use_ai_extract and posts:
        log(f"   استخراج هوشمند {persian_number(len(posts))} پست با مدل "
            f"«{getattr(ai, 'model', '')}» …")
        ai_rows, ai_stats = ai.extract_records(posts)
        log(f"   نتیجه: {persian_number(len(ai_rows))} پست با مدل تحلیل شد. "
            f"({ai_stats.summary()})")

    records: List[Record] = []
    uid = 1
    for post in posts:
        pid = post.get("id")
        if pid in existing_ids:
            stats["skipped_existing"] += 1
            continue
        rec = rule_record_from_post(post, uid)
        uid += 1
        used_ai = False
        if pid in ai_rows:
            used_ai = apply_ai_extraction(rec, ai_rows[pid])
            if used_ai:
                stats["ai_used"] += 1
        if not rec.county:
            stats["no_county"] += 1
        has_data = bool(rec.county or rec.people or rec.teacher or rec.topic or rec.place)
        if not has_data:
            stats["no_data"].append({"id": pid, "date": unix_to_jalali_time(post["ts"]),
                                     "text": post["text"][:200]})
        rec.ai_confidence = getattr(rec, "ai_confidence", 0)
        records.append(rec)
    stats["records"] = len(records)
    return records, stats


# ---- خواندن/نوشتن فایل ----------------------------------------------------
def ensure_dir(path: str) -> str:
    os.makedirs(path, exist_ok=True)
    return path


def slugify_fa(text: str, max_len: int = 60) -> str:
    s = normalize_text(text)
    s = re.sub(r"[\\/:*?\"<>|]", "-", s)
    s = re.sub(r"\s+", "_", s)
    return s[:max_len]


# ============================================================================
# ۲) ساختار داده
# ============================================================================

@dataclass
class Record:
    """یک سطر داده (یک فعالیت/تولید)."""
    county_raw: str
    county: str
    month_key: str            # مثل "1405-06"
    sheet: str                # کلید شیت: حضوری/مجازی/گردان/خلاقانه/تولیدات
    date_raw: Any = None
    date: Optional[Tuple[int, int, int]] = None
    topic: str = ""
    teacher: str = ""
    minutes: int = 0
    people: int = 0
    visits: int = 0
    place: str = ""
    platform: str = ""
    org: str = ""
    producer: str = ""
    production_kind: str = ""
    pages: int = 0
    class_kind: str = ""
    link: str = ""
    content: str = ""
    source_file: str = ""
    source_row: int = 0
    post_id: int = 0                 # شماره پست تلگرام (در صورت وجود)
    channel: str = ""                # نام کانال تلگرام
    origin: str = "excel"            # منبع رکورد: excel یا telegram
    uid: int = 0                     # شناسه یکتا برای ارجاع به مدل هوش مصنوعی
    ai_confidence: int = 0           # میزان اطمینان مدل (۰ تا ۱۰۰)
    ai_impact: str = ""              # جمله‌ی «نتیجه و اثر» نوشته‌شده توسط مدل
    categories: List[str] = field(default_factory=list)

    @property
    def structured_text(self) -> str:
        """متن ستون‌های ساختاریافته (بدون متن پست تلگرام)."""
        return " ".join([self.topic, self.teacher, self.place, self.platform,
                         self.org, self.producer, self.production_kind])

    @property
    def text(self) -> str:
        """متن کامل، شامل متن پست تلگرام (برای دسته‌بندی موضوعی)."""
        return (self.structured_text + " " + self.content).strip()


@dataclass
class FileReport:
    """وضعیت خواندن هر فایل."""
    path: str
    county: str = ""
    ok: bool = True
    rows: int = 0
    empty_sheets: List[str] = field(default_factory=list)
    missing_sheets: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)
    error: str = ""


@dataclass
class Dataset:
    records: List[Record] = field(default_factory=list)
    files: List[FileReport] = field(default_factory=list)
    months: List[str] = field(default_factory=list)      # ["1405-06", ...]
    counties: List[str] = field(default_factory=list)
    expectations: Dict[str, Dict[str, float]] = field(default_factory=dict)
    warnings: List[str] = field(default_factory=list)
    source: str = "excel"                        # excel | telegram | both
    tg_stats: Dict[str, Any] = field(default_factory=dict)
    date_range_label: str = ""
    ai_note: str = ""

    def month_label(self, mk: str) -> str:
        try:
            y, m = mk.split("-")
            return jalali_label(int(y), int(m))
        except Exception:
            return mk


# ============================================================================
# ۳) کشف ورودی‌ها (فایل/پوشه)
# ============================================================================

def discover_inputs(paths: Sequence[str]
                    ) -> Tuple[Dict[str, List[str]], List[str], List[str]]:
    """
    ورودی: مسیر فایل‌ها/پوشه‌ها.
    خروجی: دیکشنری {نام پوشه/دوره: [مسیر فایل‌های اکسل]}، فهرست هشدارها و
           فهرست فایل‌های JSON تلگرام که خودکار پیدا شده‌اند.
    منطق: هر زیرپوشه‌ی مستقیم (یا خود پوشه‌ی داده‌شده) یک «دوره» است.
    فایل‌های اکسل چسبیده در ریشه‌ی یک پوشه هم به همان پوشه نسبت داده می‌شوند.
    """
    warns: List[str] = []
    json_files: List[str] = []
    periods: Dict[str, List[str]] = {}

    def add(period: str, f: str):
        periods.setdefault(period, [])
        if f not in periods[period]:
            periods[period].append(f)

    for p in paths:
        p = os.path.abspath(os.path.expanduser(p))
        if not os.path.exists(p):
            warns.append(f"مسیر یافت نشد و نادیده گرفته شد: {p}")
            continue
        if os.path.isfile(p):
            if p.lower().endswith((".xlsx", ".xlsm")):
                if _is_aux_file(p):
                    warns.append(f"فایل «{os.path.basename(p)}» جدول «حد انتظار/کارنامه» تشخیص داده شد "
                                 f"و به‌عنوان داده‌ی فعالیت خوانده نمی‌شود.")
                else:
                    add(_period_name(os.path.dirname(p)) or "دوره ۱", p)
            elif p.lower().endswith(".json"):
                json_files.append(p)      # خودِ فایل خروجی تلگرام داده شده است
            elif os.path.basename(p).lower().endswith((".html", ".txt", ".csv")):
                warns.append(f"این فایل «خروجی JSON تلگرام» نیست؛ در تلگرام قالب "
                             f"JSON را انتخاب کنید: {os.path.basename(p)}")
            else:
                warns.append(f"فایل پشتیبانی‌نشده (فقط xlsx/xlsm و خروجی JSON تلگرام): {p}")
            continue

        # پوشه: ابتدا فایل‌های اکسل خودِ پوشه
        direct = sorted([os.path.join(p, f) for f in os.listdir(p)
                         if f.lower().endswith((".xlsx", ".xlsm")) and not f.startswith("~$")])
        json_files += [os.path.join(p, f) for f in sorted(os.listdir(p))
                       if f.lower().endswith(".json") and not f.startswith("~$")]
        subdirs = sorted([os.path.join(p, d) for d in os.listdir(p)
                          if os.path.isdir(os.path.join(p, d)) and not d.startswith(".")])

        root_has_data = any(not _is_aux_file(f) for f in direct)
        if root_has_data and subdirs:
            # حالت ترکیبی: فایل‌های ریشه + پوشه‌های ماهانه
            for f in direct:
                if not _is_aux_file(f):
                    add(_period_name(p) or "دوره ۱", f)
        elif direct and not subdirs:
            for f in direct:
                if not _is_aux_file(f):
                    add(_period_name(p) or "دوره ۱", f)

        for d in subdirs:
            entries = os.listdir(d)
            json_files += [os.path.join(d, f) for f in entries
                           if f.lower().endswith(".json") and not f.startswith("~$")]
            files = sorted([os.path.join(d, f) for f in entries
                            if f.lower().endswith((".xlsx", ".xlsm")) and not f.startswith("~$")])
            good = [f for f in files if not _is_aux_file(f)]
            if not good:
                if not files and not any(f.lower().endswith(".json") for f in entries):
                    warns.append(f"پوشه‌ی «{os.path.basename(d)}» خالی است "
                                 f"(هیچ فایل اکسل یا خروجی تلگرامی ندارد).")
                continue
            for f in good:
                add(os.path.basename(d), f)

    return periods, warns, json_files


def find_career_files(paths: Sequence[str], max_depth: int = 2) -> List[str]:
    """یافتن فایل‌های «کارنامه/حد انتظار» در مسیرهای ورودی (برای محاسبه‌ی درصد تحقق)."""
    found: List[str] = []
    for p in paths or []:
        p = os.path.abspath(os.path.expanduser(p))
        if os.path.isfile(p):
            if _is_aux_file(p):
                found.append(p)
            continue
        if not os.path.isdir(p):
            continue
        base_depth = p.rstrip(os.sep).count(os.sep)
        for root, _dirs, files in os.walk(p):
            if root.count(os.sep) - base_depth > max_depth:
                continue
            for f in files:
                fp = os.path.join(root, f)
                if f.lower().endswith((".xlsx", ".xlsm")) and _is_aux_file(fp):
                    found.append(fp)
    return found


def _is_aux_file(path: str) -> bool:
    """فایل‌های کمکی مثل «کارنامه» داده‌ی فعالیت نیستند."""
    base = normalize_text(os.path.basename(path))
    return any(k in base for k in CAREER_TEMPLATE_NAMES) or "کارنامه" in base


def _period_name(path: str) -> str:
    base = os.path.basename(os.path.abspath(path))
    return base if base not in ("", ".", "..") else "دوره ۱"


def infer_county_from_filename(path: str) -> str:
    """تشخیص نام ناحیه از نام فایل با تطبیق تقریبی با فهرست ۳۲ ناحیه."""
    base = os.path.splitext(os.path.basename(path))[0]
    base_key = county_key(base)
    best, best_len = "", 0
    for c in EXPECTED_COUNTIES:
        ck = county_key(c)
        if ck and ck in base_key and len(ck) > best_len:
            best, best_len = c, len(ck)
    if best:
        return best
    # اگر «امام حسین ع» نوشته شده باشد
    for c in EXPECTED_COUNTIES:
        parts = [p for p in re.split(r"[\s()]+", c) if p]
        if parts and all(county_key(p) in base_key for p in parts):
            return c
    cleaned = normalize_text(base)
    cleaned = re.sub(r"(گزارش|ماه|ناحیه|شهرستان|سال|\d+)", " ", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned or base


# ============================================================================
# ۴) خواندن فایل‌های اکسل
# ============================================================================

def match_sheet(wb, key: str) -> Optional[Any]:
    """پیدا کردن شیت بر اساس نام‌های احتمالی."""
    names = {normalize_key(ws.title): ws for ws in wb.worksheets}
    for cand in SHEET_KEYS.get(key, []):
        ck = normalize_key(cand)
        if ck in names:
            return names[ck]
    # تطبیق تقریبی
    for ws in wb.worksheets:
        nk = normalize_key(ws.title)
        for cand in SHEET_KEYS.get(key, []):
            ck = normalize_key(cand)
            if ck and (ck in nk or nk in ck):
                return ws
    return None


def header_map(ws, header_row: int = 1) -> Dict[str, int]:
    """نقشه‌ی «نام استاندارد ستون → شماره ستون»."""
    mapping: Dict[str, int] = {}
    raw: Dict[int, str] = {}
    # گاهی سطر عنوان در سطر ۱ یا ۲ است؛ یکی از دو سطر اول که پرتر باشد ملاک است
    best_row, best_score = header_row, -1
    for r in (1, 2):
        score = sum(1 for c in range(1, min(ws.max_column, 25) + 1)
                    if normalize_text(ws.cell(r, c).value))
        if score > best_score:
            best_row, best_score = r, score
    for c in range(1, ws.max_column + 1):
        v = normalize_text(ws.cell(best_row, c).value)
        if v:
            raw[c] = v
    used = set()
    for field_name, syns in COLUMN_SYNONYMS.items():
        for c, v in raw.items():
            if c in used:
                continue
            vk = normalize_key(v)
            for s in syns:
                sk = normalize_key(s)
                if vk == sk or (sk and (sk in vk or vk in sk)) and len(vk) >= 3:
                    mapping[field_name] = c
                    used.add(c)
                    break
            if field_name in mapping:
                break
    return mapping


def read_workbook(path: str, period: str, log) -> Tuple[List[Record], FileReport]:
    """خواندن یک فایل اکسل و تبدیل به رکوردها."""
    fr = FileReport(path=path)
    county_guess = infer_county_from_filename(path)
    fr.county = county_guess
    records: List[Record] = []
    try:
        wb = openpyxl.load_workbook(path, data_only=True, read_only=False)
    except Exception as e:
        fr.ok = False
        fr.error = f"فایل باز نشد (احتمالاً خراب است): {e}"
        log(fr.error, level="warn")
        return records, fr

    found_any_sheet = False
    for key in ["حضوری", "مجازی", "گردان", "خلاقانه", "تولیدات"]:
        ws = match_sheet(wb, key)
        if ws is None:
            fr.missing_sheets.append(SHEET_LABELS[key])
            continue
        found_any_sheet = True
        cmap = header_map(ws)
        header_row = 1
        rows_added = 0
        for r in range(1, ws.max_row + 1):
            date_col = cmap.get("date")
            if date_col and normalize_text(ws.cell(r, date_col).value):
                header_row = r  # سطر عنوان
                break
        for r in range(header_row + 1, ws.max_row + 1):
            def get(field_name: str) -> Any:
                c = cmap.get(field_name)
                return ws.cell(r, c).value if c else None

            vals = [get(f) for f in
                    ("date", "county", "topic", "teacher", "people", "link",
                     "production_kind", "platform", "place", "content")]
            if not any(normalize_text(v) for v in vals):
                continue  # سطر خالی

            date_raw = get("date")
            d = parse_shamsi(date_raw)
            if d is None and not normalize_text(date_raw):
                d = None
            # تعیین نام ناحیه: اول ستون «نام ناحیه»، در غیر این صورت نام فایل
            county_raw = normalize_text(get("county"))
            org_val = normalize_text(get("org"))
            if is_org_level(county_raw):
                org_val = org_val or county_raw
                county_raw = ""
            county_raw = county_raw or county_guess
            county = resolve_county(county_raw)
            if county not in EXPECTED_COUNTIES and county_guess in EXPECTED_COUNTIES:
                # ستون نام ناحیه با فهرست استاندارد نمی‌خواند → نام فایل ملاک است
                county_note = (f"نام ناحیه در ستون جدول («{county_raw}») با فهرست استاندارد "
                               f"نمی‌خواند؛ نام فایل («{county_guess}») ملاک قرار گرفت.")
                if county_note not in fr.warnings:
                    fr.warnings.append(county_note)
                county = county_guess
            month_key = f"{d[0]}-{d[1]:02d}" if d else period
            rec = Record(
                county_raw=county_raw, county=county, month_key=month_key, sheet=key,
                date_raw=date_raw, date=d,
                topic=normalize_text(get("topic")),
                teacher=normalize_text(get("teacher")) or normalize_text(get("producer")),
                minutes=to_int(get("minutes")),
                people=to_int(get("people")) or to_int(get("visits")),
                visits=to_int(get("visits")),
                place=normalize_text(get("place")),
                platform=normalize_text(get("platform")),
                org=org_val,
                producer=normalize_text(get("producer")),
                production_kind=normalize_text(get("production_kind")),
                pages=to_int(get("pages")),
                class_kind=normalize_text(get("class_kind")),
                link=normalize_text(get("link")),
                content=normalize_text(get("content")),
                source_file=os.path.basename(path), source_row=r,
            )
            records.append(rec)
            rows_added += 1

        if rows_added == 0:
            fr.empty_sheets.append(SHEET_LABELS[key])
        else:
            fr.rows += rows_added

    if not found_any_sheet:
        fr.ok = False
        fr.error = "هیچ‌کدام از ۵ شیت استاندارد در این فایل پیدا نشد."
        log(fr.error, level="warn")
    elif fr.rows == 0:
        fr.warnings.append("فایل هیچ سطر داده‌ای ندارد (فقط سرستون‌ها).")

    try:
        wb.close()
    except Exception:
        pass
    return records, fr


_COUNTY_INDEX: Dict[str, str] = {}


def build_county_index(extra_names: Iterable[str] = ()) -> None:
    _COUNTY_INDEX.clear()
    for c in list(EXPECTED_COUNTIES) + [n for n in extra_names if n]:
        _COUNTY_INDEX.setdefault(county_key(c), c)


def resolve_county(raw: str) -> str:
    """نام ناحیه را به شکل استاندارد فهرست ۳۲گانه برمی‌گرداند."""
    if not raw:
        return ""
    k = county_key(raw)
    if k in _COUNTY_INDEX:
        return _COUNTY_INDEX[k]
    for ck, canonical in _COUNTY_INDEX.items():
        if ck and (ck in k or k in ck) and min(len(ck), len(k)) >= 4:
            return canonical
    # تلاش دوم: تطبیق با حذف «ع» پایانی و «و»
    k2 = k.replace("ع", "")
    for ck, canonical in _COUNTY_INDEX.items():
        ck2 = ck.replace("ع", "")
        if ck2 and (ck2 in k2 or k2 in ck2) and min(len(ck2), len(k2)) >= 4:
            return canonical
    return normalize_text(raw)


def read_telegram_export(paths: Sequence[str]) -> Dict[Tuple[str, int], str]:
    """
    خواندن خروجی JSON تلگرام (Telegram Desktop → Export chat history → JSON).
    خروجی: دیکشنری {(نام کانال، شماره‌ی پست): متن پست}
    پشتیبانی از دو قالب: لیست ساده‌ی پیام‌ها یا {"messages": [...]}
    """
    index: Dict[Tuple[str, int], str] = {}
    files: List[str] = []
    for p in paths or []:
        p = os.path.abspath(os.path.expanduser(p))
        if os.path.isfile(p) and p.lower().endswith(".json"):
            files.append(p)
        elif os.path.isdir(p):
            for root, _dirs, fs in os.walk(p):
                files += [os.path.join(root, f) for f in fs if f.lower().endswith(".json")]

    for f in files:
        try:
            with open(f, "r", encoding="utf-8") as fh:
                data = json.load(fh)
        except Exception:
            continue
        msgs = data.get("messages", data) if isinstance(data, dict) else data
        if not isinstance(msgs, list):
            continue
        channel = (data.get("name") if isinstance(data, dict) else None) or \
                  os.path.splitext(os.path.basename(f))[0]
        channel = re.sub(r"^chat\d+$", channel, str(channel))
        for m in msgs:
            if not isinstance(m, dict):
                continue
            pid = m.get("id")
            text = m.get("text")
            if isinstance(text, list):  # قالب متن چندتکه‌ای تلگرام
                text = "".join(t if isinstance(t, str) else str(t.get("text", "")) for t in text)
            if pid is None or not text:
                continue
            index[(str(channel), int(pid))] = normalize_text(text)
    return index


def attach_telegram_content(dataset: Dataset, index: Dict[Tuple[str, int], str], log) -> int:
    """اتصال متن پست‌های تلگرام به رکوردها (برای دسته‌بندی دقیق‌تر)."""
    if not index:
        return 0
    by_id: Dict[int, List[str]] = defaultdict(list)
    for (ch, pid), text in index.items():
        by_id[pid].append(text)
    hits = 0
    for rec in dataset.records:
        if rec.content:
            continue
        pid = telegram_post_id(rec.link)
        if pid is None:
            continue
        texts = by_id.get(pid)
        if texts:
            rec.content = texts[0]
            hits += 1
    if hits:
        log(f"متن {persian_number(hits)} پست از خروجی تلگرام بازخوانی و به رکوردها متصل شد.",
            level="ok")
    return hits


def read_expectations(paths: Iterable[str]) -> Dict[str, Dict[str, float]]:
    """خواندن جدول «حد انتظار» از فایل کارنامه (در صورت وجود)."""
    result: Dict[str, Dict[str, float]] = {}
    for p in paths:
        try:
            wb = openpyxl.load_workbook(p, data_only=True)
        except Exception:
            continue
        target = None
        for ws in wb.worksheets:
            if "حد انتظار" in normalize_text(ws.title) or "پایگاه داده" in normalize_text(ws.title):
                target = ws
                break
        if target is None:
            continue
        cmap = header_map(target)
        for r in range(2, target.max_row + 1):
            name = normalize_text(target.cell(r, 1).value)
            if not name:
                continue
            county = resolve_county(name)
            row: Dict[str, float] = {}
            for field_name in ("people", "members", "meeting"):
                c = cmap.get(field_name)
                if c:
                    row[field_name] = to_float(target.cell(r, c).value)
            # ستون‌های تخصصی کارنامه: حوزه / حضوری / مجازی / خلاقانه / تولیدات
            header_vals = [normalize_text(target.cell(1, c).value) for c in range(1, target.max_column + 1)]
            for c, h in enumerate(header_vals, start=1):
                if not h:
                    continue
                if "حوزه" in h:
                    row["hoze"] = to_float(target.cell(r, c).value)
                elif "حضوری" in h or "توانمند" in h:
                    row["hazeri"] = to_float(target.cell(r, c).value)
                elif "مجازی" in h or "لایو" in h:
                    row["majazi"] = to_float(target.cell(r, c).value)
                elif "خلاقانه" in h:
                    row["khalaghane"] = to_float(target.cell(r, c).value)
                elif "تولیدات" in h:
                    row["toliat"] = to_float(target.cell(r, c).value)
                elif "انجمن" in h and "نشست" in h:
                    row["meeting"] = to_float(target.cell(r, c).value)
                elif "اعضا" in h:
                    row["members"] = to_float(target.cell(r, c).value)
            if row:
                result[county] = row
    return result


# ============================================================================
# ۵) دسته‌بندی هوشمند
# ============================================================================

def kw_in(keyword: str, hay_text: str, hay_key: str) -> bool:
    """تطبیق کلیدواژه: تک‌واژه‌ها با مرز واژه، عبارت‌ها به‌صورت زیررشته."""
    if not keyword:
        return False
    kw = normalize_text(keyword)
    if not kw:
        return False
    if " " in kw:
        return kw in hay_text
    pattern = r"(?<![\u0600-\u06FF\u200c\w])" + re.escape(kw) + r"(?![\u0600-\u06FF\u200c\w])"
    return re.search(pattern, hay_text) is not None


def classify_records(dataset: Dataset, categories: Dict[str, Dict[str, Any]]) -> None:
    """دسته‌بندی هوشمند رکوردها بر اساس کلیدواژه/شیت."""
    for rec in dataset.records:
        hay_text = normalize_text(rec.text)
        hay_key = normalize_key(rec.text)
        # برای تشخیص «روتین»، فقط ستون‌های ساختاریافته ملاک است (نه متن پست تلگرام)
        hay_structured = normalize_text(rec.structured_text)
        positive: List[str] = []
        routine: List[str] = []

        for name, spec in categories.items():
            mode = spec.get("mode")
            if name == "routine":
                continue
            if mode == "sheets":
                if rec.sheet in spec.get("sheets", []):
                    positive.append(name)
            elif mode == "keywords":
                for kw in spec.get("keywords", []):
                    if kw_in(kw, hay_text, hay_key):
                        positive.append(name)
                        break
            elif mode == "min_people":
                if rec.people >= int(spec.get("threshold", 10 ** 9)):
                    positive.append(name)

        # ارتقای خودکار اقدامات پرحضور به دسته‌ی «ویژه» (ملاک تعداد مخاطب)
        if rec.people >= 100 and "special" in categories:
            positive.append("special")

        for kw in categories.get("routine", {}).get("keywords", []):
            if kw_in(kw, hay_structured, normalize_key(rec.structured_text)):
                routine.append("routine")
                break

        cats = list(positive)
        if routine and (ROUTINE_STRICT or not positive):
            cats.append("routine")
        rec.categories = cats


def select_records(dataset: Dataset, include: Sequence[str], exclude: Sequence[str],
                   months: Sequence[str], counties: Sequence[str],
                   only_included: bool = False) -> List[Record]:
    """اعمال فیلترهای زمانی/مکانی/موضوعی روی رکوردها."""
    out = []
    for rec in dataset.records:
        if months and rec.month_key not in months:
            continue
        if counties and rec.county not in counties:
            continue
        if exclude and any(x in rec.categories for x in exclude):
            continue
        if include and only_included and not any(x in rec.categories for x in include):
            continue
        out.append(rec)
    return out


# ============================================================================
# ۶) جمع‌بندی آماری
# ============================================================================

def aggregate(records: Sequence[Record]) -> Dict[str, Any]:
    agg: Dict[str, Any] = {
        "total_activities": len(records),
        "total_people": sum(r.people for r in records),
        "total_minutes": sum(r.minutes for r in records),
        "total_pages": sum(r.pages for r in records),
        "counties": len({r.county for r in records if r.county}),
        "teachers": len({r.teacher for r in records if r.teacher}),
        "locations": len({r.place for r in records if r.place}),
        "platforms": len({r.platform for r in records if r.platform}),
        "by_sheet": Counter(r.sheet for r in records),
        "by_sheet_people": Counter(),
        "by_county": Counter(county_label(r) for r in records),
        "by_county_people": Counter(),
        "by_month": Counter(r.month_key for r in records),
        "by_month_people": Counter(),
        "topics": Counter(r.topic for r in records if r.topic),
        "teachers_list": Counter(r.teacher for r in records if r.teacher),
        "platforms_list": Counter(r.platform for r in records if r.platform),
        "production_kinds": Counter(r.production_kind for r in records if r.production_kind),
        "categories": Counter(),
        "links": [],
    }
    for r in records:
        agg["by_sheet_people"][r.sheet] += r.people
        agg["by_county_people"][county_label(r)] += r.people
        agg["by_month_people"][r.month_key] += r.people
        for c in r.categories:
            agg["categories"][c] += 1
        if r.link:
            agg["links"].append(r.link)
    agg["with_link"] = len(agg["links"])
    agg["with_content"] = sum(1 for r in records if r.content)
    agg["unique_links"] = len({telegram_post_id(l) or l for l in agg["links"]})
    return agg


def month_over_month(records: Sequence[Record], months: Sequence[str]) -> List[Dict[str, Any]]:
    """مقایسه‌ی ماه‌به‌ماه (رشد درصدی)."""
    rows = []
    prev_act = prev_people = None
    for mk in months:
        subset = [r for r in records if r.month_key == mk]
        act = len(subset)
        people = sum(r.people for r in subset)
        row = {
            "month": mk, "activities": act, "people": people,
            "growth_activities": None if prev_act in (None, 0) else (act - prev_act) / prev_act,
            "growth_people": None if prev_people in (None, 0) else (people - prev_people) / prev_people,
        }
        rows.append(row)
        prev_act, prev_people = act, people
    return rows


# ============================================================================
# ۷) تولید متن گزارش (با لحن اداری‑رسمی و حدود طول)
# ============================================================================

def split_sentences(text: str) -> List[str]:
    parts = re.split(r"(?<=[.!؟:])\s+|\n+", text)
    return [p.strip() for p in parts if p.strip()]


def _words(lines: Sequence[str]) -> int:
    return sum(len(l.split()) for l in lines)


def _section_spans(lines: Sequence[str]) -> List[Tuple[int, int]]:
    """بازه‌ی سطری هر «بخش» (از سطر عنوان تا پیش از عنوان بعدی)."""
    starts = [i for i, l in enumerate(lines) if l.startswith("## ")]
    return [(s, starts[k + 1] if k + 1 < len(starts) else len(lines))
            for k, s in enumerate(starts)]


def reduce_report_items(lines: List[str], keep: int) -> List[str]:
    """کاهش تعداد اقدامات نمایش‌داده‌شده در هر بخش (با حفظ ساختار و آمار)."""
    out: List[str] = []
    kept = 0
    dropped = 0
    keep_children = True

    def flush(rest: List[str]) -> List[str]:
        if dropped:
            while rest and not rest[-1].strip():
                rest.pop()
            rest.append(f"- و {persian_number(dropped)} اقدام دیگر در همین دسته "
                        f"(فهرست کامل در فایل پیوست «اقدامات»).")
            rest.append("")
        return rest

    for l in lines:
        s = l.strip()
        if s.startswith("## "):
            out = flush(out)
            dropped, kept = 0, 0
            out.append(l)
            continue
        if "اقدام دیگر در همین دسته" in s:
            # شمارنده‌ی مرحله‌ی قبل را حفظ می‌کنیم تا آمار کاهش، گم نشود
            m = re.search(r"و\s*(\d+)\s*اقدام دیگر", fa_digits(s))
            if m:
                dropped += int(m.group(1))
            continue
        if s.startswith("• "):
            if kept < keep:
                kept += 1
                keep_children = True
                out.append(l)
            else:
                dropped += 1
                keep_children = False
            continue
        if s.startswith("- ") and not s.startswith("- و "):
            if keep_children:
                out.append(l)
            continue
        out.append(l)
    return flush(out)


def enforce_word_limit(text: str, max_words: int) -> str:
    """
    کوتاه‌سازی ساختارمند متن تا سقف واژه:
      ۱) حذف سطرهای «مستند»  ۲) کاهش اقدامات هر بخش
      ۳) حذف بخش‌های انتهایی  ۴) برش سطری — با حفظ «جمع‌بندی».
    """
    if not max_words or len(text.split()) <= max_words:
        return text

    note = "\n\n(به دلیل سقف واژه، بخشی از جزئیات حذف شده است.)"
    budget = max(60, max_words - len(note.split()))
    lines = text.split("\n")

    # ۰) حذف سطرهای اختیاری (فهرست نواحی بدون گزارش و ...) — این‌ها در فایل اکسل باقی می‌مانند
    if _words(lines) > budget:
        OPTIONAL_PREFIXES = ("- نواحی بدون گزارش", "- پنج ناحیه با کمترین اقدام")
        lines = [l for l in lines if not l.strip().startswith(OPTIONAL_PREFIXES)]

    # ۱) حذف لینک‌های مستند
    if _words(lines) > budget:
        lines = [l for l in lines if not l.strip().startswith("- مستند")]

    # ۲) کاهش تعداد اقدامات هر بخش
    for keep in (6, 5, 4, 3, 2, 1, 0):
        if _words(lines) <= budget:
            break
        lines = reduce_report_items(lines, keep=keep)

    # ۳) حذف بخش‌های انتهایی (به‌جز مقدمه، سیمای کلی و جمع‌بندی)
    protected = ("## مقدمه", "## سیمای کلی", "## جمع‌بندی")
    while _words(lines) > budget:
        spans = [s for s in _section_spans(lines) if not lines[s[0]].startswith(protected)]
        if not spans:
            break
        s, e = spans[-1]
        lines = lines[:s] + lines[e:]

    # ۴) برش سطری با حفظ جمع‌بندی
    if _words(lines) > budget:
        spans = _section_spans(lines)
        tail_start = next((s for s, _e in spans if lines[s].startswith("## جمع‌بندی")), None)
        head: List[str] = []
        used = 0
        limit = max(20, budget - _words(lines[tail_start:])) if tail_start is not None else budget
        stop = tail_start if tail_start is not None else len(lines)
        for l in lines[:stop]:
            w = len(l.split())
            if used + w > limit:
                break
            head.append(l)
            used += w
        lines = head + (lines[tail_start:] if tail_start is not None else [])

    return "\n".join(lines).rstrip() + note


def enforce_char_limit(text: str, max_chars: int) -> str:
    if not max_chars or len(text) <= max_chars:
        return text
    note = "\n\n(به دلیل سقف کاراکتر، بخشی از جزئیات حذف شده است.)"
    return text[:max(200, max_chars - len(note))].rstrip() + note


def compact_impact(rec: Record, max_words: int = 16) -> str:
    """«نتیجه و اثر» کوتاه برای گزارش‌های یک‌صفحه‌ای (نخستین عبارت کلیدی)."""
    text = impact_sentence(rec)
    first = re.split(r"[؛.]", text)[0].strip()
    words = first.split()
    if len(words) > max_words:
        first = " ".join(words[:max_words]).rstrip("،") + "…"
    return first + "." if first and not first.endswith(".") else first


def impact_sentence(rec: Record) -> str:
    """تولید «نتیجه و اثر» برای هر اقدام، بدون اغراق و بدون داده‌ی ساختگی."""
    if rec.ai_impact:
        return rec.ai_impact
    if rec.sheet == "تولیدات":
        kind = rec.production_kind or "محتوای رسانه‌ای"
        pages = f" ({persian_number(rec.pages)} صفحه)" if rec.pages else ""
        return (f"تولید و انتشار {kind}{pages} با موضوع «{rec.topic or '—'}»؛ "
                f"غنی‌سازی جریان محتوایی کانال‌های رسمی و دسترسی مخاطبان به روایت دقیق.")
    if rec.people > 0:
        if rec.sheet == "خلاقانه":
            return (f"دسترسی {persian_number(rec.people)} مخاطب/بازدید در بستر "
                    f"{rec.platform or 'فضای مجازی'}؛ افزایش ضریب نفوذ محتوای بومی و "
                    f"مشارکت مخاطبان در تولید و بازنشر پیام.")
        base = f"آموزش {persian_number(rec.people)} نفر"
        if rec.sheet in ("حضوری", "گردان"):
            base += " به‌صورت حضوری"
        elif rec.sheet == "مجازی":
            base += " در بستر " + (rec.platform or "فضای مجازی")
        return base + "؛ افزایش سطح آگاهی و توان تحلیل مخاطبان نسبت به محتوای رسانه‌ای."
    return "ارتقای آمادگی رسانه‌ای مخاطبان هدف و تقویت جریان اطلاع‌رسانی رسمی."


def build_report(dataset: Dataset, records: Sequence[Record], title: str,
                 sections: Sequence[str], categories: Dict[str, Dict[str, Any]],
                 months: Sequence[str], counties: Sequence[str],
                 max_words: int = 0, max_chars: int = 0,
                 details: bool = False) -> Dict[str, Any]:
    """ساخت متن گزارش نهایی به‌همراه جدول‌های آماری."""
    include = list(sections)
    sections = list(sections)
    leftover_exists = any(not any(c in include for c in r.categories) for r in records)
    if leftover_exists and "others" not in sections:
        sections.append("others")
    agg = aggregate(records)
    months_lbl = "، ".join(dataset.month_label(m) for m in months) if months else "کل بازه"
    period_lbl = months_lbl

    # در سقف‌های واژه‌ی کم، سطرهای اختیاری حذف و متن‌ها فشرده می‌شوند
    compact = bool(max_words) and max_words < 420

    lines: List[str] = []
    lines.append(f"# {title}")
    lines.append("")
    if compact:
        lines.append(f"**بازه‌ی گزارش:** {period_lbl}")
    else:
        lines.append(f"**بازه‌ی گزارش:** {period_lbl}   |   **تعداد نواحی/شهرستان‌های دارای داده:** "
                     f"{persian_number(agg['counties'])} از {persian_number(len(EXPECTED_COUNTIES))}")

    if dataset.source == "telegram":
        used = dataset.tg_stats.get("records", 0)
        src = (f"خروجی JSON کانال تلگرام — {persian_number(used)} پست تحلیل‌شده"
               + (f" در بازه‌ی {dataset.date_range_label}" if dataset.date_range_label else ""))
    elif dataset.source == "both":
        src = ("فایل‌های اکسل نواحی + خروجی تلگرام"
               + (f" (بازه‌ی {dataset.date_range_label})" if dataset.date_range_label else ""))
    else:
        src = "فایل‌های اکسل ماهانه‌ی نواحی"
    lines.append(f"**منبع داده:** {src}")
    if dataset.ai_note:
        lines.append(f"**تحلیل هوشمند:** {dataset.ai_note}")
    lines.append("")

    # مقدمه‌ی آماری
    intro = (f"در بازه‌ی {period_lbl}، در مجموع {persian_number(agg['total_activities'])} اقدام "
             f"غیرروتین در {persian_number(agg['counties'])} ناحیه/شهرستان ثبت شده است که "
             f"{persian_number(agg['total_people'])} نفر مخاطب را پوشش داده و "
             f"{persian_number(agg['unique_links'])} پست/مستند رسانه‌ای برای آن بارگذاری شده است. "
             f"در این اقدامات {persian_number(agg['teachers'])} مدرس و "
             f"{persian_number(agg['locations'])} مکان/بستر برگزاری نقش داشته‌اند. "
             f"برای {persian_number(agg['with_link'])} اقدام "
             f"({persian_percent(agg['with_link'] / max(1, agg['total_activities']))}) "
             f"لینک مستند رسانه‌ای ثبت شده است"
             + (f" و متن {persian_number(agg['with_content'])} پست از خروجی تلگرام "
                f"بازخوانی و در دسته‌بندی لحاظ شده است." if agg["with_content"] else "."))
    if compact:
        intro = (f"در بازه‌ی {period_lbl}، {persian_number(agg['total_activities'])} اقدام "
                 f"غیرروتین در {persian_number(agg['counties'])} ناحیه با "
                 f"{persian_number(agg['total_people'])} مخاطب و "
                 f"{persian_number(agg['unique_links'])} مستند رسانه‌ای ثبت شده است.")
    lines.append("## مقدمه‌ی آماری")
    lines.append(intro)
    lines.append("")

    # سیمای کلی: سهم هر نوع فعالیت + پوشش نواحی
    lines.append("## سیمای کلی فعالیت‌ها")
    total_act = len(records) or 1
    if compact:
        dist = "، ".join(
            f"{SHEET_LABELS[k]} {persian_number(agg['by_sheet'][k])}"
            for k in ["حضوری", "مجازی", "گردان", "خلاقانه", "تولیدات"] if agg["by_sheet"].get(k))
        lines.append(f"- توزیع فعالیت‌ها: {dist} — نواحی دارای گزارش: "
                     f"{persian_number(agg['counties'])} از {persian_number(len(EXPECTED_COUNTIES))}")
    else:
        for key in ["حضوری", "مجازی", "گردان", "خلاقانه", "تولیدات"]:
            cnt = agg["by_sheet"].get(key, 0)
            if not cnt:
                continue
            ppl = agg["by_sheet_people"].get(key, 0)
            extra = (f" — مجموع مخاطب {persian_number(ppl)} نفر" if ppl
                     else (" — بدون مخاطب مستقیم (تولید محتوا)" if key == "تولیدات" else ""))
            lines.append(f"- {SHEET_LABELS[key]}: {persian_number(cnt)} اقدام "
                         f"({persian_percent(cnt / total_act)}){extra}")
    by_county = agg["by_county"]
    counted = [(c, n) for c, n in by_county.items() if c]
    counted.sort(key=lambda x: (-x[1], x[0]))
    top5 = counted[:5]
    zero_counties = [c for c in EXPECTED_COUNTIES if c not in by_county]
    if not compact:
        lines.append("- نواحی دارای گزارش: " + persian_number(len(counted)) + " از "
                     + persian_number(len(EXPECTED_COUNTIES)))
    if top5 and not compact:
        lines.append("- پنج ناحیه‌ی فعال: " + "، ".join(
            f"{c} ({persian_number(n)})" for c, n in top5))
    least5 = sorted(counted, key=lambda x: (x[1], x[0]))[:5]
    least5 += [(c, 0) for c in zero_counties]
    least5 = least5[:5]
    if (not compact) and least5 and len(counted) > 5 and \
            [c for c, _ in least5] != [c for c, _ in top5]:
        lines.append("- پنج ناحیه با کمترین اقدام: " + "، ".join(
            f"{c} ({persian_number(n)})" for c, n in least5))
    if dataset.tg_stats and not compact:
        no_data = dataset.tg_stats.get("no_data") or []
        lines.append(f"- پست‌های خوانده‌شده از تلگرام: "
                     f"{persian_number(dataset.tg_stats.get('posts', 0))}"
                     + (f"، از این میان {persian_number(dataset.tg_stats.get('skipped_existing', 0))} "
                        f"پست در اکسل هم موجود بود" if dataset.tg_stats.get('skipped_existing') else "")
                     + (f"، {persian_number(len(no_data))} پست داده‌ی ساختاریافته نداشت"
                        f" (فهرست در پیوست)" if no_data else ""))
        if dataset.tg_stats.get("ai_used"):
            lines.append(f"- رکوردهای استخراج‌شده با کمک مدل هوش مصنوعی: "
                         f"{persian_number(dataset.tg_stats['ai_used'])}")
    if zero_counties and not compact:
        shown = zero_counties[:8]
        more = len(zero_counties) - len(shown)
        lines.append("- نواحی بدون گزارش در این بازه (" + persian_number(len(zero_counties))
                     + " ناحیه): " + "، ".join(shown)
                     + (f" و {persian_number(more)} ناحیه دیگر." if more > 0 else "."))
    lines.append("")

    # ---- آماده‌سازی داده‌ی بخش‌ها -------------------------------------------
    prepared: List[Tuple[str, str, List[Record]]] = []
    for sec in sections:
        if sec == "others":
            sec_title = OTHERS_TITLE
            sec_recs = [r for r in records if not any(c in include for c in r.categories)]
        else:
            sec_title = categories.get(sec, {"title": sec})["title"]
            sec_recs = [r for r in records if sec in r.categories]
        prepared.append((sec, sec_title,
                         sorted(sec_recs, key=lambda r: (r.people, r.minutes), reverse=True)))

    def section_stats(sec_recs: List[Record]) -> str:
        people = sum(r.people for r in sec_recs)
        counties_in = len({r.county for r in sec_recs})
        if compact:
            extra = f"، {persian_number(people)} مخاطب" if people else ""
            return f"{persian_number(len(sec_recs))} اقدام{extra}، {persian_number(counties_in)} ناحیه"
        if people:
            return (f"**خلاصه‌ی آماری:** {persian_number(len(sec_recs))} اقدام در "
                    f"{persian_number(counties_in)} ناحیه؛ مجموع مخاطبان "
                    f"{persian_number(people)} نفر؛ میانگین "
                    f"{persian_number(people / len(sec_recs))} نفر به‌ازای هر اقدام.")
        return (f"**خلاصه:** {persian_number(len(sec_recs))} اقدام تولیدی/رسانه‌ای در "
                f"{persian_number(counties_in)} ناحیه (بدون مخاطب مستقیم).")

    def render_sections(alloc: Dict[str, int]) -> List[str]:
        out: List[str] = []
        shown_ids: set = set()      # هر اقدام فقط یک‌بار در گزارش (در نخستین محور خود) می‌آید
        for sec, sec_title, sec_recs in prepared:
            if compact and sec == "others" and sec_recs:
                out.append(f"- {persian_number(len(sec_recs))} اقدام دیگر در سایر محورها "
                           f"(فهرست کامل در فایل پیوست «اقدامات»).")
                out.append("")
                continue
            if not sec_recs:
                if sec == "others":
                    continue
                out.append(f"## بخش: {sec_title}")
                out.append("در این محور، داده‌ی قابل استنادی در بازه‌ی مورد نظر ثبت نشده است.")
                out.append("")
                continue
            # در حالت فشرده، آماره‌ی بخش در خود عنوان می‌آید تا یک سطر صرفه‌جویی شود
            if compact:
                out.append(f"## بخش: {sec_title} ({section_stats(sec_recs)})")
            else:
                out.append(f"## بخش: {sec_title}")
                out.append(section_stats(sec_recs))
            out.append("")
            want = max(0, min(alloc.get(sec, 0), len(sec_recs)))
            picked = []
            for r in sec_recs:
                if len(picked) >= want:
                    break
                key = getattr(r, "uid", None) or id(r)
                if key in shown_ids:
                    continue
                picked.append(r)
            for r in picked:
                shown_ids.add(getattr(r, "uid", None) or id(r))
                where = r.place or r.platform or "—"
                if compact:
                    out.append(f"• **{county_label(r)}** — {r.topic or SHEET_LABELS.get(r.sheet, r.sheet)}"
                               f" ({fa_date(r.date)}، {where}) — نتیجه و اثر: {compact_impact(r)}")
                else:
                    out.append(f"• **{county_label(r)}** — {r.topic or SHEET_LABELS.get(r.sheet, r.sheet)}"
                               f" (تاریخ {fa_date(r.date)}، {where})")
                    out.append(f"  - نتیجه و اثر: {impact_sentence(r)}")
                    if details and r.link:
                        out.append(f"  - مستند: {r.link}")
            rest = len(sec_recs) - len(picked)
            if rest > 0 and not compact:      # در حالت فشرده، آماره‌ی محور در خود عنوان آمده است
                if not picked:
                    out.append(f"- {persian_number(rest)} اقدام این محور در فایل پیوست "
                               f"«اقدامات» فهرست شده است.")
                else:
                    out.append(f"- و {persian_number(rest)} اقدام دیگر در همین محور "
                               f"(فهرست کامل در فایل پیوست «اقدامات»).")
            out.append("")
        return out

    def build_tail() -> List[str]:
        """بخش‌های پایانی (درصد تحقق و جمع‌بندی) — پیش از تخصیص بودجه ساخته می‌شود."""
        tail: List[str] = []
        compliance = compute_compliance(dataset, records, months, counties)
        if compliance and not compact:
            tail.append("## درصد تحقق نسبت به حد انتظار")
            for row in compliance[:8]:
                tail.append(f"- {row['county']}: حضوری {persian_percent(row['p_hazeri'])}"
                            f"، مجازی {persian_percent(row['p_majazi'])}"
                            f"، خلاقانه {persian_percent(row['p_khalaghane'])}"
                            f"، تولیدات {persian_percent(row['p_toliat'])}"
                            f" (میانگین {persian_percent(row['p_total'])})")
            if len(compliance) > 8:
                tail.append(f"- و {persian_number(len(compliance) - 8)} ناحیه‌ی دیگر "
                            f"(جدول کامل در شیت «تحقق انتظار» فایل اکسل).")
            tail.append("")

        tail.append("## جمع‌بندی")
        top = agg["by_county"].most_common(5)
        peak = agg["by_sheet"].most_common(1)
        peak_txt = (f"{SHEET_LABELS.get(peak[0][0], peak[0][0])} با "
                    f"{persian_number(peak[0][1])} اقدام") if peak else "—"
        if compact:
            top_txt = "، ".join(f"{c} ({persian_number(n)})" for c, n in top[:3]) or "—"
            tail.append(f"بیشترین اقدامات در {top_txt} ثبت شده و پرتکرارترین قالب، {peak_txt} است؛ "
                        f"مجموع مخاطبان {persian_number(agg['total_people'])} نفر "
                        f"(میانگین {persian_number(agg['total_people'] / agg['total_activities'] if agg['total_activities'] else 0)} نفر).")
        else:
            top_txt = "، ".join(f"{c} ({persian_number(n)} اقدام)" for c, n in top) if top else "—"
            tail.append(f"بیشترین حجم اقدامات به‌ترتیب در نواحی {top_txt} ثبت شده و پرتکرارترین قالب، "
                        f"{peak_txt} است. مجموع مخاطبان مستقیم این بازه "
                        f"{persian_number(agg['total_people'])} نفر و میانگین مخاطب هر اقدام "
                        f"{persian_number(agg['total_people'] / agg['total_activities'] if agg['total_activities'] else 0)} نفر است.")
            if len(months) > 1:
                mom = month_over_month(records, months)
                growths = [m for m in mom if m["growth_activities"] is not None]
                if growths:
                    g = growths[-1]["growth_activities"]
                    direction = "رشد" if g >= 0 else "کاهش"
                    tail.append(f"روند ماه‌به‌ماه تعداد اقدامات در آخرین دوره نسبت به دوره‌ی قبل، "
                                f"{direction} {persian_percent(abs(g))} را نشان می‌دهد.")
        tail.append("")
        return tail

    tail_lines = build_tail()

    # ---- تخصیص تعداد اقدامات هر محور با توجه به سقف واژه ----------------------
    alloc: Dict[str, int] = {}
    if max_words:
        per_item = 22 if compact else 42          # برآورد واژه برای هر اقدام
        base_words = (_words(lines) + _words(render_sections({})) + _words(tail_lines))
        capacity = max(0, (max_words - base_words)) // per_item
    else:
        capacity = MAX_ITEMS_PER_SECTION * max(1, len(prepared))

    # در گزارش یک‌صفحه‌ای، «سایر اقدامات» فقط یک سطر خلاصه می‌گیرد و بودجه مصرف نمی‌کند
    alloc_pool = [p for p in prepared if not (compact and p[0] == "others")]

    # گام ۱: هر محورِ دارای داده، دست‌کم یک اقدام مشخص داشته باشد
    for sec, _sec_title, sec_recs in alloc_pool:
        if capacity <= 0:
            break
        if sec_recs:
            alloc[sec] = 1
            capacity -= 1
    # گام ۲: توزیع چرخشی باقی‌مانده‌ی بودجه
    i = 0
    while capacity > 0 and alloc_pool:
        sec, _sec_title, sec_recs = alloc_pool[i % len(alloc_pool)]
        cap = min(MAX_ITEMS_PER_SECTION, len(sec_recs))
        if alloc.get(sec, 0) < cap:
            alloc[sec] = alloc.get(sec, 0) + 1
            capacity -= 1
        elif all(alloc.get(s_, 0) >= min(MAX_ITEMS_PER_SECTION, len(rs))
                 for s_, _t, rs in alloc_pool):
            break
        i += 1

    lines.extend(render_sections(alloc))
    lines.extend(tail_lines)

    body = "\n".join(lines)
    body = enforce_word_limit(body, max_words)
    body = enforce_char_limit(body, max_chars)

    word_count = len(body.split())
    return {"markdown": body, "agg": agg, "word_count": word_count}


# ============================================================================
# ۸) جدول‌های آماری (برای اکسل و نمایش)
# ============================================================================

def compute_compliance(dataset: Dataset, records: Sequence[Record],
                       months: Sequence[str], counties: Sequence[str]) -> List[Dict[str, Any]]:
    """درصد تحقق عملکرد هر ناحیه نسبت به «حد انتظار» (اگر فایل کارنامه موجود باشد)."""
    if not dataset.expectations:
        return []
    rows: List[Dict[str, Any]] = []
    for county, exp in dataset.expectations.items():
        if counties and county not in counties:
            continue
        rel = [r for r in records if r.county == county]
        act = {
            "hazeri": sum(1 for r in rel if r.sheet in ("حضوری", "گردان")),
            "majazi": sum(1 for r in rel if r.sheet == "مجازی"),
            "khalaghane": sum(1 for r in rel if r.sheet == "خلاقانه"),
            "toliat": sum(1 for r in rel if r.sheet == "تولیدات"),
        }
        def pct(key: str, exp_key: str = None) -> float:
            e = float(exp.get(exp_key or key, 0) or 0)
            return (act[key] / e) if e else 0.0
        ps = [pct("hazeri"), pct("majazi"), pct("khalaghane"), pct("toliat")]
        rows.append({
            "county": county,
            "hazeri": act["hazeri"], "majazi": act["majazi"],
            "khalaghane": act["khalaghane"], "toliat": act["toliat"],
            "exp_hazeri": exp.get("hazeri", 0), "exp_majazi": exp.get("majazi", 0),
            "exp_khalaghane": exp.get("khalaghane", 0), "exp_toliat": exp.get("toliat", 0),
            "p_hazeri": ps[0], "p_majazi": ps[1], "p_khalaghane": ps[2], "p_toliat": ps[3],
            "p_total": sum(ps) / 4,
        })
    rows.sort(key=lambda r: r["p_total"], reverse=True)
    return rows


def table_compliance(dataset: Dataset, records: Sequence[Record], months: Sequence[str],
                     counties: Sequence[str]) -> Tuple[List[str], List[List[Any]]]:
    headers = ["نام ناحیه", "حضوری (حد انتظار)", "حضوری (عملکرد)", "٪ حضوری",
               "مجازی (حد انتظار)", "مجازی (عملکرد)", "٪ مجازی",
               "خلاقانه (حد انتظار)", "خلاقانه (عملکرد)", "٪ خلاقانه",
               "تولیدات (حد انتظار)", "تولیدات (عملکرد)", "٪ تولیدات", "میانگین تحقق"]
    rows = []
    for r in compute_compliance(dataset, records, months, counties):
        rows.append([r["county"], r["exp_hazeri"], r["hazeri"], r["p_hazeri"],
                     r["exp_majazi"], r["majazi"], r["p_majazi"],
                     r["exp_khalaghane"], r["khalaghane"], r["p_khalaghane"],
                     r["exp_toliat"], r["toliat"], r["p_toliat"], r["p_total"]])
    return headers, rows


def table_county(categories: Dict[str, Dict[str, Any]], include: Sequence[str],
                 exclude: Sequence[str], records: Sequence[Record]) -> Tuple[List[str], List[List[Any]]]:
    headers = ["نام ناحیه", "تعداد اقدام", "مجموع مخاطب", "میانگین مخاطب",
               "حضوری", "مجازی", "گردان", "خلاقانه", "تولیدات", "پست مستند"]
    counties = sorted({county_label(r) for r in records})
    rows = []
    for c in counties:
        rs = [r for r in records if county_label(r) == c]
        people = sum(r.people for r in rs)
        rows.append([
            c, len(rs), people, (people / len(rs)) if rs else 0,
            sum(1 for r in rs if r.sheet == "حضوری"),
            sum(1 for r in rs if r.sheet == "مجازی"),
            sum(1 for r in rs if r.sheet == "گردان"),
            sum(1 for r in rs if r.sheet == "خلاقانه"),
            sum(1 for r in rs if r.sheet == "تولیدات"),
            sum(1 for r in rs if r.link),
        ])
    rows.sort(key=lambda x: x[1], reverse=True)
    return headers, rows


def table_sheets(records: Sequence[Record]) -> Tuple[List[str], List[List[Any]]]:
    headers = ["نوع فعالیت", "تعداد", "سهم از کل", "مجموع مخاطب"]
    total = len(records) or 1
    rows = []
    for key in ["حضوری", "مجازی", "گردان", "خلاقانه", "تولیدات"]:
        rs = [r for r in records if r.sheet == key]
        if not rs:
            continue
        rows.append([SHEET_LABELS[key], len(rs), len(rs) / total, sum(r.people for r in rs)])
    return headers, rows


def table_months(dataset: Dataset, records: Sequence[Record],
                 months: Sequence[str]) -> Tuple[List[str], List[List[Any]]]:
    headers = ["دوره", "تعداد اقدام", "مجموع مخاطب", "رشد اقدامات", "رشد مخاطب"]
    rows = []
    for m in month_over_month(records, months):
        rows.append([
            dataset.month_label(m["month"]), m["activities"], m["people"],
            m["growth_activities"], m["growth_people"],
        ])
    return headers, rows


def table_gaps(dataset: Dataset, counties: Sequence[str]) -> Tuple[List[str], List[List[Any]]]:
    headers = ["وضعیت", "ناحیه/شهرستان"]
    have = {r.county for r in dataset.records if r.county}
    missing = [c for c in EXPECTED_COUNTIES if c not in have]
    rows = []
    if missing:
        rows.append(["گزارش نداده", "، ".join(missing)])
    else:
        rows.append(["گزارش نداده", "—"])
    return headers, rows


# ============================================================================
# ۹) خروجی‌ها: Markdown / HTML / Word / Excel
# ============================================================================

FONT_FILE_CANDIDATES = ["IRANSans.ttf", "IRANSansX-Regular.ttf", "Vazirmatn-Regular.ttf"]


def find_persian_font(roots: Sequence[str]) -> Optional[str]:
    for root in roots:
        if not root:
            continue
        for name in FONT_FILE_CANDIDATES:
            p = os.path.join(root, name)
            if os.path.exists(p):
                return p
        for dirpath, _dirs, files in os.walk(root):
            for f in files:
                if f.lower().endswith((".ttf", ".otf")) and any(
                        k in f.lower() for k in ("iran", "vazir", "sahel", "shabnam", "nazanin", "titr")):
                    return os.path.join(dirpath, f)
    return None


def markdown_to_html(md: str, title: str) -> str:
    """تبدیل ساده و امن مارک‌داون گزارش به HTML راست‌به‌چپ."""
    out: List[str] = []
    for raw in md.split("\n"):
        line = _html.escape(raw)
        line = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", line)
        line = re.sub(r"`(.+?)`", r"<code>\1</code>", line)
        if line.startswith("# "):
            out.append(f"<h1>{line[2:]}</h1>")
        elif line.startswith("## "):
            out.append(f"<h2>{line[3:]}</h2>")
        elif line.startswith("- "):
            out.append(f"<li>{line[2:]}</li>")
        elif line.startswith("  - "):
            out.append(f"<li class='sub'>{line[4:]}</li>")
        elif line.startswith("• "):
            out.append(f"<li>{line[2:]}</li>")
        elif not line.strip():
            out.append("")
        else:
            out.append(f"<p>{line}</p>")
    html_body = "\n".join(out)
    return f"""<!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<title>{_html.escape(title)}</title>
<style>
  body {{ font-family: Tahoma, "IRANSans", "Vazirmatn", sans-serif; background:#f6f7fb;
         color:#12203a; margin:0; padding:32px; line-height:2; }}
  .sheet {{ max-width: 980px; margin:0 auto; background:#fff; padding:40px 48px;
            border-radius:14px; box-shadow:0 8px 30px rgba(16,32,64,.08); }}
  h1 {{ font-size:24px; border-bottom:3px solid #1b2a4a; padding-bottom:12px; }}
  h2 {{ font-size:19px; margin-top:28px; color:#1b2a4a; border-right:5px solid #c8a24a;
        padding-right:10px; }}
  p, li {{ font-size:15px; text-align:justify; }}
  li {{ margin:4px 0; }}
  li.sub {{ color:#3d4a63; font-size:14px; }}
  strong {{ color:#0f1b33; }}
</style>
</head>
<body><div class="sheet">
{html_body}
</div></body></html>"""


def write_markdown(path: str, md: str) -> None:
    with open(path, "w", encoding="utf-8") as f:
        f.write(md)


def write_html(path: str, md: str, title: str) -> None:
    with open(path, "w", encoding="utf-8") as f:
        f.write(markdown_to_html(md, title))


def _docx_set_rtl_paragraph(p) -> None:
    """راست‌به‌چپ کردن یک پاراگراف Word."""
    try:
        from docx.oxml.ns import qn
        pPr = p._p.get_or_add_pPr()
        bidi = pPr.makeelement(qn("w:bidi"), {})
        pPr.append(bidi)
    except Exception:
        pass


def _docx_add_page_number_footer(doc, text_right: str) -> None:
    """درج پاصفحه با شماره‌ی صفحه (فیلد PAGE)."""
    try:
        from docx.oxml import OxmlElement
        from docx.oxml.ns import qn
        for section in doc.sections:
            footer = section.footer
            para = footer.paragraphs[0] if footer.paragraphs else footer.add_paragraph()
            para.text = ""
            run = para.add_run(text_right + "    |    صفحه ")
            fld = OxmlElement("w:fldSimple")
            fld.set(qn("w:instr"), "PAGE")
            run._r.addnext(fld)
            for r in para.runs:
                r.font.size = _docx_pt(9)
    except Exception:
        pass


def _docx_pt(value: int):
    from docx.shared import Pt
    return Pt(value)


def write_docx(path: str, md: str, title: str,
               tables: List[Tuple[str, List[str], List[List[Any]]]],
               font_path: Optional[str] = None, logo_path: Optional[str] = None) -> bool:
    """تولید فایل Word (docx) راست‌به‌چپ با عنوان، متن گزارش و پیوست جدول‌ها."""
    try:
        from docx import Document
        from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
        from docx.oxml.ns import qn
        from docx.shared import Cm, Pt, RGBColor
    except ImportError:
        return False

    FONT = "IRANSans"
    DARK = RGBColor(0x1B, 0x2A, 0x4A)
    GOLD = RGBColor(0xC8, 0xA2, 0x4A)

    doc = Document()

    # --- تنظیم کلی: راست‌به‌چپ، فونت فارسی، اندازه‌ی صفحه
    style = doc.styles["Normal"]
    style.font.name = FONT
    style.font.size = Pt(12)
    style.paragraph_format.line_spacing = 1.15
    try:
        style.element.rPr.rFonts.set(qn("w:cs"), FONT)
        style.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
    except Exception:
        pass
    for section in doc.sections:
        try:
            section._sectPr.xpath("./w:bidi")[0].set(qn("w:val"), "1")
        except Exception:
            pass
        section.top_margin = Cm(2.2)
        section.bottom_margin = Cm(2.0)
        section.left_margin = Cm(2.2)
        section.right_margin = Cm(2.2)

    def make_para(text: str, size: int = 12, bold: bool = False, center: bool = False,
                  color: Optional[Any] = None, space_after: int = 6, bullet: bool = False):
        paragraph = doc.add_paragraph(style="List Bullet" if bullet else None)
        run = paragraph.add_run(text)
        run.font.name = FONT
        run.font.size = Pt(size)
        run.font.bold = bold
        if color is not None:
            run.font.color.rgb = color
        try:
            run._element.rPr.rFonts.set(qn("w:cs"), FONT)
            run._element.rPr.rFonts.set(qn("w:rtl"), "1")
        except Exception:
            pass
        paragraph.alignment = (WD_ALIGN_PARAGRAPH.CENTER if center else WD_ALIGN_PARAGRAPH.RIGHT)
        paragraph.paragraph_format.space_after = Pt(space_after)
        _docx_set_rtl_paragraph(paragraph)
        return paragraph

    # --- سرصفحه: لوگو (اختیاری) و عنوان
    if logo_path and os.path.exists(logo_path):
        try:
            doc.add_picture(logo_path, width=Cm(4))
            doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
        except Exception:
            pass

    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    make_para(title, size=17, bold=True, center=True, color=DARK, space_after=2)
    make_para("گزارش رسمی — تهیه‌شده با گزارش‌یار هوشمند سازمانی", size=10,
              center=True, color=GOLD, space_after=14)

    # --- بدنه‌ی گزارش از متن Markdown
    for raw in md.split("\n"):
        line = raw.rstrip()
        if not line.strip():
            continue
        if line.startswith("# "):
            continue  # عنوان در بالای سند درج شد
        text = re.sub(r"\*\*(.+?)\*\*", r"\1", line)
        if line.startswith("## "):
            make_para(text[3:], size=14, bold=True, color=DARK, space_after=6)
        elif line.startswith(("• ", "- ")) or line.startswith("  - "):
            indent = line.startswith("  - ")
            body = re.sub(r"^(\s*[-•]\s*)", "", text)
            paragraph = make_para(body, size=11 if indent else 12, bullet=True,
                                  space_after=3)
            if indent:
                paragraph.paragraph_format.left_indent = Pt(22)
        else:
            make_para(text, size=12, space_after=6)

    # --- پیوست: جدول‌های آماری
    if tables:
        doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)
        make_para("پیوست: جدول‌های آماری", size=15, bold=True, color=DARK, space_after=10)
        for caption, headers, rows in tables:
            if not rows:
                continue
            make_para(caption, size=13, bold=True, color=DARK, space_after=4)
            table = doc.add_table(rows=1, cols=len(headers))
            try:
                table.style = "Table Grid"
            except Exception:
                pass
            header_cells = table.rows[0].cells
            for i, h in enumerate(headers):
                header_cells[i].text = str(h)
                for para in header_cells[i].paragraphs:
                    for run in para.runs:
                        run.font.bold = True
                        run.font.size = Pt(10)
                        run.font.name = FONT
            for row in rows[:80]:
                cells = table.add_row().cells
                for i, v in enumerate(row):
                    txt = (persian_number(v) if isinstance(v, (int, float))
                           and not isinstance(v, bool) else str(v))
                    if isinstance(v, float) and 0 <= v <= 3 and "٪" in str(headers[i]):
                        txt = persian_percent(v)
                    cells[i].text = txt
                    for para in cells[i].paragraphs:
                        for run in para.runs:
                            run.font.size = Pt(10)
                            run.font.name = FONT
            try:
                tblPr = table._tbl.tblPr
                bidi = tblPr.makeelement(qn("w:bidiVisual"), {})
                tblPr.append(bidi)
            except Exception:
                pass
            doc.add_paragraph().paragraph_format.space_after = Pt(8)

    _docx_add_page_number_footer(doc, title)
    doc.save(path)
    return True


def write_xlsx(path: str, dataset: Dataset, records: Sequence[Record],
               report_md: str, categories: Dict[str, Dict[str, Any]],
               include: Sequence[str], exclude: Sequence[str],
               months: Sequence[str], logo_path: Optional[str] = None) -> None:
    """خروجی اکسل: گزارش + جدول‌های آماری + فهرست اقدامات + مغایرت‌ها."""
    wb = openpyxl.Workbook()
    thin = Side(style="thin", color="BFC9DA")
    border = Border(left=thin, right=thin, top=thin, bottom=thin)

    def style_header(ws, row=1):
        fill = PatternFill("solid", fgColor="1B2A4A")
        for c in range(1, ws.max_column + 1):
            cell = ws.cell(row, c)
            cell.font = Font(bold=True, color="FFFFFF", name="IRANSans", size=12)
            cell.fill = fill
            cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
            cell.border = border

    def autosize(ws, widths=None):
        for i, col in enumerate(ws.columns, start=1):
            letter = get_column_letter(i)
            if widths and i <= len(widths):
                ws.column_dimensions[letter].width = widths[i - 1]
                continue
            mx = 10
            for cell in col[:80]:
                if cell.value is not None:
                    mx = max(mx, min(48, len(str(cell.value)) + 2))
            ws.column_dimensions[letter].width = mx

    # شیت ۱: متن گزارش
    ws = wb.active
    ws.title = "گزارش"
    for i, line in enumerate(report_md.split("\n"), start=1):
        ws.cell(i, 1).value = re.sub(r"\*\*(.+?)\*\*", r"\1", line)
    ws.column_dimensions["A"].width = 130
    for row in ws.iter_rows(min_row=1, max_row=ws.max_row, max_col=1):
        for cell in row:
            cell.alignment = Alignment(horizontal="right", vertical="center", wrap_text=True)
            cell.font = Font(name="IRANSans", size=12)

    # شیت ۲: خلاصه نواحی
    headers, rows = table_county(categories, include, exclude, records)
    ws2 = wb.create_sheet("خلاصه نواحی")
    ws2.append(headers)
    for r in rows:
        ws2.append(r)
    style_header(ws2)
    autosize(ws2, [22, 12, 14, 14, 10, 10, 10, 10, 10, 12])

    # شیت ۳: سهم انواع فعالیت
    headers, rows = table_sheets(records)
    ws3 = wb.create_sheet("انواع فعالیت")
    ws3.append(headers)
    for r in rows:
        ws3.append(r)
    style_header(ws3)
    autosize(ws3, [26, 12, 14, 14])

    # شیت ۴: مقایسه دوره‌ها
    headers, rows = table_months(dataset, records, months)
    ws4 = wb.create_sheet("مقایسه دوره‌ها")
    ws4.append(headers)
    for r in rows:
        ws4.append(r)
    style_header(ws4)
    autosize(ws4, [20, 14, 14, 14, 14])

    # شیت ۵: فهرست اقدامات (داده‌ی پاک‌سازی‌شده، بدون روتین)
    ws5 = wb.create_sheet("اقدامات")
    headers = ["ناحیه", "دوره", "نوع فعالیت", "تاریخ", "موضوع", "مدرس/تولیدکننده",
               "مخاطب", "مدت/صفحات", "مکان/بستر", "دسته‌ها", "لینک مستند",
               "منبع", "شماره پست", "کانال", "اطمینان مدل", "فایل مبدأ", "سطر"]
    ws5.append(headers)
    for r in sorted(records, key=lambda x: (x.county, x.sheet)):
        ws5.append([
            county_label(r), dataset.month_label(r.month_key), SHEET_LABELS.get(r.sheet, r.sheet),
            f"{r.date[0]}/{r.date[1]:02d}/{r.date[2]:02d}" if r.date else "",  # در اکسل: عدد لاتین
            r.topic, r.teacher or r.producer, r.people, r.minutes or r.pages,
            r.place or r.platform,
            "، ".join(categories[c]["title"] for c in r.categories if c in categories),
            r.link, ("تلگرام" if r.origin == "telegram" else "اکسل"),
            (r.post_id or ""), r.channel, (r.ai_confidence or ""),
            r.source_file, r.source_row,
        ])
    style_header(ws5)
    autosize(ws5, [18, 16, 20, 12, 40, 20, 10, 12, 22, 30, 34, 10, 10, 20, 10, 26, 6])

    # شیت: درصد تحقق نسبت به حد انتظار
    comp_headers, comp_rows = table_compliance(dataset, records, months, [])
    if comp_rows:
        wsc = wb.create_sheet("تحقق انتظار")
        wsc.append(comp_headers)
        for r in comp_rows:
            wsc.append(r)
        style_header(wsc)
        for row in wsc.iter_rows(min_row=2, max_row=wsc.max_row):
            for cell in row:
                if isinstance(cell.value, float) and cell.column in (4, 7, 10, 13, 14):
                    cell.number_format = "0.0%"
        autosize(wsc, [20] + [16] * 13)

    # شیت: مغایرت‌ها و کنترل کیفیت
    # شیت: پست‌های تلگرام بدون داده‌ی ساختاریافته
    no_data = (dataset.tg_stats or {}).get("no_data") or []
    if no_data:
        ws7 = wb.create_sheet("پست‌های بدون داده")
        ws7.append(["شماره پست", "تاریخ پست", "متن پست (خلاصه)"])
        for item in no_data:
            ws7.append([item.get("id"), item.get("date"), item.get("text")])
        style_header(ws7)
        autosize(ws7, [14, 20, 90])

    ws6 = wb.create_sheet("کنترل کیفیت")
    ws6.append(["موضوع", "شرح"])
    ws6.append(["تعداد فایل‌های خوانده‌شده", len(dataset.files)])
    ws6.append(["تعداد سطرهای خام خوانده‌شده", len(dataset.records)])
    ws6.append(["دوره‌های موجود", "، ".join(dataset.month_label(m) for m in dataset.months)])
    ws6.append(["نواحی دارای داده", "، ".join(dataset.counties)])
    ws6.append(["جدول حد انتظار (کارنامه)",
                "خوانده شد" if dataset.expectations else "یافت نشد (فایل کارنامه در ورودی نبود)"])
    have_by_period: Dict[str, set] = defaultdict(set)
    for r in dataset.records:
        have_by_period[r.month_key].add(r.county)
    for mk in dataset.months:
        missing = [c for c in EXPECTED_COUNTIES if c not in have_by_period[mk]]
        ws6.append([f"نواحی بدون گزارش — {dataset.month_label(mk)}",
                    "، ".join(missing) if missing else "—"])
    for fr in dataset.files:
        if fr.error:
            ws6.append([f"خطا در فایل {os.path.basename(fr.path)}", fr.error])
        if fr.missing_sheets:
            ws6.append([f"شیت‌های ناموجود در {os.path.basename(fr.path)}",
                        "، ".join(fr.missing_sheets)])
        if fr.empty_sheets:
            ws6.append([f"شیت‌های خالی در {os.path.basename(fr.path)}",
                        "، ".join(fr.empty_sheets)])
    for w in dataset.warnings:
        ws6.append(["هشدار", w])
    style_header(ws6)
    autosize(ws6, [40, 90])

    wb.save(path)


# ============================================================================
# ۱۰) کنترل کیفیت
# ============================================================================

def quality_check(md: str, max_words: int, max_chars: int, records: Sequence[Record],
                  exclude: Sequence[str]) -> List[str]:
    issues: List[str] = []
    wc = len(md.split())
    if max_words and wc > max_words:
        issues.append(f"تعداد واژه‌ها ({wc}) از سقف تعیین‌شده ({max_words}) بیشتر است.")
    if max_chars and len(md) > max_chars:
        issues.append(f"تعداد کاراکترها ({len(md)}) از سقف تعیین‌شده ({max_chars}) بیشتر است.")
    for r in records:
        if any(x in exclude for x in r.categories):
            issues.append(f"یک اقدام روتین در گزارش مانده است: {r.topic or r.sheet} — {r.county}")
            break
    if "نتیجه و اثر" not in md:
        issues.append("برای اقدامات، «نتیجه و اثر» درج نشده است.")
    return issues


# ============================================================================
# ۱۱) منطق «درخواست مدیر»
# ============================================================================

REPORT_TITLES = {
    1: "گزارش تحلیلی اقدامات رسانه‌ای — سیمای کلی",
    2: "گزارش تحلیلی اقدامات رسانه‌ای — عملکرد نواحی",
    3: "گزارش تحلیلی اقدامات رسانه‌ای — تولیدات و پویش‌ها",
    4: "گزارش تحلیلی اقدامات رسانه‌ای — اقدامات ویژه و هم‌افزا",
    5: "گزارش تحلیلی اقدامات رسانه‌ای — روند دوره‌ای",
    6: "گزارش تحلیلی اقدامات رسانه‌ای — درصد تحقق و شکاف‌ها",
}

TEMPLATES = {
    1: "سیمای کلی",
    2: "عملکرد نواحی",
    3: "تولیدات و پویش‌ها",
    4: "اقدامات ویژه و هم‌افزا",
    5: "روند دوره‌ای",
    6: "درصد تحقق و شکاف‌ها",
}


def parse_request_text(text: str) -> Dict[str, Any]:
    """استخراج خودکار محدودیت‌ها از متن آزاد درخواست مدیر (راهنمای کاربر)."""
    out: Dict[str, Any] = {}
    if not text:
        return out
    t = normalize_text(text)
    m = re.search(r"(\d+)\s*(?:کلمه|واژه)", t)
    if m:
        out["max_words"] = int(m.group(1))
    m = re.search(r"(\d+)\s*(?:صفحه|برگ)", t)
    if m:
        # هر صفحه ≈ ۳۵۰ واژه (سقف محافظه‌کارانه)
        out["max_words"] = out.get("max_words", int(m.group(1)) * 350)
    m = re.search(r"(\d+)\s*(?:کاراکتر|نویسه)", t)
    if m:
        out["max_chars"] = int(m.group(1))
    m = re.search(r"(مرداد|شهریور|مهر|آبان|آذر|دی|بهمن|اسفند|فروردین|اردیبهشت|خرداد)", t)
    if m:
        out["month_name"] = m.group(1)
    return out


# ============================================================================
# ۱۲) رابط تعاملی «پرسش‌های ساده»
# ============================================================================

DATA_DIR_CANDIDATES = ["داده‌ها", "داده", "data", "input", "ورودی", "اکسل‌ها",
                       "گزارش‌ها", "demo-input"]


def guess_data_dir(*roots: str) -> str:
    """حدس پوشه‌ی داده بر اساس نام‌های رایج کنار برنامه."""
    for root in roots:
        if not root or not os.path.isdir(root):
            continue
        for name in DATA_DIR_CANDIDATES:
            for base in (root, os.path.join(root, "gozaresh-yar")):
                cand = os.path.join(base, name)
                if os.path.isdir(cand) and any(
                        f.lower().endswith((".xlsx", ".xlsm")) and not f.startswith("~$")
                        for f in os.listdir(cand)):
                    return cand
        for d in os.listdir(root):
            sub = os.path.join(root, d)
            if os.path.isdir(sub) and not d.startswith(".") and d != "gozaresh-yar":
                if any(f.lower().endswith((".xlsx", ".xlsm")) and not f.startswith("~$")
                       for f in os.listdir(sub)):
                    return root
    return ""


def _is_tty() -> bool:
    return sys.stdin.isatty() and sys.stdout.isatty()


class UI:
    """رابط ساده و راهبردی با کلیدهای جهت‌دار؛ در نبود ترمینال، حالت متنی."""

    def __init__(self):
        self.tty = _is_tty()

    def say(self, text: str = "") -> None:
        print(text)

    def head(self, text: str) -> None:
        print("\n" + "═" * 62)
        print("  " + text)
        print("═" * 62)

    def info(self, text: str) -> None:
        print("  ℹ️  " + text)

    def warn(self, text: str) -> None:
        print("  ⚠️  " + text)

    def ok(self, text: str) -> None:
        print("  ✅ " + text)

    def ask_text(self, question: str, default: str = "") -> str:
        prompt = f"❓ {question}"
        if default:
            prompt += f"  [{default}]"
        prompt += ": "
        try:
            ans = input(prompt).strip()
        except EOFError:
            ans = ""
        return ans or default

    def ask_yes_no(self, question: str, default: bool = True) -> bool:
        d = "بله/خیر" if default else "خیر/بله"
        ans = self.ask_text(f"{question} ({'بله' if default else 'خیر'} پیش‌فرض)", "")
        if not ans:
            return default
        return ans.strip() in ("بله", "ب", "y", "Y", "yes", "1", "آره", "اره")

    def select(self, title: str, options: List[Tuple[str, str]], default_index: int = 0) -> str:
        """انتخاب تک‌گزینه‌ای. خروجی: شناسه‌ی گزینه."""
        if not self.tty:
            print(f"❓ {title}")
            for i, (oid, label) in enumerate(options, start=1):
                print(f"    {i}) {label}")
            ans = self.ask_text("  شماره‌ی گزینه", str(default_index + 1))
            try:
                idx = int(fa_digits(ans)) - 1
            except ValueError:
                idx = default_index
            idx = max(0, min(idx, len(options) - 1))
            return options[idx][0]

        idx = default_index
        while True:
            # رسم منو
            print(f"\n  {title}")
            for i, (_oid, label) in enumerate(options):
                mark = "❯" if i == idx else " "
                line = f"   {mark} {'█' if i == idx else ' '} {label}"
                print("\033[7m" + line + "\033[0m" if i == idx else line)
            key = _read_key()
            if key == "UP":
                idx = (idx - 1) % len(options)
            elif key == "DOWN":
                idx = (idx + 1) % len(options)
            elif key in ("ENTER", "RIGHT"):
                return options[idx][0]
            elif key == "ESC":
                return options[default_index][0]
            elif key.isdigit() and 1 <= int(key) <= len(options):
                return options[int(key) - 1][0]
            # پاک کردن منو
            sys.stdout.write(f"\033[{len(options) + 1}A\033[J")
            sys.stdout.flush()

    def multiselect(self, title: str, options: List[Tuple[str, str]],
                    defaults: Sequence[str] = (), require_one: bool = True) -> List[str]:
        selected = set(defaults)
        start_idx = next((i for i, (oid, _l) in enumerate(options) if oid in selected), 0)
        if not self.tty:
            print(f"❓ {title}  (شماره‌ها را با کاما وارد کنید، مثلاً 1,3)")
            for i, (oid, label) in enumerate(options, start=1):
                star = "✔" if oid in selected else " "
                print(f"    {i}) [{star}] {label}")
            ans = self.ask_text("  انتخاب‌ها", ",".join(str(i + 1) for i, (o, _) in enumerate(options) if o in selected))
            picked = []
            for part in re.split(r"[،,\s]+", fa_digits(ans)):
                if part.isdigit() and 1 <= int(part) <= len(options):
                    picked.append(options[int(part) - 1][0])
            if not picked and require_one:
                picked = [options[0][0]]
            return picked

        idx = start_idx
        while True:
            print(f"\n  {title}   (کلید فاصله: انتخاب/لغو — Enter: تأیید)")
            for i, (oid, label) in enumerate(options):
                cursor = "❯" if i == idx else " "
                box = "☑" if oid in selected else "☐"
                line = f"   {cursor} {box} {label}"
                print("\033[7m" + line + "\033[0m" if i == idx else line)
            key = _read_key()
            if key == "UP":
                idx = (idx - 1) % len(options)
            elif key == "DOWN":
                idx = (idx + 1) % len(options)
            elif key == "SPACE":
                oid = options[idx][0]
                selected.symmetric_difference_update({oid})
            elif key == "ENTER":
                if selected or not require_one:
                    return [o for o, _ in options if o in selected]
                print("  ⚠️  حداقل یک مورد را انتخاب کنید.")
                continue
            elif key == "ALL":
                selected = {o for o, _ in options}
            elif key == "ESC":
                return [o for o, _ in options if o in selected] or [options[0][0]]
            sys.stdout.write(f"\033[{len(options) + 1}A\033[J")
            sys.stdout.flush()


def _read_key() -> str:
    """خواندن یک کلید (با پشتیبانی کلیدهای جهت‌دار)."""
    try:
        import termios
        import tty
    except ImportError:
        return input().strip() or "ENTER"
    fd = sys.stdin.fileno()
    old = termios.tcgetattr(fd)
    try:
        tty.setraw(fd)
        ch = sys.stdin.read(1)
        if ch in ("\x1b",):
            seq = sys.stdin.read(1)
            if seq == "[":
                s2 = sys.stdin.read(1)
                return {"A": "UP", "B": "DOWN", "C": "RIGHT", "D": "LEFT"}.get(s2, "")
            return "ESC"
        if ch in ("\r", "\n"):
            return "ENTER"
        if ch == " ":
            return "SPACE"
        if ch == "a":
            return "ALL"
        if ch == "":
            return "ESC"      # پایان ورودی (EOF) → خروج امن از منو
        return ch
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old)


# ============================================================================
# ۱۳) جریان اصلی برنامه
# ============================================================================

def log_factory(log_path: str):
    def log(msg: str, level: str = "info"):
        stamp = _dt.datetime.now().strftime("%H:%M:%S")
        icon = {"info": "•", "warn": "⚠", "error": "✖", "ok": "✔"}.get(level, "•")
        line = f"[{stamp}] {icon} {msg}"
        print(line)
        try:
            with open(log_path, "a", encoding="utf-8") as f:
                f.write(line + "\n")
        except Exception:
            pass
    return log


def load_dataset(periods: Dict[str, List[str]], log,
                 telegram_paths: Sequence[str] = (),
                 extra_files: Sequence[str] = (),
                 source: str = "excel",
                 date_range: Optional[Dict[str, Any]] = None,
                 ai=None, ai_tasks: Sequence[str] = (),
                 tg_fill_gaps: bool = False) -> Dataset:
    """
    ساخت مجموعه‌داده از منابع مختلف:
      source = "excel"    → فقط فایل‌های اکسل
      source = "telegram" → فقط خروجی JSON تلگرام (با فیلتر بازه‌ی زمانی)
      source = "both"     → اکسل + تکمیل با پست‌های تلگرام (اختیاری)
    """
    ds = Dataset()
    ds.source = source
    periods = periods or {}
    all_files = [f for files in periods.values() for f in files]
    build_county_index()
    ds.expectations = read_expectations(list(all_files) + list(extra_files))
    if ds.expectations:
        log(f"جدول «حد انتظار» برای {persian_number(len(ds.expectations))} ناحیه خوانده شد "
            f"(از فایل کارنامه).", level="ok")

    # ---------- الف) فایل‌های اکسل
    if source in ("excel", "both"):
        for period in sorted(periods):
            files = periods[period]
            log(f"دوره «{period}» — {persian_number(len(files))} فایل")
            for path in files:
                recs, fr = read_workbook(path, period, log)
                ds.records.extend(recs)
                ds.files.append(fr)
                if fr.ok and fr.rows:
                    log(f"   {os.path.basename(path)} → {persian_number(fr.rows)} سطر", level="ok")
                if fr.error:
                    ds.warnings.append(f"{os.path.basename(path)}: {fr.error}")
                if fr.empty_sheets:
                    ds.warnings.append("شیت‌های خالی در " + os.path.basename(path) + " → "
                                       + "، ".join(fr.empty_sheets))
                if fr.missing_sheets:
                    ds.warnings.append("شیت‌های ناموجود در " + os.path.basename(path) + " → "
                                       + "، ".join(fr.missing_sheets))
                for w in fr.warnings:
                    ds.warnings.append(f"{os.path.basename(path)}: {w}")

    # ---------- ب) خروجی تلگرام
    posts: List[Dict[str, Any]] = []
    if telegram_paths:
        posts_all, tg_files = read_telegram_posts(telegram_paths)
        if not posts_all:
            ds.warnings.append("خروجی تلگرام خوانده نشد یا هیچ پستی در آن نبود.")
            log("خروجی تلگرام خوانده نشد یا خالی بود.", level="warn")
        if date_range and (date_range.get("start") or date_range.get("end")):
            posts = filter_posts(posts_all, date_range.get("start"), date_range.get("end"))
            removed = len(posts_all) - len(posts)
            ds.date_range_label = date_range.get("label", "")
            log(f"بازه‌ی زمانی اعمال شد ({ds.date_range_label}): "
                f"{persian_number(len(posts))} پست در بازه، {persian_number(removed)} پست خارج از بازه.")
        else:
            posts = posts_all
        log(f"خروجی تلگرام: {persian_number(len(posts))} پست از "
            f"{persian_number(len(tg_files))} فایل خوانده شد.", level="ok")

    if source == "telegram" and not posts:
        ds.warnings.append("هیچ پستی برای تحلیل پیدا نشد؛ گزارش ساخته نمی‌شود.")
        return ds

    if source == "telegram":
        log("استخراج داده‌ی ساختاریافته از متن پست‌ها …")
        recs, tg_stats = build_telegram_dataset(posts, log, ai, ai_tasks)
        ds.records.extend(recs)
        ds.tg_stats = tg_stats
        log(f"از {persian_number(tg_stats['posts'])} پست، {persian_number(tg_stats['records'])} "
            f"رکورد اقدام ساخته شد"
            + (f" ({persian_number(tg_stats['ai_used'])} رکورد با کمک مدل)."
               if tg_stats.get("ai_used") else "."), level="ok")
        if tg_stats.get("no_data"):
            log(f"   {persian_number(len(tg_stats['no_data']))} پست داده‌ی ساختاریافته نداشت "
                f"(در پیوست فهرست شده‌اند).", level="warn")
    elif source in ("excel", "both"):
        tg_index = read_telegram_export(telegram_paths)
        attach_telegram_content(ds, tg_index, log)
        if source == "both" and posts:
            links = [r.link for r in ds.records if r.link]
            recs, tg_stats = build_telegram_dataset(posts, log, ai, ai_tasks,
                                                     fill_gaps_links=links)
            ds.tg_stats = tg_stats
            if recs:
                log(f"{persian_number(len(recs))} پست که در اکسل نبود، به‌عنوان اقدام تکمیلی "
                    f"افزوده شد.", level="ok")
                ds.records.extend(recs)
            else:
                log("همه‌ی پست‌های بازه، در فایل‌های اکسل هم ثبت شده بودند.", level="ok")

    # ---------- ج) مرتب‌سازی، شناسه یکتا و دسته‌بندی
    for i, rec in enumerate(ds.records, start=1):
        rec.uid = i
    ds.months = sorted({r.month_key for r in ds.records})
    ds.counties = sorted({r.county for r in ds.records if r.county})
    if not ds.months and ds.records:
        ds.months = sorted({r.month_key for r in ds.records})
    classify_records(ds, DEFAULT_CATEGORIES)

    # ---------- د) افزودن لایه‌ی هوش مصنوعی (دسته‌بندی، اثر، و …)
    if ai is not None and ai_tasks:
        apply_ai_layers(ds, ai, ai_tasks, log)
    return ds


def apply_ai_layers(dataset: Dataset, ai, ai_tasks: Sequence[str], log) -> None:
    """اعمال کارهای هوش مصنوعی روی مجموعه‌داده (دسته‌بندی و جمله‌ی اثر)."""
    notes: List[str] = []
    if "classify" in ai_tasks and dataset.records:
        items = [{"id": r.uid, "topic": r.topic or SHEET_LABELS.get(r.sheet, r.sheet),
                  "kind": SHEET_LABELS.get(r.sheet, r.sheet),
                  "hint": (r.structured_text or r.content)[:280]} for r in dataset.records]
        labels = ai.classify_items(items)
        applied = 0
        for r in dataset.records:
            if r.uid in labels:
                r.categories = list(labels[r.uid])
                applied += 1
        if applied:
            notes.append(f"دسته‌بندی {persian_number(applied)} اقدام با مدل")
            log(f"   دسته‌بندی هوشمند برای {persian_number(applied)} اقدام اعمال شد.", level="ok")
        else:
            log("   دسته‌بندی هوشمند نتیجه‌ای نداد؛ دسته‌بندی قاعده‌محور حفظ شد.", level="warn")

    if "impact" in ai_tasks and dataset.records:
        items = [{"id": r.uid, "county": county_label(r), "topic": r.topic,
                  "kind": SHEET_LABELS.get(r.sheet, r.sheet), "people": r.people,
                  "place": r.place} for r in dataset.records[:400]]
        impacts = ai.write_impacts(items)
        for r in dataset.records:
            if r.uid in impacts:
                r.ai_impact = impacts[r.uid]
        if impacts:
            notes.append(f"نوشتن «نتیجه و اثر» برای {persian_number(len(impacts))} اقدام با مدل")
            log(f"   «نتیجه و اثر» برای {persian_number(len(impacts))} اقدام با مدل نوشته شد.",
                level="ok")

    if notes:
        dataset.ai_note = (f"{getattr(ai, 'model', 'مدل')} — " + "؛ ".join(notes) +
                           f" | {ai.stats.summary()}")
    ai.save_cache()


def render_outputs(outdir: str, dataset: Dataset, records: Sequence[Record], report: Dict[str, Any],
                   title: str, categories: Dict[str, Dict[str, Any]], include: Sequence[str],
                   exclude: Sequence[str], months: Sequence[str], want_docx: bool,
                   want_xlsx: bool, logo_path: Optional[str], font_path: Optional[str],
                   log, counties: Sequence[str] = ()) -> List[str]:
    ensure_dir(outdir)
    md = report["markdown"]
    stamp = _dt.datetime.now().strftime("%Y%m%d-%H%M")
    base = os.path.join(outdir, f"{slugify_fa(title, 40)}_{stamp}")
    made: List[str] = []

    write_markdown(base + ".md", md)
    made.append(base + ".md")
    write_html(base + ".html", md, title)
    made.append(base + ".html")

    county_headers, county_rows = table_county(categories, include, exclude, records)
    sheet_headers, sheet_rows = table_sheets(records)
    month_headers, month_rows = table_months(dataset, records, months)
    comp_headers, comp_rows = table_compliance(dataset, records, months, counties)
    gap_rows = []
    have = {r.county for r in records}
    for mk in (months or dataset.months):
        missing = [c for c in EXPECTED_COUNTIES if c not in
                   {r.county for r in records if r.month_key == mk}]
        gap_rows.append([dataset.month_label(mk),
                         "، ".join(missing) if missing else "—"])
    tables: List[Tuple[str, List[str], List[List[Any]]]] = [
        ("جدول ۱ — خلاصه‌ی عملکرد نواحی", county_headers, county_rows),
        ("جدول ۲ — سهم انواع فعالیت", sheet_headers, sheet_rows),
        ("جدول ۳ — مقایسه‌ی دوره‌ها", month_headers, month_rows),
    ]
    if comp_rows:
        tables.append(("جدول ۴ — درصد تحقق نسبت به حد انتظار", comp_headers, comp_rows))
    tables.append(("جدول ۵ — نواحی بدون گزارش در هر دوره", ["دوره", "نواحی بدون گزارش"], gap_rows))

    if want_docx:
        if write_docx(base + ".docx", md, title, tables, font_path, logo_path):
            made.append(base + ".docx")
        else:
            log("python-docx نصب نیست؛ فایل Word ساخته نشد (pip3 install python-docx)", level="warn")

    if want_xlsx:
        write_xlsx(base + ".xlsx", dataset, records, md, categories, include, exclude,
                   months, logo_path)
        made.append(base + ".xlsx")

    return made


AI_TASK_LABELS: Dict[str, str] = {
    "extract": "استخراج داده از متن پست‌ها (فقط در حالت تلگرام)",
    "classify": "دسته‌بندی موضوعی دقیق‌تر (بحران/پویش/هم‌افزایی/ویژه/روتین)",
    "impact": "نوشتن جمله‌ی «نتیجه و اثر» برای هر اقدام",
    "polish": "ویرایش نهایی متن گزارش (لحن اداری‌تر)",
}

AI_BACKENDS: Dict[str, str] = {
    "off": "خاموش (بدون هوش مصنوعی — کاملاً آفلاین)",
    "ollama": "مدل محلی روی همین رایانه (Ollama) — آفلاین و ایمن",
    "api": "سرویس ابری سازگار با OpenAI (نیاز به اینترنت و کلید سرویس)",
    "mock": "حالت آزمایشی (برای تست خودکار، بدون مدل واقعی)",
}


def current_jalali_year() -> int:
    today = _dt.date.today()
    return gregorian_to_jalali(today.year, today.month, today.day)[0]


def parse_tasks(text: Any, default: Sequence[str] = ()) -> List[str]:
    if not text:
        return list(default)
    out = []
    for part in re.split(r"[،,;\s]+", str(text)):
        key = part.strip().lower()
        if key in AI_TASK_LABELS:
            out.append(key)
        elif key in ("all", "همه"):
            return list(AI_TASK_LABELS.keys())
    return out or list(default)


def ai_engine_from_args(args, here: str, log) -> AIEngine:
    """ساخت موتور هوش مصنوعی از پارامترهای خط فرمان + فایل تنظیمات."""
    settings = load_settings(default_settings_path(here))
    backend = (args.ai or settings.get("backend") or "off")
    if backend == "check":
        backend = settings.get("backend") or "ollama"
    return ai_engine_module.build_engine({
        "backend": backend,
        "model": args.ai_model or settings.get("model", ""),
        "base_url": args.ai_base_url or settings.get("base_url", ""),
        "api_key": args.ai_key or settings.get("api_key", ""),
        "timeout": args.ai_timeout or settings.get("timeout", 120),
        "retries": settings.get("retries", 2),
    }, here, log)


def ai_setup_interactive(ui: "UI", here: str, log, needs_extract: bool = False
                         ) -> Tuple[Optional[Any], List[str]]:
    """پرسش‌های تنظیم هوش مصنوعی و ساخت موتور. خروجی: (موتور یا None، کارها)."""
    settings = load_settings(default_settings_path(here))
    ui.head("گام ۳ از ۷ — هوش مصنوعی (اختیاری)")
    ui.say("  با فعال‌کردن هوش مصنوعی، استخراج داده از متن پست‌ها و دسته‌بندی دقیق‌تر انجام می‌شود.")
    ui.say("  اگر خاموش باشد، برنامه با روش قاعده‌محور کار می‌کند و گزارش ساخته می‌شود.")

    if settings.get("backend") in ("ollama", "api", "mock"):
        prev = (f"{AI_BACKENDS.get(settings['backend'], settings['backend'])}"
                f" — مدل {settings.get('model', '')}")
        if ui.ask_yes_no(f"از تنظیمات قبلی هوش مصنوعی استفاده شود؟ ({prev})", default=True):
            options = [("ollama", AI_BACKENDS["ollama"]), ("api", AI_BACKENDS["api"]),
                       ("off", AI_BACKENDS["off"])]
            tasks = parse_tasks(",".join(settings.get("tasks") or []),
                                ["extract", "classify", "impact"])
            merged = dict(settings)
            merged.update({"backend": settings["backend"], "tasks": tasks})
            engine = ai_engine_module.build_engine(merged, here, log)
            ok, msg = engine.available()
            ui.warn(msg) if not ok else ui.ok(msg)
            if not ok:
                return None, []
            return engine, tasks
    if not ui.ask_yes_no("هوش مصنوعی فعال شود؟", default=False):
        ui.info("هوش مصنوعی خاموش ماند؛ همه‌چیز روی همین رایانه و بدون اینترنت پردازش می‌شود.")
        save_settings(default_settings_path(here),
                      {**settings, "backend": "off"})
        return None, []

    back_opts = [("ollama", AI_BACKENDS["ollama"]), ("api", AI_BACKENDS["api"]),
                 ("mock", AI_BACKENDS["mock"])]
    ui.say("")
    ui.say("  راهنما: روش محلی (Ollama) آفلاین است و داده از دستگاه بیرون نمی‌رود.")
    ui.say("          روش ابری، متن پست‌ها را به سرویس بیرونی می‌فرستد.")
    backend = ui.select("روش استفاده از هوش مصنوعی:", back_opts, default_index=0)
    model = ""
    base_url = ""
    api_key = ""
    if backend == "ollama":
        model = ui.ask_text("نام مدل محلی (مثلاً qwen2.5:7b)", "qwen2.5:7b")
        base_url = ui.ask_text("نشانی Ollama", "http://localhost:11434")
    elif backend == "api":
        base_url = ui.ask_text("نشانی سرویس (مثلاً https://api.openai.com/v1)",
                              settings.get("base_url", "https://api.openai.com/v1"))
        model = ui.ask_text("نام مدل", settings.get("model", "gpt-4o-mini"))
        ui.warn("توجه: در روش ابری، متن پست‌ها به سرویس بیرونی ارسال می‌شود.")
        api_key = ui.ask_text("کلید سرویس (API key) — روی همین رایانه ذخیره می‌شود", "")
    else:
        model = "mock"

    tasks_default = ["classify", "impact"] + (["extract"] if needs_extract else [])
    task_opts = [(k, v) for k, v in AI_TASK_LABELS.items()]
    tasks = ui.multiselect("کدام کارها با هوش مصنوعی انجام شود؟", task_opts,
                           defaults=tasks_default)
    engine = AIEngine(backend=backend, model=model, base_url=base_url, api_key=api_key,
                      cache_path=os.path.join(here, ".ai-cache.json"), log=log)
    ok, msg = engine.available()
    ui.ok(msg) if ok else ui.warn(msg)
    if not ok and not ui.ask_yes_no("با این وجود ادامه دهیم؟ (در صورت خطا، روش قاعده‌محور "
                                    "جایگزین می‌شود)", default=True):
        return None, []
    if ui.ask_yes_no("این تنظیمات برای اجراهای بعدی ذخیره شود؟", default=True):
        save_settings(default_settings_path(here), {
            "backend": backend, "model": model, "base_url": base_url,
            "api_key": api_key, "tasks": tasks, "timeout": 120, "retries": 2,
        })
        ui.info(f"تنظیمات در فایل {os.path.basename(default_settings_path(here))} ذخیره شد.")
    return engine, tasks


def apply_polish(ui_or_none, ai, tasks: Sequence[str], report: Dict[str, Any],
                 max_words: int, log) -> None:
    """ویرایش نهایی متن گزارش با مدل (با کنترل امنیتی اعداد)."""
    if not (ai and "polish" in tasks) or not report.get("markdown"):
        return
    polished, warn = ai.polish_report(report["markdown"], max_words)
    if warn:
        (ui_or_none.warn(warn) if ui_or_none else log(warn, level="warn"))
    else:
        report["markdown"] = polished
        report["word_count"] = len(polished.split())
        (ui_or_none.ok("متن گزارش توسط مدل ویرایش شد.") if ui_or_none
         else log("متن گزارش توسط مدل ویرایش شد.", level="ok"))


def run_interactive(ui: UI) -> int:
    from pathlib import Path
    here = str(Path(__file__).resolve().parent)
    root = os.path.dirname(here)
    outdir = os.path.join(root, "گزارش‌های-ساخته‌شده")
    ensure_dir(outdir)
    log = log_factory(os.path.join(outdir, "gozaresh.log"))

    ui.head(f"{APP_NAME}  —  نسخه {APP_VERSION}")
    ui.say("سلام. من گزارش‌یار هوشمند شما هستم. 🤖")
    ui.say("چند پرسش کوتاه می‌پرسم و سپس گزارش را می‌سازم؛ هیچ دانش برنامه‌نویسی لازم نیست.")
    ui.info(f"پوشه‌ی خروجی گزارش‌ها: {outdir}")

    # ---------------- گام ۱: داده‌ها
    ui.head("گام ۱ از ۷ — داده‌ها کجاست؟")
    default_input = guess_data_dir(here, root)
    ui.say("  اکسل شهرستان‌ها، خروجی JSON تلگرام یا هر دو را می‌توانید بدهید.")
    raw = ui.ask_text("مسیر پوشه(ها)" + (" — خالی = پوشه‌ی پیشنهادی" if default_input else ""),
                      default_input)
    paths = [x.strip().strip('"').strip("'") for x in re.split(r"[،,;]+", raw) if x.strip()]
    if not paths:
        ui.warn("مسیری داده نشد. پوشه‌ی داده را کنار همین برنامه بگذارید و دوباره اجرا کنید.")
        return 1

    periods, warns, json_files = discover_inputs(paths)
    for w in warns:
        ui.warn(w)
    excel_found = bool(periods)
    if excel_found:
        total_files = sum(len(v) for v in periods.values())
        ui.ok(f"{persian_number(len(periods))} دوره و {persian_number(total_files)} فایل اکسل "
              f"پیدا شد.")
        for p in sorted(periods):
            ui.say(f"     • {p}: {persian_number(len(periods[p]))} فایل")
    else:
        ui.warn("فایل اکسلی پیدا نشد (اگر فقط خروجی تلگرام دارید، اشکالی ندارد).")

    tg_paths = list(json_files)
    if json_files:
        ui.ok("خروجی JSON تلگرام پیدا شد: " + "، ".join(os.path.basename(f) for f in tg_paths[:3]))
    elif ui.ask_yes_no("فایل یا پوشه‌ی «Export تلگرام» دارید؟", default=False):
        tg_raw = ui.ask_text("مسیر فایل/پوشه‌ی JSON تلگرام (چند مورد با کاما)", "")
        tg_paths = [x.strip().strip('"').strip("'") for x in re.split(r"[،,;]+", tg_raw) if x.strip()]

    if excel_found and tg_paths:
        ui.say("")
        ui.say("  هر دو منبع موجود است. کدام مبنای گزارش باشد؟")
        src_opts = [("excel", "فقط فایل‌های اکسل شهرستان‌ها"),
                    ("both", "اکسل + تکمیل با پست‌های تلگرام (پست‌های غیرتکراری)"),
                    ("telegram", "فقط خروجی تلگرام (تحلیل متن پست‌ها)")]
        source = ui.select("منبع داده:", src_opts, default_index=0)
    elif tg_paths:
        source = "telegram"
    else:
        source = "excel"

    # ---------------- گام ۲: بازه‌ی زمانی (برای تلگرام)
    date_range: Dict[str, Any] = {}
    ui.head("گام ۲ از ۷ — بازه‌ی زمانی")
    if source in ("telegram", "both"):
        posts_preview, _files = read_telegram_posts(tg_paths)
        if posts_preview:
            first, last = posts_preview[0], posts_preview[-1]
            ui.info(f"{persian_number(len(posts_preview))} پست در خروجی هست؛ از "
                    f"{unix_to_jalali_time(first['ts'])} تا {unix_to_jalali_time(last['ts'])}.")
        ui.say("  بازه‌ای که مسئول شما گفته را وارد کنید (مثال: 1405/06/01 یا «شهریور»).")
        yr = current_jalali_year()
        d_from = ui.ask_text("از تاریخ (خالی = از ابتدای خروجی)", "")
        d_to = ui.ask_text("تا تاریخ (خالی = تا انتهای خروجی)", "")
        date_range = resolve_date_range(d_from, d_to, yr)
        if date_range.get("label"):
            ui.ok("بازه اعمال می‌شود: " + date_range["label"])
        else:
            ui.info("بازه‌ای محدود نشد؛ همه‌ی پست‌های خروجی بررسی می‌شوند.")
    else:
        ui.info("در حالت اکسل، بازه از تاریخ سطرهای همان فایل‌ها خوانده می‌شود.")

    # ---------------- گام ۳: هوش مصنوعی
    ai, ai_tasks = ai_setup_interactive(ui, here, log, needs_extract=(source == "telegram"))

    # ---------------- خواندن داده‌ها
    ui.say("\n  در حال خواندن و تحلیل داده‌ها …")
    ds = load_dataset(periods, log, tg_paths, find_career_files(paths), source=source,
                      date_range=date_range, ai=ai, ai_tasks=ai_tasks)
    for w in ds.warnings[:12]:
        ui.warn(w)
    if len(ds.warnings) > 12:
        ui.warn(f"و {persian_number(len(ds.warnings) - 12)} هشدار دیگر (در فایل gozaresh.log).")
    if not ds.records:
        ui.warn("هیچ رکوردی خوانده نشد. مسیر و ساختار داده‌ها را بررسی کنید.")
        return 1
    ui.ok(f"{persian_number(len(ds.records))} رکورد آماده شد "
          f"({persian_number(len(ds.counties))} ناحیه، {persian_number(len(ds.months))} دوره).")
    have = {r.county for r in ds.records if r.county}
    missing = [c for c in EXPECTED_COUNTIES if c not in have]
    if missing and source != "telegram":
        ui.warn("بدون گزارش در این بازه: " + "، ".join(missing))

    # ---------------- گام ۴: دوره
    ui.head("گام ۴ از ۷ — کدام دوره؟")
    month_opts = [("ALL", "همه‌ی دوره‌های موجود")] + [(m, ds.month_label(m)) for m in ds.months]
    dm = [m for m in ds.months] if len(ds.months) > 1 else ds.months[:1]
    choice = ui.multiselect("دوره(های) مورد نظر:", month_opts, defaults=dm or ["ALL"])
    months = ds.months if (not choice or "ALL" in choice) else [m for m in ds.months if m in choice]

    # ---------------- گام ۵: نواحی
    ui.head("گام ۵ از ۷ — کدام نواحی؟")
    county_opts = [("ALL", "همه‌ی نواحی (پیشنهادی)")] + [(c, c) for c in ds.counties]
    choice = ui.multiselect("نواحی مورد نظر:", county_opts, defaults=["ALL"])
    counties = ds.counties if (not choice or "ALL" in choice) else [c for c in ds.counties if c in choice]

    # ---------------- گام ۶: محورها
    ui.head("گام ۶ از ۷ — محورهای گزارش")
    cat_opts = [(k, v["title"]) for k, v in DEFAULT_CATEGORIES.items()]
    defaults = [k for k in DEFAULT_CATEGORIES if k not in DEFAULT_EXCLUDED]
    picked = ui.multiselect("کدام دسته‌ها در گزارش بیایند؟ (روتین را برندارید)",
                            cat_opts, defaults=defaults)
    include = [k for k in picked if k not in DEFAULT_EXCLUDED]
    unpicked_positive = [k for k in DEFAULT_CATEGORIES
                         if k not in picked and k not in DEFAULT_EXCLUDED]
    exclude = [] if "routine" in picked else ["routine"]
    if unpicked_positive:
        names = "، ".join(DEFAULT_CATEGORIES[k]["title"] for k in unpicked_positive)
        if ui.ask_yes_no(f"اقدامات دسته‌های انتخاب‌نشده ({names}) از گزارش حذف شوند؟", default=False):
            exclude += unpicked_positive
    ui.info("اقدامات روتین (بازدید/نظارت/جلسه عادی) به‌صورت پیش‌فرض حذف می‌شوند.")

    # ---------------- گام ۷: قالب، محدودیت و خروجی
    ui.head("گام ۷ از ۷ — قالب، محدودیت و فایل‌های خروجی")
    tpl_opts = [(str(k), f"{k}) {v}") for k, v in TEMPLATES.items()]
    template_id = int(ui.select("قالب گزارش:", tpl_opts, default_index=0))
    default_title = REPORT_TITLES.get(template_id, REPORT_TITLES[1])
    custom_title = ui.ask_text("عنوان گزارش" + f" [{default_title}]", "").strip()
    ui.say("")
    ui.say("  اگر «درخواست مدیر» را دارید، متن آن را بچسبانید تا سقف‌هایش خودکار اعمال شود.")
    req_text = ui.ask_text("متن درخواست مدیر (اختیاری — Enter برای رد کردن)", "")
    parsed = parse_request_text(req_text)
    if parsed.get("max_words"):
        ui.info(f"از متن درخواست خوانده شد: سقف {persian_number(parsed['max_words'])} واژه")
    max_words = int(parsed.get("max_words", 0))
    max_chars = int(parsed.get("max_chars", 0))
    lim = ui.ask_text("سقف واژه" + (f" [{persian_number(max_words)}]" if max_words else "") +
                      " (خالی = بدون محدودیت)", "")
    if lim.strip().isdigit():
        max_words = int(fa_digits(lim))
    elif not max_words:
        page = ui.ask_text("یا سقف صفحه (خالی = بدون محدودیت)", "")
        if page.strip().isdigit():
            max_words = int(fa_digits(page)) * 350
    ch = ui.ask_text("سقف کاراکتر" + (f" [{persian_number(max_chars)}]" if max_chars else "") +
                     " (خالی = بدون محدودیت)", "")
    if ch.strip().isdigit():
        max_chars = int(fa_digits(ch))
    details = ui.ask_yes_no("پیوست لینک مستندات هر اقدام در متن گزارش درج شود؟", default=False)
    ui.info("قالب پیشنهادی و اصلی: فایل Word. (Markdown و HTML همیشه ساخته می‌شوند.)")
    want_word = ui.ask_yes_no("فایل Word (docx) ساخته شود؟ (خروجی اصلی)", default=True)
    want_excel = ui.ask_yes_no("فایل Excel (xlsx) هم ساخته شود؟", default=False)
    outdir_custom = ui.ask_text("پوشه‌ی خروجی — خالی = پیش‌فرض", outdir)
    logo = ui.ask_text("مسیر لوگو (png/jpg) — خالی = بدون لوگو", "")
    font_path = find_persian_font([root, here, os.path.dirname(root)])

    title = custom_title or default_title
    ui.say("\n  در حال ساخت گزارش …")
    records = select_records(ds, include, exclude, months, counties)
    if not records:
        ui.warn("با این فیلترها هیچ اقدامی باقی نماند. فیلترها را ساده‌تر کنید.")
        return 1

    sections = [k for k in include if k in DEFAULT_CATEGORIES]
    report = build_report(ds, records, title, sections, DEFAULT_CATEGORIES, months, counties,
                          max_words=max_words, max_chars=max_chars, details=details)
    apply_polish(ui, ai, ai_tasks, report, max_words, log)
    for issue in quality_check(report["markdown"], max_words, max_chars, records, exclude):
        ui.warn("کنترل کیفیت: " + issue)

    made = render_outputs(outdir_custom, ds, records, report, title, DEFAULT_CATEGORIES,
                          include, exclude, months, want_word, want_excel,
                          logo if logo and os.path.exists(logo) else None, font_path, log,
                          counties)

    ui.head("گزارش آماده شد")
    ui.ok(f"{persian_number(len(records))} اقدام غیرروتین در گزارش آمده است.")
    ui.ok(f"تعداد واژه‌ها: {persian_number(report['word_count'])}")
    for m in made:
        ui.say("   📄 " + m)
    html_out = next((m for m in made if m.endswith(".html")), None)
    if html_out and ui.ask_yes_no("گزارش همین‌جا در مرورگر باز شود؟", default=True):
        try:
            import webbrowser
            webbrowser.open("file://" + os.path.abspath(html_out))
            ui.ok("گزارش در مرورگر باز شد.")
        except Exception:
            ui.warn("باز کردن خودکار مرورگر ممکن نشد؛ فایل HTML را دستی باز کنید.")
    return 0


def run_batch(args) -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)
    outdir = args.out or os.path.join(root, "گزارش‌های-ساخته‌شده")
    logpath = os.path.join(ensure_dir(outdir), "gozaresh.log")
    log = log_factory(logpath)

    raw_inputs = args.input or [[os.path.join(here, "demo-input")]]
    paths = [x.strip() for group in raw_inputs for x in group
             for x in re.split(r"[،,;]", x) if x.strip()]
    periods, warns, json_files = discover_inputs(paths)
    for w in warns:
        log(w, level="warn")

    tg_args = [x.strip() for group in (args.telegram or []) for x in group
               for x in re.split(r"[،,;]", x) if x.strip()]
    telegram_paths = tg_args if tg_args else json_files
    if telegram_paths and not tg_args:
        log("خروجی JSON تلگرام به‌طور خودکار پیدا شد: "
            + "، ".join(os.path.basename(f) for f in telegram_paths[:3]))

    # ---- تعیین منبع داده
    source = args.source
    if source == "auto":
        if periods and telegram_paths:
            source = "both" if args.tg_fill_gaps else "excel"
        elif periods:
            source = "excel"
        elif telegram_paths:
            source = "telegram"
    if source in ("excel", "both") and not periods:
        log("هیچ فایل اکسلی پیدا نشد.", level="error")
        return 1
    if source in ("telegram", "both") and not telegram_paths:
        log("هیچ خروجی JSON تلگرامی پیدا نشد.", level="error")
        return 1
    log(f"منبع داده: {'اکسل' if source == 'excel' else 'تلگرام' if source == 'telegram' else 'اکسل + تلگرام'}")

    # ---- بازه‌ی زمانی
    date_range = resolve_date_range(args.date_from, args.date_to, current_jalali_year())
    if date_range.get("label"):
        log("بازه‌ی زمانی: " + date_range["label"])

    # ---- هوش مصنوعی
    ai = None
    ai_tasks: List[str] = []
    backend = (args.ai or "off").lower()
    if backend not in ("off", "check"):
        if ai_engine_module is None:
            log("ماژول ai_engine.py در دسترس نیست؛ بدون هوش مصنوعی ادامه می‌دهیم.", level="warn")
        else:
            ai = ai_engine_from_args(args, here, log)
            ai_tasks = parse_tasks(args.ai_tasks,
                                   ["extract", "classify", "impact"]
                                   if source == "telegram" else ["classify", "impact"])
            ok, msg = ai.available()
            log(("هوش مصنوعی: " if ok else "هشدار هوش مصنوعی: ") + msg,
                level="ok" if ok else "warn")
            if not ok:
                ai = None
                ai_tasks = []
    if backend == "check":
        if ai_engine_module is None:
            log("ماژول ai_engine.py پیدا نشد.", level="error")
            return 1
        engine = ai_engine_from_args(args, here, log)
        ok, msg = engine.available()
        log(msg, level="ok" if ok else "error")
        log(f"روش: {engine.backend} | مدل: {engine.model} | نشانی: {engine.base_url}")
        return 0 if ok else 1

    # ---- بارگذاری داده‌ها
    ds = load_dataset(periods, log, telegram_paths, find_career_files(paths),
                      source=source, date_range=date_range, ai=ai, ai_tasks=ai_tasks,
                      tg_fill_gaps=bool(args.tg_fill_gaps))
    for w in ds.warnings:
        log(w, level="warn")
    if not ds.records:
        log("هیچ رکوردی خوانده نشد.", level="error")
        if source == "excel":
            log("راهنما: مطمئن شوید فایل‌های اکسل ماهانه (با ۵ شیت استاندارد) در پوشه‌ی داده "
                "قرار دارند؛ فایل «تهیه کارنامه نواحی» و فایل‌های خالی، داده‌ی فعالیت ندارند.",
                level="warn")
        else:
            log("راهنما: بازه‌ی زمانی را بازتر کنید یا خروجی JSON دیگری بدهید.", level="warn")
        return 1

    if args.tg_fill_gaps and source == "excel" and telegram_paths:
        log("برای افزودن پست‌های تکمیلی، پارامتر --source both را هم بدهید.", level="warn")

    # ---- فیلترها
    months = ds.months
    if args.month:
        wanted = [m for m in ds.months
                  if args.month in (m, ds.month_label(m), m.split("-")[1],
                                    jalali_month_name(int(m.split("-")[1])))]
        months = wanted or ds.months
    counties = ([c for c in ds.counties if county_key(args.county) in county_key(c)]
                if args.county else ds.counties)

    include = [x for x in (args.include.split(",") if args.include else
                           [k for k in DEFAULT_CATEGORIES if k not in DEFAULT_EXCLUDED]) if x]
    exclude = [x for x in (args.exclude.split(",") if args.exclude else DEFAULT_EXCLUDED) if x]
    records = select_records(ds, include, exclude, months, counties)
    if not records:
        log("با این فیلترها اقدامی باقی نماند.", level="error")
        return 1

    # ---- ساخت گزارش
    template_id = int(args.template or 1)
    title = args.title or REPORT_TITLES.get(template_id, REPORT_TITLES[1])
    parsed_req = parse_request_text(args.request or "")
    max_words = args.max_words or int(parsed_req.get("max_words", 0))
    max_chars = args.max_chars or int(parsed_req.get("max_chars", 0))
    report = build_report(ds, records, title, include, DEFAULT_CATEGORIES, months, counties,
                          max_words=max_words, max_chars=max_chars, details=bool(args.details))
    apply_polish(None, ai, ai_tasks, report, max_words, log)

    made = render_outputs(outdir, ds, records, report, title, DEFAULT_CATEGORIES, include,
                          exclude, months, not args.no_docx, not args.no_xlsx,
                          args.logo, find_persian_font([root, here]), log, counties)
    if ai is not None:
        log("آمار هوش مصنوعی: " + ai.stats.summary())
    log(f"گزارش ساخته شد: {persian_number(len(records))} اقدام، "
        f"{persian_number(report['word_count'])} واژه", level="ok")
    for m in made:
        log("خروجی: " + m)
    return 0


def build_argparser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="gozaresh.py",
        description=f"{APP_NAME} — ساخت گزارش مدیریتی از فایل‌های اکسل ماهانه‌ی نواحی",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""نمونه‌ها:
  python3 gozaresh.py                                   اجرای تعاملی (پیشنهادی)
  python3 gozaresh.py --input ./داده‌ها --template 2     گزارش عملکرد نواحی
  python3 gozaresh.py --input ./مرداد ./شهریور --max-words 700
  python3 gozaresh.py --include crisis,special,campaign
""")
    p.add_argument("--input", "-i", nargs="*", action="append", metavar="مسیر",
                   help="پوشه یا فایل داده؛ تکرار پارامتر یا جدا کردن با کاما مجاز است")
    p.add_argument("--out", "-o", help="پوشه‌ی خروجی")
    p.add_argument("--template", "-t", type=int, default=1, choices=list(TEMPLATES.keys()),
                   help="قالب گزارش: " + " | ".join(f"{k}={v}" for k, v in TEMPLATES.items()))
    p.add_argument("--title", help="عنوان دلخواه گزارش")
    p.add_argument("--month", help="فیلتر دوره (مثلاً شهریور یا 1405-06)")
    p.add_argument("--request", help="متن درخواست مدیر؛ سقف واژه/صفحه/کاراکتر از آن خوانده می‌شود")
    p.add_argument("--county", help="فیلتر یک ناحیه")
    p.add_argument("--include", help="دسته‌ها با کاما: crisis,campaign,synergy,special")
    p.add_argument("--exclude", help="دسته‌های حذفی با کاما (پیش‌فرض: routine)")
    p.add_argument("--max-words", type=int, default=0, help="سقف تعداد واژه")
    p.add_argument("--max-chars", type=int, default=0, help="سقف تعداد کاراکتر")
    p.add_argument("--details", action="store_true", help="درج لینک مستندات در متن")
    p.add_argument("--telegram", nargs="*", action="append",
                   help="فایل/پوشه‌ی خروجی JSON تلگرام (منبع داده یا دسته‌بندی دقیق‌تر)")
    p.add_argument("--source", choices=["auto", "excel", "telegram", "both"], default="auto",
                   help="منبع داده: auto (پیش‌فرض) | excel | telegram | both")
    p.add_argument("--from", dest="date_from", metavar="تاریخ",
                   help="شروع بازه (شمسی): 1405/06/01 یا «شهریور»")
    p.add_argument("--to", dest="date_to", metavar="تاریخ",
                   help="پایان بازه (شمسی): 1405/06/31 یا «شهریور»")
    p.add_argument("--tg-fill-gaps", action="store_true",
                   help="پست‌های تلگرام که در اکسل نیستند هم به گزارش اضافه شوند")
    p.add_argument("--ai", choices=["off", "ollama", "api", "mock", "check"], default=None,
                   help="هوش مصنوعی: off | ollama (محلی) | api (ابری) | mock (آزمایشی) | check (بررسی اتصال)")
    p.add_argument("--ai-model", help="نام مدل (مثلاً qwen2.5:7b یا gpt-4o-mini)")
    p.add_argument("--ai-base-url", help="نشانی سرویس هوش مصنوعی")
    p.add_argument("--ai-key", help="کلید سرویس ابری (یا متغیر محیطی GOZARESH_AI_KEY)")
    p.add_argument("--ai-timeout", type=int, help="مهلت هر فراخوانی مدل (ثانیه)")
    p.add_argument("--ai-tasks", help="کارهای هوش مصنوعی با کاما: extract,classify,impact,polish")
    p.add_argument("--ai-help", action="store_true", help="راهنمای راه‌اندازی هوش مصنوعی")
    p.add_argument("--logo", help="مسیر فایل لوگو")
    p.add_argument("--no-docx", action="store_true", help="فایل Word ساخته نشود")
    p.add_argument("--no-xlsx", action="store_true", help="فایل Excel ساخته نشود")
    p.add_argument("--version", action="version", version=f"{APP_NAME} {APP_VERSION}")
    return p


def main(argv: Optional[Sequence[str]] = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    parser = build_argparser()
    args = parser.parse_args(argv)
    if getattr(args, "ai_help", False):
        print(AI_HELP)
        return 0

    if not argv:
        ui = UI()
        try:
            return run_interactive(ui)
        except KeyboardInterrupt:
            print("\n  لغو شد.")
            return 130
        except Exception:
            print("\n  خطای غیرمنتظره:")
            traceback.print_exc()
            return 1
    try:
        return run_batch(args)
    except Exception:
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    sys.exit(main())
