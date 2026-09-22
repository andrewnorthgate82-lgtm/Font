# -*- coding: utf-8 -*-
"""
ایجاد فایل اکسل اختصاصی کارنامه و داشبورد عملکرد ۳ ماهه نواحی نسرا
استان اصفهان - مقیاس نمره‌دهی ۷۰ تا ۱۰۰
"""

import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation

# Load base target data from monthly file
wb_base = openpyxl.load_workbook('تهیه کارنامه نواحی.xlsx')
ws_base_targets = wb_base['پایگاه داده حد انتظار']

wb = openpyxl.Workbook()
# Remove default sheet
wb.remove(wb.active)

# Create 4 sheets
ws_dash = wb.create_sheet('داشبورد مانیتورینگ ۳ ماهه')
ws_card = wb.create_sheet('کارنامه هوشمند ۳ ماهه')
ws_rep = wb.create_sheet('گزارش عملکرد ۳ ماهه')
ws_target = wb.create_sheet('پایگاه داده حد انتظار ۳ ماهه')

# Enable RTL
for ws in [ws_dash, ws_card, ws_rep, ws_target]:
    ws.sheet_view.rightToLeft = True

# Styling definitions
font_title = Font(name='IRANSans', size=14, bold=True, color='FFFFFF')
font_hdr = Font(name='IRANSans', size=11, bold=True, color='FFFFFF')
font_cell = Font(name='IRANSans', size=11)
font_cell_bold = Font(name='IRANSans', size=11, bold=True)
font_lbl = Font(name='IRANSans', size=11, bold=True, color='1F4E79')
font_val = Font(name='IRANSans', size=11, bold=True, color='843B62')

fill_navy = PatternFill(start_color='1F4E79', end_color='1F4E79', fill_type='solid')
fill_blue = PatternFill(start_color='2F5597', end_color='2F5597', fill_type='solid')
fill_gold = PatternFill(start_color='D4AF37', end_color='D4AF37', fill_type='solid')
fill_gray_lbl = PatternFill(start_color='EBF1F5', end_color='EBF1F5', fill_type='solid')
fill_yellow_val = PatternFill(start_color='FFF2CC', end_color='FFF2CC', fill_type='solid')
fill_light = PatternFill(start_color='F2F4F7', end_color='F2F4F7', fill_type='solid')

thin = Side(style='thin', color='BDC3C7')
border_box = Border(top=thin, left=thin, right=thin, bottom=thin)

# 1. Populate Target Database (3-Month Targets = 3 * Monthly Target)
headers_target = [
    'نام ناحیه (شهرستان)', 'تعداد حوزه',
    'حد انتظار ۳ ماهه حضوری (۹۳×)',
    'حد انتظار ۳ ماهه مجازی (۶۵۱×)',
    'حد انتظار ۳ ماهه خلاقانه (۱۸۶×)',
    'حد انتظار ۳ ماهه تولیدات (۹×)',
    'حد انتظار ۳ ماهه نشست (۳ نشست)',
    'حدانتظار اعضای انجمن'
]
for col_idx, h in enumerate(headers_target, 1):
    c = ws_target.cell(row=1, column=col_idx, value=h)
    c.font = font_hdr
    c.fill = fill_navy
    c.alignment = Alignment(horizontal='center', vertical='center')
    c.border = border_box

for r in range(2, 34):
    dname = ws_base_targets.cell(r, 1).value
    branches = ws_base_targets.cell(r, 2).value or 0
    anjoman = ws_base_targets.cell(r, 8).value or 22
    
    # 3x multipliers
    tgt_hozori = branches * 93
    tgt_majazi = branches * 651
    tgt_khalagh = branches * 186
    tgt_tolid = branches * 9
    tgt_neshast = 3
    
    row_vals = [dname, branches, tgt_hozori, tgt_majazi, tgt_khalagh, tgt_tolid, tgt_neshast, anjoman]
    for c_idx, val in enumerate(row_vals, 1):
        cell = ws_target.cell(row=r, column=c_idx, value=val)
        cell.font = font_cell_bold if c_idx == 1 else font_cell
        cell.alignment = Alignment(horizontal='center' if c_idx > 1 else 'right', vertical='center')
        cell.border = border_box

# 2. Populate Reports Sheet (گزارش عملکرد ۳ ماهه)
headers_rep = [
    'نام ناحیه (شهرستان)', 'عملکرد تجمعی حضوری', 'عملکرد تجمعی مجازی',
    'عملکرد تجمعی خلاقانه', 'عملکرد تجمعی تولیدات', 'عملکرد تجمعی نشست',
    'تعداد ماه‌های گزارش‌شده', 'دوره ۳ ماهه'
]
for col_idx, h in enumerate(headers_rep, 1):
    c = ws_rep.cell(row=1, column=col_idx, value=h)
    c.font = font_hdr
    c.fill = fill_blue
    c.alignment = Alignment(horizontal='center', vertical='center')
    c.border = border_box

for r in range(2, 34):
    dname = ws_base_targets.cell(r, 1).value
    ws_rep.cell(row=r, column=1, value=dname).font = font_cell_bold
    ws_rep.cell(row=r, column=1).border = border_box
    for c_idx in range(2, 7):
        cell = ws_rep.cell(row=r, column=c_idx, value=0)
        cell.font = font_cell
        cell.alignment = Alignment(horizontal='center', vertical='center')
        cell.border = border_box
    cell_m = ws_rep.cell(row=r, column=7, value=0)
    cell_m.alignment = Alignment(horizontal='center', vertical='center')
    cell_m.border = border_box
    cell_d = ws_rep.cell(row=r, column=8, value="='کارنامه هوشمند ۳ ماهه'!C3")
    cell_d.alignment = Alignment(horizontal='center', vertical='center')
    cell_d.border = border_box

# 3. Populate Smart Scorecard Sheet (کارنامه هوشمند ۳ ماهه)
ws_card.cell(row=1, column=2, value="کارنامه هوشمند پایش عملکرد ۳ ماهه (فصلی) ناحیه").font = Font(name='IRANSans', size=13, bold=True, color='1F4E79')

# Row 2: District Selection
ws_card.cell(row=2, column=2, value='انتخاب شهرستان:').font = font_lbl
ws_card.cell(row=2, column=2).fill = fill_gray_lbl
ws_card.cell(row=2, column=2).alignment = Alignment(horizontal='center', vertical='center')
ws_card.cell(row=2, column=2).border = border_box

ws_card.cell(row=2, column=3, value='مبارکه').font = font_val
ws_card.cell(row=2, column=3).fill = fill_yellow_val
ws_card.cell(row=2, column=3).alignment = Alignment(horizontal='center', vertical='center')
ws_card.cell(row=2, column=3).border = border_box

# District validation
dist_str = '"' + ','.join([ws_base_targets.cell(r, 1).value for r in range(2, 34)]) + '"'
dv_d = DataValidation(type='list', formula1=dist_str, allow_blank=False)
ws_card.add_data_validation(dv_d)
dv_d.add(ws_card['C3']) # add to C2
dv_d.add(ws_card['C2'])

# Row 3: Period Selection (فصل / دوره)
ws_card.cell(row=3, column=2, value='دوره ۳ ماهه:').font = font_lbl
ws_card.cell(row=3, column=2).fill = fill_gray_lbl
ws_card.cell(row=3, column=2).alignment = Alignment(horizontal='center', vertical='center')
ws_card.cell(row=3, column=2).border = border_box

ws_card.cell(row=3, column=3, value='بهار (فروردین تا خرداد)').font = font_val
ws_card.cell(row=3, column=3).fill = fill_yellow_val
ws_card.cell(row=3, column=3).alignment = Alignment(horizontal='center', vertical='center')
ws_card.cell(row=3, column=3).border = border_box

# Quarter validation
quarters_str = '"بهار (فروردین تا خرداد),تابستان (تیر تا شهریور),پاییز (مهر تا آذر),زمستان (دی تا اسفند)"'
dv_q = DataValidation(type='list', formula1=quarters_str, allow_blank=False)
ws_card.add_data_validation(dv_q)
dv_q.add(ws_card['C3'])

ws_card.cell(row=3, column=4, value='سال ارزیابی:').font = font_lbl
ws_card.cell(row=3, column=4).fill = fill_gray_lbl
ws_card.cell(row=3, column=4).alignment = Alignment(horizontal='center', vertical='center')
ws_card.cell(row=3, column=4).border = border_box

ws_card.cell(row=3, column=5, value='۱۴۰۵').font = font_val
ws_card.cell(row=3, column=5).fill = fill_yellow_val
ws_card.cell(row=3, column=5).alignment = Alignment(horizontal='center', vertical='center')
ws_card.cell(row=3, column=5).border = border_box

# Table Headers
card_headers = ['بخش و شاخص عملکردی', 'حد انتظار ۳ ماهه', 'عملکرد واقعی ۳ ماهه', 'درصد تحقق ۳ ماهه']
for c_idx, h in enumerate(card_headers, 2):
    cell = ws_card.cell(row=4, column=c_idx, value=h)
    cell.font = font_hdr
    cell.fill = fill_navy
    cell.alignment = Alignment(horizontal='center', vertical='center')
    cell.border = border_box

indicators_card = [
    (5, 'حضوری و توانمندسازی (ضریب ۳۱×۳ = ۹۳)', 3, 2),
    (6, 'مجازی و لایو (ضریب ۲۱۷×۳ = ۶۵۱)', 4, 3),
    (7, 'اقدامات خلاقانه (ضریب ۶۲×۳ = ۱۸۶)', 5, 4),
    (8, 'تولیدات رسانه‌ای (ضریب ۳×۳ = ۹)', 6, 5),
    (9, 'نشست انجمن مدرسان (۳ نشست)', 7, 6)
]

for row_i, name, col_tgt, col_act in indicators_card:
    # Col B: Name
    c_b = ws_card.cell(row=row_i, column=2, value=name)
    c_b.font = font_cell_bold
    c_b.border = border_box
    
    # Col C: Target 3m
    c_c = ws_card.cell(row=row_i, column=3, value=f"=IFERROR(VLOOKUP($C$2, 'پایگاه داده حد انتظار ۳ ماهه'!$A$2:$H$33, {col_tgt}, FALSE), 0)")
    c_c.font = font_cell
    c_c.alignment = Alignment(horizontal='center', vertical='center')
    c_c.border = border_box
    
    # Col D: Actual 3m
    c_d = ws_card.cell(row=row_i, column=4, value=f"=IFERROR(VLOOKUP($C$2, 'گزارش عملکرد ۳ ماهه'!$A$2:$F$33, {col_act}, FALSE), 0)")
    c_d.font = font_cell_bold
    c_d.alignment = Alignment(horizontal='center', vertical='center')
    c_d.border = border_box
    
    # Col E: Percentage
    c_e = ws_card.cell(row=row_i, column=5, value=f"=IFERROR(D{row_i}/C{row_i}, 0)")
    c_e.font = font_cell_bold
    c_e.number_format = '0.0%'
    c_e.alignment = Alignment(horizontal='center', vertical='center')
    c_e.border = border_box

# Performance Summary Box
ws_card.cell(row=11, column=2, value='درصد میانگین تحقق:').font = font_lbl
ws_card.cell(row=11, column=3, value='=AVERAGE(E5:E9)').font = font_val
ws_card.cell(row=11, column=3).number_format = '0.0%'

# 70 to 100 Grading Scale Formula!
# 70 is zero performance, 100 is 100% realization!
# Formula: 70 + 30 * MIN(1, AVERAGE(E5:E9))
ws_card.cell(row=12, column=2, value='نمره نهایی کارنامه (مقیاس ۷۰ تا ۱۰۰):').font = Font(name='IRANSans', size=11, bold=True, color='C0392B')
ws_card.cell(row=12, column=3, value='=ROUND(70 + 30 * MIN(1, MAX(0, C11)), 1)').font = Font(name='IRANSans', size=13, bold=True, color='C0392B')
ws_card.cell(row=12, column=3).number_format = '0.0'

ws_card.cell(row=13, column=2, value='رتبه در استان:').font = font_lbl
ws_card.cell(row=13, column=3, value='=IFERROR(VLOOKUP($C$2, \'داشبورد مانیتورینگ ۳ ماهه\'!$B$31:$U$62, 20, FALSE), "-")').font = font_val

ws_card.cell(row=14, column=2, value='سطح ارزیابی عملکرد ۳ ماهه:').font = font_lbl
ws_card.cell(row=14, column=3, value='=IF(C12>=100, "عالی (پیشتاز)", IF(C12>=92.5, "خوب", IF(C12>=85, "متوسط", IF(C12>70, "ضعیف", "فاقد عملکرد"))))').font = font_val

ws_card.cell(row=15, column=2, value='تعداد حوزه مقاومت:').font = font_lbl
ws_card.cell(row=15, column=3, value='=IFERROR(VLOOKUP($C$2, \'پایگاه داده حد انتظار ۳ ماهه\'!$A$2:$B$33, 2, FALSE), 0)').font = font_cell

ws_card.cell(row=16, column=2, value='تعداد ماه‌های ثبت‌شده:').font = font_lbl
ws_card.cell(row=16, column=3, value='=IFERROR(VLOOKUP($C$2, \'گزارش عملکرد ۳ ماهه\'!$A$2:$G$33, 7, FALSE), 0)').font = font_cell

for r in range(11, 17):
    ws_card.cell(row=r, column=2).fill = fill_gray_lbl
    ws_card.cell(row=r, column=2).border = border_box
    ws_card.cell(row=r, column=3).border = border_box

# 4. Populate 3-Month Monitoring Dashboard
ws_dash.cell(row=1, column=1, value='سامانه هوشمند مانیتورینگ جامع عملکرد ۳ ماهه (فصلی) نواحی نسرا - استان اصفهان').font = font_title
ws_dash.merge_cells('A1:U1')
ws_dash['A1'].fill = fill_navy
ws_dash['A1'].alignment = Alignment(horizontal='center', vertical='center')

ws_dash.cell(row=2, column=1, value='=\"سال ارزیابی: \" & \'کارنامه هوشمند ۳ ماهه\'!E3 & \" | دوره: \" & \'کارنامه هوشمند ۳ ماهه\'!C3 & \" | مقیاس نمره‌دهی رسمی: ۷۰ (حداقل) تا ۱۰۰ (عالی)\"').font = Font(name='IRANSans', size=10, bold=True, color='1F4E79')

# Dashboard Table of 32 Districts
dash_headers = [
    'ردیف', 'نام ناحیه (شهرستان)', 'تعداد حوزه',
    'حد انتظار حضوری', 'عملکرد حضوری', '% تحقق حضوری',
    'حد انتظار مجازی', 'عملکرد مجازی', '% تحقق مجازی',
    'حد انتظار خلاقانه', 'عملکرد خلاقانه', '% تحقق خلاقانه',
    'حد انتظار تولیدات', 'عملکرد تولیدات', '% تحقق تولیدات',
    'حد انتظار نشست', 'عملکرد نشست', '% تحقق نشست',
    'میانگین درصد تحقق', 'نمره کارنامه (۷۰-۱۰۰)', 'رتبه استانی'
]

for c_i, h in enumerate(dash_headers, 1):
    c = ws_dash.cell(row=30, column=c_i, value=h)
    c.font = font_hdr
    c.fill = fill_navy
    c.alignment = Alignment(horizontal='center', vertical='center')
    c.border = border_box

for idx in range(1, 33):
    r = 30 + idx
    # A: Row number
    ws_dash.cell(row=r, column=1, value=idx).alignment = Alignment(horizontal='center', vertical='center')
    ws_dash.cell(row=r, column=1).border = border_box
    
    # B: District Name
    ws_dash.cell(row=r, column=2, value=f"='پایگاه داده حد انتظار ۳ ماهه'!A{idx+1}").font = font_cell_bold
    ws_dash.cell(row=r, column=2).border = border_box
    
    # C: Branches
    ws_dash.cell(row=r, column=3, value=f"='پایگاه داده حد انتظار ۳ ماهه'!B{idx+1}").alignment = Alignment(horizontal='center', vertical='center')
    ws_dash.cell(row=r, column=3).border = border_box
    
    # D-F: Hozori
    ws_dash.cell(row=r, column=4, value=f"='پایگاه داده حد انتظار ۳ ماهه'!C{idx+1}").border = border_box
    ws_dash.cell(row=r, column=5, value=f"='گزارش عملکرد ۳ ماهه'!B{idx+1}").border = border_box
    ws_dash.cell(row=r, column=6, value=f"=IFERROR(E{r}/D{r}, 0)").number_format = '0.0%'
    ws_dash.cell(row=r, column=6).border = border_box
    
    # G-I: Majazi
    ws_dash.cell(row=r, column=7, value=f"='پایگاه داده حد انتظار ۳ ماهه'!D{idx+1}").border = border_box
    ws_dash.cell(row=r, column=8, value=f"='گزارش عملکرد ۳ ماهه'!C{idx+1}").border = border_box
    ws_dash.cell(row=r, column=9, value=f"=IFERROR(H{r}/G{r}, 0)").number_format = '0.0%'
    ws_dash.cell(row=r, column=9).border = border_box
    
    # J-L: Khalagh
    ws_dash.cell(row=r, column=10, value=f"='پایگاه داده حد انتظار ۳ ماهه'!E{idx+1}").border = border_box
    ws_dash.cell(row=r, column=11, value=f"='گزارش عملکرد ۳ ماهه'!D{idx+1}").border = border_box
    ws_dash.cell(row=r, column=12, value=f"=IFERROR(K{r}/J{r}, 0)").number_format = '0.0%'
    ws_dash.cell(row=r, column=12).border = border_box
    
    # M-O: Tolid
    ws_dash.cell(row=r, column=13, value=f"='پایگاه داده حد انتظار ۳ ماهه'!F{idx+1}").border = border_box
    ws_dash.cell(row=r, column=14, value=f"='گزارش عملکرد ۳ ماهه'!E{idx+1}").border = border_box
    ws_dash.cell(row=r, column=15, value=f"=IFERROR(N{r}/M{r}, 0)").number_format = '0.0%'
    ws_dash.cell(row=r, column=15).border = border_box
    
    # P-R: Neshast
    ws_dash.cell(row=r, column=16, value=f"='پایگاه داده حد انتظار ۳ ماهه'!G{idx+1}").border = border_box
    ws_dash.cell(row=r, column=17, value=f"='گزارش عملکرد ۳ ماهه'!F{idx+1}").border = border_box
    ws_dash.cell(row=r, column=18, value=f"=IFERROR(Q{r}/P{r}, 0)").number_format = '0.0%'
    ws_dash.cell(row=r, column=18).border = border_box
    
    # S: Average Realization %
    ws_dash.cell(row=r, column=19, value=f"=AVERAGE(F{r},I{r},L{r},O{r},R{r})").number_format = '0.0%'
    ws_dash.cell(row=r, column=19).font = font_cell_bold
    ws_dash.cell(row=r, column=19).border = border_box
    
    # T: 70-100 Score!
    ws_dash.cell(row=r, column=20, value=f"=ROUND(70 + 30 * MIN(1, MAX(0, S{r})), 1)").font = Font(name='IRANSans', size=11, bold=True, color='C0392B')
    ws_dash.cell(row=r, column=20).number_format = '0.0'
    ws_dash.cell(row=r, column=20).border = border_box
    
    # U: Provincial Rank (based on 70-100 score)
    ws_dash.cell(row=r, column=21, value=f"=RANK(T{r}, $T$31:$T$62)").alignment = Alignment(horizontal='center', vertical='center')
    ws_dash.cell(row=r, column=21).font = font_cell_bold
    ws_dash.cell(row=r, column=21).border = border_box

# Adjust column widths
for ws in [ws_dash, ws_card, ws_rep, ws_target]:
    for col in ws.columns:
        max_len = max(len(str(cell.value or '')) for cell in col)
        col_letter = openpyxl.utils.get_column_letter(col[0].column)
        ws.column_dimensions[col_letter].width = max(max_len + 3, 12)

out_file = 'تهیه کارنامه ۳ ماهه نواحی.xlsx'
wb.save(out_file)
print(f'Successfully created {out_file}!')
