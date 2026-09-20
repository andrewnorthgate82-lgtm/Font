# -*- coding: utf-8 -*-
"""
====================================================================
اسکریپت تجمیع خودکار فایل‌های گزارش ماهانه کارمندان نواحی نسرا
استان اصفهان - سال ۱۴۰۵
====================================================================
مبنای محاسبه: «مجموع تعداد نفرات» ثبت‌شده در ستون‌های نفرات کلاس‌ها
====================================================================
نحوه استفاده در کامپیوتر:
۱. این فایل و فایل «تهیه کارنامه نواحی.xlsx» را در یک پوشه قرار دهید.
۲. یک پوشه با نام «گزارشات_ماهانه» در کنار این فایل بسازید.
۳. فایل‌های اکسل ۳۲ کارمند/ناحیه را داخل پوشه «گزارشات_ماهانه» کپی کنید.
۴. روی فایل «اجرای_تجمیع_نواحی.bat» دو بار کلیک کنید (یا python تجمیع_خودکار_نواحی.py).
۵. تمام نفرات از کلاس‌ها جمع زده شده و در «تهیه کارنامه نواحی.xlsx» ثبت می‌شود.
====================================================================
"""

import os
import re
import glob
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

def process_all_reports(reports_folder="گزارشات_ماهانه", master_excel="تهیه کارنامه نواحی.xlsx"):
    print("=" * 70)
    print("🚀 سامانه تجمیع خودکار گزارش‌های ماهانه نواحی نسرا (بر مبنای تعداد نفرات)")
    print("=" * 70)
    
    if not os.path.exists(master_excel):
        print(f"❌ خطا: فایل مقصد '{master_excel}' یافت نشد!")
        return
        
    if not os.path.exists(reports_folder):
        print(f"📁 پوشه '{reports_folder}' یافت نشد. در حال ساخت پوشه...")
        os.makedirs(reports_folder, exist_ok=True)
        print(f"⚠️ لطفاً فایل‌های اکسل ماهانه کارمندان را داخل پوشه '{reports_folder}' قرار دهید و مجدداً اجرا فرمایید.")
        return
        
    excel_files = glob.glob(os.path.join(reports_folder, "*.xlsx"))
    excel_files = [f for f in excel_files if not os.path.basename(f).startswith("~$")]
    
    if not excel_files:
        print(f"⚠️ هیچ فایل اکسلی در پوشه '{reports_folder}' یافت نشد.")
        return
        
    print(f"📋 تعداد {len(excel_files)} فایل اکسل در پوشه شناسایی شد.")
    print("-" * 70)
    
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
                
            # If not detected from filename, detect from sheets
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
            
            # مجموع نفرات حضوری + توانمندسازی گردان
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
                'details': f"حضوری و گردان: {metric_hoz} نفر ({hoz_cls} کلاس) | مجازی: {metric_maj} نفر ({m_maj['classes_count']} لایو) | خلاقانه: {metric_kha} نفر | تولیدات: {metric_tol}"
            }
            print(f"✓ [{detected}]: {extracted[detected]['details']}")
            
        except Exception as e:
            print(f"❌ خطا در خواندن فایل '{fname}': {e}")

    print("-" * 70)
    print(f"💾 در حال درج داده‌های {len(extracted)} شهرستان در فایل '{master_excel}'...")
    
    wb_master = openpyxl.load_workbook(master_excel)
    ws_rep = wb_master['گزارش عملکرد ماهانه']
    
    row_map = {}
    for r in range(2, ws_rep.max_row + 1):
        dname = ws_rep.cell(r, 1).value
        if dname:
            row_map[clean_str(dname)] = r
            row_map[clean_no_vav(dname)] = r

    updated_count = 0
    for dname, data in extracted.items():
        r = row_map.get(clean_str(dname)) or row_map.get(clean_no_vav(dname))
        if r:
            ws_rep.cell(row=r, column=2, value=data['hozori_total'])
            ws_rep.cell(row=r, column=3, value=data['majazi'])
            ws_rep.cell(row=r, column=4, value=data['khalagh'])
            ws_rep.cell(row=r, column=5, value=data['tolid'])
            ws_rep.cell(row=r, column=6, value=data['neshast'])
            updated_count += 1
            
    wb_master.save(master_excel)
    print(f"🎉 عملیات با موفقیت پایان یافت! آمار {updated_count} ناحیه بر اساس مجموع تعداد نفرات ثبت شد.")
    print("📊 اکنون فایل 'تهیه کارنامه نواحی.xlsx' را باز فرمایید؛ تمام محاسبات، رتبه‌ها و داشبورد مانیتورینگ آماده است.")
    print("=" * 70)

if __name__ == '__main__':
    process_all_reports()
    generate_all_images_offline()

def generate_all_images_offline(master_excel="تهیه کارنامه نواحی.xlsx"):
    try:
        from image_generator import generate_scorecard_png, generate_dashboard_png
    except ImportError:
        print("⚠️ ماژول‌های تولید تصویر نصب نیستند یا یافت نشدند.")
        return
        
    out_dir = "تصاویر_کارنامه‌ها"
    os.makedirs(out_dir, exist_ok=True)
    print("-" * 70)
    print(f"📸 در حال تولید تصاویر کارنامه برای تمامی ۳۲ شهرستان در پوشه '{out_dir}'...")
    
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
            
    # Calculate scores & ranks
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
    rank_map = {item[0]: (idx + 1) for idx, item in enumerate(dist_scores)}
    
    count_img = 0
    for dn in DISTRICTS:
        t = targets.get(dn, {})
        a = actuals.get(dn, {'overall_score': 0})
        sc = a.get('overall_score', 0)
        rk = rank_map.get(dn, "-") if sc > 0 else "-"
        tier = "عالی (۱۰۰٪+)" if sc >= 100 else ("خوب (۷۵-۹۹٪)" if sc >= 75 else ("متوسط (۵۰-۷۴٪)" if sc >= 50 else ("ضعیف" if sc > 0 else "ثبت نشده")))
        
        out_p = os.path.join(out_dir, f"کارنامه_{dn}.png")
        generate_scorecard_png(dn, t, a, rank=str(rk), tier=tier, output_path=out_p)
        count_img += 1

    # Also generate provincial dashboard image
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
    top5 = [(i+1, dist_scores[i][0], dist_scores[i][1]) for i in range(min(5, len(dist_scores)))]
    bot5 = [(i+1, dist_scores[len(dist_scores)-1-i][0], dist_scores[len(dist_scores)-1-i][1]) for i in range(min(5, len(dist_scores)))]
    avg_sc = sum(x[1] for x in dist_scores) / len(dist_scores) if dist_scores else 0
    top_d = dist_scores[0][0] if dist_scores and dist_scores[0][1] > 0 else "در انتظار"
    rep_c = sum(1 for x in dist_scores if x[1] > 0)
    
    kpi_d = {'avg_score': avg_sc, 'top_district': top_d, 'reported_count': rep_c}
    dash_path = "تصویر_داشبورد_مدیریتی_استان.png"
    generate_dashboard_png(macro_data, top5, bot5, kpi_d, output_path=dash_path)
    
    print(f"🎉 تعداد {count_img} تصویر کارنامه در پوشه '{out_dir}' و ۱ تصویر داشبورد مدیریتی ذخیره شد!")
