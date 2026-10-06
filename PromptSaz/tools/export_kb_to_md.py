#!/usr/bin/env python3
"""
Export the whole PromptSaz/چیستا knowledge base into ONE editable Markdown
file (docs/chista-knowledge-base.md) that can be handed to an LLM
(Gemini Pro, …) for improvement, then brought back with
tools/import_kb_from_md.py — a strict parser that regenerates the JSONs.

Format contract (do not change one side without the other!):
  ## دامنه: {id} — {nameFa}
  ### شناسنامه            → شناسه / نام فارسی / وضعیت / توضیح
  ### نقش‌ها               → #### نقش: {id} → عنوان / تخصص / چه‌وقت
  ### واژه‌نامه            → #### واژه: {fa} | {en} → تعریف
  ### ساختارهای خروجی     → #### ساختار: {id} → عنوان / توضیح / کلیدواژه‌ها / ```الگو fence
  ### نگه‌بان‌ها            → #### نگه‌بان: {id} → بکن / نکن / چرا
  ### حالت‌های شکست        → #### شکست: {id} → نشانه / درمان
  ### نمونه‌پرامپت‌ها       → #### نمونه: {id} — هدف: {targetAi} → عنوان / ```پرامپت fence
  ### سؤال‌های شفاف‌سازی    → #### سؤال: {id} — اولویت: {n} → پرسش / گزینه‌ها / پاسخ آزاد /
                            کلیدواژه‌های خلأ / فقط برای

Round-trip guarantee: export → import --check must report every domain
identical to the source JSONs.
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KB_DIR = ROOT / "app" / "src" / "main" / "assets" / "knowledge"
OUT = ROOT / "docs" / "chista-knowledge-base.md"

HEADER = """# دانش‌نامهٔ حوزه‌های چیستا — نسخهٔ قابل‌ویرایش برای هوش مصنوعی

این فایل، کل دانش‌نامهٔ ۱۳ حوزهٔ اپلیکیشن «چیستا» (ساخت پرامپت) است.
می‌خواهیم محتوای آن حرفه‌ای‌تر و کامل‌تر شود. شما فایل را ویرایش کنید و
**دقیقاً همین قالب** را در خروجی برگردانید.

## قوانین طلایی (تخطی = بی‌اعتبار شدن فایل)

1. **ساختار عنوان‌ها را دقیقاً حفظ کن**: `## دامنه:`، `### شناسنامه`،
   `### نقش‌ها`، `#### نقش:`، `### واژه‌نامه`، `#### واژه:`،
   `### ساختارهای خروجی`، `#### ساختار:`، `### نگه‌بان‌ها`، `#### نگه‌بان:`،
   `### حالت‌های شکست`، `#### شکست:`، `### نمونه‌پرامپت‌ها`، `#### نمونه:`،
   `### سؤال‌های شفاف‌سازی`، `#### سؤال:` — نه کمتر، نه بیشتر، نه نام‌های دیگر.
2. **برچسب فیلدها را عیناً نگه دار**: `شناسه`، `نام فارسی`، `وضعیت`، `توضیح`،
   `عنوان`، `تخصص`، `چه‌وقت`، `تعریف`، `توضیح`، `کلیدواژه‌ها`، `الگو`،
   `بکن`، `نکن`، `چرا`، `نشانه`، `درمان`، `پرسش`، `گزینه‌ها`، `پاسخ آزاد`،
   `کلیدواژه‌های خلأ`، `فقط برای`، `هدف`، `اولویت`.
3. **شناسه‌های لاتین (id) موجود را تغییر نده**؛ برای موارد جدید، شناسهٔ
   لاتینِ کوتاه با خط تیره بساز (مثل `advanced-audience-targeting`).
4. تمام محتوا **فارسی روان و حرفه‌ای** است؛ فقط `termEn` (معادل انگلیسی
   واژه)، شناسه‌ها و مقادیر enum انگلیسی‌اند.
5. مقادیر مجاز `هدف` (targetAi): `chatgpt` | `claude` | `gemini` | `deepseek`
   | `midjourney` | `video` | `any`
6. مقادیر مجاز `فقط برای` (appliesTo): `design` | `event` | `calendar` |
   `video` | `text` — خالی یعنی «همهٔ موارد».
7. `اولویت` عددی بین ۱ تا ۹ است (۱ = مهم‌ترین سؤال).
8. متن‌های چندخطی فقط داخل بلوک کد با برچسب مشخص:
   الگوی ساختار داخل ```الگو و پرامپت نمونه داخل ```پرامپت — هیچ متن
   چندخطیِ دیگری مجاز نیست و بقیهٔ فیلدها تک‌خطی‌اند.
9. حداقل‌ها برای هر حوزهٔ تخصصی: ۴ نقش، ۱۱ واژه، ۴ ساختار، ۸ نگه‌بان،
   ۵ حالت شکست، ۵ نمونه‌پرامپت، ۱۰ سؤال شفاف‌سازی (حوزهٔ عمومی: ۳/۵/۳/۵/۳/۲/۴).
   بیشتر اشکال ندارد؛ کمتر نه.
10. هیچ دامنه‌ای را حذف نکن و ترتیب بخش‌ها را عوض نکن.

## چه چیزی را بهتر کن؟

- `تخصص` نقش‌ها را عمیق‌تر و متمایزتر از هم کن (نه تعریف کلی).
- واژه‌نامه را کامل کن (اصطلاحات واقعیِ رایج حوزه، با تعریف دقیق یک‌خطی).
- `الگو`های ساختار خروجی را کاربردی و قابل‌کپی کن با جای‌نگهدار `[داخل کروشه]`.
- نگه‌بان‌ها: «بکن/نکن» مشخص و قابل‌اجرا + «چرا» یک‌خطی.
- نمونه‌پرامپت‌ها: متن کامل و آمادهٔ استفاده (هر کدام حداقل ۳۰۰ کاراکتر)،
  با ساختار نقش/هدف/زمینه/روند کار/قالب خروجی/محدودیت‌ها/معیار کیفیت، و
  جای‌نگهدارهای `[مثل این]` برای چیزی که کاربر باید پر کند. برای هر حوزه
  حداقل یک نمونه برای مدل تصویری (`midjourney`) یا ویدیویی (`video`) اگر
  حوزه مناسب است.
- سؤال‌های شفاف‌سازی: پرسش‌های واقعاً لازم برای کامل‌کردن پرامپت؛ گزینه‌ها
  (chips) کوتاه و مفهومی؛ کلیدواژه‌های خلأ = واژه‌هایی که اگر در ایدهٔ کاربر
  بودند، یعنی این سؤال باید پرسیده شود.

## نحوهٔ تحویل خروجی

کل فایل را با همین قالب برگردان. اگر طولانی شد، از یک حوزهٔ کامل تمام‌شده
پایان بده و در پیام بعدی از سرِ همان حوزهٔ بعدی ادامه بده — هر بخشِ
تمام‌شدهٔ حوزه، مستقلاً معتبر است و جایگزین همان حوزه در برنامه می‌شود.
دامنه‌هایی که در خروجی نیایند، دست‌نخورده باقی می‌مانند.

---

"""


def field(label: str, value: str) -> str:
    value = value.strip()
    if "\n" in value:
        raise SystemExit(f"FATAL: multi-line value in single-line field '{label}': {value[:60]}…")
    return f"- {label}: {value}"


def fence(tag: str, text: str) -> str:
    body = text.strip("\n")
    return f"```{tag}\n{body}\n```"


def domain_to_md(domain: dict, description: str) -> str:
    lines = [f"## دامنه: {domain['id']} — {domain['nameFa']}", ""]
    lines += ["### شناسنامه"]
    lines.append(field("شناسه", domain["id"]))
    lines.append(field("نام فارسی", domain["nameFa"]))
    lines.append(field("وضعیت", domain["status"]))
    lines.append(field("توضیح", description))
    lines.append("")

    lines += ["### نقش‌ها"]
    for p in domain["personas"]:
        lines.append(f"#### نقش: {p['id']}")
        lines.append(field("عنوان", p["titleFa"]))
        lines.append(field("تخصص", p["expertiseFa"]))
        lines.append(field("چه‌وقت", p["whenToUseFa"]))
    lines.append("")

    lines += ["### واژه‌نامه"]
    for t in domain["terminology"]:
        lines.append(f"#### واژه: {t['termFa']} | {t['termEn']}")
        lines.append(field("تعریف", t["definitionFa"]))
    lines.append("")

    lines += ["### ساختارهای خروجی"]
    for s in domain["outputStructures"]:
        lines.append(f"#### ساختار: {s['id']}")
        lines.append(field("عنوان", s["titleFa"]))
        lines.append(field("توضیح", s["descriptionFa"]))
        if s.get("keywordsFa"):
            lines.append(field("کلیدواژه‌ها", "، ".join(s["keywordsFa"])))
        lines.append("الگو:")
        lines.append(fence("الگو", s["templateFa"]))
    lines.append("")

    lines += ["### نگه‌بان‌ها"]
    for g in domain["guardrails"]:
        lines.append(f"#### نگه‌بان: {g['id']}")
        lines.append(field("بکن", g["doFa"]))
        lines.append(field("نکن", g["dontFa"]))
        lines.append(field("چرا", g["whyFa"]))
    lines.append("")

    lines += ["### حالت‌های شکست"]
    for m in domain["failureModes"]:
        lines.append(f"#### شکست: {m['id']}")
        lines.append(field("نشانه", m["symptomFa"]))
        lines.append(field("درمان", m["fixFa"]))
    lines.append("")

    lines += ["### نمونه‌پرامپت‌ها"]
    for e in domain["examples"]:
        lines.append(f"#### نمونه: {e['id']} — هدف: {e['targetAi']}")
        lines.append(field("عنوان", e["titleFa"]))
        lines.append(fence("پرامپت", e["promptFa"]))
    lines.append("")

    lines += ["### سؤال‌های شفاف‌سازی"]
    for q in domain["clarifyingQuestions"]:
        lines.append(f"#### سؤال: {q['id']} — اولویت: {q['priority']}")
        lines.append(field("پرسش", q["questionFa"]))
        if q.get("chips"):
            lines.append(field("گزینه‌ها", " | ".join(f"{c['id']}={c['labelFa']}" for c in q["chips"])))
        lines.append(field("پاسخ آزاد", "بله" if q.get("allowFreeText", True) else "خیر"))
        if q.get("gapKeywordsFa"):
            lines.append(field("کلیدواژه‌های خلأ", "، ".join(q["gapKeywordsFa"])))
        if q.get("appliesTo"):
            lines.append(field("فقط برای", "، ".join(q["appliesTo"])))
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    registry = json.loads((KB_DIR / "_registry.json").read_text(encoding="utf-8"))
    parts = [HEADER]
    for entry in registry["domains"]:
        kb_file = KB_DIR / entry["kbFile"]
        domain = json.loads(kb_file.read_text(encoding="utf-8"))
        assert domain["id"] == entry["id"], f"id mismatch: {domain['id']} vs {entry['id']}"
        parts.append(domain_to_md(domain, entry["descriptionFa"]))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(parts), encoding="utf-8")
    print(f"OK — wrote {OUT} ({OUT.stat().st_size:,} bytes, {len(registry['domains'])} domains)")


if __name__ == "__main__":
    main()
