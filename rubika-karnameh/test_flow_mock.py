#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
test_flow_mock.py — تست خودکارِ منطق اسکریپت «بدون نیاز به مرورگر و روبیکا»
================================================================================
با شبیه‌سازی رفتار صفحه‌ی وب روبیکا (کلاس‌های ساختگی)، منطقِ
«جستجوی مخاطب ← باز کردن گفتگو ← پیوست تصویر ← ارسال» را آزمایش می‌کند.

اجرا:   python test_flow_mock.py
خروجی سالم یعنی منطق اسکریپت سالم است؛ فقط سلکتورهای واقعی صفحه‌ی روبیکا
باید روی سیستم شما با اسکریپت اصلی (و در صورت نیاز --inspect) بررسی شود.
"""
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import send_web as sw


# ---------------------------------------------------------------- شبیه‌ساز صفحه

class El:
    """یک عنصر DOM ساختگی"""

    def __init__(self, tag, text="", attrs=None):
        self.tag, self.text, self.attrs = tag, text, attrs or {}
        self.visible = True

    def click(self):
        self._click()

    def fill(self, v):
        self._fill(v)

    def press(self, key):
        self._press(key)

    def type(self, text, delay=None):
        self._type(text)

    press_sequentially = type


class MockPage:
    """رفتار شبیه روبیکا وب: جستجو ← نتیجه‌ها ← باز شدن چت ← پیوست ← ارسال"""

    def __init__(self, contacts):
        self.contacts = contacts  # {نام مخاطب در نتیجه جستجو: نام در هدر چت}
        self.state = "main"
        self.search_text = ""
        self.chat_with = None
        self.composer_value = ""
        self.attached_file = None
        self.sent = []  # (فایل، کپشن)
        self.no_send_button = False

    # ---------------- ساخت عناصر ----------------
    def _search_box(self):
        el = El("input", self.search_text)
        el._click = lambda: None
        el._fill = lambda v: setattr(self, "search_text", v)
        el._type = lambda t: setattr(self, "search_text", t)
        el._press = lambda k: None
        return el

    def _composer(self):
        el = El("textarea", self.composer_value)
        el._click = lambda: None
        el._fill = lambda v: setattr(self, "composer_value", v)

        def _type(t):
            self.composer_value = (self.composer_value or "") + t

        el._type = _type
        el._press = lambda k: self._send() if self.attached_file else None
        return el

    def _send(self):
        assert self.attached_file, "ارسال بدون فایل؟"
        self.sent.append((self.attached_file, self.composer_value))
        self.attached_file, self.composer_value = None, ""

    # ---------------- رابط playwright ----------------
    def locator(self, sel):
        s = sel.lower()
        if "placeholder*='جستجو'" in sel and self.state == "main":
            return _Single(self._search_box())
        if ("پیام" in sel or "contenteditable" in sel or s == "textarea") and self.state == "chat_open":
            return _Single(self._composer())
        if ("title" in s or "name" in s or sel in ("h1", "h2")) and self.state == "chat_open":
            return _Single(El("h2", self.contacts[self.chat_with]))
        if ("ارسال" in sel or "send" in s) and self.state == "chat_open" \
                and self.attached_file and not self.no_send_button:
            el = El("button")
            el._click = self._send
            return _Single(el)
        if "result" in s or "option" in s or sel.startswith("li"):
            if self.state == "main" and self.search_text:
                q = sw.flat(self.search_text)
                return _List(self, [c for c in self.contacts if q in sw.flat(c)])
            return _List(self, [])
        if sel == "input[type='file']" and self.state == "chat_open":
            return _FileInputs(self)
        return _Single(None)

    def wait_for_timeout(self, ms):
        pass

    def evaluate(self, js, arg=None):
        if "input[type=file]" in js:  # find_attach_input_index
            return 0 if self.state == "chat_open" else -1
        raise AssertionError(f"page.evaluate غیرمنتظره: {js[:60]}")

    def screenshot(self, **kw):
        pass


class _Single:
    def __init__(self, el):
        self.el = el

    @property
    def first(self):
        return self

    def nth(self, i):
        return self if i == 0 else _Single(None)

    def count(self):
        return 1 if self.el else 0

    def is_visible(self):
        return bool(self.el) and self.el.visible

    def inner_text(self, timeout=None):
        return self.el.text if self.el else ""

    def click(self):
        self.el.click()

    def fill(self, v):
        self.el.fill(v)

    def press(self, key):
        self.el.press(key)

    def press_sequentially(self, text, delay=None):
        self.el.type(text)

    def type(self, text, delay=None):
        self.el.type(text)

    def get_attribute(self, name):
        return self.el.attrs.get(name) if self.el else None

    def input_value(self):
        return self.el.text if self.el else ""

    def evaluate(self, js):
        if "tagName" in js:
            return self.el.tag
        return self.el.text or ""


class _List:
    def __init__(self, page, names):
        self.page, self.names = page, names

    def count(self):
        return len(self.names)

    def nth(self, i):
        page, name = self.page, self.names[i]

        def open_chat():
            page.state = "chat_open"
            page.chat_with = name
            page.search_text = ""

        return _ResultItem(name, open_chat)


class _ResultItem:
    def __init__(self, name, click_fn):
        self.name, self._click = name, click_fn

    def is_visible(self):
        return True

    def inner_text(self, timeout=None):
        return self.name + "\n«آخرین پیام…»"

    def click(self):
        self._click()


class _FileInputs:
    def __init__(self, page):
        self.page = page

    def count(self):
        return 1 if self.page.state == "chat_open" else 0

    def nth(self, i):
        page = self.page

        class _FI:
            def get_attribute(self, name):
                return "image/*" if name == "accept" else None

            def set_input_files(self, path):
                page.attached_file = path

        return _FI()


class _PhoneList:
    """نتیجه‌های جستجوی شماره: متنِ نتیجه شامل شماره است"""

    def __init__(self, page, phones):
        self.page, self.phones = page, phones

    def count(self):
        return len(self.phones)

    def nth(self, i):
        page, ph = self.page, self.phones[i]

        def open_chat():
            page.state = "chat_open"
            page.chat_with = ph
            page.search_text = ""

        return _ResultItem(f"{page.contacts[ph]} — {ph}", open_chat)


class MockPagePhone(MockPage):
    """مثل روبیکا واقعی وقتی شماره را جستجو می‌کنیم: نتیجه شامل شماره است"""

    def __init__(self, phone_headers):  # {شماره: نام هدر چت}
        self.numbers = phone_headers
        super().__init__(dict(phone_headers))  # chat_with=شماره → هدر

    def locator(self, sel):
        s = sel.lower()
        if "placeholder*='جستجو'" in sel and self.state == "main":
            return _Single(self._search_box())
        if ("پیام" in sel or "contenteditable" in sel or s == "textarea") and self.state == "chat_open":
            return _Single(self._composer())
        if ("ارسال" in sel or "send" in s) and self.state == "chat_open" and self.attached_file:
            el = El("button")
            el._click = self._send
            return _Single(el)
        if "result" in s or "option" in s or sel.startswith("li"):
            if self.state == "main" and self.search_text:
                d = sw.digits_only(self.search_text)
                tail = d[-10:] if len(d) >= 10 else d
                hits = [ph for ph in self.numbers if sw.phone_tail(ph) == tail]
                return _PhoneList(self, hits)
            return _List(self, [])
        if sel == "input[type='file']" and self.state == "chat_open":
            return _FileInputs(self)
        return _Single(None)


# ---------------------------------------------------------------- سناریوها

CONTACTS = {
    "مسئول نسرا آران و بیدگل": "مسئول نسرا آران و بیدگل",
    "فرمانده گردان آران و بیدگل": "فرمانده گردان آران و بیدگل",
    "مسئول فضای مجازی آران و بیدگل": "مسئول فضای مجازی آران و بیدگل",
}

S = dict(sw.DEFAULT_SELECTORS)
CFG = {"verify_chat_title": True}

_tmp = Path(tempfile.mkdtemp(prefix="karnameh_test_"))
IMG = _tmp / "کارنامه_آران و بیدگل.png"
IMG.write_bytes(b"fake-image-data")


def test_open_chat_success():
    page = MockPage(CONTACTS)
    ok, err = sw.open_chat(page, "مسئول نسرا آران و بیدگل", S, CFG)
    assert ok, f"باز کردن چت ناموفق: {err}"
    assert page.chat_with == "مسئول نسرا آران و بیدگل"
    print("✅ open_chat: جستجو ← انتخاب نتیجه ← باز شدن چت (با تأیید عنوان)")


def test_open_chat_not_found():
    page = MockPage(CONTACTS)
    ok, err = sw.open_chat(page, "مسئول نسرا تهران", S, CFG)
    assert not ok, "باید ناموفق می‌شد!"
    assert "پیدا نشد" in err
    print("✅ open_chat: مخاطبِ ناموجود ← خطا، بدون باز شدن چت اشتباه")


def test_send_image():
    page = MockPage(CONTACTS)
    ok, _ = sw.open_chat(page, "فرمانده گردان آران و بیدگل", S, CFG)
    assert ok
    ok, err = sw.send_image(page, IMG, "کارنامه عملکرد شهریور ۱۴۰۵ ناحیه آران و بیدگل", S)
    assert ok, f"ارسال ناموفق: {err}"
    assert len(page.sent) == 1
    f, cap = page.sent[0]
    assert Path(f).name == IMG.name, f
    assert cap == "کارنامه عملکرد شهریور ۱۴۰۵ ناحیه آران و بیدگل", cap
    print(f"✅ send_image: پیوست از ورودی مخفی فایل ← کپشن ← ارسال با دکمه ({IMG.name})")


def test_send_image_enter_fallback():
    """وقتی دکمه ارسال پیدا نمی‌شود، Enter باید بفرستد"""
    S2 = dict(S)
    S2["send_button"] = ["#not-exist"]
    page = MockPage(CONTACTS)
    ok, _ = sw.open_chat(page, "مسئول فضای مجازی آران و بیدگل", S, CFG)
    assert ok
    page.no_send_button = True
    ok, err = sw.send_image(page, IMG, "کپشن تست", S2)
    assert ok, f"ارسال با Enter ناموفق: {err}"
    assert page.sent and page.sent[0][1] == "کپشن تست"
    print("✅ send_image: وقتی دکمه‌ی ارسال نیست، Enter پیام را می‌فرستد")


def test_verify_chat_title_mismatch():
    """اگر هدر چت با نام مخاطب نخواند، نباید اجازه‌ی ارسال داده شود"""
    page = MockPage({"مسئول نسرا کاشان": "علی محمدی — نام نمایشیِ متفاوت"})
    ok, err = sw.open_chat(page, "مسئول نسرا کاشان", S, CFG)
    assert not ok and "نمی‌خواند" in err, (ok, err)
    print("✅ verify_chat_title: ناهمخوانی عنوان گفتگو ← جلوگیری از ارسال اشتباه")



def test_normalize_phone():
    assert sw.normalize_phone("۰۹۱۲ ۳۴۵ ۶۷۸۹") == "09123456789"
    assert sw.normalize_phone("+98 912 345 6789") == "09123456789"
    assert sw.normalize_phone("00989123456789") == "09123456789"
    assert sw.normalize_phone("98-912-345-6789") == "09123456789"
    assert sw.normalize_phone(9123456789) == "09123456789"          # عددی (اکسل)
    assert sw.normalize_phone(9123456789.0) == "09123456789"        # اعشاریِ اکسل
    assert sw.normalize_phone("۰۹۱۲۳۴۵۶۷۸۹") == "09123456789"
    assert sw.normalize_phone("") is None
    assert sw.normalize_phone(None) is None
    assert sw.normalize_phone("بدون شماره") is None
    print("✅ normalize_phone: همه‌ی شکل‌های شماره درست خوانده می‌شوند")


def test_xlsx_loading():
    from openpyxl import Workbook
    tmp = Path(tempfile.mkdtemp(prefix="karnameh_xlsx_"))
    wb = Workbook(); ws = wb.active
    ws.append(["نام ناحیه", "نام ۱", "شماره ۱", "نام ۲", "شماره ۲", "نام ۳", "شماره ۳"])
    ws.append(["ناحیه تست", "خودم", "۰۹۱۲۱۱۱۲۲۳۳", "خودم", "09121112233", "", ""])
    ws.append(["آران و بیدگل", "علی", 9121112244, "رضا", "+98 912 111 2255", "سعید", "۰۰۹۸۹۱۲ ۱۱۱ ۲۲۶۶"])
    ws.append(["ناحیه خالی", "", "", "", "", "", ""])
    xlsx = tmp / "مخاطبین.xlsx"
    wb.save(xlsx)
    cfg = {"roles": ["مسئول نسرا", "فرمانده گردان", "مسئول فضای مجازی"]}
    data = sw.load_recipients_xlsx(xlsx, cfg)
    # شماره‌ی تکراری در یک ناحیه = یک مخاطب
    assert len(data["ناحیه تست"]) == 1 and data["ناحیه تست"][0]["phone"] == "09121112233"
    assert len(data["آران و بیدگل"]) == 3
    assert data["آران و بیدگل"][0]["phone"] == "09121112244"
    assert data["آران و بیدگل"][1]["phone"] == "09121112255"
    assert data["آران و بیدگل"][2]["phone"] == "09121112266"
    assert data["آران و بیدگل"][0]["name"] == "علی"
    # ردیفِ بدون شماره = ناحیه‌ی ردشده
    assert data["ناحیه خالی"] == []
    print("✅ load_recipients_xlsx: اکسل درست خوانده می‌شود (تکراری‌ها یکی می‌شوند)")


def test_build_tasks_with_xlsx():
    from openpyxl import Workbook
    tmp = Path(tempfile.mkdtemp(prefix="karnameh_task_"))
    img_dir = tmp / "کارنامه‌ها"; img_dir.mkdir()
    (img_dir / "کارنامه_ناحیه تست.png").write_bytes(b"img")
    (img_dir / "کارنامه_کاشان.png").write_bytes(b"img")
    wb = Workbook(); ws = wb.active
    ws.append(["نام ناحیه", "نام ۱", "شماره ۱", "نام ۲", "شماره ۲", "نام ۳", "شماره ۳"])
    ws.append(["ناحیه تست", "خودم", "09120000000", "", "", "", ""])
    ws.append(["ناحیه بی‌تصویر", "", "09120000001", "", "", "", ""])
    xlsx = tmp / "m.xlsx"; wb.save(xlsx)
    cfg = {"images_dir": str(img_dir), "filename_prefix": "کارنامه_",
           "roles": ["مسئول نسرا", "فرمانده گردان", "مسئول فضای مجازی"],
           "image_extensions": [".jpg", ".jpeg", ".png", ".webp", ".bmp"],
           "overrides_csv": "overrides.csv", "recipients_xlsx": str(xlsx)}
    tasks = sw.build_tasks(cfg)
    assert "ناحیه تست" in tasks
    c = tasks["ناحیه تست"]["contacts"][0]
    assert c["phone"] == "09120000000" and c["key"] == "09120000000"
    # ناحیه‌ای که در اکسل نیست → جستجو با نام
    c2 = tasks["کاشان"]["contacts"][0]
    assert c2["phone"] is None and c2["key"] == "مسئول نسرا کاشان"
    print("✅ build_tasks: اکسل اولویت دارد؛ ناحیه‌های بدون اکسل با نام جستجو می‌شوند")


def test_open_chat_by_phone_found():
    page = MockPagePhone({"09121112233": "علی محمدی"})
    ok, err = sw.open_chat_by_phone(page, "09121112233", S)
    assert ok, err
    assert page.chat_with == "09121112233"
    ok2, err2 = sw.send_image(page, IMG, "کپشن تست شماره‌ای", S)
    assert ok2, err2
    assert page.sent and page.sent[0][1] == "کپشن تست شماره‌ای"
    print("✅ open_chat_by_phone: جستجوی شماره ← باز شدن چت ← ارسال تصویر")


def test_open_chat_by_phone_not_found():
    page = MockPagePhone({"09121112233": "علی محمدی"})
    ok, err = sw.open_chat_by_phone(page, "09998887777", S)
    assert not ok and "پیدا نشد" in err, (ok, err)
    assert not page.sent
    print("✅ open_chat_by_phone: شماره‌ی ناموجود ← خطا، بدون ارسال اشتباه")


if __name__ == "__main__":
    test_normalize_phone()
    test_xlsx_loading()
    test_build_tasks_with_xlsx()
    test_open_chat_success()
    test_open_chat_not_found()
    test_send_image()
    test_send_image_enter_fallback()
    test_verify_chat_title_mismatch()
    test_open_chat_by_phone_found()
    test_open_chat_by_phone_not_found()
    print("\n🎉 همه سناریوهای شبیه‌سازی‌شده پاس شدند!")
    print()
    print("=" * 60)
    print("*** ALL TESTS PASSED - SUCCESS ***")
    print("=" * 60)
