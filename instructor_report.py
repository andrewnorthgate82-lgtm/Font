# -*- coding: utf-8 -*-
"""
گزارش‌گیری از مدرسان / سخنرانان یک فایل اکسل چندشیتی
=====================================================
این اسکریپت یک فایل اکسل (با چند شیت) می‌گیرد، در همه شیت‌ها ستون
«نام سخنران / مدرس» (یا «نام تولید کننده محتوا» در شیت‌های تولیدات)
را پیدا می‌کند، اسامی را یکسان‌سازی و تجمیع می‌کند
و در خروجی یک فایل اکسل قالب‌بندی‌شده تولید می‌کند که شامل:

  ▸ شیت «خلاصه مدرسان»: لیست مدرسان + تعداد کلاس + مجموع دقیقه +
    مجموع نفرات + شیت‌های فعالیت — مرتب‌شده بر اساس تعداد کلاس (نزولی)
  ▸ شیت «جزئیات کلاس‌ها»: ریز تمام کلاس‌های هر مدرس (شیت، تاریخ، موضوع، ...)

نحوه اجرا:
    python3 instructor_report.py "فایل ورودی.xlsx"
    python3 instructor_report.py "فایل ورودی.xlsx" -o "گزارش مدرسان.xlsx"

پیش‌نیاز:
    pip install pandas openpyxl
"""

import argparse
import os
import re
import sys
import unicodedata
from collections import OrderedDict

import openpyxl
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

# ---------------------------------------------------------------- تنظیمات ---

# تشخیص ستون فرد (مدرس / سخنران / تولیدکننده محتوا) در سطر عنوان
def is_instructor_header(text):
    """آیا این عنوان، ستون شخص (مدرس/سخنران/تولیدکننده محتوا) است؟

    نکته: ستون «تولید کننده» به‌تنهایی (بدون «محتوا») معمولاً نهاد برگزارکننده
    است، نه شخص؛ بنابراین فقط وقتی «محتوا» هم در عنوان باشد فرد محسوب می‌شود.
    """
    if not text:
        return False
    if "سخنران" in text or "مدرس" in text:
        return True
    if "محتوا" in text and "تولید" in text and "کنند" in text:
        return True
    return False


# نگاشت عنوان‌های شناخته‌شده برای ستون‌های کمکی (برای شیت جزئیات و جمع‌ها)
AUX_COLUMNS = {
    "تاریخ": ("تاریخ",),
    "برگزارکننده": ("برگزار کننده", "برگزارکننده", "نام ناحیه", "تولید کننده", "تولیدکننده"),
    "موضوع": ("موضوع",),
    "مدت (دقیقه)": ("مدت", "دقیقه"),
    "تعداد نفرات": ("تعداد نفرات", "نفرات", "تعداد شرکت"),
    "مکان / بستر برگزاری": ("مکان برگزاری", "بستر برگزاری", "مکان", "بستر"),
    "نوع کلاس": ("نوع کلاس", "نوع تولید"),
    "لینک مستندات": ("لینک", "مستندات"),
}

# جداکننده‌هایی که وقتی چند مدرس در یک سلول نوشته شده باشد، اسم‌ها را جدا می‌کنند
NAME_SPLIT_RE = re.compile(r"[،؛,;/\n\r]+|\s+-\s+|\s+و\s+(?=\S{3,})")

MAX_HEADER_SCAN_ROWS = 15  # حداکثر سطرهایی که برای پیدا کردن سطر عنوان جستجو می‌شود


# ------------------------------------------------------- یکسان‌سازی اسم‌ها ---

_ZW_CHARS = re.compile(r"[\u200b\u200e\u200f\ufeff]")           # نویسه‌های نامرئی
_NIM_FASELE = "\u200c"                                          # نیم‌فاصله

_AR2FA = str.maketrans({
    "ي": "ی", "ى": "ی", "ئ": "ئ",
    "ك": "ک",
    "ٱ": "ا", "إ": "ا", "أ": "ا",
    "ة": "ه",
    "ۀ": "ه",
})


def normalize_name(raw):
    """یکسان‌سازی اسم برای اینکه «محمد رضایی» و «محمد  رضايي» یکی شمرده شوند."""
    if raw is None:
        return ""
    s = str(raw)
    s = unicodedata.normalize("NFKC", s)
    s = _ZW_CHARS.sub("", s)
    s = s.replace(_NIM_FASELE, " ")           # نیم‌فاصله → فاصله
    s = s.translate(_AR2FA)                   # حروف عربی → فارسی
    s = re.sub(r"[\u064B-\u065F\u0670]", "", s)  # حذف اعراب
    s = re.sub(r"\s+", " ", s).strip()        # فشرده‌سازی فاصله‌ها
    return s


def is_valid_name(s):
    """سلول‌های خالی، عددی یا بی‌معنی («-»، «؟» و ...) مدرس محسوب نمی‌شوند."""
    if not s:
        return False
    if re.fullmatch(r"[-–—_.؟?*×\s0-9۰-۹]+", s):
        return False
    return True


def split_names(cell_value):
    """اگر چند مدرس در یک سلول باشند، جدا می‌کند."""
    text = normalize_name(cell_value)
    if not text:
        return []
    parts = [p.strip(" -–—.") for p in NAME_SPLIT_RE.split(text)]
    return [p for p in (normalize_name(p) for p in parts) if is_valid_name(p)]


# ------------------------------------------------------------ خواندن ورودی ---

def _find_header_row(ws):
    """سطر عنوان = اولین سطری که ستون شخص (مدرس/سخنران/تولیدکننده محتوا) دارد."""
    for row in ws.iter_rows(min_row=1, max_row=MAX_HEADER_SCAN_ROWS):
        for cell in row:
            text = normalize_name(cell.value)
            if is_instructor_header(text):
                return cell.row
    return None


def _map_columns(ws, header_row):
    """پیدا کردن شماره ستون مدرس + ستون‌های کمکی بر اساس سطر عنوان."""
    instructor_col = None
    aux_cols = {}
    for cell in ws[header_row]:
        text = normalize_name(cell.value)
        if not text:
            continue
        if instructor_col is None and is_instructor_header(text):
            instructor_col = cell.column
            continue
        for label, keys in AUX_COLUMNS.items():
            if label not in aux_cols and any(k in text for k in keys):
                aux_cols[label] = cell.column
                break
    return instructor_col, aux_cols


def _to_number(value):
    """تبدیل امن به عدد (برای مدت و تعداد نفرات) — اعداد فارسی هم پشتیبانی می‌شود."""
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return value
    s = str(value).strip().translate(str.maketrans("۰۱۲۳۴۵۶۷۸۹", "0123456789"))
    m = re.search(r"\d+(?:\.\d+)?", s)
    return float(m.group()) if m else None


def read_workbook(path):
    """خواندن همه شیت‌ها و برگرداندن لیست رکوردهای کلاس."""
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    records = []          # هر رکورد: dict با مدرس + اطلاعات کمکی + نام شیت
    scanned_sheets = []
    skipped_sheets = []

    for sheet_name in wb.sheetnames:
        ws = wb[sheet_name]
        header_row = _find_header_row(ws)
        if header_row is None:
            skipped_sheets.append(sheet_name)
            continue
        instructor_col, aux_cols = _map_columns(ws, header_row)
        if instructor_col is None:
            skipped_sheets.append(sheet_name)
            continue

        count_before = len(records)
        for row in ws.iter_rows(min_row=header_row + 1, values_only=True):
            cells = {i: v for i, v in enumerate(row, start=1)}
            names = split_names(cells.get(instructor_col))
            if not names:
                continue
            aux = {}
            for label, col in aux_cols.items():
                v = cells.get(col)
                if label in ("مدت (دقیقه)", "تعداد نفرات"):
                    aux[label] = _to_number(v)
                else:
                    aux[label] = str(v).strip() if v is not None else ""
            for name in names:
                rec = {"مدرس": name, "شیت": sheet_name}
                rec.update(aux)
                records.append(rec)
        scanned_sheets.append((sheet_name, len(records) - count_before))

    wb.close()
    return records, scanned_sheets, skipped_sheets


# ----------------------------------------------------------------- تجمیع ---

def aggregate(records):
    """تجمیع رکوردها بر اساس مدرس؛ خروجی مرتب‌شده بر اساس تعداد کلاس (نزولی)."""
    stats = OrderedDict()
    for rec in records:
        name = rec["مدرس"]
        st = stats.setdefault(name, {
            "تعداد کلاس": 0,
            "مجموع دقیقه": 0.0,
            "دارای دقیقه": False,
            "مجموع نفرات": 0.0,
            "دارای نفرات": False,
            "شیت‌ها": OrderedDict(),   # شیت → تعداد کلاس در آن شیت
        })
        st["تعداد کلاس"] += 1
        if rec.get("مدت (دقیقه)") is not None:
            st["مجموع دقیقه"] += rec["مدت (دقیقه)"]
            st["دارای دقیقه"] = True
        if rec.get("تعداد نفرات") is not None:
            st["مجموع نفرات"] += rec["تعداد نفرات"]
            st["دارای نفرات"] = True
        st["شیت‌ها"][rec["شیت"]] = st["شیت‌ها"].get(rec["شیت"], 0) + 1

    # مرتب‌سازی: اول تعداد کلاس (نزولی)، بعد مجموع دقیقه (نزولی)، بعد الفبا
    ordered = sorted(
        stats.items(),
        key=lambda kv: (-kv[1]["تعداد کلاس"], -kv[1]["مجموع دقیقه"], kv[0]),
    )
    return ordered


# ------------------------------------------------------------ ساخت خروجی ---

HEADER_FILL = PatternFill("solid", fgColor="1F4E5F")
HEADER_FONT = Font(name="B Nazanin", bold=True, color="FFFFFF", size=12)
BODY_FONT = Font(name="B Nazanin", size=11)
TOP1_FILL = PatternFill("solid", fgColor="FFD966")   # رتبه ۱
TOP2_FILL = PatternFill("solid", fgColor="E2EFDA")   # رتبه ۲ و ۳
STRIPE_FILL = PatternFill("solid", fgColor="F2F2F2")  # سطرهای زوج
THIN = Side(style="thin", color="BFBFBF")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
CENTER = Alignment(horizontal="center", vertical="center", wrap_text=True)
RIGHT = Alignment(horizontal="right", vertical="center", wrap_text=True)


def _style_header(ws, n_cols, title, row_title=1, row_header=2):
    ws.sheet_view.rightToLeft = True
    ws.merge_cells(start_row=row_title, start_column=1,
                   end_row=row_title, end_column=n_cols)
    tcell = ws.cell(row=row_title, column=1, value=title)
    tcell.font = Font(name="B Nazanin", bold=True, size=14, color="1F4E5F")
    tcell.alignment = CENTER
    ws.row_dimensions[row_title].height = 28
    for col in range(1, n_cols + 1):
        c = ws.cell(row=row_header, column=col)
        c.fill = HEADER_FILL
        c.font = HEADER_FONT
        c.alignment = CENTER
        c.border = BORDER
    ws.row_dimensions[row_header].height = 24
    ws.freeze_panes = ws.cell(row=row_header + 1, column=1)


def _autofit(ws, widths):
    for i, w in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(i)].width = w


def build_output(ordered_stats, records, out_path, src_name):
    wb = openpyxl.Workbook()

    # ---------------- شیت ۱: خلاصه مدرسان ----------------
    ws = wb.active
    ws.title = "خلاصه مدرسان"
    headers = ["رتبه", "نام سخنران / مدرس", "تعداد کلاس",
               "مجموع مدت (دقیقه)", "مجموع نفرات", "تفکیک بر اساس شیت"]
    for col, h in enumerate(headers, start=1):
        ws.cell(row=2, column=col, value=h)
    _style_header(ws, len(headers), f"گزارش تجمیعی مدرسان — {src_name}")

    r = 3
    for rank, (name, st) in enumerate(ordered_stats, start=1):
        breakdown = "، ".join(f"{sheet}: {cnt}" for sheet, cnt in st["شیت‌ها"].items())
        row_vals = [
            rank,
            name,
            st["تعداد کلاس"],
            int(st["مجموع دقیقه"]) if st["دارای دقیقه"] else "—",
            int(st["مجموع نفرات"]) if st["دارای نفرات"] else "—",
            breakdown,
        ]
        for col, v in enumerate(row_vals, start=1):
            c = ws.cell(row=r, column=col, value=v)
            c.font = BODY_FONT
            c.border = BORDER
            c.alignment = RIGHT if col in (2, 6) else CENTER
            if rank == 1:
                c.fill = TOP1_FILL
            elif rank <= 3:
                c.fill = TOP2_FILL
            elif rank % 2 == 0:
                c.fill = STRIPE_FILL
        r += 1

    # سطر جمع کل
    total_classes = sum(st["تعداد کلاس"] for _, st in ordered_stats)
    ws.cell(row=r, column=2, value="جمع کل")
    ws.cell(row=r, column=3, value=total_classes)
    for col in range(1, len(headers) + 1):
        c = ws.cell(row=r, column=col)
        c.font = Font(name="B Nazanin", bold=True, size=11)
        c.border = BORDER
        c.alignment = CENTER
        c.fill = PatternFill("solid", fgColor="D6E4F0")

    _autofit(ws, [8, 30, 12, 16, 14, 55])
    ws.auto_filter.ref = f"A2:{get_column_letter(len(headers))}{r - 1}"

    # ---------------- شیت ۲: جزئیات کلاس‌ها ----------------
    ws2 = wb.create_sheet("جزئیات کلاس‌ها")
    det_headers = ["ردیف", "نام سخنران / مدرس", "شیت", "تاریخ", "برگزارکننده",
                   "موضوع", "مدت (دقیقه)", "تعداد نفرات",
                   "مکان / بستر برگزاری", "نوع کلاس", "لینک مستندات"]
    for col, h in enumerate(det_headers, start=1):
        ws2.cell(row=2, column=col, value=h)
    _style_header(ws2, len(det_headers), "ریز کلاس‌های هر مدرس")

    rank_of = {name: i for i, (name, _) in enumerate(ordered_stats)}
    det_sorted = sorted(records, key=lambda rec: rank_of.get(rec["مدرس"], 10**9))

    r = 3
    for i, rec in enumerate(det_sorted, start=1):
        vals = [
            i, rec["مدرس"], rec["شیت"],
            rec.get("تاریخ", ""), rec.get("برگزارکننده", ""),
            rec.get("موضوع", ""),
            rec.get("مدت (دقیقه)") if rec.get("مدت (دقیقه)") is not None else "",
            rec.get("تعداد نفرات") if rec.get("تعداد نفرات") is not None else "",
            rec.get("مکان / بستر برگزاری", ""), rec.get("نوع کلاس", ""),
            rec.get("لینک مستندات", ""),
        ]
        for col, v in enumerate(vals, start=1):
            c = ws2.cell(row=r, column=col, value=v)
            c.font = BODY_FONT
            c.border = BORDER
            c.alignment = RIGHT if col in (2, 5, 6, 9, 11) else CENTER
            if i % 2 == 0:
                c.fill = STRIPE_FILL
        r += 1

    _autofit(ws2, [7, 26, 22, 13, 18, 34, 13, 13, 22, 14, 32])
    ws2.auto_filter.ref = f"A2:{get_column_letter(len(det_headers))}{max(r - 1, 2)}"

    wb.save(out_path)


# -------------------------------------------------------------------- main ---

def main():
    parser = argparse.ArgumentParser(
        description="تحلیل ستون «نام سخنران / مدرس» در همه شیت‌های یک فایل اکسل "
                    "و تولید گزارش رتبه‌بندی مدرسان بر اساس تعداد کلاس.")
    parser.add_argument("input", help="مسیر فایل اکسل ورودی (.xlsx)")
    parser.add_argument("-o", "--output", default=None,
                        help="مسیر فایل خروجی (پیش‌فرض: «گزارش مدرسان - <نام ورودی>.xlsx»)")
    args = parser.parse_args()

    if not os.path.isfile(args.input):
        sys.exit(f"❌ فایل پیدا نشد: {args.input}")

    base = os.path.splitext(os.path.basename(args.input))[0]
    out_path = args.output or os.path.join(
        os.path.dirname(os.path.abspath(args.input)),
        f"گزارش مدرسان - {base}.xlsx")

    print(f"📖 در حال خواندن: {args.input}")
    records, scanned, skipped = read_workbook(args.input)

    for sheet, n in scanned:
        print(f"   ✔ شیت «{sheet}»: {n} کلاس دارای مدرس")
    for sheet in skipped:
        print(f"   ⚠ شیت «{sheet}»: ستون سخنران/مدرس/تولیدکننده محتوا پیدا نشد — رد شد")

    if not records:
        sys.exit("❌ هیچ رکوردی با ستون «نام سخنران / مدرس» پیدا نشد.")

    ordered = aggregate(records)
    print(f"\n👥 تعداد مدرسان یکتا: {len(ordered)}   |   کل کلاس‌ها: {len(records)}")
    print("\n🏆 رتبه‌بندی مدرسان (بر اساس تعداد کلاس):")
    for rank, (name, st) in enumerate(ordered, start=1):
        minutes = f" — {int(st['مجموع دقیقه'])} دقیقه" if st["دارای دقیقه"] else ""
        print(f"   {rank:>3}. {name}: {st['تعداد کلاس']} کلاس{minutes}")

    build_output(ordered, records, out_path, base)
    print(f"\n✅ گزارش ذخیره شد: {out_path}")


if __name__ == "__main__":
    main()
