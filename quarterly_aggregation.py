# -*- coding: utf-8 -*-
"""
====================================================================
سامانه تجمیع خودکار عملکرد ۳ ماهه (فصلی) نواحی نسرا - استان اصفهان
تجمیع چندین فایل اکسل ماهانه برای هر شهرستان (مثلاً ۳ فایل برای هر ناحیه)
مقیاس نمره‌دهی رسمی: از ۷۰ (عملکرد صفر) تا ۱۰۰ (عالی)
====================================================================
"""

import sys
import os
import re
import glob
import shutil
import subprocess

# Fix Windows console UTF-8 output
if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

# Ensure working directory is the script folder
script_dir = os.path.dirname(os.path.abspath(__file__))
if script_dir:
    os.chdir(script_dir)

def ensure_dependencies():
    packages = {
        'openpyxl': 'openpyxl',
        'PIL': 'pillow',
        'arabic_reshaper': 'arabic-reshaper',
        'bidi': 'python-bidi'
    }
    missing = []
    for mod, pip_name in packages.items():
        try:
            __import__(mod)
        except ImportError:
            missing.append(pip_name)
            
    if missing:
        print("=" * 75)
        print(f"📦 Installing required libraries ({', '.join(missing)})...")
        print("=" * 75)
        try:
            subprocess.check_call([sys.executable, "-m", "pip", "install"] + missing + ["--quiet", "--break-system-packages"])
            print("✓ Packages installed successfully.\n")
        except Exception:
            try:
                subprocess.check_call([sys.executable, "-m", "pip", "install"] + missing + ["--quiet"])
                print("✓ Packages installed successfully.\n")
            except Exception as e:
                print(f"⚠️ Warning: Could not auto-install packages: {e}\n")

ensure_dependencies()

import openpyxl

QUARTERS = [
    (1, "Bahar (Spring)", "بهار (فروردین تا خرداد)"),
    (2, "Tabestan (Summer)", "تابستان (تیر تا شهریور)"),
    (3, "Paeiz (Fall)", "پاییز (مهر تا آذر)"),
    (4, "Zemestan (Winter)", "زمستان (دی تا اسفند)")
]

DISTRICTS = [
    'آران و بیدگل', 'امام حسین(ع)', 'امام رضا(ع)', 'امام صادق(ع)', 'امام علی(ع)',
    'اردستان', 'برخوار', 'بویین و میاندشت', 'تیران و کرون', 'جرقویه',
    'چادگان', 'خمینی شهر', 'خوانسار', 'خور و بیابانک', 'درچه',
    'دهاقان', 'سمیرم', 'شاهین شهر', 'شهرضا', 'فریدن',
    'فریدون شهر', 'فلاورجان', 'کاشان', 'کوهپایه', 'گلپایگان',
    'لنجان', 'مبارکه', 'نایین', 'نجف آباد', 'نطنز',
    'ورزنه', 'هرند'
]

DISTRICT_EN_NAMES = {
    'آران و بیدگل': 'Aran_va_Bidgol',
    'امام حسین(ع)': 'Emam_Hossein',
    'امام رضا(ع)': 'Emam_Reza',
    'امام صادق(ع)': 'Emam_Sadegh',
    'امام علی(ع)': 'Emam_Ali',
    'اردستان': 'Ardestan',
    'برخوار': 'Borkhar',
    'بویین و میاندشت': 'Boein_Miandasht',
    'تیران و کرون': 'Tiran_va_Karvan',
    'جرقویه': 'Jarghooyeh',
    'چادگان': 'Chadegan',
    'خمینی شهر': 'Khomeyni_Shahr',
    'خوانسار': 'Khansar',
    'خور و بیابانک': 'Khor_Biabanak',
    'درچه': 'Dorcheh',
    'دهاقان': 'Dehaghan',
    'سمیرم': 'Semirom',
    'شاهین شهر': 'Shahin_Shahr',
    'شهرضا': 'Shahreza',
    'فریدن': 'Fereydan',
    'فریدون شهر': 'Fereydoon_Shahr',
    'فلاورجان': 'Falavarjan',
    'کاشان': 'Kashan',
    'کوهپایه': 'Koohpayeh',
    'گلپایگان': 'Golpayegan',
    'لنجان': 'Lenjan',
    'مبارکه': 'Mobarakeh',
    'نایین': 'Naeen',
    'نجف آباد': 'Najaf_Abad',
    'نطنز': 'Natanz',
    'ورزنه': 'Varzaneh',
    'هرند': 'Harand'
}

DISTRICT_ALIASES = {
    'آران و بیدگل': ['آران', 'بیدگل', 'aran', 'bidgol'],
    'اردستان': ['اردستان', 'زوار', 'مهاباد اردستان', 'ardestan', 'ardestn'],
    'امام حسین(ع)': ['امام حسین', 'حسین ع', 'ناحیه امام حسین', 'emam hossein', 'hosein', 'hossein', 'emam hosein'],
    'امام رضا(ع)': ['امام رضا', 'رضا ع', 'ناحیه امام رضا', 'emam reza', 'reza'],
    'امام صادق(ع)': ['امام صادق', 'صادق ع', 'ناحیه امام صادق', 'emam sadegh', 'sadegh', 'sadeq'],
    'امام علی(ع)': ['امام علی', 'ناحیه امام علی', 'emam ali'],
    'برخوار': ['برخوار', 'دولت آباد', 'دولت‌آباد', 'دستگرد', 'borkhar', 'barkhar', 'dolatabad'],
    'بویین و میاندشت': ['بویین', 'میاندشت', 'بوئین', 'boin', 'bouin', 'buin', 'miandasht'],
    'تیران و کرون': ['تیران', 'کرون', 'تیران کرون', 'tiran', 'karvan', 'koron'],
    'جرقویه': ['جرقویه', 'نیک آباد', 'jarghooyeh', 'jarghooye', 'jarqavieh'],
    'چادگان': ['چادگان', 'chadegan'],
    'خمینی شهر': ['خمینی شهر', 'خمینی‌شهر', 'همایون شهر', 'khomeini', 'khomeinishahr'],
    'خوانسار': ['خوانسار', 'khansar', 'khwansar'],
    'خور و بیابانک': ['خور', 'بیابانک', 'خور و بیابانک', 'جندق', 'khoor', 'khur', 'biabanak'],
    'درچه': ['درچه', 'dorcheh', 'dorche', 'dorce'],
    'دهاقان': ['دهاقان', 'عطاآباد', 'dehaqan', 'dehaghan'],
    'سمیرم': ['سمیرم', 'semirom'],
    'شاهین شهر': ['شاهین شهر', 'شاهین‌شهر', 'شاهین', 'shahin', 'shahinshahr'],
    'شهرضا': ['شهرضا', 'قمشه', 'shahreza'],
    'فریدن': ['فریدن', 'داران', 'fereydan', 'fereidan', 'daran'],
    'فریدون شهر': ['فریدون شهر', 'فریدون‌شهر', 'fereydunshahr', 'fereydoonshahr'],
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

def clean_str(s):
    if not s:
        return ""
    s = str(s).strip()
    s = s.replace('ي', 'ی').replace('ك', 'ک').replace('ة', 'ه')
    s = s.replace('آ', 'ا').replace('أ', 'ا').replace('إ', 'ا').replace('ئ', 'ی')
    s = re.sub(r'[\u064B-\u065F\u0670]', '', s)
    s = re.sub(r'\s*\([عeE]\)|\s*\[[عeE]\]|\s+ع\b', '', s)
    s = re.sub(r'علیه\s*السلام', '', s)
    for prefix in ['ناحیه مقاومت بسیج', 'ناحیه مقاومت', 'سپاه ناحیه', 'سپاه', 'ناحیه', 'شهرستان', 'کانون', 'دفتر']:
        s = s.replace(prefix, '')
    s = re.sub(r'[\(\)\[\]\{\}\.\_\-\:\/\d\s\u200c\u00a0]+', '', s)
    return s

def clean_no_vav(s):
    return clean_str(s).replace('و', '')

def match_district_name(text):
    if not text:
        return None
    s_raw = str(text).strip()
    if not s_raw:
        return None

    if s_raw in ['پوشه اصلی برنامه', 'پوشه reports', 'reports', 'گزارش مستقیم', 'گزارش عملکرد']:
        return None

    c_raw = clean_str(s_raw)
    c_raw_nv = clean_no_vav(s_raw)
    s_lower = s_raw.lower()

    # 1. Exact match with canonical list
    for d in DISTRICTS:
        cd = clean_str(d)
        if cd and (cd == c_raw or (len(cd) >= 4 and cd in c_raw)):
            return d

    # 2. Check Aliases (Persian & English transliterations)
    for d, aliases in DISTRICT_ALIASES.items():
        for al in aliases:
            al_clean = clean_str(al)
            if al_clean and (al_clean == c_raw or (len(al_clean) >= 4 and al_clean in c_raw)):
                return d
            al_low = al.lower()
            if len(al_low) >= 4 and al_low in s_lower:
                return d

    # 3. Match without Vav
    for d in DISTRICTS:
        cd_nv = clean_no_vav(d)
        if cd_nv and (cd_nv == c_raw_nv or (len(cd_nv) >= 4 and cd_nv in c_raw_nv)):
            return d

    return None

def parse_number(val):
    if val is None:
        return 0
    if isinstance(val, (int, float)):
        return int(val) if isinstance(val, int) or val.is_integer() else val
    s = str(val).strip()
    if not s:
        return 0
    p_digits = '۰۱۲۳۴۵۶۷۸۹'
    a_digits = '٠١٢٣٤٥٦٧٨٩'
    for i in range(10):
        s = s.replace(p_digits[i], str(i)).replace(a_digits[i], str(i))
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

def detect_district_from_workbook(wb, fpath, folder_label=''):
    fname = os.path.basename(fpath)
    d = match_district_name(fname)
    if d:
        return d, "نام فایل"

    if folder_label and folder_label not in ['پوشه اصلی برنامه', 'پوشه reports', 'reports']:
        d = match_district_name(folder_label)
        if d:
            return d, "نام پوشه"
    parent_dir = os.path.basename(os.path.dirname(os.path.abspath(fpath)))
    if parent_dir and parent_dir not in ['Font', 'reports', 'Downloads']:
        d = match_district_name(parent_dir)
        if d:
            return d, "نام پوشه والد"

    try:
        if wb.properties and wb.properties.title:
            d = match_district_name(wb.properties.title)
            if d:
                return d, "متادیتای سند"
    except Exception:
        pass

    counts = {}
    for sname in wb.sheetnames:
        sd = match_district_name(sname)
        if sd:
            counts[sd] = counts.get(sd, 0) + 10

        cn = clean_str(sname)
        if any(w in cn for w in ['اطلاعاتپایه', 'dropdown', 'base', 'ref', 'پایه', 'اطلاعات']):
            continue

        ws = wb[sname]
        max_r = min(ws.max_row + 1, 60)
        max_c = min(ws.max_column + 1, 20)
        for r in range(1, max_r):
            for c in range(1, max_c):
                v = ws.cell(r, c).value
                if v is not None:
                    md = match_district_name(v)
                    if md:
                        counts[md] = counts.get(md, 0) + 1

    if counts:
        best_d = max(counts, key=counts.get)
        return best_d, f"محتوای سلول‌های شیت ({counts[best_d]} بار)"

    return None, "عدم شناسایی"

def extract_sheet_metrics(ws):
    if ws is None:
        return {'people_sum': 0, 'classes_count': 0, 'col_name': None}
    
    target_col = None
    target_col_name = None
    header_keywords_primary = [
        'نفر', 'بازدید', 'مخاطب', 'شرکت', 'تیراژ', 'مجموع', 'فراگیر',
        'حاضر', 'دانش', 'بسیج', 'عموم', 'people', 'view', 'participants', 'attendee'
    ]
    header_keywords_secondary = [
        'تعداد', 'صفحه', 'صفحات', 'میزان', 'آمار', 'جمعیت', 'count', 'total', 'number'
    ]

    header_row = 1
    for r in range(1, min(ws.max_row + 1, 6)):
        for c in range(1, ws.max_column + 1):
            h = str(ws.cell(r, c).value or '').lower()
            if any(k in h for k in header_keywords_primary):
                target_col = c
                target_col_name = h
                header_row = r
                break
        if target_col:
            break

    if target_col is None:
        for r in range(1, min(ws.max_row + 1, 6)):
            for c in range(1, ws.max_column + 1):
                h = str(ws.cell(r, c).value or '').lower()
                if any(k in h for k in header_keywords_secondary):
                    target_col = c
                    target_col_name = h
                    header_row = r
                    break
            if target_col:
                break

    total_people = 0
    active_classes = 0
    start_row = header_row + 1

    for r in range(start_row, ws.max_row + 1):
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
                total_people += parse_number(v)
            else:
                for c in range(2, ws.max_column + 1):
                    val = ws.cell(r, c).value
                    p_num = parse_number(val)
                    if p_num > 0 and p_num != r:
                        total_people += p_num
                        break

    return {
        'people_sum': int(total_people),
        'classes_count': active_classes,
        'col_name': target_col_name
    }

def extract_workbook_indicators(wb):
    ws_hoz = None
    ws_tav = None
    ws_maj = None
    ws_kha = None
    ws_tol = None

    for sname in wb.sheetnames:
        cn = clean_str(sname)
        if 'حضوری' in cn or 'کارگاه' in cn or 'کلاس' in cn:
            ws_hoz = wb[sname]
        elif 'توانمند' in cn:
            ws_tav = wb[sname]
        elif 'مجازی' in cn or 'لایو' in cn or 'آنلاین' in cn or 'وبینار' in cn:
            ws_maj = wb[sname]
        elif 'خلاق' in cn or 'پویش' in cn or 'مسابقه' in cn or 'ابتکار' in cn:
            ws_kha = wb[sname]
        elif 'تولید' in cn or 'رسانه' in cn or 'محتوا' in cn or 'کلیپ' in cn:
            ws_tol = wb[sname]

    if any([ws_hoz, ws_tav, ws_maj, ws_kha, ws_tol]):
        m_hoz = extract_sheet_metrics(ws_hoz)
        m_tav = extract_sheet_metrics(ws_tav)
        m_maj = extract_sheet_metrics(ws_maj)
        m_kha = extract_sheet_metrics(ws_kha)
        m_tol = extract_sheet_metrics(ws_tol)

        hoz_val = (m_hoz['people_sum'] + m_tav['people_sum'])
        if hoz_val == 0:
            hoz_val = m_hoz['classes_count'] + m_tav['classes_count']

        maj_val = m_maj['people_sum'] if m_maj['people_sum'] > 0 else m_maj['classes_count']
        kha_val = m_kha['people_sum'] if m_kha['people_sum'] > 0 else m_kha['classes_count']
        tol_val = m_tol['people_sum'] if m_tol['people_sum'] > 0 else m_tol['classes_count']

        return hoz_val, maj_val, kha_val, tol_val

    # Fallback for single-sheet summaries
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

def prompt_quarter_and_year():
    print("=" * 75)
    print("   NASRA 3-MONTH (QUARTERLY) PERFORMANCE SYSTEM - ISFAHAN")
    print("   سامانه هوشمند تجمیع عملکرد ۳ ماهه (فصلی) نواحی نسرا - استان اصفهان")
    print("=" * 75)
    print("Select Quarter (Fasl):\n")
    print("  [1] Bahar    - Spring (Farvardin, Ordibehesht, Khordad)")
    print("  [2] Tabestan - Summer (Tir, Mordad, Shahrivar)")
    print("  [3] Paeiz    - Fall   (Mehr, Aban, Azar)")
    print("  [4] Zemestan - Winter (Dey, Bahman, Esfand)")
    print("-" * 75)
    
    sel_period = QUARTERS[0][2]
    try:
        ans = input("Enter Quarter number (1-4) [Default: 1 = Bahar]: ").strip()
        if ans in ['1', '2', '3', '4']:
            sel_period = QUARTERS[int(ans) - 1][2]
    except (EOFError, KeyboardInterrupt):
        sel_period = QUARTERS[0][2]

    sel_year = "1405"
    try:
        ans_y = input("Enter Year (Sal) [Default: 1405]: ").strip()
        if ans_y:
            sel_year = ans_y
    except (EOFError, KeyboardInterrupt):
        sel_year = "1405"
        
    return sel_period, sel_year

def find_quarterly_reports_files(quarter_name):
    # Searches recursively in reports/ and subfolders (e.g. reports/Mordad 1405/*.xlsx) and current directory
    reports_dir = 'reports'
    os.makedirs(reports_dir, exist_ok=True)
    
    seen = set()
    all_files = []
    
    for root, dirs, files in os.walk(reports_dir):
        for f in sorted(files):
            if f.endswith('.xlsx') and not f.startswith('~$'):
                full_p = os.path.abspath(os.path.join(root, f))
                if full_p not in seen:
                    seen.add(full_p)
                    all_files.append(os.path.join(root, f))
                    
    excluded = {
        'تهیه کارنامه نواحی.xlsx', 'تهیه کارنامه ۳ ماهه نواحی.xlsx',
        'نمرات_نهایی_نواحی.xlsx', 'final_scores.xlsx',
        'master_monitoring.xlsx', 'monthly_scorecard.xlsx',
        'گزارش شهریور ماه 1405 ناحیه.xlsx',
        'نمرات_عملکرد_۲ماهه.xlsx', 'نمرات_عملکرد_۳ماهه.xlsx', 'نمرات_عملکرد_۶ماهه.xlsx'
    }
    for f in sorted(os.listdir('.')):
        if f.endswith('.xlsx') and not f.startswith('~$') and f not in excluded and not f.startswith('کارنامه_'):
            full_p = os.path.abspath(f)
            if full_p not in seen:
                seen.add(full_p)
                all_files.append(f)
                
    return reports_dir, all_files

def process_quarterly_reports(master_excel="تهیه کارنامه ۳ ماهه نواحی.xlsx", selected_quarter=None, selected_year=None):
    if not os.path.exists(master_excel):
        print(f"❌ Error: Master 3-month Excel file '{master_excel}' not found. Regenerating...")
        try:
            import make_quarterly_excel
        except Exception:
            pass
            
    if selected_quarter and selected_year:
        quarter = selected_quarter
        year = selected_year
    else:
        quarter, year = prompt_quarter_and_year()
        
    wb_master = openpyxl.load_workbook(master_excel)
    ws_card = wb_master['کارنامه هوشمند ۳ ماهه']
    ws_card['C3'].value = quarter
    ws_card['E3'].value = str(year)
    
    print("-" * 75)
    print(f"✓ Selected Evaluation Period: [{quarter}] - Year [{year}]")
    
    folder_name, excel_files = find_quarterly_reports_files(quarter)
    print(f"📁 Reports Folder: '{folder_name}' | Total Files Detected: {len(excel_files)}")
    print("-" * 75)
    
    if not excel_files:
        print(f"⚠️ No employee Excel reports found in '{folder_name}'.")
        print(f"💡 Place your 3 monthly files per district into '{folder_name}' and run again.")
        wb_master.save(master_excel)
        return False, quarter, year
        
    # Accumulate data per district across all files!
    extracted = {}
    
    for fpath in excel_files:
        fname = os.path.basename(fpath)
        try:
            wb = openpyxl.load_workbook(fpath, data_only=True)
            detected, detection_reason = detect_district_from_workbook(wb, fpath)

            if not detected:
                print(f"⚠️ Could not detect district for file: '{fname}'")
                continue

            hoz_val, maj_val, kha_val, tol_val = extract_workbook_indicators(wb)
            neshast = 1 if (hoz_val + maj_val + kha_val + tol_val) > 0 else 0

            if detected not in extracted:
                extracted[detected] = {
                    'files_count': 0,
                    'files': [],
                    'hozori_total': 0,
                    'majazi': 0,
                    'khalagh': 0,
                    'tolid': 0,
                    'neshast': 0
                }

            extracted[detected]['files_count'] += 1
            extracted[detected]['files'].append(fname)
            extracted[detected]['hozori_total'] += hoz_val
            extracted[detected]['majazi'] += maj_val
            extracted[detected]['khalagh'] += kha_val
            extracted[detected]['tolid'] += tol_val
            extracted[detected]['neshast'] += neshast

            print(f"✓ [{detected}] (فایل {extracted[detected]['files_count']} از طریق {detection_reason}: {fname}): +{hoz_val} نفر حضوری | +{maj_val} مجازی | +{kha_val} خلاقانه | +{tol_val} تولید")

        except Exception as e:
            print(f"❌ Error processing file '{fname}': {e}")

    print("-" * 75)
    print("💾 Updating 3-month cumulative metrics into Excel...")
    
    ws_rep = wb_master['گزارش عملکرد ۳ ماهه']
    row_map = {}
    for r in range(2, ws_rep.max_row + 1):
        dname = ws_rep.cell(r, 1).value
        if dname:
            row_map[clean_str(dname)] = r
            row_map[clean_no_vav(dname)] = r

    active_count = 0
    inactive_count = 0
    
    for dname in DISTRICTS:
        r = row_map.get(clean_str(dname)) or row_map.get(clean_no_vav(dname))
        if not r:
            continue
            
        if dname in extracted:
            data = extracted[dname]
            ws_rep.cell(row=r, column=2, value=data['hozori_total'])
            ws_rep.cell(row=r, column=3, value=data['majazi'])
            ws_rep.cell(row=r, column=4, value=data['khalagh'])
            ws_rep.cell(row=r, column=5, value=data['tolid'])
            ws_rep.cell(row=r, column=6, value=data['neshast'])
            ws_rep.cell(row=r, column=7, value=data['files_count'])
            active_count += 1
        else:
            # No files -> zero performance
            ws_rep.cell(row=r, column=2, value=0)
            ws_rep.cell(row=r, column=3, value=0)
            ws_rep.cell(row=r, column=4, value=0)
            ws_rep.cell(row=r, column=5, value=0)
            ws_rep.cell(row=r, column=6, value=0)
            ws_rep.cell(row=r, column=7, value=0)
            inactive_count += 1
            print(f"⭕ [{dname}]: گزارشی ارسال نشده (عملکرد ۳ ماهه ۰ منظور شد - نمره ۷۰)")

    wb_master.save(master_excel)
    
    archive_name = f"کارنامه_۳ماهه_نواحی_{quarter.split()[0]}_{year}.xlsx"
    try:
        shutil.copyfile(master_excel, archive_name)
        print(f"📁 پشتیبان فصلی با نام '{archive_name}' ذخیره شد.")
    except Exception:
        pass
        
    print(f"🎉 عملیات با موفقیت پایان یافت! آمار {active_count} ناحیه فعال و {inactive_count} ناحیه فاقد فعالیت ثبت شد.")
    return True, quarter, str(year)

def generate_all_quarterly_images(master_excel="تهیه کارنامه ۳ ماهه نواحی.xlsx", quarter="بهار (فروردین تا خرداد)", year="1405"):
    try:
        from image_generator import generate_quarterly_scorecard_png, generate_quarterly_dashboard_png
    except Exception as e:
        print("⚠️ ماژول‌های تولید تصویر در دسترس نیستند:", e)
        return
        
    quarter_slug = quarter.split()[0]
    out_dir = os.path.join("output_cards_3months", f"{quarter_slug}_{year}")
    os.makedirs(out_dir, exist_ok=True)
    
    print("-" * 75)
    print(f"📸 در حال تولید کارنامه‌های ۳ ماهه (مقیاس ۷۰ تا ۱۰۰) در پوشه '{out_dir}'...")
    
    wb = openpyxl.load_workbook(master_excel, data_only=True)
    ws_target = wb['پایگاه داده حد انتظار ۳ ماهه']
    ws_rep = wb['گزارش عملکرد ۳ ماهه']
    
    targets = {}
    for r in range(2, 34):
        dn = ws_target.cell(r, 1).value
        targets[dn] = {
            'branches': ws_target.cell(r, 2).value or 0,
            'hozori': ws_target.cell(r, 3).value or 0,
            'majazi': ws_target.cell(r, 4).value or 0,
            'khalagh': ws_target.cell(r, 5).value or 0,
            'tolid': ws_target.cell(r, 6).value or 0,
            'neshast': ws_target.cell(r, 7).value or 3
        }
        
    actuals = {}
    for r in range(2, ws_rep.max_row + 1):
        dn = ws_rep.cell(r, 1).value
        if dn:
            actuals[dn] = {
                'hozori': ws_rep.cell(r, 2).value or 0,
                'majazi': ws_rep.cell(r, 3).value or 0,
                'khalagh': ws_rep.cell(r, 4).value or 0,
                'tolid': ws_rep.cell(r, 5).value or 0,
                'neshast': ws_rep.cell(r, 6).value or 0,
                'files_count': ws_rep.cell(r, 7).value or 0
            }
            
    # Calculate Realization and 70-100 Score with approved weights and training surplus spillover!
    # Approved logic: 10% In-Person, 30% Virtual (combined training up to 40% with surplus overflow to Creative),
    # 50% Creative Actions (capped at 50% max), 10% Media Productions (capped at 10% max).
    # Score = 70.0 + (30.0 * Total Realization Ratio)
    dist_scores = []
    for dn, t in targets.items():
        a = actuals.get(dn, {'hozori': 0, 'majazi': 0, 'khalagh': 0, 'tolid': 0, 'neshast': 0, 'files_count': 0})
        
        t_hoz = max(1, t.get('hozori', 1))
        t_maj = max(1, t.get('majazi', 1))
        t_kha = max(1, t.get('khalagh', 1))
        t_tol = max(1, t.get('tolid', 1))
        
        act_hoz = a.get('hozori', 0)
        act_maj = a.get('majazi', 0)
        act_kha = a.get('khalagh', 0)
        act_tol = a.get('tolid', 0)
        
        raw_training = (0.10 * (act_hoz / t_hoz)) + (0.30 * (act_maj / t_maj))
        training_share = min(0.40, raw_training)
        training_surplus = max(0.0, raw_training - 0.40)
        
        raw_khalagh = 0.50 * (act_kha / t_kha)
        khalagh_share = min(0.50, raw_khalagh + training_surplus)
        
        raw_tolid = act_tol / t_tol
        tolid_share = 0.10 * min(1.0, raw_tolid)
        
        total_realization_ratio = training_share + khalagh_share + tolid_share
        sc_70_100 = round(70.0 + 30.0 * min(1.0, max(0.0, total_realization_ratio)), 1)
        
        a['overall_realization'] = round(total_realization_ratio * 100, 1)
        a['score_70_100'] = sc_70_100
        dist_scores.append((dn, sc_70_100, a['overall_realization']))
        
    dist_scores.sort(key=lambda x: (x[1], x[2]), reverse=True)
    
    rank_map = {}
    current_rank = 1
    for dn, sc, real in dist_scores:
        if sc > 70.0:
            rank_map[dn] = str(current_rank)
            current_rank += 1
        else:
            rank_map[dn] = "فاقد فعالیت"
            
    count_img = 0
    for dn in DISTRICTS:
        t = targets.get(dn, {})
        a = actuals.get(dn, {'overall_realization': 0, 'score_70_100': 70.0, 'files_count': 0})
        sc = a.get('score_70_100', 70.0)
        real = a.get('overall_realization', 0.0)
        rk = rank_map.get(dn, "فاقد فعالیت")
        
        # Approved 3 Qualitative Tiers:
        if sc >= 90.0:
            tier = "عالی"
        elif sc >= 80.0:
            tier = "متوسط"
        else:
            tier = "ضعیف"
            
        en_name = DISTRICT_EN_NAMES.get(dn, dn)
        out_p_fa = os.path.join(out_dir, f"کارنامه_{dn}.png")
        out_p_en = os.path.join(out_dir, f"Scorecard_{en_name}.png")
        
        try:
            generate_quarterly_scorecard_png(
                district_name=dn,
                target_dict=t,
                actual_dict=a,
                rank=rk,
                tier=tier,
                period=quarter,
                year=year,
                files_count=a.get('files_count', 0),
                output_path=out_p_fa
            )
            shutil.copyfile(out_p_fa, out_p_en)
            count_img += 1
        except Exception as err:
            print(f"Error generating 3-month card for {dn}: {err}")

    # Provincial Dashboard
    sum_t_hoz = sum(targets[d]['hozori'] for d in targets)
    sum_t_maj = sum(targets[d]['majazi'] for d in targets)
    sum_t_kha = sum(targets[d]['khalagh'] for d in targets)
    sum_t_tol = sum(targets[d]['tolid'] for d in targets)
    sum_t_nes = sum(targets[d]['neshast'] for d in targets)
    
    sum_a_hoz = sum(actuals.get(d, {}).get('hozori', 0) for d in targets)
    sum_a_maj = sum(actuals.get(d, {}).get('majazi', 0) for d in targets)
    sum_a_kha = sum(actuals.get(d, {}).get('khalagh', 0) for d in targets)
    sum_a_tol = sum(actuals.get(d, {}).get('tolid', 0) for d in targets)
    sum_a_nes = sum(actuals.get(d, {}).get('neshast', 0) for d in targets)
    
    macro_data = [
        ('سواد رسانه حضوری و توانمندسازی (ضریب ۹۳)', int(sum_t_hoz), int(sum_a_hoz)),
        ('سواد رسانه مجازی و لایو (ضریب ۶۵۱)', int(sum_t_maj), int(sum_a_maj)),
        ('اقدامات و ابتکارات خلاقانه (ضریب ۱۸۶)', int(sum_t_kha), int(sum_a_kha)),
        ('تولیدات رسانه‌ای و محتوایی (ضریب ۹)', int(sum_t_tol), int(sum_a_tol)),
        ('نشست با انجمن مدرسان (۳ نشست)', int(sum_t_nes), int(sum_a_nes))
    ]
    
    top5 = [(i+1, dist_scores[i][0], dist_scores[i][1]) for i in range(min(5, len(dist_scores)))]
    bot5 = [(i+1, dist_scores[len(dist_scores)-1-i][0], dist_scores[len(dist_scores)-1-i][1]) for i in range(min(5, len(dist_scores)))]
    
    avg_score = sum(x[1] for x in dist_scores) / len(dist_scores) if dist_scores else 70.0
    top_d = dist_scores[0][0] if dist_scores and dist_scores[0][1] > 70.0 else "در انتظار"
    rep_c = sum(1 for x in dist_scores if x[1] > 70.0)
    
    kpi_d = {'avg_score': avg_score, 'top_district': top_d, 'reported_count': rep_c}
    dash_path_fa = os.path.join(out_dir, f"تصویر_داشبورد_مدیریتی_۳ماهه_{quarter_slug}_{year}.png")
    dash_path_en = os.path.join(out_dir, f"Dashboard_3Months_Provincial_{quarter_slug}.png")
    
    try:
        generate_quarterly_dashboard_png(macro_data, top5, bot5, kpi_d, period=quarter, year=year, output_path=dash_path_fa)
        shutil.copyfile(dash_path_fa, dash_path_en)
    except Exception as err:
        print(f"Error generating 3-month dashboard: {err}")
        
    print("-" * 75)
    print(f"🎉 تعداد {count_img} تصویر کارنامه ۳ ماهه (نمره ۷۰ تا ۱۰۰) در پوشه:")
    print(f"   📁 '{os.path.abspath(out_dir)}'")
    print("   به همراه تصویر داشبورد فصلی کل استان ذخیره گردید!")
    print("=" * 75)
    
    if sys.platform == 'win32':
        try:
            os.system(f'explorer "{os.path.abspath(out_dir)}"')
        except Exception:
            pass

if __name__ == '__main__':
    q_arg = None
    y_arg = None
    if len(sys.argv) > 1:
        raw_q = sys.argv[1].strip()
        if raw_q in ['1', '2', '3', '4']:
            q_arg = QUARTERS[int(raw_q) - 1][2]
        else:
            for q_num, q_en, q_fa in QUARTERS:
                if raw_q.lower() in q_en.lower() or raw_q in q_fa:
                    q_arg = q_fa
                    break
                    
    if len(sys.argv) > 2:
        y_arg = sys.argv[2].strip()
        
    success, sel_quarter, sel_year = process_quarterly_reports(selected_quarter=q_arg, selected_year=y_arg)
    generate_all_quarterly_images(quarter=sel_quarter, year=sel_year)
