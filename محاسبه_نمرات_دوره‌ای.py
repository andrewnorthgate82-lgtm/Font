# -*- coding: utf-8 -*-
"""
========================================================================================
سامانه هوشمند و جامع محاسبه نمرات دوره‌ای نواحی نسرا (استان اصفهان)
Comprehensive Multi-Period Evaluation & Scoring System for NASRA Districts
========================================================================================
ویژگی‌های کلیدی:
۱. معماری ماژولار و مقاوم در برابر خطاهای فایل، پسوند، یونیکد و ساختار پوشه‌ها
۲. لودر همه‌کاره اکسل (پشتیبانی از .xlsx, .xlsm, .xls باینری از طریق xlrd، و جداول HTML)
۳. موتور تشخیص فوق‌پیشرفته ۵ لایه‌ای نواحی با پشتیبانی از خطاهای نگارشی و کاراکترهای نامرئی
۴. شناسایی هوشمند ستون‌های آمار بدون تداخل با عبارات عمومی و سرفصل‌ها
۵. تجمیع دقیق عملکرد ماه‌های ارسال‌شده (تیر، مرداد، شهریور) در برابر حدانتظار کل دوره
۶. اعمال مدل اوزان مصوب: ۱۰٪ حضوری | ۳۰٪ مجازی | سرریز مازاد آموزش به خلاقانه | ۵۰٪ خلاقانه (سقف) | ۱۰٪ تولیدات
۷. خروجی اکسل استاندارد سه‌شیته با شیت اختصاصی کپی آسان و شیت کامل پایش فایل‌ها
========================================================================================
"""

import os
import sys
import re
import io
import shutil
import unicodedata
import concurrent.futures
from collections import Counter
from html.parser import HTMLParser
import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

# تنظیم خودکار و ایمن کدگذاری خروجی ترمینال و رفع قفل کنسول ویندوز
if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

# غیرفعال‌سازی حالت QuickEdit در ویندوز جهت جلوگیری از فریز شدن کنسول با کلیک ماوس
if sys.platform == 'win32':
    try:
        import ctypes
        kernel32 = ctypes.windll.kernel32
        hStdin = kernel32.GetStdHandle(-10)  # STD_INPUT_HANDLE
        mode = ctypes.c_ulong()
        if kernel32.GetConsoleMode(hStdin, ctypes.byref(mode)):
            # 0x0040: ENABLE_QUICK_EDIT_MODE, 0x0080: ENABLE_EXTENDED_FLAGS
            kernel32.SetConsoleMode(hStdin, (mode.value & ~0x0040) | 0x0080)
    except Exception:
        pass

# ==============================================================================
# ۱. تعاریف پایه، مشخصات ۳۲ شهرستان و اوزان ارزیابی مصوب
# ==============================================================================

# جدول تعداد حوزه‌ها / کانون‌های ۳۲ ناحیه استان اصفهان
BRANCH_MAP = {
    'آران و بیدگل': 2, 'اردستان': 1, 'امام حسین(ع)': 4, 'امام رضا(ع)': 3,
    'امام صادق(ع)': 4, 'امام علی(ع)': 3, 'برخوار': 2, 'بویین و میاندشت': 1,
    'تیران و کرون': 1, 'جرقویه': 1, 'چادگان': 1, 'خمینی شهر': 3,
    'خوانسار': 1, 'خور و بیابانک': 1, 'درچه': 1, 'دهاقان': 1,
    'سمیرم': 2, 'شاهین شهر': 3, 'شهرضا': 2, 'فریدن': 1,
    'فریدون شهر': 1, 'فلاورجان': 3, 'کاشان': 4, 'کوهپایه': 1,
    'گلپایگان': 2, 'لنجان': 3, 'مبارکه': 2, 'نایین': 1,
    'نجف آباد': 4, 'نطنز': 1, 'ورزنه': 1, 'هرند': 1
}

DISTRICTS = list(BRANCH_MAP.keys())

# اوزان مصوب ارزیابی
WEIGHT_HOZORI = 0.10         # ۱۰٪ آموزش حضوری
WEIGHT_MAJAZI = 0.30         # ۳۰٪ آموزش مجازی
WEIGHT_TRAINING_POOL = 0.40  # ۴۰٪ سقف سبد آموزش با سرریز مازاد
WEIGHT_KHALAGH = 0.50        # ۵۰٪ اقدامات خلاقانه (سقف ۱۰۰٪ تحقق)
WEIGHT_TOLID = 0.10          # ۱۰٪ تولیدات رسانه‌ای (سقف ۱۰۰٪ تحقق)

# ضرایب دوره‌های مختلف ارزیابی نسبت به حدانتظار یک ماهه
# حدانتظار ماهانه: حضوری ۱۰۰ نفر، مجازی ۵۰۰ نفر، خلاقانه ۵۰۰ نفر، تولیدات ۵۰ مورد به ازای هر حوزه
PERIOD_CONFIGS = {
    2: {
        'months_count': 2,
        'title': '۲ ماهه',
        'title_en': '2-Month',
        'hozori_mult': 200,
        'majazi_mult': 1000,
        'khalagh_mult': 1000,
        'tolid_mult': 100
    },
    3: {
        'months_count': 3,
        'title': '۳ ماهه',
        'title_en': '3-Month',
        'hozori_mult': 300,
        'majazi_mult': 1500,
        'khalagh_mult': 1500,
        'tolid_mult': 150
    },
    6: {
        'months_count': 6,
        'title': '۶ ماهه',
        'title_en': '6-Month',
        'hozori_mult': 600,
        'majazi_mult': 3000,
        'khalagh_mult': 3000,
        'tolid_mult': 300
    }
}

# نام‌های مستعار و نگارش‌های گوناگون جهت تطبیق هوشمند (فارسی، عربی و انگلیسی)
DISTRICT_ALIASES = {
    'آران و بیدگل': ['آران وبیدگل', 'آران بیدگل', 'اران وبیدگل', 'اران بیدگل', 'اران و بیدگل', 'aran', 'bidgol', 'aran_bidgol'],
    'اردستان': ['اردستان', 'ardestan'],
    'امام حسین(ع)': ['امام حسین', 'ناحیه امام حسین', 'حسین ع', 'حسین(ع)', 'emam hossein', 'emam_hossein', 'imam hossein'],
    'امام رضا(ع)': ['امام رضا', 'ناحیه امام رضا', 'رضا ع', 'رضا(ع)', 'emam reza', 'emam_reza', 'imam reza'],
    'امام صادق(ع)': ['امام صادق', 'ناحیه امام صادق', 'صادق ع', 'صادق(ع)', 'emam sadegh', 'emam_sadegh', 'imam sadegh'],
    'امام علی(ع)': ['امام علی', 'ناحیه امام علی', 'علی ع', 'علی(ع)', 'emam ali', 'emam_ali', 'imam ali'],
    'برخوار': ['برخوار', 'دستگرد', 'دولت آباد', 'borkhar'],
    'بویین و میاندشت': ['بویین میاندشت', 'بوئین میاندشت', 'بوئین و میاندشت', 'بویین', 'بوئین', 'boein', 'miandasht'],
    'تیران و کرون': ['تیران کرون', 'تیران وکرون', 'تیران', 'tiran', 'karvan', 'tiran_karvan'],
    'جرقویه': ['جرقویه', 'نصرآباد', 'نصراباد', 'محمدآباد', 'jarghooyeh', 'jarghooye'],
    'چادگان': ['چادگان', 'chadegan'],
    'خمینی شهر': ['خمینی شهر', 'خمینیشهر', 'خمینی‌شهر', 'khomeyni shahr', 'khomeynishahr', 'khomeini'],
    'خوانسار': ['خوانسار', 'khansar'],
    'خور و بیابانک': ['خور بیابانک', 'خوروبیابانک', 'خور و بیابانک', 'خور', 'بیابانک', 'khor', 'biabanak', 'khor_biabanak'],
    'درچه': ['درچه', 'dorcheh', 'dorche'],
    'دهاقان': ['دهاقان', 'dehaghan'],
    'سمیرم': ['سمیرم', 'semirom'],
    'شاهین شهر': ['شاهین شهر', 'شاهین‌شهر', 'شاهینشهر', 'shahin shahr', 'shahinshahr'],
    'شهرضا': ['شهرضا', 'قمشه', 'shahreza'],
    'فریدن': ['فریدن', 'داران', 'fereydan', 'daran'],
    'فریدون شهر': ['فریدون شهر', 'فریدون‌شهر', 'فریدونشهر', 'fereydunshahr', 'fereydoonshahr'],
    'فلاورجان': ['فلاورجان', 'قاهدریجان', 'falavarjan'],
    'کاشان': ['کاشان', 'kashan'],
    'کوهپایه': ['کوهپایه', 'koohpayeh', 'kuhpayeh'],
    'گلپایگان': ['گلپایگان', 'گلپايگان', 'كلپايگان', 'کلپایگان', 'گوگد', 'گلشهر', 'golpayegan', 'golpaygan', 'golpaigan', 'golpaegan', 'golpaygon'],
    'لنجان': ['لنجان', 'زرین شهر', 'زرین‌شهر', 'زرینشهر', 'lenjan', 'zarrinshahr'],
    'مبارکه': ['مبارکه', 'mobarakeh', 'mobarake'],
    'نایین': ['نایین', 'نائین', 'nayin', 'naeen'],
    'نجف آباد': ['نجف آباد', 'نجف‌آباد', 'نجف‌آباد', 'نجفاباد', 'najaf abad', 'najafabad'],
    'نطنز': ['نطنز', 'بادرود', 'natanz', 'badrood'],
    'ورزنه': ['ورزنه', 'varzaneh', 'varzane'],
    'هرند': ['هرند', 'harand']
}

def safe_print(msg):
    """
    چاپ فوق‌ایمن در خروجی کنسول با پشتیبانی کامل از ویندوز و سیستم‌های مختلف
    بدون بروز خطای کدگذاری یونیکد (UnicodeEncodeError)
    """
    try:
        print(msg)
        sys.stdout.flush()
    except Exception:
        try:
            # پاکسازی کاراکترهای ناسازگار با کنسول در صورت بروز خطا
            clean_msg = str(msg).encode('utf-8', errors='replace').decode('utf-8', errors='replace')
            sys.stdout.buffer.write(clean_msg.encode('utf-8') + b'\n')
            sys.stdout.buffer.flush()
        except Exception:
            try:
                clean_msg = str(msg).encode('ascii', errors='replace').decode('ascii')
                print(clean_msg)
                sys.stdout.flush()
            except Exception:
                pass

def robust_text_norm(text):
    """
    نرمال‌سازی بنیادی رشته‌ها:
    - استانداردسازی کامل ی/ي/ى و ک/ك و ة/ه و آ/أ/إ
    - حذف نویسه‌های کشیدگی (تطویل/ـ)
    - پاکسازی تمام کاراکترهای کنترلی نامرئی و جهت‌دار ویندوز (LTR/RTL/ZWSP/BOM)
    - پاکسازی اعراب و نشانه‌های صوتی
    """
    if text is None:
        return ''
    s = unicodedata.normalize('NFKC', str(text)).strip()
    s = s.replace('ي', 'ی').replace('ى', 'ی').replace('ئ', 'ی')
    s = s.replace('ك', 'ک')
    s = s.replace('ة', 'ه').replace('ۀ', 'ه')
    s = s.replace('آ', 'ا').replace('أ', 'ا').replace('إ', 'ا').replace('ٱ', 'ا')
    s = s.replace('\u0640', '')  # حذف تطویل/کشیده
    # حذف تمام کاراکترهای نامرئی، نشانه‌های تغییر جهت یونیکد و نیم‌فاصله‌ها در فاز پایه
    s = re.sub(r'[\u200b-\u200f\u202a-\u202e\u2060-\u206f\ufeff\u00ad\u00a0]', '', s)
    # حذف اعراب عربی
    s = re.sub(r'[\u064b-\u065f\u0670]', '', s)
    return s

def clean_district_str(val):
    """
    پاکسازی ویژه نام شهرستان جهت استخراج هسته نام:
    - حذف کلیه پیشوندها و پسوندهای اداری، کپی‌ها، نگارش‌های پرانتزی و علائم نگارشی
    """
    if not val:
        return ''
    s = robust_text_norm(val)
    
    # حذف علائم اداری و احترامی
    s = re.sub(r'\s*\([عeE]\)|\s*\[[عeE]\]|\s*\([عeE][جjJ]\)|\s*\([رr][هh]\)', '', s)
    s = re.sub(r'علیه\s*السلام', '', s)

    prefixes = [
        'ناحیه مقاومت بسیج', 'ناحیه مقاومت', 'سپاه ناحیه', 'سپاه',
        'کانون سواد فضای مجازی', 'قرارگاه فضای مجازی', 'فضای مجازی',
        'کانون سواد رسانه', 'کانون', 'دفتر', 'ناحیه', 'شهرستان', 'بسیج',
        'گزارش عملکرد', 'گزارش ماهانه', 'گزارش دوره ای', 'گزارش دوره‌ای',
        'فرم گزارش', 'گزارش', 'کارنامه', 'عملکرد'
    ]
    for p in prefixes:
        s = s.replace(robust_text_norm(p), '')

    # حذف عبارات تکمیلی یا عبارات داخل پرانتز و کروشه مانند (کپی)، (1)، [جدید]
    s = re.sub(r'[\(\[\{].*?[\)\]\}]', '', s)
    suffixes = ['کپی', 'نسخه', 'جدید', 'نهایی', 'ویرایش', 'اصلاحیه', 'ارزیابی', 'copy', 'new', 'final', 'v2', 'v1']
    for suf in suffixes:
        s = s.replace(robust_text_norm(suf), '')

    # حذف تمام اعداد، علائم نگارشی و فاصله‌های باقیمانده
    s = re.sub(r'[\(\)\[\]\{\}\.\_\-\:\/\d\s\u200c\u00a0]+', '', s)
    return s

def clean_district_relaxed(val):
    """
    نسخه منعطف نام با یکسان‌سازی حرف گ و ک و حذف حرف ربط و
    جهت ممانعت قطعی از هرگونه عدم تطابق تایپی صفحه کلیدهای مختلف
    """
    return clean_district_str(val).replace('گ', 'ک').replace('و', '')

def match_district_name(text):
    """
    موتور فوق‌هوشمند تطبیق نام شهرستان با قابلیت اطمینان ۱۰۰٪
    """
    if not text:
        return None
    raw = str(text).strip()
    if not raw:
        return None

    # نادیده‌گرفتن برچسب‌های پوشه‌ها یا کلمات عمومی رزرو شده
    ignored = {
        'reports', 'پوشه reports', 'پوشه اصلی برنامه', 'گزارش مستقیم',
        'گزارش دوره‌ای', 'گزارش ماهانه', 'کارنامه', 'داشبورد', 'دانلود', 'font', 'downloads'
    }
    if raw.lower() in ignored or robust_text_norm(raw).lower() in ignored:
        return None

    c_raw = clean_district_str(raw)
    cr_raw = clean_district_relaxed(raw)
    s_lower = robust_text_norm(raw).lower()

    if not c_raw and not cr_raw:
        return None

    # لایه ۱: تطبیق کامل با نام‌های کانونی
    for d in DISTRICTS:
        cd = clean_district_str(d)
        if cd and cd == c_raw:
            return d

    # لایه ۲: تطبیق زیررشته نام‌های کانونی (با طول حداقل ۳ کاراکتر جهت جلوگیری از تداخل)
    for d in DISTRICTS:
        cd = clean_district_str(d)
        if cd and len(cd) >= 3 and cd in c_raw:
            return d

    # لایه ۳: تطبیق با نام‌های مستعار و متغیرهای لاتین/عربی
    for d, aliases in DISTRICT_ALIASES.items():
        for al in aliases:
            al_c = clean_district_str(al)
            if al_c and (al_c == c_raw or (len(al_c) >= 3 and al_c in c_raw)):
                return d
            al_low = robust_text_norm(al).lower()
            if len(al_low) >= 3 and (al_low == s_lower or al_low in s_lower):
                return d

    # لایه ۴: تطبیق فوق‌منعطف بدون واو ربط و با تبدیل گ به ک (حل قطعی کلپایگان / گلپایگان)
    for d in DISTRICTS:
        cdr = clean_district_relaxed(d)
        if cdr and (cdr == cr_raw or (len(cdr) >= 3 and cdr in cr_raw)):
            return d

    return None

def detect_month_from_name(text):
    """
    شناسایی دقیق ماه از روی نام پوشه یا فایل (تیر، مرداد، شهریور، 04-تیر، tir_report)
    با حفاظت دقیق در برابر تداخل نام تیر و شهرستان تیران
    """
    if not text:
        return None
    s = robust_text_norm(str(text))
    s_lower = s.lower()

    months = [
        (1, 'فروردین', ['فروردین', 'farvardin']),
        (2, 'اردیبهشت', ['اردیبهشت', 'ordibehesht']),
        (3, 'خرداد', ['خرداد', 'khordad']),
        (4, 'تیر', ['تیر', '04-تیر', '۰۴-تیر', 'تیرماه', 'tir']),
        (5, 'مرداد', ['مرداد', '05-مرداد', '۰۵-مرداد', 'مردادماه', 'mordad']),
        (6, 'شهریور', ['شهریور', '06-شهریور', '۰۶-شهریور', 'شهریورماه', 'shahrivar', 'shahrivor']),
        (7, 'مهر', ['مهر', '07-مهر', '۰۷-مهر', 'مهرماه', 'mehr']),
        (8, 'آبان', ['آبان', '08-آبان', '۰۸-آبان', 'آبانماه', 'aban']),
        (9, 'آذر', ['آذر', '09-آذر', '۰۹-آذر', 'آذرماه', 'azar']),
        (10, 'دی', ['دی', '10-دی', '۱۰-دی', 'دیماه', 'dey']),
        (11, 'بهمن', ['بهمن', '11-بهمن', '۱۱-بهمن', 'بهمنماه', 'bahman']),
        (12, 'اسفند', ['اسفند', '12-اسفند', '۱۲-اسفند', 'اسفندماه', 'esfand'])
    ]

    for m_num, m_canonical, aliases in months:
        for al in aliases:
            al_norm = robust_text_norm(al).lower()
            if al_norm in s_lower:
                # ممانعت از تشخیص نادرست تیر در کلمه تیران
                if m_canonical == 'تیر' and 'تیران' in s:
                    continue
                # ممانعت از تشخیص نادرست دی در اسامی نظیر اردستان یا دهاقان
                if m_canonical == 'دی' and al == 'دی':
                    if any(x in s for x in ['دهاقان', 'اردستان', 'جدید', 'تولید']):
                        continue
                return m_canonical

    return None

def parse_number(val):
    """
    استخراج ایمن مقادیر عددی از سلول‌های اکسل:
    - پشتیبانی از اعداد اعشاری و صحیح
    - تبدیل خودکار ارقام فارسی و عربی به ارقام انگلیسی
    - حذف نمادهای هزارگان، واحدهای نفر، مخاطب، صفحه و غیره
    - جلوگیری از تفسیر تاریخ‌ها (مانند 1405/05/15) به عنوان عدد
    """
    if val is None:
        return 0
    if isinstance(val, (int, float)):
        return int(val) if isinstance(val, int) or (isinstance(val, float) and val.is_integer()) else val

    s = robust_text_norm(str(val)).strip()
    if not s:
        return 0

    # تاریخ‌ها نباید استخراج شوند
    if re.search(r'\d{2,4}[/-]\d{1,2}[/-]\d{1,2}', s):
        return 0

    # تبدیل ارقام فارسی و عربی به انگلیسی
    p_digits = '۰۱۲۳۴۵۶۷۸۹'
    a_digits = '٠١٢٣٤٥٦٧٨٩'
    for i in range(10):
        s = s.replace(p_digits[i], str(i)).replace(a_digits[i], str(i))

    for sep in [',', '،', '٬', '٫', '\u066c', '\u066b', ' ', 'نفر', 'مخاطب', 'عدد', 'صفحه', 'بازدید', 'مورد']:
        s = s.replace(sep, '')

    match = re.search(r'\d+(\.\d+)?', s)
    if match:
        try:
            num_str = match.group()
            return float(num_str) if '.' in num_str else int(num_str)
        except Exception:
            return 0
    return 0

# ==============================================================================
# ۳. لودر همه‌کاره و منعطف اکسل (Universal Robust Excel Loader)
# ==============================================================================

class GenericCell:
    def __init__(self, value):
        self.value = value

class GenericSheet:
    def __init__(self, title, rows_data):
        self.title = title
        self._data = rows_data  # 0-indexed list of lists
        self.max_row = len(rows_data)
        self.max_column = max((len(r) for r in rows_data), default=0)

    def cell(self, row, column, value=None):
        if row < 1 or row > self.max_row or column < 1:
            return GenericCell(None)
        row_idx = row - 1
        col_idx = column - 1
        row_cells = self._data[row_idx]
        if col_idx < len(row_cells):
            return GenericCell(row_cells[col_idx])
        return GenericCell(None)

    def iter_rows(self, min_row=1, max_row=None, min_col=1, max_col=None, values_only=True):
        if max_row is None:
            max_row = self.max_row
        if max_col is None:
            max_col = self.max_column
        for r_idx in range(min_row - 1, min(max_row, self.max_row)):
            row = self._data[r_idx] if r_idx < len(self._data) else []
            yield tuple((row[c_idx] if c_idx < len(row) else None) for c_idx in range(min_col - 1, max_col))

class GenericWorkbook:
    def __init__(self, sheets_dict, default_sheet=None, title=None):
        self._sheets = sheets_dict
        self.sheetnames = list(sheets_dict.keys())
        self._active_sheet = default_sheet or (self.sheetnames[0] if self.sheetnames else None)
        class Properties:
            def __init__(self, t):
                self.title = t
        self.properties = Properties(title)

    def __getitem__(self, item):
        return self._sheets[item]

    @property
    def active(self):
        return self._sheets[self._active_sheet] if self._active_sheet else None

class HTMLTableExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tables = []
        self._cur_table = []
        self._cur_row = []
        self._cur_cell = []
        self._in_cell = False

    def handle_starttag(self, tag, attrs):
        if tag in ('td', 'th'):
            self._in_cell = True
            self._cur_cell = []
        elif tag == 'tr':
            self._cur_row = []
        elif tag == 'table':
            self._cur_table = []

    def handle_endtag(self, tag):
        if tag in ('td', 'th'):
            self._in_cell = False
            self._cur_row.append(''.join(self._cur_cell).strip())
        elif tag == 'tr':
            if self._cur_row:
                self._cur_table.append(self._cur_row)
        elif tag == 'table':
            if self._cur_table:
                self.tables.append(self._cur_table)

    def handle_data(self, data):
        if self._in_cell:
            self._cur_cell.append(data)

def load_workbook_robust(fpath):
    """
    بارگذاری بی‌نقص انواع فایل‌های اکسل و خروجی‌های سیستمی:
    ۱. فایل‌های مدرن XLSX و XLSM با حفاظت کامل در برابر پیوندهای خارجی و ماکروها
    ۲. فایل‌های ZIP با پسوند .xls
    ۳. فایل‌های باینری سنتی BIFF8 (.xls) با پشتیبانی از کتابخانه xlrd
    ۴. جداول HTML ذخیره‌شده به عنوان فایل اکسل
    """
    if not os.path.exists(fpath) or os.path.getsize(fpath) == 0:
        raise ValueError("فایل خالی است یا وجود ندارد")

    with open(fpath, 'rb') as f:
        file_bytes = f.read()

    # ۱. فایل استاندارد آفیس زیپ (OpenXML / XLSX / XLSM)
    if file_bytes.startswith(b'PK\x03\x04'):
        try:
            return openpyxl.load_workbook(fpath, data_only=True, keep_links=False)
        except Exception:
            try:
                return openpyxl.load_workbook(io.BytesIO(file_bytes), data_only=True, keep_links=False)
            except Exception:
                return openpyxl.load_workbook(fpath, data_only=True, read_only=True, keep_links=False)

    # ۲. فایل باینری قدیمی مایکروسافت اکسل (.xls / BIFF8)
    if file_bytes.startswith(b'\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1'):
        try:
            import xlrd
            book = xlrd.open_workbook(file_contents=file_bytes)
            sheets_dict = {}
            for sname in book.sheet_names():
                sh = book.sheet_by_name(sname)
                rows = []
                for r in range(sh.nrows):
                    rows.append([sh.cell_value(r, c) for c in range(sh.ncols)])
                sheets_dict[sname] = GenericSheet(sname, rows)
            return GenericWorkbook(sheets_dict)
        except ImportError:
            raise RuntimeError("جهت پردازش فایل باینری قدیمی .xls نصب پکیج xlrd الزامی است.")
        except Exception as e:
            raise RuntimeError(f"خطا در پردازش فایل .xls باینری: {e}")

    # ۳. جداول HTML نام‌گذاری شده به پسوند اکسل
    if b'<html' in file_bytes.lower() or b'<table' in file_bytes.lower():
        try:
            content = file_bytes.decode('utf-8', errors='ignore')
            parser = HTMLTableExtractor()
            parser.feed(content)
            if parser.tables:
                sheets_dict = {}
                for idx, tbl in enumerate(parser.tables, start=1):
                    s_name = f"Sheet{idx}"
                    sheets_dict[s_name] = GenericSheet(s_name, tbl)
                return GenericWorkbook(sheets_dict)
        except Exception as e:
            raise RuntimeError(f"خطا در تفسیر جدول HTML: {e}")

    # تلاش نهایی از طریق لودر پیش‌فرض
    return openpyxl.load_workbook(fpath, data_only=True, keep_links=False)

# ==============================================================================
# ۴. موتور فوق‌هوشمند کشف ناحیه (District Detection Engine)
# ==============================================================================

def detect_district_from_workbook(wb, fpath, folder_label=''):
    """
    موتور ۵ لایه‌ای و همه‌جانبه شناسایی ناحیه:
    لایه ۱: نام فایل (گلپایگان.xlsx، گزارش گلپایگان، کلپایگان، گلپايگان)
    لایه ۲: نام پوشه والد و ساختار دایرکتوری (reports/تیر/گلپایگان/)
    لایه ۳: نام شیت‌های داخل ورک‌بوک
    لایه ۴: متادیتای عنوان سند
    لایه ۵: اسکن عمیق سلول‌ها با فیلتر هوشمند شیت‌های اطلاعات پایه و لیست‌های استانی
    """
    fname = os.path.basename(fpath)

    # ۱. بررسی نام فایل
    d = match_district_name(fname)
    if d:
        return d, "نام فایل"

    # ۲. بررسی برچسب پوشه و مسیر کامل والد
    if folder_label and folder_label not in ['reports', 'پوشه reports', 'پوشه اصلی برنامه']:
        for part in folder_label.replace('\\', '/').split('/'):
            d = match_district_name(part)
            if d:
                return d, f"پوشه «{part}»"

    parent_dir = os.path.basename(os.path.dirname(os.path.abspath(fpath)))
    if parent_dir and parent_dir not in ['Font', 'reports', 'Downloads']:
        d = match_district_name(parent_dir)
        if d:
            return d, f"پوشه والد «{parent_dir}»"

    # ۳. بررسی نام شیت‌های اکسل
    for sname in wb.sheetnames:
        d = match_district_name(sname)
        if d:
            return d, f"نام شیت «{sname}»"

    # ۴. بررسی متادیتای عنوان سند
    try:
        if wb.properties and wb.properties.title:
            d = match_district_name(wb.properties.title)
            if d:
                return d, "متادیتای عنوان سند"
    except Exception:
        pass

    # ۵. اسکن سلول‌های شیت‌های غیرپایه
    district_counts = Counter()
    for sname in wb.sheetnames:
        cn = robust_text_norm(sname)
        if any(w in cn for w in ['اطلاعاتپایه', 'اطلاعات پایه', 'dropdown', 'base', 'ref', 'لیست', 'پایه']):
            continue

        ws = wb[sname]
        max_r = min(ws.max_row + 1, 80)
        max_c = min(ws.max_column + 1, 20)

        found_in_sheet = set()
        sheet_hits = Counter()

        for r in range(1, max_r):
            for c in range(1, max_c):
                v = ws.cell(r, c).value
                if v is not None:
                    md = match_district_name(v)
                    if md:
                        found_in_sheet.add(md)
                        weight = 5 if r <= 8 else 1
                        sheet_hits[md] += weight

        # در صورتی که بیش از ۳ شهرستان در یک شیت باشد، شیت مرجع است و فیلتر می‌گردد
        if len(found_in_sheet) > 3:
            continue

        for dist, cnt in sheet_hits.items():
            district_counts[dist] += cnt

    if district_counts:
        best_district, count = district_counts.most_common(1)[0]
        return best_district, f"محتوای سلول‌های کاربرگ ({count} بار مشاهده)"

    return None, "عدم شناسایی"

# ==============================================================================
# ۵. موتور استخراج شاخص‌های عملکردی (Indicator Extraction Engine)
# ==============================================================================

def extract_sheet_metrics(ws):
    """
    استخراج سریع، مقیاس‌پذیر و ایمن مخاطبان و برنامه‌ها با استفاده از iter_rows
    و محدودسازی ابعاد به حداکثر ۳۰۰ سطر و ۲۰ ستون جهت ممانعت از هرگونه توقف یا کندی
    """
    if ws is None:
        return {'people_sum': 0, 'classes_count': 0}

    exact_stat_keywords = [
        'تعداد نفرات', 'تعداد بازدید', 'تعداد مخاطب', 'تعداد شرکت',
        'تعداد فراگیر', 'تعداد حاضر', 'تعداد صفحه', 'تعداد صفحات', 'تعداد نسخه'
    ]
    secondary_stat_keywords = [
        'تعداد نفرات / تعداد بازدید', 'نفرات', 'مخاطبان', 'مخاطبین',
        'تیراژ', 'فراگیران', 'شرکت کنندگان', 'بازدید', 'صفحات'
    ]
    disqualifiers = [
        'ردیف', 'تاریخ', 'نام ناحیه', 'نام سخنران', 'مدرس', 'موضوع',
        'مکان', 'بستر', 'لینک', 'نوع کلاس', 'نوع تولید', 'برگزار کننده'
    ]

    max_r = min(getattr(ws, 'max_row', 100) or 100, 300)
    max_c = min(getattr(ws, 'max_column', 20) or 20, 20)

    try:
        rows = list(ws.iter_rows(min_row=1, max_row=max_r, min_col=1, max_col=max_c, values_only=True))
    except Exception:
        rows = []
        for r in range(1, max_r + 1):
            rows.append([ws.cell(r, c).value for c in range(1, max_c + 1)])

    if not rows:
        return {'people_sum': 0, 'classes_count': 0}

    target_col = None  # 1-based index
    header_row_idx = 0  # 0-based index

    # جستجوی هدر در ۶ ردیف ابتدایی
    for r_idx in range(min(len(rows), 6)):
        row = rows[r_idx]
        for c_idx, val in enumerate(row):
            h_norm = robust_text_norm(str(val or ''))
            if any(dq in h_norm for dq in disqualifiers):
                continue
            if any(k in h_norm for k in exact_stat_keywords):
                target_col = c_idx + 1
                header_row_idx = r_idx
                break
        if target_col:
            break

    if target_col is None:
        for r_idx in range(min(len(rows), 6)):
            row = rows[r_idx]
            for c_idx, val in enumerate(row):
                h_norm = robust_text_norm(str(val or ''))
                if any(dq in h_norm for dq in disqualifiers):
                    continue
                if any(k in h_norm for k in secondary_stat_keywords):
                    target_col = c_idx + 1
                    header_row_idx = r_idx
                    break
            if target_col:
                break

    total_people = 0
    active_classes = 0
    detail_rows_found = False
    consecutive_empty = 0

    for r_idx in range(header_row_idx + 1, len(rows)):
        row = rows[r_idx]
        has_content = any(v is not None and str(v).strip() != '' for v in row[1:])
        if not has_content and row[0] is not None and len(str(row[0]).strip()) > 3:
            has_content = True

        if not has_content:
            consecutive_empty += 1
            if consecutive_empty >= 8:
                break
            continue

        consecutive_empty = 0

        # بررسی سطر خلاصه / جمع کل
        first_few = ' '.join(str(v or '') for v in row[:4])
        if any(w in first_few for w in ['جمع کل', 'مجموع', 'جمع:', 'total', 'sum']):
            if not detail_rows_found and target_col and target_col <= len(row):
                p_num = parse_number(row[target_col - 1])
                if p_num > 0:
                    total_people = p_num
                    active_classes = max(1, active_classes)
            continue

        active_classes += 1
        detail_rows_found = True

        if target_col and target_col <= len(row):
            p_num = parse_number(row[target_col - 1])
            total_people += p_num
        else:
            for c_idx in range(1, len(row)):
                p_num = parse_number(row[c_idx])
                if p_num > 0 and p_num != (r_idx + 1):
                    total_people += p_num
                    break

    return {
        'people_sum': int(total_people),
        'classes_count': active_classes
    }

def extract_workbook_indicators(wb):
    """
    استخراج ۴ شاخص عملکردی اصلی از تمام کاربرگ‌های ورک‌بوک
    """
    ws_hoz = None
    ws_tav = None
    ws_maj = None
    ws_kha = None
    ws_tol = None

    for sname in wb.sheetnames:
        cn = robust_text_norm(sname)
        if 'حضوری' in cn or 'کارگاه' in cn or 'کلاس' in cn or 'همایش' in cn:
            ws_hoz = wb[sname]
        elif 'توانمند' in cn or 'گردان' in cn:
            ws_tav = wb[sname]
        elif 'مجازی' in cn or 'لایو' in cn or 'آنلاین' in cn or 'وبینار' in cn:
            ws_maj = wb[sname]
        elif 'خلاق' in cn or 'پویش' in cn or 'مسابقه' in cn or 'ابتکار' in cn:
            ws_kha = wb[sname]
        elif 'تولید' in cn or 'رسانه' in cn or 'محتوا' in cn or 'کلیپ' in cn or 'پوستر' in cn:
            ws_tol = wb[sname]

    if any([ws_hoz, ws_tav, ws_maj, ws_kha, ws_tol]):
        m_hoz = extract_sheet_metrics(ws_hoz)
        m_tav = extract_sheet_metrics(ws_tav)
        m_maj = extract_sheet_metrics(ws_maj)
        m_kha = extract_sheet_metrics(ws_kha)
        m_tol = extract_sheet_metrics(ws_tol)

        # آموزش حضوری شامل آموزش‌های حضوری عمومی + توانمندسازی گردان است
        hoz_val = m_hoz['people_sum'] + m_tav['people_sum']
        if hoz_val == 0:
            hoz_val = m_hoz['classes_count'] + m_tav['classes_count']

        maj_val = m_maj['people_sum'] if m_maj['people_sum'] > 0 else m_maj['classes_count']
        kha_val = m_kha['people_sum'] if m_kha['people_sum'] > 0 else m_kha['classes_count']
        tol_val = m_tol['people_sum'] if m_tol['people_sum'] > 0 else m_tol['classes_count']

        return hoz_val, maj_val, kha_val, tol_val

    # حالت پشتیبان برای فایل‌های تک‌کاربرگ یا خلاصه‌شده با استفاده ایمن از iter_rows
    ws = wb.active
    hoz_val, maj_val, kha_val, tol_val = 0, 0, 0, 0
    max_c = min(getattr(ws, 'max_column', 20) or 20, 20)
    max_r = min(getattr(ws, 'max_row', 100) or 100, 200)

    try:
        rows = list(ws.iter_rows(min_row=1, max_row=max_r, min_col=1, max_col=max_c, values_only=True))
    except Exception:
        rows = []
        for r in range(1, max_r + 1):
            rows.append([ws.cell(r, c).value for c in range(1, max_c + 1)])

    consecutive_empty = 0
    for r_idx, row in enumerate(rows):
        row_text = ' '.join(str(v or '') for v in row)
        if not row_text.strip():
            consecutive_empty += 1
            if consecutive_empty >= 8:
                break
            continue
        consecutive_empty = 0

        row_val = 0
        for val in reversed(row):
            p_num = parse_number(val)
            if p_num > 0 and p_num != (r_idx + 1):
                row_val = p_num
                break

        row_norm = robust_text_norm(row_text)
        if 'حضوری' in row_norm or 'توانمند' in row_norm or 'کارگاه' in row_norm:
            hoz_val += row_val
        elif 'مجازی' in row_norm or 'لایو' in row_norm or 'وبینار' in row_norm or 'آنلاین' in row_norm:
            maj_val += row_val
        elif 'خلاق' in row_norm or 'مسابقه' in row_norm or 'پویش' in row_norm:
            kha_val += row_val
        elif 'تولید' in row_norm or 'رسانه' in row_norm or 'کلیپ' in row_norm:
            tol_val += row_val

    return hoz_val, maj_val, kha_val, tol_val

def _load_and_extract_file(fpath, folder_label):
    """
    تابع کارگری مجزا جهت اجرای ایمن پردازش هر فایل با قابلیت اعمال Timeout
    """
    wb = None
    try:
        wb = load_workbook_robust(fpath)
        detected, detection_reason = detect_district_from_workbook(wb, fpath, folder_label)
        if not detected:
            return None, "عدم شناسایی شهرستان", 0, 0, 0, 0
        hoz_val, maj_val, kha_val, tol_val = extract_workbook_indicators(wb)
        return detected, detection_reason, hoz_val, maj_val, kha_val, tol_val
    finally:
        if wb is not None:
            try:
                if hasattr(wb, 'close'):
                    wb.close()
            except Exception:
                pass

def scan_reports_directory(reports_dir='reports'):
    """
    اسکن جامع و مقاوم فایل‌های اکسل در تمام مسیرها:
    - بررسی همزمان مسیر استقرار فایل اسکریپت (__file__) و مسیر جاری سیستم (cwd)
    - اسکن ساختارهای متنوع پوشه‌ها (پوشه ماهانه، پوشه شهرستانی، پوشه تودرتو)
    - پوشش پسوندهای متداول اکسل (.xlsx, .xlsm, .xls)
    - پالایش فایل‌های موقت، پشتیبان و مسترهای برنامه
    """
    script_dir = os.path.dirname(os.path.abspath(__file__)) if '__file__' in globals() else os.getcwd()
    cwd = os.getcwd()

    candidate_roots = []
    
    # پوشه reports در کنار اسکریپت و در مسیر جاری
    r_script = os.path.join(script_dir, reports_dir)
    if os.path.exists(r_script):
        candidate_roots.append((r_script, True))
    
    if cwd != script_dir:
        r_cwd = os.path.join(cwd, reports_dir)
        if os.path.exists(r_cwd):
            candidate_roots.append((r_cwd, True))

    # پوشه اسکریپت و پوشه جاری به منظور اسکن پوشه‌های ماهانه احتمالی مستقر در کنار برنامه
    candidate_roots.append((script_dir, False))
    if cwd != script_dir:
        candidate_roots.append((cwd, False))

    excluded_files = {
        'نمرات_نهایی_نواحی.xlsx', 'final_scores.xlsx',
        'تهیه کارنامه نواحی.xlsx', 'تهیه کارنامه ۳ ماهه نواحی.xlsx',
        'master_monitoring.xlsx', 'monthly_scorecard.xlsx',
        'گزارش شهریور ماه 1405 ناحیه.xlsx',
        'نمرات_عملکرد_۲ماهه.xlsx', 'نمرات_عملکرد_۳ماهه.xlsx', 'نمرات_عملکرد_۶ماهه.xlsx'
    }

    files_list = []
    seen_paths = set()

    for root_dir, is_reports_folder in candidate_roots:
        if not os.path.exists(root_dir):
            continue

        if is_reports_folder:
            for root, dirs, files in os.walk(root_dir):
                for f in sorted(files):
                    f_lower = f.lower()
                    if f_lower.endswith(('.xlsx', '.xlsm', '.xls')) and not f.startswith('~$') and f not in excluded_files:
                        full_p = os.path.abspath(os.path.join(root, f))
                        if full_p not in seen_paths:
                            seen_paths.add(full_p)
                            rel_dir = os.path.relpath(root, root_dir)
                            folder_label = '' if rel_dir == '.' else rel_dir
                            files_list.append((full_p, folder_label, f))
        else:
            # اسکن پوشه‌های فصلی یا شهرستانی در سطح ریشه
            month_keywords = ['فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور', 'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند']
            for entry in sorted(os.listdir(root_dir)):
                entry_path = os.path.join(root_dir, entry)
                if os.path.isdir(entry_path) and not entry.startswith('.') and entry != reports_dir and entry != 'output_cards_3months':
                    entry_norm = robust_text_norm(entry)
                    if any(m in entry_norm for m in month_keywords) or match_district_name(entry):
                        for root, dirs, files in os.walk(entry_path):
                            for f in sorted(files):
                                f_lower = f.lower()
                                if f_lower.endswith(('.xlsx', '.xlsm', '.xls')) and not f.startswith('~$') and f not in excluded_files:
                                    full_p = os.path.abspath(os.path.join(root, f))
                                    if full_p not in seen_paths:
                                        seen_paths.add(full_p)
                                        files_list.append((full_p, entry, f))
                elif os.path.isfile(entry_path):
                    f_lower = entry.lower()
                    if f_lower.endswith(('.xlsx', '.xlsm', '.xls')) and not entry.startswith('~$') and entry not in excluded_files and not entry.startswith('کارنامه_'):
                        full_p = os.path.abspath(entry_path)
                        if full_p not in seen_paths:
                            seen_paths.add(full_p)
                            files_list.append((full_p, 'پوشه اصلی برنامه', entry))

    detected_subdirs = sorted(list({flabel for _, flabel, _ in files_list if flabel and flabel != 'پوشه اصلی برنامه'}))

    safe_print(f"🔍 گزارش جستجوی فایل‌های اکسل در سیستم:")
    if detected_subdirs:
        safe_print(f"   📂 پوشه‌های شناسایی‌شده: {len(detected_subdirs)} پوشه ({', '.join(detected_subdirs)})")
    safe_print(f"   📄 مجموع فایل‌های اکسل کشف شده برای ارزیابی: {len(files_list)} فایل")
    for fpath, flabel, fname in files_list:
        loc = f"در پوشه «{flabel}»" if flabel else f"مستقیم در پوشه گزارشات"
        safe_print(f"      • {fname} ({loc})")

    return detected_subdirs, files_list

# ==============================================================================
# ۷. تعیین سطوح کیفی سه‌گانه (Qualitative Tiers)
# ==============================================================================

def get_tier_3_levels(score, scale_mode='0-100'):
    """
    سطوح کیفی ۳ گانه مصوب: عالی | متوسط | ضعیف
    """
    if scale_mode == '0-100':
        if score >= 85.0:
            return "عالی"
        elif score >= 50.0:
            return "متوسط"
        else:
            return "ضعیف"
    else:  # مقیاس ۷۰ تا ۱۰۰
        if score >= 90.0:
            return "عالی"
        elif score >= 75.0:
            return "متوسط"
        else:
            return "ضعیف"

# ==============================================================================
# ۸. موتور اصلی ارزیابی دوره‌ای و تولید اکسل نهایی
# ==============================================================================

def run_period_evaluation(selected_months=None, selected_scale=None):
    n_months = selected_months if selected_months in [2, 3, 6] else 3
    scale_mode = selected_scale if selected_scale in ['0-100', '70-100'] else '0-100'

    cfg = PERIOD_CONFIGS[n_months]
    scale_label_fa = "مقیاس واقعی (۰ تا ۱۰۰)" if scale_mode == '0-100' else "مقیاس استاندارد نسرا (۷۰ تا ۱۰۰)"
    score_header_fa = "نمره واقعی (۰-۱۰۰)" if scale_mode == '0-100' else "نمره نسرا (۷۰-۱۰۰)"

    safe_print("\n" + "=" * 80)
    safe_print(f"📌 دوره انتخابی: عملکرد {cfg['title']} ({cfg['title_en']})")
    safe_print(f"📊 مقیاس انتخابی نمره‌دهی: {scale_label_fa}")
    safe_print(f"🎯 حدانتظارها: ضریب {n_months} برابری اهداف ماهانه")
    safe_print("⚖️ اوزان ارزیابی: ۱۰٪ حضوری | ۳۰٪ مجازی (سرشکن در سبد ۴۰٪ آموزش) | ۵۰٪ خلاقانه (سقف ۱۰۰٪) | ۱۰٪ تولیدات")
    safe_print("=" * 80)

    subdirs, all_files = scan_reports_directory('reports')
    safe_print("-" * 80)

    # مقداردهی اولیه آمار تجمعی ۳۲ شهرستان
    accumulated = {}
    for dn in DISTRICTS:
        accumulated[dn] = {
            'files_count': 0,
            'months_found': [],
            'files': [],
            'hozori': 0,
            'majazi': 0,
            'khalagh': 0,
            'tolid': 0
        }

    audit_logs = []

    # پردازش تک‌تک فایل‌های کشف‌شده با استفاده از محافظت زمانی (Timeout) جهت جلوگیری قطعی از توقف
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
        for fpath, folder_label, fname in all_files:
            audit_entry = {
                'filename': fname,
                'folder': folder_label,
                'path': fpath,
                'month': None,
                'district': None,
                'reason': None,
                'hoz': 0, 'maj': 0, 'kha': 0, 'tol': 0,
                'status': 'ناموفق',
                'error_msg': ''
            }

            try:
                future = executor.submit(_load_and_extract_file, fpath, folder_label)
                # اعمال محدودیت زمانی ۱۰ ثانیه برای هر فایل اکسل
                res = future.result(timeout=10.0)
                detected, detection_reason, hoz_val, maj_val, kha_val, tol_val = res

                if not detected:
                    msg = f"شناسایی ناحیه برای فایل '{fname}' (در پوشه '{folder_label}') ناموفق بود."
                    safe_print(f"⚠️ {msg}")
                    audit_entry['status'] = 'اخطار'
                    audit_entry['error_msg'] = 'عدم شناسایی شهرستان'
                    audit_logs.append(audit_entry)
                    continue

                # برچسب‌گذاری ماه
                detected_month = detect_month_from_name(folder_label) or detect_month_from_name(fname)
                month_tag = detected_month or (folder_label if folder_label else f"گزارش {accumulated[detected]['files_count'] + 1}")

                accumulated[detected]['files_count'] += 1
                if month_tag not in accumulated[detected]['months_found']:
                    accumulated[detected]['months_found'].append(month_tag)
                accumulated[detected]['files'].append(f"{folder_label}/{fname}" if folder_label else fname)
                accumulated[detected]['hozori'] += hoz_val
                accumulated[detected]['majazi'] += maj_val
                accumulated[detected]['khalagh'] += kha_val
                accumulated[detected]['tolid'] += tol_val

                audit_entry['district'] = detected
                audit_entry['reason'] = detection_reason
                audit_entry['month'] = month_tag
                audit_entry['hoz'] = hoz_val
                audit_entry['maj'] = maj_val
                audit_entry['kha'] = kha_val
                audit_entry['tol'] = tol_val
                audit_entry['status'] = 'موفق'
                audit_logs.append(audit_entry)

                src_info = f"«{folder_label}»" if folder_label else "مستقیم"
                safe_print(f"✓ [{detected}] ({src_info} | {month_tag}): +{hoz_val} حضوری | +{maj_val} مجازی | +{kha_val} خلاقانه | +{tol_val} تولید")

            except concurrent.futures.TimeoutError:
                msg = f"زمان پردازش فایل '{fname}' (در پوشه '{folder_label}') طولانی شد و برای حفظ روند برنامه رد شد."
                safe_print(f"⚠️ {msg}")
                audit_entry['status'] = 'اخطار'
                audit_entry['error_msg'] = 'توقف به دلیل طولانی شدن زمان (Timeout)'
                audit_logs.append(audit_entry)
            except Exception as e:
                msg = f"خطا در پردازش فایل '{fname}': {e}"
                safe_print(f"❌ {msg}")
                audit_entry['status'] = 'خطا'
                audit_entry['error_msg'] = str(e)
                audit_logs.append(audit_entry)

    safe_print("-" * 80)
    safe_print("📊 محاسبه نمرات بر مبنای اوزان مصوب، سرریز آموزش و سقف ۱۰۰٪...")

    results = []
    for dn in DISTRICTS:
        b_count = BRANCH_MAP[dn]
        t_hoz = b_count * cfg['hozori_mult']
        t_maj = b_count * cfg['majazi_mult']
        t_kha = b_count * cfg['khalagh_mult']
        t_tol = b_count * cfg['tolid_mult']

        acc = accumulated[dn]
        a_hoz = acc['hozori']
        a_maj = acc['majazi']
        a_kha = acc['khalagh']
        a_tol = acc['tolid']
        f_cnt = acc['files_count']
        m_found = acc['months_found']

        missing_months = [s for s in subdirs if s not in m_found] if subdirs else []

        # ۱. نرخ تحقق خام هر شاخص
        r_hoz_raw = (a_hoz / t_hoz) if t_hoz > 0 else 0.0
        r_maj_raw = (a_maj / t_maj) if t_maj > 0 else 0.0
        r_kha_raw = (a_kha / t_kha) if t_kha > 0 else 0.0
        r_tol_raw = (a_tol / t_tol) if t_tol > 0 else 0.0

        pct_hoz_raw = r_hoz_raw * 100.0
        pct_maj_raw = r_maj_raw * 100.0
        pct_kha_raw = r_kha_raw * 100.0
        pct_tol_raw = r_tol_raw * 100.0

        # ۲. سبد آموزش (مجموعاً ۴۰٪): ۱۰٪ حضوری + ۳۰٪ مجازی با جبران متقابل
        raw_training_share = (WEIGHT_HOZORI * r_hoz_raw) + (WEIGHT_MAJAZI * r_maj_raw)
        training_share = min(WEIGHT_TRAINING_POOL, raw_training_share)
        surplus_training = max(0.0, raw_training_share - WEIGHT_TRAINING_POOL)

        # ۳. اقدامات خلاقانه (۵۰٪): بهره‌مندی از مازاد آموزش با سقف قطعی ۵۰٪
        raw_khalagh_share = WEIGHT_KHALAGH * r_kha_raw
        khalagh_share = min(WEIGHT_KHALAGH, raw_khalagh_share + surplus_training)

        # ۴. تولیدات رسانه‌ای (۱۰٪): با سقف قطعی ۱۰٪
        tolid_share = WEIGHT_TOLID * min(1.0, r_tol_raw)

        # مجموع تحقق موزون (۰ تا ۱.۰)
        total_realization_ratio = training_share + khalagh_share + tolid_share
        total_realization_pct = round(total_realization_ratio * 100.0, 2)

        # محاسبه نمرات در هر دو مقیاس
        score_real = round(total_realization_ratio * 100.0, 1)
        score_nasra = round(70.0 + (30.0 * total_realization_ratio), 1)

        # نمره انتخابی کاربر
        if scale_mode == '0-100':
            final_score = score_real
            tier = get_tier_3_levels(score_real, '0-100')
        else:
            final_score = score_nasra
            tier = get_tier_3_levels(score_nasra, '70-100')

        if f_cnt >= n_months:
            status_desc = f"کامل ({len(m_found)} از {n_months} ماه)"
        elif f_cnt > 0:
            matched_subdirs = [s for s in subdirs if s in m_found]
            unmatched_subdirs = [s for s in subdirs if s not in m_found]
            if matched_subdirs and unmatched_subdirs:
                status_desc = f"کسری: {len(matched_subdirs)} از {n_months} ماه (عدم فعالیت در: {'، '.join(unmatched_subdirs)})"
            else:
                status_desc = f"کسری: تحویل {f_cnt} از {n_months} ماه"
        else:
            status_desc = f"فاقد گزارش (عملکرد ۰ در کل {n_months} ماه)"

        results.append({
            'district': dn,
            'branches': b_count,
            'files_count': f_cnt,
            'months_found': m_found,
            'missing_months': missing_months,
            'status_desc': status_desc,
            'score': final_score,
            'score_real': score_real,
            'score_nasra': score_nasra,
            'tier': tier,
            'total_realization_pct': total_realization_pct,
            'training_share_pct': round(training_share * 100.0, 2),
            'surplus_training_pct': round(surplus_training * 100.0, 2),
            'khalagh_share_pct': round(khalagh_share * 100.0, 2),
            'tolid_share_pct': round(tolid_share * 100.0, 2),
            't_hoz': t_hoz, 'a_hoz': a_hoz, 'pct_hoz_raw': pct_hoz_raw,
            't_maj': t_maj, 'a_maj': a_maj, 'pct_maj_raw': pct_maj_raw,
            't_kha': t_kha, 'a_kha': a_kha, 'pct_kha_raw': pct_kha_raw,
            't_tol': t_tol, 'a_tol': a_tol, 'pct_tol_raw': pct_tol_raw
        })

    active_threshold = 0.0 if scale_mode == '0-100' else 70.0
    sorted_by_score = sorted(results, key=lambda x: (x['score'], x['total_realization_pct']), reverse=True)
    rank_map = {}
    active_rank = 1
    for item in sorted_by_score:
        dn = item['district']
        if item['score'] > active_threshold:
            rank_map[dn] = active_rank
            active_rank += 1
        else:
            rank_map[dn] = "-"

    for item in results:
        item['rank'] = rank_map[item['district']]

    # ==========================================
    # تولید فایل اکسل متمرکز سه شیته
    # ==========================================
    excel_filename = "نمرات_نهایی_نواحی.xlsx"
    script_dir = os.path.dirname(os.path.abspath(__file__)) if '__file__' in globals() else os.getcwd()
    out_path = os.path.join(script_dir, excel_filename)

    wb = openpyxl.Workbook()

    font_title = Font(name='Calibri', size=14, bold=True, color='1E3A8A')
    font_td = Font(name='Calibri', size=11, bold=False, color='0F172A')
    font_score = Font(name='Calibri', size=12, bold=True, color='047857')
    font_score_alt = Font(name='Calibri', size=11, bold=True, color='1E40AF')
    font_th = Font(name='Calibri', size=11, bold=True, color='FFFFFF')
    font_copy_dn = Font(name='Calibri', size=12, bold=True, color='0F172A')
    font_copy_sc = Font(name='Calibri', size=12, bold=True, color='047857')

    fill_copy_header = PatternFill(start_color='059669', end_color='059669', fill_type='solid')
    fill_compare_header = PatternFill(start_color='1F4E79', end_color='1F4E79', fill_type='solid')
    fill_audit_header = PatternFill(start_color='374151', end_color='374151', fill_type='solid')

    fill_tier_ali = PatternFill(start_color='D1FAE5', end_color='D1FAE5', fill_type='solid')
    fill_tier_motevaset = PatternFill(start_color='FEF3C7', end_color='FEF3C7', fill_type='solid')
    fill_tier_zaeef = PatternFill(start_color='FEE2E2', end_color='FEE2E2', fill_type='solid')
    fill_score_col = PatternFill(start_color='ECFDF5', end_color='ECFDF5', fill_type='solid')

    border_thin = Border(
        left=Side(style='thin', color='CBD5E1'), right=Side(style='thin', color='CBD5E1'),
        top=Side(style='thin', color='CBD5E1'), bottom=Side(style='thin', color='CBD5E1')
    )

    align_center = Alignment(horizontal='center', vertical='center')
    align_right = Alignment(horizontal='right', vertical='center')

    # ------------------------------------------
    # شیت ۱: فقط نام و نمره (ساده و آماده کپی مستقیم)
    # ------------------------------------------
    ws_raw = wb.active
    ws_raw.title = "فقط نام و نمره (ساده)"
    ws_raw.views.sheetView[0].rightToLeft = True

    ws_raw['A1'] = "نام ناحیه"
    ws_raw['B1'] = score_header_fa
    ws_raw['A1'].font = font_th
    ws_raw['B1'].font = font_th
    ws_raw['A1'].fill = fill_copy_header
    ws_raw['B1'].fill = fill_copy_header
    ws_raw['A1'].alignment = align_center
    ws_raw['B1'].alignment = align_center
    ws_raw['A1'].border = border_thin
    ws_raw['B1'].border = border_thin

    for idx, r in enumerate(results, start=2):
        ws_raw.cell(row=idx, column=1, value=r['district']).alignment = align_right
        ws_raw.cell(row=idx, column=2, value=r['score']).alignment = align_center
        ws_raw.cell(row=idx, column=1).font = font_copy_dn
        ws_raw.cell(row=idx, column=2).font = font_copy_sc
        ws_raw.cell(row=idx, column=1).border = border_thin
        ws_raw.cell(row=idx, column=2).border = border_thin

    ws_raw.column_dimensions['A'].width = 25
    ws_raw.column_dimensions['B'].width = 22

    # ------------------------------------------
    # شیت ۲: جدول مقایسه جامع دو مقیاس و جزئیات
    # ------------------------------------------
    ws_comp = wb.create_sheet(title="مقایسه دو مقیاس")
    ws_comp.views.sheetView[0].rightToLeft = True

    ws_comp.merge_cells('A1:H1')
    ws_comp['A1'] = f"جدول ارزیابی و مقایسه نمرات دوره {cfg['title']} در دو مقیاس واقعی (۰ تا ۱۰۰) و استاندارد نسرا (۷۰ تا ۱۰۰)"
    ws_comp['A1'].font = font_title
    ws_comp['A1'].alignment = align_center

    headers_comp = [
        ('ردیف', 8), ('نام ناحیه (شهرستان)', 24), ('نمره واقعی (۰ تا ۱۰۰)', 20),
        ('نمره نسرا (۷۰ تا ۱۰۰)', 20), ('سطح کیفی', 16), ('رتبه استانی', 14),
        ('درصد تحقق وزنی', 18), ('وضعیت و ماه‌های ثبت‌شده', 38)
    ]

    for c_idx, (h_title, w) in enumerate(headers_comp, start=1):
        cell = ws_comp.cell(row=3, column=c_idx, value=h_title)
        cell.font = font_th
        cell.fill = fill_compare_header
        cell.alignment = align_center
        cell.border = border_thin
        ws_comp.column_dimensions[get_column_letter(c_idx)].width = w

    for idx, r in enumerate(results, start=1):
        row_num = 3 + idx
        ws_comp.cell(row=row_num, column=1, value=idx).alignment = align_center
        ws_comp.cell(row=row_num, column=2, value=r['district']).alignment = align_right
        ws_comp.cell(row=row_num, column=3, value=r['score_real']).alignment = align_center
        ws_comp.cell(row=row_num, column=4, value=r['score_nasra']).alignment = align_center
        ws_comp.cell(row=row_num, column=5, value=r['tier']).alignment = align_center
        ws_comp.cell(row=row_num, column=6, value=r['rank']).alignment = align_center
        ws_comp.cell(row=row_num, column=7, value=f"{r['total_realization_pct']:.1f}%").alignment = align_center
        ws_comp.cell(row=row_num, column=8, value=r['status_desc']).alignment = align_right

        ws_comp.cell(row=row_num, column=1).font = font_td
        ws_comp.cell(row=row_num, column=2).font = font_copy_dn
        ws_comp.cell(row=row_num, column=3).font = font_copy_sc
        ws_comp.cell(row=row_num, column=4).font = font_score_alt
        ws_comp.cell(row=row_num, column=3).fill = fill_score_col

        tier_cell = ws_comp.cell(row=row_num, column=5)
        if r['tier'] == 'عالی': tier_cell.fill = fill_tier_ali
        elif r['tier'] == 'متوسط': tier_cell.fill = fill_tier_motevaset
        else: tier_cell.fill = fill_tier_zaeef

        ws_comp.cell(row=row_num, column=6).font = font_td
        ws_comp.cell(row=row_num, column=7).font = font_td
        ws_comp.cell(row=row_num, column=8).font = font_td

        for c in range(1, 9):
            ws_comp.cell(row=row_num, column=c).border = border_thin

    # ------------------------------------------
    # شیت ۳: گزارش پایش و ردگیری فایل‌های ورودی (Audit Trail)
    # ------------------------------------------
    ws_audit = wb.create_sheet(title="پایش فایل‌های ورودی")
    ws_audit.views.sheetView[0].rightToLeft = True

    ws_audit.merge_cells('A1:L1')
    ws_audit['A1'] = "گزارش وضعیت شناسایی، استخراج و پردازش تک‌تک فایل‌های اکسل در سیستم"
    ws_audit['A1'].font = font_title
    ws_audit['A1'].alignment = align_center

    headers_audit = [
        ('ردیف', 8), ('نام فایل', 30), ('پوشه / برچسب', 20), ('ماه منتسب', 14),
        ('ناحیه شناسایی‌شده', 22), ('روش شناسایی', 22),
        ('حضوری', 12), ('مجازی', 12), ('خلاقانه', 12), ('تولیدات', 12),
        ('وضعیت', 12), ('پیام خطا / توضیحات', 30)
    ]

    for c_idx, (h_title, w) in enumerate(headers_audit, start=1):
        cell = ws_audit.cell(row=3, column=c_idx, value=h_title)
        cell.font = font_th
        cell.fill = fill_audit_header
        cell.alignment = align_center
        cell.border = border_thin
        ws_audit.column_dimensions[get_column_letter(c_idx)].width = w

    for idx, a in enumerate(audit_logs, start=1):
        row_num = 3 + idx
        ws_audit.cell(row=row_num, column=1, value=idx).alignment = align_center
        ws_audit.cell(row=row_num, column=2, value=a['filename']).alignment = align_right
        ws_audit.cell(row=row_num, column=3, value=a['folder']).alignment = align_center
        ws_audit.cell(row=row_num, column=4, value=a['month'] or '-').alignment = align_center
        ws_audit.cell(row=row_num, column=5, value=a['district'] or '-').alignment = align_right
        ws_audit.cell(row=row_num, column=6, value=a['reason'] or '-').alignment = align_center
        ws_audit.cell(row=row_num, column=7, value=a['hoz']).alignment = align_center
        ws_audit.cell(row=row_num, column=8, value=a['maj']).alignment = align_center
        ws_audit.cell(row=row_num, column=9, value=a['kha']).alignment = align_center
        ws_audit.cell(row=row_num, column=10, value=a['tol']).alignment = align_center
        ws_audit.cell(row=row_num, column=11, value=a['status']).alignment = align_center
        ws_audit.cell(row=row_num, column=12, value=a['error_msg']).alignment = align_right

        status_cell = ws_audit.cell(row=row_num, column=11)
        if a['status'] == 'موفق':
            status_cell.fill = fill_tier_ali
        elif a['status'] == 'اخطار':
            status_cell.fill = fill_tier_motevaset
        else:
            status_cell.fill = fill_tier_zaeef

        for c in range(1, 13):
            ws_audit.cell(row=row_num, column=c).font = font_td
            ws_audit.cell(row=row_num, column=c).border = border_thin

    # ذخیره‌سازی فایل اکسل
    wb.save(out_path)
    if os.getcwd() != script_dir:
        try:
            wb.save(os.path.join(os.getcwd(), excel_filename))
        except Exception:
            pass

    # چاپ خروجی در ترمینال
    safe_print("\n" + "=" * 105)
    safe_print(f"📋 جدول نمرات دوره {cfg['title']} نواحی نسرا - {scale_label_fa}:")
    safe_print("=" * 105)
    safe_print(f"{'ردیف':^6} | {'نام ناحیه (شهرستان)':<20} | {score_header_fa:^18} | {'مقیاس دیگر':^14} | {'سطح کیفی':^10} | {'رتبه':^6} | {'تحقق کل':^10} | {'وضعیت ماه‌های ارسالی':<26}")
    safe_print("-" * 105)
    for idx, r in enumerate(results, start=1):
        other_sc = r['score_nasra'] if scale_mode == '0-100' else r['score_real']
        other_lbl = f"{other_sc:.1f} (نسرا)" if scale_mode == '0-100' else f"{other_sc:.1f} (واقعی)"
        safe_print(f"{idx:^6} | {r['district']:<20} | {r['score']:^18.1f} | {other_lbl:^14} | {r['tier']:^10} | {str(r['rank']):^6} | {r['total_realization_pct']:^8.1f}% | {r['status_desc']:<26}")
    safe_print("=" * 105)

    safe_print(f"\n🎉 فایل اکسل متمرکز با موفقیت تولید شد:")
    safe_print(f"   📄 «{out_path}»")
    safe_print(f"\n💡 در شیت ۱ («فقط نام و نمره (ساده)»)، ستون‌های نام و نمره انتخابی ({score_header_fa}) آماده کپی مستقیم هستند.")
    safe_print("💡 در شیت ۲، مقایسه همزمان هر دو مقیاس (واقعی ۰-۱۰۰ و نسرا ۷۰-۱۰۰) در کنار هم قرار دارد.")
    safe_print("💡 در شیت ۳ («پایش فایل‌های ورودی»)، گزارش ردگیری و شناسایی تک‌تک فایل‌های اکسل در دسترس است.")
    safe_print("=" * 105)

    if sys.platform == 'win32':
        try:
            os.startfile(out_path)
        except Exception:
            pass

    return results

if __name__ == '__main__':
    arg_m = 3
    arg_scale = '0-100'

    if len(sys.argv) > 1:
        raw_m = sys.argv[1].strip()
        if raw_m in ['2', '3', '6']:
            arg_m = int(raw_m)
        elif raw_m in ['0', 'real', '0-100']:
            arg_scale = '0-100'
        elif raw_m in ['70', 'nasra', '70-100']:
            arg_scale = '70-100'

    if len(sys.argv) > 2:
        raw_s = sys.argv[2].strip().lower()
        if raw_s in ['0', 'real', '0-100', '1']:
            arg_scale = '0-100'
        elif raw_s in ['70', 'nasra', '70-100', '2']:
            arg_scale = '70-100'

    # در صورت اجرای تعاملی بدون پارامترهای خط فرمان
    if len(sys.argv) == 1:
        safe_print("\n=======================================================")
        safe_print("  سامانه ارزیابی عملکرد دوره‌ای نواحی نسرا (اصفهان)")
        safe_print("=======================================================")
        safe_print("لطفاً بازه زمانی ارزیابی را انتخاب فرمایید:")
        safe_print("  [1] عملکرد ۲ ماهه")
        safe_print("  [2] عملکرد ۳ ماهه (فصلی - پیش‌فرض)")
        safe_print("  [3] عملکرد ۶ ماهه")
        
        try:
            choice = input("\nشماره گزینه (1 / 2 / 3) [پیش‌فرض 2]: ").strip()
            if choice == '1':
                arg_m = 2
            elif choice == '3':
                arg_m = 6
            else:
                arg_m = 3
        except (EOFError, KeyboardInterrupt):
            arg_m = 3

        safe_print("\nلطفاً مقیاس نمره‌دهی مورد نظر را انتخاب فرمایید:")
        safe_print("  [1] مقیاس واقعی (۰ تا ۱۰۰ - پیش‌فرض)")
        safe_print("  [2] مقیاس استاندارد نسرا (۷۰ تا ۱۰۰)")
        
        try:
            choice_sc = input("\nشماره گزینه (1 / 2) [پیش‌فرض 1]: ").strip()
            if choice_sc == '2':
                arg_scale = '70-100'
            else:
                arg_scale = '0-100'
        except (EOFError, KeyboardInterrupt):
            arg_scale = '0-100'

    run_period_evaluation(selected_months=arg_m, selected_scale=arg_scale)
