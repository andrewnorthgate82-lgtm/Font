#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
send_bot.py — ارسال کارنامه‌های عملکرد از طریق «ربات رسمی روبیکا» (Bot API)
================================================================================

این روش جایگزینِ روش مرورگر (send_web.py) است و برای استفاده‌ی بلندمدت پایدارتر است:

  ۱) در https://rubika.ir/botapi یک ربات می‌سازید و توکن (token) می‌گیرید.
  ۲) هر مسئول فقط «یک بار» ربات شما را استارت می‌زند و نام ناحیه + سمت خودش را
     می‌فرستد  (این اسکریپت با گزینه‌ی --listen پیام‌ها را می‌گیرد و ثبت می‌کند).
  ۳) بعد از ثبت همه‌ی مسئولان، همین اسکریپت هر ماه کارنامه‌ی هر ناحیه را
     به‌صورت خودکار برای مسئولان همان ناحیه می‌فرستد.

مزیت‌ها: بدون مرورگر، بدون لاگین دستی، بدون ریسک محدودیت اکانت شخصی، بسیار سریع.
محدودیت: هر گیرنده باید یک بار ربات را استارت زده باشد (قانون ربات‌های روبیکا)؛
         حجم تصویر حداکثر ~۱۰ مگابایت.

راهنمای سریع:
    python send_bot.py --token TOKEN --listen      ← ثبت مسئولان (یک بار؛ باز بگذارید)
    python send_bot.py --token TOKEN --dry-run     ← چک وضعیت ثبت و نگاشت
    python send_bot.py --token TOKEN               ← ارسال واقعی
    python send_bot.py --token TOKEN --only "آران و بیدگل"

توکن را می‌توان به‌جای --token در config.json (کلید bot_token) هم گذاشت.
"""

from __future__ import annotations

import argparse
import csv
import json
import logging
import mimetypes
import random
import sys
import time
from datetime import datetime
from pathlib import Path

import requests

from send_web import BASE, build_tasks, flat, load_config, normalize

API_ROOT = "https://botapi.rubika.ir/v3"
log = logging.getLogger("karnameh_bot")

ROLE_KEYWORDS = {
    "مسئول نسرا": ["نسرا"],
    "فرمانده گردان": ["فرمانده", "گردان"],
    "مسئول فضای مجازی": ["فضای مجازی", "فضای"],
    # اگر نقش دیگری در config.json اضافه کردید، کلیدواژه‌اش را اینجا هم اضافه کنید
}

WELCOME = (
    "سلام 👋\n"
    "این ربات، کارنامه‌ی عملکرد ماهانه‌ی ناحیه شما را ارسال می‌کند.\n"
    "لطفاً «نام ناحیه و سمت خود» را دقیقاً به یکی از این شکل‌ها ارسال کنید:\n"
    "  آران و بیدگل - نسرا\n"
    "  آران و بیدگل - فرمانده گردان\n"
    "  آران و بیدگل - مسئول فضای مجازی"
)


# ----------------------------------------------------------------------------- لایه API


class BotAPI:
    def __init__(self, token: str):
        self.base = f"{API_ROOT}/{token}"

    def call(self, method: str, payload: dict | None = None, timeout: int = 35) -> dict:
        r = requests.post(f"{self.base}/{method}", json=payload or {}, timeout=timeout)
        try:
            j = r.json()
        except ValueError:
            raise RuntimeError(f"{method} → HTTP {r.status_code}: {r.text[:300]}")
        if r.status_code != 200:
            raise RuntimeError(f"{method} → HTTP {r.status_code}: {r.text[:300]}")
        status = j.get("status") if isinstance(j, dict) else None
        if status not in (None, "OK", "ok", "success", 200, "200", True):
            raise RuntimeError(f"{method} → پاسخ سرور: {str(j)[:300]}")
        return j.get("data", j) if isinstance(j, dict) else j

    def get_me(self):
        return self.call("getMe")

    def send_message(self, chat_id: str, text: str):
        return self.call("sendMessage", {"chat_id": chat_id, "text": text})

    def send_photo(self, chat_id: str, image: Path, caption: str | None = None):
        # مرحله ۱: گرفتن آدرس آپلود
        d = self.call("requestSendFile", {"type": "Image"})
        upload_url = dig(d, "upload_url") or dig(d, "data", "upload_url")
        if not upload_url:
            raise RuntimeError(f"upload_url در پاسخ requestSendFile نبود: {str(d)[:200]}")
        # مرحله ۲: آپلود فایل (multipart)
        mime = mimetypes.guess_type(image.name)[0] or "image/jpeg"
        with image.open("rb") as fh:
            up = requests.post(
                upload_url,
                files={"file": (image.name, fh, mime)},
                timeout=600,
            )
        try:
            uj = up.json()
        except ValueError:
            raise RuntimeError(f"آپلود → HTTP {up.status_code}: {up.text[:300]}")
        if up.status_code != 200:
            raise RuntimeError(f"آپلود → HTTP {up.status_code}: {up.text[:300]}")
        file_id = dig(uj, "file_id") or dig(uj, "data", "file_id") or dig(uj, "data", "data", "file_id")
        if not file_id:
            raise RuntimeError(f"file_id در پاسخ آپلود نبود: {str(uj)[:200]}")
        # مرحله ۳: ارسال به چت
        payload = {"chat_id": chat_id, "file_id": file_id}
        if caption:
            payload["text"] = caption
        return self.call("sendFile", payload)


def dig(obj, *keys):
    """برداشتن کلیدهای تودرتو از پاسخ JSON (مقاوم به تفاوت ساختار)."""
    cur = obj
    for k in keys:
        if isinstance(cur, dict) and k in cur:
            cur = cur[k]
        else:
            return None
    return cur


# ----------------------------------------------------------------------------- دفترچه گیرنده‌ها


def users_file(cfg) -> Path:
    return BASE / cfg.get("bot_users_file", "bot_users.json")


def load_users(cfg) -> dict:
    p = users_file(cfg)
    if p.exists():
        try:
            return json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}


def save_users(cfg, users: dict):
    users_file(cfg).write_text(
        json.dumps(users, ensure_ascii=False, indent=1), encoding="utf-8"
    )


def load_recipients_csv(cfg) -> list:
    """فایل اختیاری recipients.csv با ستون‌های: ناحیه, سمت, chat_id
    (برای وقتی که می‌خواهید دستی chat_id بدهید و از ثبتِ با ربات صرف‌نظر کنید)"""
    p = BASE / "recipients.csv"
    if not p.exists():
        return []
    rows = []
    with p.open(encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            row = [c.strip() for c in row if c and c.strip()]
            if len(row) >= 3 and normalize(row[0]) not in ("ناحیه", "city", "شهر"):
                rows.append({"city": row[0], "role": row[1], "chat_id": row[2]})
    return rows


def match_chat_id(users: dict, csv_rows: list, city: str, role: str):
    """پیدا کردن chat_id مسئولِ (ناحیه، سمت) از بین ثبت‌ها."""
    fc, fr = flat(city), flat(role)
    # ۱) CSV دستی
    for r in csv_rows:
        if flat(r["city"]) in fc or fc in flat(r["city"]):
            if flat(r["role"]) in fr or fr in flat(r["role"]):
                return r["chat_id"], "recipients.csv"
    # ۲) برچسب‌های ثبت‌شده از طریق ربات
    kws = [flat(k) for k in ROLE_KEYWORDS.get(role, [role])]
    for cid, u in users.items():
        lbl = flat(u.get("label") or "")
        if not lbl:
            continue
        if fc in lbl and any(k in lbl for k in kws):
            return cid, u.get("label")
    return None, None


# ----------------------------------------------------------------------------- حالت ثبت (--listen)


def extract_updates(payload) -> tuple:
    """استخراجِ مقاوم آپدیت‌ها: (update_id, chat_id, text)"""
    out = []
    items = dig(payload, "data", "updates") or dig(payload, "updates") or []
    if isinstance(items, dict):
        items = list(items.values())
    for up in items or []:
        if not isinstance(up, dict):
            continue
        uid = up.get("update_id") or up.get("id")
        nm = up.get("new_message") or up.get("newMessage") or {}
        chat_id = up.get("chat_id") or nm.get("chat_id") or nm.get("sender_chat_id")
        text = (nm.get("text") or up.get("text") or "").strip()
        if chat_id:
            out.append((uid, str(chat_id), text))
    return out


def run_listen(cfg, api: BotAPI):
    users = load_users(cfg)
    print("🎧 حالت ثبت مسئولان فعال شد (برای توقف Ctrl+C).")
    print("   به مسئولان بگویید ربات را استارت بزنند و «ناحیه - سمت» خود را بفرستند.")
    print(f"   ثبت‌شده تا الان: {sum(1 for u in users.values() if u.get('label'))} نفر\n")
    offset = None
    seen = set()
    while True:
        try:
            payload = {"offset": offset} if offset else {}
            r = requests.post(f"{api.base}/getUpdates", json=payload, timeout=90)
            j = r.json() if r.content else {}
        except requests.RequestException as e:
            log.warning("getUpdates: %s — ۳ ثانیه دیگر تلاش می‌شود", e)
            time.sleep(3)
            continue
        except ValueError:
            j = {}
        for uid, chat_id, text in extract_updates(j):
            if uid is not None:
                offset = max(offset or 0, int(uid) + 1)
                if uid in seen:
                    continue
                seen.add(uid)
            if not text:
                continue
            u = users.setdefault(chat_id, {"label": None, "time": None})
            print(f"  ← {chat_id}: «{text if len(text) < 60 else text[:57] + '…'}»")
            if text in ("/start", "شروع", "/start rubika"):
                try:
                    api.send_message(chat_id, WELCOME)
                    print("     → راهنما فرستاده شد")
                except RuntimeError as e:
                    log.error("ارسال خوش‌آمد به %s: %s", chat_id, e)
            elif flat(text) in ("list", "لیست", "/list"):
                names = [u2["label"] for u2 in users.values() if u2.get("label")]
                try:
                    api.send_message(chat_id, f"ثبت‌شده‌ها ({len(names)}):\n" + "\n".join(names))
                except RuntimeError as e:
                    log.error("ارسال لیست به %s: %s", chat_id, e)
            elif "-" in text or not u.get("label"):
                # متن دریافت‌شده به‌عنوان «ناحیه - سمت» ثبت می‌شود
                u["label"] = normalize(text)
                u["time"] = datetime.now().isoformat(timespec="seconds")
                save_users(cfg, users)
                print(f"  ✅ {chat_id} → «{u['label']}»")
                try:
                    api.send_message(
                        chat_id,
                        f"✅ ثبت شد: {u['label']}\n"
                        "کارنامه‌ی ماهانه‌ی ناحیه‌تان برایتان ارسال خواهد شد.\n"
                        "برای اصلاح، /start را دوباره بفرستید.",
                    )
                except RuntimeError as e:
                    log.error("تأیید ثبت به %s: %s", chat_id, e)
        time.sleep(1.5)


# ----------------------------------------------------------------------------- ارسال


def run_send(cfg, api: BotAPI, tasks: dict, args):
    users = load_users(cfg)
    csv_rows = load_recipients_csv(cfg)

    # نگاشت کامل + گزارش موارد گمشده
    plan, missing = [], []
    for city, g in tasks.items():
        for role in cfg["roles"]:
            cid, src = match_chat_id(users, csv_rows, city, role)
            if cid:
                plan.append({"city": city, "role": role, "chat_id": cid, "src": src,
                             "images": g["images"]})
            else:
                missing.append(f"{role} {city}")

    total = sum(len(p["images"]) for p in plan)
    print(f"\n🤖 ربات: {len(plan)} مخاطبِ ثبت‌شده | {total} پیام | {len(missing)} مخاطبِ گمشده")
    if missing:
        print("⚠️ برای این‌ها گیرنده‌ای ثبت نشده (با --listen یا recipients.csv اضافه کنید):")
        for m in missing:
            print(f"   • {m}")

    if args.dry_run:
        print("\n📋 حالت آزمایشی — چیزی ارسال نمی‌شود. نقشه ارسال:")
        for p in plan:
            print(f"   {p['city']} | {p['role']} → {p['chat_id']} ({p['src']}) × {len(p['images'])} تصویر")
        print("\n✅ برای ارسال واقعی، بدون --dry-run اجرا کنید.")
        return

    if not plan:
        sys.exit("هیچ گیرنده‌ای برای ارسال نیست.")

    sent, records = load_bot_sent(cfg)
    if args.force:
        sent = set()
    stats = {"ok": 0, "fail": 0, "skip": 0}
    failures = []
    budget = args.limit
    stop = False
    try:
        for p in plan:
            if stop:
                break
            todo = [im for im in p["images"] if (im.name, p["chat_id"]) not in sent]
            if not todo:
                stats["skip"] += len(p["images"])
                continue
            if budget is not None and stats["ok"] >= budget:
                stop = True
                break
            print(f"\n🏙 {p['city']} | {p['role']} → {p['chat_id']}")
            for im in todo:
                if budget is not None and stats["ok"] >= budget:
                    stop = True
                    break
                cap = make_caption(cfg, p["city"])
                try:
                    api.send_photo(p["chat_id"], im, cap)
                    sent.add((im.name, p["chat_id"]))
                    records.append({"image": im.name, "chat_id": p["chat_id"],
                                    "time": datetime.now().isoformat(timespec="seconds")})
                    save_bot_sent(cfg, records)
                    stats["ok"] += 1
                    log.info("   ✅ %s → %s", im.name, p["chat_id"])
                except Exception as e:
                    stats["fail"] += 1
                    failures.append({"chat_id": p["chat_id"], "image": im.name, "error": str(e)})
                    log.error("   ❌ %s → %s | %s", im.name, p["chat_id"], e)
                time.sleep(random.uniform(1.5, 3.5))
            if not stop:
                time.sleep(random.uniform(2, 4))
    except KeyboardInterrupt:
        print("\n⛔ متوقف شد؛ ارسال‌های انجام‌شده ثبت هستند.")
    finally:
        save_bot_sent(cfg, records)

    print("\n" + "═" * 60)
    print(f"📊 نتیجه: ✅ {stats['ok']} ارسال شد | ❌ {stats['fail']} ناموفق | ↷ {stats['skip']} رد شد")
    if failures:
        (BASE / "debug").mkdir(exist_ok=True)
        (BASE / "debug" / "bot_failures.json").write_text(
            json.dumps(failures, ensure_ascii=False, indent=2), encoding="utf-8")
        print("   جزئیات خطاها در debug/bot_failures.json")


def make_caption(cfg, city):
    from send_web import make_caption as mc
    return mc(cfg, city)


def load_bot_sent(cfg):
    p = BASE / cfg.get("bot_sent_log", "sent_log_bot.json")
    records = []
    if p.exists():
        try:
            records = json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            records = []
    return {(r["image"], r["chat_id"]) for r in records}, records


def save_bot_sent(cfg, records):
    p = BASE / cfg.get("bot_sent_log", "sent_log_bot.json")
    p.write_text(json.dumps(records, ensure_ascii=False, indent=1), encoding="utf-8")


# ----------------------------------------------------------------------------- اجرای اصلی


def main():
    if sys.stdout and hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8")
        except Exception:
            pass

    ap = argparse.ArgumentParser(
        description="ارسال کارنامه‌ها از طریق ربات رسمی روبیکا (Bot API)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--config", default="config.json")
    ap.add_argument("--token", help="توکن ربات (یا کلید bot_token در config.json)")
    ap.add_argument("--listen", action="store_true", help="حالت ثبت مسئولان: پیام‌ها را می‌گیرد و ذخیره می‌کند")
    ap.add_argument("--dry-run", action="store_true", help="چک نقشه ارسال بدون ارسال")
    ap.add_argument("--only", nargs="+", metavar="ناحیه", help="فقط این ناحیه/نواحی")
    ap.add_argument("--limit", type=int, metavar="N", help="حداکثر N پیام در این اجرا")
    ap.add_argument("--force", action="store_true", help="بی‌توجه به سابقه، همه دوباره ارسال شود")
    ap.add_argument("--month", metavar="نام‌ماه", help="ماه را برای کپشن بازنویسی کند")
    args = ap.parse_args()

    cfg = load_config(BASE / args.config)
    if args.month:
        cfg["month"] = args.month

    fh = logging.FileHandler(BASE / "karnameh_bot.log", encoding="utf-8")
    fh.setFormatter(logging.Formatter("%(asctime)s | %(levelname)-7s | %(message)s"))
    log.addHandler(fh)
    sh = logging.StreamHandler(sys.stdout)
    sh.setFormatter(logging.Formatter("%(asctime)s | %(message)s", "%H:%M:%S"))
    log.addHandler(sh)
    log.setLevel(logging.INFO)

    token = args.token or cfg.get("bot_token")
    if not token:
        sys.exit("❌ توکن ربات داده نشد. از --token TOKEN استفاده کنید یا bot_token را در config.json بگذارید.")

    api = BotAPI(token)
    try:
        me = api.get_me()
        name = dig(me, "bot", "name") or dig(me, "name") or "?"
        print(f"🤖 اتصال به ربات برقرار شد: {name}")
    except Exception as e:
        sys.exit(f"❌ اتصال به ربات ناموفق بود (توکن را چک کنید): {e}")

    tasks = build_tasks(cfg, only=args.only)
    if not tasks:
        sys.exit("هیچ ناحیه‌ای مطابق فیلتر انتخابی پیدا نشد.")

    if args.listen:
        run_listen(cfg, api)
    else:
        run_send(cfg, api, tasks, args)


if __name__ == "__main__":
    main()
