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
            else:
                warns.append(f"فایل پشتیبانی‌نشده (فقط xlsx/xlsm): {p}")
            continue

        # پوشه: ابتدا فایل‌های اکسل خودِ پوشه
        direct = sorted([os.path.join(p, f) for f in os.listdir(p)
                         if f.lower().endswith((".xlsx", ".xlsm")) and not f.startswith("~$")])
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
        "by_county": Counter(r.county for r in records),
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
        agg["by_county_people"][r.county] += r.people
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


def impact_sentence(rec: Record) -> str:
    """تولید «نتیجه و اثر» برای هر اقدام، بدون اغراق و بدون داده‌ی ساختگی."""
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

    lines: List[str] = []
    lines.append(f"# {title}")
    lines.append("")
    lines.append(f"**بازه‌ی گزارش:** {period_lbl}   |   **تعداد نواحی/شهرستان‌های دارای داده:** "
                 f"{persian_number(agg['counties'])} از {persian_number(len(EXPECTED_COUNTIES))}")
    lines.append("")

    # در سقف‌های واژه‌ی کم، سطرهای اختیاری حذف و متن‌ها فشرده می‌شوند
    compact = bool(max_words) and max_words < 420

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
            f"{SHEET_LABELS[k]} {persian_number(agg['by_sheet'][k])} "
            f"({persian_percent(agg['by_sheet'][k] / total_act)})"
            for k in ["حضوری", "مجازی", "گردان", "خلاقانه", "تولیدات"] if agg["by_sheet"].get(k))
        lines.append(f"- توزیع فعالیت‌ها: {dist}")
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
    lines.append("- نواحی دارای گزارش: " + persian_number(len(counted)) + " از "
                 + persian_number(len(EXPECTED_COUNTIES)))
    if top5:
        lines.append("- پنج ناحیه‌ی فعال: " + "، ".join(
            f"{c} ({persian_number(n)})" for c, n in top5))
    least5 = sorted(counted, key=lambda x: (x[1], x[0]))[:5]
    least5 += [(c, 0) for c in zero_counties]
    least5 = least5[:5]
    if (not compact) and least5 and len(counted) > 5 and \
            [c for c, _ in least5] != [c for c, _ in top5]:
        lines.append("- پنج ناحیه با کمترین اقدام: " + "، ".join(
            f"{c} ({persian_number(n)})" for c, n in least5))
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

    def render_sections(alloc: Dict[str, int]) -> List[str]:
        out: List[str] = []
        for sec, sec_title, sec_recs in prepared:
            out.append(f"## بخش: {sec_title}")
            if not sec_recs:
                out.append("در این بخش، داده‌ی قابل استنادی در بازه‌ی مورد نظر ثبت نشده است.")
                out.append("")
                continue
            people = sum(r.people for r in sec_recs)
            counties_in = len({r.county for r in sec_recs})
            n = len(sec_recs)
            if compact:
                extra = f"{persian_number(people)} مخاطب، " if people else "بدون مخاطب مستقیم، "
                out.append(f"**خلاصه:** {persian_number(n)} اقدام، {extra}"
                           f"{persian_number(counties_in)} ناحیه.")
            elif people:
                out.append(f"**خلاصه‌ی آماری:** {persian_number(n)} اقدام در "
                           f"{persian_number(counties_in)} ناحیه؛ مجموع مخاطبان "
                           f"{persian_number(people)} نفر؛ میانگین "
                           f"{persian_number(people / n)} نفر به‌ازای هر اقدام.")
            else:
                out.append(f"**خلاصه:** {persian_number(n)} اقدام تولیدی/رسانه‌ای در "
                           f"{persian_number(counties_in)} ناحیه (بدون مخاطب مستقیم).")
            out.append("")
            shown = max(0, min(alloc.get(sec, 0), n))
            for r in sec_recs[:shown]:
                where = r.place or r.platform or "—"
                out.append(f"• **{r.county}** — {r.topic or SHEET_LABELS.get(r.sheet, r.sheet)} "
                           f"(تاریخ {fa_date(r.date)}، {where})")
                out.append(f"  - نتیجه و اثر: {impact_sentence(r)}")
                if details and r.link:
                    out.append(f"  - مستند: {r.link}")
            rest = n - shown
            if rest > 0:
                if shown == 0:
                    out.append(f"- {persian_number(rest)} اقدام این دسته در فایل پیوست "
                               f"«اقدامات» فهرست شده است.")
                else:
                    out.append(f"- و {persian_number(rest)} اقدام دیگر در همین دسته "
                               f"(فهرست کامل در فایل پیوست «اقدامات»).")
            out.append("")
        return out

    # ---- تخصیص تعداد اقدامات هر بخش با توجه به سقف واژه ----------------------
    alloc: Dict[str, int] = {}
    capacity = MAX_ITEMS_PER_SECTION * max(1, len(prepared))
    if max_words:
        base_words = _words(lines) + _words(render_sections({})) + 45  # + برآورد جمع‌بندی
        capacity = max(0, (max_words - base_words) // 42)              # ≈۴۲ واژه برای هر اقدام
    i = 0
    while capacity > 0 and prepared:
        sec, _sec_title, sec_recs = prepared[i % len(prepared)]
        cap = min(MAX_ITEMS_PER_SECTION, len(sec_recs))
        if alloc.get(sec, 0) < cap:
            alloc[sec] = alloc.get(sec, 0) + 1
            capacity -= 1
        elif all(alloc.get(s, 0) >= min(MAX_ITEMS_PER_SECTION, len(rs)) for s, _t, rs in prepared):
            break
        i += 1

    lines.extend(render_sections(alloc))

    # درصد تحقق نسبت به «حد انتظار» (در صورت وجود فایل کارنامه)
    compliance = compute_compliance(dataset, records, months, counties)
    if compliance and not compact:
        lines.append("## درصد تحقق نسبت به حد انتظار")
        for row in compliance[:8]:
            lines.append(f"- {row['county']}: حضوری {persian_percent(row['p_hazeri'])}"
                         f"، مجازی {persian_percent(row['p_majazi'])}"
                         f"، خلاقانه {persian_percent(row['p_khalaghane'])}"
                         f"، تولیدات {persian_percent(row['p_toliat'])}"
                         f" (میانگین {persian_percent(row['p_total'])})")
        if len(compliance) > 8:
            lines.append(f"- و {persian_number(len(compliance) - 8)} ناحیه‌ی دیگر "
                         f"(جدول کامل در شیت «تحقق انتظار» فایل اکسل).")
        lines.append("")

    # جمع‌بندی
    lines.append("## جمع‌بندی")
    top = agg["by_county"].most_common(5)
    top_txt = "، ".join(f"{c} ({persian_number(n)} اقدام)" for c, n in top) if top else "—"
    peak = agg["by_sheet"].most_common(1)
    peak_txt = (f"{SHEET_LABELS.get(peak[0][0], peak[0][0])} با "
                f"{persian_number(peak[0][1])} اقدام") if peak else "—"
    lines.append(f"بیشترین حجم اقدامات به‌ترتیب در نواحی {top_txt} ثبت شده و پرتکرارترین قالب، "
                 f"{peak_txt} است. مجموع مخاطبان مستقیم این بازه "
                 f"{persian_number(agg['total_people'])} نفر و میانگین مخاطب هر اقدام "
                 f"{persian_number(agg['total_people'] / agg['total_activities'] if agg['total_activities'] else 0)} نفر است.")
    if len(months) > 1:
        mom = month_over_month(records, months)
        growths = [m for m in mom if m["growth_activities"] is not None]
        if growths:
            g = growths[-1]["growth_activities"]
            direction = "رشد" if g >= 0 else "کاهش"
            lines.append(f"روند ماه‌به‌ماه تعداد اقدامات در آخرین دوره نسبت به دوره‌ی قبل، "
                         f"{direction} {persian_percent(abs(g))} را نشان می‌دهد.")
    lines.append("")

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
    counties = sorted({r.county for r in records})
    rows = []
    for c in counties:
        rs = [r for r in records if r.county == c]
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


def write_docx(path: str, md: str, title: str, tables: List[Tuple[List[str], List[List[Any]]]],
               font_path: Optional[str] = None, logo_path: Optional[str] = None) -> bool:
    """تولید فایل Word با python-docx (در صورت نصب بودن)."""
    try:
        from docx import Document
        from docx.enum.text import WD_ALIGN_PARAGRAPH
        from docx.oxml.ns import qn
        from docx.shared import Pt, RGBColor
    except ImportError:
        return False

    doc = Document()
    # راست‌به‌چپ کردن کل سند
    for section in doc.sections:
        try:
            section._sectPr.xpath("./w:bidi")[0].set(qn("w:val"), "1")
        except Exception:
            pass

    style = doc.styles["Normal"]
    style.font.name = "IRANSans"
    style.font.size = Pt(12)
    try:
        style.element.rPr.rFonts.set(qn("w:cs"), "IRANSans")
        style.element.rPr.rFonts.set(qn("w:eastAsia"), "IRANSans")
    except Exception:
        pass

    if logo_path and os.path.exists(logo_path):
        try:
            doc.add_picture(logo_path, width=Pt(180))
        except Exception:
            pass

    for raw in md.split("\n"):
        line = raw.rstrip()
        if not line.strip():
            continue
        if line.startswith("# "):
            p = doc.add_heading(line[2:], level=0)
        elif line.startswith("## "):
            p = doc.add_heading(line[3:], level=1)
        elif line.startswith(("• ", "- ")) or line.startswith("  - "):
            indent = 1 if line.startswith("  - ") else 0
            text = re.sub(r"^(\s*[-•]\s*)", "", line)
            p = doc.add_paragraph(re.sub(r"\*\*(.+?)\*\*", r"\1", text), style="List Bullet")
            if indent:
                p.paragraph_format.left_indent = Pt(28)
        else:
            p = doc.add_paragraph(re.sub(r"\*\*(.+?)\*\*", r"\1", line))
        try:
            p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
            for run in p.runs:
                run.font.name = "IRANSans"
                run.font.size = Pt(12)
                run._element.rPr.rFonts.set(qn("w:cs"), "IRANSans")
                if p.style.name.startswith("Heading"):
                    run.font.color.rgb = RGBColor(0x1B, 0x2A, 0x4A)
        except Exception:
            pass

    for headers, rows in tables:
        if not rows:
            continue
        t = doc.add_table(rows=1, cols=len(headers))
        t.style = "Light Grid Accent 1"
        for i, h in enumerate(headers):
            cell = t.rows[0].cells[i]
            cell.text = str(h)
        for row in rows[:60]:
            cells = t.add_row().cells
            for i, v in enumerate(row):
                cells[i].text = persian_number(v) if isinstance(v, (int, float)) and not isinstance(v, bool) else str(v)
        # راست‌به‌چپ جدول
        try:
            tblPr = t._tbl.tblPr
            bidi = tblPr.makeelement(qn("w:bidiVisual"), {})
            tblPr.append(bidi)
        except Exception:
            pass
        doc.add_paragraph("")

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
               "فایل مبدأ", "سطر"]
    ws5.append(headers)
    for r in sorted(records, key=lambda x: (x.county, x.sheet)):
        ws5.append([
            r.county, dataset.month_label(r.month_key), SHEET_LABELS.get(r.sheet, r.sheet),
            f"{r.date[0]}/{r.date[1]:02d}/{r.date[2]:02d}" if r.date else "",  # در اکسل: عدد لاتین
            r.topic, r.teacher or r.producer, r.people, r.minutes or r.pages,
            r.place or r.platform,
            "، ".join(categories[c]["title"] for c in r.categories if c in categories),
            r.link, r.source_file, r.source_row,
        ])
    style_header(ws5)
    autosize(ws5, [18, 16, 20, 12, 40, 20, 10, 12, 22, 30, 34, 26, 6])

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
                 extra_files: Sequence[str] = ()) -> Dataset:
    ds = Dataset()
    all_files = [f for files in periods.values() for f in files]
    build_county_index()
    ds.expectations = read_expectations(list(all_files) + list(extra_files))
    if ds.expectations:
        log(f"جدول «حد انتظار» برای {persian_number(len(ds.expectations))} ناحیه خوانده شد "
            f"(از فایل کارنامه).", level="ok")

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

    # خروجی تلگرام (اختیاری): فقط برای بازخوانی متن پست‌ها و دسته‌بندی دقیق‌تر
    tg_index = read_telegram_export(telegram_paths)
    if telegram_paths and not tg_index:
        ds.warnings.append("خروجی تلگرام خوانده نشد یا خالی بود؛ دسته‌بندی بر پایه‌ی ستون‌های اکسل انجام شد.")
        log("خروجی تلگرام خوانده نشد؛ ادامه‌ی کار با ستون‌های اکسل.", level="warn")
    attach_telegram_content(ds, tg_index, log)

    ds.months = sorted({r.month_key for r in ds.records})
    ds.counties = sorted({r.county for r in ds.records if r.county})
    classify_records(ds, DEFAULT_CATEGORIES)
    return ds


def render_outputs(outdir: str, dataset: Dataset, records: Sequence[Record], report: Dict[str, Any],
                   title: str, categories: Dict[str, Dict[str, Any]], include: Sequence[str],
                   exclude: Sequence[str], months: Sequence[str], want_docx: bool,
                   want_xlsx: bool, logo_path: Optional[str], font_path: Optional[str],
                   log) -> List[str]:
    ensure_dir(outdir)
    md = report["markdown"]
    stamp = _dt.datetime.now().strftime("%Y%m%d-%H%M")
    base = os.path.join(outdir, f"{slugify_fa(title, 40)}_{stamp}")
    made: List[str] = []

    write_markdown(base + ".md", md)
    made.append(base + ".md")
    write_html(base + ".html", md, title)
    made.append(base + ".html")

    tables = [table_county(categories, include, exclude, records),
              table_sheets(records),
              table_months(dataset, records, months)]

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


def run_interactive(ui: UI) -> int:
    from pathlib import Path
    here = str(Path(__file__).resolve().parent)
    root = os.path.dirname(here)
    outdir = os.path.join(root, "گزارش‌های-ساخته‌شده")
    logpath = os.path.join(outdir, "gozaresh.log")
    ensure_dir(outdir)
    log = log_factory(logpath)

    ui.head(f"{APP_NAME}  —  نسخه {APP_VERSION}")
    ui.say("سلام. من گزارش‌یار هوشمند شما هستم. 🤖")
    ui.say("چند پرسش کوتاه می‌پرسم و سپس گزارش را می‌سازم؛ هیچ دانش برنامه‌نویسی لازم نیست.")
    ui.say("")
    ui.info(f"پوشه‌ی خروجی گزارش‌ها: {outdir}")

    # ۱) مسیر داده (با حدس هوشمند پوشه‌ی داده)
    default_input = guess_data_dir(here, root)
    ui.head("گام ۱ از ۶ — داده‌ها کجاست؟")
    ui.say("  می‌توانید: چند پوشه را با کاما جدا کنید، یا پوشه‌ها را داخل پوشه‌ی پروژه بریزید.")
    raw = ui.ask_text("مسیر پوشه(ها)" + (" — خالی = پوشه‌ی پیشنهادی" if default_input else ""),
                      default_input)
    paths = [p.strip().strip('"').strip("'") for p in re.split(r"[،,;]+", raw) if p.strip()]
    if not paths:
        ui.warn("مسیری داده نشد. پوشه‌ی فایل‌های اکسل را کنار همین برنامه بگذارید و دوباره اجرا کنید.")
        return 1

    periods, warns, json_files = discover_inputs(paths)
    if not periods:
        ui.warn("هیچ فایل اکسلی پیدا نشد. پوشه‌ها را بررسی کنید.")
        return 1
    total_files = sum(len(v) for v in periods.values())
    ui.ok(f"{persian_number(len(periods))} دوره و {persian_number(total_files)} فایل اکسل پیدا شد:")
    for p in sorted(periods):
        ui.say(f"     • {p}: {persian_number(len(periods[p]))} فایل")
    for w in warns:
        ui.warn(w)

    tg_paths: List[str] = []
    if json_files:
        ui.info("خروجی JSON تلگرام به‌طور خودکار پیدا شد: "
                + "، ".join(os.path.basename(f) for f in json_files[:3]))
        if ui.ask_yes_no("متن پست‌های این خروجی در دسته‌بندی لحاظ شود؟", default=True):
            tg_paths = json_files
    elif ui.ask_yes_no("فایل یا پوشه‌ی «Export تلگرام» هم دارید؟ (اختیاری)", default=False):
        tg_raw = ui.ask_text("مسیر فایل/پوشه‌ی JSON تلگرام (چند مورد با کاما)", "")
        tg_paths = [x.strip().strip('"').strip("'") for x in re.split(r"[،,;]+", tg_raw) if x.strip()]

    ui.say("\n  در حال خواندن فایل‌ها …")
    ds = load_dataset(periods, log, tg_paths, find_career_files(paths))
    if not ds.records:
        ui.warn("هیچ سطر داده‌ای خوانده نشد. ساختار فایل‌ها را بررسی کنید.")
        return 1
    ui.ok(f"{persian_number(len(ds.records))} سطر داده خوانده شد "
          f"({persian_number(len(ds.counties))} ناحیه، {persian_number(len(ds.months))} دوره).")
    for w in ds.warnings[:12]:
        ui.warn(w)
    if len(ds.warnings) > 12:
        ui.warn(f"و {persian_number(len(ds.warnings) - 12)} هشدار دیگر (در فایل gozaresh.log).")

    # بررسی نواحی بدون گزارش
    have = {r.county for r in ds.records}
    missing = [c for c in EXPECTED_COUNTIES if c not in have]
    if missing:
        ui.warn("بدون گزارش در این بازه: " + "، ".join(missing))

    # ۲) دوره
    ui.head("گام ۲ از ۶ — کدام دوره؟")
    month_opts = [("ALL", "همه‌ی دوره‌های موجود")] + [(m, ds.month_label(m)) for m in ds.months]
    choice = ui.multiselect("دوره(های) مورد نظر را انتخاب کنید:", month_opts,
                            defaults=[m for m in ds.months] if len(ds.months) > 1 else [ds.months[0]])
    months = ds.months if (not choice or "ALL" in choice) else [m for m in ds.months if m in choice]

    # ۳) نواحی
    ui.head("گام ۳ از ۶ — کدام نواحی؟")
    county_opts = [("ALL", "همه‌ی نواحی (پیشنهادی)")] + [(c, c) for c in ds.counties]
    choice = ui.multiselect("نواحی مورد نظر:", county_opts, defaults=["ALL"])
    counties = ds.counties if (not choice or "ALL" in choice) else [c for c in ds.counties if c in choice]

    # ۴) محورها و دسته‌ها
    ui.head("گام ۴ از ۶ — محورهای گزارش")
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
    ui.info("اقدامات بدون کلیدواژه‌ی مشخص، در بخش «سایر اقدامات» گزارش می‌شوند تا داده‌ای از قلم نیفتد.")

    # ۵) قالب گزارش
    ui.head("گام ۵ از ۶ — قالب و محدودیت‌ها")
    tpl_opts = [(str(k), f"{k}) {v}") for k, v in TEMPLATES.items()]
    tpl = ui.select("قالب گزارش را انتخاب کنید:", tpl_opts, default_index=0)
    template_id = int(tpl)

    ui.say("")
    ui.say("  اگر «درخواست مدیر» را دارید، متن آن را بچسبانید تا سقف‌های آن خودکار اعمال شود.")
    req_text = ui.ask_text("متن درخواست مدیر (اختیاری — Enter برای رد کردن)", "")
    parsed = parse_request_text(req_text)
    if parsed.get("max_words"):
        ui.info(f"از متن درخواست خوانده شد: سقف {persian_number(parsed['max_words'])} واژه")
    if req_text:
        ui.info("توجه: تطبیق دقیق دسته‌ها با متن درخواست، پس از اجرا در گام بعدی قابل انتخاب است.")

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

    # ۶) خروجی
    ui.head("گام ۶ از ۶ — فایل‌های خروجی")
    want_word = ui.ask_yes_no("فایل Word (docx) ساخته شود؟", default=True)
    want_excel = ui.ask_yes_no("فایل Excel (xlsx) ساخته شود؟", default=True)
    outdir_custom = ui.ask_text("پوشه‌ی خروجی — خالی = پیش‌فرض", outdir)
    logo = ui.ask_text("مسیر لوگو (png/jpg) — خالی = بدون لوگو", "")
    font_path = find_persian_font([root, here, os.path.dirname(root)])

    title = REPORT_TITLES.get(template_id, REPORT_TITLES[1])
    ui.say("\n  در حال ساخت گزارش …")
    records = select_records(ds, include, exclude, months, counties)
    if not records:
        ui.warn("با این فیلترها هیچ اقدامی باقی نماند. فیلترها را ساده‌تر کنید.")
        return 1

    sections = [k for k in include if k in DEFAULT_CATEGORIES]
    report = build_report(ds, records, title, sections, DEFAULT_CATEGORIES, months, counties,
                          max_words=max_words, max_chars=max_chars, details=details)

    issues = quality_check(report["markdown"], max_words, max_chars, records, exclude)
    for i in issues:
        ui.warn("کنترل کیفیت: " + i)

    made = render_outputs(outdir_custom, ds, records, report, title, DEFAULT_CATEGORIES,
                          include, exclude, months, want_word, want_excel,
                          logo if logo and os.path.exists(logo) else None, font_path, log)

    ui.head("گزارش آماده شد")
    ui.ok(f"{persian_number(len(records))} اقدام غیرروتین در گزارش آمده است.")
    ui.ok(f"تعداد واژه‌ها: {persian_number(report['word_count'])}")
    for m in made:
        ui.say("   📄 " + m)
    ui.say("\n  فایل اصلی را که می‌خواهید، همین‌جا باز کنید (Markdown/HTML برای مطالعه، "
           "Word برای ویرایش، Excel برای بررسی اعداد).")

    html_out = next((m for m in made if m.endswith(".html")), None)
    if html_out and ui.ask_yes_no("گزارش همین حالا در مرورگر باز شود؟", default=True):
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
    if not periods:
        log("هیچ فایل اکسلی پیدا نشد.", level="error")
        return 1
    tg_args = [x.strip() for group in (args.telegram or []) for x in group
               for x in re.split(r"[،,;]", x) if x.strip()]
    telegram_paths = tg_args if tg_args else json_files
    if telegram_paths and not args.telegram:
        log("خروجی JSON تلگرام به‌طور خودکار پیدا شد: "
            + "، ".join(os.path.basename(f) for f in telegram_paths[:3]))
    ds = load_dataset(periods, log, telegram_paths, find_career_files(paths))
    for w in ds.warnings:
        log(w, level="warn")
    if not ds.records:
        log("در هیچ‌یک از فایل‌ها سطر داده‌ای پیدا نشد.", level="error")
        log("راهنما: مطمئن شوید فایل‌های اکسل ماهانه (با ۵ شیت استاندارد) در پوشه‌ی داده قرار دارند؛ "
            "فایل «تهیه کارنامه نواحی» و فایل‌های خالی، داده‌ی فعالیت ندارند.", level="warn")
        return 1

    months = ds.months
    if args.month:
        wanted = [m for m in ds.months
                  if args.month in (m, ds.month_label(m), m.split("-")[1],
                                    jalali_month_name(int(m.split("-")[1])))]
        months = wanted or ds.months
    counties = [c for c in ds.counties if not args.county or county_key(args.county) in county_key(c)] \
        if args.county else ds.counties

    include = [x for x in (args.include.split(",") if args.include else
                           [k for k in DEFAULT_CATEGORIES if k not in DEFAULT_EXCLUDED]) if x]
    exclude = [x for x in (args.exclude.split(",") if args.exclude else DEFAULT_EXCLUDED) if x]
    records = select_records(ds, include, exclude, months, counties)
    if not records:
        log("با این فیلترها اقدامی باقی نماند.", level="error")
        return 1

    template_id = int(args.template or 1)
    title = args.title or REPORT_TITLES.get(template_id, REPORT_TITLES[1])
    parsed_req = parse_request_text(args.request or "")
    report = build_report(ds, records, title, include, DEFAULT_CATEGORIES, months, counties,
                          max_words=args.max_words or int(parsed_req.get("max_words", 0)),
                          max_chars=args.max_chars or int(parsed_req.get("max_chars", 0)),
                          details=bool(args.details))
    made = render_outputs(outdir, ds, records, report, title, DEFAULT_CATEGORIES, include,
                          exclude, months, not args.no_docx, not args.no_xlsx,
                          args.logo, find_persian_font([root, here]), log)
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
                   help="فایل/پوشه‌ی خروجی JSON تلگرام (برای دسته‌بندی دقیق‌تر)")
    p.add_argument("--logo", help="مسیر فایل لوگو")
    p.add_argument("--no-docx", action="store_true", help="فایل Word ساخته نشود")
    p.add_argument("--no-xlsx", action="store_true", help="فایل Excel ساخته نشود")
    p.add_argument("--version", action="version", version=f"{APP_NAME} {APP_VERSION}")
    return p


def main(argv: Optional[Sequence[str]] = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    parser = build_argparser()
    args = parser.parse_args(argv)

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
