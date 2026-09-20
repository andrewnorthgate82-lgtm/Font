# -*- coding: utf-8 -*-
"""
========================================================================
ماژول تولید تصاویر بهینه‌شده ویژه تلفن همراه (1080 × 1920 ایستاده - عمودی)
کارنامه هوشمند و داشبورد مانیتورینگ عملکرد نواحی نسرا - استان اصفهان
طراحی مدرن، چینش کاملاً فارسی (RTL)، تایپوگرافی بدون کاراکترهای نامعتبر
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
    # Remove any emoji characters that could cause tofu boxes in standard Persian fonts
    cleaned = []
    for ch in s:
        cp = ord(ch)
        if (0x1F600 <= cp <= 0x1F64F or
            0x1F300 <= cp <= 0x1F5FF or
            0x1F680 <= cp <= 0x1F6FF or
            0x1F700 <= cp <= 0x1F77F or
            0x1F780 <= cp <= 0x1F7FF or
            0x1F800 <= cp <= 0x1F8FF or
            0x1F900 <= cp <= 0x1F9FF or
            0x1FA00 <= cp <= 0x1FA6F or
            0x2600 <= cp <= 0x26FF or
            0x2700 <= cp <= 0x27BF):
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

def load_font(size, bold=False):
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

def draw_progressbar(draw, x, y, w, h, pct, fill_color, bg_color=(235, 237, 240), radius=6):
    draw.rounded_rectangle([x, y, x + w, y + h], radius=radius, fill=bg_color)
    clamped_pct = max(0.0, min(100.0, pct))
    fill_w = int(w * (clamped_pct / 100.0))
    if fill_w > 4:
        draw.rounded_rectangle([x, y, x + fill_w, y + h], radius=radius, fill=fill_color)

def generate_scorecard_png(district_name, target_dict, actual_dict, rank="-", tier="عالی", month="شهریور", output_path="scorecard.png"):
    from PIL import Image, ImageDraw
    
    # 1080 x 1920 Mobile Portrait (Vertical)
    width, height = 1080, 1920
    img = Image.new('RGB', (width, height), color=(245, 247, 250))
    draw = ImageDraw.Draw(img)
    
    # Fonts
    font_banner_sub = load_font(21)
    font_banner_title = load_font(38, bold=True)
    font_banner_dist = load_font(27, bold=True)
    
    font_card_lbl = load_font(20)
    font_card_val = load_font(52, bold=True)
    font_card_sub = load_font(18)
    
    font_sec_hdr = load_font(23, bold=True)
    font_item_title = load_font(22, bold=True)
    font_item_meta = load_font(20)
    font_badge = load_font(20, bold=True)
    
    font_info_hdr = load_font(22, bold=True)
    font_info_body = load_font(20)
    font_footer = load_font(18)
    font_stamp = load_font(19, bold=True)

    # 1. Header Banner (Y: 0 to 230)
    draw.rectangle([0, 0, width, 230], fill=(27, 54, 93))
    draw.rectangle([0, 230, width, 238], fill=(41, 128, 185))
    
    draw.text((width//2, 50), fa("نهضت سواد رسانه‌ای انقلاب اسلامی (نسرا) - استان اصفهان"), fill=(212, 239, 252), font=font_banner_sub, anchor="mm")
    draw.text((width//2, 115), fa("کارنامه هوشمند ارزیابی عملکرد ماهانه"), fill=(255, 255, 255), font=font_banner_title, anchor="mm")
    sub_title = f"شهرستان: {district_name}  |  دوره ارزیابی: ماه {month} ۱۴۰۵"
    draw.text((width//2, 180), fa(sub_title), fill=(254, 249, 231), font=font_banner_dist, anchor="mm")

    # Score & status
    score_val = actual_dict.get('overall_score', 0)
    score_str = f"{score_val:.1f}%" if isinstance(score_val, (int, float)) else str(score_val)
    is_zero = (isinstance(score_val, (int, float)) and score_val == 0)

    # 2. Executive Summary Cards (Y: 260 to 470) - RTL arranged
    card_w = 306
    card_h = 195
    y_cards = 260
    
    # Right Card: Score (Most prominent in RTL)
    c_right_x = 718
    c1_bg = (248, 249, 250) if is_zero else (235, 245, 251)
    c1_out = (180, 180, 180) if is_zero else (41, 128, 185)
    c1_txt = (120, 120, 120) if is_zero else (27, 54, 93)
    draw.rounded_rectangle([c_right_x, y_cards, c_right_x + card_w, y_cards + card_h], radius=14, fill=c1_bg, outline=c1_out, width=2)
    draw.text((c_right_x + card_w//2, y_cards + 35), fa("میانگین تحقق اهداف"), fill=c1_out, font=font_card_lbl, anchor="mm")
    draw.text((c_right_x + card_w//2, y_cards + 105), fa(score_str), fill=c1_txt, font=font_card_val, anchor="mm")
    draw.text((c_right_x + card_w//2, y_cards + 160), fa("کل شاخص‌های پنج‌گانه"), fill=(120, 140, 160), font=font_card_sub, anchor="mm")

    # Center Card: Rank
    c_mid_x = 387
    c2_bg = (253, 242, 242) if is_zero else (232, 248, 245)
    c2_out = (217, 83, 79) if is_zero else (39, 174, 96)
    c2_txt = (192, 57, 43) if is_zero else (14, 98, 81)
    draw.rounded_rectangle([c_mid_x, y_cards, c_mid_x + card_w, y_cards + card_h], radius=14, fill=c2_bg, outline=c2_out, width=2)
    draw.text((c_mid_x + card_w//2, y_cards + 35), fa("رتبه در استان"), fill=c2_out, font=font_card_lbl, anchor="mm")
    rank_disp = "عدم فعالیت" if is_zero else (f"رتبه {rank}" if "رتبه" not in str(rank) else str(rank))
    draw.text((c_mid_x + card_w//2, y_cards + 105), fa(rank_disp), fill=c2_txt, font=load_font(34, bold=True), anchor="mm")
    rank_sub = "فاقد گزارش ماهانه" if is_zero else "از میان ۳۲ شهرستان"
    draw.text((c_mid_x + card_w//2, y_cards + 160), fa(rank_sub), fill=(120, 140, 160), font=font_card_sub, anchor="mm")

    # Left Card: Qualitative Tier
    c_left_x = 55
    c3_bg = (250, 250, 250) if is_zero else ((254, 249, 231) if ("عالی" in tier or "خوب" in tier) else (253, 242, 242))
    c3_out = (180, 180, 180) if is_zero else ((241, 196, 15) if ("عالی" in tier or "خوب" in tier) else (217, 83, 79))
    c3_txt = (120, 120, 120) if is_zero else ((125, 102, 8) if ("عالی" in tier or "خوب" in tier) else (150, 30, 30))
    draw.rounded_rectangle([c_left_x, y_cards, c_left_x + card_w, y_cards + card_h], radius=14, fill=c3_bg, outline=c3_out, width=2)
    draw.text((c_left_x + card_w//2, y_cards + 35), fa("سطح کیفی عملکرد"), fill=c3_out, font=font_card_lbl, anchor="mm")
    tier_disp = "فاقد عملکرد" if is_zero else tier
    draw.text((c_left_x + card_w//2, y_cards + 105), fa(tier_disp), fill=c3_txt, font=load_font(24, bold=True), anchor="mm")
    b_count = target_dict.get('branches', 0)
    draw.text((c_left_x + card_w//2, y_cards + 160), fa(f"{b_count} حوزه مقاومت"), fill=(120, 140, 160), font=font_card_sub, anchor="mm")

    # 3. Overall Progress Bar Card (Y: 490 to 600)
    bar_y = 490
    draw.rounded_rectangle([55, bar_y, 1025, bar_y + 100], radius=12, fill=(255, 255, 255), outline=(220, 225, 230), width=1)
    draw.text((1000, bar_y + 30), fa("درصد پیشرفت کل اهداف ابلاغی شهرستان:"), fill=(44, 62, 80), font=font_card_lbl, anchor="rm")
    draw.text((80, bar_y + 30), fa(score_str), fill=(27, 54, 93), font=load_font(26, bold=True), anchor="lm")
    bar_color = (180, 180, 180) if is_zero else ((39, 174, 96) if score_val >= 100 else ((41, 128, 185) if score_val >= 75 else ((243, 156, 18) if score_val >= 50 else (192, 57, 43))))
    draw_progressbar(draw, 80, bar_y + 58, 920, 22, score_val, bar_color, radius=10)

    # 4. Indicators Section (Y: 620 to 1420)
    sec_y = 620
    draw.rectangle([55, sec_y, 1025, sec_y + 55], fill=(31, 78, 121))
    draw.text((width//2, sec_y + 27), fa("ریز عملکرد شاخص‌های پنج‌گانه ابلاغی (بر مبنای مجموع تعداد نفرات)"), fill=(255, 255, 255), font=font_sec_hdr, anchor="mm")

    indicators = [
        (1, "سواد رسانه حضوری و توانمندسازی (۳۱×)", target_dict.get('hozori', 0), actual_dict.get('hozori', 0)),
        (2, "سواد رسانه مجازی و لایو (۲۱۷×)", target_dict.get('majazi', 0), actual_dict.get('majazi', 0)),
        (3, "اقدامات و ابتکارات خلاقانه (۶۲×)", target_dict.get('khalagh', 0), actual_dict.get('khalagh', 0)),
        (4, "تولیدات رسانه‌ای و محتوایی (۳×)", target_dict.get('tolid', 0), actual_dict.get('tolid', 0)),
        (5, "نشست دبیر نسرا با انجمن مدرسان", target_dict.get('neshast', 1), actual_dict.get('neshast', 0))
    ]

    card_start_y = sec_y + 70
    row_height = 135
    row_gap = 14

    for idx, name, tgt, act in indicators:
        curr_y = card_start_y + (idx - 1) * (row_height + row_gap)
        pct = (act / tgt * 100) if tgt > 0 else 0
        diff = act - tgt
        diff_str = f"+{diff:,}" if diff > 0 else f"{diff:,}"

        draw.rounded_rectangle([55, curr_y, 1025, curr_y + row_height], radius=12, fill=(255, 255, 255), outline=(225, 230, 235), width=1)
        
        # Line 1: Title and % badge
        draw.text((1000, curr_y + 35), fa(f"{idx}. {name}"), fill=(27, 54, 93), font=font_item_title, anchor="rm")
        
        if pct == 0:
            b_bg, b_txt = (240, 240, 240), (120, 120, 120)
        elif pct >= 100:
            b_bg, b_txt = (212, 237, 218), (21, 87, 36)
        elif pct >= 75:
            b_bg, b_txt = (209, 236, 241), (12, 84, 96)
        elif pct >= 50:
            b_bg, b_txt = (255, 243, 205), (133, 100, 4)
        else:
            b_bg, b_txt = (248, 215, 218), (114, 28, 36)

        badge_w, badge_h = 145, 36
        draw.rounded_rectangle([80, curr_y + 17, 80 + badge_w, curr_y + 17 + badge_h], radius=8, fill=b_bg)
        draw.text((80 + badge_w//2, curr_y + 17 + badge_h//2), fa(f"{pct:.1f}% تحقق"), fill=b_txt, font=font_badge, anchor="mm")

        # Line 2: Details
        unit = "نشست" if "نشست" in name else "نفر"
        details_txt = f"حد انتظار: {int(tgt):,} {unit}   |   عملکرد واقعی: {int(act):,} {unit}   |   انحراف از هدف: {diff_str} {unit}"
        draw.text((1000, curr_y + 75), fa(details_txt), fill=(80, 95, 110), font=font_item_meta, anchor="rm")

        # Line 3: Progress bar
        row_bar_col = (180, 180, 180) if pct == 0 else ((39, 174, 96) if pct >= 100 else ((41, 128, 185) if pct >= 75 else ((243, 156, 18) if pct >= 50 else (192, 57, 43))))
        draw_progressbar(draw, 80, curr_y + 106, 920, 14, pct, row_bar_col, radius=7)

    # 5. Organizational Profile Section (Y: 1450 to 1700)
    prof_y = 1450
    draw.rounded_rectangle([55, prof_y, 1025, prof_y + 240], radius=14, fill=(255, 255, 255), outline=(215, 220, 228), width=1)
    
    draw.rounded_rectangle([55, prof_y, 1025, prof_y + 50], radius=14, fill=(240, 244, 248))
    draw.rectangle([55, prof_y + 25, 1025, prof_y + 50], fill=(240, 244, 248))
    draw.text((1000, prof_y + 25), fa("مشخصات تشکیلاتی و مبانی سنجش کارنامه ناحیه:"), fill=(31, 78, 121), font=font_info_hdr, anchor="rm")

    b_status = "عدم ارسال گزارش ماهانه در این دوره (عملکرد صفر منظور شد)" if is_zero else "گزارش ماهانه رسمی دریافت و در سامانه ثبت شد"
    profile_bullets = [
        f"• تعداد حوزه‌های مقاومت تابعه: {target_dict.get('branches', 0)} حوزه مقاومت",
        "• حد انتظار اعضای انجمن مدرسان شهرستان: ۲۲ نفر",
        "• ملاک قطعی محاسبه شاخص‌ها: مجموع «تعداد نفرات» شرکت‌کننده ثبت‌شده در گزارش‌های ماهانه",
        f"• وضعیت گزارش دوره: {b_status}"
    ]

    for b_idx, bullet in enumerate(profile_bullets):
        draw.text((1000, prof_y + 80 + b_idx * 38), fa(bullet), fill=(60, 75, 90), font=font_info_body, anchor="rm")

    # 6. Official Footer & Verification Stamp (Y: 1720 to 1890)
    foot_y = 1720
    draw.rounded_rectangle([55, foot_y, 1025, foot_y + 155], radius=14, fill=(255, 255, 255), outline=(41, 128, 185), width=2)
    
    # Official stamp box on the left
    draw.rounded_rectangle([80, foot_y + 18, 380, foot_y + 138], radius=10, fill=(242, 249, 246), outline=(39, 174, 96), width=2)
    draw.text((230, foot_y + 48), fa("دبیرخانه نهضت سواد رسانه‌ای"), fill=(27, 54, 93), font=font_stamp, anchor="mm")
    draw.text((230, foot_y + 82), fa("استان اصفهان - کارنامه رسمی"), fill=(39, 174, 96), font=font_stamp, anchor="mm")
    draw.text((230, foot_y + 112), fa("[ مورد تأیید مراجع استانی ]"), fill=(44, 62, 80), font=load_font(16), anchor="mm")

    # Explanatory text on the right
    draw.text((1000, foot_y + 45), fa(f"کارنامه رسمی ارزیابی عملکرد ماه «{month}» سال ۱۴۰۵"), fill=(27, 54, 93), font=load_font(21, bold=True), anchor="rm")
    draw.text((1000, foot_y + 85), fa("صادره از سامانه جامع مانیتورینگ عملکرد ۳۲ شهرستان استان اصفهان"), fill=(80, 95, 110), font=font_footer, anchor="rm")
    draw.text((1000, foot_y + 120), fa("• بهینه‌شده با ابعاد ایستاده (۱۰۸۰×۱۹۲۰) جهت مطالعه آسان در گوشی و پیام‌رسان‌ها"), fill=(120, 130, 140), font=load_font(16), anchor="rm")

    if isinstance(output_path, (str, os.PathLike)):
        parent_dir = os.path.dirname(os.path.abspath(output_path))
        if parent_dir:
            os.makedirs(parent_dir, exist_ok=True)
    img.save(output_path, "PNG", dpi=(150, 150))
    return img

def generate_dashboard_png(macro_data, top5_data, bottom5_data, kpi_data, month="شهریور", output_path="dashboard.png"):
    from PIL import Image, ImageDraw
    
    # 1080 x 1920 Mobile Portrait (Vertical)
    width, height = 1080, 1920
    img = Image.new('RGB', (width, height), color=(245, 247, 250))
    draw = ImageDraw.Draw(img)
    
    # Fonts
    font_banner_sub = load_font(21)
    font_banner_title = load_font(34, bold=True)
    font_banner_dist = load_font(24, bold=True)
    
    font_card_lbl = load_font(19)
    font_card_val = load_font(40, bold=True)
    
    font_sec_hdr = load_font(23, bold=True)
    font_item_title = load_font(21, bold=True)
    font_item_meta = load_font(19)
    font_badge = load_font(19, bold=True)
    
    font_rank_num = load_font(24, bold=True)
    font_rank_name = load_font(21, bold=True)
    font_rank_score = load_font(21, bold=True)

    # 1. Header Banner (Y: 0 to 220)
    draw.rectangle([0, 0, width, 220], fill=(27, 54, 93))
    draw.rectangle([0, 220, width, 228], fill=(41, 128, 185))
    
    draw.text((width//2, 45), fa("نهضت سواد رسانه‌ای انقلاب اسلامی (نسرا) - استان اصفهان"), fill=(212, 239, 252), font=font_banner_sub, anchor="mm")
    draw.text((width//2, 105), fa("داشبورد مدیریتی مانیتورینگ عملکرد نواحی"), fill=(255, 255, 255), font=font_banner_title, anchor="mm")
    dash_sub = f"دوره ارزیابی: ماه {month} ۱۴۰۵  |  پایش جامع ۳۲ شهرستان و ۲۲۹ حوزه مقاومت"
    draw.text((width//2, 168), fa(dash_sub), fill=(254, 249, 231), font=font_banner_dist, anchor="mm")

    # 2. Executive KPI Cards (2x2 Grid) (Y: 250 to 510) - RTL arranged
    c_w = 465
    c_h = 115
    row1_y = 250
    row2_y = 380
    col_right_x = 560
    col_left_x = 55

    # Top-Right: Formation Coverage
    draw.rounded_rectangle([col_right_x, row1_y, col_right_x + c_w, row1_y + c_h], radius=12, fill=(255, 255, 255), outline=(27, 54, 93), width=1)
    draw.text((col_right_x + c_w//2, row1_y + 32), fa("پوشش تشکیلاتی استان"), fill=(60, 80, 100), font=font_card_lbl, anchor="mm")
    draw.text((col_right_x + c_w//2, row1_y + 78), fa("۳۲ ناحیه  |  ۲۲۹ حوزه"), fill=(27, 54, 93), font=load_font(30, bold=True), anchor="mm")

    # Top-Left: Provincial Average
    draw.rounded_rectangle([col_left_x, row1_y, col_left_x + c_w, row1_y + c_h], radius=12, fill=(235, 245, 251), outline=(41, 128, 185), width=2)
    draw.text((col_left_x + c_w//2, row1_y + 32), fa(f"میانگین عملکرد استان ({month})"), fill=(41, 128, 185), font=font_card_lbl, anchor="mm")
    draw.text((col_left_x + c_w//2, row1_y + 78), fa(f"{kpi_data.get('avg_score', 0):.1f}%"), fill=(27, 54, 93), font=font_card_val, anchor="mm")

    # Bottom-Right: Top Performer
    draw.rounded_rectangle([col_right_x, row2_y, col_right_x + c_w, row2_y + c_h], radius=12, fill=(232, 248, 245), outline=(39, 174, 96), width=2)
    draw.text((col_right_x + c_w//2, row2_y + 32), fa("پیشتاز استان (رتبه ۱)"), fill=(39, 174, 96), font=font_card_lbl, anchor="mm")
    draw.text((col_right_x + c_w//2, row2_y + 78), fa(kpi_data.get('top_district', 'کاشان')), fill=(14, 98, 81), font=load_font(32, bold=True), anchor="mm")

    # Bottom-Left: Reports Status
    draw.rounded_rectangle([col_left_x, row2_y, col_left_x + c_w, row2_y + c_h], radius=12, fill=(254, 249, 231), outline=(241, 196, 15), width=2)
    draw.text((col_left_x + c_w//2, row2_y + 32), fa("وضعیت ارسال گزارش نواحی"), fill=(183, 149, 11), font=font_card_lbl, anchor="mm")
    draw.text((col_left_x + c_w//2, row2_y + 78), fa(f"{kpi_data.get('reported_count', 32)} از ۳۲ ناحیه فعال"), fill=(125, 102, 8), font=load_font(28, bold=True), anchor="mm")

    # 3. Macro Indicators Section (Y: 530 to 1180)
    sec_y = 530
    draw.rectangle([55, sec_y, 1025, sec_y + 50], fill=(31, 78, 121))
    draw.text((width//2, sec_y + 25), fa(f"۱. جدول و نمودار تحقق شاخص‌های کلان در کل استان اصفهان - {month} ۱۴۰۵"), fill=(255, 255, 255), font=font_sec_hdr, anchor="mm")

    m_start_y = sec_y + 65
    m_h = 108
    m_gap = 12

    for idx, (m_name, m_tgt, m_act) in enumerate(macro_data, start=1):
        curr_y = m_start_y + (idx - 1) * (m_h + m_gap)
        pct = (m_act / m_tgt * 100) if m_tgt > 0 else 0
        diff = m_act - m_tgt
        diff_str = f"+{diff:,}" if diff > 0 else f"{diff:,}"

        draw.rounded_rectangle([55, curr_y, 1025, curr_y + m_h], radius=10, fill=(255, 255, 255), outline=(225, 230, 235), width=1)
        
        # Line 1: Name and % badge
        draw.text((1000, curr_y + 28), fa(f"{idx}. {m_name}"), fill=(27, 54, 93), font=font_item_title, anchor="rm")
        
        pct_color = (21, 87, 36) if pct >= 100 else ((12, 84, 96) if pct >= 75 else ((133, 100, 4) if pct >= 50 else (114, 28, 36)))
        pct_bg = (212, 237, 218) if pct >= 100 else ((209, 236, 241) if pct >= 75 else ((255, 243, 205) if pct >= 50 else (248, 215, 218)))
        draw.rounded_rectangle([80, curr_y + 12, 210, curr_y + 44], radius=6, fill=pct_bg)
        draw.text((145, curr_y + 28), fa(f"{pct:.1f}% تحقق"), fill=pct_color, font=font_badge, anchor="mm")

        # Line 2: Details
        meta_txt = f"حد انتظار استان: {int(m_tgt):,} نفر   |   عملکرد واقعی: {int(m_act):,} نفر   |   انحراف: {diff_str} نفر"
        draw.text((1000, curr_y + 60), fa(meta_txt), fill=(80, 95, 110), font=font_item_meta, anchor="rm")

        # Line 3: Progress bar
        bar_col = (39, 174, 96) if pct >= 100 else ((41, 128, 185) if pct >= 75 else ((243, 156, 18) if pct >= 50 else (192, 57, 43)))
        draw_progressbar(draw, 80, curr_y + 84, 920, 12, pct, bar_col, radius=6)

    # 4. Top 5 & Bottom 5 Leaderboards (Y: 1200 to 1710) - RTL arranged
    lead_y = 1200
    col_w = 465
    
    # Right Box: Top 5 (Primary focus in RTL reading order)
    draw.rounded_rectangle([col_right_x, lead_y, col_right_x + col_w, lead_y + 490], radius=12, fill=(255, 255, 255), outline=(39, 174, 96), width=2)
    draw.rounded_rectangle([col_right_x, lead_y, col_right_x + col_w, lead_y + 55], radius=12, fill=(17, 120, 100))
    draw.rectangle([col_right_x, lead_y + 30, col_right_x + col_w, lead_y + 55], fill=(17, 120, 100))
    draw.text((col_right_x + col_w//2, lead_y + 27), fa("۵ ناحیه برتر و پیشتاز استان"), fill=(255, 255, 255), font=font_sec_hdr, anchor="mm")

    # Left Box: Bottom 5
    draw.rounded_rectangle([col_left_x, lead_y, col_left_x + col_w, lead_y + 490], radius=12, fill=(255, 255, 255), outline=(192, 57, 43), width=2)
    draw.rounded_rectangle([col_left_x, lead_y, col_left_x + col_w, lead_y + 55], radius=12, fill=(192, 57, 43))
    draw.rectangle([col_left_x, lead_y + 30, col_left_x + col_w, lead_y + 55], fill=(192, 57, 43))
    draw.text((col_left_x + col_w//2, lead_y + 27), fa("۵ ناحیه نیازمند پیگیری و تقویت"), fill=(255, 255, 255), font=font_sec_hdr, anchor="mm")

    curr_item_y = lead_y + 65
    item_h = 76

    for k in range(5):
        t_row = top5_data[k] if k < len(top5_data) else (k+1, "در انتظار", 0)
        b_row = bottom5_data[k] if k < len(bottom5_data) else (k+1, "در انتظار", 0)

        # Top 5 item (on Right column)
        row_bg_t = (248, 252, 250) if k % 2 == 0 else (255, 255, 255)
        draw.rounded_rectangle([col_right_x + 10, curr_item_y, col_right_x + col_w - 10, curr_item_y + item_h], radius=8, fill=row_bg_t)
        
        draw.ellipse([col_right_x + col_w - 60, curr_item_y + 18, col_right_x + col_w - 20, curr_item_y + 58], fill=(17, 120, 100))
        draw.text((col_right_x + col_w - 40, curr_item_y + 38), fa(str(t_row[0])), fill=(255, 255, 255), font=font_rank_num, anchor="mm")
        
        draw.text((col_right_x + col_w - 75, curr_item_y + 38), fa(t_row[1]), fill=(27, 54, 93), font=font_rank_name, anchor="rm")
        draw.text((col_right_x + 30, curr_item_y + 38), fa(f"{t_row[2]:.1f}%"), fill=(21, 87, 36), font=font_rank_score, anchor="lm")

        # Bottom 5 item (on Left column)
        row_bg_b = (255, 248, 248) if k % 2 == 0 else (255, 255, 255)
        draw.rounded_rectangle([col_left_x + 10, curr_item_y, col_left_x + col_w - 10, curr_item_y + item_h], radius=8, fill=row_bg_b)
        
        draw.ellipse([col_left_x + col_w - 60, curr_item_y + 18, col_left_x + col_w - 20, curr_item_y + 58], fill=(192, 57, 43))
        draw.text((col_left_x + col_w - 40, curr_item_y + 38), fa(str(b_row[0])), fill=(255, 255, 255), font=font_rank_num, anchor="mm")
        
        b_score_disp = f"{b_row[2]:.1f}%" if b_row[2] > 0 else "عدم فعالیت"
        draw.text((col_left_x + col_w - 75, curr_item_y + 38), fa(b_row[1]), fill=(27, 54, 93), font=font_rank_name, anchor="rm")
        draw.text((col_left_x + 30, curr_item_y + 38), fa(b_score_disp), fill=(192, 57, 43), font=font_rank_score, anchor="lm")

        curr_item_y += item_h + 8

    # 5. Footer (Y: 1730 to 1890)
    foot_y = 1730
    draw.rounded_rectangle([55, foot_y, 1025, foot_y + 145], radius=14, fill=(255, 255, 255), outline=(41, 128, 185), width=2)
    
    draw.rounded_rectangle([80, foot_y + 18, 380, foot_y + 128], radius=10, fill=(242, 249, 246), outline=(39, 174, 96), width=2)
    draw.text((230, foot_y + 45), fa("دبیرخانه نهضت سواد رسانه‌ای"), fill=(27, 54, 93), font=load_font(19, bold=True), anchor="mm")
    draw.text((230, foot_y + 75), fa("استان اصفهان - گزارش مدیریتی"), fill=(39, 174, 96), font=load_font(19, bold=True), anchor="mm")
    draw.text((230, foot_y + 105), fa("[ نسخه رسمی ویژه مسئولین ]"), fill=(44, 62, 80), font=load_font(16), anchor="mm")

    draw.text((1000, foot_y + 45), fa(f"داشبورد رسمی مانیتورینگ عملکرد ۳۲ شهرستان نسرا - ماه «{month}» ۱۴۰۵"), fill=(27, 54, 93), font=load_font(21, bold=True), anchor="rm")
    draw.text((1000, foot_y + 85), fa("ملاک ارزیابی: مجموع تعداد نفرات شرکت‌کننده در کلاس‌ها و لایوها"), fill=(80, 95, 110), font=load_font(18), anchor="rm")
    draw.text((1000, foot_y + 118), fa("• طراحی‌شده با سایز عمودی ایستاده (۱۰۸۰×۱۹۲۰) جهت مطالعه آسان در موبایل"), fill=(120, 130, 140), font=load_font(16), anchor="rm")

    if isinstance(output_path, (str, os.PathLike)):
        parent_dir = os.path.dirname(os.path.abspath(output_path))
        if parent_dir:
            os.makedirs(parent_dir, exist_ok=True)
    img.save(output_path, "PNG", dpi=(150, 150))
    return img

print("image_generator.py ready (1080x1920 mobile optimized)")
