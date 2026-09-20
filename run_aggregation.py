# -*- coding: utf-8 -*-
"""
====================================================================
سامانه تجمیع خودکار فایل‌های گزارش ماهانه کارمندان نواحی نسرا
استان اصفهان - سال ۱۴۰۵
پشتیبانی از پوشه‌های انگلیسی و فارسی، ثبت صفر برای نواحی بدون فعالیت
و انتخاب پویای ماه گزارش
====================================================================
"""

import sys
import os
import re
import glob
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
        print("=" * 70)
        print(f"📦 در حال نصب خودکار پیش‌نیازها ({', '.join(missing)})...")
        print("=" * 70)
        try:
            subprocess.check_call([sys.executable, "-m", "pip", "install"] + missing + ["--quiet"])
            print("✓ تمامی پیش‌نیازها با موفقیت نصب شدند.\n")
        except Exception as e:
            print(f"⚠️ توجه: امکان نصب آنلاین پیش‌نیازها فراهم نبود: {e}\n")

ensure_dependencies()

import openpyxl

DISTRICTS = [
    'آران و بیدگل', 'امام حسین(ع)', 'امام رضا(ع)', 'امام صادق(ع)', 'امام علی(ع)',
    'اردستان', 'برخوار', 'بویین و میاندشت', 'تیران و کرون', 'جرقویه',
    'چادگان', 'خمینی شهر', 'خوانسار', 'خور و بیابانک', 'درچه',
    'دهاقان', 'سمیرم', 'شاهین شهر', 'شهرضا', 'فریدن',
    'فریدون شهر', 'فلاورجان', 'کاشان', 'کوهپایه', 'گلپایگان',
    'لنجان', 'مبارکه', 'نایین', 'نجف آباد', 'نطنز',
    'ورزنه', 'هرند'
]

# Mapping to English filenames for clean filesystem support
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

def clean_str(s):
    if not s:
        return ""
    s = str(s).strip()
    s = s.replace('ي', 'ی').replace('ك', 'ک').replace('ة', 'ه')
    s = re.sub(r'[\(\)\[\]\{\}\.\_\-\d\s\u200c]+', '', s)
    s = s.replace('ع', '')
    return s

def clean_no_vav(s):
    return clean_str(s).replace('و', '')

def match_district_name(text):
    if not text:
        return None
    c_raw = clean_str(text)
    c_raw_nv = clean_no_vav(text)
    for d in DISTRICTS:
        cd = clean_str(d)
        if cd == c_raw or cd in c_raw:
            return d
    for d in DISTRICTS:
        cd_nv = clean_no_vav(d)
        if cd_nv == c_raw_nv or cd_nv in c_raw_nv:
            return d
    return None

def parse_number(val):
    if val is None:
        return 0
    if isinstance(val, (int, float)):
        return val
    s = str(val).strip()
    if not s:
        return 0
    p_digits = '۰۱۲۳۴۵۶۷۸۹'
    a_digits = '٠١٢٣٤٥٦٧٨٩'
    for i in range(10):
        s = s.replace(p_digits[i], str(i)).replace(a_digits[i], str(i))
    s = s.replace(',', '').replace('،', '')
    match = re.search(r'\d+(\.\d+)?', s)
    if match:
        try:
            return float(match.group()) if '.' in match.group() else int(match.group())
        except:
            return 0
    return 0

def extract_sheet_metrics(ws):
    if ws is None:
        return {'people_sum': 0, 'classes_count': 0, 'col_name': None}
    
    target_col = None
    target_col_name = None
    
    for c in range(1, ws.max_column + 1):
        h = str(ws.cell(1, c).value or '')
        if any(k in h for k in ['نفر', 'بازدید']):
            target_col = c
            target_col_name = h
            break
            
    if target_col is None:
        for c in range(1, ws.max_column + 1):
            h = str(ws.cell(1, c).value or '')
            if any(k in h for k in ['تعداد', 'صفحه', 'صفحات']):
                target_col = c
                target_col_name = h
                break

    total_people = 0
    active_classes = 0
    
    for r in range(2, ws.max_row + 1):
        has_act = any(ws.cell(r, c).value is not None and str(ws.cell(r, c).value).strip() != '' for c in range(2, ws.max_column + 1))
        if has_act:
            active_classes += 1
            if target_col:
                v = ws.cell(r, target_col).value
                total_people += parse_number(v)
                
    return {
        'people_sum': int(total_people),
        'classes_count': active_classes,
        'col_name': target_col_name
    }

def get_reports_folder():
    # Support both 'reports' (English) and 'گزارشات_ماهانه' (Persian)
    if os.path.exists('reports'):
        files = glob.glob(os.path.join('reports', '*.xlsx'))
        files = [f for f in files if not os.path.basename(f).startswith('~$')]
        if files:
            return 'reports', files
    if os.path.exists('گزارشات_ماهانه'):
        files = glob.glob(os.path.join('گزارشات_ماهانه', '*.xlsx'))
        files = [f for f in files if not os.path.basename(f).startswith('~$')]
        if files:
            return 'گزارشات_ماهانه', files
    # Default to 'reports'
    os.makedirs('reports', exist_ok=True)
    return 'reports', []

def process_all_reports(master_excel="تهیه کارنامه نواحی.xlsx", selected_month=None):
    print("=" * 75)
    print("🚀 سامانه هوشمند تجمیع عملکرد ماهانه نواحی نسرا استان اصفهان")
    print("=" * 75)
    
    if not os.path.exists(master_excel):
        print(f"❌ خطا: فایل کارنامه '{master_excel}' یافت نشد!")
        return False, "شهریور"
        
    wb_master = openpyxl.load_workbook(master_excel)
    ws_card = wb_master['کارنامه هوشمند']
    
    # Read or set month
    current_month_excel = str(ws_card['C3'].value or 'شهریور').strip()
    if selected_month:
        month = selected_month
        ws_card['C3'].value = month
    else:
        month = current_month_excel if current_month_excel in [
            'فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور',
            'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند'
        ] else 'شهریور'
        
    print(f"📅 دوره ارزیابی عملکرد: ماه «{month}» سال ۱۴۰۵")
    
    folder_name, excel_files = get_reports_folder()
    
    if not excel_files:
        print(f"⚠️ هیچ فایل اکسلی در پوشه '{folder_name}' یافت نشد.")
        print(f"💡 لطفاً فایل‌های گزارش ماهانه را داخل پوشه '{folder_name}' قرار دهید.")
        return False, month
        
    print(f"📁 پوشه ورودی: '{folder_name}' | تعداد فایل‌های دریافتی: {len(excel_files)}")
    print("-" * 75)
    
    extracted = {}
    
    for fpath in excel_files:
        fname = os.path.basename(fpath)
        try:
            wb = openpyxl.load_workbook(fpath, data_only=True)
            detected = match_district_name(fname)
            
            ws_hozori = None
            ws_tavanmand = None
            ws_majazi = None
            ws_khalagh = None
            ws_tolid = None
            
            for sname in wb.sheetnames:
                cn = clean_str(sname)
                if 'حضوری' in cn: ws_hozori = wb[sname]
                elif 'توانمند' in cn: ws_tavanmand = wb[sname]
                elif 'مجازی' in cn or 'لایو' in cn: ws_majazi = wb[sname]
                elif 'خلاق' in cn: ws_khalagh = wb[sname]
                elif 'تولید' in cn: ws_tolid = wb[sname]
                
            if not detected:
                for ws in [ws_hozori, ws_majazi, ws_khalagh, ws_tavanmand]:
                    if ws is None: continue
                    for r in range(2, min(ws.max_row + 1, 25)):
                        for c in [3, 4, 2]:
                            m = match_district_name(ws.cell(r, c).value)
                            if m:
                                detected = m
                                break
                        if detected: break
                    if detected: break
                    
            if not detected:
                print(f"⚠️ اخطار: شهرستان مربوط به فایل '{fname}' شناسایی نشد.")
                continue
                
            m_hoz = extract_sheet_metrics(ws_hozori)
            m_tav = extract_sheet_metrics(ws_tavanmand)
            m_maj = extract_sheet_metrics(ws_majazi)
            m_kha = extract_sheet_metrics(ws_khalagh)
            m_tol = extract_sheet_metrics(ws_tolid)
            
            hoz_tot = m_hoz['people_sum'] + m_tav['people_sum']
            hoz_cls = m_hoz['classes_count'] + m_tav['classes_count']
            metric_hoz = hoz_tot if hoz_tot > 0 else hoz_cls
            
            metric_maj = m_maj['people_sum'] if m_maj['people_sum'] > 0 else m_maj['classes_count']
            metric_kha = m_kha['people_sum'] if m_kha['people_sum'] > 0 else m_kha['classes_count']
            metric_tol = m_tol['people_sum'] if m_tol['people_sum'] > 0 else m_tol['classes_count']
            neshast = 1 if (metric_hoz + metric_maj + metric_kha + metric_tol) > 0 else 0
            
            extracted[detected] = {
                'hozori_total': metric_hoz,
                'majazi': metric_maj,
                'khalagh': metric_kha,
                'tolid': metric_tol,
                'neshast': neshast,
                'details': f"حضوری: {metric_hoz} نفر | مجازی: {metric_maj} نفر | خلاقانه: {metric_kha} نفر | تولیدات: {metric_tol}"
            }
            print(f"✓ [{detected}]: {extracted[detected]['details']}")
            
        except Exception as e:
            print(f"❌ خطا در پردازش فایل '{fname}': {e}")

    print("-" * 75)
    print("💾 در حال ثبت عملکرد در فایل اکسل کارنامه...")
    
    ws_rep = wb_master['گزارش عملکرد ماهانه']
    row_map = {}
    for r in range(2, ws_rep.max_row + 1):
        dname = ws_rep.cell(r, 1).value
        if dname:
            row_map[clean_str(dname)] = r
            row_map[clean_no_vav(dname)] = r

    active_count = 0
    inactive_count = 0
    
    # Process ALL 32 districts: active districts get their numbers, missing districts get ZERO!
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
            active_count += 1
        else:
            # District had NO report in folder -> Zero performance!
            ws_rep.cell(row=r, column=2, value=0)
            ws_rep.cell(row=r, column=3, value=0)
            ws_rep.cell(row=r, column=4, value=0)
            ws_rep.cell(row=r, column=5, value=0)
            ws_rep.cell(row=r, column=6, value=0)
            inactive_count += 1
            print(f"⭕ [{dname}]: گزارشی ارسال نشده (عملکرد این ماه ۰ ثبت شد)")

    wb_master.save(master_excel)
    print(f"🎉 ثبت در اکسل کامل شد! {active_count} ناحیه فعال و {inactive_count} ناحیه فاقد فعالیت ثبت گردید.")
    return True, month

def generate_all_images_offline(master_excel="تهیه کارنامه نواحی.xlsx", month="شهریور"):
    try:
        from image_generator import generate_scorecard_png, generate_dashboard_png
    except Exception as e:
        print("⚠️ ماژول‌های تولید تصویر در دسترس نیستند:", e)
        return
        
    out_dir = "output_cards"
    os.makedirs(out_dir, exist_ok=True)
    print("-" * 75)
    print(f"📸 در حال تولید تصاویر کارنامه برای تمامی ۳۲ شهرستان در پوشه '{out_dir}' (ماه {month})...")
    
    if not os.path.exists(master_excel):
        print(f"❌ فایل '{master_excel}' یافت نشد.")
        return

    wb = openpyxl.load_workbook(master_excel, data_only=True)
    ws_target = wb['پایگاه داده حد انتظار']
    ws_rep = wb['گزارش عملکرد ماهانه']
    
    targets = {}
    for r in range(2, 34):
        dn = ws_target.cell(r, 1).value
        targets[dn] = {
            'branches': ws_target.cell(r, 2).value or 0,
            'hozori': ws_target.cell(r, 3).value or 0,
            'majazi': ws_target.cell(r, 4).value or 0,
            'khalagh': ws_target.cell(r, 5).value or 0,
            'tolid': ws_target.cell(r, 6).value or 0,
            'neshast': ws_target.cell(r, 7).value or 1
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
                'neshast': ws_rep.cell(r, 6).value or 0
            }
            
    dist_scores = []
    for dn, t in targets.items():
        a = actuals.get(dn, {'hozori': 0, 'majazi': 0, 'khalagh': 0, 'tolid': 0, 'neshast': 0})
        pcts = []
        for k in ['hozori', 'majazi', 'khalagh', 'tolid', 'neshast']:
            tv = t.get(k, 1)
            av = a.get(k, 0)
            pcts.append((av / tv * 100) if tv > 0 else 0)
        sc = sum(pcts) / len(pcts) if pcts else 0
        a['overall_score'] = sc
        dist_scores.append((dn, sc))
        
    dist_scores.sort(key=lambda x: x[1], reverse=True)
    
    # Active rankings
    rank_map = {}
    current_rank = 1
    for dn, sc in dist_scores:
        if sc > 0:
            rank_map[dn] = str(current_rank)
            current_rank += 1
        else:
            rank_map[dn] = "فاقد فعالیت"
    
    count_img = 0
    for dn in DISTRICTS:
        t = targets.get(dn, {})
        a = actuals.get(dn, {'overall_score': 0})
        sc = a.get('overall_score', 0)
        rk = rank_map.get(dn, "فاقد فعالیت")
        
        if sc >= 100:
            tier = "عالی (۱۰۰٪+)"
        elif sc >= 75:
            tier = "خوب (۷۵-۹۹٪)"
        elif sc >= 50:
            tier = "متوسط (۵۰-۷۴٪)"
        elif sc > 0:
            tier = "ضعیف (زیر ۵۰٪)"
        else:
            tier = "فاقد عملکرد (عدم فعالیت)"
        
        en_name = DISTRICT_EN_NAMES.get(dn, dn)
        # Save with both Persian and English filenames so it's guaranteed to work
        out_p_fa = os.path.join(out_dir, f"Scorecard_{dn}.png")
        out_p_en = os.path.join(out_dir, f"Scorecard_{en_name}.png")
        
        try:
            generate_scorecard_png(dn, t, a, rank=rk, tier=tier, month=month, output_path=out_p_en)
            if out_p_fa != out_p_en:
                try:
                    import shutil
                    shutil.copyfile(out_p_en, out_p_fa)
                except Exception:
                    pass
            count_img += 1
        except Exception as err:
            print(f"خطا در تولید تصویر کارنامه {dn}: {err}")

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
        ('سواد رسانه حضوری و توانمندسازی (۳۱×)', int(sum_t_hoz), int(sum_a_hoz)),
        ('سواد رسانه مجازی و لایو (۲۱۷×)', int(sum_t_maj), int(sum_a_maj)),
        ('اقدامات و ابتکارات خلاقانه (۶۲×)', int(sum_t_kha), int(sum_a_kha)),
        ('تولیدات رسانه‌ای و محتوایی (۳×)', int(sum_t_tol), int(sum_a_tol)),
        ('نشست با انجمن مدرسان (۱ نشست)', int(sum_t_nes), int(sum_a_nes))
    ]
    
    active_dists = [x for x in dist_scores if x[1] > 0]
    inactive_dists = [x for x in dist_scores if x[1] == 0]
    
    top5 = [(i+1, active_dists[i][0], active_dists[i][1]) for i in range(min(5, len(active_dists)))]
    # Bottom 5 from end of active list or inactive list
    all_sorted = active_dists + inactive_dists
    bot5 = [(i+1, all_sorted[len(all_sorted)-1-i][0], all_sorted[len(all_sorted)-1-i][1]) for i in range(min(5, len(all_sorted)))]
    
    avg_sc = sum(x[1] for x in dist_scores) / len(dist_scores) if dist_scores else 0
    top_d = dist_scores[0][0] if dist_scores and dist_scores[0][1] > 0 else "در انتظار"
    rep_c = sum(1 for x in dist_scores if x[1] > 0)
    
    kpi_d = {'avg_score': avg_sc, 'top_district': top_d, 'reported_count': rep_c}
    dash_path_en = os.path.join(out_dir, "Dashboard_Provincial.png")
    dash_path_fa = os.path.join(out_dir, "تصویر_داشبورد_مدیریتی_استان.png")
    
    try:
        generate_dashboard_png(macro_data, top5, bot5, kpi_d, month=month, output_path=dash_path_en)
        try:
            import shutil
            shutil.copyfile(dash_path_en, dash_path_fa)
        except Exception:
            pass
    except Exception as err:
        print(f"خطا در تولید تصویر داشبورد: {err}")
    
    print(f"🎉 تعداد {count_img} تصویر کارنامه در پوشه '{out_dir}' و تصویر داشبورد با موفقیت ذخیره شد!")
    print("=" * 75)
    
    # Automatically pop up the folder in Windows Explorer
    if sys.platform == 'win32':
        try:
            os.system(f'explorer "{os.path.abspath(out_dir)}"')
        except Exception:
            pass

if __name__ == '__main__':
    # Allow month from command line: python run_aggregation.py مهر
    month_arg = sys.argv[1] if len(sys.argv) > 1 else None
    success, current_month = process_all_reports(selected_month=month_arg)
    generate_all_images_offline(month=current_month)
