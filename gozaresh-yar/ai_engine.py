# -*- coding: utf-8 -*-
"""
موتور هوش مصنوعی اختیاری برای «گزارش‌یار هوشمند سازمانی».

هدف: افزایش دقت در سه کارِ متنی —
  ۱) استخراج داده‌های ساختاریافته از متن پست‌های تلگرام (شهرستان، تعداد نفرات، مدرس، مکان، موضوع، نوع)
  ۲) دسته‌بندی دقیق‌تر اقدامات (بحران / پویش / هم‌افزایی / رویداد ویژه / روتین)
  ۳) نوشتن جمله‌ی «نتیجه و اثر» و ویرایش نهایی متن گزارش با لحن اداری

دو روش پشتیبانی می‌شود:
  • ollama : مدل محلی روی همان رایانه (آفلاین — داده از دستگاه بیرون نمی‌رود)  ← پیشنهادی
  • api    : هر سرویس سازگار با OpenAI (OpenAI / OpenRouter / Groq / سرور داخلی …)

اصل مهم: اگر هوش مصنوعی خاموش باشد یا خطا بدهد، برنامه با روش قاعده‌محور (کلیدواژه/الگو)
ادامه می‌دهد و هیچ‌گاه عددی از خود نمی‌سازد. هر خروجی نامعتبر پذیرفته نمی‌شود.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Sequence, Tuple

APP_VERSION = "1.0"

# ---------------------------------------------------------------------------
# پیام‌های سیستمی (پرامپت‌ها) — همه‌چیز روی «فقط از متن داده‌شده» قفل شده است
# ---------------------------------------------------------------------------

SYS_EXTRACT = """تو یک تحلیل‌گر داده‌ی سازمانی فارسی‌زبان هستی.
متن پست‌های یک کانال تلگرام به تو داده می‌شود. وظیفه‌ی تو تبدیل هر پست به یک رکورد داده است.
قواعد قطعی که باید رعایت کنی:
۱) فقط از اطلاعات موجود در همان متن استفاده کن. هیچ عدد، نام، مکان یا موضوعی از خودت نساز.
۲) اگر اطلاعاتی در متن نیست، مقدار آن را رشته‌ی خالی "" یا عدد 0 بگذار.
۳) خروجی را فقط و فقط به شکل آرایه‌ی JSON برگردان؛ هیچ توضیح، مقدمه یا بلوک کد اضافه نگذار.
۴) ارقام را با عدد لاتین (مثلاً 45) بنویس، نه فارسی.
قالب هر عضو آرایه:
{"id": <شماره پست>, "ناحیه": "", "تعداد": 0, "مدرس": "", "مکان": "",
 "موضوع": "", "نوع": "حضوری", "اطمینان": 0}
مقدار مجاز برای "نوع" یکی از این‌هاست:
حضوری | مجازی | گردان | خلاقانه | تولیدات
راهنمای انتخاب نوع: لایو/پخش زنده/فضای مجازی → مجازی؛
گردان/توانمندسازی گردان → گردان؛ پویش/مسابقه/خلاقانه → خلاقانه؛
تولید کلیپ/موشن/پوستر/اینفوگرافیک/صفحه → تولیدات؛ در غیر این صورت → حضوری.
«اطمینان» عددی بین ۰ تا ۱۰۰ است و میزان اطمینان تو از درستی رکورد را نشان می‌دهد."""

SYS_CLASSIFY = """تو یک کارشناس دسته‌بندی گزارش‌های سازمانی هستی.
برای هر اقدام که «موضوع» و «نوع» و «شرح مختصر» آن داده می‌شود، مشخص کن در کدام دسته‌ها قرار می‌گیرد.
قواعد:
۱) فقط از میان این برچسب‌ها انتخاب کن:
   crisis (بحران و مسئله ویژه: شایعه، واکنش سریع، هجمه، حادثه)
   campaign (تولیدات و پویش‌های جریان‌ساز)
   synergy (هم‌افزایی و اقدامات مشترک با نهادهای دیگر)
   special (برنامه‌ها و رویدادهای ویژه: همایش، کارگاه، جشنواره، مسابقه با مخاطب بالا)
   routine (اقدام روتین: بازدید، نظارت، جلسه‌ی عادی اداری، مکاتبه)
۲) یک اقدام می‌تواند چند برچسب بگیرد، اما اگر «routine» است بقیه را نده.
۳) خروجی فقط آرایه‌ی JSON: [{"id": <شماره>, "دسته": ["crisis"]}]
۴) هیچ توضیحی اضافه نکن."""

SYS_IMPACT = """تو نویسنده‌ی متن اداری فارسی هستی.
برای هر اقدام، یک جمله‌ی «نتیجه و اثر» بنویس؛ حداکثر ۲۰ واژه، رسمی و بدون اغراق.
قواعد:
۱) فقط از اعداد و اطلاعاتی که در ورودی آمده استفاده کن؛ هیچ عدد جدیدی نساز.
۲) هر جمله با نتیجه/اثر شروع شود، نه با تکرار نام اقدام.
۳) خروجی فقط آرایه‌ی JSON: [{"id": <شماره>, "اثر": "..."}]
۴) از عبارت‌های تبلیغاتی، تعجب و علامت سؤال پرهیز کن."""

SYS_POLISH = """تو ویراستار متون اداری فارسی هستی.
متن گزارش را ویرایش کن: لحن رسمی‌تر، جمله‌های روان‌تر، حذف تکرار.
قواعد قطعی:
۱) هیچ عدد، تاریخ، نام ناحیه، نام مدرس یا آماره‌ای را تغییر نده و حذف نکن. همه‌ی اعداد باید
   عیناً باقی بمانند (فقط نگارش فارسی/لاتین آن‌ها می‌تواند یکدست شود).
۲) ساختار بخش‌ها (عنوان‌های «## …») و ترتیب آن‌ها حفظ شود.
۳) طول متن از سقف واژه‌ی اعلام‌شده بیشتر نشود.
۴) خروجی فقط متن ویرایش‌شده باشد (بدون توضیح و بدون بلوک کد)."""


# ---------------------------------------------------------------------------
# ابزارهای کمکی
# ---------------------------------------------------------------------------

def _sha1(text: str) -> str:
    return hashlib.sha1(text.encode("utf-8")).hexdigest()


def extract_json(text: str) -> Any:
    """استخراج اولین شیء/آرایه‌ی JSON از پاسخ مدل (حذف حصارهای کد و توضیحات اضافه)."""
    if not text:
        return None
    t = text.strip()
    t = re.sub(r"^```(?:json)?\s*", "", t)
    t = re.sub(r"\s*```$", "", t)
    for opener, closer in (("[", "]"), ("{", "}")):
        start = t.find(opener)
        end = t.rfind(closer)
        if start != -1 and end > start:
            candidate = t[start:end + 1]
            try:
                return json.loads(candidate)
            except json.JSONDecodeError:
                # تلاش دوم: پاک‌سازی کاماهای اضافه
                cleaned = re.sub(r",\s*([}\]])", r"\1", candidate)
                try:
                    return json.loads(cleaned)
                except json.JSONDecodeError:
                    continue
    try:
        return json.loads(t)
    except json.JSONDecodeError:
        return None


def numbers_of(text: str) -> List[str]:
    """فهرست همه‌ی اعداد یک متن (برای کنترل دست‌نخورده ماندن آماره‌ها)."""
    return re.findall(r"\d+(?:[.,٫]\d+)?", str(text).translate(
        str.maketrans("۰۱۲۳۴۵۶۷۸۹", "0123456789")))


# ---------------------------------------------------------------------------
# موتور اصلی
# ---------------------------------------------------------------------------

@dataclass
class AIResult:
    ok: bool
    data: Any = None
    error: str = ""
    used_cache: bool = False
    raw: str = ""


@dataclass
class AIStats:
    calls: int = 0
    cache_hits: int = 0
    failures: int = 0
    items_ai: int = 0
    items_rule: int = 0
    notes: List[str] = field(default_factory=list)

    def summary(self) -> str:
        return (f"فراخوانی مدل: {self.calls} | از حافظه‌ی موقت: {self.cache_hits} | "
                f"خطا: {self.failures} | رکورد هوشمند: {self.items_ai} | "
                f"رکورد قاعده‌محور: {self.items_rule}")


class AIEngine:
    """ارتباط با مدل زبانی: محلی (Ollama) یا سرویس سازگار با OpenAI."""

    def __init__(self, backend: str = "off", model: str = "", base_url: str = "",
                 api_key: str = "", timeout: int = 120, retries: int = 2,
                 cache_path: str = "", log=None, verbose: bool = True):
        self.backend = (backend or "off").lower()
        self.model = model or {"ollama": "qwen2.5:7b", "mock": "mock",
                               "api": "gpt-4o-mini"}.get(self.backend, "")
        if self.backend == "ollama":
            self.base_url = (base_url or "http://localhost:11434").rstrip("/")
        elif self.backend == "api":
            self.base_url = (base_url or "https://api.openai.com/v1").rstrip("/")
        elif self.backend == "mock":
            self.base_url = base_url or "mock://"
        else:
            self.base_url = base_url or ""
        self.api_key = api_key or os.environ.get("GOZARESH_AI_KEY", "")
        self.timeout = timeout
        self.retries = max(0, retries)
        self.cache_path = cache_path
        self.log = log or (lambda *a, **k: None)
        self.stats = AIStats()
        self.verbose = verbose
        self._cache: Dict[str, str] = {}
        self._cache_dirty = False
        self._load_cache()

    # ---------------------------------------------------------------- حافظه‌ی موقت
    def _load_cache(self) -> None:
        if not self.cache_path or not os.path.exists(self.cache_path):
            return
        try:
            with open(self.cache_path, "r", encoding="utf-8") as f:
                self._cache = json.load(f)
        except Exception:
            self._cache = {}

    def save_cache(self) -> None:
        if not self.cache_path or not self._cache_dirty:
            return
        try:
            os.makedirs(os.path.dirname(self.cache_path) or ".", exist_ok=True)
            with open(self.cache_path, "w", encoding="utf-8") as f:
                json.dump(self._cache, f, ensure_ascii=False)
            self._cache_dirty = False
        except Exception:
            pass

    # ---------------------------------------------------------------- سلامت سرویس
    def available(self) -> Tuple[bool, str]:
        """بررسی در دسترس بودن مدل؛ خروجی: (سلامت، پیام)."""
        if self.backend == "off":
            return False, "هوش مصنوعی خاموش است."
        if self.backend == "mock":
            return True, "حالت آزمایشی (Mock) فعال است."
        if self.backend == "ollama":
            try:
                req = urllib.request.Request(self.base_url + "/api/tags")
                with urllib.request.urlopen(req, timeout=8) as resp:
                    data = json.loads(resp.read().decode("utf-8", "ignore"))
                names = [m.get("name", "") for m in data.get("models", [])]
                if not names:
                    return False, "Ollama در دسترس است اما هیچ مدلی نصب نشده (ollama pull …)."
                if self.model not in names:
                    return (True, f"هشدار: مدل «{self.model}» در فهرست مدل‌های نصب‌شده نبود. "
                                  f"مدل‌های موجود: {'، '.join(names[:5])}")
                return True, f"Ollama آماده است (مدل {self.model})."
            except Exception as e:
                return False, (f"ارتباط با Ollama برقرار نشد ({e}). "
                               f"مطمئن شوید برنامه‌ی Ollama اجرا است: «ollama serve»")
        if self.backend == "api":
            if not self.api_key:
                return False, "کلید سرویس (API key) تنظیم نشده است."
            try:
                req = urllib.request.Request(self.base_url + "/models",
                                             headers={"Authorization": "Bearer " + self.api_key})
                with urllib.request.urlopen(req, timeout=10) as resp:
                    resp.read()
                return True, f"ارتباط با سرویس {self.base_url} برقرار است (مدل {self.model})."
            except urllib.error.HTTPError as e:
                if e.code in (401, 403):
                    return False, "کلید سرویس نامعتبر است (خطای احراز هویت)."
                return True, f"سرویس پاسخ داد (کد {e.code})؛ ادامه می‌دهیم."
            except Exception as e:
                return False, f"ارتباط با سرویس برقرار نشد: {e}"
        return False, "روش نامشخص."

    # ---------------------------------------------------------------- فراخوانی مدل
    def _post_json(self, url: str, payload: dict, headers: dict) -> dict:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(url, data=data, method="POST",
                                     headers={"Content-Type": "application/json", **headers})
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            return json.loads(resp.read().decode("utf-8", "ignore"))

    def complete(self, system: str, user: str, json_mode: bool = False,
                 max_tokens: int = 2000) -> AIResult:
        """یک فراخوانی مدل (با حافظه‌ی موقت و تلاش مجدد)."""
        if self.backend == "off":
            return AIResult(False, error="خاموش")

        key = _sha1(f"{self.backend}|{self.model}|{system}|{user}|{json_mode}")
        if key in self._cache:
            self.stats.cache_hits += 1
            return AIResult(True, data=extract_json(self._cache[key]),
                            used_cache=True, raw=self._cache[key])

        if self.backend == "mock":
            raw = _mock_response(system, user)
            self.stats.calls += 1
            return AIResult(True, data=extract_json(raw), raw=raw)

        last_err = ""
        for attempt in range(self.retries + 1):
            try:
                if self.backend == "ollama":
                    payload = {
                        "model": self.model,
                        "messages": [{"role": "system", "content": system},
                                     {"role": "user", "content": user}],
                        "stream": False,
                        "options": {"temperature": 0.1, "num_predict": max_tokens},
                    }
                    if json_mode:
                        payload["format"] = "json"
                    out = self._post_json(self.base_url + "/api/chat", payload, {})
                    raw = (out.get("message") or {}).get("content", "")
                else:  # api سازگار با OpenAI
                    payload = {
                        "model": self.model,
                        "messages": [{"role": "system", "content": system},
                                     {"role": "user", "content": user}],
                        "temperature": 0.1,
                        "max_tokens": max_tokens,
                    }
                    if json_mode:
                        payload["response_format"] = {"type": "json_object"}
                    headers = {}
                    if self.api_key:
                        headers["Authorization"] = "Bearer " + self.api_key
                    try:
                        out = self._post_json(self.base_url + "/chat/completions", payload, headers)
                    except urllib.error.HTTPError as e:
                        if json_mode and e.code in (400, 404, 422):
                            payload.pop("response_format", None)
                            out = self._post_json(self.base_url + "/chat/completions",
                                                  payload, headers)
                        else:
                            raise
                    raw = ((out.get("choices") or [{}])[0].get("message") or {}).get("content", "")

                self.stats.calls += 1
                if raw:
                    self._cache[key] = raw
                    self._cache_dirty = True
                    return AIResult(True, data=extract_json(raw), raw=raw)
                last_err = "پاسخ خالی از مدل"
            except Exception as e:  # شبکه، زمان‌بندی، قالب …
                last_err = f"{type(e).__name__}: {e}"
                if attempt < self.retries:
                    time.sleep(1.5 * (attempt + 1))
        self.stats.failures += 1
        return AIResult(False, error=last_err)

    # ---------------------------------------------------------------- کارها
    def extract_records(self, posts: Sequence[Dict[str, Any]],
                        batch_size: int = 10) -> Tuple[Dict[int, Dict[str, Any]], AIStats]:
        """استخراج رکوردهای ساختاریافته از متن پست‌ها. خروجی: {شماره پست: رکورد}."""
        result: Dict[int, Dict[str, Any]] = {}
        for i in range(0, len(posts), batch_size):
            batch = posts[i:i + batch_size]
            lines = []
            for p in batch:
                body = str(p.get("text", "")).replace("\n", " ")
                body = body[:900]
                lines.append(f'{{"id": {p.get("id")}, "text": "{body}"}}')
            user = ("متن پست‌ها:\n[" + ",\n".join(lines) + "]\n\n"
                    "خروجی: آرایه‌ی JSON طبق قالب اعلام‌شده.")
            res = self.complete(SYS_EXTRACT, user, json_mode=True)
            data = res.data if res.ok else None
            if isinstance(data, dict):
                data = data.get("items") or data.get("records") or [data]
            if not isinstance(data, list):
                self.stats.notes.append(f"پاسخ نامعتبر برای دسته‌ی {i // batch_size + 1} "
                                        f"→ استفاده از روش قاعده‌محور")
                continue
            for item in data:
                if not isinstance(item, dict):
                    continue
                pid = item.get("id")
                try:
                    pid = int(str(pid).translate(str.maketrans("۰۱۲۳۴۵۶۷۸۹", "0123456789")))
                except (TypeError, ValueError):
                    continue
                result[pid] = item
                self.stats.items_ai += 1
        self.save_cache()
        return result, self.stats

    def classify_items(self, items: Sequence[Dict[str, Any]],
                       batch_size: int = 15) -> Dict[int, List[str]]:
        """دسته‌بندی هوشمند اقدامات. خروجی: {شناسه: [برچسب‌ها]}."""
        out: Dict[int, List[str]] = {}
        allowed = {"crisis", "campaign", "synergy", "special", "routine"}
        for i in range(0, len(items), batch_size):
            batch = items[i:i + batch_size]
            user = ("اقدامات:\n" + json.dumps(
                [{"id": it["id"], "موضوع": it.get("topic", ""), "نوع": it.get("kind", ""),
                  "شرح": it.get("hint", "")[:300]} for it in batch],
                ensure_ascii=False) + "\n\nخروجی: آرایه‌ی JSON طبق قالب اعلام‌شده.")
            res = self.complete(SYS_CLASSIFY, user, json_mode=True)
            data = res.data if res.ok else None
            if isinstance(data, dict):
                data = data.get("items") or data.get("results") or []
            if not isinstance(data, list):
                continue
            for item in data:
                if not isinstance(item, dict):
                    continue
                try:
                    pid = int(item.get("id"))
                except (TypeError, ValueError):
                    continue
                labels = item.get("دسته") or item.get("labels") or item.get("category") or []
                if isinstance(labels, str):
                    labels = [labels]
                labels = [str(l).strip().lower() for l in labels if str(l).strip().lower() in allowed]
                if labels:
                    out[pid] = labels
        self.save_cache()
        return out

    def write_impacts(self, items: Sequence[Dict[str, Any]],
                      batch_size: int = 12) -> Dict[int, str]:
        """نوشتن جمله‌ی «نتیجه و اثر» برای اقدامات."""
        out: Dict[int, str] = {}
        for i in range(0, len(items), batch_size):
            batch = items[i:i + batch_size]
            user = ("اقدامات:\n" + json.dumps(
                [{"id": it["id"], "ناحیه": it.get("county", ""), "موضوع": it.get("topic", ""),
                  "نوع": it.get("kind", ""), "مخاطب": it.get("people", 0),
                  "مکان": it.get("place", "")} for it in batch], ensure_ascii=False)
                + "\n\nخروجی: آرایه‌ی JSON طبق قالب اعلام‌شده.")
            res = self.complete(SYS_IMPACT, user, json_mode=True)
            data = res.data if res.ok else None
            if isinstance(data, dict):
                data = data.get("items") or []
            if not isinstance(data, list):
                continue
            for item in data:
                if not isinstance(item, dict):
                    continue
                try:
                    pid = int(item.get("id"))
                except (TypeError, ValueError):
                    continue
                txt = str(item.get("اثر") or item.get("impact") or "").strip()
                if txt and numbers_of(txt) <= numbers_of(json.dumps(batch, ensure_ascii=False)):
                    out[pid] = txt
        self.save_cache()
        return out

    def polish_report(self, text: str, max_words: int = 0) -> Tuple[str, str]:
        """
        ویرایش نهایی گزارش. خروجی: (متن ویرایش‌شده، هشدار).
        نکته‌ی ایمنی: اگر اعداد متن تغییر کنند، ویرایش پذیرفته نمی‌شود.
        """
        user = f"سقف واژه: {max_words or 'بدون محدودیت'}\n\nمتن گزارش:\n{text}"
        res = self.complete(SYS_POLISH, user, json_mode=False,
                            max_tokens=min(4000, max(1200, len(text) // 3)))
        if not res.ok or not res.raw:
            return text, "ویرایش هوش مصنوعی انجام نشد؛ متن اصلی استفاده شد."
        polished = res.raw.strip()
        polished = re.sub(r"^```[a-zA-Z]*\s*", "", polished)
        polished = re.sub(r"\s*```$", "", polished)
        before, after = sorted(numbers_of(text)), sorted(numbers_of(polished))
        if before != after:
            return text, ("ویرایش هوش مصنوعی رد شد: آماره‌های متن تغییر کرده بود "
                          "(اصل بی‌تغییر ماند).")
        if "##" not in polished:
            return text, "ویرایش هوش مصنوعی ساختار بخش‌ها را از دست داده بود؛ رد شد."
        if max_words and len(polished.split()) > max_words:
            return text, "ویرایش هوش مصنوعی از سقف واژه گذشت؛ رد شد."
        return polished, ""


def _mock_response(system: str, user: str) -> str:
    """
    پاسخ ساختگی برای آزمون خودکار مسیر هوش مصنوعی (بدون نیاز به مدل واقعی).
    این بخش فقط برای اطمینان از درستی لوله‌کشی داده است.
    """
    if system is SYS_EXTRACT or "شماره پست" in system:
        out = []
        for m in re.finditer(r'\{"id": (\d+), "text": "(.{0,900})"\}', user):
            pid, text = int(m.group(1)), m.group(2)
            county = ""
            for c in ("آران و بیدگل", "کاشان", "شهرضا", "نجف آباد", "خمینی شهر"):
                if c in text:
                    county = c
                    break
            people = 0
            mp = re.search(r"(\d+)\s*نفر", text)
            if mp:
                people = int(mp.group(1))
            kind = "حضوری"
            if any(k in text for k in ("لایو", "پخش زنده", "فضای مجازی")):
                kind = "مجازی"
            if any(k in text for k in ("کلیپ", "موشن", "پوستر", "اینفوگرافیک", "صفحه")):
                kind = "تولیدات"
            out.append({"id": pid, "ناحیه": county, "تعداد": people, "مدرس": "",
                        "مکان": "", "موضوع": "", "نوع": kind, "اطمینان": 60})
        return json.dumps(out, ensure_ascii=False)
    if "دسته‌بندی" in system or "کارشناس دسته‌بندی" in system:
        items = re.findall(r'"id":\s*(\d+)', user)
        rows = []
        for pid in items:
            labels = ["special"]
            if "شایعه" in user or "بحران" in user:
                labels = ["crisis"]
            rows.append({"id": int(pid), "دسته": labels})
        return json.dumps(rows, ensure_ascii=False)[:2000]
    if "نتیجه و اثر" in system:
        items = re.findall(r'"id":\s*(\d+)', user)
        return json.dumps([{"id": int(p), "اثر": "افزایش سطح آگاهی مخاطبان هدف."}
                           for p in items], ensure_ascii=False)
    if "ویراستار" in system:
        return re.sub(r"\n{3,}", "\n\n", user.split("متن گزارش:\n", 1)[-1]) + "\n"
    return "[]"


# ---------------------------------------------------------------------------
# تنظیمات ذخیره‌شده (فایل ai-settings.json کنار برنامه)
# ---------------------------------------------------------------------------

def default_settings_path(program_dir: str) -> str:
    return os.path.join(program_dir, "ai-settings.json")


def load_settings(path: str) -> Dict[str, Any]:
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def save_settings(path: str, data: Dict[str, Any]) -> None:
    try:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


def build_engine(settings: Dict[str, Any], program_dir: str, log) -> AIEngine:
    """ساخت موتور بر پایه‌ی تنظیمات ذخیره‌شده."""
    backend = str(settings.get("backend", "off")).lower()
    return AIEngine(
        backend=backend,
        model=str(settings.get("model", "")),
        base_url=str(settings.get("base_url", "")),
        api_key=str(settings.get("api_key", "")),
        timeout=int(settings.get("timeout", 120) or 120),
        retries=int(settings.get("retries", 2) or 2),
        cache_path=os.path.join(program_dir, ".ai-cache.json"),
        log=log,
        verbose=bool(settings.get("verbose", True)),
    )


AI_HELP = f"""راهنمای افزودن هوش مصنوعی (اختیاری)

۱) روش محلی و آفلاین (پیشنهادی — داده از دستگاه بیرون نمی‌رود):
   الف) برنامه‌ی Ollama را از ollama.com نصب کنید.
   ب) در ترمینال/CMD اجرا کنید:   ollama pull qwen2.5:7b
   پ) در گزارش‌یار، روش «ollama» را انتخاب کنید (نشانی پیش‌فرض: http://localhost:11434).

۲) روش ابری (سازگار با OpenAI؛ نیاز به اینترنت و کلید سرویس):
   در گزارش‌یار روش «api» را انتخاب و نشانی و کلید را وارد کنید.
   ⚠ توجه: در این روش متن پست‌ها به سرویس بیرونی ارسال می‌شود.

۳) اگر هوش مصنوعی خاموش باشد یا خطا بدهد، برنامه با روش قاعده‌محور (کلیدواژه و الگو)
   دقیقاً همان مسیر را ادامه می‌دهد و گزارش ساخته می‌شود — فقط دقتِ استخراج از متن‌های
   آزاد کمتر است.

نسخه‌ی موتور: {APP_VERSION}
"""
