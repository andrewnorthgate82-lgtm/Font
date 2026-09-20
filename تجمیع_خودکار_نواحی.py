# -*- coding: utf-8 -*-
"""
====================================================================
اسکریپت تجمیع خودکار فایل‌های گزارش ماهانه کارمندان نواحی نسرا
استان اصفهان - سال ۱۴۰۵
====================================================================
نحوه استفاده در کامپیوتر:
۱. این فایل و فایل «تهیه کارنامه نواحی.xlsx» را در یک پوشه قرار دهید.
۲. یک پوشه با نام «گزارشات_ماهانه» در کنار این فایل بسازید.
۳. فایل‌های اکسل ۳۲ کارمند/ناحیه را داخل پوشه «گزارشات_ماهانه» کپی کنید.
۴. این اسکریپت را اجرا کنید.
۵. تمام آمار به صورت خودکار استخراج شده و در «تهیه کارنامه نواحی.xlsx» ثبت می‌شود.
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

def count_active_rows(ws, check_cols=None):
    if ws is None:
        return 0
    if check_cols is None:
        check_cols = range(2, ws.max_column + 1)
    count = 0
    for r in range(2, ws.max_row + 1):
        if any(ws.cell(r, c).value is not None and str(ws.cell(r, c).value).strip() != '' for c in check_cols):
            count += 1
    return count

def process_all_reports(reports_folder="گزارشات_ماهانه", master_excel="تهیه کارنامه نواحی.xlsx"):
    print("=" * 65)
    print("🚀 آغاز فرآیند استخراج خودکار گزارش‌های ماهانه نواحی نسرا")
    print("=" * 65)
    
    if not os.path.exists(master_excel):
        print(f"❌ خطا: فایل مقصد '{master_excel}' یافت نشد!")
        return
        
    if not os.path.exists(reports_folder):
        print(f"📁 پوشه '{reports_folder}' یافت نشد. در حال ساخت پوشه...")
        os.makedirs(reports_folder, exist_ok=True)
        print(f"⚠️ لطفاً فایل‌های اکسل ماهانه کارمندان را در پوشه '{reports_folder}' قرار دهید و مجدداً اجرا فرمایید.")
        return
        
    excel_files = glob.glob(os.path.join(reports_folder, "*.xlsx"))
    excel_files = [f for f in excel_files if not os.path.basename(f).startswith("~$")]
    
    if not excel_files:
        print(f"⚠️ هیچ فایل اکسلی در پوشه '{reports_folder}' یافت نشد.")
        return
        
    print(f"📋 تعداد {len(excel_files)} فایل اکسل در پوشه شناسایی شد.")
    print("-" * 65)
    
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
                
            c_hoz = count_active_rows(ws_hozori, [2, 3, 4, 5])
            c_tav = count_active_rows(ws_tavanmand, [2, 3, 4, 5])
            c_maj = count_active_rows(ws_majazi, [2, 3, 4, 5])
            c_kha = count_active_rows(ws_khalagh, [2, 3, 4, 5])
            c_tol = count_active_rows(ws_tolid, [2, 3, 4, 5, 6])
            
            hoz_comb = c_hoz + c_tav
            neshast = 1 if (hoz_comb + c_maj + c_kha + c_tol) > 0 else 0
            
            extracted[detected] = {
                'hozori_total': hoz_comb,
                'hozori_pure': c_hoz,
                'tavanmand': c_tav,
                'majazi': c_maj,
                'khalagh': c_kha,
                'tolid': c_tol,
                'neshast': neshast,
                'file': fname
            }
            print(f"✓ [{detected}]: حضوری و توانمند={hoz_comb} (حضوری:{c_hoz}+گردان:{c_tav}) | مجازی={c_maj} | خلاقانه={c_kha} | تولیدات={c_tol} | نشست={neshast}")
            
        except Exception as e:
            print(f"❌ خطا در خواندن فایل '{fname}': {e}")

    print("-" * 65)
    print(f"💾 در حال درج داده‌های {len(extracted)} شهرستان در فایل '{master_excel}'...")
    
    wb_master = openpyxl.load_workbook(master_excel)
    ws_rep = wb_master['گزارش عملکرد ماهانه']
    
    # Map row
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
    print(f"🎉 عملیات با موفقیت پایان یافت! اطلاعات {updated_count} ناحیه در فایل ذخیره شد.")
    print("📊 اکنون فایل 'تهیه کارنامه نواحی.xlsx' را باز فرمایید؛ تمام محاسبات، رتبه‌ها و داشبورد مانیتورینگ آماده است.")
    print("=" * 65)

if __name__ == '__main__':
    process_all_reports()
