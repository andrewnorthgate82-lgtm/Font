from image_generator import generate_scorecard_png, generate_dashboard_png
# -*- coding: utf-8 -*-
import os
import re
import io
import zipfile
from flask import Flask, request, jsonify, send_file, render_template_string
import openpyxl

app = Flask(__name__)
BASE_DIR = "/home/user/Font"
MAIN_EXCEL_PATH = os.path.join(BASE_DIR, "تهیه کارنامه نواحی.xlsx")

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
    
    # 1. Exact match
    for d in DISTRICTS:
        cd = clean_str(d)
        if cd == c_raw:
            return d
            
    # 2. Substring match
    for d in DISTRICTS:
        cd = clean_str(d)
        if cd in c_raw:
            return d
            
    # 3. Match without vav
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
    # Replace Persian & Arabic digits
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
    
    # Look for 'نفر' or 'بازدید'
    for c in range(1, ws.max_column + 1):
        h = str(ws.cell(1, c).value or '')
        if any(k in h for k in ['نفر', 'بازدید']):
            target_col = c
            target_col_name = h
            break
            
    # Fallback to 'تعداد' or 'صفح'
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
        # Check if row has active event
        has_activity = any(ws.cell(r, c).value is not None and str(ws.cell(r, c).value).strip() != '' for c in range(2, ws.max_column + 1))
        if has_activity:
            active_classes += 1
            if target_col:
                v = ws.cell(r, target_col).value
                total_people += parse_number(v)
                
    return {
        'people_sum': int(total_people),
        'classes_count': active_classes,
        'col_name': target_col_name
    }

def analyze_workbook_data(wb, filename_hint=""):
    detected_district = match_district_name(filename_hint)
    
    # Find sheets
    ws_hozori = None
    ws_tavanmand = None
    ws_majazi = None
    ws_khalagh = None
    ws_tolid = None
    
    for name in wb.sheetnames:
        c_name = clean_str(name)
        if 'حضوری' in c_name:
            ws_hozori = wb[name]
        elif 'توانمند' in c_name:
            ws_tavanmand = wb[name]
        elif 'مجازی' in c_name or 'لایو' in c_name:
            ws_majazi = wb[name]
        elif 'خلاق' in c_name:
            ws_khalagh = wb[name]
        elif 'تولید' in c_name:
            ws_tolid = wb[name]

    # Detect district from inside sheets if not in filename
    if not detected_district:
        for ws in [ws_hozori, ws_majazi, ws_khalagh, ws_tavanmand]:
            if ws is None:
                continue
            for r in range(2, min(ws.max_row + 1, 25)):
                for c in [3, 4, 2]:
                    val = ws.cell(r, c).value
                    matched = match_district_name(val)
                    if matched:
                        detected_district = matched
                        break
                if detected_district:
                    break
            if detected_district:
                break

    if not detected_district and 'اطلاعات پایه' in wb.sheetnames:
        ws_info = wb['اطلاعات پایه']
        for r in range(2, ws_info.max_row + 1):
            val = ws_info.cell(r, 3).value
            matched = match_district_name(val)
            if matched:
                detected_district = matched
                break

    # Extract metrics by SUM OF PEOPLE
    m_hoz = extract_sheet_metrics(ws_hozori)
    m_tav = extract_sheet_metrics(ws_tavanmand)
    m_maj = extract_sheet_metrics(ws_majazi)
    m_kha = extract_sheet_metrics(ws_khalagh)
    m_tol = extract_sheet_metrics(ws_tolid)
    
    # Combined Indicator 1: مجموع نفرات سواد رسانه حضوری + توانمندسازی گردان
    hoz_people_total = m_hoz['people_sum'] + m_tav['people_sum']
    hoz_classes_total = m_hoz['classes_count'] + m_tav['classes_count']
    # If people column was left empty but classes were held, fallback to class count
    metric_hozori = hoz_people_total if hoz_people_total > 0 else hoz_classes_total
    
    # Indicator 2: سواد رسانه مجازی
    metric_majazi = m_maj['people_sum'] if m_maj['people_sum'] > 0 else m_maj['classes_count']
    
    # Indicator 3: اقدامات خلاقانه
    metric_khalagh = m_kha['people_sum'] if m_kha['people_sum'] > 0 else m_kha['classes_count']
    
    # Indicator 4: تولیدات رسانه‌ای (اگر تعداد صفحات ذکر شده جمع صفحات وگرنه تعداد آثار)
    metric_tolid = m_tol['people_sum'] if m_tol['people_sum'] > 0 else m_tol['classes_count']
    
    # Indicator 5: نشست انجمن مدرسان
    neshast = 1 if (metric_hozori + metric_majazi + metric_khalagh + metric_tolid) > 0 else 0

    return {
        'filename': filename_hint,
        'district': detected_district,
        # Metrics for Excel insertion (SUM OF PEOPLE)
        'hozori_total': metric_hozori,
        'majazi': metric_majazi,
        'khalagh': metric_khalagh,
        'tolid': metric_tolid,
        'neshast': neshast,
        # Detailed breakdowns for UI display
        'hoz_people': m_hoz['people_sum'],
        'hoz_classes': m_hoz['classes_count'],
        'tav_people': m_tav['people_sum'],
        'tav_classes': m_tav['classes_count'],
        'maj_people': m_maj['people_sum'],
        'maj_classes': m_maj['classes_count'],
        'kha_people': m_kha['people_sum'],
        'kha_classes': m_kha['classes_count'],
        'tol_people': m_tol['people_sum'],
        'tol_classes': m_tol['classes_count'],
        'status': 'شناسایی شد' if detected_district else 'عدم تشخیص ناحیه'
    }

HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>سامانه استخراج خودکار و مانیتورینگ عملکرد نواحی نسرا</title>
    <style>
        :root {
            --primary: #1B365D;
            --primary-light: #2C3E50;
            --accent: #2980B9;
            --success: #27AE60;
            --warning: #F39C12;
            --danger: #C0392B;
            --bg: #F4F6F9;
            --card-bg: #FFFFFF;
            --text: #2C3E50;
        }
        * { box-sizing: border-box; font-family: 'Tahoma', 'Segoe UI', sans-serif; }
        body { background-color: var(--bg); color: var(--text); margin: 0; padding: 20px; direction: rtl; }
        .container { max-width: 1150px; margin: 0 auto; }
        .header {
            background: linear-gradient(135deg, #1B365D, #2C3E50);
            color: white; padding: 25px; border-radius: 12px;
            text-align: center; box-shadow: 0 4px 15px rgba(0,0,0,0.1); margin-bottom: 20px;
        }
        .header h1 { margin: 0 0 8px 0; font-size: 22px; }
        .header p { margin: 0; opacity: 0.9; font-size: 13.5px; }
        
        .card {
            background: var(--card-bg); border-radius: 12px; padding: 22px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.06); margin-bottom: 20px;
        }
        .card-header {
            display: flex; justify-content: space-between; align-items: center;
            border-bottom: 2px solid #ECEFF1; padding-bottom: 12px; margin-bottom: 15px;
        }
        .card-header h2 { margin: 0; font-size: 17px; color: var(--primary); }

        .dropzone {
            border: 2px dashed #3498DB; border-radius: 10px; padding: 35px 20px;
            text-align: center; background: #EBF5FB; cursor: pointer; transition: all 0.2s;
        }
        .dropzone:hover, .dropzone.dragover { background: #D4E6F1; border-color: #2980B9; }
        .dropzone-icon { font-size: 42px; margin-bottom: 10px; }
        .dropzone h3 { margin: 0 0 6px 0; font-size: 16px; color: #1B4F72; }
        .dropzone p { margin: 0; font-size: 12.5px; color: #5D6D7E; }

        .btn {
            display: inline-flex; align-items: center; justify-content: center;
            padding: 10px 20px; font-size: 14px; font-weight: bold; border-radius: 6px;
            text-decoration: none; cursor: pointer; border: none; transition: all 0.2s;
        }
        .btn-success { background: #27AE60; color: white; box-shadow: 0 3px 8px rgba(39,174,96,0.3); }
        .btn-success:hover { background: #219150; }
        .btn-primary { background: #2980B9; color: white; box-shadow: 0 3px 8px rgba(41,128,185,0.3); }
        .btn-primary:hover { background: #1F618D; }
        .btn-warning { background: #F39C12; color: white; }
        .btn-warning:hover { background: #D68910; }
        .btn-secondary { background: #7F8C8D; color: white; }

        table { width: 100%; border-collapse: collapse; font-size: 12.5px; margin-top: 15px; }
        th { background: #1B365D; color: white; padding: 10px 8px; font-weight: bold; text-align: center; }
        td { padding: 9px 8px; border-bottom: 1px solid #EAECEE; text-align: center; }
        tr:nth-child(even) { background-color: #F8FAFC; }
        
        .badge {
            display: inline-block; padding: 3px 8px; border-radius: 10px; font-size: 11px; font-weight: bold;
        }
        .badge-success { background: #D4EDDA; color: #155724; }
        .badge-danger { background: #F8D7DA; color: #721C24; }

        .spinner {
            display: none; border: 4px solid #f3f3f3; border-top: 4px solid #3498db;
            border-radius: 50%; width: 28px; height: 28px; animation: spin 1s linear infinite;
            margin: 15px auto;
        }
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
        
        .alert-box {
            background: #E8F8F5; border-right: 4px solid #27AE60; padding: 12px 16px;
            border-radius: 6px; font-size: 13px; line-height: 1.7; color: #0E6251; margin-bottom: 15px;
        }
        .sub-metric { font-size: 11px; color: #666; display: block; margin-top: 2px; }
        .main-metric { font-size: 14px; font-weight: bold; color: #1B365D; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🚀 سامانه استخراج خودکار و مانیتورینگ کارنامه نواحی نسرا</h1>
            <p>نسرا استان اصفهان - سال ۱۴۰۵ | محاسبه هوشمند بر اساس «مجموع تعداد نفرات» کلاس‌ها</p>
        </div>

        <div class="card">
            <div class="card-header">
                <h2>📥 آپلود و تحلیل خودکار فایل‌های گزارش ماهانه (۳۲ شهرستان)</h2>
                <div>
                    <button class="btn btn-warning" onclick="generateAndAnalyzeSampleData()" id="btn-sample">
                        ⚡ آزمایش با ۳۲ فایل تستی نمونه
                    </button>
                    <a href="/download/master" class="btn btn-primary" style="margin-right: 8px;">
                        📥 دانلود فایل اصلی فعلی
                    </a>
                </div>
            </div>

            <div class="alert-box">
                <strong>✓ ملاک سنجش (مجموع تعداد نفرات):</strong> در این نسخه طبق دستور شما، سیستم از هر شیت به صورت خودکار <strong>ستون «تعداد نفرات / بازدید»</strong> کلاس‌ها را تجمیع (SUM) می‌کند. در شاخص ۱ نیز مجموع نفرات شیت‌های «سوادرسانه حضوری» و «توانمندسازی گردان» با یکدیگر جمع شده و درج می‌گردد.
            </div>

            <div class="dropzone" id="dropzone" onclick="document.getElementById('fileInput').click()">
                <div class="dropzone-icon">📂</div>
                <h3>فایل‌های اکسل ماهانه کارمندان (.xlsx) یا فایل ZIP را اینجا بکشید و رها کنید</h3>
                <p>می‌توانید تمامی ۳۲ فایل را به صورت همزمان انتخاب فرمایید</p>
                <input type="file" id="fileInput" multiple accept=".xlsx,.zip" style="display:none" onchange="handleFileSelect(this.files)">
            </div>
            
            <div class="spinner" id="spinner"></div>

            <div id="resultsArea" style="display: none; margin-top: 20px;">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <h3 style="margin: 0; color: #1B365D;">📊 نتایج استخراج شده بر اساس تعداد نفرات (<span id="fileCount">0</span> فایل):</h3>
                    <button class="btn btn-success" onclick="applyAndDownload()" id="btn-apply" style="font-size: 15px; padding: 12px 24px;">
                        💾 درج در فایل «تهیه کارنامه نواحی» و دانلود اکسل نهایی ⬇️
                    </button>
                </div>

                <div style="overflow-x: auto;">
                    <table id="resultsTable">
                        <thead>
                            <tr>
                                <th>ردیف</th>
                                <th>نام فایل ارسالی</th>
                                <th>شهرستان شناسایی‌شده</th>
                                <th>۱. حضوری و توانمندسازی (۳۱×)<br><small style="font-weight:normal;">مجموع نفرات شرکت‌کننده</small></th>
                                <th>۲. مجازی و لایو (۲۱۷×)<br><small style="font-weight:normal;">مجموع نفرات / بازدید</small></th>
                                <th>۳. اقدامات خلاقانه (۶۲×)<br><small style="font-weight:normal;">مجموع مخاطبان / بازدید</small></th>
                                <th>۴. تولیدات رسانه‌ای (۳×)<br><small style="font-weight:normal;">تعداد صفحات / آثار</small></th>
                                <th>۵. نشست انجمن</th>
                                <th>وضعیت</th>
                            </tr>
                        </thead>
                        <tbody id="resultsTbody"></tbody>
                    </table>
                </div>
            </div>
        </div>

        
        <div class="card" style="border-top: 5px solid #8E44AD;">
            <div class="card-header">
                <h2>📸 دریافت تصویر باکیفیت کارنامه و داشبورد (جهت ارسال در ایتا / بله / واتساپ)</h2>
            </div>
            <p style="font-size: 13.5px; line-height: 1.7; color: #444; margin-bottom: 15px;">
                بدون نیاز به گرفتن اسکرین‌شات یا اجرای ماکرو، می‌توانید تصویر رسمی کارنامه هر شهرستان یا داشبورد کل استان را به صورت فایل عکس (PNG) دانلود فرمایید:
            </p>
            <div style="display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-bottom: 15px;">
                <label style="font-weight: bold; font-size: 14px;">دوره ارزیابی (ماه):</label>
                <select id="selectMonth" style="padding: 10px 14px; font-size: 14px; border-radius: 6px; border: 1px solid #BDC3C7; font-weight: bold; color: #8E44AD; margin-left: 15px;">
                    <option value="فروردین">فروردین</option>
                    <option value="اردیبهشت">اردیبهشت</option>
                    <option value="خرداد">خرداد</option>
                    <option value="تیر">تیر</option>
                    <option value="مرداد">مرداد</option>
                    <option value="شهریور" selected>شهریور</option>
                    <option value="مهر">مهر</option>
                    <option value="آبان">آبان</option>
                    <option value="آذر">آذر</option>
                    <option value="دی">دی</option>
                    <option value="بهمن">بهمن</option>
                    <option value="اسفند">اسفند</option>
                </select>
                <label style="font-weight: bold; font-size: 14px;">انتخاب شهرستان:</label>
                <select id="selectDistrict" style="padding: 10px 14px; font-size: 14px; border-radius: 6px; border: 1px solid #BDC3C7; font-weight: bold; color: #1B365D;">
                    <option value="کاشان">کاشان</option>
                    <option value="نجف آباد">نجف آباد</option>
                    <option value="شاهین شهر">شاهین شهر</option>
                    <option value="لنجان">لنجان</option>
                    <option value="خمینی شهر">خمینی شهر</option>
                    <option value="آران و بیدگل">آران و بیدگل</option>
                    <option value="امام حسین(ع)">امام حسین(ع)</option>
                    <option value="امام رضا(ع)">امام رضا(ع)</option>
                    <option value="امام صادق(ع)">امام صادق(ع)</option>
                    <option value="امام علی(ع)">امام علی(ع)</option>
                    <option value="اردستان">اردستان</option>
                    <option value="برخوار">برخوار</option>
                    <option value="بویین و میاندشت">بویین و میاندشت</option>
                    <option value="تیران و کرون">تیران و کرون</option>
                    <option value="جرقویه">جرقویه</option>
                    <option value="چادگان">چادگان</option>
                    <option value="خوانسار">خوانسار</option>
                    <option value="خور و بیابانک">خور و بیابانک</option>
                    <option value="درچه">درچه</option>
                    <option value="دهاقان">دهاقان</option>
                    <option value="سمیرم">سمیرم</option>
                    <option value="شهرضا">شهرضا</option>
                    <option value="فریدن">فریدن</option>
                    <option value="فریدون شهر">فریدون شهر</option>
                    <option value="فلاورجان">فلاورجان</option>
                    <option value="کوهپایه">کوهپایه</option>
                    <option value="گلپایگان">گلپایگان</option>
                    <option value="مبارکه">مبارکه</option>
                    <option value="نایین">نایین</option>
                    <option value="نطنز">نطنز</option>
                    <option value="ورزنه">ورزنه</option>
                    <option value="هرند">هرند</option>
                </select>
                <button class="btn btn-primary" onclick="downloadSelectedScorecard()" style="background: #8E44AD; box-shadow: 0 3px 8px rgba(142,68,173,0.3);">
                    📸 دانلود تصویر کارنامه این شهرستان (PNG)
                </button>
                <button class="btn btn-success" onclick="downloadDashboardImage()" style="margin-right: auto;">
                    📊 دانلود تصویر داشبورد مدیریتی کل استان (PNG)
                </button>
            </div>
        </div>

        <div class="card">
            <div class="card-header">
                <h2>🛠️ راهنمای اجرای آفلاین روی کامپیوتر شخصی (بدون نیاز به اینترنت)</h2>
            </div>
            <p style="font-size: 13.5px; line-height: 1.7; color: #444;">
                اگر تمایل دارید این فرآیند را به صورت آفلاین بر روی کامپیوتر شخصی خودتان اجرا کنید:
            </p>
            <ol style="font-size: 13px; line-height: 1.8; color: #333;">
                <li>در کنار فایل <code>تهیه کارنامه نواحی.xlsx</code> یک پوشه با نام <code>گزارشات_ماهانه</code> ایجاد فرمایید.</li>
                <li>فایل‌های اکسل ۳۲ کارمند را داخل آن پوشه بریزید.</li>
                <li>روی فایل <code>اجرای_تجمیع_نواحی.bat</code> دوبار کلیک کنید. سیستم تمام ستون‌های «تعداد نفرات» را جمع زده و در ۲ ثانیه کارنامه را تکمیل می‌کند!</li>
            </ol>
            <div>
                <a href="/download/zip" class="btn btn-success" style="font-size: 14.5px; padding: 11px 22px; margin-left: 10px;">
                    🎁 دانلود بسته کامل آفلاین (فایل ZIP آماده برای کامپیوتر)
                </a>
                <a href="/download/script" class="btn btn-secondary">
                    🐍 دانلود تکی اسکریپت (تجمیع_خودکار_نواحی.py)
                </a>
            </div>
        </div>
    </div>

    <script>
        const dropzone = document.getElementById('dropzone');
        let currentExtractedData = [];

        dropzone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropzone.classList.add('dragover');
        });
        dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
        dropzone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropzone.classList.remove('dragover');
            if (e.dataTransfer.files.length > 0) {
                uploadFiles(e.dataTransfer.files);
            }
        });

        function handleFileSelect(files) {
            if (files.length > 0) {
                uploadFiles(files);
            }
        }

        async function uploadFiles(files) {
            const formData = new FormData();
            for (let i = 0; i < files.length; i++) {
                formData.append('files', files[i]);
            }

            document.getElementById('spinner').style.display = 'block';
            try {
                const response = await fetch('/api/analyze', {
                    method: 'POST',
                    body: formData
                });
                const res = await response.json();
                document.getElementById('spinner').style.display = 'none';

                if (res.success) {
                    currentExtractedData = res.data;
                    renderTable(res.data);
                } else {
                    alert('خطا در پردازش فایل‌ها: ' + res.error);
                }
            } catch (err) {
                document.getElementById('spinner').style.display = 'none';
                alert('خطا در برقراری ارتباط با سرور: ' + err);
            }
        }

        function renderTable(data) {
            document.getElementById('resultsArea').style.display = 'block';
            document.getElementById('fileCount').innerText = data.length;
            const tbody = document.getElementById('resultsTbody');
            tbody.innerHTML = '';

            data.forEach((item, idx) => {
                const tr = document.createElement('tr');
                const isMatched = item.district !== null;
                const badge = isMatched 
                    ? `<span class="badge badge-success">✓ شناسایی شد</span>`
                    : `<span class="badge badge-danger">✗ نامشخص</span>`;

                tr.innerHTML = `
                    <td>${idx + 1}</td>
                    <td style="text-align: right; font-weight: 500;">${item.filename}</td>
                    <td style="font-weight: bold; color: #1B365D;">${item.district || 'تشخیص داده نشد'}</td>
                    <td>
                        <span class="main-metric">${item.hozori_total.toLocaleString()} نفر</span>
                        <span class="sub-metric">${item.hoz_people.toLocaleString()} حضوری (${item.hoz_classes} کلاس) + ${item.tav_people.toLocaleString()} گردان (${item.tav_classes} کلاس)</span>
                    </td>
                    <td>
                        <span class="main-metric">${item.majazi.toLocaleString()} نفر</span>
                        <span class="sub-metric">${item.maj_classes} برنامه لایو</span>
                    </td>
                    <td>
                        <span class="main-metric">${item.khalagh.toLocaleString()} نفر</span>
                        <span class="sub-metric">${item.kha_classes} اقدام خلاقانه</span>
                    </td>
                    <td>
                        <span class="main-metric">${item.tolid.toLocaleString()}</span>
                        <span class="sub-metric">${item.tol_classes} اثر / عنوان</span>
                    </td>
                    <td><strong style="color: #27AE60;">${item.neshast} نشست</strong></td>
                    <td>${badge}</td>
                `;
                tbody.appendChild(tr);
            });
        }

        async function applyAndDownload() {
            if (currentExtractedData.length === 0) {
                alert('هیچ داده‌ای برای ثبت وجود ندارد!');
                return;
            }

            const btn = document.getElementById('btn-apply');
            btn.innerText = '⏳ در حال درج در اکسل...';
            btn.disabled = true;

            try {
                const response = await fetch('/api/apply', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ items: currentExtractedData })
                });

                if (response.ok) {
                    const blob = await response.blob();
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'تهیه کارنامه نواحی_تکمیل_شده.xlsx';
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    btn.innerText = '✅ انجام شد! دانلود مجدد فایل ⬇️';
                    btn.disabled = false;
                } else {
                    const err = await response.json();
                    alert('خطا: ' + err.error);
                    btn.innerText = '💾 درج در فایل «تهیه کارنامه نواحی» و دانلود اکسل نهایی ⬇️';
                    btn.disabled = false;
                }
            } catch (e) {
                alert('خطا در ارسال درخواست: ' + e);
                btn.innerText = '💾 درج در فایل «تهیه کارنامه نواحی» و دانلود اکسل نهایی ⬇️';
                btn.disabled = false;
            }
        }

        async 
        function downloadSelectedScorecard() {
            const d = document.getElementById('selectDistrict').value;
            const m = document.getElementById('selectMonth').value;
            window.location.href = '/image/scorecard?district=' + encodeURIComponent(d) + '&month=' + encodeURIComponent(m);
        }
        function downloadDashboardImage() {
            const m = document.getElementById('selectMonth').value;
            window.location.href = '/image/dashboard?month=' + encodeURIComponent(m);
        }

        function generateAndAnalyzeSampleData() {
            const btn = document.getElementById('btn-sample');
            btn.innerText = '⏳ در حال تولید داده‌های ۳۲ ناحیه بر مبنای تعداد نفرات...';
            btn.disabled = true;

            try {
                const response = await fetch('/api/generate-sample-data', { method: 'POST' });
                const res = await response.json();
                btn.innerText = '⚡ آزمایش با ۳۲ فایل تستی نمونه';
                btn.disabled = false;

                if (res.success) {
                    currentExtractedData = res.data;
                    renderTable(res.data);
                } else {
                    alert('خطا: ' + res.error);
                }
            } catch (e) {
                btn.innerText = '⚡ آزمایش با ۳۲ فایل تستی نمونه';
                btn.disabled = false;
                alert('خطا: ' + e);
            }
        }
    </script>
</body>
</html>
"""

@app.route('/')
def index():
    return render_template_string(HTML_TEMPLATE)



@app.route('/image/scorecard')
def get_scorecard_img():
    d_name = request.args.get('district', 'کاشان')
    month = request.args.get('month', 'شهریور')
    wb = openpyxl.load_workbook(MAIN_EXCEL_PATH, data_only=True)
    ws_target = wb['پایگاه داده حد انتظار']
    ws_rep = wb['گزارش عملکرد ماهانه']
    ws_dash = wb['داشبورد مانیتورینگ نواحی']
    
    # Get target
    t_data = {'branches': 5, 'hozori': 155, 'majazi': 1085, 'khalagh': 310, 'tolid': 15, 'neshast': 1}
    for r in range(2, 34):
        if ws_target.cell(r, 1).value == d_name:
            t_data = {
                'branches': ws_target.cell(r, 2).value or 0,
                'hozori': ws_target.cell(r, 3).value or 0,
                'majazi': ws_target.cell(r, 4).value or 0,
                'khalagh': ws_target.cell(r, 5).value or 0,
                'tolid': ws_target.cell(r, 6).value or 0,
                'neshast': ws_target.cell(r, 7).value or 1
            }
            break
            
    # Get actual
    a_data = {'hozori': 0, 'majazi': 0, 'khalagh': 0, 'tolid': 0, 'neshast': 0, 'overall_score': 0}
    for r in range(2, ws_rep.max_row + 1):
        if ws_rep.cell(r, 1).value == d_name:
            a_data['hozori'] = ws_rep.cell(r, 2).value or 0
            a_data['majazi'] = ws_rep.cell(r, 3).value or 0
            a_data['khalagh'] = ws_rep.cell(r, 4).value or 0
            a_data['tolid'] = ws_rep.cell(r, 5).value or 0
            a_data['neshast'] = ws_rep.cell(r, 6).value or 0
            break
            
    # Calculate score & rank
    pcts = []
    for k in ['hozori', 'majazi', 'khalagh', 'tolid', 'neshast']:
        t_v = t_data.get(k, 1)
        a_v = a_data.get(k, 0)
        pcts.append((a_v / t_v * 100) if t_v > 0 else 0)
    a_data['overall_score'] = sum(pcts) / len(pcts) if pcts else 0
    
    tier = "عالی (۱۰۰٪+)" if a_data['overall_score'] >= 100 else ("خوب (۷۵-۹۹٪)" if a_data['overall_score'] >= 75 else ("متوسط (۵۰-۷۴٪)" if a_data['overall_score'] >= 50 else ("ضعیف" if a_data['overall_score'] > 0 else "ثبت نشده")))
    
    img_io = io.BytesIO()
    generate_scorecard_png(d_name, t_data, a_data, rank="۱", tier=tier, month=month, output_path=img_io)
    img_io.seek(0)
    
    fa_filename = f"کارنامه_{d_name}_{month}.png"
    return send_file(img_io, mimetype="image/png", as_attachment=True, download_name=fa_filename)

@app.route('/image/dashboard')
def get_dashboard_img():
    month = request.args.get('month', 'شهریور')
    wb = openpyxl.load_workbook(MAIN_EXCEL_PATH, data_only=True)
    ws_target = wb['پایگاه داده حد انتظار']
    ws_rep = wb['گزارش عملکرد ماهانه']
    
    # Macro data
    sum_t_hoz = sum(ws_target.cell(r, 3).value or 0 for r in range(2, 34))
    sum_t_maj = sum(ws_target.cell(r, 4).value or 0 for r in range(2, 34))
    sum_t_kha = sum(ws_target.cell(r, 5).value or 0 for r in range(2, 34))
    sum_t_tol = sum(ws_target.cell(r, 6).value or 0 for r in range(2, 34))
    sum_t_nes = sum(ws_target.cell(r, 7).value or 0 for r in range(2, 34))
    
    sum_a_hoz = sum(ws_rep.cell(r, 2).value or 0 for r in range(2, 34))
    sum_a_maj = sum(ws_rep.cell(r, 3).value or 0 for r in range(2, 34))
    sum_a_kha = sum(ws_rep.cell(r, 4).value or 0 for r in range(2, 34))
    sum_a_tol = sum(ws_rep.cell(r, 5).value or 0 for r in range(2, 34))
    sum_a_nes = sum(ws_rep.cell(r, 6).value or 0 for r in range(2, 34))
    
    macro_data = [
        ('سواد رسانه حضوری و توانمندسازی (۳۱×)', int(sum_t_hoz), int(sum_a_hoz)),
        ('سواد رسانه مجازی و لایو (۲۱۷×)', int(sum_t_maj), int(sum_a_maj)),
        ('اقدامات و ابتکارات خلاقانه (۶۲×)', int(sum_t_kha), int(sum_a_kha)),
        ('تولیدات رسانه‌ای و محتوایی (۳×)', int(sum_t_tol), int(sum_a_tol)),
        ('نشست با انجمن مدرسان (۱ نشست)', int(sum_t_nes), int(sum_a_nes))
    ]
    
    # Calculate district scores
    dist_scores = []
    for r in range(2, 34):
        dname = ws_target.cell(r, 1).value
        p_list = []
        for c_idx in range(2, 7):
            t_v = ws_target.cell(r, c_idx + 1).value or 1
            a_v = ws_rep.cell(r, c_idx).value or 0
            p_list.append((a_v / t_v * 100) if t_v > 0 else 0)
        s = sum(p_list) / len(p_list)
        dist_scores.append((dname, s))
        
    dist_scores.sort(key=lambda x: x[1], reverse=True)
    top5 = [(i+1, dist_scores[i][0], dist_scores[i][1]) for i in range(min(5, len(dist_scores)))]
    bot5 = [(i+1, dist_scores[len(dist_scores)-1-i][0], dist_scores[len(dist_scores)-1-i][1]) for i in range(min(5, len(dist_scores)))]
    
    avg_score = sum(x[1] for x in dist_scores) / len(dist_scores) if dist_scores else 0
    top_d = dist_scores[0][0] if dist_scores and dist_scores[0][1] > 0 else "در انتظار"
    rep_cnt = sum(1 for x in dist_scores if x[1] > 0)
    
    kpi_data = {'avg_score': avg_score, 'top_district': top_d, 'reported_count': rep_cnt}
    
    img_io = io.BytesIO()
    generate_dashboard_png(macro_data, top5, bot5, kpi_data, month=month, output_path=img_io)
    img_io.seek(0)
    
    fa_dash_filename = f"تصویر_داشبورد_مدیریتی_استان_{month}.png"
    return send_file(img_io, mimetype="image/png", as_attachment=True, download_name=fa_dash_filename)

@app.route('/download/zip')
def download_zip():
    p = os.path.join(BASE_DIR, "بسته_آفلاین_مانیتورینگ_نواحی.zip")
    return send_file(p, as_attachment=True, download_name="بسته_آفلاین_مانیتورینگ_نواحی.zip")

@app.route('/download/master')
def download_master():
    return send_file(MAIN_EXCEL_PATH, as_attachment=True, download_name="تهیه کارنامه نواحی.xlsx")

@app.route('/download/template')
def download_template():
    p = os.path.join(BASE_DIR, "گزارش شهریور ماه 1405 ناحیه.xlsx")
    return send_file(p, as_attachment=True, download_name="گزارش شهریور ماه 1405 ناحیه.xlsx")

@app.route('/download/script')
def download_script():
    p = os.path.join(BASE_DIR, "تجمیع_خودکار_نواحی.py")
    return send_file(p, as_attachment=True, download_name="تجمیع_خودکار_نواحی.py")

@app.route('/api/analyze', methods=['POST'])
def api_analyze():
    uploaded_files = request.files.getlist('files')
    if not uploaded_files:
        return jsonify({'success': False, 'error': 'فایلی ارسال نشده است.'})
    
    results = []
    
    for f in uploaded_files:
        fname = f.filename
        if fname.endswith('.zip'):
            try:
                z = zipfile.ZipFile(f.stream)
                for member in z.namelist():
                    if member.endswith('.xlsx') and not member.startswith('__MACOSX') and not member.startswith('~$'):
                        content = z.read(member)
                        wb = openpyxl.load_workbook(io.BytesIO(content), data_only=True)
                        res = analyze_workbook_data(wb, filename_hint=os.path.basename(member))
                        results.append(res)
            except Exception as e:
                print(f"Error reading zip {fname}: {e}")
        elif fname.endswith('.xlsx') and not fname.startswith('~$'):
            try:
                wb = openpyxl.load_workbook(f.stream, data_only=True)
                res = analyze_workbook_data(wb, filename_hint=fname)
                results.append(res)
            except Exception as e:
                print(f"Error reading xlsx {fname}: {e}")
                results.append({
                    'filename': fname,
                    'district': None,
                    'hozori_total': 0,
                    'majazi': 0,
                    'khalagh': 0,
                    'tolid': 0,
                    'neshast': 0,
                    'hoz_people': 0, 'hoz_classes': 0,
                    'tav_people': 0, 'tav_classes': 0,
                    'maj_people': 0, 'maj_classes': 0,
                    'kha_people': 0, 'kha_classes': 0,
                    'tol_people': 0, 'tol_classes': 0,
                    'status': f'خطا: {str(e)}'
                })
                
    return jsonify({'success': True, 'data': results})

@app.route('/api/apply', methods=['POST'])
def api_apply():
    req_data = request.get_json()
    items = req_data.get('items', [])
    if not items:
        return jsonify({'error': 'داده‌ای یافت نشد'}), 400
        
    wb = openpyxl.load_workbook(MAIN_EXCEL_PATH)
    ws_rep = wb['گزارش عملکرد ماهانه']
    
    row_map = {}
    for r in range(2, ws_rep.max_row + 1):
        name = ws_rep.cell(r, 1).value
        if name:
            row_map[clean_str(name)] = r
            row_map[clean_no_vav(name)] = r

    applied_count = 0
    for item in items:
        d_name = item.get('district')
        if not d_name:
            continue
        c_name = clean_str(d_name)
        c_nv = clean_no_vav(d_name)
        
        target_row = row_map.get(c_name) or row_map.get(c_nv)
        if target_row:
            ws_rep.cell(row=target_row, column=2, value=item.get('hozori_total', 0))
            ws_rep.cell(row=target_row, column=3, value=item.get('majazi', 0))
            ws_rep.cell(row=target_row, column=4, value=item.get('khalagh', 0))
            ws_rep.cell(row=target_row, column=5, value=item.get('tolid', 0))
            ws_rep.cell(row=target_row, column=6, value=item.get('neshast', 0))
            applied_count += 1
            
    # Save back to file
    wb.save(MAIN_EXCEL_PATH)
    
    mem_file = io.BytesIO()
    wb.save(mem_file)
    mem_file.seek(0)
    
    return send_file(
        mem_file,
        as_attachment=True,
        download_name="تهیه کارنامه نواحی_تکمیل_شده.xlsx",
        mimetype="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )

@app.route('/api/generate-sample-data', methods=['POST'])
def api_generate_sample():
    wb_target = openpyxl.load_workbook(MAIN_EXCEL_PATH, data_only=True)
    ws_target = wb_target['پایگاه داده حد انتظار']
    
    targets = {}
    for r in range(2, 34):
        d_name = ws_target.cell(r, 1).value
        targets[d_name] = {
            'branches': ws_target.cell(r, 2).value,
            'hozori': ws_target.cell(r, 3).value,
            'majazi': ws_target.cell(r, 4).value,
            'khalagh': ws_target.cell(r, 5).value,
            'tolid': ws_target.cell(r, 6).value,
            'neshast': ws_target.cell(r, 7).value
        }
        
    sample_data = []
    import random
    random.seed(42)
    
    for idx, d_name in enumerate(DISTRICTS, start=1):
        t = targets.get(d_name, {'branches': 5, 'hozori': 155, 'majazi': 1085, 'khalagh': 310, 'tolid': 15, 'neshast': 1})
        factor = random.choice([0.48, 0.65, 0.82, 0.94, 1.05, 1.15, 0.76, 0.89])
        
        hoz_tot = int(t['hozori'] * factor)
        hoz_pure = int(hoz_tot * 0.55)
        tavan = hoz_tot - hoz_pure
        
        maj = int(t['majazi'] * factor)
        khal = int(t['khalagh'] * factor)
        tol = int(t['tolid'] * factor)
        nesh = 1 if factor >= 0.5 else 0
        
        # Realistic class counts
        hoz_cls = max(1, int(hoz_pure / 35))
        tav_cls = max(1, int(tavan / 35))
        maj_cls = max(1, int(maj / 250))
        kha_cls = max(1, int(khal / 80))
        tol_cls = max(1, int(tol / 3))
        
        sample_data.append({
            'filename': f"گزارش شهریور ماه_{d_name}.xlsx",
            'district': d_name,
            'hozori_total': hoz_tot,
            'majazi': maj,
            'khalagh': khal,
            'tolid': tol,
            'neshast': nesh,
            'hoz_people': hoz_pure,
            'hoz_classes': hoz_cls,
            'tav_people': tavan,
            'tav_classes': tav_cls,
            'maj_people': maj,
            'maj_classes': maj_cls,
            'kha_people': khal,
            'kha_classes': kha_cls,
            'tol_people': tol,
            'tol_classes': tol_cls,
            'status': 'شناسایی شد'
        })
        
    return jsonify({'success': True, 'data': sample_data})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080, debug=False)
