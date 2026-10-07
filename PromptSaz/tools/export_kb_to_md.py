#!/usr/bin/env python3
"""
Export the PromptSaz/چیستا knowledge base to editable Markdown.

  python3 tools/export_kb_to_md.py            → docs/chista-knowledge-base.md (all 13 domains, one file)
  python3 tools/export_kb_to_md.py --split    → docs/kb-domains/{domain-id}.md (one file per domain)
                                                 + docs/kb-domains/README.md
  python3 tools/export_kb_to_md.py --parts 3  → docs/kb-parts/chista-kb-{i}of{n}.md
                                                 (balanced whole-domain groups that each
                                                 fit in ONE model response)

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

این فایل برای «مرور یکجا» است. برای ویرایش با هوش مصنوعی از نسخهٔ
سه‌بخشی (chista-kb-1of3.md تا chista-kb-3of3.md) استفاده کن که هر بخشش
کم‌حجم‌تر است و در یک پاسخ کامل برمی‌گردد.

---
"""

SPLIT_HEADER = """# دانش‌نامهٔ چیستا — حوزهٔ «{name_fa}»

این فایل، دانش‌نامهٔ حوزهٔ «{name_fa}» اپلیکیشن «چیستا» (ساخت پرامپت) است.
می‌خواهیم محتوای آن حرفه‌ای‌تر و کامل‌تر شود. شما فایل را ویرایش کنید و
**دقیقاً همین قالب** را در خروجی برگردانید.

{rules}
## نحوهٔ تحویل خروجی

کل همین فایل را در «یک پاسخ» و داخل «یک بلوک کد» برگردان — بدون هیچ
توضیح یا مقدمه‌ای قبل و بعدش. بخش‌ها را کم نکن؛ فقط محتوا را قوی‌تر کن.
خط پایانی «پایان فایل» را هم عیناً در انتهای خروجی نگه دار.
"""

PART_HEADER = """# دانش‌نامهٔ چیستا — بخش {i} از {n}

این فایل، بخش {i} از {n} بخش دانش‌نامهٔ اپلیکیشن «چیستا» (ساخت پرامپت)
است و شامل این حوزه‌هاست: {domain_list}.
می‌خواهیم محتوای آن حرفه‌ای‌تر و کامل‌تر شود. شما فایل را ویرایش کنید و
**دقیقاً همین قالب** را در خروجی برگردانید.

{rules}
## نحوهٔ تحویل خروجی

کل همین فایل را در «یک پاسخ» و داخل «یک بلوک کد» کامل برگردان — بدون
هیچ توضیح یا مقدمه‌ای قبل و بعدش. حوزه‌های بخش‌های دیگر را اینجا نیاور.
اگر پاسخ در میانه بریده شد، در پیام بعدی دقیقاً از همان خط ادامه بده و
چیزی را تکرار نکن. خط پایانی «پایان فایل» را هم عیناً در انتهای خروجی
نگه دار.
"""

END_MARKER = "<!-- پایان فایل: {label} — این خط باید آخرین خط خروجی باشد -->"

PARTS_README = """# دانش‌نامهٔ چیستا — نسخهٔ سه‌بخشی برای ویرایش با هوش مصنوعی

هر فایل شامل چند حوزهٔ کامل است و به‌گونه‌ای متعادل شده که مدل بتواند
کل آن را در «یک پاسخ» برگرداند.

| فایل | حوزه‌ها |
|------|---------|
{table}

## طرز کار

1. فایل بخش ۱ را در گفتگو ضمیمه کن و بنویس:
   «این فایل را طبق راهنمای ابتدای خودش کامل‌تر و حرفه‌ای‌تر کن و کل آن
   را در یک پاسخ و داخل یک بلوک کد برگردان.»
2. کل بلوک کدِ خروجی را کپی کن و در یک فایل متنی با همان نام ذخیره کن
   (`chista-kb-1of3.md`). اگر Gemini فایل Canvas ساخت، همان را دانلود کن.
3. همین کار را برای بخش ۲ و ۳ تکرار کن — اگر لازم شد هر بخش در گفتگوی
   جداگانه.
4. هر تعداد فایل که آماده شد را برگردان. حوزه‌هایی که برنگردند، همان
   نسخهٔ فعلی‌شان می‌مانند.

**اگر خروجی وسط راه بریده شد، جای نگرانی نیست:** برنامهٔ ما فقط
حوزه‌های کامل را می‌خواند و حوزهٔ ناقص را خودکار کنار می‌گذارد. همان
بخش را دوباره از مدل بخواه یا ادامهٔش را بگیر.
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


def balanced_parts(domains: list[tuple[dict, str]], n: int) -> list[list[tuple[dict, str]]]:
    """Whole domains into n bins, size-balanced (longest-processing-time)."""
    bins: list[list[tuple[dict, str]]] = [[] for _ in range(n)]
    totals = [0] * n
    for domain, description in sorted(domains, key=lambda x: -len(domain_to_md(x[0], x[1]))):
        i = totals.index(min(totals))
        bins[i].append((domain, description))
        totals[i] += len(domain_to_md(domain, description))
    # keep the registry order inside each bin; drop empty bins
    order = {d[0]["id"]: i for i, d in enumerate(domains)}
    return [sorted(b, key=lambda x: order[x[0]["id"]]) for b in bins if b]


def main() -> None:
    split = "--split" in sys.argv
    parts_mode = "--parts" in sys.argv
    domains = load_domains()

    if parts_mode:
        idx = sys.argv.index("--parts")
        n = int(sys.argv[idx + 1]) if idx + 1 < len(sys.argv) and sys.argv[idx + 1].isdigit() else 3
        out_dir = ROOT / "docs" / "kb-parts"
        out_dir.mkdir(parents=True, exist_ok=True)
        for old in out_dir.glob("*.md"):
            old.unlink()
        groups = balanced_parts(domains, n)
        table = "\n".join(
            f"| `chista-kb-{i}of{len(groups)}.md` | " +
            "، ".join(d[0]["nameFa"] for d in group) + " |"
            for i, group in enumerate(groups, start=1)
        )
        (out_dir / "README.md").write_text(PARTS_README.format(table=table), encoding="utf-8")
        for i, group in enumerate(groups, start=1):
            header = PART_HEADER.format(
                i=i, n=len(groups),
                domain_list="، ".join(d[0]["nameFa"] for d in group),
                rules=RULES,
            )
            body = "\n\n".join(domain_to_md(d, description) for d, description in group)
            path = out_dir / f"chista-kb-{i}of{len(groups)}.md"
            path.write_text(
                header + "\n---\n\n" + body + "\n---\n\n" +
                END_MARKER.format(label=f"بخش {i} از {len(groups)}") + "\n",
                encoding="utf-8",
            )
            print(f"OK — {path.name} ({path.stat().st_size:,} bytes, {len(group)} domains)")
        return

    if not split:
        parts = [HEADER_ALL]
        for domain, description in domains:
            parts.append(domain_to_md(domain, description))
        OUT_ALL.parent.mkdir(parents=True, exist_ok=True)
        OUT_ALL.write_text(
            "\n".join(parts) + "\n---\n\n" + END_MARKER.format(label="دانش‌نامهٔ کامل") + "\n",
            encoding="utf-8",
        )
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
        path.write_text(
            header + "\n---\n\n" + domain_to_md(domain, description) + "\n---\n\n" +
            END_MARKER.format(label=f"حوزهٔ {domain['nameFa']}") + "\n",
            encoding="utf-8",
        )
        print(f"OK — {path.name} ({path.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
