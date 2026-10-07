#!/usr/bin/env python3
"""
Import an AI-edited chista-knowledge-base.md back into the app's JSON files.

Merge semantics (the round-trip contract):
  - every domain section present in the Markdown REPLACES that domain's JSON
    entirely (parsed + sanity-checked first);
  - domains absent from the file stay untouched;
  - new domain ids are added (JSON + registry entry with the file's توضیح).

Usage (any number of files and/or directories; a directory contributes
all its *.md files):
  python3 tools/import_kb_from_md.py docs/chista-knowledge-base.md --check
  python3 tools/import_kb_from_md.py docs/kb-domains --check
  python3 tools/import_kb_from_md.py a.md b.md c.md --apply

--check only parses + compares against the current JSONs and prints a report.
Round-trip guarantee: export_kb_to_md.py (both modes) → this tool --check must
report every domain identical. The same domain in two input files is an error.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KB_DIR = ROOT / "app" / "src" / "main" / "assets" / "knowledge"

VALID_TARGETS = {"chatgpt", "claude", "gemini", "deepseek", "midjourney", "video", "any"}
VALID_APPLIES = {"design", "event", "calendar", "video", "text"}
ID_RE = re.compile(r"^[a-z][a-z0-9_-]*$")

SECTION_KEYS = {
    "نقش‌ها": "personas",
    "واژه‌نامه": "terminology",
    "ساختارهای خروجی": "outputStructures",
    "نگه‌بان‌ها": "guardrails",
    "حالت‌های شکست": "failureModes",
    "نمونه‌پرامپت‌ها": "examples",
    "سؤال‌های شفاف‌سازی": "clarifyingQuestions",
}
REQUIRED_ITEM_FIELDS = {
    "نقش‌ها": {"id", "titleFa", "expertiseFa", "whenToUseFa"},
    "واژه‌نامه": {"termFa", "termEn", "definitionFa"},
    "ساختارهای خروجی": {"id", "titleFa", "descriptionFa", "templateFa"},
    "نگه‌بان‌ها": {"id", "doFa", "dontFa", "whyFa"},
    "حالت‌های شکست": {"id", "symptomFa", "fixFa"},
    "نمونه‌پرامپت‌ها": {"id", "targetAi", "titleFa", "promptFa"},
    "سؤال‌های شفاف‌سازی": {"id", "questionFa", "priority"},
}


class Parse:
    def __init__(self, path: Path):
        self.lines = path.read_text(encoding="utf-8").splitlines()
        self.i = 0
        self.errors: list[str] = []

    def err(self, msg: str):
        self.errors.append(f"line {self.i + 1}: {msg}")

    def peek(self):
        return self.lines[self.i] if self.i < len(self.lines) else None

    def next(self):
        line = self.peek()
        self.i += 1
        return line


def parse_domain(p: Parse) -> dict | None:
    header = p.next()
    m = re.match(r"^## دامنه: (\S+) — (.+)$", header.strip())
    if not m:
        p.err(f"bad domain header: {header!r}")
        return None
    domain: dict = {"id": m.group(1), "nameFa": m.group(2).strip(), "status": "ready",
                    "_descriptionFa": "", "sections": {}}
    item: dict | None = None
    pending_chips: list | None = None
    section: str | None = None

    def close_item():
        nonlocal item, pending_chips
        if item is None:
            return
        where = f"{domain['id']}/{section}/{item.get('id', item.get('termFa', '?'))}"
        ok = True
        if section in REQUIRED_ITEM_FIELDS:
            missing = REQUIRED_ITEM_FIELDS[section] - set(item)
            if missing:
                p.err(f"{where}: missing fields {sorted(missing)}")
                ok = False
        if ok:
            if section == "سؤال‌های شفاف‌سازی":
                if not 1 <= item.get("priority", 0) <= 9:
                    p.err(f"{where}: priority out of 1..9")
                    ok = False
                if pending_chips is not None:
                    item["chips"] = pending_chips
                if "allowFreeText" not in item:
                    item["allowFreeText"] = True
            if ok:
                domain["sections"].setdefault(SECTION_KEYS[section], []).append(item)
        item = None
        pending_chips = None

    while True:
        line = p.peek()
        if line is None or line.startswith("## دامنه:"):
            close_item()
            break
        p.i += 1
        stripped = line.strip()

        if stripped.startswith("### "):
            close_item()
            section = stripped[4:].strip()
            if section not in SECTION_KEYS and section != "شناسنامه":
                p.err(f"{domain['id']}: unknown section {section!r}")
                section = None
            continue
        if stripped == "":
            continue

        if section == "شناسنامه":
            m = re.match(r"^- (شناسه|نام فارسی|وضعیت|توضیح): (.*)$", stripped)
            if m:
                if m.group(1) == "توضیح":
                    domain["_descriptionFa"] = m.group(2).strip()
                elif m.group(1) == "شناسه":
                    if m.group(2).strip() != domain["id"]:
                        p.err(f"{domain['id']}: شناسه mismatch {m.group(2)!r}")
                elif m.group(1) == "نام فارسی":
                    domain["nameFa"] = m.group(2).strip()
                else:
                    domain["status"] = m.group(2).strip()
            continue

        if section is None:
            continue

        m4 = re.match(r"^#### (.+)$", stripped)
        if m4:
            close_item()
            head = m4.group(1).strip()
            if section == "نقش‌ها":
                item = {"id": head.removeprefix("نقش:").strip()}
            elif section == "واژه‌نامه":
                fa, sep, en = head.removeprefix("واژه:").partition("|")
                if not sep or not fa.strip() or not en.strip():
                    p.err(f"{domain['id']}: bad واژه header {head!r}")
                    item = None
                else:
                    item = {"termFa": fa.strip(), "termEn": en.strip()}
            elif section == "ساختارهای خروجی":
                item = {"id": head.removeprefix("ساختار:").strip()}
            elif section == "نگه‌بان‌ها":
                item = {"id": head.removeprefix("نگه‌بان:").strip()}
            elif section == "حالت‌های شکست":
                item = {"id": head.removeprefix("شکست:").strip()}
            elif section == "نمونه‌پرامپت‌ها":
                m = re.match(r"^نمونه: (\S+) — هدف: (\S+)$", head)
                if not m or m.group(2) not in VALID_TARGETS:
                    p.err(f"{domain['id']}: bad نمونه header {head!r}")
                    item = None
                else:
                    item = {"id": m.group(1), "targetAi": m.group(2)}
            elif section == "سؤال‌های شفاف‌سازی":
                m = re.match(r"^سؤال: (\S+) — اولویت: (\d+)$", head)
                if not m:
                    p.err(f"{domain['id']}: bad سؤال header {head!r}")
                    item = None
                else:
                    item = {"id": m.group(1), "priority": int(m.group(2))}
            continue

        if item is None:
            continue

        m = re.match(r"^- ([^:]+): ?(.*)$", stripped)
        if section == "نقش‌ها" and m:
            key = {"عنوان": "titleFa", "تخصص": "expertiseFa", "چه‌وقت": "whenToUseFa"}.get(m.group(1))
            if key:
                item[key] = m.group(2).strip()
        elif section == "واژه‌نامه" and m and m.group(1) == "تعریف":
            item["definitionFa"] = m.group(2).strip()
        elif section == "ساختارهای خروجی" and m:
            key = {"عنوان": "titleFa", "توضیح": "descriptionFa"}.get(m.group(1))
            if key:
                item[key] = m.group(2).strip()
            elif m.group(1) == "کلیدواژه‌ها":
                item["keywordsFa"] = [x.strip() for x in m.group(2).split("،") if x.strip()]
        elif section == "نگه‌بان‌ها" and m:
            key = {"بکن": "doFa", "نکن": "dontFa", "چرا": "whyFa"}.get(m.group(1))
            if key:
                item[key] = m.group(2).strip()
        elif section == "حالت‌های شکست" and m:
            key = {"نشانه": "symptomFa", "درمان": "fixFa"}.get(m.group(1))
            if key:
                item[key] = m.group(2).strip()
        elif section == "نمونه‌پرامپت‌ها" and m and m.group(1) == "عنوان":
            item["titleFa"] = m.group(2).strip()
        elif section == "سؤال‌های شفاف‌سازی" and m:
            if m.group(1) == "پرسش":
                item["questionFa"] = m.group(2).strip()
            elif m.group(1) == "گزینه‌ها":
                pending_chips = []
                for pair in m.group(2).split("|"):
                    cid, sep, label = pair.strip().partition("=")
                    if cid and sep and label:
                        pending_chips.append({"id": cid.strip(), "labelFa": label.strip()})
                    else:
                        p.err(f"{domain['id']}/{item.get('id')}: bad chip {pair!r}")
            elif m.group(1) == "پاسخ آزاد":
                item["allowFreeText"] = m.group(2).strip() != "خیر"
            elif m.group(1) == "کلیدواژه‌های خلأ":
                item["gapKeywordsFa"] = [x.strip() for x in m.group(2).split("،") if x.strip()]
            elif m.group(1) == "فقط برای":
                values = [x.strip() for x in m.group(2).split("،") if x.strip()]
                bad = [v for v in values if v not in VALID_APPLIES]
                if bad:
                    p.err(f"{domain['id']}/{item.get('id')}: bad appliesTo {bad}")
                else:
                    item["appliesTo"] = values
        elif stripped == "الگو:" and section == "ساختارهای خروجی":
            item["templateFa"] = read_fence(p, "الگو", f"{domain['id']}/{item.get('id')}")
        elif stripped == "```پرامپت" and section == "نمونه‌پرامپت‌ها":
            p.i -= 1
            item["promptFa"] = read_fence(p, "پرامپت", f"{domain['id']}/{item.get('id')}")

    return domain


def read_fence(p: Parse, tag: str, where: str) -> str:
    """Consume a ```{tag} … ``` block starting at/after the current line."""
    while p.peek() is not None and p.peek().strip() == "":
        p.i += 1
    opener = p.peek()
    if opener is None or opener.strip() != f"```{tag}":
        p.err(f"{where}: expected ```{tag} fence")
        return ""
    p.i += 1
    body: list[str] = []
    while True:
        line = p.next()
        if line is None:
            p.err(f"{where}: fence ```{tag} never closes")
            return ""
        if line.strip() == "```":
            break
        body.append(line)
    return "\n".join(body).strip("\n")


# Keys whose absence == empty list (schema/Kotlin defaults) — for comparison.
LIST_KEYS = {"chips", "gapKeywordsFa", "keywordsFa", "appliesTo"}


def normalize(obj):
    """Make missing-default and explicit-default representations equal."""
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            if k in LIST_KEYS and v == []:
                continue
            if k == "allowFreeText" and v is True:
                continue
            out[k] = normalize(v)
        return out
    if isinstance(obj, list):
        return [normalize(x) for x in obj]
    return obj


def collect_paths(args: list[str]) -> list[Path]:
    paths: list[Path] = []
    for a in args:
        p = Path(a)
        if p.is_dir():
            paths += sorted(p.glob("*.md"))
        else:
            paths.append(p)
    return paths


def main() -> None:
    args = sys.argv[1:]
    if not args or args[-1] not in ("--check", "--apply"):
        print(__doc__)
        sys.exit(2)
    apply = args[-1] == "--apply"
    paths = collect_paths(args[:-1])
    if not paths:
        print("no input files")
        sys.exit(2)

    domains: list[dict] = []
    errors: list[str] = []
    seen_in: dict[str, str] = {}
    for md_path in paths:
        if not md_path.exists():
            print(f"✗ file not found: {md_path}")
            sys.exit(2)
        p = Parse(md_path)
        while p.peek() is not None and not p.peek().startswith("## دامنه:"):
            p.i += 1
        while p.peek() is not None:
            d = parse_domain(p)
            if d:
                if d["id"] in seen_in:
                    errors.append(
                        f"{md_path.name}: domain {d['id']} already came from {seen_in[d['id']]}"
                    )
                seen_in[d["id"]] = md_path.name
                domains.append(d)
        errors += p.errors

    if errors:
        print("PARSE ERRORS:")
        for e in errors[:40]:
            print("  ✗", e)
        sys.exit(1)

    registry = json.loads((KB_DIR / "_registry.json").read_text(encoding="utf-8"))
    by_id = {e["id"]: e for e in registry["domains"]}

    identical, changed, added = [], [], []
    for d in domains:
        description = d.pop("_descriptionFa")
        sections = d.pop("sections")
        did = d["id"]
        if not ID_RE.match(did):
            print(f"✗ invalid domain id {did!r}")
            sys.exit(1)
        ordered = {"id": did, "nameFa": d["nameFa"], "status": d["status"]}
        for key in ["personas", "terminology", "outputStructures", "guardrails",
                    "failureModes", "examples", "clarifyingQuestions"]:
            ordered[key] = sections.get(key, [])

        path = KB_DIR / f"{did}.json"
        if path.exists():
            current = json.loads(path.read_text(encoding="utf-8"))
            same = normalize(current) == normalize(ordered) and \
                by_id.get(did, {}).get("descriptionFa", "") == description
            if same:
                identical.append(did)
            else:
                changed.append(did)
                if apply:
                    path.write_text(json.dumps(ordered, ensure_ascii=False, indent=2) + "\n",
                                    encoding="utf-8")
                    if did in by_id:
                        by_id[did]["descriptionFa"] = description
                        by_id[did]["nameFa"] = ordered["nameFa"]
        else:
            added.append(did)
            if apply:
                path.write_text(json.dumps(ordered, ensure_ascii=False, indent=2) + "\n",
                                encoding="utf-8")
                registry["domains"].append({
                    "id": did,
                    "nameFa": ordered["nameFa"],
                    "descriptionFa": description,
                    "status": ordered["status"],
                    "kbFile": f"{did}.json",
                })

    if apply and (changed or added):
        (KB_DIR / "_registry.json").write_text(
            json.dumps(registry, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"parsed {len(domains)} domains — identical: {len(identical)}, "
          f"changed: {len(changed)} {changed}, new: {len(added)} {added}")
    if apply:
        print("APPLIED ✓ — now run: python3 tools/validate_kb.py")
    sys.exit(0)


if __name__ == "__main__":
    main()
