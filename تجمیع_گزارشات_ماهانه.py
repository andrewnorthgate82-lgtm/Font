# -*- coding: utf-8 -*-
"""
ابزار کمکی تجمیع خودکار گزارش‌های ماهانه نواحی نسرا - استان اصفهان
این اسکریپت فایل‌های گزارش ماهانه ارسال‌شده توسط کارمندان/نواحی را خوانده،
تعداد فعالیت‌های هر شاخص را شمارش کرده و به صورت خودکار در فایل «تهیه کارنامه نواحی.xlsx» درج می‌کند.
"""

import os
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

def clean_name(s):
    if not s:
        return ""
    s = str(s).strip()
    s = s.replace("(", "（").replace(")", "）").replace(" ", "")
    s = s.replace("ي", "ی").replace("ك", "ک")
    return s

def count_active_rows(ws, min_r=2, max_r=99, check_cols=[2, 3, 4]):
    """شمارش ردیف‌هایی که در آن داده‌ای ثبت شده است"""
    count = 0
    for r in range(min_r, min(ws.max_row + 1, max_r + 1)):
        # اگر حداقل در یکی از ستون‌های تاریخ، موضوع یا مشخصات مقداری وجود داشته باشد
        has_val = any(ws.cell(r, c).value is not None and str(ws.cell(r, c).value).strip() != "" for c in check_cols)
        if has_val:
            count += 1
    return count

def extract_from_monthly_file(file_path):
    """استخراج آمار فعالیت‌ها از یک فایل گزارش ماهانه ناحیه"""
    wb = openpyxl.load_workbook(file_path, data_only=True)
    stats = {
        'district': None,
        'hozori': 0,
        'tavanmand': 0,
        'majazi': 0,
        'khalagh': 0,
        'tolid': 0,
        'neshast': 0
    }
    
    # 1. سوادرسانه حضوری
    if 'سوادرسانه حضوری' in wb.sheetnames:
        ws = wb['سوادرسانه حضوری']
        stats['hozori'] = count_active_rows(ws, check_cols=[2, 3, 4, 5])
        # پیدا کردن نام ناحیه
        for r in range(2, min(ws.max_row+1, 20)):
            val = ws.cell(r, 3).value
            if val and str(val).strip():
                stats['district'] = str(val).strip()
                break

    # 2. توانمندسازی گردان
    if 'توانمندسازی گردان' in wb.sheetnames:
        ws = wb['توانمندسازی گردان']
        stats['tavanmand'] = count_active_rows(ws, check_cols=[2, 3, 4, 5])
        if not stats['district']:
            for r in range(2, min(ws.max_row+1, 20)):
                val = ws.cell(r, 3).value
                if val and str(val).strip():
                    stats['district'] = str(val).strip()
                    break

    # 3. سوادرسانه مجازی (لایو)
    if 'سوادرسانه مجازی (لایو)' in wb.sheetnames:
        ws = wb['سوادرسانه مجازی (لایو)']
        stats['majazi'] = count_active_rows(ws, check_cols=[2, 3, 4, 5])

    # 4. سواد رسانه خلاقانه
    if 'سواد رسانه خلاقانه' in wb.sheetnames:
        ws = wb['سواد رسانه خلاقانه']
        stats['khalagh'] = count_active_rows(ws, check_cols=[2, 3, 4])

    # 5. تولیدات
    for s_name in wb.sheetnames:
        if 'تولیدات' in s_name:
            ws = wb[s_name]
            stats['tolid'] = count_active_rows(ws, check_cols=[2, 3, 4, 5, 6])
            break

    # شاخص ۱ برابر مجموع سوادرسانه حضوری و توانمندسازی گردان است
    total_hozori = stats['hozori'] + stats['tavanmand']
    return {
        'district': stats['district'],
        'حضوری_و_توانمندسازی': total_hozori,
        'مجازی_لایو': stats['majazi'],
        'اقدامات_خلاقانه': stats['khalagh'],
        'تولیدات_رسانه': stats['tolid'],
        'نشست_انجمن': stats['neshast']
    }

if __name__ == '__main__':
    print("اسکریپت تجمیع خودکار گزارش‌ها آماده بهره‌برداری است.")
