# -*- coding: utf-8 -*-
import os
import arabic_reshaper
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFont

FONT_PATH = "/home/user/Font/IRANSans.ttf"

def fa(text):
    if text is None:
        return ""
    reshaped = arabic_reshaper.reshape(str(text))
    return get_display(reshaped)

def generate_scorecard_png(district_name, target_dict, actual_dict, rank="-", tier="عالی", output_path="scorecard.png"):
    width, height = 850, 750
    img = Image.new('RGB', (width, height), color=(248, 249, 250))
    draw = ImageDraw.Draw(img)
    
    font_title = ImageFont.truetype(FONT_PATH, 22)
    font_sub = ImageFont.truetype(FONT_PATH, 13)
    font_hdr = ImageFont.truetype(FONT_PATH, 14)
    font_cell = ImageFont.truetype(FONT_PATH, 13)
    font_cell_bold = ImageFont.truetype(FONT_PATH, 13)
    font_score = ImageFont.truetype(FONT_PATH, 28)
    font_score_lbl = ImageFont.truetype(FONT_PATH, 14)

    # 1. Header Banner
    draw.rectangle([0, 0, width, 95], fill=(27, 54, 93))
    draw.rectangle([0, 95, width, 100], fill=(41, 128, 185))
    
    draw.text((width//2, 35), fa("کارنامه هوشمند و ارزیابی عملکرد ماهانه ناحیه"), fill=(255, 255, 255), font=font_title, anchor="mm")
    sub_text = f"شهرستان: {district_name}  |  سال ارزیابی: ۱۴۰۵  |  نهضت سواد رسانه‌ای انقلاب اسلامی (نسرا) اصفهان"
    draw.text((width//2, 70), fa(sub_text), fill=(236, 240, 241), font=font_sub, anchor="mm")

    # 2. Score & Rank Badges
    score_val = actual_dict.get('overall_score', 0)
    score_str = f"{score_val:.1f}%" if isinstance(score_val, (int, float)) else str(score_val)
    
    # Card 1: Score
    draw.rounded_rectangle([50, 115, 290, 205], radius=8, fill=(235, 245, 251), outline=(41, 128, 185), width=2)
    draw.text((170, 135), fa("نمره کل عملکرد"), fill=(41, 128, 185), font=font_score_lbl, anchor="mm")
    draw.text((170, 170), fa(score_str), fill=(27, 54, 93), font=font_score, anchor="mm")

    # Card 2: Rank
    draw.rounded_rectangle([310, 115, 550, 205], radius=8, fill=(232, 248, 245), outline=(39, 174, 96), width=2)
    draw.text((430, 135), fa("رتبه در استان اصفهان"), fill=(39, 174, 96), font=font_score_lbl, anchor="mm")
    draw.text((430, 170), fa(f"رتبه {rank}"), fill=(14, 98, 81), font=font_score, anchor="mm")

    # Card 3: Tier
    draw.rounded_rectangle([570, 115, 800, 205], radius=8, fill=(254, 249, 231), outline=(241, 196, 15), width=2)
    draw.text((685, 135), fa("سطح کیفی ارزیابی"), fill=(183, 149, 11), font=font_score_lbl, anchor="mm")
    draw.text((685, 170), fa(tier), fill=(125, 102, 8), font=ImageFont.truetype(FONT_PATH, 18), anchor="mm")

    # 3. Table of Indicators
    tbl_top = 225
    tbl_left = 50
    tbl_right = 800
    row_h = 48
    
    draw.rectangle([tbl_left, tbl_top, tbl_right, tbl_top + row_h], fill=(59, 95, 144))
    
    cols = [
        (tbl_left, tbl_left + 70, fa("ردیف")),
        (tbl_left + 70, tbl_left + 350, fa("بخش و شاخص عملکردی")),
        (tbl_left + 350, tbl_left + 490, fa("حد انتظار")),
        (tbl_left + 490, tbl_left + 630, fa("عملکرد واقعی")),
        (tbl_left + 630, tbl_right, fa("درصد تحقق"))
    ]
    for c_start, c_end, c_lbl in cols:
        draw.text(((c_start + c_end)//2, tbl_top + row_h//2), c_lbl, fill=(255, 255, 255), font=font_hdr, anchor="mm")
        draw.line([c_end, tbl_top, c_end, tbl_top + row_h], fill=(255, 255, 255), width=1)

    indicators = [
        (1, "حضوری و توانمندسازی (۳۱×)", target_dict.get('hozori', 0), actual_dict.get('hozori', 0)),
        (2, "مجازی و لایو (۲۱۷×)", target_dict.get('majazi', 0), actual_dict.get('majazi', 0)),
        (3, "اقدامات خلاقانه (۶۲×)", target_dict.get('khalagh', 0), actual_dict.get('khalagh', 0)),
        (4, "تولیدات رسانه‌ای (۳×)", target_dict.get('tolid', 0), actual_dict.get('tolid', 0)),
        (5, "نشست با انجمن مدرسان", target_dict.get('neshast', 1), actual_dict.get('neshast', 0))
    ]

    curr_y = tbl_top + row_h
    for idx, name, tgt, act in indicators:
        pct = (act / tgt * 100) if tgt > 0 else 0
        bg_color = (255, 255, 255) if idx % 2 == 1 else (244, 246, 249)
        draw.rectangle([tbl_left, curr_y, tbl_right, curr_y + row_h], fill=bg_color)
        
        draw.text((tbl_left + 35, curr_y + row_h//2), fa(str(idx)), fill=(100, 100, 100), font=font_cell, anchor="mm")
        draw.text((tbl_left + 85, curr_y + row_h//2), fa(name), fill=(27, 54, 93), font=font_cell_bold, anchor="lm")
        draw.text((tbl_left + 420, curr_y + row_h//2), fa(f"{tgt:,}"), fill=(50, 50, 50), font=font_cell, anchor="mm")
        draw.text((tbl_left + 560, curr_y + row_h//2), fa(f"{act:,}"), fill=(20, 20, 20), font=font_cell_bold, anchor="mm")
        
        pct_color = (21, 87, 36) if pct >= 100 else ((12, 84, 96) if pct >= 75 else ((133, 100, 4) if pct >= 50 else (114, 28, 36)))
        pct_bg = (212, 237, 218) if pct >= 100 else ((209, 236, 241) if pct >= 75 else ((255, 243, 205) if pct >= 50 else (248, 215, 218)))
        
        draw.rounded_rectangle([tbl_left + 645, curr_y + 8, tbl_right - 15, curr_y + row_h - 8], radius=6, fill=pct_bg)
        draw.text(((tbl_left + 645 + tbl_right - 15)//2, curr_y + row_h//2), fa(f"{pct:.1f}%"), fill=pct_color, font=font_cell_bold, anchor="mm")
        
        draw.line([tbl_left, curr_y + row_h, tbl_right, curr_y + row_h], fill=(220, 224, 230), width=1)
        for c_start, c_end, _ in cols:
            draw.line([c_end, curr_y, c_end, curr_y + row_h], fill=(220, 224, 230), width=1)
            
        curr_y += row_h

    draw.rectangle([tbl_left, tbl_top, tbl_right, curr_y], outline=(59, 95, 144), width=2)

    # 4. Footer
    footer_y = curr_y + 20
    draw.rectangle([tbl_left, footer_y, tbl_right, footer_y + 110], fill=(255, 255, 255), outline=(220, 224, 230), width=1)
    
    info_txt1 = f"• تعداد حوزه‌های مقاومت: {target_dict.get('branches', 0)} حوزه"
    info_txt2 = "• حدانتظار اعضای انجمن مدرسان: ۲۲ نفر"
    info_txt3 = "• ملاک محاسبه شاخص‌ها: مجموع تعداد نفرات شرکت‌کننده و مخاطبان آموزش‌دیده"
    
    draw.text((tbl_right - 20, footer_y + 25), fa(info_txt1), fill=(60, 60, 60), font=font_sub, anchor="rm")
    draw.text((tbl_right - 20, footer_y + 55), fa(info_txt2), fill=(60, 60, 60), font=font_sub, anchor="rm")
    draw.text((tbl_right - 20, footer_y + 85), fa(info_txt3), fill=(100, 100, 100), font=font_sub, anchor="rm")

    draw.rectangle([tbl_left + 30, footer_y + 15, tbl_left + 220, footer_y + 95], outline=(41, 128, 185), width=1)
    draw.text((tbl_left + 125, footer_y + 35), fa("دبیرخانه نهضت سواد رسانه‌ای"), fill=(27, 54, 93), font=font_sub, anchor="mm")
    draw.text((tbl_left + 125, footer_y + 65), fa("استان اصفهان - مورد تأیید"), fill=(39, 174, 96), font=font_sub, anchor="mm")

    if isinstance(output_path, str):
        img.save(output_path, "PNG", dpi=(150, 150))
    else:
        img.save(output_path, "PNG", dpi=(150, 150))
    return img

print("image_generator.py ready")

def generate_dashboard_png(macro_data, top5_data, bottom5_data, kpi_data, output_path="dashboard.png"):
    width, height = 1000, 850
    img = Image.new('RGB', (width, height), color=(248, 249, 250))
    draw = ImageDraw.Draw(img)
    
    font_title = ImageFont.truetype(FONT_PATH, 20)
    font_sub = ImageFont.truetype(FONT_PATH, 12)
    font_hdr = ImageFont.truetype(FONT_PATH, 13)
    font_cell = ImageFont.truetype(FONT_PATH, 11.5)
    font_cell_bold = ImageFont.truetype(FONT_PATH, 11.5)
    font_score = ImageFont.truetype(FONT_PATH, 20)
    font_lbl = ImageFont.truetype(FONT_PATH, 12)

    # 1. Header Banner
    draw.rectangle([0, 0, width, 85], fill=(27, 54, 93))
    draw.rectangle([0, 85, width, 90], fill=(41, 128, 185))
    draw.text((width//2, 30), fa("داشبورد مدیریتی مانیتورینگ و ارزیابی عملکرد نواحی نسرا"), fill=(255, 255, 255), font=font_title, anchor="mm")
    sub = "استان اصفهان  |  سال ارزیابی: ۱۴۰۵  |  پایش جامع ۳۲ شهرستان و ۲۲۹ حوزه مقاومت"
    draw.text((width//2, 60), fa(sub), fill=(236, 240, 241), font=font_sub, anchor="mm")

    # 2. KPI Cards (4 cards)
    c_w = 210
    card_y = 105
    card_h = 75
    
    # Card 1: Coverage
    draw.rounded_rectangle([40, card_y, 40 + c_w, card_y + card_h], radius=6, fill=(242, 244, 247), outline=(27, 54, 93), width=1)
    draw.text((40 + c_w//2, card_y + 20), fa("پوشش تشکیلاتی استان"), fill=(44, 62, 80), font=font_lbl, anchor="mm")
    draw.text((40 + c_w//2, card_y + 48), fa("۳۲ ناحیه  |  ۲۲۹ حوزه"), fill=(27, 54, 93), font=font_score, anchor="mm")

    # Card 2: Average Score
    draw.rounded_rectangle([270, card_y, 270 + c_w, card_y + card_h], radius=6, fill=(235, 245, 251), outline=(41, 128, 185), width=1)
    draw.text((270 + c_w//2, card_y + 20), fa("میانگین نمره عملکرد استان"), fill=(41, 128, 185), font=font_lbl, anchor="mm")
    draw.text((270 + c_w//2, card_y + 48), fa(f"{kpi_data.get('avg_score', 0):.1f}%"), fill=(27, 54, 93), font=font_score, anchor="mm")

    # Card 3: Top Performer
    draw.rounded_rectangle([500, card_y, 500 + c_w, card_y + card_h], radius=6, fill=(232, 248, 245), outline=(39, 174, 96), width=1)
    draw.text((500 + c_w//2, card_y + 20), fa("پیشتاز استان (رتبه ۱)"), fill=(39, 174, 96), font=font_lbl, anchor="mm")
    draw.text((500 + c_w//2, card_y + 48), fa(kpi_data.get('top_district', 'کاشان')), fill=(14, 98, 81), font=font_score, anchor="mm")

    # Card 4: Reports Status
    draw.rounded_rectangle([730, card_y, 730 + c_w, card_y + card_h], radius=6, fill=(254, 249, 231), outline=(241, 196, 15), width=1)
    draw.text((730 + c_w//2, card_y + 20), fa("وضعیت ثبت گزارش‌ها"), fill=(183, 149, 11), font=font_lbl, anchor="mm")
    draw.text((730 + c_w//2, card_y + 48), fa(f"{kpi_data.get('reported_count', 32)} از ۳۲ ناحیه"), fill=(125, 102, 8), font=font_score, anchor="mm")

    # 3. Macro Indicators Table
    tbl_y = 200
    t_left = 40
    t_right = 960
    r_h = 36
    
    draw.rectangle([t_left, tbl_y, t_right, tbl_y + r_h], fill=(31, 78, 121))
    draw.text((t_left + 15, tbl_y + r_h//2), fa("۱. جدول وضعیت تحقق شاخص‌های کلان در سطح کل استان اصفهان"), fill=(255, 255, 255), font=font_hdr, anchor="lm")
    
    # Sub-header
    sub_y = tbl_y + r_h
    draw.rectangle([t_left, sub_y, t_right, sub_y + r_h], fill=(59, 95, 144))
    
    m_cols = [
        (t_left, t_left + 50, fa("ردیف")),
        (t_left + 50, t_left + 350, fa("شاخص عملکردی")),
        (t_left + 350, t_left + 500, fa("حد انتظار کل استان")),
        (t_left + 500, t_left + 650, fa("عملکرد واقعی کل استان")),
        (t_left + 650, t_left + 800, fa("انحراف از هدف")),
        (t_left + 800, t_right, fa("درصد تحقق استانی"))
    ]
    for cs, ce, clbl in m_cols:
        draw.text(((cs+ce)//2, sub_y + r_h//2), clbl, fill=(255, 255, 255), font=font_cell_bold, anchor="mm")
        draw.line([ce, sub_y, ce, sub_y + r_h], fill=(255, 255, 255), width=1)

    curr_row_y = sub_y + r_h
    for idx, (m_name, m_tgt, m_act) in enumerate(macro_data, start=1):
        diff = m_act - m_tgt
        pct = (m_act / m_tgt * 100) if m_tgt > 0 else 0
        diff_str = f"+{diff:,}" if diff > 0 else f"{diff:,}"
        
        bg_col = (255, 255, 255) if idx % 2 == 1 else (244, 246, 249)
        draw.rectangle([t_left, curr_row_y, t_right, curr_row_y + r_h], fill=bg_col)
        
        draw.text((t_left + 25, curr_row_y + r_h//2), fa(str(idx)), fill=(80, 80, 80), font=font_cell, anchor="mm")
        draw.text((t_left + 65, curr_row_y + r_h//2), fa(m_name), fill=(27, 54, 93), font=font_cell_bold, anchor="lm")
        draw.text((t_left + 425, curr_row_y + r_h//2), fa(f"{m_tgt:,}"), fill=(60, 60, 60), font=font_cell, anchor="mm")
        draw.text((t_left + 575, curr_row_y + r_h//2), fa(f"{m_act:,}"), fill=(20, 20, 20), font=font_cell_bold, anchor="mm")
        
        diff_color = (21, 87, 36) if diff >= 0 else (114, 28, 36)
        draw.text((t_left + 725, curr_row_y + r_h//2), fa(diff_str), fill=diff_color, font=font_cell, anchor="mm")
        
        pct_color = (21, 87, 36) if pct >= 100 else ((12, 84, 96) if pct >= 75 else ((133, 100, 4) if pct >= 50 else (114, 28, 36)))
        draw.text((t_left + 880, curr_row_y + r_h//2), fa(f"{pct:.1f}%"), fill=pct_color, font=font_cell_bold, anchor="mm")
        
        draw.line([t_left, curr_row_y + r_h, t_right, curr_row_y + r_h], fill=(220, 224, 230), width=1)
        for cs, ce, _ in m_cols:
            draw.line([ce, curr_row_y, ce, curr_row_y + r_h], fill=(220, 224, 230), width=1)
            
        curr_row_y += r_h

    # 4. Top 5 & Bottom 5 Leaderboard
    lead_y = curr_row_y + 20
    col_half_w = 445
    
    # Left Box: Top 5
    draw.rectangle([t_left, lead_y, t_left + col_half_w, lead_y + 32], fill=(17, 120, 100))
    draw.text((t_left + col_half_w//2, lead_y + 16), fa("🏆 ۵ ناحیه برتر و پیشتاز استان اصفهان"), fill=(255, 255, 255), font=font_hdr, anchor="mm")
    
    # Right Box: Bottom 5
    draw.rectangle([t_right - col_half_w, lead_y, t_right, lead_y + 32], fill=(192, 57, 43))
    draw.text((t_right - col_half_w//2, lead_y + 16), fa("⚠️ ۵ ناحیه نیازمند پیگیری و تقویت"), fill=(255, 255, 255), font=font_hdr, anchor="mm")
    
    curr_l_y = lead_y + 32
    for k in range(5):
        t_row = top5_data[k] if k < len(top5_data) else (k+1, "در انتظار", 0)
        b_row = bottom5_data[k] if k < len(bottom5_data) else (k+1, "در انتظار", 0)
        
        # Left (Top 5)
        draw.rectangle([t_left, curr_l_y, t_left + col_half_w, curr_l_y + 28], fill=(255, 255, 255) if k%2==0 else (244, 246, 249))
        draw.text((t_left + 25, curr_l_y + 14), fa(f"#{t_row[0]}"), fill=(17, 120, 100), font=font_cell_bold, anchor="mm")
        draw.text((t_left + 60, curr_l_y + 14), fa(t_row[1]), fill=(27, 54, 93), font=font_cell_bold, anchor="lm")
        draw.text((t_left + col_half_w - 30, curr_l_y + 14), fa(f"{t_row[2]:.1f}%"), fill=(21, 87, 36), font=font_cell_bold, anchor="rm")
        draw.line([t_left, curr_l_y + 28, t_left + col_half_w, curr_l_y + 28], fill=(220, 224, 230), width=1)
        
        # Right (Bottom 5)
        draw.rectangle([t_right - col_half_w, curr_l_y, t_right, curr_l_y + 28], fill=(255, 255, 255) if k%2==0 else (244, 246, 249))
        draw.text((t_right - col_half_w + 25, curr_l_y + 14), fa(f"#{b_row[0]}"), fill=(192, 57, 43), font=font_cell_bold, anchor="mm")
        draw.text((t_right - col_half_w + 60, curr_l_y + 14), fa(b_row[1]), fill=(27, 54, 93), font=font_cell_bold, anchor="lm")
        draw.text((t_right - 30, curr_l_y + 14), fa(f"{b_row[2]:.1f}%"), fill=(192, 57, 43), font=font_cell_bold, anchor="rm")
        draw.line([t_right - col_half_w, curr_l_y + 28, t_right, curr_l_y + 28], fill=(220, 224, 230), width=1)
        
        curr_l_y += 28

    # 5. Bottom note
    f_y = curr_l_y + 20
    draw.rectangle([t_left, f_y, t_right, f_y + 60], fill=(255, 255, 255), outline=(220, 224, 230), width=1)
    note_txt = "گزارش رسمی دبیرخانه نسرا استان اصفهان  |  ملاک محاسبه: مجموع نفرات شرکت‌کننده  |  جهت ارائه به مسئولین استانی"
    draw.text((width//2, f_y + 30), fa(note_txt), fill=(100, 100, 100), font=font_sub, anchor="mm")

    if isinstance(output_path, str):
        img.save(output_path, "PNG", dpi=(150, 150))
    else:
        img.save(output_path, "PNG", dpi=(150, 150))
    return img
