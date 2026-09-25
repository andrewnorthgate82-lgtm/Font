# -*- coding: utf-8 -*-
"""
ساخت نمونه‌ی واقع‌نما از «Export کانال تلگرام» (فرضی) برای آزمودن حالت
«تحلیل متن پست‌ها» — خروجی: demo-input/channel/result.json

این داده کاملاً فرضی است و هیچ ارتباطی با کانال واقعی ندارد.
"""
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gozaresh import jalali_to_unix  # noqa: E402  (فقط برای تبدیل تاریخ)

random.seed(23)
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "demo-input", "channel")
os.makedirs(OUT, exist_ok=True)

COUNTIES = ["آران و بیدگل", "کاشان", "شهرضا", "نجف آباد", "خمینی شهر", "لنجان",
            "گلپایگان", "اردستان"]
TEACHERS = ["حمید گودرزی", "مریم رضایی", "علی محمدی", "زهرا کریمی", "سعید نوری"]
PLACES = ["مسجد جامع", "دبیرستان شهید بهشتی", "حسینیه اعظم", "سالن اجتماعات ناحیه",
          "پایگاه مقاومت", "مدرسه شهید فهمیده", "کانون فرهنگی هنری"]
TOPICS = ["سواد رسانه", "جنگ شناختی", "آموزش هوش مصنوعی", "آموزش تولید محتوا",
          "امنیت سایبری", "آسیب‌های شبکه‌های اجتماعی", "سواد رسانه و فضای مجازی"]

TEMPLATES_CLASS = [
    "برگزاری کارگاه {topic} در ناحیه {county} با حضور {n} نفر از دانش‌آموزان و تدریس {teacher} در {place}",
    "ناحیه {county} برگزار کرد: کلاس {topic} با شرکت {n} نفر — مدرس: {teacher} — مکان: {place}",
    "با همت ناحیه {county}، دوره‌ی {topic} با مشارکت {n} نفر و تدریس {teacher} در {place} برگزار شد",
]
TEMPLATES_LIVE = [
    "لایو آموزشی {topic} در بستر ایتا برای ناحیه {county} با {n} نفر مخاطب برگزار شد؛ مدرس {teacher}",
    "پخش زنده‌ی کارگاه {topic} ناحیه {county} با {n} نفر بازدید؛ تدریس {teacher}",
]
TEMPLATES_PROD = [
    "تولید کلیپ {topic} توسط ناحیه {county} با موضوع {topic} — {n} نفر بازدید",
    "پوستر و اینفوگرافیک {topic} با همکاری ناحیه {county} منتشر شد — {n} بازدید",
]
TEMPLATES_CAMPAIGN = [
    "پویش «هر خانه یک رسانه» با مشارکت {n} دانش‌آموز در ناحیه {county} آغاز شد",
    "مسابقه‌ی تولید محتوا با موضوع {topic} در ناحیه {county} با {n} نفر شرکت‌کننده برگزار گردید",
]
TEMPLATES_SYNERGY = [
    "کارگاه مشترک {topic} با همکاری آموزش و پرورش و ناحیه {county} با حضور {n} نفر برگزار شد",
    "تفاهم‌نامه‌ی همکاری با اداره‌ی فرهنگ و ارشاد و ناحیه {county} برای برگزاری دوره‌ی {topic} امضا شد؛ {n} نفر مخاطب هدف",
]
TEMPLATES_CRISIS = [
    "واکنش سریع به شایعه‌ی منتشرشده در فضای مجازی درباره‌ی {county}؛ تیم سواد رسانه با {n} نفر مخاطب روشنگری کرد",
    "جلسه‌ی فوق‌العاده‌ی مقابله با هجمه‌ی رسانه‌ای در ناحیه {county} با حضور {n} نفر فعال رسانه‌ای برگزار شد",
]
TEMPLATES_NODATA = [
    "اطلاعیه: زمان‌بندی کلاس‌های سواد رسانه‌ی هفته‌ی آینده اعلام می‌شود.",
    "گزارش تصویری فعالیت‌های فرهنگی هفته‌ی گذشته در کانال بارگذاری شد.",
    "یادآوری: مسئولان نواحی، مستندات را در سامانه بارگذاری کنند.",
]


def make_text() -> str:
    county = random.choice(COUNTIES)
    teacher = random.choice(TEACHERS)
    place = random.choice(PLACES)
    topic = random.choice(TOPICS)
    n = random.choice(["35", "۴۲", "60", "۷۵", "120", "۴۵۰", "80", "۹۵"])
    roll = random.random()
    if roll < 0.45:
        tpl = random.choice(TEMPLATES_CLASS)
    elif roll < 0.60:
        tpl = random.choice(TEMPLATES_LIVE)
    elif roll < 0.72:
        tpl = random.choice(TEMPLATES_PROD)
    elif roll < 0.84:
        tpl = random.choice(TEMPLATES_CAMPAIGN)
    elif roll < 0.93:
        tpl = random.choice(TEMPLATES_SYNERGY)
    else:
        tpl = random.choice(TEMPLATES_CRISIS)
    return tpl.format(county=county, teacher=teacher, place=place, topic=topic, n=n)


messages = []
msg_id = 1000
# شهریور ۱۴۰۵ + چند روز از مرداد و مهر (برای آزمودن فیلتر بازه)
plan = [(1405, 5, 25, 6), (1405, 6, 1, 40), (1405, 7, 2, 6)]
for jy, jm, jd_start, count in plan:
    for i in range(count):
        jd = min(28, jd_start + i % 28)
        ts = jalali_to_unix(jy, jm, jd) + random.randint(8 * 3600, 20 * 3600)
        if random.random() < 0.12:
            text = random.choice(TEMPLATES_NODATA)
        else:
            text = make_text()
        messages.append({
            "id": msg_id,
            "type": "message",
            "date": "2026-08-25T10:00:00",
            "date_unixtime": str(ts),
            "text": text,
        })
        msg_id += 1

messages.sort(key=lambda m: int(m["date_unixtime"]))
data = {
    "name": "soad_resaneh_isfahan",
    "type": "public_channel",
    "id": 1234567890,
    "username": "soad_resaneh_isfahan",
    "messages": messages,
}
path = os.path.join(OUT, "result.json")
with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=1)
print("ساخته شد:", path, "| تعداد پست:", len(messages))
