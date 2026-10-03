# PromptSaz — Knowledge Base Guide

The domain knowledge bases are plain JSON files in `app/src/main/assets/knowledge/`.
**Adding or improving a domain never requires code changes** — the engine (Phase 3)
reads whatever the registry marks as `ready`.

## Files

| File | Purpose |
|------|---------|
| `_registry.json` | The list of all domains (ready + coming soon) and their status. |
| `kb.schema.json` | JSON Schema describing every knowledge-base file. |
| `general.json` | Light knowledge base for ideas that fit no specific domain (always ready). |
| `marketing.json` · `social_media.json` · `programming.json` | Full knowledge bases shipped in v1.0. |

## Adding a new domain (e.g. «تحلیل داده»)

1. Create `app/src/main/assets/knowledge/data_analysis.json` following the shape
   below and the content minimums table.
2. Add an entry to `_registry.json`:

   ```json
   {
     "id": "data_analysis",
     "nameFa": "تحلیل داده",
     "descriptionFa": "پاک‌سازی داده، تحلیل آماری و روایت یافته‌ها",
     "status": "ready",
     "kbFile": "data_analysis.json"
   }
   ```

3. Run the validator: `python3 tools/validate_kb.py` — it must print `OK`.
4. Rebuild the app. The new domain automatically appears in the domain picker,
   the KB browser and the archive filters.

Roadmap domains can be listed with `"status": "coming_soon"` (no `kbFile`) —
they show as «به‌زودی» in the UI.

## Structure of a knowledge-base file

```jsonc
{
  "id": "marketing",            // must match the registry id
  "nameFa": "بازاریابی و تبلیغات",
  "status": "ready",
  "personas": [                  // expert role models; engine picks by task fit
    { "id": "growth-marketer", "titleFa": "…", "expertiseFa": "…", "whenToUseFa": "…" }
  ],
  "terminology": [               // key terms; injected into Context/Examples
    { "termFa": "قلاب", "termEn": "Hook", "definitionFa": "…" }
  ],
  "outputStructures": [          // standard answer shapes for Output Format
    { "id": "aida", "titleFa": "AIDA", "descriptionFa": "…", "templateFa": "…" }
  ],
  "guardrails": [                // do/don't pairs; phrased positively first
    { "id": "no-hype", "doFa": "…", "dontFa": "…", "whyFa": "…" }
  ],
  "failureModes": [              // symptom → fix; powers the "improve" suggestions
    { "id": "generic-output", "symptomFa": "…", "fixFa": "…" }
  ],
  "examples": [                  // complete, high-quality example prompts (few-shot)
    { "id": "instagram-ad", "titleFa": "…", "targetAi": "chatgpt", "promptFa": "…" }
  ],
  "clarifyingQuestions": [       // tap-to-answer questions before generation
    { "id": "audience", "questionFa": "مخاطب اصلی کیست؟", "priority": 1,
      "chips": [ { "id": "b2c", "labelFa": "مصرف‌کننده نهایی" } ],
      "allowFreeText": true,
      "gapKeywordsFa": ["مخاطب", "مشتری"] }   // if the idea contains these, the
  ]                                            // engine treats the gap as answered
}
```

`targetAi` allowed values: `chatgpt`, `claude`, `gemini`, `deepseek`,
`midjourney`, `video`, `any`.

## Content minimums

| Section | Full domain | `general.json` |
|---------|------------:|---------------:|
| personas | ≥ 4 | ≥ 3 |
| terminology | ≥ 10 | ≥ 5 |
| outputStructures | ≥ 4 | ≥ 3 |
| guardrails | ≥ 8 | ≥ 5 |
| failureModes | ≥ 5 | ≥ 3 |
| examples | ≥ 3 (each ≥ 300 chars) | ≥ 2 |
| clarifyingQuestions | ≥ 6 | ≥ 4 |

## Writing rules (why the validator checks what it checks)

- **All `*Fa` fields are real, fluent Persian** — no placeholders, no lorem, no
  machine-translation tone. Use نیم‌فاصله (ZWNJ) correctly.
- **Guardrails are do-first**: the `doFa` is the primary instruction; `dontFa`
  exists only where an explicit prohibition genuinely helps.
- **Example prompts are complete** — a user could paste them into ChatGPT today
  and get a great result. They follow the 10-part anatomy.
- **Every id is stable** — ids are referenced by the engine and analytics-free
  telemetry; never rename an id after release.
- **priorities are 1–9**; the engine asks at most 4 questions, sorted by priority.
- **`gapKeywordsFa`** are checked against the user's idea: if any keyword is
  present, that question is considered already answered and is not asked.
  The engine additionally auto-skips questions whose answer it extracted as a
  fact from the idea (subject, platform, date, duration, instructor, price,
  audience) — the user is never re-asked anything they already said.
- **Examples are never truncated** — no `…`/`...` inside `promptFa`; the
  validator rejects them.
- **`keywordsFa` on structures** gates niche structures (content calendar,
  shot list, content pillars) behind explicit request; without the keyword
  they never win the selection.

### Design/output routing (engine, automatic)

Words like پوستر، بنر، لوگو، کاور، طراحی، تصویر route to design work in EVERY
domain: a designer persona is preferred, the deliverable becomes a designer
brief (or an image prompt / normal text when the user answers the
«خروجی چه باشد؟» question differently), and poster-specific constraints are
added. Platform names (اینستاگرام، تلگرام…) never change the deliverable.
Every ready domain should therefore contain at least one design persona and
one complete design example.

## Validation

```bash
python3 tools/validate_kb.py
```

Checks: registry consistency, required fields, id patterns and uniqueness,
Persian-text sanity, content minimums, example length floor. Exits non-zero on
any error — safe to wire into CI.
