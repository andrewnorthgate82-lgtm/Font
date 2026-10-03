#!/usr/bin/env python3
"""
PromptSaz knowledge-base validator.

Validates every knowledge base JSON under app/src/main/assets/knowledge/:
  - registry consistency (ids unique, statuses valid, kbFile exists for ready domains)
  - schema conformance (required fields, types, id patterns)
  - content minimums per domain (real content, not placeholders)
  - Persian text sanity for *Fa fields (non-empty, contains Persian characters)
  - example prompt quality floor (>= 300 chars)

Run:  python3 tools/validate_kb.py
Exit code 0 = all good; 1 = validation errors found.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KB_DIR = ROOT / "app" / "src" / "main" / "assets" / "knowledge"

ID_PATTERN = re.compile(r"^[a-z][a-z0-9_-]*$")
PERSIAN_PATTERN = re.compile(r"[\u0600-\u06FF]")
VALID_TARGETS = {"chatgpt", "claude", "gemini", "deepseek", "midjourney", "video", "any"}

# Content minimums: full domains vs the lighter general KB.
FULL_MINIMUMS = {
    "personas": 4, "terminology": 10, "outputStructures": 4,
    "guardrails": 8, "failureModes": 5, "examples": 3, "clarifyingQuestions": 6,
}
GENERAL_MINIMUMS = {
    "personas": 3, "terminology": 5, "outputStructures": 3,
    "guardrails": 5, "failureModes": 3, "examples": 2, "clarifyingQuestions": 4,
}

errors: list[str] = []
stats: list[str] = []


def err(message: str) -> None:
    errors.append(message)


def check_persian(field: str, value: str, where: str, min_len: int = 2) -> None:
    if not value or len(value.strip()) < min_len:
        err(f"{where}: field '{field}' is empty or too short")
        return
    if not PERSIAN_PATTERN.search(value):
        err(f"{where}: field '{field}' has no Persian characters: {value[:40]!r}")


def check_id(value: str, where: str) -> None:
    if not ID_PATTERN.match(value):
        err(f"{where}: invalid id {value!r} (must match {ID_PATTERN.pattern})")


def check_unique_ids(items: list[dict], key: str, where: str) -> None:
    seen = set()
    for i, item in enumerate(items):
        item_id = item.get("id")
        if item_id is None:
            if key in ("personas", "outputStructures", "guardrails", "failureModes", "examples", "clarifyingQuestions"):
                err(f"{where}[{i}]: missing 'id'")
            continue
        if item_id in seen:
            err(f"{where}[{i}]: duplicate id {item_id!r}")
        seen.add(item_id)


def validate_domain(kb: dict, file_name: str) -> None:
    where = f"domain '{kb.get('id', file_name)}'"
    domain_id = kb.get("id", "")

    if kb.get("status") != "ready":
        err(f"{where}: status must be 'ready' in a shipped kb file")

    required = ["id", "nameFa", "status", "personas", "terminology",
                "outputStructures", "guardrails", "failureModes",
                "examples", "clarifyingQuestions"]
    for field in required:
        if field not in kb:
            err(f"{where}: missing required field '{field}'")

    check_id(domain_id, where)
    check_persian("nameFa", kb.get("nameFa", ""), where, min_len=3)

    minimums = GENERAL_MINIMUMS if domain_id == "general" else FULL_MINIMUMS

    # personas
    personas = kb.get("personas", [])
    for i, p in enumerate(personas):
        w = f"{where}.personas[{i}]"
        check_id(p.get("id", ""), w)
        check_persian("titleFa", p.get("titleFa", ""), w, min_len=5)
        check_persian("expertiseFa", p.get("expertiseFa", ""), w, min_len=20)
        check_persian("whenToUseFa", p.get("whenToUseFa", ""), w, min_len=15)
    check_unique_ids(personas, "id", f"{where}.personas")

    # terminology
    for i, t in enumerate(kb.get("terminology", [])):
        w = f"{where}.terminology[{i}]"
        check_persian("termFa", t.get("termFa", ""), w)
        if not t.get("termEn", "").strip():
            err(f"{w}: empty termEn")
        check_persian("definitionFa", t.get("definitionFa", ""), w, min_len=20)

    # output structures
    structures = kb.get("outputStructures", [])
    for i, s in enumerate(structures):
        w = f"{where}.outputStructures[{i}]"
        check_id(s.get("id", ""), w)
        check_persian("titleFa", s.get("titleFa", ""), w, min_len=3)
        check_persian("descriptionFa", s.get("descriptionFa", ""), w, min_len=20)
        check_persian("templateFa", s.get("templateFa", ""), w, min_len=40)
        keywords = s.get("keywordsFa", [])
        if not isinstance(keywords, list) or any(
            not isinstance(k, str) or len(k.strip()) < 2 for k in keywords
        ):
            err(f"{w}: 'keywordsFa' must be a list of non-empty strings")
    check_unique_ids(structures, "id", f"{where}.outputStructures")

    # guardrails
    guardrails = kb.get("guardrails", [])
    for i, g in enumerate(guardrails):
        w = f"{where}.guardrails[{i}]"
        check_id(g.get("id", ""), w)
        check_persian("doFa", g.get("doFa", ""), w, min_len=15)
        check_persian("dontFa", g.get("dontFa", ""), w, min_len=10)
        check_persian("whyFa", g.get("whyFa", ""), w, min_len=15)
    check_unique_ids(guardrails, "id", f"{where}.guardrails")

    # failure modes
    modes = kb.get("failureModes", [])
    for i, m in enumerate(modes):
        w = f"{where}.failureModes[{i}]"
        check_id(m.get("id", ""), w)
        check_persian("symptomFa", m.get("symptomFa", ""), w, min_len=15)
        check_persian("fixFa", m.get("fixFa", ""), w, min_len=15)
    check_unique_ids(modes, "id", f"{where}.failureModes")

    # examples
    examples = kb.get("examples", [])
    for i, e in enumerate(examples):
        w = f"{where}.examples[{i}]"
        check_id(e.get("id", ""), w)
        check_persian("titleFa", e.get("titleFa", ""), w, min_len=5)
        if e.get("targetAi") not in VALID_TARGETS:
            err(f"{w}: invalid targetAi {e.get('targetAi')!r}")
        prompt = e.get("promptFa", "")
        check_persian("promptFa", prompt, w, min_len=50)
        if len(prompt) < 300:
            err(f"{w}: example prompt too short ({len(prompt)} chars, need >= 300)")
    check_unique_ids(examples, "id", f"{where}.examples")

    # clarifying questions
    questions = kb.get("clarifyingQuestions", [])
    for i, q in enumerate(questions):
        w = f"{where}.clarifyingQuestions[{i}]"
        check_id(q.get("id", ""), w)
        check_persian("questionFa", q.get("questionFa", ""), w, min_len=10)
        priority = q.get("priority")
        if not isinstance(priority, int) or not 1 <= priority <= 9:
            err(f"{w}: priority must be int 1..9, got {priority!r}")
        chips = q.get("chips", [])
        if not isinstance(chips, list):
            err(f"{w}: chips must be a list")
        else:
            for j, c in enumerate(chips):
                if not c.get("id"):
                    err(f"{w}.chips[{j}]: missing id")
                check_persian("labelFa", c.get("labelFa", ""), f"{w}.chips[{j}]")
            chip_ids = [c.get("id") for c in chips]
            if len(chip_ids) != len(set(chip_ids)):
                err(f"{w}: duplicate chip ids")
        if not chips and q.get("allowFreeText") is False:
            err(f"{w}: no chips and allowFreeText=false leaves the user no way to answer")
    check_unique_ids(questions, "id", f"{where}.clarifyingQuestions")

    # content minimums
    for section, minimum in minimums.items():
        actual = len(kb.get(section, []))
        if actual < minimum:
            err(f"{where}: {section} has {actual} entries, minimum is {minimum}")
        stats.append(
            f"  {domain_id or file_name}.{section}: {actual} (min {minimum})"
        )


def main() -> int:
    if not KB_DIR.is_dir():
        err(f"knowledge dir not found: {KB_DIR}")
        print("\n".join(errors))
        return 1

    registry_path = KB_DIR / "_registry.json"
    try:
        registry = json.loads(registry_path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001
        err(f"_registry.json: cannot parse ({exc})")
        print("\n".join(errors))
        return 1

    if registry.get("version") != 1:
        err(f"_registry.json: version must be 1, got {registry.get('version')!r}")

    domains = registry.get("domains", [])
    if not isinstance(domains, list) or not domains:
        err("_registry.json: 'domains' must be a non-empty list")
        print("\n".join(errors))
        return 1

    seen_ids = set()
    ready_files = set()
    for i, entry in enumerate(domains):
        w = f"_registry.domains[{i}]"
        domain_id = entry.get("id", "")
        check_id(domain_id, w)
        if domain_id in seen_ids:
            err(f"{w}: duplicate domain id {domain_id!r}")
        seen_ids.add(domain_id)
        check_persian("nameFa", entry.get("nameFa", ""), w, min_len=3)
        check_persian("descriptionFa", entry.get("descriptionFa", ""), w, min_len=10)
        if entry.get("status") not in ("ready", "coming_soon"):
            err(f"{w}: invalid status {entry.get('status')!r}")
        if entry.get("status") == "ready":
            kb_file = entry.get("kbFile")
            if not kb_file:
                err(f"{w}: ready domain without kbFile")
            else:
                path = KB_DIR / kb_file
                if not path.is_file():
                    err(f"{w}: kbFile '{kb_file}' does not exist")
                else:
                    ready_files.add(kb_file)
                    try:
                        kb = json.loads(path.read_text(encoding="utf-8"))
                    except Exception as exc:  # noqa: BLE001
                        err(f"{kb_file}: cannot parse ({exc})")
                        continue
                    if kb.get("id") != domain_id:
                        err(f"{kb_file}: id {kb.get('id')!r} != registry id {domain_id!r}")
                    validate_domain(kb, kb_file)

    # stray kb files not referenced as ready?
    for path in KB_DIR.glob("*.json"):
        if path.name in ("_registry.json", "kb.schema.json"):
            continue
        if path.name not in ready_files:
            err(f"{path.name}: file exists but no ready registry entry points to it")

    ready_count = sum(1 for d in domains if d.get("status") == "ready")
    soon_count = len(domains) - ready_count
    stats.append(f"  registry: {len(domains)} domains ({ready_count} ready, {soon_count} coming_soon)")

    print("PromptSaz knowledge-base validation")
    print("===================================")
    for line in stats:
        print(line)

    if errors:
        print(f"\nFAILED with {len(errors)} error(s):")
        for e in errors:
            print(f"  - {e}")
        return 1
    print("\nOK — all knowledge bases are valid and meet content minimums.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
