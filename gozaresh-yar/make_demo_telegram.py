# -*- coding: utf-8 -*-
"""
ساخت نمونه‌ی «Export تلگرام» (فرضی) برای آزمایش قابلیت بازخوانی متن پست‌ها.
خروجی: demo-input/telegram/result.json  (قالب استاندارد Telegram Desktop)
این فایل صرفاً آزمایشی است و هیچ پست واقعی در آن وجود ندارد.
"""
import json
import os
import random

random.seed(11)
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "demo-input", "telegram")
os.makedirs(OUT, exist_ok=True)

BODIES = [
    "واکنش سریع به شایعه‌ی منتشرشده در فضای مجازی درباره‌ی قطعی اینترنت؛ استاد گودرزی در جمع ۴۰ نفره‌ی دانش‌آموزان",
    "پویش «هر خانه یک رسانه» با مشارکت فعال مدارس و تولید محتوای دانش‌آموزی آغاز شد",
    "همکاری مشترک با آموزش و پرورش برای برگزاری کارگاه سواد رسانه در ۵ مدرسه",
    "همایش بزرگ سواد رسانه با حضور مدیران و فعالان رسانه‌ای شهرستان برگزار شد",
    "تولید اینفوگرافیک آموزشی با موضوع جنگ شناختی و انتشار در کانال‌های رسمی",
    "برگزاری جلسه هماهنگی ماهانه با حضور مسئولان نواحی و بررسی عملکرد",
    "بازدید نظارتی از روند برگزاری کلاس‌های سواد رسانه در سطح حوزه‌ها",
    "کارگاه هوش مصنوعی و تولید محتوا ویژه‌ی فعالان فضای مجازی برگزار گردید",
]

messages = []
for i, body in enumerate(BODIES * 40, start=100):
    messages.append({
        "id": i,
        "type": "message",
        "date": "2026-08-25T10:00:00",
        "date_unixtime": str(1756112400 + i),
        "text": body,
    })

with open(os.path.join(OUT, "result.json"), "w", encoding="utf-8") as f:
    json.dump({"name": "soad_resaneh_isfahan", "type": "public_channel", "id": 1,
               "messages": messages}, f, ensure_ascii=False, indent=1)

print("ساخته شد:", os.path.join(OUT, "result.json"), "- تعداد پست:", len(messages))
