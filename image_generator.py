# -*- coding: utf-8 -*-
"""
========================================================================
ماژول تولید تصاویر بهینه‌شده ویژه تلفن همراه (1080 × 1920 ایستاده - عمودی)
کارنامه هوشمند و داشبورد مانیتورینگ عملکرد نواحی نسرا - استان اصفهان
با متن‌های پررنگ (Bold)، خوانا و باکنتراست بالا ویژه نمایشگرهای موبایل
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
    # Remove emojis that might cause missing-glyph tofu boxes in standard fonts
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

def draw_t(draw, xy, text, fill, font, anchor="la", bold=False, stroke_w=0):
    t = fa(text)
    sw = stroke_w
    if bold and sw == 0:
        sw = 1
    if sw > 0:
        draw.text(xy, t, fill=fill, font=font, anchor=anchor, stroke_width=sw, stroke_fill=fill)
    else:
        draw.text(xy, t, fill=fill, font=font, anchor=anchor)

def draw_progressbar(draw, x, y, w, h, pct, fill_color, bg_color=(230, 235, 240), radius=7):
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

    # 1. Header Banner (Y: 0 to 235)
    draw.rectangle([0, 0, width, 235], fill=(20, 45, 85))
    draw.rectangle([0, 235, width, 243], fill=(37, 99, 235))
    
    draw_t(draw, (width//2, 50), "نهضت سواد رسانه‌ای انقلاب اسلامی (نسرا) - استان اصفهان", (224, 242, 254), load_font(23), anchor="mm", bold=True)
    draw_t(draw, (width//2, 118), "کارنامه هوشمند ارزیابی عملکرد ماهانه", (255, 255, 255), load_font(42), anchor="mm", bold=True, stroke_w=1)
    sub_title = f"شهرستان: {district_name}  |  دوره ارزیابی: ماه {month} ۱۴۰۵"
    draw_t(draw, (width//2, 185), sub_title, (254, 240, 138), load_font(28), anchor="mm", bold=True, stroke_w=1)

    # Score & status
    score_val = actual_dict.get('overall_score', 0)
    score_str = f"{score_val:.1f}%" if isinstance(score_val, (int, float)) else str(score_val)
    is_zero = (isinstance(score_val, (int, float)) and score_val == 0)

    # 2. Executive Summary Cards (Y: 265 to 460) - RTL arranged
    card_w = 306
    card_h = 195
    y_cards = 265
    
    # Right Card: Score (Primary RTL focus)
    c_right_x = 718
    c1_bg = (248, 250, 252) if is_zero else (239, 246, 255)
    c1_out = (148, 163, 184) if is_zero else (37, 99, 235)
    c1_lbl = (100, 116, 139) if is_zero else (30, 64, 175)
    c1_txt = (100, 116, 139) if is_zero else (15, 23, 42)
    draw.rounded_rectangle([c_right_x, y_cards, c_right_x + card_w, y_cards + card_h], radius=14, fill=c1_bg, outline=c1_out, width=2)
    draw_t(draw, (c_right_x + card_w//2, y_cards + 35), "میانگین تحقق اهداف", c1_lbl, load_font(21), anchor="mm", bold=True)
    draw_t(draw, (c_right_x + card_w//2, y_cards + 105), score_str, c1_txt, load_font(56), anchor="mm", bold=True, stroke_w=1)
    draw_t(draw, (c_right_x + card_w//2, y_cards + 162), "کل شاخص‌های پنج‌گانه", (71, 85, 105), load_font(19), anchor="mm", bold=True)

    # Center Card: Rank
    c_mid_x = 387
    c2_bg = (254, 242, 242) if is_zero else (236, 253, 245)
    c2_out = (239, 68, 68) if is_zero else (16, 185, 129)
    c2_lbl = (185, 28, 28) if is_zero else (4, 120, 87)
    c2_txt = (153, 27, 27) if is_zero else (6, 95, 70)
    draw.rounded_rectangle([c_mid_x, y_cards, c_mid_x + card_w, y_cards + card_h], radius=14, fill=c2_bg, outline=c2_out, width=2)
    draw_t(draw, (c_mid_x + card_w//2, y_cards + 35), "رتبه در استان", c2_lbl, load_font(21), anchor="mm", bold=True)
    rank_disp = "عدم فعالیت" if is_zero else (f"رتبه {rank}" if "رتبه" not in str(rank) else str(rank))
    draw_t(draw, (c_mid_x + card_w//2, y_cards + 105), rank_disp, c2_txt, load_font(38), anchor="mm", bold=True, stroke_w=1)
    rank_sub = "فاقد گزارش ماهانه" if is_zero else "از میان ۳۲ شهرستان"
    draw_t(draw, (c_mid_x + card_w//2, y_cards + 162), rank_sub, (71, 85, 105), load_font(19), anchor="mm", bold=True)

    # Left Card: Tier
    c_left_x = 55
    c3_bg = (248, 250, 252) if is_zero else ((254, 252, 232) if ("عالی" in tier or "خوب" in tier) else (254, 242, 242))
    c3_out = (148, 163, 184) if is_zero else ((234, 179, 8) if ("عالی" in tier or "خوب" in tier) else (239, 68, 68))
    c3_lbl = (100, 116, 139) if is_zero else ((161, 98, 7) if ("عالی" in tier or "خوب" in tier) else (185, 28, 28))
    c3_txt = (100, 116, 139) if is_zero else ((133, 77, 14) if ("عالی" in tier or "خوب" in tier) else (153, 27, 27))
    draw.rounded_rectangle([c_left_x, y_cards, c_left_x + card_w, y_cards + card_h], radius=14, fill=c3_bg, outline=c3_out, width=2)
    draw_t(draw, (c_left_x + card_w//2, y_cards + 35), "سطح کیفی عملکرد", c3_lbl, load_font(21), anchor="mm", bold=True)
    tier_disp = "فاقد عملکرد" if is_zero else tier
    draw_t(draw, (c_left_x + card_w//2, y_cards + 105), tier_disp, c3_txt, load_font(26), anchor="mm", bold=True, stroke_w=1)
    b_count = target_dict.get('branches', 0)
    draw_t(draw, (c_left_x + card_w//2, y_cards + 162), f"{b_count} حوزه مقاومت", (71, 85, 105), load_font(19), anchor="mm", bold=True)

    # 3. Overall Progress Bar Card (Y: 485 to 590)
    bar_y = 485
    draw.rounded_rectangle([55, bar_y, 1025, bar_y + 105], radius=12, fill=(255, 255, 255), outline=(203, 213, 225), width=2)
    draw_t(draw, (1000, bar_y + 32), "درصد پیشرفت کل اهداف ابلاغی شهرستان:", (15, 23, 42), load_font(22), anchor="rm", bold=True)
    draw_t(draw, (80, bar_y + 32), score_str, (15, 23, 42), load_font(28), anchor="lm", bold=True, stroke_w=1)
    bar_color = (148, 163, 184) if is_zero else ((16, 185, 129) if score_val >= 100 else ((37, 99, 235) if score_val >= 75 else ((234, 179, 8) if score_val >= 50 else (239, 68, 68))))
    draw_progressbar(draw, 80, bar_y + 60, 920, 24, score_val, bar_color, radius=12)

    # 4. Indicators Section (Y: 615 to 1420)
    sec_y = 615
    draw.rectangle([55, sec_y, 1025, sec_y + 58], fill=(30, 58, 110))
    draw_t(draw, (width//2, sec_y + 29), "ریز عملکرد شاخص‌های پنج‌گانه ابلاغی (بر مبنای مجموع تعداد نفرات)", (255, 255, 255), load_font(25), anchor="mm", bold=True, stroke_w=1)

    indicators = [
        (1, "سواد رسانه حضوری و توانمندسازی (۳۱×)", target_dict.get('hozori', 0), actual_dict.get('hozori', 0)),
        (2, "سواد رسانه مجازی و لایو (۲۱۷×)", target_dict.get('majazi', 0), actual_dict.get('majazi', 0)),
        (3, "اقدامات و ابتکارات خلاقانه (۶۲×)", target_dict.get('khalagh', 0), actual_dict.get('khalagh', 0)),
        (4, "تولیدات رسانه‌ای و محتوایی (۳×)", target_dict.get('tolid', 0), actual_dict.get('tolid', 0)),
        (5, "نشست دبیر نسرا با انجمن مدرسان", target_dict.get('neshast', 1), actual_dict.get('neshast', 0))
    ]

    card_start_y = sec_y + 72
    row_h = 138
    row_gap = 14

    for idx, name, tgt, act in indicators:
        curr_y = card_start_y + (idx - 1) * (row_h + row_gap)
        pct = (act / tgt * 100) if tgt > 0 else 0
        diff = act - tgt
        diff_str = f"+{diff:,}" if diff > 0 else f"{diff:,}"

        draw.rounded_rectangle([55, curr_y, 1025, curr_y + row_h], radius=12, fill=(255, 255, 255), outline=(203, 213, 225), width=2)
        
        # Line 1: Bold Dark Title
        draw_t(draw, (1000, curr_y + 35), f"{idx}. {name}", (15, 23, 42), load_font(24), anchor="rm", bold=True, stroke_w=0.8)
        
        # Badge
        if pct == 0:
            b_bg, b_txt = (241, 245, 249), (100, 116, 139)
        elif pct >= 100:
            b_bg, b_txt = (209, 250, 229), (4, 120, 87)
        elif pct >= 75:
            b_bg, b_txt = (224, 242, 254), (3, 105, 161)
        elif pct >= 50:
            b_bg, b_txt = (254, 243, 199), (180, 83, 9)
        else:
            b_bg, b_txt = (254, 226, 226), (185, 28, 28)

        badge_w, badge_h = 150, 38
        draw.rounded_rectangle([80, curr_y + 16, 80 + badge_w, curr_y + 16 + badge_h], radius=8, fill=b_bg, outline=b_txt, width=1)
        draw_t(draw, (80 + badge_w//2, curr_y + 16 + badge_h//2), f"{pct:.1f}% تحقق", b_txt, load_font(21), anchor="mm", bold=True, stroke_w=0.8)

        # Line 2: Bold High-Contrast Details Line
        unit = "نشست" if "نشست" in name else "نفر"
        meta_txt = f"حد انتظار: {int(tgt):,} {unit}   |   عملکرد واقعی: {int(act):,} {unit}   |   انحراف از هدف: {diff_str} {unit}"
        draw_t(draw, (1000, curr_y + 76), meta_txt, (30, 41, 59), load_font(22), anchor="rm", bold=True)

        # Line 3: Thick Progress bar
        row_bar_col = (148, 163, 184) if pct == 0 else ((16, 185, 129) if pct >= 100 else ((37, 99, 235) if pct >= 75 else ((234, 179, 8) if pct >= 50 else (239, 68, 68))))
        draw_progressbar(draw, 80, curr_y + 107, 920, 16, pct, row_bar_col, radius=8)

    # 5. Organizational Profile Section (Y: 1455 to 1700)
    prof_y = 1455
    draw.rounded_rectangle([55, prof_y, 1025, prof_y + 240], radius=14, fill=(255, 255, 255), outline=(203, 213, 225), width=2)
    draw.rounded_rectangle([55, prof_y, 1025, prof_y + 52], radius=14, fill=(239, 246, 255))
    draw.rectangle([55, prof_y + 26, 1025, prof_y + 52], fill=(239, 246, 255))
    draw_t(draw, (1000, prof_y + 26), "مشخصات تشکیلاتی و مبانی سنجش کارنامه ناحیه:", (30, 64, 175), load_font(23), anchor="rm", bold=True, stroke_w=0.8)

    b_status = "عدم ارسال گزارش ماهانه در این دوره (عملکرد صفر منظور شد)" if is_zero else "گزارش ماهانه رسمی دریافت و در سامانه ثبت شد"
    profile_bullets = [
        f"• تعداد حوزه‌های مقاومت تابعه: {target_dict.get('branches', 0)} حوزه مقاومت",
        "• حد انتظار اعضای انجمن مدرسان شهرستان: ۲۲ نفر",
        "• ملاک قطعی محاسبه شاخص‌ها: مجموع «تعداد نفرات» شرکت‌کننده ثبت‌شده در گزارش‌های ماهانه",
        f"• وضعیت گزارش دوره: {b_status}"
    ]

    for b_idx, bullet in enumerate(profile_bullets):
        draw_t(draw, (1000, prof_y + 82 + b_idx * 38), bullet, (15, 23, 42), load_font(21), anchor="rm", bold=True)

    # 6. Official Footer & Verification Stamp (Y: 1725 to 1890)
    foot_y = 1725
    draw.rounded_rectangle([55, foot_y, 1025, foot_y + 155], radius=14, fill=(255, 255, 255), outline=(37, 99, 235), width=2)
    
    draw.rounded_rectangle([80, foot_y + 18, 380, foot_y + 138], radius=10, fill=(236, 253, 245), outline=(16, 185, 129), width=2)
    draw_t(draw, (230, foot_y + 48), "دبیرخانه نهضت سواد رسانه‌ای", (15, 23, 42), load_font(20), anchor="mm", bold=True, stroke_w=0.8)
    draw_t(draw, (230, foot_y + 82), "استان اصفهان - کارنامه رسمی", (5, 150, 105), load_font(20), anchor="mm", bold=True, stroke_w=0.8)
    draw_t(draw, (230, foot_y + 112), "[ مورد تأیید مراجع استانی ]", (30, 41, 59), load_font(17), anchor="mm", bold=True)

    draw_t(draw, (1000, foot_y + 45), f"کارنامه رسمی ارزیابی عملکرد ماه «{month}» سال ۱۴۰۵", (15, 23, 42), load_font(22), anchor="rm", bold=True, stroke_w=0.8)
    draw_t(draw, (1000, foot_y + 85), "صادره از سامانه جامع مانیتورینگ عملکرد ۳۲ شهرستان استان اصفهان", (51, 65, 85), load_font(19), anchor="rm", bold=True)
    draw_t(draw, (1000, foot_y + 120), "• بهینه‌شده با ابعاد ایستاده (۱۰۸۰×۱۹۲۰) ویژه مطالعه آسان در گوشی و پیام‌رسان‌ها", (71, 85, 105), load_font(17), anchor="rm", bold=True)

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

    # 1. Header Banner (Y: 0 to 230)
    draw.rectangle([0, 0, width, 230], fill=(20, 45, 85))
    draw.rectangle([0, 230, width, 238], fill=(37, 99, 235))
    
    draw_t(draw, (width//2, 48), "نهضت سواد رسانه‌ای انقلاب اسلامی (نسرا) - استان اصفهان", (224, 242, 254), load_font(22), anchor="mm", bold=True)
    draw_t(draw, (width//2, 112), "داشبورد مدیریتی مانیتورینگ عملکرد نواحی", (255, 255, 255), load_font(38), anchor="mm", bold=True, stroke_w=1)
    dash_sub = f"دوره ارزیابی: ماه {month} ۱۴۰۵  |  پایش جامع ۳۲ شهرستان و ۲۲۹ حوزه مقاومت"
    draw_t(draw, (width//2, 178), dash_sub, (254, 240, 138), load_font(26), anchor="mm", bold=True, stroke_w=1)

    # 2. Executive KPI Cards (2x2 Grid) (Y: 255 to 520) - RTL arranged
    c_w = 465
    c_h = 120
    row1_y = 255
    row2_y = 390
    col_right_x = 560
    col_left_x = 55

    # Top-Right: Formation Coverage
    draw.rounded_rectangle([col_right_x, row1_y, col_right_x + c_w, row1_y + c_h], radius=14, fill=(255, 255, 255), outline=(203, 213, 225), width=2)
    draw_t(draw, (col_right_x + c_w//2, row1_y + 35), "پوشش تشکیلاتی استان", (71, 85, 105), load_font(20), anchor="mm", bold=True)
    draw_t(draw, (col_right_x + c_w//2, row1_y + 82), "۳۲ ناحیه  |  ۲۲۹ حوزه", (15, 23, 42), load_font(32), anchor="mm", bold=True, stroke_w=0.8)

    # Top-Left: Provincial Average
    draw.rounded_rectangle([col_left_x, row1_y, col_left_x + c_w, row1_y + c_h], radius=14, fill=(239, 246, 255), outline=(37, 99, 235), width=2)
    draw_t(draw, (col_left_x + c_w//2, row1_y + 35), f"میانگین عملکرد استان ({month})", (30, 64, 175), load_font(20), anchor="mm", bold=True)
    draw_t(draw, (col_left_x + c_w//2, row1_y + 82), f"{kpi_data.get('avg_score', 0):.1f}%", (15, 23, 42), load_font(44), anchor="mm", bold=True, stroke_w=1)

    # Bottom-Right: Top Performer
    draw.rounded_rectangle([col_right_x, row2_y, col_right_x + c_w, row2_y + c_h], radius=14, fill=(236, 253, 245), outline=(16, 185, 129), width=2)
    draw_t(draw, (col_right_x + c_w//2, row2_y + 35), "پیشتاز استان (رتبه ۱)", (4, 120, 87), load_font(20), anchor="mm", bold=True)
    draw_t(draw, (col_right_x + c_w//2, row2_y + 82), kpi_data.get('top_district', 'کاشان'), (6, 95, 70), load_font(34), anchor="mm", bold=True, stroke_w=0.8)

    # Bottom-Left: Reports Status
    draw.rounded_rectangle([col_left_x, row2_y, col_left_x + c_w, row2_y + c_h], radius=14, fill=(254, 252, 232), outline=(234, 179, 8), width=2)
    draw_t(draw, (col_left_x + c_w//2, row2_y + 35), "وضعیت ارسال گزارش نواحی", (161, 98, 7), load_font(20), anchor="mm", bold=True)
    draw_t(draw, (col_left_x + c_w//2, row2_y + 82), f"{kpi_data.get('reported_count', 32)} از ۳۲ ناحیه فعال", (133, 77, 14), load_font(28), anchor="mm", bold=True, stroke_w=0.8)

    # 3. Macro Indicators Section (Y: 535 to 1180)
    sec_y = 535
    draw.rectangle([55, sec_y, 1025, sec_y + 54], fill=(30, 58, 110))
    draw_t(draw, (width//2, sec_y + 27), f"۱. جدول و نمودار تحقق شاخص‌های کلان در کل استان اصفهان - {month} ۱۴۰۵", (255, 255, 255), load_font(24), anchor="mm", bold=True, stroke_w=1)

    m_start_y = sec_y + 68
    m_h = 108
    m_gap = 12

    for idx, (m_name, m_tgt, m_act) in enumerate(macro_data, start=1):
        curr_y = m_start_y + (idx - 1) * (m_h + m_gap)
        pct = (m_act / m_tgt * 100) if m_tgt > 0 else 0
        diff = m_act - m_tgt
        diff_str = f"+{diff:,}" if diff > 0 else f"{diff:,}"

        draw.rounded_rectangle([55, curr_y, 1025, curr_y + m_h], radius=12, fill=(255, 255, 255), outline=(203, 213, 225), width=2)
        
        # Line 1: Bold Dark Title
        draw_t(draw, (1000, curr_y + 30), f"{idx}. {m_name}", (15, 23, 42), load_font(23), anchor="rm", bold=True, stroke_w=0.8)
        
        pct_color = (4, 120, 87) if pct >= 100 else ((3, 105, 161) if pct >= 75 else ((180, 83, 9) if pct >= 50 else (185, 28, 28)))
        pct_bg = (209, 250, 229) if pct >= 100 else ((224, 242, 254) if pct >= 75 else ((254, 243, 199) if pct >= 50 else (254, 226, 226)))
        draw.rounded_rectangle([80, curr_y + 12, 220, curr_y + 46], radius=7, fill=pct_bg, outline=pct_color, width=1)
        draw_t(draw, (150, curr_y + 29), f"{pct:.1f}% تحقق", pct_color, load_font(20), anchor="mm", bold=True, stroke_w=0.8)

        # Line 2: Bold Details
        meta_txt = f"حد انتظار استان: {int(m_tgt):,} نفر   |   عملکرد واقعی: {int(m_act):,} نفر   |   انحراف: {diff_str} نفر"
        draw_t(draw, (1000, curr_y + 64), meta_txt, (30, 41, 59), load_font(21), anchor="rm", bold=True)

        # Line 3: Thick Progress bar
        bar_col = (16, 185, 129) if pct >= 100 else ((37, 99, 235) if pct >= 75 else ((234, 179, 8) if pct >= 50 else (239, 68, 68)))
        draw_progressbar(draw, 80, curr_y + 88, 920, 14, pct, bar_col, radius=7)

    # 4. Top 5 & Bottom 5 Leaderboards (Y: 1205 to 1715) - RTL arranged
    lead_y = 1205
    col_w = 465
    
    # Right Box: Top 5 (Primary RTL)
    draw.rounded_rectangle([col_right_x, lead_y, col_right_x + col_w, lead_y + 495], radius=14, fill=(255, 255, 255), outline=(16, 185, 129), width=2)
    draw.rounded_rectangle([col_right_x, lead_y, col_right_x + col_w, lead_y + 56], radius=14, fill=(5, 150, 105))
    draw.rectangle([col_right_x, lead_y + 30, col_right_x + col_w, lead_y + 56], fill=(5, 150, 105))
    draw_t(draw, (col_right_x + col_w//2, lead_y + 28), "۵ ناحیه برتر و پیشتاز استان", (255, 255, 255), load_font(24), anchor="mm", bold=True, stroke_w=0.8)

    # Left Box: Bottom 5
    draw.rounded_rectangle([col_left_x, lead_y, col_left_x + col_w, lead_y + 495], radius=14, fill=(255, 255, 255), outline=(239, 68, 68), width=2)
    draw.rounded_rectangle([col_left_x, lead_y, col_left_x + col_w, lead_y + 56], radius=14, fill=(220, 38, 38))
    draw.rectangle([col_left_x, lead_y + 30, col_left_x + col_w, lead_y + 56], fill=(220, 38, 38))
    draw_t(draw, (col_left_x + col_w//2, lead_y + 28), "۵ ناحیه نیازمند پیگیری و تقویت", (255, 255, 255), load_font(24), anchor="mm", bold=True, stroke_w=0.8)

    curr_item_y = lead_y + 68
    item_h = 76

    for k in range(5):
        t_row = top5_data[k] if k < len(top5_data) else (k+1, "در انتظار", 0)
        b_row = bottom5_data[k] if k < len(bottom5_data) else (k+1, "در انتظار", 0)

        # Top 5 item (on Right column)
        row_bg_t = (240, 253, 244) if k % 2 == 0 else (255, 255, 255)
        draw.rounded_rectangle([col_right_x + 10, curr_item_y, col_right_x + col_w - 10, curr_item_y + item_h], radius=9, fill=row_bg_t)
        
        draw.ellipse([col_right_x + col_w - 60, curr_item_y + 18, col_right_x + col_w - 18, curr_item_y + 60], fill=(5, 150, 105))
        draw_t(draw, (col_right_x + col_w - 39, curr_item_y + 39), str(t_row[0]), (255, 255, 255), load_font(24), anchor="mm", bold=True, stroke_w=0.8)
        
        draw_t(draw, (col_right_x + col_w - 75, curr_item_y + 39), t_row[1], (15, 23, 42), load_font(22), anchor="rm", bold=True)
        draw_t(draw, (col_right_x + 30, curr_item_y + 39), f"{t_row[2]:.1f}%", (4, 120, 87), load_font(23), anchor="lm", bold=True, stroke_w=0.8)

        # Bottom 5 item (on Left column)
        row_bg_b = (254, 242, 242) if k % 2 == 0 else (255, 255, 255)
        draw.rounded_rectangle([col_left_x + 10, curr_item_y, col_left_x + col_w - 10, curr_item_y + item_h], radius=9, fill=row_bg_b)
        
        draw.ellipse([col_left_x + col_w - 60, curr_item_y + 18, col_left_x + col_w - 18, curr_item_y + 60], fill=(220, 38, 38))
        draw_t(draw, (col_left_x + col_w - 39, curr_item_y + 39), str(b_row[0]), (255, 255, 255), load_font(24), anchor="mm", bold=True, stroke_w=0.8)
        
        b_score_disp = f"{b_row[2]:.1f}%" if b_row[2] > 0 else "عدم فعالیت"
        draw_t(draw, (col_left_x + col_w - 75, curr_item_y + 39), b_row[1], (15, 23, 42), load_font(22), anchor="rm", bold=True)
        draw_t(draw, (col_left_x + 30, curr_item_y + 39), b_score_disp, (185, 28, 28), load_font(22), anchor="lm", bold=True, stroke_w=0.8)

        curr_item_y += item_h + 8

    # 5. Footer (Y: 1730 to 1890)
    foot_y = 1730
    draw.rounded_rectangle([55, foot_y, 1025, foot_y + 150], radius=14, fill=(255, 255, 255), outline=(37, 99, 235), width=2)
    
    draw.rounded_rectangle([80, foot_y + 18, 380, foot_y + 132], radius=10, fill=(236, 253, 245), outline=(16, 185, 129), width=2)
    draw_t(draw, (230, foot_y + 46), "دبیرخانه نهضت سواد رسانه‌ای", (15, 23, 42), load_font(20), anchor="mm", bold=True, stroke_w=0.8)
    draw_t(draw, (230, foot_y + 78), "استان اصفهان - گزارش مدیریتی", (5, 150, 105), load_font(20), anchor="mm", bold=True, stroke_w=0.8)
    draw_t(draw, (230, foot_y + 108), "[ نسخه رسمی ویژه مسئولین ]", (30, 41, 59), load_font(17), anchor="mm", bold=True)

    draw_t(draw, (1000, foot_y + 45), f"داشبورد رسمی مانیتورینگ عملکرد ۳۲ شهرستان نسرا - ماه «{month}» ۱۴۰۵", (15, 23, 42), load_font(22), anchor="rm", bold=True, stroke_w=0.8)
    draw_t(draw, (1000, foot_y + 85), "ملاک ارزیابی: مجموع تعداد نفرات شرکت‌کننده در کلاس‌ها و لایوها", (51, 65, 85), load_font(19), anchor="rm", bold=True)
    draw_t(draw, (1000, foot_y + 118), "• طراحی‌شده با سایز عمودی ایستاده (۱۰۸۰×۱۹۲۰) ویژه مطالعه آسان در موبایل", (71, 85, 105), load_font(17), anchor="rm", bold=True)

    if isinstance(output_path, (str, os.PathLike)):
        parent_dir = os.path.dirname(os.path.abspath(output_path))
        if parent_dir:
            os.makedirs(parent_dir, exist_ok=True)
    img.save(output_path, "PNG", dpi=(150, 150))
    return img

print("image_generator.py ready (super bold, 1080x1920 mobile optimized)")
