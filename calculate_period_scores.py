# -*- coding: utf-8 -*-
"""
========================================================================================
سامانه هوشمند ارزیابی عملکرد دوره‌ای نواحی نسرا - استان اصفهان
NASRA Intelligent Multi-Period District Performance Evaluation System
========================================================================================
ویژگی‌های کلیدی موتور بازطراحی‌شده:
1. کشف و رهگیری ۱۰۰٪ هوشمند فایل‌ها در پوشه‌های ماهانه (تیر، مرداد، شهریور) و زیرپوشه‌ها
2. شناسایی چندلایه‌ای نام شهرستان از نام فایل، پوشه والد، نام شیت و اسکن محتوای سلول‌ها
3. تفکیک هوشمند شیت‌های اطلاعات پایه/رفرنس (جلوگیری از تشخیص اشتباه نام شهرستان)
4. استخراج دقیق شاخص‌ها حتی با اعداد فارسی، خطوط خالی، فرمول‌ها و ستون‌های جابجا شده
5. پشتیبانی از دوره‌های ۲، ۳ و ۶ ماهه با مقیاس‌های انتخابی واقعی (۰ تا ۱۰۰) و استاندارد نسرا (۷۰ تا ۱۰۰)
6. اعمال مدل مصوب سرریز آموزش: جبران متقابل حضوری و مجازی در سبد ۴۰٪ + انتقال مازاد به خلاقانه
========================================================================================
"""

import os
import sys
import re
import shutil
import unicodedata
from collections import Counter
import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

# ==============================================================================
# ۱. تعاریف پایه، شهرستان‌ها، حوزه‌ها و اوزان ارزیابی
# ==============================================================================

DISTRICTS = [
    'آران و بیدگل', 'اردستان', 'امام حسین(ع)', 'امام رضا(ع)', 'امام صادق(ع)',
    'امام علی(ع)', 'برخوار', 'بویین و میاندشت', 'تیران و کرون', 'جرقویه',
    'چادگان', 'خمینی شهر', 'خوانسار', 'خور و بیابانک', 'درچه',
    'دهاقان', 'سمیرم', 'شاهین شهر', 'شهرضا', 'فریدن',
    'فریدون شهر', 'فلاورجان', 'کاشان', 'کوهپایه', 'گلپایگان',
    'لنجان', 'مبارکه', 'نایین', 'نجف آباد', 'نطنز',
    'ورزنه', 'هرند'
]

# تعداد حوزه‌های مقاومت تابعه هر شهرستان (مجموع استان: ۲۲۹ حوزه)
BRANCH_MAP = {
    'آران و بیدگل': 10, 'اردستان': 6, 'امام حسین(ع)': 5, 'امام رضا(ع)': 22,
    'امام صادق(ع)': 22, 'امام علی(ع)': 6, 'برخوار': 5, 'بویین و میاندشت': 3,
    'تیران و کرون': 5, 'جرقویه': 3, 'چادگان': 4, 'خمینی شهر': 12,
    'خوانسار': 3, 'خور و بیابانک': 2, 'درچه': 3, 'دهاقان': 3,
    'سمیرم': 6, 'شاهین شهر': 11, 'شهرضا': 8, 'فریدن': 3,
    'فریدون شهر': 3, 'فلاورجان': 9, 'کاشان': 21, 'کوهپایه': 3,
    'گلپایگان': 5, 'لنجان': 14, 'مبارکه': 8, 'نایین': 4,
    'نجف آباد': 15, 'نطنز': 4, 'ورزنه': 3, 'هرند': 3
}

# ضرایب پایه ماهانه به ازای هر ۱ حوزه مقاومت
BASE_HOZORI = 31     # حضوری ماهانه به ازای هر حوزه
BASE_MAJAZI = 217    # مجازی ماهانه به ازای هر حوزه
BASE_KHALAGH = 62    # خلاقانه ماهانه به ازای هر حوزه
BASE_TOLID = 3       # تولیدات رسانه‌ای ماهانه به ازای هر حوزه

# اوزان مصوب مدل ارزیابی عملکرد (مجموع ۱۰۰٪)
WEIGHT_HOZORI = 0.10        # وزن حضوری (۱۰٪)
WEIGHT_MAJAZI = 0.30        # وزن مجازی (۳۰٪)
WEIGHT_TRAINING_POOL = 0.40 # سقف سبد کلی آموزش (۴۰٪)
WEIGHT_KHALAGH = 0.50       # وزن اقدامات خلاقانه (۵۰٪)
WEIGHT_TOLID = 0.10         # وزن تولیدات رسانه‌ای (۱۰٪)

# ماه‌های شمسی جهت شناسایی دوره‌ها و پوشه‌ها
PERSIAN_MONTHS = {
    1: ['فروردین', 'farvardin', '01'],
    2: ['اردیبهشت', 'ordibehesht', '02'],
    3: ['خرداد', 'khordad', '03'],
    4: ['تیر', 'tir', '04'],
    5: ['مرداد', 'mordad', '05'],
    6: ['شهریور', 'shahrivar', '06'],
    7: ['مهر', 'mehr', '07'],
    8: ['آبان', 'aban', '08'],
    9: ['آذر', 'azar', '09'],
    10: ['دی', 'dey', '10'],
    11: ['بهمن', 'bahman', '11'],
    12: ['اسفند', 'esfand', '12']
}

# نام‌های مستعار و نگارش‌های گوناگون فارسی و انگلیسی شهرستان‌ها
DISTRICT_ALIASES = {
    'آران و بیدگل': ['آران', 'بیدگل', 'اران و بیدگل', 'اران بیدگل', 'آران بیدگل', 'aran', 'bidgol'],
    'اردستان': ['اردستان', 'زوار', 'مهاباد اردستان', 'ardestan', 'ardestn'],
    'امام حسین(ع)': ['امام حسین', 'حسین ع', 'ناحیه امام حسین', 'emam hossein', 'hosein', 'hossein', 'emam hosein'],
    'امام رضا(ع)': ['امام رضا', 'رضا ع', 'ناحیه امام رضا', 'emam reza', 'reza'],
    'امام صادق(ع)': ['امام صادق', 'صادق ع', 'ناحیه امام صادق', 'emam sadegh', 'sadegh', 'sadeq'],
    'امام علی(ع)': ['امام علی', 'علی ع', 'ناحیه امام علی', 'emam ali'],
    'برخوار': ['برخوار', 'دولت آباد', 'دولت‌آباد', 'دستگرد', 'borkhar', 'barkhar', 'dolatabad'],
    'بویین و میاندشت': ['بویین', 'میاندشت', 'بوئین', 'بویین میاندشت', 'بوئین میاندشت', 'boin', 'bouin', 'buin', 'miandasht'],
    'تیران و کرون': ['تیران', 'کرون', 'تیران کرون', 'تیران وکرون', 'tiran', 'karvan', 'koron'],
    'جرقویه': ['جرقویه', 'نیک آباد', 'jarghooyeh', 'jarghooye', 'jarqavieh'],
    'چادگان': ['چادگان', 'chadegan'],
    'خمینی شهر': ['خمینی شهر', 'خمینی‌شهر', 'همایون شهر', 'khomeini', 'khomeinishahr'],
    'خوانسar': ['خوانسار', 'khansar', 'khwansar'],
    'خوانسار': ['خوانسار', 'khansar', 'khwansar'],
    'خور و بیابانک': ['خور', 'بیابانک', 'خور و بیابانک', 'خوروبیابانک', 'خور بیابانک', 'جندق', 'khoor', 'khur', 'biabanak'],
    'درچه': ['درچه', 'dorcheh', 'dorche', 'dorce'],
    'دهاقان': ['دهاقان', 'عطاآباد', 'dehaqan', 'dehaghan'],
    'سمیرم': ['سمیرم', 'semirom'],
    'شاهین شهر': ['شاهین شهر', 'شاهین‌شهر', 'شاهین', 'shahin', 'shahinshahr'],
    'شهرضا': ['شهرضا', 'قمشه', 'shahreza'],
    'فریدن': ['فریدن', 'داران', 'fereydan', 'fereidan', 'daran'],
    'فریدون شهر': ['فریدون شهر', 'فریدون‌شهر', 'فریدونشهر', 'fereydunshahr', 'fereydoonshahr'],
    'فلاورجان': ['فلاورجان', 'قاهدریجان', 'falavarjan'],
    'کاشان': ['کاشان', 'قمصر', 'kashan'],
    'کوهپایه': ['کوهپایه', 'تودشک', 'koohpayeh', 'kuhpayeh'],
    'گلپایگان': ['گلپایگان', 'گلپايگان', 'گوگد', 'گلشهر', 'golpayegan', 'golpaygan', 'golpaigan', 'golpaegan', 'golpaygon'],
    'لنجان': ['لنجان', 'زرین شهر', 'زرین‌شهر', 'سده لنجان', 'lenjan', 'zarrinshahr'],
    'مبارکه': ['مبارکه', 'دیزیچه', 'mobarakeh', 'mobarake'],
    'نایین': ['نایین', 'نائین', 'انارک', 'بافران', 'naeen', 'nain', 'naein'],
    'نجف آباد': ['نجف آباد', 'نجف‌آباد', 'یزدانشهر', 'گلدشت', 'najafabad', 'najaf abad'],
    'نطنز': ['نطنز', 'بادرود', 'natanz', 'badrood'],
    'ورزنه': ['ورزنه', 'varzaneh', 'varzane'],
    'هرند': ['هرند', 'اژیه', 'harand']
}

PERIOD_CONFIGS = {
    2: {
        'months': 2,
        'title': '۲ ماهه',
        'title_en': '2-Month',
        'desc': 'دوره ۲ ماهه (تجمیع ۲ پوشه ماهانه یا فایل‌های مستقیم)',
        'hozori_mult': BASE_HOZORI * 2,    # 62
        'majazi_mult': BASE_MAJAZI * 2,    # 434
        'khalagh_mult': BASE_KHALAGH * 2,  # 124
        'tolid_mult': BASE_TOLID * 2       # 6
    },
    3: {
        'months': 3,
        'title': '۳ ماهه',
        'title_en': '3-Month',
        'desc': 'دوره ۳ ماهه (تجمیع ۳ پوشه ماهانه یا فایل‌های مستقیم)',
        'hozori_mult': BASE_HOZORI * 3,    # 93
        'majazi_mult': BASE_MAJAZI * 3,    # 651
        'khalagh_mult': BASE_KHALAGH * 3,  # 186
        'tolid_mult': BASE_TOLID * 3       # 9
    },
    6: {
        'months': 6,
        'title': '۶ ماهه',
        'title_en': '6-Month',
        'desc': 'دوره ۶ ماهه (تجمیع ۶ پوشه ماهانه یا فایل‌های مستقیم)',
        'hozori_mult': BASE_HOZORI * 6,    # 186
        'majazi_mult': BASE_MAJAZI * 6,    # 1302
        'khalagh_mult': BASE_KHALAGH * 6,  # 372
        'tolid_mult': BASE_TOLID * 6       # 18
    }
}

# ==============================================================================
# ۲. توابع استانداردسازی، پیش‌پردازش متن و تطبیق هوشمند نام‌ها
# ==============================================================================

def clean_str(val):
    """
    نرمال‌سازی پیشرفته رشته‌های فارسی/عربی با حذف نویز، پیشوندها و نویسه‌های زائد.
    به کارگیری فرم استاندارد NFKC یونیکد جهت یکپارچه‌سازی ارقام و کاراکترها.
    """
    if val is None:
        return ''
    s = unicodedata.normalize('NFKC', str(val)).strip()
    # استانداردسازی حروف عربی و فارسی
    s = s.replace('ي', 'ی').replace('ك', 'ک').replace('ة', 'ه').replace('ۀ', 'ه')
    s = s.replace('آ', 'ا').replace('أ', 'ا').replace('إ', 'ا').replace('ئ', 'ی')
    # حذف اعراب و نشانه‌های صوتی
    s = re.sub(r'[\u064B-\u065F\u0670]', '', s)
    # حذف ایمن نشانه‌های احترام بدون حذف کاراکتر 'ع' در نام‌هایی چون 'علی'
    s = re.sub(r'\s*\([عeE]\)|\s*\[[عeE]\]|\s*\([عeE][جjJ]\)|\s*\([رr][هh]\)', '', s)
    s = re.sub(r'علیه\s*السلام', '', s)
    # حذف پیشوندهای متداول اداری
    prefixes = [
        'ناحیه مقاومت بسیج', 'ناحیه مقاومت', 'سپاه ناحیه', 'سپاه',
        'کانون سواد فضای مجازی', 'قرارگاه فضای مجازی', 'فضای مجازی',
        'کانون', 'دفتر', 'ناحیه', 'شهرستان', 'بسیج'
    ]
    for p in prefixes:
        s = s.replace(p, '')
    # حذف علائم نگارشی، پرانتزها، اعداد و فواصل غیرضروری
    s = re.sub(r'[\(\)\[\]\{\}\.\_\-\:\/\d\s\u200c\u00a0]+', '', s)
    return s

def clean_no_vav(val):
    return clean_str(val).replace('و', '')

def match_district_name(text):
    """
    تطبیق جامع و فوق‌العاده دقیق نام شهرستان با استفاده از الگوریتم لایه‌ای:
    1. تطبیق کامل با نام‌های ۳۲ ناحیه
    2. تطبیق نام‌های ۳۲ ناحیه داخل متن (زیررشته با طول >= ۴)
    3. بررسی نام‌های مستعار و نگارش‌های گوناگون (فارسی و لاتین)
    4. تطبیق بدون حرف ربط 'و' (مانند آران بیدگل، تیران کرون)
    """
    if not text:
        return None
    s_raw = str(text).strip()
    if not s_raw:
        return None

    # نادیده‌گرفتن برچسب‌های عمومی و سیستمی
    ignored_labels = {
        'reports', 'پوشه reports', 'پوشه اصلی برنامه', 'گزارش مستقیم',
        'گزارش دوره‌ای', 'گزارش ماهانه', 'کارنامه', 'داشبورد', 'دانلود'
    }
    if s_raw in ignored_labels:
        return None

    c_raw = clean_str(s_raw)
    c_raw_nv = clean_no_vav(s_raw)
    s_lower = s_raw.lower()

    # ۱. تطبیق نام‌های کانونی
    for d in DISTRICTS:
        cd = clean_str(d)
        if cd and (cd == c_raw or (len(cd) >= 4 and cd in c_raw)):
            return d

    # ۲. تطبیق نام‌های مستعار و نگارش‌های گوناگون
    for d, aliases in DISTRICT_ALIASES.items():
        for al in aliases:
            al_clean = clean_str(al)
            if al_clean and (al_clean == c_raw or (len(al_clean) >= 4 and al_clean in c_raw)):
                return d
            al_low = al.lower()
            if len(al_low) >= 4 and al_low in s_lower:
                return d

    # ۳. تطبیق بدون حرف 'و' (مثلاً آران بیدگل یا خور بیابانک)
    for d in DISTRICTS:
        cd_nv = clean_no_vav(d)
        if cd_nv and (cd_nv == c_raw_nv or (len(cd_nv) >= 4 and cd_nv in c_raw_nv)):
            return d

    return None

def detect_month_from_name(text):
    """
    شناسایی دقیق ماه از روی نام پوشه یا فایل (مانند تیر، مرداد 1405، شهریور، tir_report)
    با حفاظت در برابر تداخل نام تیر و تیران
    """
    if not text:
        return None
    s = str(text).strip()
    months = [
        (1, 'فروردین', ['فروردین', 'farvardin']),
        (2, 'اردیبهشت', ['اردیبهشت', 'ordibehesht']),
        (3, 'خرداد', ['خرداد', 'khordad']),
        (4, 'تیر', ['تیر', 'tir']),
        (5, 'مرداد', ['مرداد', 'mordad']),
        (6, 'شهریور', ['شهریور', 'shahrivar']),
        (7, 'مهر', ['مهر', 'mehr']),
        (8, 'آبان', ['آبان', 'aban']),
        (9, 'آذر', ['آذر', 'azar']),
        (10, 'دی', ['دی', 'dey']),
        (11, 'بهمن', ['بهمن', 'bahman']),
        (12, 'اسفند', ['اسفند', 'esfand'])
    ]
    norm_s = unicodedata.normalize('NFKC', s).replace('ي', 'ی').replace('ك', 'ک')
    norm_s_lower = norm_s.lower()

    for m_num, m_canonical, aliases in months:
        for al in aliases:
            if al in norm_s or al in norm_s_lower:
                if al == 'تیر' and 'تیران' in norm_s:
                    continue
                return m_canonical
    return None

def parse_number(val):
    """
    استخراج ایمن مقدار عددی از سلول‌های اکسل:
    - تبدیل ارقام فارسی و عربی به انگلیسی
    - حذف جداکننده‌های هزارگان، واحدهای نفر، مخاطب، صفحه و غیره
    - ممانعت از استخراج نادرست تاریخ‌ها (مانند 1405/05/15) به جای تعداد
    """
    if val is None:
        return 0
    if isinstance(val, (int, float)):
        return int(val) if isinstance(val, int) or (isinstance(val, float) and val.is_integer()) else val

    s = unicodedata.normalize('NFKC', str(val)).strip()
    if not s:
        return 0

    # بررسی عدم تفسیر تاریخ به عنوان عدد (مثلا تاریخ 1405/05/15 نباید 1405 استخراج شود)
    if re.search(r'\d{2,4}[/-]\d{1,2}[/-]\d{1,2}', s):
        return 0

    # تبدیل ارقام فارسی و عربی
    p_digits = '۰۱۲۳۴۵۶۷۸۹'
    a_digits = '٠١٢٣٤٥٦٧٨٩'
    for i in range(10):
        s = s.replace(p_digits[i], str(i)).replace(a_digits[i], str(i))

    # حذف جداکننده‌ها و واحدهای متنی
    for sep in [',', '،', '٬', '٫', '\u066c', '\u066b', ' ', '\u00a0', '\u200c', 'نفر', 'مخاطب', 'عدد', 'صفحه', 'بازدید', 'مورد']:
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
# ۳. موتور فوق‌هوشمند شناسایی ناحیه از فایل اکسل (District Detection Engine)
# ==============================================================================

def detect_district_from_workbook(wb, fpath, folder_label=''):
    """
    موتور ۵ لایه‌ای و دقیق کشف ناحیه:
    لایه ۱: نام فایل (مثلا گلپایگان.xlsx، گزارش گلپایگان.xlsx)
    لایه ۲: نام پوشه ماه یا زیرپوشه (مثلا reports/تیر/گلپایگان/)
    لایه ۳: نام شیت‌های داخل فایل اکسل
    لایه ۴: متادیتای عنوان فایل اکسل
    لایه ۵: اسکن عمیق سلول‌ها با فیلتر هوشمند شیت‌های اطلاعات پایه و لیست‌های استانی
    """
    fname = os.path.basename(fpath)
    
    # ۱. بررسی نام فایل
    d = match_district_name(fname)
    if d:
        return d, "نام فایل"

    # ۲. بررسی برچسب پوشه و مسیر پوشه‌های والد
    if folder_label and folder_label not in ['reports', 'پوشه reports', 'پوشه اصلی برنامه']:
        # تفکیک بخش‌های مسیر در صورت تودرتو بودن پوشه‌ها
        for part in folder_label.replace('\\', '/').split('/'):
            d = match_district_name(part)
            if d:
                return d, f"پوشه «{part}»"

    parent_dir = os.path.basename(os.path.dirname(os.path.abspath(fpath)))
    if parent_dir and parent_dir not in ['Font', 'reports', 'Downloads']:
        d = match_district_name(parent_dir)
        if d:
            return d, f"پوشه والد «{parent_dir}»"

    # ۳. بررسی نام شیت‌ها
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

    # ۵. اسکن عمیق سلول‌ها با فیلتر دقیق شیت‌های رفرنس (اطلاعات پایه)
    district_counts = Counter()
    
    for sname in wb.sheetnames:
        cn = clean_str(sname)
        # شناسایی اولیه نام شیت‌های پایه
        if any(w in cn for w in ['اطلاعاتپایه', 'dropdown', 'base', 'ref', 'لیست', 'پایه', 'پایگاهداده']):
            continue

        ws = wb[sname]
        max_r = min(ws.max_row + 1, 80)
        max_c = min(ws.max_column + 1, 20)

        # بررسی شیت از نظر وجود رفرنس عمومی (اگر بیش از ۳ شهرستان متفاوت در شیت باشد، شیت رفرنس است)
        found_in_sheet = set()
        sheet_hits = Counter()

        for r in range(1, max_r):
            for c in range(1, max_c):
                v = ws.cell(r, c).value
                if v is not None:
                    md = match_district_name(v)
                    if md:
                        found_in_sheet.add(md)
                        # سرفصل‌ها و عناوین بالای جدول وزن بالاتری دریافت می‌کنند
                        weight = 5 if r <= 8 else 1
                        sheet_hits[md] += weight

        # اگر در یک شیت چندین شهرستان گوناگون لیست شده باشند، آن شیت رفرنس است و کنار گذاشته می‌شود
        if len(found_in_sheet) > 3:
            continue

        for dist, cnt in sheet_hits.items():
            district_counts[dist] += cnt

    if district_counts:
        best_district, count = district_counts.most_common(1)[0]
        return best_district, f"محتوای سلول‌های شیت ({count} بار مشاهده)"

    return None, "عدم شناسایی"

# ==============================================================================
# ۴. موتور استخراج شاخص‌ها از کاربرگ‌های اکسل (Indicator Extraction Engine)
# ==============================================================================

def extract_sheet_metrics(ws):
    """
    استخراج تعداد مخاطبان و تعداد برنامه‌ها از یک شیت با شناسایی خودکار ستون آمار
    """
    if ws is None:
        return {'people_sum': 0, 'classes_count': 0}

    target_col = None
    header_keywords_primary = [
        'نفر', 'بازدید', 'مخاطب', 'شرکت', 'تیراژ', 'مجموع', 'فراگیر',
        'حاضر', 'دانش', 'بسیج', 'عموم', 'people', 'view', 'participants', 'attendee'
    ]
    header_keywords_secondary = [
        'تعداد', 'صفحه', 'صفحات', 'میزان', 'آمار', 'جمعیت', 'count', 'total', 'number'
    ]

    header_row = 1
    # جستجوی ستون در ۱۰ ردیف نخست جهت پوشش هدرهای ترکیبی
    for r in range(1, min(ws.max_row + 1, 11)):
        for c in range(1, ws.max_column + 1):
            h = str(ws.cell(r, c).value or '').lower()
            if any(k in h for k in header_keywords_primary):
                target_col = c
                header_row = r
                break
        if target_col:
            break

    if target_col is None:
        for r in range(1, min(ws.max_row + 1, 11)):
            for c in range(1, ws.max_column + 1):
                h = str(ws.cell(r, c).value or '').lower()
                if any(k in h for k in header_keywords_secondary):
                    target_col = c
                    header_row = r
                    break
            if target_col:
                break

    total_people = 0
    active_classes = 0
    start_row = header_row + 1

    for r in range(start_row, ws.max_row + 1):
        # بررسی فعال بودن ردیف (ردیف‌های خالی یا صرفاً دارای شماره ردیف نادیده گرفته می‌شوند)
        has_act = any(
            ws.cell(r, c).value is not None and str(ws.cell(r, c).value).strip() != ''
            for c in range(2, ws.max_column + 1)
        )
        if not has_act and ws.cell(r, 1).value is not None and len(str(ws.cell(r, 1).value).strip()) > 3:
            has_act = True

        if has_act:
            active_classes += 1
            if target_col:
                v = ws.cell(r, target_col).value
                p_num = parse_number(v)
                total_people += p_num
            else:
                # جستجوی مقادیر عددی در ردیف
                for c in range(2, ws.max_column + 1):
                    val = ws.cell(r, c).value
                    p_num = parse_number(val)
                    if p_num > 0 and p_num != r:
                        total_people += p_num
                        break

    return {
        'people_sum': int(total_people),
        'classes_count': active_classes
    }

def extract_workbook_indicators(wb):
    """
    استخراج ۴ شاخص عملکردی اصلی از تمام کاربرگ‌های فایل ارسالی
    """
    ws_hoz = None
    ws_tav = None
    ws_maj = None
    ws_kha = None
    ws_tol = None

    for sname in wb.sheetnames:
        cn = clean_str(sname)
        if 'حضوری' in cn or 'کارگاه' in cn or 'کلاس' in cn or 'همایش' in cn:
            ws_hoz = wb[sname]
        elif 'توانمند' in cn:
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

        hoz_val = m_hoz['people_sum'] + m_tav['people_sum']
        if hoz_val == 0:
            hoz_val = m_hoz['classes_count'] + m_tav['classes_count']

        maj_val = m_maj['people_sum'] if m_maj['people_sum'] > 0 else m_maj['classes_count']
        kha_val = m_kha['people_sum'] if m_kha['people_sum'] > 0 else m_kha['classes_count']
        tol_val = m_tol['people_sum'] if m_tol['people_sum'] > 0 else m_tol['classes_count']

        return hoz_val, maj_val, kha_val, tol_val

    # حالت جایگزین برای کاربرگ‌های خلاصه‌شده یا فایل‌های تک‌شیت
    ws = wb.active
    hoz_val, maj_val, kha_val, tol_val = 0, 0, 0, 0
    for r in range(1, ws.max_row + 1):
        row_text = ' '.join(str(ws.cell(r, c).value or '') for c in range(1, ws.max_column + 1))
        row_val = 0
        for c in range(ws.max_column, 0, -1):
            val = ws.cell(r, c).value
            p_num = parse_number(val)
            if p_num > 0 and p_num != r:
                row_val = p_num
                break

        if 'حضوری' in row_text or 'توانمند' in row_text or 'کارگاه' in row_text:
            hoz_val += row_val
        elif 'مجازی' in row_text or 'لایو' in row_text or 'وبینار' in row_text or 'آنلاین' in row_text:
            maj_val += row_val
        elif 'خلاق' in row_text or 'مسابقه' in row_text or 'پویش' in row_text:
            kha_val += row_val
        elif 'تولید' in row_text or 'رسانه' in row_text or 'کلیپ' in row_text:
            tol_val += row_val

    return hoz_val, maj_val, kha_val, tol_val

# ==============================================================================
# ۵. موتور اسکن و کشف ساختار پوشه‌ها و فایل‌های گزارش
# ==============================================================================

def scan_reports_directory(reports_dir='reports'):
    """
    اسکن جامع و عمیق فایل‌های اکسل در ساختار پوشه‌ها:
    - اسکن پوشه reports و تمام زیرپوشه‌های ماهانه (تیر، مرداد، شهریور)
    - اسکن پوشه‌های ماهانه در ریشه برنامه (در صورت قرارگیری پوشه تیر در کنار اسکریپت)
    - پشتیبانی از تمام پسوندهای متداول اکسل (.xlsx, .xlsm, .xls) بزرگ و کوچک
    - نادیده‌گرفتن فایل‌های موقت، پشتیبان‌ها و فایل‌های مستر سامانه
    """
    os.makedirs(reports_dir, exist_ok=True)
    
    files_list = []
    seen_paths = set()

    # ۱. اسکن بازگشتی داخل پوشه reports
    for root, dirs, files in os.walk(reports_dir):
        for f in sorted(files):
            if f.lower().endswith(('.xlsx', '.xlsm', '.xls')) and not f.startswith('~$'):
                full_p = os.path.abspath(os.path.join(root, f))
                if full_p not in seen_paths:
                    seen_paths.add(full_p)
                    rel_dir = os.path.relpath(root, reports_dir)
                    folder_label = '' if rel_dir == '.' else rel_dir
                    files_list.append((os.path.join(root, f), folder_label, f))

    # ۲. اسکن پوشه‌های ماهانه که ممکن است مستقیماً در کنار فایل برنامه قرار گرفته باشند
    month_keywords = ['فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور', 'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند']
    for entry in sorted(os.listdir('.')):
        if os.path.isdir(entry) and not entry.startswith('.') and entry != 'reports' and entry != 'output_cards_3months':
            if any(m in entry for m in month_keywords) or match_district_name(entry):
                for root, dirs, files in os.walk(entry):
                    for f in sorted(files):
                        if f.lower().endswith(('.xlsx', '.xlsm', '.xls')) and not f.startswith('~$'):
                            full_p = os.path.abspath(os.path.join(root, f))
                            if full_p not in seen_paths:
                                seen_paths.add(full_p)
                                files_list.append((os.path.join(root, f), entry, f))

    # ۳. اسکن فایل‌های مستقیم قرار داده شده در ریشه برنامه
    excluded_files = {
        'نمرات_نهایی_نواحی.xlsx', 'final_scores.xlsx',
        'تهیه کارنامه نواحی.xlsx', 'تهیه کارنامه ۳ ماهه نواحی.xlsx',
        'master_monitoring.xlsx', 'monthly_scorecard.xlsx',
        'گزارش شهریور ماه 1405 ناحیه.xlsx',
        'نمرات_عملکرد_۲ماهه.xlsx', 'نمرات_عملکرد_۳ماهه.xlsx', 'نمرات_عملکرد_۶ماهه.xlsx'
    }
    for f in sorted(os.listdir('.')):
        if f.lower().endswith(('.xlsx', '.xlsm', '.xls')) and not f.startswith('~$') and f not in excluded_files and not f.startswith('کارنامه_'):
            full_p = os.path.abspath(f)
            if full_p not in seen_paths:
                seen_paths.add(full_p)
                files_list.append((f, 'پوشه اصلی برنامه', f))

    # جمع‌آوری نام پوشه‌های زیرمجموعه شناخته‌شده
    detected_subdirs = sorted(list({flabel for _, flabel, _ in files_list if flabel and flabel != 'پوشه اصلی برنامه'}))

    print(f"🔍 گزارش جستجوی فایل‌های اکسل در سیستم:")
    if detected_subdirs:
        print(f"   📂 پوشه‌های شناسایی‌شده: {len(detected_subdirs)} پوشه ({', '.join(detected_subdirs)})")
    print(f"   📄 مجموع فایل‌های اکسل کشف شده برای ارزیابی: {len(files_list)} فایل")
    for fpath, flabel, fname in files_list:
        loc = f"در پوشه «{flabel}»" if flabel else f"مستقیم در پوشه {reports_dir}"
        print(f"      • {fname} ({loc})")

    return detected_subdirs, files_list

# ==============================================================================
# ۶. تعیین سطوح کیفی سه‌گانه
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
# ۷. موتور اصلی محاسبه نمرات دوره‌ای و تولید اکسل متمرکز
# ==============================================================================

def run_period_evaluation(selected_months=None, selected_scale=None):
    n_months = selected_months if selected_months in [2, 3, 6] else 3
    scale_mode = selected_scale if selected_scale in ['0-100', '70-100'] else '0-100'

    cfg = PERIOD_CONFIGS[n_months]
    scale_label_fa = "مقیاس واقعی (۰ تا ۱۰۰)" if scale_mode == '0-100' else "مقیاس استاندارد نسرا (۷۰ تا ۱۰۰)"
    score_header_fa = "نمره واقعی (۰-۱۰۰)" if scale_mode == '0-100' else "نمره نسرا (۷۰-۱۰۰)"

    print("\n" + "=" * 80)
    print(f"📌 دوره انتخابی: عملکرد {cfg['title']} ({cfg['title_en']})")
    print(f"📊 مقیاس انتخابی نمره‌دهی: {scale_label_fa}")
    print(f"🎯 حدانتظارها: ضریب {n_months} برابری اهداف ماهانه")
    print("⚖️ اوزان ارزیابی: ۱۰٪ حضوری | ۳۰٪ مجازی (سرشکن در سبد ۴۰٪ آموزش) | ۵۰٪ خلاقانه (سقف ۱۰۰٪) | ۱۰٪ تولیدات")
    print("=" * 80)

    subdirs, all_files = scan_reports_directory('reports')
    print("-" * 80)

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

    # پردازش تک‌تک فایل‌های کشف‌شده
    for fpath, folder_label, fname in all_files:
        try:
            wb = openpyxl.load_workbook(fpath, data_only=True)
            detected, detection_reason = detect_district_from_workbook(wb, fpath, folder_label)

            if not detected:
                print(f"⚠️ شناسایی ناحیه برای فایل '{fname}' (در پوشه '{folder_label}') ناموفق بود.")
                continue

            hoz_val, maj_val, kha_val, tol_val = extract_workbook_indicators(wb)

            # برچسب‌گذاری ماه دریافتی
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

            src_info = f"پوشه «{folder_label}»" if folder_label else "پوشه مستقیم"
            print(f"✓ [{detected}] (شناسایی از: {detection_reason} | {src_info} | ماه: {month_tag}): +{hoz_val} حضوری | +{maj_val} مجازی | +{kha_val} خلاقانه | +{tol_val} تولید")

        except Exception as e:
            print(f"❌ خطا در پردازش فایل '{fname}': {e}")

    print("-" * 80)
    print("📊 محاسبه نمرات بر مبنای اوزان مصوب، سرریز آموزش و سقف ۱۰۰٪...")

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
                status_desc = f"تحویل {f_cnt} از {n_months} ماه"
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

    # تولید فایل اکسل متمرکز و بهینه‌سازی شده
    excel_filename = "نمرات_نهایی_نواحی.xlsx"
    wb = openpyxl.Workbook()

    font_title = Font(name='Calibri', size=15, bold=True, color='1E3A8A')
    font_td = Font(name='Calibri', size=11, bold=False, color='0F172A')
    font_score = Font(name='Calibri', size=12, bold=True, color='047857')
    font_score_alt = Font(name='Calibri', size=11, bold=True, color='1E40AF')
    font_copy_th = Font(name='Calibri', size=12, bold=True, color='FFFFFF')
    font_copy_dn = Font(name='Calibri', size=12, bold=True, color='0F172A')
    font_copy_sc = Font(name='Calibri', size=12, bold=True, color='047857')

    fill_copy_header = PatternFill(start_color='059669', end_color='059669', fill_type='solid')
    fill_compare_header = PatternFill(start_color='1F4E79', end_color='1F4E79', fill_type='solid')
    
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

    # ==========================================
    # شیت ۱: فقط نام و نمره (ساده و آماده کپی مستقیم)
    # ==========================================
    ws_raw = wb.active
    ws_raw.title = "فقط نام و نمره (ساده)"
    ws_raw.views.sheetView[0].rightToLeft = True
    
    ws_raw['A1'] = "نام ناحیه"
    ws_raw['B1'] = score_header_fa
    ws_raw['A1'].font = font_copy_th
    ws_raw['B1'].font = font_copy_th
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

    # ==========================================
    # شیت ۲: جدول مقایسه جامع دو مقیاس
    # ==========================================
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
        cell.font = font_copy_th
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

    wb.save(excel_filename)
    try:
        shutil.copyfile(excel_filename, "final_scores.xlsx")
    except Exception:
        pass

    # چاپ خروجی در ترمینال
    print("\n" + "=" * 105)
    print(f"📋 جدول نمرات دوره {cfg['title']} نواحی نسرا - {scale_label_fa}:")
    print("=" * 105)
    print(f"{'ردیف':^6} | {'نام ناحیه (شهرستان)':<20} | {score_header_fa:^18} | {'مقیاس دیگر':^14} | {'سطح کیفی':^10} | {'رتبه':^6} | {'تحقق کل':^10} | {'وضعیت ماه‌های ارسالی':<26}")
    print("-" * 105)
    for idx, r in enumerate(results, start=1):
        other_sc = r['score_nasra'] if scale_mode == '0-100' else r['score_real']
        other_lbl = f"{other_sc:.1f} (نسرا)" if scale_mode == '0-100' else f"{other_sc:.1f} (واقعی)"
        print(f"{idx:^6} | {r['district']:<20} | {r['score']:^18.1f} | {other_lbl:^14} | {r['tier']:^10} | {str(r['rank']):^6} | {r['total_realization_pct']:^8.1f}% | {r['status_desc']:<26}")
    print("=" * 105)

    print(f"\n🎉 فایل اکسل متمرکز با موفقیت تولید شد:")
    print(f"   📄 «{os.path.abspath(excel_filename)}»")
    print(f"\n💡 در شیت ۱ («فقط نام و نمره (ساده)»)، ستون‌های نام و نمره انتخابی ({score_header_fa}) آماده کپی مستقیم هستند.")
    print("💡 در شیت ۲، مقایسه همزمان هر دو مقیاس (واقعی ۰-۱۰۰ و نسرا ۷۰-۱۰۰) در کنار هم قرار دارد.")
    print("=" * 105)

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

    run_period_evaluation(selected_months=arg_m, selected_scale=arg_scale)
