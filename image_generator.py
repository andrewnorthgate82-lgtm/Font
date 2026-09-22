# -*- coding: utf-8 -*-
"""
========================================================================
ماژول تولید تصاویر بهینه‌شده ویژه تلفن همراه (1080 × 1920 ایستاده - عمودی)
کارنامه هوشمند و داشبورد مانیتورینگ عملکرد نواحی نسرا - استان اصفهان
پشتیبانی کامل از انتخاب پویای ماه و سال ارزیابی (تیر، مرداد، شهریور و...)
طراحی مدرن، بدون تداخل و توهم‌رفتگی متن، فونت استاندارد IRANSans
========================================================================
"""

import os
import sys

def get_font_path():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    candidates = [
        os.path.join(script_dir, "IRANSans.ttf"),
        os.path.join(os.getcwd(), "IRANSans.ttf"),
        os.path.join(script_dir, "IRANSansBold.ttf"),
        "IRANSans.ttf",
        "/home/user/Font/IRANSans.ttf",
        "C:\\Windows\\Fonts\\tahoma.ttf",
        "C:\\Windows\\Fonts\\arial.ttf",
        "C:\\Windows\\Fonts\\segoeui.ttf",
    ]
    for p in candidates:
        if os.path.exists(p):
            return p
    return None

FONT_PATH = get_font_path()

def fa(text):
    if text is None:
        return ""
    s = str(text)
    # Remove any emoji characters that could cause missing-glyph tofu boxes
    cleaned = []
    for ch in s:
        cp = ord(ch)
        if (0x1F600 <= cp <= 0x1F64F or 0x1F300 <= cp <= 0x1F5FF or
            0x1F680 <= cp <= 0x1F6FF or 0x1F700 <= cp <= 0x1F7FF or
            0x2600 <= cp <= 0x26FF or 0x2700 <= cp <= 0x27BF):
            continue
        cleaned.append(ch)
    s = "".join(cleaned)
    try:
        import arabic_reshaper
        from bidi.algorithm import get_display
        reshaped = arabic_reshaper.reshape(s)
        return get_display(reshaped)
    except Exception:
        return s

def load_font(size):
    from PIL import ImageFont
    if FONT_PATH and os.path.exists(FONT_PATH):
        try:
            return ImageFont.truetype(FONT_PATH, int(size))
        except Exception:
            pass
    for alt in ["arial.ttf", "tahoma.ttf", "DejaVuSans.ttf"]:
        try:
            return ImageFont.truetype(alt, int(size))
        except Exception:
            continue
    return ImageFont.load_default()

def draw_progressbar(draw, x, y, w, h, pct, fill_color, bg_color=(235, 238, 242), radius=7):
    draw.rounded_rectangle([x, y, x + w, y + h], radius=radius, fill=bg_color)
    clamped_pct = max(0.0, min(100.0, pct))
    fill_w = int(w * (clamped_pct / 100.0))
    if fill_w > 4:
        draw.rounded_rectangle([x, y, x + fill_w, y + h], radius=radius, fill=fill_color)

def generate_scorecard_png(district_name, target_dict, actual_dict, rank="۱", tier="عالی", month="شهریور", year="۱۴۰۵", output_path="scorecard.png"):
    from PIL import Image, ImageDraw
    
    # 1080 x 1920 Mobile Portrait (Vertical)
    width, height = 1080, 1920
    img = Image.new('RGB', (width, height), color=(245, 247, 250))
    draw = ImageDraw.Draw(img)
    
    # 1. Header Banner (Y: 0 to 225)
    draw.rectangle([0, 0, width, 225], fill=(20, 38, 68))
    draw.rectangle([0, 225, width, 233], fill=(37, 99, 235))
    
    draw.text((width//2, 45), fa("نهضت سواد رسانه‌ای انقلاب اسلامی (نسرا) - استان اصفهان"), fill=(191, 219, 254), font=load_font(21), anchor="mm")
    draw.text((width//2, 108), fa("کارنامه هوشمند ارزیابی عملکرد ماهانه"), fill=(255, 255, 255), font=load_font(38), anchor="mm")
    sub_title = f"شهرستان: {district_name}   |   دوره ارزیابی: ماه {month} سال {year}"
    draw.text((width//2, 172), fa(sub_title), fill=(254, 240, 138), font=load_font(26), anchor="mm")

    score_val = actual_dict.get('overall_score', 0)
    score_str = f"{score_val:.1f}%" if isinstance(score_val, (int, float)) else str(score_val)
    is_zero = (isinstance(score_val, (int, float)) and score_val == 0)

    # 2. Executive Summary Cards (Y: 255 to 455) - RTL arranged
    card_w = 306
    card_h = 200
    y_cards = 255
    
    # Right: Score
    c_right_x = 718
    c1_bg = (248, 249, 250) if is_zero else (235, 245, 251)
    c1_out = (180, 180, 180) if is_zero else (37, 99, 235)
    c1_txt = (100, 110, 120) if is_zero else (20, 38, 68)
    draw.rounded_rectangle([c_right_x, y_cards, c_right_x + card_w, y_cards + card_h], radius=14, fill=c1_bg, outline=c1_out, width=2)
    draw.text((c_right_x + card_w//2, y_cards + 36), fa("میانگین تحقق اهداف"), fill=c1_out, font=load_font(20), anchor="mm")
    draw.text((c_right_x + card_w//2, y_cards + 105), fa(score_str), fill=c1_txt, font=load_font(52), anchor="mm")
    draw.text((c_right_x + card_w//2, y_cards + 165), fa("کل شاخص‌های ابلاغی"), fill=(71, 85, 105), font=load_font(18), anchor="mm")

    # Center: Rank
    c_mid_x = 387
    c2_bg = (254, 242, 242) if is_zero else (236, 253, 245)
    c2_out = (220, 38, 38) if is_zero else (16, 185, 129)
    c2_txt = (185, 28, 28) if is_zero else (4, 120, 87)
    draw.rounded_rectangle([c_mid_x, y_cards, c_mid_x + card_w, y_cards + card_h], radius=14, fill=c2_bg, outline=c2_out, width=2)
    draw.text((c_mid_x + card_w//2, y_cards + 36), fa("رتبه در استان"), fill=c2_out, font=load_font(20), anchor="mm")
    rank_disp = "عدم فعالیت" if is_zero else (f"رتبه {rank}" if "رتبه" not in str(rank) else str(rank))
    draw.text((c_mid_x + card_w//2, y_cards + 105), fa(rank_disp), fill=c2_txt, font=load_font(34), anchor="mm")
    rank_sub = "فاقد گزارش ماهانه" if is_zero else "از میان ۳۲ شهرستان"
    draw.text((c_mid_x + card_w//2, y_cards + 165), fa(rank_sub), fill=(71, 85, 105), font=load_font(18), anchor="mm")

    # Left: Qualitative Tier
    c_left_x = 55
    if is_zero:
        tier_title = "فاقد عملکرد"
        tier_sub = "عدم ارسال گزارش"
        c3_bg, c3_out, c3_txt = (250, 250, 250), (180, 180, 180), (120, 120, 120)
    elif score_val >= 100:
        tier_title = "سطح عالی"
        tier_sub = "تحقق بالای ۱۰۰ درصد"
        c3_bg, c3_out, c3_txt = (254, 252, 232), (234, 179, 8), (161, 98, 7)
    elif score_val >= 75:
        tier_title = "سطح خوب"
        tier_sub = "تحقق ۷۵ تا ۹۹ درصد"
        c3_bg, c3_out, c3_txt = (239, 246, 255), (59, 130, 246), (29, 78, 216)
    elif score_val >= 50:
        tier_title = "سطح متوسط"
        tier_sub = "تحقق ۵۰ تا ۷۴ درصد"
        c3_bg, c3_out, c3_txt = (255, 251, 235), (245, 158, 11), (180, 83, 9)
    else:
        tier_title = "سطح ضعیف"
        tier_sub = "تحقق زیر ۵۰ درصد"
        c3_bg, c3_out, c3_txt = (254, 242, 242), (239, 68, 68), (185, 28, 28)

    draw.rounded_rectangle([c_left_x, y_cards, c_left_x + card_w, y_cards + card_h], radius=14, fill=c3_bg, outline=c3_out, width=2)
    draw.text((c_left_x + card_w//2, y_cards + 36), fa("سطح کیفی عملکرد"), fill=c3_out, font=load_font(20), anchor="mm")
    draw.text((c_left_x + card_w//2, y_cards + 105), fa(tier_title), fill=c3_txt, font=load_font(30), anchor="mm")
    draw.text((c_left_x + card_w//2, y_cards + 165), fa(tier_sub), fill=(71, 85, 105), font=load_font(18), anchor="mm")

    # 3. Overall Progress Bar Card (Y: 480 to 585)
    bar_y = 480
    draw.rounded_rectangle([55, bar_y, 1025, bar_y + 105], radius=12, fill=(255, 255, 255), outline=(226, 232, 240), width=1)
    draw.text((995, bar_y + 32), fa("درصد پیشرفت کل اهداف ابلاغی شهرستان:"), fill=(30, 41, 59), font=load_font(21), anchor="rm")
    draw.text((85, bar_y + 32), fa(score_str), fill=(20, 38, 68), font=load_font(26), anchor="lm")
    bar_color = (180, 180, 180) if is_zero else ((16, 185, 129) if score_val >= 100 else ((37, 99, 235) if score_val >= 75 else ((245, 158, 11) if score_val >= 50 else (220, 38, 38))))
    draw_progressbar(draw, 85, bar_y + 64, 910, 20, score_val, bar_color, radius=10)

    # 4. Indicators Section (Y: 610 to 1430)
    sec_y = 610
    draw.rectangle([55, sec_y, 1025, sec_y + 54], fill=(24, 43, 73))
    draw.text((width//2, sec_y + 27), fa("ریز عملکرد شاخص‌های پنج‌گانه ابلاغی - بر مبنای مجموع تعداد نفرات"), fill=(255, 255, 255), font=load_font(23), anchor="mm")

    indicators = [
        (1, "سواد رسانه حضوری و توانمندسازی (ضریب ۳۱)", target_dict.get('hozori', 0), actual_dict.get('hozori', 0)),
        (2, "سواد رسانه مجازی و لایو (ضریب ۲۱۷)", target_dict.get('majazi', 0), actual_dict.get('majazi', 0)),
        (3, "اقدامات و ابتکارات خلاقانه (ضریب ۶۲)", target_dict.get('khalagh', 0), actual_dict.get('khalagh', 0)),
        (4, "تولیدات رسانه‌ای و محتوایی (ضریب ۳)", target_dict.get('tolid', 0), actual_dict.get('tolid', 0)),
        (5, "نشست دبیر نسرا با انجمن مدرسان (۱ نشست)", target_dict.get('neshast', 1), actual_dict.get('neshast', 0))
    ]

    card_start_y = sec_y + 70
    row_height = 135
    row_gap = 14

    for idx, name, tgt, act in indicators:
        curr_y = card_start_y + (idx - 1) * (row_height + row_gap)
        pct = (act / tgt * 100) if tgt > 0 else 0
        diff = act - tgt
        diff_str = f"+{diff:,}" if diff > 0 else f"{diff:,}"

        draw.rounded_rectangle([55, curr_y, 1025, curr_y + row_height], radius=12, fill=(255, 255, 255), outline=(226, 232, 240), width=1)
        
        # Line 1: Title and % badge
        draw.text((995, curr_y + 35), fa(f"{idx}. {name}"), fill=(20, 38, 68), font=load_font(22), anchor="rm")
        
        if pct == 0:
            b_bg, b_txt = (241, 245, 249), (100, 116, 139)
        elif pct >= 100:
            b_bg, b_txt = (209, 250, 229), (6, 95, 70)
        elif pct >= 75:
            b_bg, b_txt = (219, 234, 254), (30, 64, 175)
        elif pct >= 50:
            b_bg, b_txt = (254, 243, 199), (146, 64, 14)
        else:
            b_bg, b_txt = (254, 226, 226), (153, 27, 27)

        badge_w, badge_h = 145, 36
        draw.rounded_rectangle([85, curr_y + 17, 85 + badge_w, curr_y + 17 + badge_h], radius=8, fill=b_bg)
        draw.text((85 + badge_w//2, curr_y + 17 + badge_h//2), fa(f"{pct:.1f}% تحقق"), fill=b_txt, font=load_font(20), anchor="mm")

        # Line 2: Details in bold dark readable color
        unit = "نشست" if "نشست" in name else "نفر"
        details_txt = f"حد انتظار: {int(tgt):,} {unit}   •   عملکرد واقعی: {int(act):,} {unit}   •   انحراف از هدف: {diff_str} {unit}"
        draw.text((995, curr_y + 75), fa(details_txt), fill=(30, 41, 59), font=load_font(20), anchor="rm")

        # Line 3: Progress bar
        row_bar_col = (180, 180, 180) if pct == 0 else ((16, 185, 129) if pct >= 100 else ((37, 99, 235) if pct >= 75 else ((245, 158, 11) if pct >= 50 else (220, 38, 38))))
        draw_progressbar(draw, 85, curr_y + 104, 910, 16, pct, row_bar_col, radius=8)

    # 5. Organizational Profile Section (Y: 1450 to 1695)
    prof_y = 1450
    draw.rounded_rectangle([55, prof_y, 1025, prof_y + 245], radius=14, fill=(255, 255, 255), outline=(226, 232, 240), width=1)
    
    draw.rounded_rectangle([55, prof_y, 1025, prof_y + 50], radius=14, fill=(241, 245, 249))
    draw.rectangle([55, prof_y + 25, 1025, prof_y + 50], fill=(241, 245, 249))
    draw.text((995, prof_y + 25), fa("مشخصات تشکیلاتی و مبانی سنجش کارنامه ناحیه:"), fill=(20, 38, 68), font=load_font(22), anchor="rm")

    b_status = f"عدم ارسال گزارش در ماه {month} - عملکرد صفر منظور شد" if is_zero else f"گزارش ماه {month} دریافت و در سامانه ثبت شد"
    profile_bullets = [
        f"• تعداد حوزه‌های مقاومت تابعه: {target_dict.get('branches', 0)} حوزه مقاومت",
        "• حد انتظار اعضای انجمن مدرسان شهرستان: ۲۲ نفر",
        "• ملاک قطعی ارزیابی شاخص‌ها: مجموع «تعداد نفرات» شرکت‌کننده ثبت‌شده در گزارش‌های ماهانه",
        f"• وضعیت گزارش دوره: {b_status}"
    ]

    for b_idx, bullet in enumerate(profile_bullets):
        draw.text((995, prof_y + 82 + b_idx * 40), fa(bullet), fill=(30, 41, 59), font=load_font(20), anchor="rm")

    # 6. Official Footer & Verification Stamp (Y: 1720 to 1890)
    foot_y = 1720
    draw.rounded_rectangle([55, foot_y, 1025, foot_y + 160], radius=14, fill=(255, 255, 255), outline=(37, 99, 235), width=2)
    
    # Official stamp box on the left
    draw.rounded_rectangle([85, foot_y + 18, 385, foot_y + 142], radius=10, fill=(240, 253, 244), outline=(16, 185, 129), width=2)
    draw.text((235, foot_y + 48), fa("دبیرخانه نهضت سواد رسانه‌ای"), fill=(20, 38, 68), font=load_font(19), anchor="mm")
    draw.text((235, foot_y + 82), fa("استان اصفهان - کارنامه رسمی"), fill=(4, 120, 87), font=load_font(19), anchor="mm")
    draw.text((235, foot_y + 114), fa("[ مورد تأیید مراجع استانی ]"), fill=(51, 65, 85), font=load_font(16), anchor="mm")

    # Explanatory text on the right
    draw.text((995, foot_y + 45), fa(f"کارنامه رسمی ارزیابی عملکرد ماه «{month}» سال {year}"), fill=(20, 38, 68), font=load_font(21), anchor="rm")
    draw.text((995, foot_y + 85), fa("صادره از سامانه جامع مانیتورینگ عملکرد ۳۲ شهرستان استان اصفهان"), fill=(71, 85, 105), font=load_font(18), anchor="rm")
    draw.text((995, foot_y + 122), fa("• بهینه‌شده با ابعاد ایستاده (۱۰۸۰×۱۹۲۰) جهت مطالعه آسان در گوشی و پیام‌رسان‌ها"), fill=(100, 116, 139), font=load_font(16), anchor="rm")

    if isinstance(output_path, (str, os.PathLike)):
        parent_dir = os.path.dirname(os.path.abspath(output_path))
        if parent_dir:
            os.makedirs(parent_dir, exist_ok=True)
    img.save(output_path, "PNG", dpi=(150, 150))
    return img

def generate_dashboard_png(macro_data, top5_data, bottom5_data, kpi_data, month="شهریور", year="۱۴۰۵", output_path="dashboard.png"):
    from PIL import Image, ImageDraw
    
    # 1080 x 1920 Mobile Portrait (Vertical)
    width, height = 1080, 1920
    img = Image.new('RGB', (width, height), color=(245, 247, 250))
    draw = ImageDraw.Draw(img)
    
    # 1. Header Banner (Y: 0 to 225)
    draw.rectangle([0, 0, width, 225], fill=(20, 38, 68))
    draw.rectangle([0, 225, width, 233], fill=(37, 99, 235))
    
    draw.text((width//2, 45), fa("نهضت سواد رسانه‌ای انقلاب اسلامی (نسرا) - استان اصفهان"), fill=(191, 219, 254), font=load_font(21), anchor="mm")
    draw.text((width//2, 108), fa("داشبورد مدیریتی مانیتورینگ عملکرد نواحی"), fill=(255, 255, 255), font=load_font(38), anchor="mm")
    dash_sub = f"دوره ارزیابی: ماه {month} سال {year}   |   پایش جامع ۳۲ شهرستان و ۲۲۹ حوزه مقاومت"
    draw.text((width//2, 172), fa(dash_sub), fill=(254, 240, 138), font=load_font(25), anchor="mm")

    # 2. Executive KPI Cards (2x2 Grid) (Y: 255 to 515) - RTL arranged
    c_w = 465
    c_h = 118
    row1_y = 255
    row2_y = 390
    col_right_x = 560
    col_left_x = 55

    # Top-Right: Formation Coverage
    draw.rounded_rectangle([col_right_x, row1_y, col_right_x + c_w, row1_y + c_h], radius=14, fill=(255, 255, 255), outline=(20, 38, 68), width=1)
    draw.text((col_right_x + c_w//2, row1_y + 34), fa("پوشش تشکیلاتی استان"), fill=(71, 85, 105), font=load_font(20), anchor="mm")
    draw.text((col_right_x + c_w//2, row1_y + 80), fa("۳۲ ناحیه   |   ۲۲۹ حوزه"), fill=(20, 38, 68), font=load_font(30), anchor="mm")

    # Top-Left: Provincial Average
    draw.rounded_rectangle([col_left_x, row1_y, col_left_x + c_w, row1_y + c_h], radius=14, fill=(235, 245, 251), outline=(37, 99, 235), width=2)
    draw.text((col_left_x + c_w//2, row1_y + 34), fa(f"میانگین عملکرد استان ({month})"), fill=(37, 99, 235), font=load_font(20), anchor="mm")
    draw.text((col_left_x + c_w//2, row1_y + 80), fa(f"{kpi_data.get('avg_score', 0):.1f}%"), fill=(20, 38, 68), font=load_font(42), anchor="mm")

    # Bottom-Right: Top Performer
    draw.rounded_rectangle([col_right_x, row2_y, col_right_x + c_w, row2_y + c_h], radius=14, fill=(236, 253, 245), outline=(16, 185, 129), width=2)
    draw.text((col_right_x + c_w//2, row2_y + 34), fa("پیشتاز استان (رتبه ۱)"), fill=(4, 120, 87), font=load_font(20), anchor="mm")
    draw.text((col_right_x + c_w//2, row2_y + 80), fa(kpi_data.get('top_district', 'کاشان')), fill=(6, 95, 70), font=load_font(32), anchor="mm")

    # Bottom-Left: Reports Status
    draw.rounded_rectangle([col_left_x, row2_y, col_left_x + c_w, row2_y + c_h], radius=14, fill=(254, 252, 232), outline=(234, 179, 8), width=2)
    draw.text((col_left_x + c_w//2, row2_y + 34), fa("وضعیت ارسال گزارش نواحی"), fill=(161, 98, 7), font=load_font(20), anchor="mm")
    draw.text((col_left_x + c_w//2, row2_y + 80), fa(f"{kpi_data.get('reported_count', 32)} از ۳۲ ناحیه فعال"), fill=(133, 77, 14), font=load_font(28), anchor="mm")

    # 3. Macro Indicators Section (Y: 535 to 1180)
    sec_y = 535
    draw.rectangle([55, sec_y, 1025, sec_y + 50], fill=(24, 43, 73))
    draw.text((width//2, sec_y + 25), fa(f"۱. جدول و نمودار تحقق شاخص‌های کلان در کل استان اصفهان - {month} {year}"), fill=(255, 255, 255), font=load_font(23), anchor="mm")

    m_start_y = sec_y + 65
    m_h = 108
    m_gap = 12

    for idx, (m_name, m_tgt, m_act) in enumerate(macro_data, start=1):
        curr_y = m_start_y + (idx - 1) * (m_h + m_gap)
        pct = (m_act / m_tgt * 100) if m_tgt > 0 else 0
        diff = m_act - m_tgt
        diff_str = f"+{diff:,}" if diff > 0 else f"{diff:,}"

        draw.rounded_rectangle([55, curr_y, 1025, curr_y + m_h], radius=12, fill=(255, 255, 255), outline=(226, 232, 240), width=1)
        
        # Line 1: Name and % badge
        draw.text((995, curr_y + 28), fa(f"{idx}. {m_name}"), fill=(20, 38, 68), font=load_font(21), anchor="rm")
        
        pct_color = (6, 95, 70) if pct >= 100 else ((30, 64, 175) if pct >= 75 else ((146, 64, 14) if pct >= 50 else (153, 27, 27)))
        pct_bg = (209, 250, 229) if pct >= 100 else ((219, 234, 254) if pct >= 75 else ((254, 243, 199) if pct >= 50 else (254, 226, 226)))
        draw.rounded_rectangle([85, curr_y + 12, 215, curr_y + 44], radius=6, fill=pct_bg)
        draw.text((150, curr_y + 28), fa(f"{pct:.1f}% تحقق"), fill=pct_color, font=load_font(19), anchor="mm")

        # Line 2: Details
        meta_txt = f"حد انتظار استان: {int(m_tgt):,} نفر   •   عملکرد واقعی: {int(m_act):,} نفر   •   انحراف: {diff_str} نفر"
        draw.text((995, curr_y + 60), fa(meta_txt), fill=(51, 65, 85), font=load_font(19), anchor="rm")

        # Line 3: Progress bar
        bar_col = (16, 185, 129) if pct >= 100 else ((37, 99, 235) if pct >= 75 else ((245, 158, 11) if pct >= 50 else (220, 38, 38)))
        draw_progressbar(draw, 85, curr_y + 84, 910, 14, pct, bar_col, radius=7)

    # 4. Top 5 & Bottom 5 Leaderboards (Y: 1205 to 1715) - RTL arranged
    lead_y = 1205
    col_w = 465
    
    # Right Box: Top 5
    draw.rounded_rectangle([col_right_x, lead_y, col_right_x + col_w, lead_y + 490], radius=14, fill=(255, 255, 255), outline=(16, 185, 129), width=2)
    draw.rounded_rectangle([col_right_x, lead_y, col_right_x + col_w, lead_y + 55], radius=14, fill=(4, 120, 87))
    draw.rectangle([col_right_x, lead_y + 30, col_right_x + col_w, lead_y + 55], fill=(4, 120, 87))
    draw.text((col_right_x + col_w//2, lead_y + 27), fa("۵ ناحیه برتر و پیشتاز استان"), fill=(255, 255, 255), font=load_font(23), anchor="mm")

    # Left Box: Bottom 5
    draw.rounded_rectangle([col_left_x, lead_y, col_left_x + col_w, lead_y + 490], radius=14, fill=(255, 255, 255), outline=(220, 38, 38), width=2)
    draw.rounded_rectangle([col_left_x, lead_y, col_left_x + col_w, lead_y + 55], radius=14, fill=(185, 28, 28))
    draw.rectangle([col_left_x, lead_y + 30, col_left_x + col_w, lead_y + 55], fill=(185, 28, 28))
    draw.text((col_left_x + col_w//2, lead_y + 27), fa("۵ ناحیه نیازمند پیگیری و تقویت"), fill=(255, 255, 255), font=load_font(23), anchor="mm")

    curr_item_y = lead_y + 65
    item_h = 76

    for k in range(5):
        t_row = top5_data[k] if k < len(top5_data) else (k+1, "در انتظار", 0)
        b_row = bottom5_data[k] if k < len(bottom5_data) else (k+1, "در انتظار", 0)

        # Top 5 item
        row_bg_t = (248, 252, 250) if k % 2 == 0 else (255, 255, 255)
        draw.rounded_rectangle([col_right_x + 10, curr_item_y, col_right_x + col_w - 10, curr_item_y + item_h], radius=8, fill=row_bg_t)
        
        draw.ellipse([col_right_x + col_w - 60, curr_item_y + 18, col_right_x + col_w - 20, curr_item_y + 58], fill=(4, 120, 87))
        draw.text((col_right_x + col_w - 40, curr_item_y + 38), fa(str(t_row[0])), fill=(255, 255, 255), font=load_font(22), anchor="mm")
        
        draw.text((col_right_x + col_w - 75, curr_item_y + 38), fa(t_row[1]), fill=(20, 38, 68), font=load_font(21), anchor="rm")
        draw.text((col_right_x + 30, curr_item_y + 38), fa(f"{t_row[2]:.1f}%"), fill=(4, 120, 87), font=load_font(22), anchor="lm")

        # Bottom 5 item
        row_bg_b = (254, 248, 248) if k % 2 == 0 else (255, 255, 255)
        draw.rounded_rectangle([col_left_x + 10, curr_item_y, col_left_x + col_w - 10, curr_item_y + item_h], radius=8, fill=row_bg_b)
        
        draw.ellipse([col_left_x + col_w - 60, curr_item_y + 18, col_left_x + col_w - 20, curr_item_y + 58], fill=(185, 28, 28))
        draw.text((col_left_x + col_w - 40, curr_item_y + 38), fa(str(b_row[0])), fill=(255, 255, 255), font=load_font(22), anchor="mm")
        
        b_score_disp = f"{b_row[2]:.1f}%" if b_row[2] > 0 else "عدم فعالیت"
        draw.text((col_left_x + col_w - 75, curr_item_y + 38), fa(b_row[1]), fill=(20, 38, 68), font=load_font(21), anchor="rm")
        draw.text((col_left_x + 30, curr_item_y + 38), fa(b_score_disp), fill=(185, 28, 28), font=load_font(21), anchor="lm")

        curr_item_y += item_h + 8

    # 5. Footer (Y: 1730 to 1890)
    foot_y = 1730
    draw.rounded_rectangle([55, foot_y, 1025, foot_y + 150], radius=14, fill=(255, 255, 255), outline=(37, 99, 235), width=2)
    
    draw.rounded_rectangle([85, foot_y + 18, 385, foot_y + 132], radius=10, fill=(240, 253, 244), outline=(16, 185, 129), width=2)
    draw.text((235, foot_y + 45), fa("دبیرخانه نهضت سواد رسانه‌ای"), fill=(20, 38, 68), font=load_font(19), anchor="mm")
    draw.text((235, foot_y + 76), fa("استان اصفهان - گزارش مدیریتی"), fill=(4, 120, 87), font=load_font(19), anchor="mm")
    draw.text((235, foot_y + 106), fa("[ نسخه رسمی ویژه مسئولین ]"), fill=(51, 65, 85), font=load_font(16), anchor="mm")

    draw.text((995, foot_y + 45), fa(f"داشبورد رسمی مانیتورینگ عملکرد ۳۲ شهرستان نسرا - ماه «{month}» {year}"), fill=(20, 38, 68), font=load_font(21), anchor="rm")
    draw.text((995, foot_y + 82), fa("ملاک ارزیابی: مجموع تعداد نفرات شرکت‌کننده در کلاس‌ها و لایوها"), fill=(71, 85, 105), font=load_font(18), anchor="rm")
    draw.text((995, foot_y + 116), fa("• طراحی‌شده با سایز عمودی ایستاده (۱۰۸۰×۱۹۲۰) جهت مطالعه آسان در موبایل"), fill=(100, 116, 139), font=load_font(16), anchor="rm")

    if isinstance(output_path, (str, os.PathLike)):
        parent_dir = os.path.dirname(os.path.abspath(output_path))
        if parent_dir:
            os.makedirs(parent_dir, exist_ok=True)
    img.save(output_path, "PNG", dpi=(150, 150))
    return img

print("image_generator.py ready (dynamic month and year supported)")

def generate_quarterly_scorecard_png(district_name, target_dict, actual_dict, rank="۱", tier="عالی", period="بهار (فروردین تا خرداد)", year="۱۴۰۵", files_count=3, output_path="scorecard_3m.png"):
    from PIL import Image, ImageDraw
    
    width, height = 1080, 1920
    img = Image.new('RGB', (width, height), color=(245, 247, 250))
    draw = ImageDraw.Draw(img)
    
    # 1. Header Banner (Y: 0 to 225)
    draw.rectangle([0, 0, width, 225], fill=(20, 38, 68))
    draw.rectangle([0, 225, width, 233], fill=(16, 185, 129))
    
    draw.text((width//2, 45), fa("نهضت سواد رسانه‌ای انقلاب اسلامی (نسرا) - استان اصفهان"), fill=(191, 219, 254), font=load_font(21), anchor="mm")
    draw.text((width//2, 108), fa("کارنامه هوشمند ارزیابی عملکرد ۳ ماهه (فصلی)"), fill=(255, 255, 255), font=load_font(36), anchor="mm")
    sub_title = f"شهرستان: {district_name}   |   دوره ارزیابی: {period} سال {year}"
    draw.text((width//2, 172), fa(sub_title), fill=(254, 240, 138), font=load_font(25), anchor="mm")

    # Score calculation (70 to 100 scale)
    realization_val = actual_dict.get('overall_realization', 0.0)
    # Score 70 is zero, 100 is 100%
    score_val = actual_dict.get('score_70_100', 70.0 + 30.0 * min(1.0, max(0.0, realization_val / 100.0)))
    score_str = f"{score_val:.1f}"
    is_zero = (realization_val == 0)

    # 2. Executive Summary Cards (Y: 255 to 455)
    card_w = 306
    card_h = 200
    y_cards = 255
    
    # Right: Official Score (70-100)
    c_right_x = 718
    c1_bg = (248, 249, 250) if is_zero else (236, 253, 245)
    c1_out = (180, 180, 180) if is_zero else (16, 185, 129)
    c1_txt = (100, 110, 120) if is_zero else (4, 120, 87)
    draw.rounded_rectangle([c_right_x, y_cards, c_right_x + card_w, y_cards + card_h], radius=14, fill=c1_bg, outline=c1_out, width=2)
    draw.text((c_right_x + card_w//2, y_cards + 36), fa("نمره عملکرد (۷۰ تا ۱۰۰)"), fill=c1_out, font=load_font(20), anchor="mm")
    draw.text((c_right_x + card_w//2, y_cards + 105), fa(score_str), fill=c1_txt, font=load_font(54), anchor="mm")
    draw.text((c_right_x + card_w//2, y_cards + 165), fa("مبنا: ۷۰ صفر تا ۱۰۰ عالی"), fill=(71, 85, 105), font=load_font(18), anchor="mm")

    # Center: Rank
    c_mid_x = 387
    c2_bg = (254, 242, 242) if is_zero else (235, 245, 251)
    c2_out = (220, 38, 38) if is_zero else (37, 99, 235)
    c2_txt = (185, 28, 28) if is_zero else (29, 78, 216)
    draw.rounded_rectangle([c_mid_x, y_cards, c_mid_x + card_w, y_cards + card_h], radius=14, fill=c2_bg, outline=c2_out, width=2)
    draw.text((c_mid_x + card_w//2, y_cards + 36), fa("رتبه فصلی در استان"), fill=c2_out, font=load_font(20), anchor="mm")
    rank_disp = "عدم فعالیت" if is_zero else (f"رتبه {rank}" if "رتبه" not in str(rank) else str(rank))
    draw.text((c_mid_x + card_w//2, y_cards + 105), fa(rank_disp), fill=c2_txt, font=load_font(34), anchor="mm")
    rank_sub = "فاقد گزارش ۳ ماهه" if is_zero else "از میان ۳۲ شهرستان"
    draw.text((c_mid_x + card_w//2, y_cards + 165), fa(rank_sub), fill=(71, 85, 105), font=load_font(18), anchor="mm")

    # Left: Qualitative Tier
    c_left_x = 55
    if is_zero:
        tier_title = "فاقد عملکرد"
        tier_sub = "نمره ۷۰ (عدم فعالیت)"
        c3_bg, c3_out, c3_txt = (250, 250, 250), (180, 180, 180), (120, 120, 120)
    elif score_val >= 99.9:
        tier_title = "سطح عالی"
        tier_sub = "نمره کامل ۱۰۰ (پیشتاز)"
        c3_bg, c3_out, c3_txt = (254, 252, 232), (234, 179, 8), (161, 98, 7)
    elif score_val >= 92.5:
        tier_title = "سطح خوب"
        tier_sub = f"نمره {score_str} (تحقق مطلوب)"
        c3_bg, c3_out, c3_txt = (239, 246, 255), (59, 130, 246), (29, 78, 216)
    elif score_val >= 85.0:
        tier_title = "سطح متوسط"
        tier_sub = f"نمره {score_str} (متوسط)"
        c3_bg, c3_out, c3_txt = (255, 251, 235), (245, 158, 11), (180, 83, 9)
    else:
        tier_title = "سطح ضعیف"
        tier_sub = f"نمره {score_str} (نیازمند تلاش)"
        c3_bg, c3_out, c3_txt = (254, 242, 242), (239, 68, 68), (185, 28, 28)

    draw.rounded_rectangle([c_left_x, y_cards, c_left_x + card_w, y_cards + card_h], radius=14, fill=c3_bg, outline=c3_out, width=2)
    draw.text((c_left_x + card_w//2, y_cards + 36), fa("سطح ارزیابی ۳ ماهه"), fill=c3_out, font=load_font(20), anchor="mm")
    draw.text((c_left_x + card_w//2, y_cards + 105), fa(tier_title), fill=c3_txt, font=load_font(30), anchor="mm")
    draw.text((c_left_x + card_w//2, y_cards + 165), fa(tier_sub), fill=(71, 85, 105), font=load_font(18), anchor="mm")

    # 3. Overall Progress Bar Card (Y: 480 to 585)
    bar_y = 480
    draw.rounded_rectangle([55, bar_y, 1025, bar_y + 105], radius=12, fill=(255, 255, 255), outline=(226, 232, 240), width=1)
    draw.text((995, bar_y + 32), fa("درصد تحقق تجمعی اهداف ۳ ماهه شهرستان:"), fill=(30, 41, 59), font=load_font(21), anchor="rm")
    draw.text((85, bar_y + 32), fa(f"{realization_val:.1f}%"), fill=(20, 38, 68), font=load_font(26), anchor="lm")
    bar_color = (180, 180, 180) if is_zero else ((16, 185, 129) if realization_val >= 100 else ((37, 99, 235) if realization_val >= 75 else ((245, 158, 11) if realization_val >= 50 else (220, 38, 38))))
    draw_progressbar(draw, 85, bar_y + 64, 910, 20, realization_val, bar_color, radius=10)

    # 4. Indicators Section (Y: 610 to 1430)
    sec_y = 610
    draw.rectangle([55, sec_y, 1025, sec_y + 54], fill=(20, 38, 68))
    draw.text((width//2, sec_y + 27), fa("ریز عملکرد تجمعی شاخص‌های ۵گانه در دوره ۳ ماهه (مجموع نفرات)"), fill=(255, 255, 255), font=load_font(23), anchor="mm")

    indicators = [
        (1, "سواد رسانه حضوری و توانمندسازی (ضریب ۳۱×۳ = ۹۳)", target_dict.get('hozori', 0), actual_dict.get('hozori', 0)),
        (2, "سواد رسانه مجازی و لایو (ضریب ۲۱۷×۳ = ۶۵۱)", target_dict.get('majazi', 0), actual_dict.get('majazi', 0)),
        (3, "اقدامات و ابتکارات خلاقانه (ضریب ۶۲×۳ = ۱۸۶)", target_dict.get('khalagh', 0), actual_dict.get('khalagh', 0)),
        (4, "تولیدات رسانه‌ای و محتوایی (ضریب ۳×۳ = ۹)", target_dict.get('tolid', 0), actual_dict.get('tolid', 0)),
        (5, "نشست دبیر نسرا با انجمن مدرسان (۳ نشست)", target_dict.get('neshast', 3), actual_dict.get('neshast', 0))
    ]

    card_start_y = sec_y + 70
    row_height = 135
    row_gap = 14

    for idx, name, tgt, act in indicators:
        curr_y = card_start_y + (idx - 1) * (row_height + row_gap)
        pct = (act / tgt * 100) if tgt > 0 else 0
        diff = act - tgt
        diff_str = f"+{diff:,}" if diff > 0 else f"{diff:,}"

        draw.rounded_rectangle([55, curr_y, 1025, curr_y + row_height], radius=12, fill=(255, 255, 255), outline=(226, 232, 240), width=1)
        
        draw.text((995, curr_y + 35), fa(f"{idx}. {name}"), fill=(20, 38, 68), font=load_font(22), anchor="rm")
        
        if pct == 0:
            b_bg, b_txt = (241, 245, 249), (100, 116, 139)
        elif pct >= 100:
            b_bg, b_txt = (209, 250, 229), (6, 95, 70)
        elif pct >= 75:
            b_bg, b_txt = (219, 234, 254), (30, 64, 175)
        elif pct >= 50:
            b_bg, b_txt = (254, 243, 199), (146, 64, 14)
        else:
            b_bg, b_txt = (254, 226, 226), (153, 27, 27)

        badge_w, badge_h = 145, 36
        draw.rounded_rectangle([85, curr_y + 17, 85 + badge_w, curr_y + 17 + badge_h], radius=8, fill=b_bg)
        draw.text((85 + badge_w//2, curr_y + 17 + badge_h//2), fa(f"{pct:.1f}% تحقق"), fill=b_txt, font=load_font(20), anchor="mm")

        unit = "نشست" if "نشست" in name else "نفر"
        details_txt = f"حد انتظار ۳ ماهه: {int(tgt):,} {unit}   •   عملکرد تجمعی ۳ ماهه: {int(act):,} {unit}   •   انحراف: {diff_str} {unit}"
        draw.text((995, curr_y + 75), fa(details_txt), fill=(30, 41, 59), font=load_font(20), anchor="rm")

        row_bar_col = (180, 180, 180) if pct == 0 else ((16, 185, 129) if pct >= 100 else ((37, 99, 235) if pct >= 75 else ((245, 158, 11) if pct >= 50 else (220, 38, 38))))
        draw_progressbar(draw, 85, curr_y + 104, 910, 16, pct, row_bar_col, radius=8)

    # 5. Organizational Profile Section (Y: 1450 to 1695)
    prof_y = 1450
    draw.rounded_rectangle([55, prof_y, 1025, prof_y + 245], radius=14, fill=(255, 255, 255), outline=(226, 232, 240), width=1)
    
    draw.rounded_rectangle([55, prof_y, 1025, prof_y + 50], radius=14, fill=(241, 245, 249))
    draw.rectangle([55, prof_y + 25, 1025, prof_y + 50], fill=(241, 245, 249))
    draw.text((995, prof_y + 25), fa("مشخصات تشکیلاتی و مبانی سنجش کارنامه ۳ ماهه:"), fill=(20, 38, 68), font=load_font(22), anchor="rm")

    b_status = f"تعداد فایل‌های تجمیع‌شده: {files_count} فایل از ۳ ماه دوره"
    profile_bullets = [
        f"• تعداد حوزه‌های مقاومت تابعه: {target_dict.get('branches', 0)} حوزه مقاومت",
        f"• حد انتظار ۳ ماهه: محاسبه‌شده با ضریب ۳ برابر اهداف ماهانه ابلاغی",
        "• فرمول محاسبه نمره: نمره = ۷۰ + (۳۰ × درصد تحقق) | مقیاس رسمی ۷۰ (صفر) تا ۱۰۰ (عالی)",
        f"• وضعیت گزارش‌های دریافتی: {b_status}"
    ]

    for b_idx, bullet in enumerate(profile_bullets):
        draw.text((995, prof_y + 82 + b_idx * 40), fa(bullet), fill=(30, 41, 59), font=load_font(20), anchor="rm")

    # 6. Official Footer & Verification Stamp (Y: 1720 to 1890)
    foot_y = 1720
    draw.rounded_rectangle([55, foot_y, 1025, foot_y + 160], radius=14, fill=(255, 255, 255), outline=(16, 185, 129), width=2)
    
    # Official stamp box on the left
    draw.rounded_rectangle([85, foot_y + 18, 385, foot_y + 142], radius=10, fill=(240, 253, 244), outline=(16, 185, 129), width=2)
    draw.text((235, foot_y + 48), fa("دبیرخانه نهضت سواد رسانه‌ای"), fill=(20, 38, 68), font=load_font(19), anchor="mm")
    draw.text((235, foot_y + 82), fa("استان اصفهان - کارنامه رسمی"), fill=(4, 120, 87), font=load_font(19), anchor="mm")
    draw.text((235, foot_y + 114), fa("[ مورد تأیید مراجع استانی ]"), fill=(51, 65, 85), font=load_font(16), anchor="mm")

    # Explanatory text on the right
    draw.text((995, foot_y + 45), fa(f"کارنامه رسمی ارزیابی عملکرد دوره {period} سال {year}"), fill=(20, 38, 68), font=load_font(21), anchor="rm")
    draw.text((995, foot_y + 85), fa("صادره از سامانه جامع مانیتورینگ عملکرد ۳۲ شهرستان استان اصفهان"), fill=(71, 85, 105), font=load_font(18), anchor="rm")
    draw.text((995, foot_y + 122), fa("• بهینه‌شده با ابعاد ایستاده (۱۰۸۰×۱۹۲۰) جهت مطالعه آسان در گوشی و پیام‌رسان‌ها"), fill=(100, 116, 139), font=load_font(16), anchor="rm")

    if isinstance(output_path, (str, os.PathLike)):
        parent_dir = os.path.dirname(os.path.abspath(output_path))
        if parent_dir:
            os.makedirs(parent_dir, exist_ok=True)
    img.save(output_path, "PNG", dpi=(150, 150))
    return img

def generate_quarterly_dashboard_png(macro_data, top5_data, bottom5_data, kpi_data, period="بهار (فروردین تا خرداد)", year="۱۴۰۵", output_path="dashboard_3m.png"):
    from PIL import Image, ImageDraw
    
    width, height = 1080, 1920
    img = Image.new('RGB', (width, height), color=(245, 247, 250))
    draw = ImageDraw.Draw(img)
    
    # 1. Header Banner (Y: 0 to 225)
    draw.rectangle([0, 0, width, 225], fill=(20, 38, 68))
    draw.rectangle([0, 225, width, 233], fill=(16, 185, 129))
    
    draw.text((width//2, 45), fa("نهضت سواد رسانه‌ای انقلاب اسلامی (نسرا) - استان اصفهان"), fill=(191, 219, 254), font=load_font(21), anchor="mm")
    draw.text((width//2, 108), fa("داشبورد مدیریتی مانیتورینگ عملکرد ۳ ماهه"), fill=(255, 255, 255), font=load_font(38), anchor="mm")
    dash_sub = f"دوره ارزیابی: {period} سال {year}   |   پایش جامع ۳۲ شهرستان و ۲۲۹ حوزه"
    draw.text((width//2, 172), fa(dash_sub), fill=(254, 240, 138), font=load_font(25), anchor="mm")

    # 2. Executive KPI Cards (2x2 Grid) (Y: 255 to 515) - RTL arranged
    c_w = 465
    c_h = 118
    row1_y = 255
    row2_y = 390
    col_right_x = 560
    col_left_x = 55

    # Top-Right: Formation Coverage
    draw.rounded_rectangle([col_right_x, row1_y, col_right_x + c_w, row1_y + c_h], radius=14, fill=(255, 255, 255), outline=(20, 38, 68), width=1)
    draw.text((col_right_x + c_w//2, row1_y + 34), fa("پوشش تشکیلاتی استان"), fill=(71, 85, 105), font=load_font(20), anchor="mm")
    draw.text((col_right_x + c_w//2, row1_y + 80), fa("۳۲ ناحیه   |   ۲۲۹ حوزه"), fill=(20, 38, 68), font=load_font(30), anchor="mm")

    # Top-Left: Provincial Average Score (70-100)
    draw.rounded_rectangle([col_left_x, row1_y, col_left_x + c_w, row1_y + c_h], radius=14, fill=(236, 253, 245), outline=(16, 185, 129), width=2)
    draw.text((col_left_x + c_w//2, row1_y + 34), fa("میانگین نمره عملکرد استان (۷۰-۱۰۰)"), fill=(4, 120, 87), font=load_font(19), anchor="mm")
    avg_sc = kpi_data.get('avg_score', 70.0)
    draw.text((col_left_x + c_w//2, row1_y + 80), fa(f"{avg_sc:.1f}"), fill=(20, 38, 68), font=load_font(42), anchor="mm")

    # Bottom-Right: Top Performer
    draw.rounded_rectangle([col_right_x, row2_y, col_right_x + c_w, row2_y + c_h], radius=14, fill=(254, 252, 232), outline=(234, 179, 8), width=2)
    draw.text((col_right_x + c_w//2, row2_y + 34), fa("پیشتاز استان (رتبه ۱ فصل)"), fill=(161, 98, 7), font=load_font(20), anchor="mm")
    draw.text((col_right_x + c_w//2, row2_y + 80), fa(kpi_data.get('top_district', 'مبارکه')), fill=(133, 77, 14), font=load_font(32), anchor="mm")

    # Bottom-Left: Reports Status
    draw.rounded_rectangle([col_left_x, row2_y, col_left_x + c_w, row2_y + c_h], radius=14, fill=(235, 245, 251), outline=(37, 99, 235), width=2)
    draw.text((col_left_x + c_w//2, row2_y + 34), fa("وضعیت ارسال گزارش نواحی"), fill=(37, 99, 235), font=load_font(20), anchor="mm")
    draw.text((col_left_x + c_w//2, row2_y + 80), fa(f"{kpi_data.get('reported_count', 32)} از ۳۲ ناحیه فعال"), fill=(20, 38, 68), font=load_font(28), anchor="mm")

    # 3. Macro Indicators Section (Y: 535 to 1180)
    sec_y = 535
    draw.rectangle([55, sec_y, 1025, sec_y + 50], fill=(24, 43, 73))
    draw.text((width//2, sec_y + 25), fa(f"۱. تحقق شاخص‌های کلان ۳ ماهه در کل استان اصفهان - {period}"), fill=(255, 255, 255), font=load_font(23), anchor="mm")

    m_start_y = sec_y + 65
    m_h = 108
    m_gap = 12

    for idx, (m_name, m_tgt, m_act) in enumerate(macro_data, start=1):
        curr_y = m_start_y + (idx - 1) * (m_h + m_gap)
        pct = (m_act / m_tgt * 100) if m_tgt > 0 else 0
        diff = m_act - m_tgt
        diff_str = f"+{diff:,}" if diff > 0 else f"{diff:,}"

        draw.rounded_rectangle([55, curr_y, 1025, curr_y + m_h], radius=12, fill=(255, 255, 255), outline=(226, 232, 240), width=1)
        
        draw.text((995, curr_y + 28), fa(f"{idx}. {m_name}"), fill=(20, 38, 68), font=load_font(21), anchor="rm")
        
        pct_color = (6, 95, 70) if pct >= 100 else ((30, 64, 175) if pct >= 75 else ((146, 64, 14) if pct >= 50 else (153, 27, 27)))
        pct_bg = (209, 250, 229) if pct >= 100 else ((219, 234, 254) if pct >= 75 else ((254, 243, 199) if pct >= 50 else (254, 226, 226)))
        draw.rounded_rectangle([85, curr_y + 12, 215, curr_y + 44], radius=6, fill=pct_bg)
        draw.text((150, curr_y + 28), fa(f"{pct:.1f}% تحقق"), fill=pct_color, font=load_font(19), anchor="mm")

        unit = "نشست" if "نشست" in m_name else "نفر"
        meta_txt = f"حد انتظار ۳ ماهه استان: {int(m_tgt):,} {unit}   •   عملکرد تجمعی استان: {int(m_act):,} {unit}   •   انحراف: {diff_str} {unit}"
        draw.text((995, curr_y + 60), fa(meta_txt), fill=(51, 65, 85), font=load_font(19), anchor="rm")

        bar_col = (16, 185, 129) if pct >= 100 else ((37, 99, 235) if pct >= 75 else ((245, 158, 11) if pct >= 50 else (220, 38, 38)))
        draw_progressbar(draw, 85, curr_y + 84, 910, 14, pct, bar_col, radius=7)

    # 4. Top 5 & Bottom 5 Leaderboards (Y: 1205 to 1715) - RTL arranged
    lead_y = 1205
    col_w = 465
    
    # Right Box: Top 5
    draw.rounded_rectangle([col_right_x, lead_y, col_right_x + col_w, lead_y + 490], radius=14, fill=(255, 255, 255), outline=(16, 185, 129), width=2)
    draw.rounded_rectangle([col_right_x, lead_y, col_right_x + col_w, lead_y + 55], radius=14, fill=(4, 120, 87))
    draw.rectangle([col_right_x, lead_y + 30, col_right_x + col_w, lead_y + 55], fill=(4, 120, 87))
    draw.text((col_right_x + col_w//2, lead_y + 27), fa("🏆 ۵ ناحیه برتر و پیشتاز فصل"), fill=(255, 255, 255), font=load_font(23), anchor="mm")

    # Left Box: Bottom 5
    draw.rounded_rectangle([col_left_x, lead_y, col_left_x + col_w, lead_y + 490], radius=14, fill=(255, 255, 255), outline=(220, 38, 38), width=2)
    draw.rounded_rectangle([col_left_x, lead_y, col_left_x + col_w, lead_y + 55], radius=14, fill=(185, 28, 28))
    draw.rectangle([col_left_x, lead_y + 30, col_left_x + col_w, lead_y + 55], fill=(185, 28, 28))
    draw.text((col_left_x + col_w//2, lead_y + 27), fa("⚠️ نواحی نیازمند پیگیری در ۳ ماهه"), fill=(255, 255, 255), font=load_font(23), anchor="mm")

    curr_item_y = lead_y + 65
    item_h = 76

    for k in range(5):
        t_row = top5_data[k] if k < len(top5_data) else (k+1, "در انتظار", 70.0)
        b_row = bottom5_data[k] if k < len(bottom5_data) else (k+1, "در انتظار", 70.0)

        # Top 5 item
        row_bg_t = (248, 252, 250) if k % 2 == 0 else (255, 255, 255)
        draw.rounded_rectangle([col_right_x + 10, curr_item_y, col_right_x + col_w - 10, curr_item_y + item_h], radius=8, fill=row_bg_t)
        
        draw.ellipse([col_right_x + col_w - 60, curr_item_y + 18, col_right_x + col_w - 20, curr_item_y + 58], fill=(4, 120, 87))
        draw.text((col_right_x + col_w - 40, curr_item_y + 38), fa(str(t_row[0])), fill=(255, 255, 255), font=load_font(22), anchor="mm")
        
        draw.text((col_right_x + col_w - 75, curr_item_y + 38), fa(t_row[1]), fill=(20, 38, 68), font=load_font(21), anchor="rm")
        draw.text((col_right_x + 30, curr_item_y + 38), fa(f"نمره {t_row[2]:.1f}"), fill=(4, 120, 87), font=load_font(21), anchor="lm")

        # Bottom 5 item
        row_bg_b = (254, 248, 248) if k % 2 == 0 else (255, 255, 255)
        draw.rounded_rectangle([col_left_x + 10, curr_item_y, col_left_x + col_w - 10, curr_item_y + item_h], radius=8, fill=row_bg_b)
        
        draw.ellipse([col_left_x + col_w - 60, curr_item_y + 18, col_left_x + col_w - 20, curr_item_y + 58], fill=(185, 28, 28))
        draw.text((col_left_x + col_w - 40, curr_item_y + 38), fa(str(b_row[0])), fill=(255, 255, 255), font=load_font(22), anchor="mm")
        
        b_score_disp = f"نمره {b_row[2]:.1f}" if b_row[2] > 70.0 else "نمره ۷۰ (عدم فعالیت)"
        draw.text((col_left_x + col_w - 75, curr_item_y + 38), fa(b_row[1]), fill=(20, 38, 68), font=load_font(21), anchor="rm")
        draw.text((col_left_x + 30, curr_item_y + 38), fa(b_score_disp), fill=(185, 28, 28), font=load_font(20), anchor="lm")

        curr_item_y += item_h + 8

    # 5. Footer (Y: 1730 to 1890)
    foot_y = 1730
    draw.rounded_rectangle([55, foot_y, 1025, foot_y + 150], radius=14, fill=(255, 255, 255), outline=(16, 185, 129), width=2)
    
    draw.rounded_rectangle([85, foot_y + 18, 385, foot_y + 132], radius=10, fill=(240, 253, 244), outline=(16, 185, 129), width=2)
    draw.text((235, foot_y + 45), fa("دبیرخانه نهضت سواد رسانه‌ای"), fill=(20, 38, 68), font=load_font(19), anchor="mm")
    draw.text((235, foot_y + 76), fa("استان اصفهان - گزارش فصلی"), fill=(4, 120, 87), font=load_font(19), anchor="mm")
    draw.text((235, foot_y + 106), fa("[ نسخه رسمی ویژه مسئولین ]"), fill=(51, 65, 85), font=load_font(16), anchor="mm")

    draw.text((995, foot_y + 45), fa(f"داشبورد رسمی مانیتورینگ عملکرد ۳ ماهه نسرا - {period} {year}"), fill=(20, 38, 68), font=load_font(21), anchor="rm")
    draw.text((995, foot_y + 82), fa("فرمول نمره نهایی: ۷۰ + (۳۰ × درصد تحقق) | بازه ۷۰ (حداقل) تا ۱۰۰ (عالی)"), fill=(71, 85, 105), font=load_font(18), anchor="rm")
    draw.text((995, foot_y + 116), fa("• طراحی‌شده با سایز عمودی ایستاده (۱۰۸۰×۱۹۲۰) جهت مطالعه آسان در موبایل"), fill=(100, 116, 139), font=load_font(16), anchor="rm")

    if isinstance(output_path, (str, os.PathLike)):
        parent_dir = os.path.dirname(os.path.abspath(output_path))
        if parent_dir:
            os.makedirs(parent_dir, exist_ok=True)
    img.save(output_path, "PNG", dpi=(150, 150))
    return img
