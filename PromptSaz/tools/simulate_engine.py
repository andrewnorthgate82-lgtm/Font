#!/usr/bin/env python3
"""Offline replica of the PromptSaz engine (assembler + markdown renderer + scorer)
used to verify every audit-test assertion before CI. Ports the Kotlin logic 1:1."""
import json, re
from pathlib import Path

BASE = Path("app/src/main/assets/knowledge")

# ---------------- shared helpers ----------------
def to_persian_digits(s):
    return "".join("۰۱۲۳۴۵۶۷۸۹"[int(c)] if c in "0123456789" else c for c in s)

def to_english_digits(s):
    out = []
    for c in s:
        if "۰" <= c <= "۹": out.append(chr(48 + ord(c) - ord("۰")))
        elif "٠" <= c <= "٩": out.append(chr(48 + ord(c) - ord("٠")))
        else: out.append(c)
    return "".join(out)

def tokenize(text):
    text = text.replace("ي", "ی").replace("ك", "ک")
    return {p.lower() for p in re.split(r"[^\w]+", text, flags=re.UNICODE) if len(p) > 2}

# ---------------- IdeaFacts (label-based) ----------------
MONTHS = ["فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند"]
WORDNUM = {"یک":"۱","دو":"۲","سه":"۳","چهار":"۴","پنج":"۵","شش":"۶","هفت":"۷","هشت":"۸","نه":"۹","ده":"۱۰",
           "دوازده":"۱۲","چهارده":"۱۴","پانزده":"۱۵","بیست":"۲۰","سی":"۳۰","چهل":"۴۰","پنجاه":"۵۰"}
PLATFORMS = ["اینستاگرام","تلگرام","واتساپ","لینکدین","یوتیوب","توییتر","ایکس","فیسبوک","آپارات","وبسایت","وب‌سایت","سایت","وبلاگ","پادکست"]
EVENT_RE = re.compile("(دوره|وبینار|کارگاه|سمینار|کلاس|همایش|جلسه|رویداد|جشن|مراسم|کنسرت|نمایشگاه)(?:\s+(آموزشی|تخصصی|آنلاین|حضوری|معرفی))?")
DATE_FALLBACK = re.compile("[۰-۹0-9]{1,2}\s*(" + "|".join(MONTHS) + ")")
DURATION_FALLBACK = re.compile("(?:به\s*مدت|مدت)\s*([۰-۹0-9]{1,3}|" + "|".join(WORDNUM) + ")\s*(روز|هفته|ماه|ساعت|دقیقه|ثانیه)")
PRICE_RE = re.compile("([۰-۹0-9،٬]+\s*(?:تومان|ریال|میلیون))")

LABELS = [
    (re.compile("موضوع\s*(?:آموزشی|اصلی)?"), "SUBJECT", "موضوع", False),
    (re.compile("(?:مدرس|استاد|سخنران|برگزارکننده|گوینده)"), "INSTRUCTOR", "مدرس", False),
    (re.compile("تاریخ\s*(?:برگزاری|شروع)?"), "DATE", "تاریخ", False),
    (re.compile("ساعت\s*(?:برگزاری)?"), "TIME", "ساعت", False),
    (re.compile("مدت(?:\s*زمان)?"), "DURATION", "مدت", False),
    (re.compile("(?:مکان|محل)(?:\s*برگزاری)?"), "LOCATION", "مکان", False),
    (re.compile("بستر(?:\s*(?:و\s*)?(?:تبلیغ|انتشار|نشر))*"), "PLATFORM", "بستر انتشار", False),
    (re.compile("(?:پلتفرم|کانال\s*انتشار)"), "PLATFORM", "پلتفرم", False),
    (re.compile("(?:قیمت|هزینه)"), "PRICE", "قیمت", False),
    (re.compile("مخاطبان?"), "AUDIENCE", "مخاطب", False),
    (re.compile("(?:ثبت[‌ ]?نام|راه\s*ارتباطی|شماره\s*تماس|لینک\s*ثبت)"), "REGISTRATION", "راه ثبت‌نام", False),
    (re.compile("حضوری"), "MODE", "شیوه برگزاری", True),
    (re.compile("آنلاین"), "MODE", "شیوه برگزاری", True),
    (re.compile("ترکیبی"), "MODE", "شیوه برگزاری", True),
]

LEADING = ["از","به","برای","با","در","که","و","هم"]
TRAILING = ["از","به","تا","و","برای","است","هست","بود","شد","می‌باشد","خواهد بود"]
MAX_VALUE_LEN = 60

def _norm_nums(value):
    text = to_english_digits(value)
    for w, d in WORDNUM.items():
        text = re.sub(f"(?<!\\w){w}(?!\\w)", d, text, flags=re.UNICODE)
    return to_persian_digits(text)

def _sentence_cut(value):
    best = None
    for ch in ['؛','!','؟','\n']:
        i = value.find(ch)
        if i >= 0 and (best is None or i < best): best = i
    dot = value.find('.')
    while dot >= 0:
        nxt = dot + 1
        if nxt >= len(value) or not value[nxt].isalnum():
            if best is None or dot < best: best = dot
            break
        dot = value.find('.', nxt)
    return best

def _clean_value(raw, cut_at_conjunction):
    value = raw
    cut = _sentence_cut(value)
    if cut is not None: value = value[:cut]
    value = value.strip().strip(':،; ')
    if cut_at_conjunction:
        value = re.split(r"\s+(?:و|که)\s+", value)[0]
    changed = True
    while changed:
        changed = False
        for c in LEADING:
            if value.startswith(c + " "):
                value = value[len(c)+1:].strip(); changed = True
    changed = True
    while changed:
        changed = False
        for c in TRAILING:
            if value.endswith(" " + c):
                value = value[:-(len(c)+1)].strip(); changed = True
    value = value.strip(':،.؛ ')
    return value[:MAX_VALUE_LEN].strip()

def extract_facts(idea):
    text = re.sub(r"[ \t]+", " ", idea.replace("ي","ی").replace("ك","ک")).strip()
    if not text: return []
    # find labels
    matches = []
    for pat, cat, lab, selfv in LABELS:
        for m in pat.finditer(text):
            s, e = m.start(), m.end()
            while e > s and text[e-1].isspace(): e -= 1
            while s < e and text[s].isspace(): s += 1
            before_ok = s == 0 or not text[s-1].isalnum()
            after_ok = e >= len(text) or not text[e].isalnum()
            if before_ok and after_ok:
                matches.append((s, e, cat, lab, selfv, text[s:e]))
    matches.sort(key=lambda t: (t[0], t[1]))
    accepted = []
    for m in matches:
        if not any(a[0] <= m[1]-1 and m[0] <= a[1]-1 for a in accepted):
            accepted.append(m)
    facts = []
    mode_location = None
    for i, (s, e, cat, lab, selfv, mtext) in enumerate(accepted):
        vend = accepted[i+1][0] if i+1 < len(accepted) else len(text)
        raw = text[e:vend]
        if selfv:
            pm = re.match(r"\s*در\s+([^،.؛:!؟\n]{2,30})", raw)
            if pm: mode_location = _clean_value(pm.group(1), False)
            value = mtext
        else:
            value = _clean_value(raw, cat == "INSTRUCTOR")
        if value.strip():
            facts.append((lab, _norm_nums(value), cat, s))
    if mode_location:
        facts.append(("مکان", mode_location, "LOCATION", 10**9))
    present = {f[2] for f in facts}
    m = EVENT_RE.search(text)
    if m and "EVENT" not in present:
        facts.append(("نوع رویداد", (m.group(1) + " " + (m.group(2) or "")).strip(), "EVENT", 10**9))
    if "DATE" not in present:
        m = DATE_FALLBACK.search(text)
        if m: facts.append(("تاریخ", _norm_nums(m.group(0)), "DATE", 10**9))
    if "DURATION" not in present:
        m = DURATION_FALLBACK.search(text)
        if m: facts.append(("مدت", f"{_norm_nums(m.group(1))} {m.group(2)}", "DURATION", 10**9))
    if "PLATFORM" not in present:
        for p in PLATFORMS:
            if p in text:
                facts.append(("پلتفرم/بستر انتشار", p, "PLATFORM", 10**9)); break
    if "PRICE" not in present:
        if "رایگان" in text:
            facts.append(("قیمت", "رایگان", "PRICE", 10**9))
        else:
            m = PRICE_RE.search(text)
            if m: facts.append(("قیمت", _norm_nums(m.group(1)), "PRICE", 10**9))
    seen, out = set(), []
    for f in sorted(facts, key=lambda f: f[3]):
        if f[2] not in seen:
            seen.add(f[2]); out.append(f)
    return out

# ---------------- OutputRouter ----------------
DESIGN_WORDS = ["پوستر","بنر","لوگو","آرم","کاور","طراحی","طرح بصری","تصویرسازی","اینفوگرافیک"]
IMAGE_WORDS = ["میدجرنی","midjourney","دالی","dall-e","پرامپت تصویر","پرامپت ساخت تصویر","تصویر با هوش مصنوعی"]
CALENDAR_WORDS = ["تقویم","برنامه انتشار","برنامه‌ی انتشار","برنامه‌ریزی محتوا","برنامه ریزی محتوا"]
VIDEO_WORDS = ["ویدیو","ریلز","شورتز","تیزر","ویدیویی","سناریو","اسکریپت ویدیو"]
PLATFORM_TOKENS = {"اینستاگرام","تلگرام","واتساپ","لینکدین","یوتیوب","توییتر","ایکس","فیسبوک","آپارات","وبسایت","سایت","وبلاگ","گوگل","ایمیل","پیج","کانال"}
ANSWER_TO_TYPE = {"متن و ساختار":"TEXT","پرامپت برای ساخت تصویر":"IMAGE_PROMPT","بریف برای طراح":"DESIGN_BRIEF","همه‌ی موارد":"ALL"}

def matches_any(idea, words):
    norm = idea.replace("ي","ی").replace("ك","ک")
    tokens = tokenize(norm)
    for w in words:
        if " " in w:
            if w in norm: return True
        elif any(t == w or t.startswith(w) for t in tokens): return True
    return False

def output_type(spec):
    for a in spec["answers"]:
        if a["questionId"] == "deliverable-type" and a["value"]:
            for k, v in ANSWER_TO_TYPE.items():
                if k in a["value"]: return v
    return default_output_type(spec["idea"])

def default_output_type(idea):
    if matches_any(idea, IMAGE_WORDS): return "IMAGE_PROMPT"
    if matches_any(idea, DESIGN_WORDS): return "DESIGN_BRIEF"
    return "TEXT"

def task_category_idea(idea):
    if matches_any(idea, CALENDAR_WORDS): return "CALENDAR"
    if matches_any(idea, VIDEO_WORDS): return "VIDEO"
    if matches_any(idea, DESIGN_WORDS): return "DESIGN"
    return "TEXT"

def task_category(spec):
    ic = task_category_idea(spec["idea"])
    at = None
    for a in spec["answers"]:
        if a["questionId"] == "deliverable-type" and a["value"]:
            for k, v in ANSWER_TO_TYPE.items():
                if k in a["value"]: at = v
    if at == "TEXT" and ic == "DESIGN": return "TEXT"
    if at == "DESIGN_BRIEF" and ic == "TEXT": return "DESIGN"
    return ic

# ---------------- ClarificationEngine ----------------
QUESTION_FACT_MAP = {
    "product-service": ["SUBJECT","EVENT"], "content-topic": ["SUBJECT","EVENT"],
    "platform": ["PLATFORM"], "audience": ["AUDIENCE"], "price": ["PRICE"],
    "instructor": ["INSTRUCTOR"], "date": ["DATE"], "duration": ["DURATION"],
    "event-mode": ["MODE"], "registration": ["REGISTRATION"], "event-time": ["TIME"],
    "location": ["LOCATION"],
}
MAX_QUESTIONS = 5

def normalize_q(text):
    text = text.replace("ي","ی").replace("ك","ک").replace("\u200c"," ").lower()
    return "".join(c for c in text if c.isalnum() or c.isspace())

def questions_for(kb, idea):
    if kb is None: return []
    if not idea.strip(): return []
    norm = normalize_q(idea)
    facts = extract_facts(idea)
    fact_cats = {f[2] for f in facts}
    task = task_category_idea(idea)
    is_event = "EVENT" in fact_cats
    out = []
    for q in kb["clarifyingQuestions"]:
        scopes = q.get("appliesTo", [])
        if scopes:
            if not any(s == task.lower() or (s == "event" and is_event) for s in scopes):
                continue
        if any(normalize_q(k) in norm for k in q.get("gapKeywordsFa", [])): continue
        if any(c in fact_cats for c in QUESTION_FACT_MAP.get(q["id"], [])): continue
        out.append(q)
    out.sort(key=lambda q: (q["priority"], q["id"]))
    return out[:MAX_QUESTIONS]

# ---------------- PromptAssembler ----------------
DESIGN_PERSONA_TOKENS = {"پوستر","طراح","طراحی","گرافیک","هنری","بصری","بنر","لوگو","کاور","هویت"}
VIDEO_EXAMPLE_TOKENS = ["ویدیو","ریلز","شورتز","تیزر","صحنه","شات","دوربین"]
TONE_QIDS = {"brand-tone","tone"}
LENGTH_QIDS = {"length-limit","video-length","length"}
STOPWORDS = {"برای","های","این","آن","با","از","که","را","تا","یا","هم","روی","می","کنم","کنیم","شود","شده","دارد","است","هست"}
IMAGE_EXAMPLE_TARGET = "midjourney"

def scoring_tokens(text):
    return tokenize(text) - PLATFORM_TOKENS - STOPWORDS

def normalize_for_match(t):
    return re.sub(r"\s+", " ", t.replace("ي","ی").replace("ك","ک").replace("\u200c","")).strip()

def select_persona(kb, spec, otype, tcat):
    personas = kb["personas"]
    if not personas: return None
    design_task = tcat == "DESIGN" or otype in ("DESIGN_BRIEF","IMAGE_PROMPT")
    text = scoring_tokens(spec["idea"] + " " + " ".join(a["value"] or "" for a in spec["answers"]))
    if not text: return None
    best, bs = None, 0
    for p in personas:
        pt = tokenize(p["titleFa"] + " " + p["expertiseFa"])
        s = len(pt & text) + (5 if design_task and (pt & DESIGN_PERSONA_TOKENS) else 0)
        if s > bs: best, bs = p, s
    if bs > 0: return best
    if design_task:
        for p in personas:
            if tokenize(p["titleFa"] + " " + p["expertiseFa"]) & DESIGN_PERSONA_TOKENS: return p
    return None

def select_structure(kb, spec):
    structures = kb["outputStructures"]
    if not structures: return None
    idea_text = spec["idea"] + " " + " ".join(a["value"] or "" for a in spec["answers"])
    norm = normalize_for_match(idea_text)
    pool = [s for s in structures if not s.get("keywordsFa") or any(normalize_for_match(k) in norm for k in s["keywordsFa"])]
    if not pool: return None
    text = scoring_tokens(idea_text)
    if not text: return None
    best, bs = None, 0
    for s in pool:
        tt = tokenize(s["titleFa"]); bt = tokenize(s["descriptionFa"] + " " + s["templateFa"])
        sc = 3*len(tt & text) + len(bt & text)
        if sc > bs: best, bs = s, sc
    return best if bs > 0 else None

def matches_task_medium(example, tcat):
    tokens = tokenize(example["titleFa"]) | tokenize(example["promptFa"])
    is_design = bool(tokens & DESIGN_PERSONA_TOKENS)
    is_video = any(m in tok for m in VIDEO_EXAMPLE_TOKENS for tok in tokens)
    return not ((is_design and tcat != "DESIGN") or (is_video and tcat != "VIDEO"))

def select_example(kb, spec, otype, tcat, target="chatgpt"):
    examples = kb["examples"]
    if not examples: return None
    if otype == "IMAGE_PROMPT":
        eligible = [e for e in examples if e["targetAi"] == IMAGE_EXAMPLE_TARGET]
    else:
        eligible = [e for e in examples if e["targetAi"] != IMAGE_EXAMPLE_TARGET and matches_task_medium(e, tcat)]
    if not eligible: return None
    it = scoring_tokens(spec["idea"])
    best, bs = None, 0
    for e in eligible:
        tt = tokenize(e["titleFa"]); bt = tokenize(e["promptFa"])
        s = 3*len(tt & it) + len(bt & it)
        if tcat == "DESIGN" and ((tt | bt) & DESIGN_PERSONA_TOKENS): s += 5
        if e["targetAi"] == "any" or e["targetAi"] == target: s += 1
        if s > bs: best, bs = e, s
    return best if bs >= 2 else None

def relevant_terms(idea, kb, n):
    text = tokenize(idea)
    matched = [t for t in kb["terminology"] if tokenize(t["termFa"] + " " + t["termEn"]) & text]
    seen, out = set(), []
    for t in matched + kb["terminology"]:
        if t["termEn"] not in seen:
            seen.add(t["termEn"]); out.append(t)
    return out[:n]

def ratio_for(facts):
    plat = next((f[1] for f in facts if f[2] == "PLATFORM"), None)
    if plat and "اینستاگرام" in plat: return "۴:۵ یا ۱:۱ — پست اینستاگرام"
    if plat and "استوری" in plat: return "۹:۱۶ — استوری"
    return "۴:۵ برای پست؛ ۹:۱۶ برای استوری"

def fact_value(facts, cat):
    return next((f[1] for f in facts if f[2] == cat), None)

def deliverable_fa(otype, tcat, facts):
    target = fact_value(facts, "EVENT") or fact_value(facts, "SUBJECT") or ""
    suffix = f" برای {target}" if target.strip() else ""
    if otype == "DESIGN_BRIEF" or (otype == "ALL" and tcat == "DESIGN"): return f"بریف کامل طراحی{suffix}"
    if otype == "IMAGE_PROMPT": return f"پرامپت آمادهٔ ساخت تصویر{suffix}"
    if otype == "ALL": return f"بستهٔ کامل (متن + بریف طراحی + پرامپت تصویر){suffix}"
    if tcat == "CALENDAR": return f"تقویم محتوایی کامل{suffix}"
    return f"متن ساخت‌یافتهٔ کامل{suffix}"

VARIABLE_TOKENS = {"audience":"AUDIENCE","product-service":"TOPIC_OR_PRODUCT","content-topic":"TOPIC_OR_PRODUCT",
    "campaign-goal":"CAMPAIGN_GOAL","goal":"GOAL_OR_PURPOSE","purpose":"GOAL_OR_PURPOSE","platform":"PLATFORM",
    "brand-tone":"TONE","tone":"TONE","competitive-edge":"COMPETITIVE_EDGE","length-limit":"LENGTH",
    "video-length":"LENGTH","length":"LENGTH","cta":"CALL_TO_ACTION","language-stack":"TECH_STACK",
    "skill-level":"SKILL_LEVEL","output-type":"OUTPUT_TYPE","context":"CONTEXT_DETAILS","environment":"ENVIRONMENT",
    "testing-need":"TESTING","format":"RESPONSE_FORMAT","event-mode":"EVENT_MODE","registration":"REGISTRATION_INFO",
    "event-time":"EVENT_TIME","style-colors":"STYLE_AND_COLORS"}

def variable_token(qid):
    return VARIABLE_TOKENS.get(qid, qid.replace("-", "_").upper())

def variables_for(spec, facts, idea):
    variables = []
    for a in spec["answers"]:
        if not a["value"]:
            if a["questionId"] in ("deliverable-type", "output-type"): continue
            covered = any(c in {f[2] for f in facts} for c in QUESTION_FACT_MAP.get(a["questionId"], []))
            if not covered:
                variables.append((variable_token(a["questionId"]), a["questionFa"]))
    for m in re.finditer(r"\[([A-Za-z0-9_ ]{2,30})\]", idea):
        tok = m.group(1).strip().upper().replace(" ", "_")
        if tok not in [v[0] for v in variables]:
            variables.append((tok, "مقدار را کاربر تعیین می‌کند"))
    return variables

def with_period(text):
    t = text.rstrip()
    return text if t.endswith(('.', '؟', '!')) else t + "."

def fa_num(n): return to_persian_digits(str(n))

def assemble(spec, kb):
    idea = spec["idea"].strip()
    quick = spec["detailLevel"] == "QUICK"
    expert = spec["detailLevel"] == "EXPERT"
    facts = extract_facts(idea)
    otype = output_type(spec)
    tcat = task_category(spec)
    answered = [a for a in spec["answers"] if a["value"]]
    variables = variables_for(spec, facts, idea)
    media = spec["targetAi"] in ("MIDJOURNEY", "VIDEO_GENERATORS")
    S = []

    persona = select_persona(kb, spec, otype, tcat) if kb else None
    if persona: body = f"تو {persona['titleFa']} هستی؛ {persona['expertiseFa']}."
    elif tcat == "DESIGN" or otype == "DESIGN_BRIEF":
        body = ("تو کارگردان هنری و طراح گرافیک باتجربه هستی؛ متخصص پوستر، بنر و هویت بصری "
                "با تسلط بر تایپوگرافی فارسی و محدودیت‌های چاپ و نمایش دیجیتال.")
    else:
        body = f"تو یک متخصص باتجربه در حوزه «{kb['nameFa'] if kb else 'موضوع درخواست'}» با نگاه عملی و نتیجه‌محور هستی."
    if expert and kb:
        terms = relevant_terms(spec["idea"], kb, 2)
        if terms:
            body += " این مفاهیم را درست به‌کار ببر: " + "، ".join(f"{t['termFa']} ({t['termEn']})" for t in terms) + "."
    S.append(("ROLE", "نقش", body))

    if spec["mode"] == "IMPROVE":
        body = "بازنویسی حرفه‌ای و ساخت‌یافته پرامپت پیوست‌شده در بخش زمینه؛ نیت اصلی کاربر دقیقاً حفظ شود."
    else:
        body = f"تحویل نهایی: {deliverable_fa(otype, tcat, facts)}. هدف، پاسخ کامل و آماده استفاده به درخواست کاربر در بخش زمینه است."
    body += (" هرچه لازم است در زمینه آمده است؛ چیزی که کاربر نداده از خودت نساز." if not variables
             else " اطلاعاتی که کاربر نداده را از خودت نساز؛ جای آن‌ها همان [متغیر] مشخص بماند و در پایان پاسخ، فهرست این موارد را برای تکمیل بیاور.")
    S.append(("OBJECTIVE", "هدف", body))

    lines = []
    if spec["mode"] == "IMPROVE":
        lines += ["پرامپت اصلی کاربر (بازنویسی شود):", f"«{idea.strip()}»"]
    else:
        lines += [f"درخواست کاربر: «{idea}»"]
    if facts:
        lines += ["", "اطلاعات قطعی (از متن کاربر؛ این‌ها را دقیقاً همین‌طور استفاده کن و تغییرشان نده):"]
        lines += [f"- {f[0]}: {f[1]}" for f in facts]
    if answered:
        lines += ["", "پاسخ‌های کاربر به سؤال‌ها:"]
        lines += [f"- {a['questionFa']} {a['value']}" for a in answered]
    if expert and kb: lines.append(f"حوزه کاری: {kb['nameFa']}.")
    S.append(("CONTEXT", "زمینه", "\n".join(lines)))

    if not variables:
        body = "همه اطلاعات لازم در بخش زمینه آمده است؛ متغیر جدید تعریف نکن و چیزی را که کاربر گفته دوباره نپرس."
    else:
        body = "این موارد را کاربر تعیین نکرده؛ در خروجی همان [نام] بماند و در پایان فهرستشان کن:\n" + \
               "\n".join(f"- [{t}]: {d}" for t, d in dict(variables).items())
    S.append(("INPUTS", "ورودی‌ها و متغیرها", body))

    if not media:
        if otype == "DESIGN_BRIEF" or (otype == "ALL" and tcat == "DESIGN"):
            steps = ["۱) پیام اصلی و پیام‌های ثانویه را از اطلاعات قطعی استخراج کن.",
                     "۲) سلسله‌مراتب متن را بچین: عنوان اصلی، زیرعنوان، جزئیات کلیدی.",
                     "۳) عناصر اجباری (نام، تاریخ، مدرس یا برند، دعوت به اقدام) را مشخص کن.",
                     "۴) سبک بصری، رنگ و حال‌وهوا را متناسب با پیام پیشنهاد بده.",
                     "۵) بریف نهایی را کامل و بدون ابهام بنویس."]
        elif otype == "IMAGE_PROMPT":
            steps = ["۱) سوژه اصلی و حال‌وهوای تصویر را از اطلاعات قطعی تعیین کن.",
                     "۲) ترکیب‌بندی را با جای خالی برای متن‌ها طراحی کن: بالا برای تیتر، پایین برای اطلاعات.",
                     "۳) سبک هنری، نور و پالت رنگی را انتخاب کن.",
                     "۴) پرامپت تصویر را به انگلیسی بنویس و فهرست «نباشد»ها را به آن اضافه کن.",
                     "۵) لایهٔ متن فارسی را جدا و کامل بنویس."]
        elif tcat == "CALENDAR":
            steps = ["۱) بازه زمانی و تعداد محتوا را از درخواست کاربر تعیین کن.",
                     "۲) ستون‌های تقویم را بر اساس هدف انتخاب کن.",
                     "۳) برای هر روز، موضوع و قالب مشخص پیشنهاد بده.",
                     "۴) تقویم را کامل و قابل اجرا تحویل بده."]
        elif spec["domainId"] == "marketing":
            steps = ["۱) بینش اصلی مخاطب را از بخش زمینه استخراج کن.",
                     "۲) یک پیام اصلی انتخاب کن و همه خروجی را حول آن بساز.",
                     "۳) خروجی را دقیقاً در قالب خواسته‌شده تولید کن.",
                     "۴) هر ادعا را به اطلاعات قطعی یا مزیت اعلام‌شده گره بزن."]
        elif spec["domainId"] == "social_media":
            steps = ["۱) پیام اصلی را در یک جمله تعریف کن.",
                     "۲) قلاب (شروع محتوا) را با زاویه‌ای غیرکلیشه‌ای بنویس.",
                     "۳) بدنه را با ریتم تند و بدون مکث بساز.",
                     "۴) پایان‌بندی را با یک اقدام مشخص ببند."]
        elif spec["domainId"] == "programming":
            steps = ["۱) ورودی، خروجی و محدودیت‌ها را فهرست کن.",
                     "۲) ساده‌ترین راه‌حل کامل را طراحی کن و تصمیم مهم را در دو جمله توضیح بده.",
                     "۳) کد کامل و قابل اجرا بنویس؛ بدون شبه‌کد.",
                     "۴) تست برای حالت عادی، مرزی و خطا بنویس."]
        else:
            steps = ["۱) خواسته را در یک جمله دقیق بازنویسی کن.",
                     "۲) ساختار پاسخ را طبق قالب خروجی بچین.",
                     "۳) محتوای اصلی را کامل و بدون حاشیه تولید کن.",
                     "۴) پاسخ را با معیارهای کیفیت بسنج و اصلاح کن."]
        if expert: steps.append(f"{fa_num(len(steps)+1)}) پیش از پاسخ نهایی، چک‌لیست بخش کیفیت را بی‌صدا مرور کن.")
        S.append(("PROCESS", "روند کار", "\n".join(steps[:3] if quick else steps)))

    tone = next((a["value"] for a in answered if a["questionId"] in TONE_QIDS), None)
    length = next((a["value"] for a in answered if a["questionId"] in LENGTH_QIDS), None)
    lines = []
    if otype == "DESIGN_BRIEF" or (otype == "ALL" and tcat == "DESIGN"):
        lines += ["ساختار: بریف طراحی برای طراح گرافیک با این بخش‌ها — ۱) هدف و پیام اصلی ۲) مشخصات فنی (سایز و نسبت تصویر) ۳) سلسله‌مراتب متن (عنوان، زیرعنوان، جزئیات) ۴) عناصر اجباری ۵) سبک و حال‌وهوای بصری ۶) ملاحظات فنی.",
                  f"نسبت تصویر: {ratio_for(facts)}."]
    elif otype == "IMAGE_PROMPT":
        lines += ["ساختار: دو بخش مجزا با تیتر واضح —",
                  f"۱) پرامپت تصویر (به انگلیسی): یک پاراگراف {fa_num(60)} تا {fa_num(150)} کلمه؛ ترتیب اجزا: سوژه و حال‌وهوا، ترکیب‌بندی با جای خالی برای متن (نوار بالایی برای تیتر، نوار پایینی برای اطلاعات)، سبک هنری، نور، پالت رنگی؛ در پایان پرامپت، نسبت تصویر و فهرست «نباشد»ها را با پارامترهای استاندارد همان ابزار تصویرساز بنویس.",
                  "۲) لایهٔ متن (فارسی، کاملاً جدا از پرامپت تصویر): متن دقیقی که کاربر بعداً با فتوشاپ یا کنوا روی تصویر می‌گذارد — تیتر، زیرتیتر، نام مدرس یا برند، تاریخ و ساعت، دعوت به اقدام — با محل و اندازهٔ نسبی هر کدام.",
                  f"نسبت تصویر: {ratio_for(facts)}."]
        if spec["targetAi"] == "MIDJOURNEY":
            lines.append("پارامترها به سبک میدجرنی: نسبت تصویر و فهرست «نباشد»ها در پایان پرامپت، طبق قالب استاندارد خود میدجرنی.")
    elif otype == "ALL":
        lines += ["ساختار: سه بخش مجزا با تیتر واضح — ۱) متن و ساختار محتوا ۲) بریف طراحی گرافیک ۳) پرامپت تولید تصویر."]
    elif tcat == "CALENDAR":
        lines += ["ساختار: جدول تقویم محتوایی با ستون‌های: روز | موضوع | قالب | پیام کوتاه | دعوت به اقدام."]
    else:
        structure = select_structure(kb, spec) if kb else None
        if structure: lines.append(f"ساختار: {structure['titleFa']} — {structure['templateFa']}")
        else: lines.append("ساختار: پاسخ بخش‌بندی‌شده با تیتر کوتاه؛ ابتدا نتیجه اصلی، سپس جزئیات.")
    lang = {"PERSIAN":"فارسی روان","ENGLISH":"انگلیسی","SAME_AS_INPUT":"همان زبان درخواست کاربر"}[spec["outputLanguage"]]
    if otype == "IMAGE_PROMPT":
        lines.append(f"زبان: پرامپت تصویر به انگلیسی (بهترین عملکرد ابزارهای تصویرساز)؛ لایهٔ متن و توضیحات به {lang}.")
    else:
        lines.append(f"زبان پاسخ: {lang}.")
    if tone or (not quick and otype != "IMAGE_PROMPT"):
        lines.append(f"لحن: {tone or 'نیمه‌رسمی روان'}.")
    if otype == "IMAGE_PROMPT":
        lines.append(f"طول: پاراگراف پرامپت تصویر {fa_num(60)} تا {fa_num(150)} کلمه؛ لایهٔ متن کوتاه و دقیق.")
    else:
        lines.append(f"طول: {length or {'QUICK':'کوتاه؛ حداکثر ۱۵۰ کلمه.','STANDARD':'متوسط؛ ۱۵۰ تا ۴۰۰ کلمه.','EXPERT':'کامل و بدون حاشیه؛ طول تابع کامل بودن است.'}[spec['detailLevel']]}")
    if quick: lines.append("پیش‌درآمد و جمع‌بندی تکراری ننویس.")
    S.append(("OUTPUT_FORMAT", "قالب خروجی", "\n".join(lines)))

    count = 3 if quick else (None if expert else 5)
    chosen = kb["guardrails"][:count] if (kb and count) else (kb["guardrails"] if kb else [])
    lines = [f"- {g['doFa']}؛ {g['dontFa']}." for g in chosen]
    if otype == "IMAGE_PROMPT":
        lines += ["- تصویر بدون هیچ متن، حرف یا لوگویی ساخته شود؛ حروف فارسی روی تصویر تولید نکن (متن‌ها در لایهٔ متن جدا داده می‌شوند).",
                  "- در ترکیب‌بندی، جای خالی مناسب متن در نظر بگیر: بخش بالایی برای تیتر و بخش پایینی برای اطلاعات.",
                  "- فهرست «نباشد»ها حتماً در پرامپت بیاید: بدون متن، بدون حروف به‌هم‌ریخته، بدون واترمارک، بدون لوگو."]
    elif otype == "DESIGN_BRIEF" or tcat == "DESIGN":
        lines += [f"- نسبت تصویر با پلتفرم هدف هماهنگ باشد: {ratio_for(facts)}.",
                  "- متن روی تصویر کوتاه و خوانا با حداکثر سه سطح سلسله‌مراتب (عنوان، زیرعنوان، جزئیات).",
                  "- فونت فارسی استاندارد و خوانا؛ کنتراست متن و پس‌زمینه کافی باشد.",
                  "- جای مشخص و واضح برای اطلاعات کلیدی (تاریخ، نام مدرس یا برند، ثبت‌نام یا تماس) در نظر بگیر.",
                  "- رنگ‌ها با هم هویت بصری واحد بسازند؛ از شلوغی بصری پرهیز کن."]
    elif tcat == "CALENDAR":
        lines += ["- هر روز تقویم موضوع تکراری و بدون هدف نداشته باشد.",
                  "- قالب هر محتوا با محدودیت واقعی پلتفرم هماهنگ باشد."]
    elif tcat == "VIDEO":
        lines += ["- ریتم محتوا تند باشد و در ثانیه‌های اول قلاب اصلی بیاید.",
                  "- برای هر پلان، تصویر و حرکت دوربین مشخص تعریف شود."]
    if not any("از خودت نساز" in l or "از خودت درست نکن" in l for l in lines):
        lines.append("- هیچ عدد، آمار، نام یا ادعایی که در ورودی نیامده از خودت نساز.")
    if not any("مقدمه‌چینی" in l for l in lines):
        lines.append("- مستقیم وارد محتوا شو؛ مقدمه‌چینی و جمله انگیزشی ننویس.")
    S.append(("CONSTRAINTS", "محدودیت‌ها و نگه‌داشت‌ها", "\n".join(lines)))

    if not quick and not media:
        example = select_example(kb, spec, otype, tcat) if kb else None
        body = (f"سبک و سطح جزئیات مطلوب شبیه این نمونهٔ کامل است (فقط سبک، نه محتوا):\n«{example['promptFa'].strip()}»"
                if example else "پیش از تولید، یک نمونهٔ کوچک از ساختار خروجی در ذهن بساز و خروجی نهایی را هم‌سبک آن بنویس.")
        S.append(("EXAMPLES", "مثال‌ها", body))
    if not quick and not media:
        criteria = ["۱) انطباق کامل با قالب خروجی.",
                    "۲) اتصال هر ادعا و عدد به اطلاعات قطعی و ورودی‌ها.",
                    "۳) بدون حاشیه، تکرار و کلی‌گویی.",
                    "۴) یکدستی زبان، لحن و ارقام فارسی در کل پاسخ."]
        if otype == "IMAGE_PROMPT":
            criteria += ["۵) پرامپت تصویر کاملاً بدون متن و حروف باشد و جای متن‌ها خالی بماند.",
                         "۶) لایهٔ متن فارسی، دقیق و کامل، جدا از پرامپت تصویر بیاید."]
        else:
            if kb and kb["failureModes"]:
                criteria.append(f"{fa_num(len(criteria)+1)}) {with_period(kb['failureModes'][0]['fixFa'])}")
            if tcat == "DESIGN" or otype == "DESIGN_BRIEF":
                criteria.append(f"{fa_num(len(criteria)+1)}) خوانایی متن در اندازهٔ کوچک و اولویت‌بندی صحیح اطلاعات روی طرح.")
        S.append(("QUALITY", "معیارهای کیفیت", "پیش از ارسال، پاسخ را بی‌صدا با این معیارها بسنج و در صورت نقض، اصلاحش کن:\n" + "\n".join(criteria)))
    if not media:
        mx = 2 if quick else 3
        S.append(("CLARIFICATION", "قاعده شفاف‌سازی", f"اگر اطلاعات ضروری برای شروع ناقص است، پیش از پاسخ حداکثر {fa_num(mx)} سؤال کوتاه و مشخص بپرس، پاسخ کاربر را بگیر و بعد ادامه بده؛ به‌جای سؤال، حدس نزن."))
    return S

def render_markdown(sections):
    return "\n\n".join(f"## {t}\n{b}" for _, t, b in sections)

WEIGHTS = {"ROLE":10,"OBJECTIVE":12,"CONTEXT":12,"INPUTS":10,"PROCESS":10,"OUTPUT_FORMAT":14,
           "CONSTRAINTS":12,"EXAMPLES":8,"QUALITY":7,"CLARIFICATION":5}
MEDIA_FOLDED = {"PROCESS","EXAMPLES","QUALITY","CLARIFICATION"}

def score_sections(sections, spec):
    by_kind = {}
    for k, t, b in sections: by_kind.setdefault(k, (t, b))
    media = spec["targetAi"] in ("MIDJOURNEY", "VIDEO_GENERATORS")
    total = 0
    for kind, w in WEIGHTS.items():
        if kind in by_kind:
            if len(by_kind[kind][1].strip()) < 40: total += w // 2
            else: total += w
        elif media and kind in MEDIA_FOLDED:
            total += w
    return min(total, 100)

def generate(spec, kb):
    sections = assemble(spec, kb)
    if spec["targetAi"] == "MIDJOURNEY":
        subject = " ".join(spec["idea"].split())
        style = "professional product photography, soft studio lighting, clean background" if ("محصول" in subject or "پکیج" in subject) else "cinematic composition, natural lighting, cohesive color palette"
        prompt = f"{subject}, {style}, large clean empty areas for text, sharp focus, high detail --ar 4:5 --v 6 --no text, watermark, logo, letters"
        layer = extract_facts(spec["idea"])
        text = prompt if not layer else prompt + "\n\nلایهٔ متن (فارسی — جدا از پرامپت، بعداً روی تصویر اضافه کن):\n" + "\n".join(f"- {f[0]}: {f[1]}" for f in layer)
    elif spec["targetAi"] == "VIDEO_GENERATORS":
        subj = " ".join(spec["idea"].split())
        text = "\n".join([f"ویدیوی کوتاه ۲۰ ثانیه‌ای درباره: {subj}",
            "صحنه ۱ (۰ تا ۳ ثانیه): نمای نزدیک از سوژه با نور طبیعی؛ حرکت آرام دوربین به جلو.",
            "صحنه ۲ (۳ تا ۸ ثانیه): سوژه در حالت اصلی کار یا حرکت؛ دوربین دنبال‌کننده و نرم.",
            "صحنه ۳ (۸ تا ۱۴ ثانیه): نمای میانی از نتیجه یا واکنش؛ تغییر زاویه برای ریتم.",
            "صحنه ۴ (۱۴ تا ۲۰ ثانیه): قاب پایانی ثابت با پس‌زمینه ساده.",
            "پالت رنگی هماهنگ؛ نور یکنواخت؛ ریتم تند بدون مکث؛ صدای پس‌زمینه ملایم و هماهنگ با حال‌وهوا؛ بدون متن و واترمارک روی تصویر."])
    else:
        text = render_markdown(sections)
    return {"sections": sections, "text": text, "score": score_sections(sections, spec)}

def kbload(n): return json.loads((BASE / n).read_text(encoding="utf-8"))
KBS = {"marketing": kbload("marketing.json"), "social_media": kbload("social_media.json"),
       "programming": kbload("programming.json"), "general": kbload("general.json")}

def spec_of(idea, domain, target="CHATGPT", detail="STANDARD", answers=None, mode="NEW"):
    return {"idea": idea, "domainId": domain, "targetAi": target, "outputLanguage": "PERSIAN",
            "detailLevel": detail, "mode": mode, "answers": answers or []}

def skipped_spec(idea, domain):
    qs = questions_for(KBS[domain], idea)
    return spec_of(idea, domain, answers=[{"questionId": q["id"], "questionFa": q["questionFa"], "value": None} for q in qs])

# ---------------- test harness ----------------
FAILURES = []
def check(label, cond, detail=""):
    if cond: print(f"  ✓ {label}")
    else:
        print(f"  ✗ FAIL {label}" + (f"  [{detail}]" if detail else ""))
        FAILURES.append(label)

def assert_no_latin_digits(text):
    for line in text.split("\n"):
        if "--" in line: continue
        latin = sum(1 for c in line if 'a' <= c <= 'z' or 'A' <= c <= 'Z')
        persian = sum(1 for c in line if '\u0600' <= c <= '\u06FF')
        if latin > persian: continue  # English image-prompt lines
        cleaned = re.sub(r"\[[A-Za-z0-9_ ]+\]", "[]", line)
        m = re.search(r"[0-9]", cleaned)
        if m:
            check("no Latin digits", False, m.group(0) + " → " + line[:80]); return
    check("no Latin digits", True)

def section_body(out, title):
    parts = out["text"].split("## ")
    for p in parts:
        if p.startswith(title):
            return p
    return ""

def assert_clean_structure(out):
    check("no ellipsis", "…" not in out["text"] and "..." not in out["text"])
    assert_no_latin_digits(out["text"])
    for h in ["## نقش","## هدف","## زمینه","## ورودی‌ها و متغیرها","## روند کار","## قالب خروجی","## محدودیت‌ها","## مثال‌ها","## معیارهای کیفیت","## قاعده شفاف‌سازی"]:
        check(f"header {h}", h in out["text"])
    # constraints: no duplicate lines, no nested parens
    cons = section_body(out, "محدودیت‌ها")
    lines = [l for l in cons.split("\n") if l.strip()]
    check("constraints unique", len(lines) == len(set(lines)))
    check("no nested parens", not re.search(r"\([^()]*\(", out["text"]))
    # quality criteria: each numbered line ends with a period
    qual = section_body(out, "معیارهای کیفیت")
    numbered = [l for l in qual.split("\n") if re.match(r"^[۰-۹]\)", l.strip())]
    check("quality criteria end with period", all(l.strip().endswith('.') for l in numbered), str(numbered))

def assert_no_raw_metadata(out, kb):
    for p in kb["personas"]:
        marker = p["whenToUseFa"].strip()
        if len(marker) > 10:
            check(f"no leak: {p['id']}", marker not in out["text"])

def fact_block_lines(out):
    body = section_body(out, "زمینه")
    in_facts = False
    lines = []
    for l in body.split("\n"):
        if l.startswith("اطلاعات قطعی"): in_facts = True; continue
        if in_facts:
            if l.startswith("پاسخ‌های کاربر") or (l == "" and lines): break
            if l.startswith("- ") and ": " in l: lines.append(l)
    return lines

def assert_facts_separated(out):
    fact_lines = fact_block_lines(out)
    labels = ["موضوع", "مدرس", "تاریخ", "مدت", "ساعت", "مکان", "بستر انتشار", "قیمت", "مخاطب", "راه ثبت‌نام", "شیوه برگزاری", "نوع رویداد"]
    ok = True
    for l in fact_lines:
        value = l[2:].split(": ", 1)[1].strip()
        if not value:
            check(f"fact value non-empty ({l[:25]}…)", False, l); ok = False
        for other in labels:
            if value != other and value.startswith(other + " "):
                check(f"fact separation ({l[:25]}…)", False, f"value contains label {other}: {value}"); ok = False
        for trailing in ["از", "به", "تا", "و", "برای"]:
            if value == trailing or value.endswith(" " + trailing):
                check(f"fact value trailing connector ({l[:25]}…)", False, value); ok = False
    if ok:
        check(f"facts separated ({len(fact_lines)} fields)", True)

REG_MULTI = """می‌خواهم برای دوره آموزشی خودم یک پوستر طراحی کنم.
موضوع: آموزش هوش مصنوعی
مدرس: محسن ابوطالبیان
تاریخ: از ۱۱ مهر به مدت ۳ روز
بستر تبلیغ و انتشار: اینستاگرام"""

REG_SINGLE = "میخواهیم برای دوره آموزشی خودم یک پوستر طراحی کنم موضوع آموزش هوش مصنوعی مدرس محسن ابوطالبیان تاریخ از ۱۱ مهر به مدت ۳ روز بستر تبلیغ و انتشار اینستاگرام هست"

print("=== 0. fact extraction variants (الف) ===")
for label, text in [("multi-line", REG_MULTI), ("single-line", REG_SINGLE)]:
    facts = {f[2]: f[1] for f in extract_facts(text)}
    print(f"  [{label}] " + " | ".join(f"{k}={v}" for k, v in facts.items()))
    check(f"{label}: subject exact", facts.get("SUBJECT") == "آموزش هوش مصنوعی", facts.get("SUBJECT"))
    check(f"{label}: instructor exact", facts.get("INSTRUCTOR") == "محسن ابوطالبیان", facts.get("INSTRUCTOR"))
    check(f"{label}: date exact", facts.get("DATE") == "۱۱ مهر", facts.get("DATE"))
    check(f"{label}: duration exact", facts.get("DURATION") == "۳ روز", facts.get("DURATION"))
    check(f"{label}: platform exact", facts.get("PLATFORM") == "اینستاگرام", facts.get("PLATFORM"))
# colon-free, multi-field, with extras
extra = "پوستر سمینار موضوع مدیریت زمان مدرس رضا محمدی تاریخ ۲۰ آبان ساعت ۱۰ صبح مکان تهران حضوری ثبت‌نام در site.com قیمت رایگان"
facts = {f[2]: f[1] for f in extract_facts(extra)}
print("  [extras] " + " | ".join(f"{k}={v}" for k, v in facts.items()))
check("extras: time", facts.get("TIME") == "۱۰ صبح", facts.get("TIME"))
check("extras: location", facts.get("LOCATION") == "تهران", facts.get("LOCATION"))
check("extras: mode", facts.get("MODE") == "حضوری", facts.get("MODE"))
check("extras: registration", facts.get("REGISTRATION") == "site.com", facts.get("REGISTRATION"))
check("extras: price", facts.get("PRICE") == "رایگان", facts.get("PRICE"))

print("=== 1. regression (multi-line) ===")
kb = KBS["marketing"]
asked = [q["id"] for q in questions_for(kb, REG_MULTI)]
print("  asked:", asked)
check("deliverable asked", "deliverable-type" in asked)
check("event-mode asked", "event-mode" in asked)
check("registration asked", "registration" in asked)
check("event-time asked", "event-time" in asked)
check("style-colors asked", "style-colors" in asked)
check("product-service not asked", "product-service" not in asked)
check("platform not asked", "platform" not in asked)
out = generate(skipped_spec(REG_MULTI, "marketing"), kb)
check("designer role", "کارگردان هنری" in out["text"], out["text"][:150])
check("no گوگل ادز", "گوگل ادز" not in out["text"])
check("بریف کامل طراحی", "بریف کامل طراحی" in out["text"])
check("brief format", "بریف طراحی برای طراح گرافیک" in out["text"])
check("no calendar", "تقویم محتوایی" not in out["text"])
check("facts block", "اطلاعات قطعی" in out["text"])
check("مدرس clean", "مدرس: محسن ابوطالبیان" in out["text"])
check("date clean", "تاریخ: ۱۱ مهر" in out["text"])
check("no glued fields", "ابوطالبیان تاریخ" not in out["text"])
check("no trailing از", "تاریخ از" not in out["text"].split("اطلاعات قطعی")[1][:400])
check("duration clean", "مدت: ۳ روز" in out["text"])
check("platform clean", "بستر انتشار: اینستاگرام" in out["text"])
check("subject clean", "موضوع: آموزش هوش مصنوعی" in out["text"])
check("idea once", out["text"].split("پوستر طراحی کنم").__len__() - 1 == 1)
check("no [TOPIC_OR_PRODUCT]", "[TOPIC_OR_PRODUCT]" not in out["text"])
check("no [PLATFORM]", "[PLATFORM]" not in out["text"])
check("no fabrication order", "هیچ جای خالی رها نکن" not in out["text"])
check("no-fabrication rule", "از خودت نساز" in out["text"])
check("poster example", "سارا رستمی" in out["text"])
check("no A/B", "A/B" not in out["text"])
check("fixFa directive", kb["failureModes"][0]["fixFa"] in out["text"])
check("no meta fix phrasing", "در پرامپت،" not in out["text"])
assert_facts_separated(out)
assert_clean_structure(out)
assert_no_raw_metadata(out, kb)

print("=== 1b. regression (single line, no punctuation) ===")
facts1 = {f[2]: f[1] for f in extract_facts(REG_MULTI)}
facts2 = {f[2]: f[1] for f in extract_facts(REG_SINGLE)}
same = all(facts1.get(k) == facts2.get(k) for k in ["SUBJECT","INSTRUCTOR","DATE","DURATION","PLATFORM"])
check("single-line parity", same, f"{facts1} vs {facts2}")
out = generate(skipped_spec(REG_SINGLE, "marketing"), kb)
check("single-line: clean instructor", "مدرس: محسن ابوطالبیان" in out["text"])
check("single-line: clean subject", "موضوع: آموزش هوش مصنوعی" in out["text"])
check("single-line: no glue", "ابوطالبیان تاریخ" not in "\n".join(fact_block_lines(out)))
assert_clean_structure(out)

print("=== 2. image deliverable (ب) ===")
sp = skipped_spec(REG_MULTI, "marketing")
sp["answers"] = sp["answers"] + [{"questionId":"deliverable-type","questionFa":"خروجی چه باشد؟","value":"پرامپت برای ساخت تصویر"}]
iout = generate(sp, kb)
check("image: deliverable", "پرامپت آمادهٔ ساخت تصویر" in iout["text"])
check("image: english instruction", "به انگلیسی" in iout["text"])
check("image: length 60-150", "۶۰ تا ۱۵۰" in iout["text"])
check("image: text layer", "لایهٔ متن" in iout["text"])
check("image: negative list", "بدون هیچ متن" in iout["text"])
check("image: no garbled letters", "حروف به‌هم‌ریخته" in iout["text"])
check("image: no tone", "لحن:" not in iout["text"])
check("image: no brief example", "ساختار: بریف طراحی برای طراح گرافیک" not in iout["text"])
check("image: image example embedded", "--ar" in iout["text"])
check("image: image criteria", "جای متن‌ها خالی بماند" in iout["text"])
check("image: no irrelevant fixFa", kb["failureModes"][0]["fixFa"] not in iout["text"])
assert_no_latin_digits(iout["text"])
check("image: headers", all(h in iout["text"] for h in ["## نقش","## هدف","## زمینه"]))

print("=== 3. midjourney target adapter ===")
mj = generate(spec_of(REG_MULTI, "marketing", target="MIDJOURNEY"), kb)
check("mj: --ar", "--ar" in mj["text"])
check("mj: strong --no", "--no text, watermark, logo, letters" in mj["text"])
check("mj: text layer from facts", "لایهٔ متن" in mj["text"] and "مدرس: محسن ابوطالبیان" in mj["text"])

print("=== 4. deliverable answers ===")
sp = skipped_spec(REG_MULTI, "marketing"); sp["answers"] = [{"questionId":"deliverable-type","questionFa":"خروجی چه باشد؟","value":"متن و ساختار"}]
tout = generate(sp, kb)
check("text deliverable", "متن ساخت‌یافتهٔ کامل" in tout["text"])
check("no brief format", "ساختار: بریف طراحی برای طراح گرافیک" not in tout["text"])

print("=== 5. marketing domain ===")
idea = "برای پیج لباسم یک تقویم محتوایی یک‌ماهه می‌خواهم"
check("calendar: no deliverable q", "deliverable-type" not in [q["id"] for q in questions_for(kb, idea)])
out = generate(skipped_spec(idea, "marketing"), kb)
check("calendar deliverable", "تقویم محتوایی" in out["text"])
assert_clean_structure(out)
out = generate(skipped_spec("تبلیغ بنویس", "marketing"), kb)
assert_clean_structure(out)
check("score >= 85", out["score"] >= 85, str(out["score"]))
idea = "ایمیل خوش‌آمدگویی برای مشتریان جدید فروشگاه"
check("email: no deliverable q", "deliverable-type" not in [q["id"] for q in questions_for(kb, idea)])
out = generate(skipped_spec(idea, "marketing"), kb)
check("no calendar", "تقویم محتوایی" not in out["text"])
assert_clean_structure(out)

print("=== 6. social domain ===")
kb = KBS["social_media"]
out = generate(skipped_spec("کاور پیج اینستاگرام برای کافه‌ام می‌خوام", "social_media"), kb)
check("designer role", "طراح گرافیک" in out["text"])
check("brief", "بریف کامل طراحی" in out["text"])
assert_clean_structure(out); assert_no_raw_metadata(out, kb)
out = generate(skipped_spec("سناریوی ریلز ۳۰ ثانیه‌ای برای معرفی اپلیکیشن", "social_media"), kb)
check("script writer", "سناریونویس" in out["text"])
assert_clean_structure(out)
out = generate(skipped_spec("کپشن معرفی محصول جدید عطر", "social_media"), kb)
check("caption", "کپشن" in out["text"])
assert_clean_structure(out)

print("=== 7. programming domain ===")
kb = KBS["programming"]
idea = "یک اپ اندروید برای لیست کارها با کاتلین می‌خواهم"
check("stack not asked", "language-stack" not in [q["id"] for q in questions_for(kb, idea)])
out = generate(skipped_spec(idea, "programming"), kb)
check("android persona", "توسعه‌دهنده ارشد اندروید" in out["text"])
check("code process", "کد کامل" in out["text"])
assert_clean_structure(out)
out = generate(skipped_spec("فرم ثبت‌نام من خطا می‌دهد، رفع باگ کن", "programming"), kb)
check("bug structure", "بررسی سیستماتیک" in out["text"])
assert_clean_structure(out)

print("=== 8. general domain ===")
kb = KBS["general"]
idea = "متن معرفی رستوران ایرانی برای وبسایت"
check("no deliverable q", "deliverable-type" not in [q["id"] for q in questions_for(kb, idea)])
out = generate(skipped_spec(idea, "general"), kb)
check("text deliverable", "متن ساخت‌یافتهٔ کامل" in out["text"])
assert_clean_structure(out)
out = generate(skipped_spec("پوستر جشن فارغ‌التحصیلی دانشجویان می‌خواهم", "general"), kb)
check("designer role", "کارگردان هنری" in out["text"])
check("brief", "بریف کامل طراحی" in out["text"])
asked = [q["id"] for q in questions_for(kb, "پوستر جشن فارغ‌التحصیلی دانشجویان می‌خواهم")]
check("poster questions for graduation", "style-colors" in asked and "registration" in asked, str(asked))
assert_clean_structure(out)
idea = "یک ایده کوتاه بده"
check("deliverable asked", "deliverable-type" in [q["id"] for q in questions_for(kb, idea)])
out = generate(skipped_spec(idea, "general"), kb)
assert_clean_structure(out)
# general guardrails fixed
check("no broken sentence", "می‌دانستنی نیست" not in out["text"])
kb_gen = KBS["general"]
out_g = generate(skipped_spec("پاسخ ساخت‌یافته به سؤال درباره تاریخ ایران", "general"), kb_gen)
cons_g = section_body(out_g, "محدودیت‌ها")
check("no-fabrication not duplicated in constraints", cons_g.count("از خودت نساز") <= 1, str(cons_g.count("از خودت نساز")))

print("=== 9. image routing (میدجرنی در متن ایده) ===")
kb = KBS["general"]
idea = "با میدجرنی یک پوستر کافه بسازم"
check("no deliverable q", "deliverable-type" not in [q["id"] for q in questions_for(kb, idea)])
out = generate(skipped_spec(idea, "general"), kb)
check("image deliverable", "پرامپت آمادهٔ ساخت تصویر" in out["text"])
check("image text layer", "لایهٔ متن" in out["text"])
check("image example same type", "--ar" in out["text"])
assert_clean_structure(out)

print("=== 10. sweep: every domain, text + design + detail-rich ===")
DETAIL_RICH = {
    "marketing": "پوستر دوره آموزشی موضوع طراحی سایت مدرس مریم احمدی تاریخ ۵ آذر ساعت ۱۷ مکان اصفهان حضوری ثبت‌نام در register.example قیمت ۸۰۰ هزار تومان",
    "social_media": "پوستر معرفی محصول جدید کافه موضوع نوشیدنی زمستانی مخاطب جوانان تهران تاریخ ۱۰ دی",
    "programming": "پوستر معرفی اپ موبایل موضوع مدیریت هزینه مدرس تیم محصول",
    "general": "پوستر جشنواره موسیقی موضوع موسیقی سنتی تاریخ ۲۲ بهمن ساعت ۱۹ مکان تالار وحدت",
}
for dom in ["marketing", "social_media", "programming", "general"]:
    kb = KBS[dom]
    out = generate(skipped_spec(DETAIL_RICH[dom], dom), kb)
    assert_facts_separated(out)
    assert_no_latin_digits(out["text"])
    check(f"{dom}: brief for poster", "بریف" in out["text"])
    check(f"{dom}: no calendar", "تقویم محتوایی" not in out["text"])
    fblock = "\n".join(fact_block_lines(out))
    check(f"{dom}: no glued facts", "احمدی تاریخ" not in fblock and "مصنوعی مدرس" not in fblock, fblock[:200])
    # text idea stays text
    text_idea = {"marketing":"تیتر تبلیغاتی برای فروشگاه آنلاین","social_media":"کپشن انگیزشی صبحگاهی",
                 "programming":"تابع جاوااسکریپت برای اعتبارسنجی فرم","general":"پاسخ ساخت‌یافته به سؤال درباره تاریخ ایران"}[dom]
    tout = generate(skipped_spec(text_idea, dom), kb)
    assert_clean_structure(tout)
    assert_no_raw_metadata(tout, kb)
    check(f"{dom}: text stays text", "تقویم محتوایی" not in tout["text"])

print("=== 11. existing PromptEngineTest scenarios ===")
kbm = KBS["marketing"]
def pet_spec(idea="می‌خواهم برای فروش دوره آنلاینم تبلیغ اینستاگرام بنویسم", target="CHATGPT", detail="STANDARD", answers=None):
    return spec_of(idea, "marketing", target, detail, answers)
out = generate(pet_spec(), kbm)
check("pet score 100", out["score"] == 100, str(out["score"]))
out = generate(pet_spec(detail="QUICK"), kbm)
check("pet quick <100", out["score"] < 100, str(out["score"]))
check("pet quick no examples header", "## مثال‌ها" not in out["text"])
out = generate(pet_spec(target="MIDJOURNEY", idea="تصویر یک لیوان قهوه داغ روی میز چوبی"), kbm)
check("pet mj --ar", "--ar" in out["text"]); check("pet mj --no", "--no" in out["text"])
check("pet mj قهوه", "قهوه" in out["text"]); check("pet mj no header", "## نقش" not in out["text"])
check("pet mj score>=90", out["score"] >= 90, str(out["score"]))
out = generate(pet_spec(target="VIDEO_GENERATORS"), kbm)
check("pet video صحنه ۱", "صحنه ۱" in out["text"]); check("pet video صحنه ۴", "صحنه ۴" in out["text"])
out = generate(pet_spec(answers=[{"questionId":"audience","questionFa":"مخاطب این محتوا یا پوستر کیست؟","value":None}]), kbm)
check("pet [AUDIENCE]", "[AUDIENCE]" in out["text"])
out = generate(pet_spec(answers=[{"questionId":"audience","questionFa":"مخاطب این محتوا یا پوستر کیست؟","value":"زنان ۲۵ تا ۴۰ سال"}]), kbm)
check("pet answered in context", "زنان ۲۵ تا ۴۰ سال" in out["text"])

print("=== 12. ClarificationEngineTest scenarios ===")
test_kb = {"clarifyingQuestions":[
    {"id":"audience","questionFa":"مخاطب کیست؟","priority":1,"gapKeywordsFa":["مخاطب","مشتری"]},
    {"id":"platform","questionFa":"کدام پلتفرم؟","priority":2,"gapKeywordsFa":["اینستاگرام"]},
    {"id":"tone","questionFa":"لحن؟","priority":3,"gapKeywordsFa":[]},
    {"id":"goal","questionFa":"هدف؟","priority":4,"gapKeywordsFa":[]},
    {"id":"budget","questionFa":"بودجه؟","priority":5,"gapKeywordsFa":[]},
    {"id":"extra","questionFa":"اضافه؟","priority":6,"gapKeywordsFa":[]}]}
qs = questions_for(test_kb, "یک ایده ساده")
check("cet at most 5", len(qs) == 5, str(len(qs)))
check("cet priorities", [q["priority"] for q in qs] == [1,2,3,4,5])
qs = questions_for(test_kb, "می‌خواهم برای مشتری‌هایم در اینستاگرام تبلیغ بدهم")
check("cet no audience", all(q["id"] != "audience" for q in qs))
check("cet no platform", all(q["id"] != "platform" for q in qs))
check("cet not empty", len(qs) > 0)
check("cet blank", questions_for(test_kb, "   ") == [])
check("cet null kb", questions_for(None, "یک ایده") == [])
check("cet max 5", len(questions_for(test_kb, "یک ایده ساده")) <= 5)

print()
if FAILURES:
    print(f"❌ {len(FAILURES)} FAILURES:")
    for f in FAILURES: print("  -", f)
    raise SystemExit(1)
print("✅ ALL SIMULATED ASSERTIONS PASS")
