#!/usr/bin/env python3
"""
Export the PromptSaz/چیستا knowledge base to editable Markdown.

  python3 tools/export_kb_to_md.py            → docs/chista-knowledge-base.md (all 13 domains, one file)
  python3 tools/export_kb_to_md.py --split    → docs/kb-domains/{domain-id}.md (one file per domain)
                                                 + docs/kb-domains/README.md

Both forms carry the same editing contract in their header and both are read
back by tools/import_kb_from_md.py (which accepts any number of files/dirs).

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
OUT_ALL = ROOT / "docs" / "chista-knowledge-base.md"
OUT_SPLIT_DIR = ROOT / "docs" / "kb-domains"

RULES = """## قوانین طلایی (تخطی = بی‌اعتبار شدن فایل)

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
"""

HEADER_ALL = f"""# دانش‌نامهٔ حوزه‌های چیستا — نسخهٔ قابل‌ویرایش برای هوش مصنوعی

این فایل، کل دانش‌نامهٔ ۱۳ حوزهٔ اپلیکیشن «چیستا» (ساخت پرامپت) است.
می‌خواهیم محتوای آن حرفه‌ای‌تر و کامل‌تر شود. شما فایل را ویرایش کنید و
**دقیقاً همین قالب** را در خروجی برگردانید.

{RULES}
## نحوهٔ تحویل خروجی

کل فایل را با همین قالب برگردان. اگر طولانی شد، از یک حوزهٔ کامل تمام‌شده
پایان بده و در پیام بعدی از سرِ همان حوزهٔ بعدی ادامه بده — هر بخشِ
تمام‌شدهٔ حوزه، مستقلاً معتبر است و جایگزین همان حوزه در برنامه می‌شود.
دامنه‌هایی که در خروجی نیایند، دست‌نخورده باقی می‌مانند.

---
"""

SPLIT_HEADER = """# دانش‌نامهٔ چیستا — حوزهٔ «{name_fa}»

این فایل، دانش‌نامهٔ حوزهٔ «{name_fa}» اپلیکیشن «چیستا» (ساخت پرامپت) است.
می‌خواهیم محتوای آن حرفه‌ای‌تر و کامل‌تر شود. شما فایل را ویرایش کنید و
**دقیقاً همین قالب** را در خروجی برگردانید.

{rules}
## نحوهٔ تحویل خروجی

همین فایل را کامل و با همان قالب برگردان — فقط همین یک حوزه. بخش‌ها را
کم نکن؛ فقط محتوا را قوی‌تر کن.
"""

README_MD = """# فایل‌های دانش‌نامهٔ چیستا — یک فایل برای هر حوزه

هر فایل `{{id}}.md` دانش‌نامهٔ کامل یک حوزه است و مستقلاً قابل‌ویرایش و
جایگزینی است. راهنمای ویرایش داخل خود هر فایل آمده است.

| فایل | حوزه |
|------|------|
{table}

## طرز کار

1. فایل یک حوزه را به هوش مصنوعی بده و بگو: «این فایل را طبق راهنمای
   ابتدای خودش کامل‌تر و حرفه‌ای‌تر کن و با همان قالب برگردان.»
2. خروجی را ذخیره کن (نام فایل مهم نیست، محتوا مهم است).
3. فایل‌های ویرایش‌شده را برگردان — هر تعداد که بود. هر حوزه‌ای که برنگردد،
   همان نسخهٔ فعلی می‌ماند. حوزهٔ جدید هم می‌توانی اضافه کنی.
"""


def field(label: str, value: str) -> str:
    value = value.strip()
    if "\n" in value:
        raise SystemExit(f"FATAL: multi-line value in single-line field '{label}': {value[:60]}…")
    return f"- {label}: {value}"


def fence(tag: str, text: str) -> str:
    return f"```{tag}\n{text.strip(chr(10))}\n```"


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


def load_domains() -> list[tuple[dict, str]]:
    registry = json.loads((KB_DIR / "_registry.json").read_text(encoding="utf-8"))
    result = []
    for entry in registry["domains"]:
        domain = json.loads((KB_DIR / entry["kbFile"]).read_text(encoding="utf-8"))
        assert domain["id"] == entry["id"], f"id mismatch: {domain['id']} vs {entry['id']}"
        result.append((domain, entry["descriptionFa"]))
    return result


def main() -> None:
    split = "--split" in sys.argv
    domains = load_domains()

    if not split:
        parts = [HEADER_ALL]
        for domain, description in domains:
            parts.append(domain_to_md(domain, description))
        OUT_ALL.parent.mkdir(parents=True, exist_ok=True)
        OUT_ALL.write_text("\n".join(parts), encoding="utf-8")
        print(f"OK — wrote {OUT_ALL} ({OUT_ALL.stat().st_size:,} bytes, {len(domains)} domains)")
        return

    OUT_SPLIT_DIR.mkdir(parents=True, exist_ok=True)
    for old in OUT_SPLIT_DIR.glob("*.md"):
        old.unlink()
    table = "\n".join(
        f"| `{entry['id']}.md` | {entry['nameFa']} |"
        for entry in json.loads((KB_DIR / "_registry.json").read_text(encoding="utf-8"))["domains"]
    )
    (OUT_SPLIT_DIR / "README.md").write_text(README_MD.format(table=table), encoding="utf-8")
    for domain, description in domains:
        header = SPLIT_HEADER.format(name_fa=domain["nameFa"], rules=RULES)
        path = OUT_SPLIT_DIR / f"{domain['id']}.md"
        path.write_text(header + "\n---\n\n" + domain_to_md(domain, description), encoding="utf-8")
        print(f"OK — {path.name} ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
