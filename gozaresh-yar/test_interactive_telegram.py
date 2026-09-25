# -*- coding: utf-8 -*-
"""
آزمون خودکار مسیر تعاملی در حالت «فقط خروجی تلگرام» (شبیه‌سازی کاربر واقعی).
اجرا:  python3 test_interactive_telegram.py      (نیازمند pexpect)
"""
import glob
import os
import sys
import time

import pexpect

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, "..", "گزارش‌های-ساخته‌شده"))
CHANNEL = os.path.join(HERE, "demo-input", "channel")


def main() -> int:
    if not os.path.exists(os.path.join(CHANNEL, "result.json")):
        os.system(f"{sys.executable} {os.path.join(HERE, 'make_demo_channel.py')}")
    if os.path.isdir(OUT):
        for f in glob.glob(os.path.join(OUT, "*")):
            try:
                os.remove(f)
            except OSError:
                pass
    # تنظیمات قبلی هوش مصنوعی نباید روی این آزمون اثر بگذارد
    for f in ("ai-settings.json", ".ai-cache.json"):
        p = os.path.join(HERE, f)
        if os.path.exists(p):
            os.remove(p)

    child = pexpect.spawn(sys.executable, ["gozaresh.py"], cwd=HERE,
                          encoding="utf-8", timeout=180, dimensions=(40, 140))
    child.logfile_read = sys.stdout

    def send_when(pattern, keys, delay=0.6):
        child.expect(pattern)
        time.sleep(delay)
        child.send(keys)

    send_when("گام ۱", "demo-input/channel\r")        # پوشه‌ی خروجی تلگرام
    send_when("از تاریخ", "شهریور\r")                  # بازه: شهریور ۱۴۰۵
    send_when("تا تاریخ", "شهریور\r")
    send_when("هوش مصنوعی فعال شود", "\r")            # خیر (بدون هوش مصنوعی)
    send_when("گام ۴", "\r")                          # تأیید دوره‌ها
    send_when("گام ۵", "\r")                          # همه‌ی نواحی
    send_when("گام ۶", "\r")                          # دسته‌های پیش‌فرض
    send_when("گام ۷", "\r")                          # قالب ۱ (سیمای کلی)
    send_when("عنوان گزارش", "\r")                    # عنوان پیش‌فرض                          # قالب ۱ (سیمای کلی)
    send_when("متن درخواست مدیر", "گزارش حداکثر 600 کلمه باشد\r")
    send_when("سقف واژه", "\r")
    send_when("سقف کاراکتر", "\r")
    send_when("پیوست لینک", "\r")                      # خیر
    send_when("فایل Word", "\r")                       # Word: بله
    send_when("فایل Excel", "y\r")                     # Excel: بله
    send_when("پوشه‌ی خروجی", "\r")                     # پیش‌فرض
    send_when("مسیر لوگو", "\r")                        # بدون لوگو
    send_when("گزارش آماده شد", "\r")                   # باز شدن مرورگر
    child.expect(pexpect.EOF)

    made = sorted(glob.glob(os.path.join(OUT, "*")))
    names = [os.path.basename(m) for m in made]
    print("\n\n===== خروجی‌های ساخته‌شده =====")
    for n in names:
        print(" •", n)

    ok = (any(n.endswith(".md") for n in names) and any(n.endswith(".docx") for n in names)
          and any(n.endswith(".xlsx") for n in names) and any(n.endswith(".html") for n in names))
    if ok:
        md = [m for m in made if m.endswith(".md")][0]
        text = open(md, encoding="utf-8").read()
        if "خروجی JSON کانال تلگرام" not in text:
            print("\n✖ در متن گزارش، «منبع داده» تلگرام ثبت نشده است.")
            ok = False
        words = len(text.split())
        if words > 600:
            print(f"\n✖ سقف واژه رعایت نشد: {words} واژه.")
            ok = False
        else:
            print(f"\n• طول گزارش: {words} واژه (سقف ۶۰۰) — رعایت شد.")
    print("\nنتیجه‌ی آزمون تعاملی (حالت تلگرام):", "موفق ✅" if ok else "ناموفق ❌")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
