# -*- coding: utf-8 -*-
"""
آزمون خودکار مسیر تعاملی (شبیه‌سازی کاربر واقعی با صفحه‌کلید).
اجرا:  python3 test_interactive.py
"""
import glob
import os
import shutil
import sys
import tempfile
import time

import pexpect

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(tempfile.gettempdir(), "gozaresh-test-out")   # پوشه‌ی موقت آزمون


def main() -> int:
    if os.path.isdir(OUT):
        for f in glob.glob(os.path.join(OUT, "*")):
            try:
                os.remove(f)
            except OSError:
                pass

    child = pexpect.spawn(sys.executable, ["gozaresh.py"], cwd=HERE,
                          encoding="utf-8", timeout=120, dimensions=(40, 140))
    child.logfile_read = sys.stdout

    def send_when(pattern, keys, delay=0.6):
        child.expect(pattern)
        time.sleep(delay)
        child.send(keys)

    send_when("گام ۱", "\r")                       # مسیر پیشنهادی داده
    send_when("منبع داده", "\r")                    # اکسل (پیش‌فرض)
    send_when("گام ۳", "\r")                       # هوش مصنوعی: خیر
    send_when("گام ۴", "\r")                       # تأیید دوره‌ها
    send_when("گام ۵", "\r")                       # همه‌ی نواحی
    send_when("گام ۶", "\r")                       # دسته‌های پیش‌فرض
    send_when("گام ۷", "\r")                          # قالب ۱ (سیمای کلی)
    send_when("عنوان گزارش", "\r")                    # عنوان پیش‌فرض                       # قالب ۱ (سیمای کلی)
    send_when("متن درخواست مدیر", "گزارش حداکثر 500 کلمه باشد\r")
    send_when("سقف واژه", "\r")
    send_when("سقف کاراکتر", "\r")
    send_when("پیوست لینک", "\r")                   # خیر
    send_when("فایل Word", "\r")                    # Word: بله
    send_when("فایل Excel", "y\r")                  # Excel: بله
    send_when("پوشه‌ی خروجی", OUT + "\r")          # پوشه‌ی موقت آزمون
    send_when("مسیر لوگو", "\r")                     # بدون لوگو
    send_when("گزارش آماده شد", "\r")                # باز شدن مرورگر
    child.expect(pexpect.EOF)

    made = sorted(glob.glob(os.path.join(OUT, "*")))
    names = [os.path.basename(m) for m in made]
    print("\n\n===== خروجی‌های ساخته‌شده =====")
    for n in names:
        print(" •", n)
    ok = (any(n.endswith(".md") for n in names) and any(n.endswith(".docx") for n in names)
          and any(n.endswith(".xlsx") for n in names) and any(n.endswith(".html") for n in names))
    print("\nنتیجه‌ی آزمون تعاملی:", "موفق ✅" if ok else "ناموفق ❌")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
