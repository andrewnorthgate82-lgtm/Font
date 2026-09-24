#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
send_web.py — ارسال خودکار کارنامه‌های عملکرد ماهانه از طریق «نسخه وب روبیکا»
================================================================================

کار این اسکریپت:
  ۱) همه‌ی تصاویر داخل پوشه‌ی مشخص‌شده در config.json را می‌خواند
     (مثلاً «کارنامه_آران و بیدگل.png»)
  ۲) نام ناحیه/شهر را از نام فایل جدا می‌کند  («آران و بیدگل»)
  ۳) برای هر ناحیه، مخاطب‌هایی که اسمشان از ترکیبِ «نقش + ناحیه» ساخته می‌شود
     در روبیکا جستجو می‌کند  (مثلاً «مسئول نسرا آران و بیدگل»)
  ۴) تصویر(های) همان ناحیه را همراه با کپشن برای هر سه مسئول ارسال می‌کند.

پیش‌نیاز (فقط بار اول):
    pip install -U playwright
    playwright install chromium

راهنمای سریع:
    python send_web.py --dry-run                ← چک نگاشت فایل‌ها به مخاطبین (بدون ارسال)
    python send_web.py                          ← ارسال واقعی (بار اول خودتان در مرورگر لاگین می‌کنید)
    python send_web.py --only "آران و بیدگل"    ← فقط ناحیه/نواحی دلخواه
    python send_web.py --limit 3                ← فقط ۳ پیام اول (برای تست)
    python send_web.py --inspect                ← عیب‌یابی سلکتورها (اگر اسکریپت عناصر صفحه را پیدا نکرد)

نکته‌ها:
  • لاگین فقط بار اول لازم است؛ نشست مرورگر در پوشه‌ی rubika_session ذخیره می‌شود.
  • ارسال‌های موفق در sent_log.json ثبت می‌شوند؛ با اجرای دوباره، فقط موارد باقی‌مانده
    ارسال می‌شوند (اگر وسط کار قطع شد، نگران نباشید — دوباره اجرا کنید).
  • برای ارسال مجدد همه‌چیز از ابتدا:  python send_web.py --force
"""

from __future__ import annotations

import argparse
import csv
import difflib
import json
import logging
import random
import re
import sys
import time
from datetime import datetime
from pathlib import Path

BASE = Path(__file__).resolve().parent
WEB_URL = "https://web.rubika.ir/"

log = logging.getLogger("karnameh")

# ----------------------------------------------------------------------------- تنظیمات پیش‌فرض

DEFAULT_CONFIG = {
    # پوشه‌ای که تصاویر کارنامه داخل آن است (نسبی یا مسیر کامل)
    "images_dir": "کارنامه‌ها",
    # چیزی که از ابتدای نام فایل حذف می‌شود تا نام ناحیه بماند
    "filename_prefix": "کارنامه_",
    # نقش‌هایی که با نام ناحیه ترکیب می‌شوند تا نام مخاطب ساخته شود
    "roles": ["مسئول نسرا", "فرمانده گردان", "مسئول فضای مجازی"],
    # برای کپشن پیام
    "month": "شهریور ۱۴۰۵",
    "caption_template": "کارنامه عملکرد {month} ناحیه {city}",
    "send_caption": True,
    "image_extensions": [".jpg", ".jpeg", ".png", ".webp", ".bmp"],
    # فاصله بین پیام‌ها (ثانیه) — برای پرهیز از محدودیت ضداسپم روبیکا
    "min_delay_seconds": 8,
    "max_delay_seconds": 18,
    "extra_delay_between_contacts_seconds": 5,
    "headless": False,
    "login_timeout_seconds": 900,
    "verify_chat_title": True,
    # خالی = مرورگر Chromium خودِ playwright | "msedge" = Edge ویندوز | "chrome" = کروم
    "browser_channel": "",
    # مسیرهای خروجی
    "session_dir": "rubika_session",
    "sent_log": "sent_log.json",
    "overrides_csv": "overrides.csv",
    "recipients_xlsx": "مخاطبین.xlsx",
    # سلکتورهای صفحه (در صورت تغییر رابط روبیکا، اینجا را ویرایش کنید)
    "selectors": {},
}

DEFAULT_SELECTORS = {
    # نشانگرهای صفحه‌ی اصلی بعد از لاگین (هرکدام پیدا شد یعنی داخل هستیم)
    "logged_in": [
        "input[placeholder*='جستجو']",
        "[contenteditable='true']",
        "input[placeholder*='Search']",
    ],
    # دکمه‌ای که ممکن است اول باید کلیک شود تا باکس جستجو باز شود
    "search_open_button": [
        "button[aria-label*='جستجو']",
        "[class*='search' i] button",
        "[data-testid*='search']",
    ],
    # باکس جستجوی مخاطبین/گفتگوها
    "search_input": [
        "input[placeholder*='جستجو']",
        "input[placeholder*='Search']",
        "input[type='search']",
        "[contenteditable='true'][data-placeholder*='جستجو']",
    ],
    # هر آیتم در نتیجه‌های جستجو
    "search_result_item": [
        "[class*='result' i]",
        "[role='option']",
        "[class*='chatItem' i]",
        "[class*='chat-item' i]",
        "[class*='listItem' i]",
        "li[class]",
    ],
    # عنوان گفتگوی باز‌شده (برای اطمینان از اینکه گفتگوی درست باز شده)
    "chat_title": [
        "header [class*='title' i]",
        "header [class*='name' i]",
        "[class*='chat-title' i]",
        "[class*='chatTitle' i]",
        "h1",
        "h2",
    ],
    # ورودی پیام / کپشن (اولویت با ورودی داخل پنجره‌ی پیش‌نمایش تصویر است)
    "message_input": [
        "[class*='preview' i] textarea",
        "[class*='preview' i] input:not([type='file'])",
        "[class*='modal' i] [contenteditable='true']",
        "textarea[placeholder*='پیام']",
        "textarea[placeholder*='Message']",
        "input[placeholder*='پیام']",
        "[contenteditable='true'][data-placeholder*='پیام']",
        "[contenteditable='true']",
        "textarea",
    ],
    # دکمه‌ی ارسال
    "send_button": [
        "button[aria-label*='ارسال']",
        "button[title*='ارسال']",
        "[aria-label*='Send']",
        "button[title*='Send']",
    ],
    # دکمه‌ی بازگشت (برای چیدمان تک‌ستونه)
    "back_button": [
        "button[aria-label*='بازگشت']",
        "[aria-label*='Back']",
        "[class*='back' i]",
    ],
    # ورودی فایل (معمولاً مخفی است)
    "file_input": ["input[type='file']"],
}

# ----------------------------------------------------------------------------- ابزارهای عمومی


def setup_logging(log_file: str):
    fmt = logging.Formatter("%(asctime)s | %(levelname)-7s | %(message)s", "%H:%M:%S")
    log.setLevel(logging.INFO)
    sh = logging.StreamHandler(sys.stdout)
    sh.setFormatter(fmt)
    fh = logging.FileHandler(BASE / log_file, encoding="utf-8")
    fh.setFormatter(logging.Formatter("%(asctime)s | %(levelname)-7s | %(message)s"))
    log.handlers.clear()
    log.addHandler(sh)
    log.addHandler(fh)


def normalize(text: str) -> str:
    """یکسان‌سازی متن فارسی (ی/ک عربی، نیم‌فاصله، فاصله‌های تکراری و …)"""
    if not text:
        return ""
    text = text.replace("ي", "ی").replace("ك", "ک").replace("ۀ", "ه").replace("ة", "ه")
    text = text.replace("\u200c", " ").replace("\u200f", "").replace("\u200e", "")
    return re.sub(r"\s+", " ", text).strip()


def flat(text: str) -> str:
    """نسخه‌ی بدون فاصله برای مقایسه‌های سخت‌گیرانه‌تر"""
    return normalize(text).replace(" ", "")


def safe_name(text: str) -> str:
    return re.sub(r"[^\w\u0600-\u06FF]+", "_", text)[:60] or "unnamed"


def load_config(path: Path) -> dict:
    cfg = dict(DEFAULT_CONFIG)
    if path.exists():
        try:
            user = json.loads(path.read_text(encoding="utf-8-sig"))
            cfg.update(user)
        except Exception as e:
            sys.exit(f"خطا در خواندن {path}: {e}")
    else:
        # بار اول: فایل نمونه بساز تا کاربر راحت ویرایش کند
        try:
            path.write_text(
                json.dumps(DEFAULT_CONFIG, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            print(f"ℹ️ فایل تنظیمات نمونه ساخته شد: {path}")
        except Exception:
            pass
    sel = dict(DEFAULT_SELECTORS)
    for k, v in (cfg.get("selectors") or {}).items():
        if isinstance(v, list):
            sel[k] = v
    cfg["selectors"] = sel
    return cfg


# ----------------------------------------------------------------------------- دفترچه‌ی اکسل (ناحیه‌ها و شماره‌ها)

FA_DIGITS = str.maketrans("۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩", "01234567890123456789")


def digits_only(text) -> str:
    """فقط ارقامِ متن (ارقام فارسی/عربی هم به انگلیسی تبدیل می‌شوند)"""
    return "".join(ch for ch in str(text).translate(FA_DIGITS) if ch.isdigit())


def normalize_phone(raw):
    """تبدیل هر شکلی از شماره (ارقام فارسی/انگلیسی، +۹۸ / ۰۰۹۸ / ۹۸ / بدون صفر)
    به شکل استاندارد ۰۹xxxxxxxxx — اگر هیچ رقمی نبود None."""
    if raw is None or (isinstance(raw, str) and not raw.strip()):
        return None
    if isinstance(raw, float) and raw.is_integer():
        raw = int(raw)  # اکسل شماره‌ها را عددی (اعشاری) ذخیره می‌کند
    d = digits_only(raw)
    if not d:
        return None
    if d.startswith("0098"):
        d = d[4:]
    elif d.startswith("98") and len(d) > 10:
        d = d[2:]
    if len(d) == 10 and d.startswith("9"):
        d = "0" + d
    return d or None


def phone_tail(phone: str) -> str:
    """آخرین ۱۰ رقم شماره (بدون صفر ابتدایی) برای تطبیق مطمئن در نتایج جستجو"""
    d = digits_only(phone)
    return d[-10:] if len(d) >= 10 else d


def load_recipients_xlsx(path: Path, cfg: dict) -> dict:
    """خواندن فایل اکسلِ ناحیه‌ها و شماره‌ها.
    خروجی: {نام ناحیه: [مخاطب‌ها]} که مخاطب dict با کلیدهای
    key/label/phone/name/role است. ناحیه‌ای که ردیفش هست ولی شماره‌ی
    معتبری ندارد با فهرستِ خالی برمی‌گردد (یعنی آن ناحیه رد می‌شود)."""
    if not path.exists():
        # فایل نیست؛ قالب خالی بساز تا کاربر پرش کند (اگر ممکن باشد)
        if make_recipients_xlsx(path):
            print(f"📘 فایل «{path.name}» پیدا نشد؛ یک نسخه‌ی خالی ساخته شد.")
            print("   برای ارسال واقعی، شماره‌ی مسئولان را داخلش بنویسید.")
        return {}
    try:
        from openpyxl import load_workbook
    except ImportError:
        sys.exit("❌ کتابخانه‌ی خواندن اکسل (openpyxl) نصب نیست.\n"
                 "   یک بار فایل 1-نصب.bat را اجرا کنید.")
    try:
        wb = load_workbook(path, read_only=True, data_only=True)
    except PermissionError:
        sys.exit(f"❌ فایل «{path.name}» در اکسل باز است.\n"
                 "   آن را ذخیره کنید، ببندید و دوباره این فایل را اجرا کنید.")
    ws = wb.active
    rows = [list(r) for r in ws.iter_rows(values_only=True)]
    wb.close()

    # پیدا کردن سطرِ عنوان (سلولی که «نام ناحیه» در آن است)
    header_idx = 0
    for i, row in enumerate(rows[:10]):
        if any(isinstance(c, str) and "نام ناحیه" in c for c in row):
            header_idx = i
            break

    roles = cfg["roles"]
    data = {}
    for row in rows[header_idx + 1:]:
        if not row or not any(c not in (None, "") for c in row):
            continue
        city = normalize(str(row[0]).strip()) if row[0] is not None else ""
        if not city or city.startswith("#"):
            continue
        contacts = []
        for ri, role in enumerate(roles):
            name_i, phone_i = 1 + ri * 2, 2 + ri * 2
            name = (normalize(str(row[name_i]).strip())
                    if len(row) > name_i and row[name_i] is not None else "")
            raw = row[phone_i] if len(row) > phone_i else None
            phone = normalize_phone(raw)
            if raw not in (None, "") and not phone:
                print(f"   ⚠️ در اکسل، شماره‌ی «{role} {city}» خوانا نیست («{raw}») — نادیده گرفته شد")
            if phone:
                label = f"{role} {city}" + (f" — {name}" if name else "")
                contacts.append({"key": phone, "phone": phone, "name": name,
                                 "role": role, "label": label})
        # شماره‌ی تکراری در یک ناحیه = یک پیام (وقتی یک نفر دو نقش دارد)
        seen, uniq = set(), []
        for c in contacts:
            if c["phone"] in seen:
                print(f"   ℹ️ شماره‌ی «{c['label']}» تکراری است — فقط یک بار ارسال می‌شود")
                continue
            seen.add(c["phone"])
            uniq.append(c)
        data[city] = uniq
    return data


def make_placeholder_png(path: Path, w=480, h=320, rgb=(96, 140, 190)):
    """ساختن یک تصویر PNG ساده برای تست (بدون نیاز به کتابخانه‌ی تصویری)"""
    import struct, zlib

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)  # 8bit، رنگ واقعی
    raw = b"".join(b"\x00" + bytes(rgb) * w for _ in range(h))
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    path.write_bytes(png)


def ensure_test_image(cfg: dict) -> Path:
    """مطمئن شدن از وجود پوشه‌ی تصاویر و تصویرِ «ناحیه تست».
    اگر نباشند، ساخته می‌شوند تا حالت تست همیشه کار کند. خروجی: مسیر پوشه."""
    folder = Path(cfg["images_dir"])
    if not folder.is_absolute():
        folder = BASE / folder
    if not folder.is_dir():
        # شاید پوشه با کمی تفاوت در نام هست (نیم‌فاصله/فاصله)
        want = flat(folder.name)
        if folder.parent.is_dir():
            for cand in sorted(folder.parent.iterdir()):
                if cand.is_dir() and flat(cand.name) == want:
                    folder = cand
                    print(f"   ℹ️ پوشه‌ی «{cand.name}» به‌عنوان پوشه‌ی تصاویر شناخته شد")
                    break
    if not folder.is_dir():
        folder.mkdir(parents=True, exist_ok=True)
        print(f"📁 پوشه‌ی تصاویر پیدا نشد؛ ساخته شد: {folder.name}")
    prefix = cfg.get("filename_prefix", "کارنامه_")
    img = folder / f"{prefix}ناحیه تست.png"
    if not img.exists():
        make_placeholder_png(img)
        print(f"🖼 تصویر تست ساخته شد: {img.name}")
    return folder


def make_recipients_xlsx(path: Path):
    """ساختن قالبِ خالیِ فایل مخاطبین (وقتی فایل پیدا نشد)"""
    try:
        from openpyxl import Workbook
        from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
        from openpyxl.utils import get_column_letter
    except ImportError:
        return False
    try:
        wb = Workbook()
        ws = wb.active
        ws.title = "ناحیه‌ها"
        ws.sheet_view.rightToLeft = True
        headers = [
            "نام ناحیه",
            "نام و نام خانوادگی مسئول نسرا (اختیاری)",
            "شماره روبیکای مسئول نسرا",
            "نام و نام خانوادگی فرمانده گردان (اختیاری)",
            "شماره روبیکای فرمانده گردان",
            "نام و نام خانوادگی مسئول فضای مجازی (اختیاری)",
            "شماره روبیکای مسئول فضای مجازی",
        ]
        thin = Side(style="thin", color="BBBBBB")
        border = Border(left=thin, right=thin, top=thin, bottom=thin)
        for col, h in enumerate(headers, 1):
            c = ws.cell(row=1, column=col, value=h)
            c.fill = PatternFill("solid", fgColor="1F4E79")
            c.font = Font(bold=True, color="FFFFFF", size=11)
            c.border = border
            c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        a2 = ws.cell(row=2, column=1, value="ناحیه تست")
        a2.font = Font(bold=True)
        for col in range(1, 8):
            ws.cell(row=2, column=col).border = border
            ws.cell(row=2, column=col).fill = PatternFill("solid", fgColor="FFF2CC")
        for i, w in enumerate([22, 26, 22, 26, 22, 26, 22], 1):
            ws.column_dimensions[get_column_letter(i)].width = w
        ws.freeze_panes = "A2"
        ws.row_dimensions[1].height = 40
        wb.save(path)
        return True
    except Exception:
        return False


# ----------------------------------------------------------------------------- ساخت فهرست کارها


def city_from_stem(stem: str, prefix: str) -> str:
    stem, p = normalize(stem), normalize(prefix)
    if p and stem.startswith(p):
        return stem[len(p):].strip(" -_–.")
    m = re.match(r"^کارنامه[\s_\-–]*(.*)$", stem)
    if m:
        return m.group(1).strip()
    return stem


def load_overrides(path: Path) -> dict:
    """فایل اختیاری overrides.csv — برای فایل‌هایی که نام‌شان از الگو پیروی نمی‌کند.
    ستون اول: نام فایل؛ ستون‌های بعدی: نام مخاطب یا شماره همراه"""
    if not path.exists():
        return {}
    res = {}
    with path.open(encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            row = [c.strip() for c in row if c and c.strip()]
            if not row or normalize(row[0]) in ("file", "فایل", "filename"):
                continue
            res[row[0]] = row[1:]
    return res


def build_tasks(cfg: dict, only=None) -> dict:
    folder = Path(cfg["images_dir"])
    if not folder.is_absolute():
        folder = BASE / folder
    if not folder.is_dir():
        sys.exit(f"❌ پوشه تصاویر پیدا نشد: {folder}\n   (مقدار images_dir را در config.json اصلاح کنید)")
    exts = {e.lower() for e in cfg["image_extensions"]}
    files = sorted(
        (f for f in folder.iterdir() if f.is_file() and f.suffix.lower() in exts),
        key=lambda p: p.name,
    )
    if not files:
        sys.exit(f"❌ هیچ تصویری با پسوندهای {sorted(exts)} در «{folder}» پیدا نشد.")
    overrides = load_overrides(BASE / cfg["overrides_csv"])
    xlsx_path = BASE / cfg.get("recipients_xlsx", "مخاطبین.xlsx")
    xlsx = load_recipients_xlsx(xlsx_path, cfg)
    xlsx_lookup = {flat(k): v for k, v in xlsx.items()}
    if xlsx:
        print(f"📘 فایل اکسلِ مخاطبین خوانده شد: {xlsx_path.name} ({len(xlsx)} ردیف ناحیه)")
    tasks = {}
    for f in files:
        custom = overrides.get(f.name) or overrides.get(f.stem)
        if custom:
            city, names = f.stem, list(custom)
        else:
            city = city_from_stem(f.stem, cfg["filename_prefix"])
            names = [f"{role} {city}".strip() for role in cfg["roles"]]
        if only:
            wanted = [flat(o) for o in only]
            c = flat(city)
            if not any(w in c or c in w for w in wanted):
                continue
        g = tasks.setdefault(city, {"images": [], "contacts": None})
        g["images"].append(f)
        if g["contacts"] is None:
            xc = xlsx_lookup.get(flat(city))
            if xc is not None:
                # اکسل اولویت دارد: ارسال مستقیم با شماره تلفن
                g["contacts"] = xc
                if not xc:
                    print(f"   ⚠️ ناحیه «{city}» در اکسل هست ولی شماره‌ای برایش ثبت نشده — رد می‌شود")
            else:
                # بدون اکسل: جستجو با نام مخاطب (نیازمند مخاطبِ ذخیره‌شده در گوشی)
                g["contacts"] = [{"key": n, "phone": None, "name": None, "role": None, "label": n}
                                 for n in names]
    # ناحیه‌هایی که در اکسل هستند ولی تصویرِ متناظر ندارند
    if only is None:
        matched = {flat(c) for c in tasks}
        for city, cs in xlsx.items():
            if cs and flat(city) not in matched:
                print(f"   ⚠️ ناحیه «{city}» در اکسل هست ولی تصویری با نام «کارنامه_{city}» پیدا نشد")
    # ناحیه‌های بدون مخاطب را حذف کن
    for city in [c for c, g in tasks.items() if not g["contacts"]]:
        del tasks[city]
    return tasks


def make_caption(cfg: dict, city: str):
    if not cfg.get("send_caption"):
        return None
    try:
        return cfg["caption_template"].format(month=cfg.get("month", ""), city=city)
    except (KeyError, IndexError):
        return cfg["caption_template"]


def print_dry_run(cfg: dict, tasks: dict):
    total_images = sum(len(g["images"]) for g in tasks.values())
    total_msgs = sum(len(g["images"]) * len(g["contacts"]) for g in tasks.values())
    print()
    print("📋 حالت آزمایشی (dry-run) — هیچ چیزی ارسال نمی‌شود")
    print(f"   پوشه تصاویر : {Path(cfg['images_dir']) if Path(cfg['images_dir']).is_absolute() else BASE / cfg['images_dir']}")
    print(f"   ماه (کپشن)  : {cfg.get('month', '')}")
    print(f"   نقش‌ها       : {' | '.join(cfg['roles'])}")
    print(f"   تعداد ناحیه‌ها: {len(tasks)} | تصاویر: {total_images} | پیام‌های ارسالی: {total_msgs}")
    print("   " + "─" * 70)
    for i, (city, g) in enumerate(tasks.items(), 1):
        imgs = "، ".join(im.name for im in g["images"])
        print(f"\n {i}) ناحیه: «{city}»")
        print(f"    تصویر(ها): {imgs}")
        for c in g["contacts"]:
            if c["phone"]:
                nm = f" ({c['name']})" if c["name"] else ""
                print(f"    ✉ {c['role']}{nm} → 📱 {c['phone']}")
            else:
                print(f"    ✉ {c['label']} — جستجو با نام (شماره‌ای در اکسل ثبت نشده)")
    if total_msgs > 60:
        print(f"\n⚠️ {total_msgs} پیام ارسال می‌شود؛ با تأخیر پیش‌فرض حدوداً "
              f"{total_msgs * (cfg['min_delay_seconds'] + cfg['max_delay_seconds']) // 2 // 60} دقیقه طول می‌کشد.")
    print("\n✅ اگر نگاشت درست است، بدون --dry-run اجرا کنید.")


# ----------------------------------------------------------------------------- دفترچه ارسال‌ها

def load_sent(cfg: dict):
    p = BASE / cfg["sent_log"]
    records = []
    if p.exists():
        try:
            records = json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            records = []
    return {(r["image"], r["contact"]) for r in records}, records


def save_sent(cfg: dict, records: list):
    p = BASE / cfg["sent_log"]
    p.write_text(json.dumps(records, ensure_ascii=False, indent=1), encoding="utf-8")


# ----------------------------------------------------------------------------- ابزارهای Playwright


def find_locator(page, selectors, timeout=5000):
    """اولین عنصرِ موجود و مرئی از بین سلکتورهای کاندید را برمی‌گرداند (یا None)."""
    deadline = time.time() + timeout / 1000
    while True:
        for sel in selectors:
            try:
                loc = page.locator(sel)
                if loc.count() > 0 and loc.first.is_visible():
                    return loc.first
            except Exception:
                pass
        if time.time() >= deadline:
            return None
        page.wait_for_timeout(300)


def type_text(loc, text, delay=70):
    try:
        loc.press_sequentially(text, delay=delay)
    except AttributeError:
        loc.type(text, delay=delay)


def clear_input(loc):
    try:
        loc.fill("")
    except Exception:
        try:
            loc.press("Control+a")
            loc.press("Delete")
        except Exception:
            pass


def input_text_of(loc) -> str:
    try:
        tag = loc.evaluate("el => el.tagName.toLowerCase()")
        if tag in ("input", "textarea"):
            return loc.input_value() or ""
        return loc.evaluate("el => (el.innerText || '')")
    except Exception:
        return ""


def screenshot(page, name):
    d = BASE / "debug"
    d.mkdir(exist_ok=True)
    try:
        page.screenshot(path=str(d / safe_name(name) + ".png"), full_page=True)
    except Exception:
        pass


def wait_for_login(page, S, timeout_s) -> bool:
    print("⏳ در انتظار ورود به روبیکا…")
    print("   اگر صفحه‌ی ورود باز شده، با شماره همراه و کد تأیید وارد شوید (فقط بار اول).")
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        for sel in S["logged_in"]:
            try:
                if page.locator(sel).count() > 0:
                    print("✅ ورود تأیید شد.")
                    return True
            except Exception:
                pass
        time.sleep(1.5)
    return False


def pick_result(page, contact, S, min_score=0.55):
    """بهترین نتیجه‌ی جستجو را بر اساس شباهتِ نام مخاطب انتخاب می‌کند."""
    want = flat(contact)
    best, best_score = None, 0.0
    for sel in S["search_result_item"]:
        try:
            items = page.locator(sel)
            count = min(items.count(), 30)
        except Exception:
            continue
        for i in range(count):
            it = items.nth(i)
            try:
                if not it.is_visible():
                    continue
                txt = normalize(it.inner_text(timeout=700))
            except Exception:
                continue
            if not txt:
                continue
            ftxt = flat(txt)[:150]
            score = difflib.SequenceMatcher(None, want, ftxt).ratio()
            if want and want in ftxt:  # نام کامل مخاطب داخل متن نتیجه هست
                score = 1.0
            if score > best_score:
                best, best_score = it, score
        if best and best_score >= 0.999:
            break
    return best if best_score >= min_score else None


def verify_chat_title(page, contact, S):
    """بررسی اینکه گفتگوی بازشده همان مخاطب است.
    خروجی: (True/False/None، متنِ عنوانِ پیدا‌شده) — None یعنی عنوانی پیدا نشد."""
    want = flat(contact)
    for sel in S["chat_title"]:
        try:
            loc = page.locator(sel)
            for i in range(min(loc.count(), 8)):
                el = loc.nth(i)
                if not el.is_visible():
                    continue
                t = normalize(el.inner_text(timeout=700))
                if t and len(t) < 80:  # عنوان باید کوتاه باشد، نه کل هدر
                    return (want in flat(t)), t
        except Exception:
            continue
    return None, None


def get_search_box(page, S):
    """پیدا کردن باکس جستجو (اگر لازم بود دکمه‌ی جستجو/بازگشت را می‌زند)"""
    box = find_locator(page, S["search_input"], timeout=6000)
    if box:
        return box
    # شاید اول باید دکمه‌ی جستجو را زد
    btn = find_locator(page, S["search_open_button"], timeout=1500)
    if btn:
        try:
            btn.click()
            page.wait_for_timeout(500)
        except Exception:
            pass
    # در چیدمان تک‌ستونه شاید لازم باشد اول «بازگشت» بزنیم
    back = find_locator(page, S["back_button"], timeout=1000)
    if back:
        try:
            back.click()
            page.wait_for_timeout(500)
        except Exception:
            pass
    return find_locator(page, S["search_input"], timeout=4000)


def open_chat(page, contact, S, cfg) -> tuple:
    """جستجوی مخاطب و باز کردن گفتگو با او. خروجی: (موفق؟, پیام خطا)"""
    box = get_search_box(page, S)
    if not box:
        return False, "باکس جستجو پیدا نشد (سلکتور search_input را در config.json اصلاح کنید — از --inspect کمک بگیرید)"

    try:
        box.click()
        page.wait_for_timeout(300)
        clear_input(box)
        print(f"   ↻ جستجوی «{contact}» ...")
        type_text(box, contact)
        page.wait_for_timeout(1700)  # منتظر بارگذاری نتیجه‌ها
    except Exception as e:
        return False, f"خطا در تایپ در جستجو: {e}"

    # راه ۱: کلیک روی نتیجه‌ی منطبق (امن‌ترین)
    item = pick_result(page, contact, S)
    if item:
        try:
            print("   ✅ مخاطب در نتیجه‌های جستجو پیدا شد")
            item.click()
            page.wait_for_timeout(1000)
        except Exception as e:
            return False, f"کلیک روی نتیجه ناموفق بود: {e}"
    else:
        # راه ۲: زدن Enter (اولین نتیجه باز می‌شود) — فقط وقتی نتایج هست
        print("   ↻ نتیجه‌ی دقیق پیدا نشد؛ اولین نتیجه را باز می‌کنیم")
        try:
            box.press("Enter")
            page.wait_for_timeout(1200)
        except Exception:
            pass

    # آیا گفتگویی باز شد؟ (باید ورودی پیام دیده شود)
    if not find_locator(page, S["message_input"], timeout=6000):
        return False, "مخاطب در نتیجه‌های جستجو پیدا نشد (نام ذخیره‌شده در دفترچه تلفن را چک کنید)"
    print("   ✅ گفتگو باز شد")

    # بررسی ایمنی: عنوان گفتگو باید شامل نام مخاطب باشد
    if cfg.get("verify_chat_title", True):
        ok, seen_title = verify_chat_title(page, contact, S)
        if ok is False:
            if cfg.get("_self_test"):
                # در حالت تست، مخاطبِ شماره‌ی خودِ کاربر است؛ روبیکا ممکن است عنوان را
                # با نام پروفایل نشان دهد نه نام دفترچه تلفن — ادامه می‌دهیم.
                print(f"   ⚠️ عنوان گفتگو «{seen_title}» با نام مخاطب نمی‌خواند — "
                      "چون حالت تست است، ادامه می‌دهیم")
            else:
                return False, (f"عنوان گفتگوی بازشده «{seen_title}» با نام مخاطب "
                               f"«{contact}» نمی‌خواند (برای اطمینان ارسال نشد)")
        elif ok is None:
            print("   ℹ️ عنوان گفتگو پیدا نشد؛ بدون بررسی عنوان ادامه می‌دهیم")
    return True, None


def search_results(page, S, limit=30):
    """فهرستِ نتایجِ قابلِ دیدنِ جستجو: [(locator, متن)]"""
    out = []
    for sel in S["search_result_item"]:
        try:
            items = page.locator(sel)
            count = min(items.count(), limit)
        except Exception:
            continue
        for i in range(count):
            it = items.nth(i)
            try:
                if not it.is_visible():
                    continue
                txt = normalize(it.inner_text(timeout=700))
            except Exception:
                continue
            if txt:
                out.append((it, txt))
        if out:
            break  # اولین سلکتوری که نتیجه داد کافی است
    return out


def pick_result_by_phone(page, phone, S):
    """نتیجه‌ی جستجو که «شماره» در متنش هست را برمی‌گرداند (یا None)"""
    tail = phone_tail(phone)
    if not tail:
        return None
    for it, txt in search_results(page, S):
        if tail in digits_only(txt):
            return it
    return None


def dump_search_dom(page, phone):
    """ذخیره‌ی وضعیت صفحه بعد از جستجوی شماره — برای عیب‌یابیِ نتیجه‌های جستجو"""
    d = BASE / "debug"
    d.mkdir(exist_ok=True)
    try:
        data = page.evaluate(
            """() => {
                const out = {url: location.href,
                             anyText: (document.body.innerText||'').replace(/\\s+/g,' ').slice(0,600),
                             items: []};
                const sels = ['[class*="result" i]','[role="option"]','li',
                              '[class*="item" i]','[class*="chat" i]','[class*="list" i]'];
                const seen = new Set();
                for (const s of sels){
                    for (const el of Array.from(document.querySelectorAll(s)).slice(0,40)){
                        const t = (el.innerText||'').replace(/\\s+/g,' ').trim();
                        if (!t || t.length>300 || seen.has(t)) continue;
                        seen.add(t);
                        out.items.push({sel: s,
                                        cls: (el.className && el.className.toString ? el.className.toString() : '').slice(0,100),
                                        text: t.slice(0,120)});
                        if (out.items.length>=40) return out;
                    }
                }
                return out;
            }"""
        )
        (d / "dom_search_number.json").write_text(
            json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    except Exception as e:
        print(f"   ⚠️ استخراج DOM ناموفق: {e}")


def open_chat_by_phone(page, phone, S) -> tuple:
    """باز کردن گفتگو با «جستجوی شماره تلفن» — بدون نیاز به مخاطب ذخیره‌شده.
    خروجی: (موفق؟, پیام خطا)"""
    box = get_search_box(page, S)
    if not box:
        return False, "باکس جستجو پیدا نشد (سلکتور search_input را در config.json اصلاح کنید — از --inspect کمک بگیرید)"

    variants = [phone, "98" + phone[1:], "+98" + phone[1:]]
    for v in variants:
        try:
            box.click()
            page.wait_for_timeout(300)
            clear_input(box)
            print(f"   ↻ جستجوی شماره {v} ...")
            type_text(box, v)
            page.wait_for_timeout(2200)  # منتظر بارگذاری نتیجه‌ها
        except Exception as e:
            return False, f"خطا در تایپ در جستجو: {e}"

        # راه ۱: نتیجه‌ای که شماره در متنش هست (مطمئن‌ترین)
        item = pick_result_by_phone(page, phone, S)
        if item:
            try:
                print("   ✅ شماره در نتیجه‌های جستجو پیدا شد")
                item.click()
                page.wait_for_timeout(1000)
            except Exception as e:
                return False, f"کلیک روی نتیجه ناموفق بود: {e}"
        else:
            results = search_results(page, S)
            if len(results) == 1:
                # جستجوی یک شماره‌ی کامل که فقط یک نتیجه دارد = همان شخص
                it, txt = results[0]
                print(f"   ⚠️ فقط یک نتیجه دیده شد («{txt[:40]}…»)؛ همان باز می‌شود")
                try:
                    it.click()
                    page.wait_for_timeout(1000)
                except Exception as e:
                    return False, f"کلیک روی نتیجه ناموفق بود: {e}"
            else:
                continue  # شکلِ دیگر شماره را امتحان کن

        # آیا گفتگویی باز شد؟ (باید ورودی پیام دیده شود)
        if not find_locator(page, S["message_input"], timeout=6000):
            return False, f"نتیجه کلیک شد ولی گفتگوی شماره {phone} باز نشد"
        print("   ✅ گفتگو باز شد")
        return True, None

    # عیب‌یابی: چه چیزهایی در فهرستِ نتیجه‌ها دیده می‌شود؟
    results = search_results(page, S)
    print(f"   🔎 عیب‌یابی: اسکریپت بعد از تایپ شماره، {len(results)} مورد در فهرست نتیجه‌ها دید")
    for it, txt in results[:3]:
        print(f"      • «{txt[:60]}»")
    dump_search_dom(page, phone)
    print("   💾 گزارش کامل در debug/dom_search_number.json ذخیره شد")
    return False, (f"شماره {phone} در نتیجه‌های جستجوی روبیکا پیدا نشد "
                   "(شماره را در اکسل چک کنید)")


def find_attach_input_index(page, S):
    """اندیس input[type=file] مربوط به پیوستِ گفتگو (نزدیک‌ترین به محل تایپ پیام)."""
    for sel in S["message_input"]:
        try:
            idx = page.evaluate(
                """(sel) => {
                    const el = document.querySelector(sel);
                    if (!el) return -1;
                    let node = el;
                    while (node && node !== document.body) {
                        if (!node.querySelectorAll) { node = node.parentElement; continue; }
                        const inp = node.querySelectorAll('input[type=file]');
                        if (inp.length) {
                            const all = Array.from(document.querySelectorAll('input[type=file]'));
                            return all.indexOf(inp[0]);
                        }
                        node = node.parentElement;
                    }
                    return -1;
                }""",
                sel,
            )
            if isinstance(idx, int) and idx >= 0:
                return idx
        except Exception:
            continue
    return None


def attach_file(page, image: Path, S) -> tuple:
    """انتخاب فایل تصویر برای ارسال — اول از ورودی مخفی فایل، بعد از دکمه پیوست."""
    # ۱) ورودی‌های مخفیِ type=file
    try:
        inputs = page.locator("input[type='file']")
        cnt = inputs.count()
        if cnt > 0:
            idx = find_attach_input_index(page, S)
            if idx is None:
                idx = cnt - 1
                for i in range(cnt):
                    acc = inputs.nth(i).get_attribute("accept") or ""
                    if "image" in acc.lower():
                        idx = i
                        break
            inputs.nth(idx).set_input_files(str(image))
            page.wait_for_timeout(2500)  # منتظر پیش‌نمایش
            return True, None
    except Exception as e:
        log.debug("set_input_files failed: %s", e)

    # ۲) کلیک روی دکمه پیوست و گرفتن پنجره انتخاب فایل
    attach_candidates = [
        "button[aria-label*='پیوست']",
        "button[aria-label*='فایل']",
        "button[title*='فایل']",
        "[aria-label*='Attach']",
        "[aria-label*='File']",
        "[class*='attach' i]",
        "[class*='paperclip' i]",
        "[class*='clip' i]",
    ]
    try:
        with page.expect_file_chooser(timeout=4000) as fc:
            for sel in attach_candidates:
                btn = find_locator(page, [sel], timeout=700)
                if btn:
                    btn.click()
                    break
            else:
                raise RuntimeError("دکمه پیوست پیدا نشد")
        fc.value.set_files(str(image))
        page.wait_for_timeout(2500)
        return True, None
    except Exception as e:
        return False, f"نتوانستیم فایل را پیوست کنیم: {e}"


def send_image(page, image: Path, caption, S) -> tuple:
    """ارسال یک تصویر (با کپشن) در گفتگوی باز. خروجی: (موفق؟, پیام خطا)"""
    print(f"   ↻ پیوست تصویر «{image.name}» ...")
    ok, err = attach_file(page, image, S)
    if not ok:
        return False, err
    print("   ✅ تصویر پیوست شد")

    cap = find_locator(page, S["message_input"], timeout=10000)
    if not cap:
        return False, "ورودی پیام/کپشن بعد از پیوست تصویر پیدا نشد"
    try:
        clear_input(cap)
        if caption:
            print("   ↻ نوشتن متن زیر عکس ...")
            type_text(cap, caption, delay=25)
            page.wait_for_timeout(400)
    except Exception as e:
        return False, f"تایپ کپشن ناموفق بود: {e}"

    # ارسال: اول دکمه، در غیر این صورت Enter
    print("   ↻ ارسال ...")
    btn = find_locator(page, S["send_button"], timeout=2500)
    sent_via = None
    if btn:
        try:
            btn.click()
            sent_via = "دکمه ارسال"
        except Exception:
            btn = None
    if not btn:
        try:
            cap.press("Enter")
            sent_via = "Enter"
        except Exception as e:
            return False, f"نه دکمه ارسال پیدا شد نه Enter کار کرد: {e}"
    print(f"   ✅ ارسال شد (با {sent_via})")

    page.wait_for_timeout(2500)

    # بررسی: ورودی پیام باید خالی شده باشد (نشانه‌ی ارسال)
    now_empty = False
    try:
        cur = find_locator(page, S["message_input"], timeout=2500)
        if cur:
            now_empty = input_text_of(cur).strip() == "" or (
                caption and input_text_of(cur).strip() != caption
            )
    except Exception:
        pass
    if not now_empty:
        # یک تلاش دیگر با Enter
        try:
            cur = find_locator(page, S["message_input"], timeout=2500)
            if cur and caption and input_text_of(cur).strip() == caption:
                cur.press("Enter")
                page.wait_for_timeout(2500)
        except Exception:
            pass
        log.warning("⚠️ اطمینان کامل از ارسال حاصل نشد (%s) — لطفاً در مرورگر چک کنید", image.name)
    log.info("   ارسال با %s", sent_via)
    return True, None


# ----------------------------------------------------------------------------- حالت عیب‌یابی


def dump_dom(page, out_path: Path):
    try:
        data = page.evaluate(
            """() => {
                const pick = (el) => ({
                    tag: el.tagName.toLowerCase(),
                    type: el.getAttribute('type') || '',
                    placeholder: el.getAttribute('placeholder') || el.getAttribute('data-placeholder') || '',
                    aria: el.getAttribute('aria-label') || '',
                    title: el.getAttribute('title') || '',
                    id: el.id || '',
                    cls: (el.className && el.className.toString ? el.className.toString() : '').slice(0, 120),
                    text: (el.innerText || '').replace(/\\s+/g, ' ').slice(0, 80),
                });
                return Array.from(
                    document.querySelectorAll('input, textarea, [contenteditable="true"], button, [role="button"], header')
                ).slice(0, 400).map(pick);
            }"""
        )
        out_path.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    except Exception as e:
        print(f"⚠️ استخراج DOM ناموفق: {e}")


def run_inspect(cfg, S):
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        sys.exit(
            "❌ کتابخانه‌ی playwright نصب نیست. این دو دستور را اجرا کنید:\n"
            "     pip install -U playwright\n"
            "     playwright install chromium"
        )

    dbg = BASE / "debug"
    dbg.mkdir(exist_ok=True)
    with sync_playwright() as p:
        ctx = launch(p, cfg)
        page = ctx.pages[0] if ctx.pages else ctx.new_page()
        page.goto(WEB_URL, wait_until="domcontentloaded", timeout=90000)
        if not wait_for_login(page, S, cfg["login_timeout_seconds"]):
            print("⌛ لاگین انجام نشد؛ دوباره تلاش کنید.")
            return
        page.wait_for_timeout(2000)
        print("\n🔍 [حالت عیب‌یابی]")
        input("۱) یک گفتگو را باز کنید (مثلاً با یکی از مسئولان) و بعد Enter بزنید… ")
        dump_dom(page, dbg / "dom_chat_open.json")
        screenshot(page, "chat_open")
        name = input("۲) برای تست جستجو، نام یک مخاطب را وارد کنید (یا فقط Enter): ").strip()
        if name:
            box = find_locator(page, S["search_input"], timeout=5000)
            if not box:
                print("❌ باکس جستجو پیدا نشد — dom_main.json را برای تنظیم سلکتورها بفرستید.")
                dump_dom(page, dbg / "dom_main.json")
                screenshot(page, "main_page")
                ctx.close()
                return
            box.click()
            clear_input(box)
            type_text(box, name)
            page.wait_for_timeout(2000)
            screenshot(page, "search_results")
            dump_dom(page, dbg / "dom_search.json")
        print("\n✅ خروجی‌ها در پوشه‌ی debug ذخیره شد:")
        print("   dom_chat_open.json, dom_search.json, chat_open.png, search_results.png")
        print("   اگر اسکریپت چیزی پیدا نمی‌کرد، این فایل‌ها را برای سازنده بفرستید تا سلکتورها دقیق شوند.")
        ctx.close()


# ----------------------------------------------------------------------------- اجرای اصلی


def launch(p, cfg):
    """راه‌اندازی مرورگر.
    ترتیب تلاش: کانالِ تنظیم‌شده در config.json ← Chromiumِ دانلودیِ playwright
    ← Edge خودِ ویندوز ← Chrome.
    (اگر دانلود Chromium در نصب ناموفق بوده باشد، خودکار از Edge استفاده می‌شود.)"""
    common = dict(
        user_data_dir=str(BASE / cfg["session_dir"]),
        headless=cfg.get("headless", False),
        locale="fa-IR",
        timezone_id="Asia/Tehran",
        viewport={"width": 1400, "height": 900},
        args=["--disable-blink-features=AutomationControlled"],
    )
    channel = (cfg.get("browser_channel") or "").strip()
    attempts = ([channel] if channel else []) + [None, "msedge", "chrome"]
    attempts = [a for i, a in enumerate(attempts) if a not in attempts[:i]]  # بدون تکرار
    last_err = None
    for ch in attempts:
        try:
            if ch:
                print(f"   ↻ تلاش برای باز کردن مرورگر {ch} ...")
                ctx = p.chromium.launch_persistent_context(channel=ch, **common)
            else:
                ctx = p.chromium.launch_persistent_context(**common)
            if ch:
                log.info("مرورگر %s با موفقیت باز شد", ch)
            return ctx
        except Exception as e:
            last_err = e
            continue  # مرورگر بعدی را امتحان کن
    raise SystemExit(
        "❌ هیچ مرورگری برای اجرای اسکریپت پیدا نشد.\n"
        "   - مطمئن شوید مرورگر Edge یا Chrome روی ویندوز نصب است، یا\n"
        "   - این دستور را اجرا کنید:  playwright install chromium\n"
        f"   جزئیات خطا: {str(last_err)[:150]}"
    )


def run_sending(cfg, S, tasks, args):
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        sys.exit(
            "❌ کتابخانه‌ی playwright نصب نیست. این دو دستور را اجرا کنید:\n"
            "     pip install -U playwright\n"
            "     playwright install chromium"
        )

    sent, records = load_sent(cfg)
    if args.force:
        sent = set()
    stats = {"ok": 0, "fail": 0, "skip": 0}
    failures = []
    budget = args.limit if args.limit else None

    print(f"\n🚀 شروع ارسال — {sum(len(g['images']) for g in tasks.values())} تصویر برای "
          f"{sum(len(g['contacts']) for g in tasks.values())} مخاطبِ {len(tasks)} ناحیه")
    if sent:
        print(f"   {len(sent)} مورد قبلاً ارسال شده و رد می‌شود (برای تکرار: --force)")

    with sync_playwright() as p:
        ctx = launch(p, cfg)
        page = ctx.pages[0] if ctx.pages else ctx.new_page()
        try:
            page.goto(WEB_URL, wait_until="domcontentloaded", timeout=90000)
        except Exception as e:
            log.warning("باز شدن صفحه: %s", e)
        if not wait_for_login(page, S, cfg["login_timeout_seconds"]):
            print("⌛ زمان انتظار لاگین تمام شد. دوباره اجرا کنید (لاگین شما ذخیره شده است).")
            ctx.close()
            return
        page.wait_for_timeout(3000)
        screenshot(page, "after_login")

        seq = 0
        stop = False
        try:
            for city, g in tasks.items():
                if stop:
                    break
                print(f"\n🏙 ناحیه: {city}")
                for contact in g["contacts"]:
                    if stop:
                        break
                    ckey = contact["key"]
                    todo = [im for im in g["images"] if (im.name, ckey) not in sent]
                    if not todo:
                        stats["skip"] += 1
                        print(f"   ↷ {contact['label']} — قبلاً ارسال شده، رد شد")
                        continue
                    if budget is not None and stats["ok"] >= budget:
                        stop = True
                        print(f"   ⏹ به سقف --limit ({budget} پیام) رسیدیم؛ ادامه با اجرای دوباره")
                        break
                    seq += 1
                    how = contact["phone"] if contact["phone"] else "جستجو با نام"
                    print(f"   [{seq}] {contact['label']} — {how}")
                    if contact["phone"]:
                        ok, err = open_chat_by_phone(page, contact["phone"], S)
                        if not ok and cfg.get("_self_test"):
                            # در حالت تست: روبیکا شماره‌ی خودِ کاربر را با جستجوی شماره
                            # نشان نمی‌دهد؛ با نامِ مخاطبِ ذخیره‌شده در گوشی امتحان می‌کنیم
                            print(f"   ⤵ شماره پیدا نشد؛ تلاش با نام مخاطبِ «{contact['label']}» ...")
                            ok, err = open_chat(page, contact["label"], S, cfg)
                    else:
                        ok, err = open_chat(page, contact["label"], S, cfg)
                    if not ok:
                        stats["fail"] += 1
                        failures.append({"contact": contact["label"], "stage": "باز کردن گفتگو", "error": err})
                        log.error("   ❌ %s | %s", contact["label"], err)
                        screenshot(page, f"{seq:03d}_open_fail_{safe_name(contact['label'])}")
                        page.wait_for_timeout(1500)
                        continue
                    for im in todo:
                        if budget is not None and stats["ok"] >= budget:
                            stop = True
                            break
                        cap = make_caption(cfg, city)
                        ok2, err2 = send_image(page, im, cap, S)
                        if ok2:
                            sent.add((im.name, ckey))
                            records.append({
                                "image": im.name, "contact": contact["label"],
                                "time": datetime.now().isoformat(timespec="seconds"),
                            })
                            save_sent(cfg, records)
                            stats["ok"] += 1
                            log.info("   ✅ %s → %s", im.name, contact["label"])
                        else:
                            stats["fail"] += 1
                            failures.append({"contact": contact["label"], "image": im.name,
                                             "stage": "ارسال تصویر", "error": err2})
                            log.error("   ❌ %s → %s | %s", im.name, contact["label"], err2)
                            screenshot(page, f"{seq:03d}_send_fail_{safe_name(contact['label'])}")
                        page.wait_for_timeout(random.randint(2500, 4500))
                    if not stop:
                        page.wait_for_timeout(random.randint(
                            cfg["min_delay_seconds"] * 1000, cfg["max_delay_seconds"] * 1000))
                if not stop:
                    page.wait_for_timeout(int(cfg["extra_delay_between_contacts_seconds"]) * 1000)
        except KeyboardInterrupt:
            print("\n⛔ متوقف شد (Ctrl+C). ارسال‌های انجام‌شده ثبت شدند؛ با اجرای دوباره ادامه می‌یابد.")
        finally:
            save_sent(cfg, records)
            try:
                ctx.close()
            except Exception:
                pass

    print("\n" + "═" * 60)
    print(f"📊 نتیجه: ✅ {stats['ok']} ارسال شد | ❌ {stats['fail']} ناموفق | ↷ {stats['skip']} رد شد")
    # خط انگلیسی برای وقتی که کنسول فارسی را درست نشان نمی‌دهد
    print(f"RESULT: sent={stats['ok']}  failed={stats['fail']}  skipped={stats['skip']}")
    if failures:
        (BASE / "debug").mkdir(exist_ok=True)
        (BASE / "debug" / "failures.json").write_text(
            json.dumps(failures, ensure_ascii=False, indent=2), encoding="utf-8")
        print("   موارد ناموفق در debug/failures.json و اسکرین‌شات‌ها در debug/ ذخیره شد.")
        for f in failures[:10]:
            print(f"   • {f['contact']}: {f['error']}")
        if len(failures) > 10:
            print(f"   … و {len(failures) - 10} مورد دیگر")
    if stats["fail"] > 3:
        print("\n💡 اگر خطاها به‌خاطر «پیدا نشدن» عناصر صفحه است، از --inspect استفاده کنید")
        print("   و فایل‌های پوشه‌ی debug را برای تنظیم دقیق سلکتورها بفرستید.")


def main():
    if sys.stdout and hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8")
        except Exception:
            pass

    ap = argparse.ArgumentParser(
        description="ارسال خودکار کارنامه‌های عملکرد از طریق نسخه وب روبیکا",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--config", default="config.json", help="مسیر فایل تنظیمات (پیش‌فرض: config.json)")
    ap.add_argument("--dry-run", action="store_true", help="فقط نمایش نگاشت فایل ← مخاطبین، بدون ارسال")
    ap.add_argument("--inspect", action="store_true", help="حالت عیب‌یابی سلکتورها")
    ap.add_argument("--only", nargs="+", metavar="ناحیه", help="فقط این ناحیه/نواحی ارسال شود")
    ap.add_argument("--limit", type=int, metavar="N", help="حداکثر N پیام در این اجرا (برای تست)")
    ap.add_argument("--force", action="store_true", help="بی‌توجه به سابقه، همه دوباره ارسال شود")
    ap.add_argument("--month", metavar="نام‌ماه", help="ماه را برای کپشن بازنویسی کند")
    ap.add_argument("--self-test", action="store_true",
                    help="حالت تست: فقط یک پیام آزمایشی برای «ناحیه تست» می‌فرستد")
    ap.add_argument("--headless", action="store_true", help="بدون پنجره‌ی مرورگر (پیشنهاد نمی‌شود)")
    args = ap.parse_args()

    if args.self_test:
        print("\n🧪 حالت تست — یک پیام آزمایشی برای «ناحیه تست» ارسال می‌شود")
        print("   گیرنده: شماره‌ای که در فایل اکسلِ «مخاطبین.xlsx» برای «ناحیه تست» نوشته‌اید")
        print("   (اگر آن ردیف خالی باشد، با نام مخاطبِ ذخیره‌شده در گوشی جستجو می‌شود)\n")
        args.only = ["ناحیه تست"]
        if not args.limit:
            args.limit = 1

    cfg = load_config(BASE / args.config)
    if args.month:
        cfg["month"] = args.month
    if args.headless:
        cfg["headless"] = True
    if args.self_test:
        cfg["_self_test"] = True  # در حالت تست، بررسی عنوان گفتگو سخت‌گیرانه نیست
        # پوشه‌ی تصاویر و تصویر تست در صورت نبود، خودکار ساخته می‌شوند
        cfg["images_dir"] = str(ensure_test_image(cfg))
    S = cfg["selectors"]
    setup_logging("karnameh_send.log")

    tasks = build_tasks(cfg, only=args.only)
    if not tasks:
        if args.self_test:
            sys.exit("هیچ ناحیه‌ای مطابق فیلتر انتخابی پیدا نشد.\n"
                     "💡 برای تست: فایل مخاطبین.xlsx را باز کنید و در ردیفِ «ناحیه تست»،\n"
                     "   شماره‌ی موبایل خودتان را در ستون «شماره روبیکای مسئول نسرا» بنویسید،\n"
                     "   ذخیره کنید، ببندید و دوباره این فایل را اجرا کنید.")
        sys.exit("هیچ ناحیه‌ای مطابق فیلتر انتخابی پیدا نشد.")

    if args.self_test:
        g = next(iter(tasks.values()))
        c = g["contacts"][0]
        if c["phone"]:
            print(f"   📱 گیرنده‌ی تست: شماره {c['phone']}")
        else:
            print(f"   ⚠️ در اکسل شماره‌ای برای «ناحیه تست» نیست؛ با نام «{c['label']}» جستجو می‌شود")
            print("      💡 بهتر است شماره‌ی خودتان را در فایل مخاطبین.xlsx وارد کنید")
        print()

    if args.dry_run:
        print_dry_run(cfg, tasks)
        return
    if args.inspect:
        run_inspect(cfg, S)
        return
    run_sending(cfg, S, tasks, args)


if __name__ == "__main__":
    main()
