# Phase 1 — Team Brainstorming, Scope & Architecture

**Project:** PromptSaz — «پرامپت‌ساز»
**Date:** 2026-10-02 · **Status:** Delivered — awaiting approval
**Plan file:** `PromptSaz/docs/PHASE_1.md` (this document)

---

## 0. Session context

**The team in the room:**

1. **Principal Prompt Engineer** — owns the 10-part prompt anatomy, target-model adaptation, prompt quality.
2. **Domain Strategist & Copywriter** — owns feature scope, Persian copy, knowledge-base content direction.
3. **Android Software Architect** — owns architecture and technology decisions.
4. **Senior Android Developer (Kotlin / Jetpack Compose)** — owns buildability and code structure.
5. **UI/UX Designer (Persian RTL specialist)** — owns the minimal, fast, RTL-first experience.
6. **QA Engineer** — owns risk, edge cases, and honesty of claims.

**Repo context (decision):** This repository already contains unrelated files (a Telegram-channel export `result.json`, two school-district Excel reports, `IRANSans.ttf`, a horse SVG from svgrepo, a photo). They are **left untouched**. The Android project will be created in a clean subfolder **`PromptSaz/`**. If any of those files (e.g., the horse SVG or the photo) are intended as branding assets for the app, say so when approving and we will incorporate them.

---

## 1. Brainstorming session — summary of debates and decisions

### 1.1 How minimal is "minimal" on the home screen?
- **Designer:** the home screen must be exactly one text box and one primary button — anything more breaks the promise of speed.
- **Copywriter:** the four selectors (domain, target AI, output language, detail level) materially change prompt quality; hiding them hurts.
- **Resolution:** Home = idea box + primary CTA «طراحی پرامپت», plus **one collapsed settings row** («تنظیمات پرامپت ▾») that expands into four chip groups. Sensible defaults (عمومی / هر مدلی / مثل ورودی / استاندارد) mean "type → tap" works with zero configuration. **Designer wins on the default view; Copywriter wins on access.**

### 1.2 Offline engine architecture (the central debate)
- **Developer, option A:** pure string templates per domain — fastest to ship. **Rejected:** one-size-fits-all, cannot adapt per target model, low quality ceiling (Prompt Engineer's veto: "templates produce prompts that read like forms").
- **Prompt Engineer, option B:** KB-fragment composition — the knowledge bases supply personas, structures, guardrails, examples; the engine stitches them around the user's idea. Necessary but not sufficient.
- **Architect, option C (adopted):** a **three-stage pipeline with an intermediate representation**:
  1. `PromptSpec` (idea + domain + target + language + detail + answers) →
  2. **Section IR** (an ordered list of 10 typed sections, still structured data) →
  3. **Target rendering** (adapter converts IR into the final text for ChatGPT / Claude / Gemini / image / video / generic).
- **Why IR matters:** the Quality Scorer, the Variant generator, and the Improve-mode rewriter all operate on structured sections, not on flat text — deterministic, testable, instant. Golden-file unit tests per (domain × target) lock quality.
- **Prompt Engineer's condition:** every section must be dense and imperative — no filler, no pleasantries; a self-lint pass rejects banned patterns. Reasoning steps are included in Process **only** where they improve quality (per spec).

### 1.3 Clarifying questions — before or after generation?
- **Designer:** after generation, conversationally (like chatting with an AI).
- **Copywriter + QA:** **before** — no wasted generation; tap-to-answer chips make it 2 seconds; the spec explicitly allows skipping.
- **Resolution:** before generation, 2–4 questions max, priority-ordered, driven by the domain KB (each question declares which "gaps" in the idea trigger it), always with «رد کردن».

### 1.4 What "Skip" actually does (Prompt Engineer's proposal — adopted)
A skipped question is **not silently ignored**: it becomes a `[BRACKETED]` input variable in Section 4 (Inputs / Variables) of the prompt, so the target AI (or the user, later) fills it in. Skipping never degrades structure — it converts unknowns into explicit variables. Belt-and-suspenders: Section 10 (Clarification Rule) always instructs the target AI to ask up to N questions instead of guessing.

### 1.5 Quality Score — honest by design
- **QA:** an offline heuristic cannot measure "quality"; showing 87/100 as truth is false precision.
- **Copywriter:** users need a concrete, motivating signal.
- **Resolution:** the score is named **«امتیاز کامل بودن ساختار»** (structural-completeness score). Weighted 10-part rubric (below), per-section breakdown, and one-tap **deterministic** improvements (e.g., "قالب خروجی مشخص نیست → اضافه شود"). UI copy states it measures the *structure* of the prompt, not the target model's answer quality. **QA's framing wins.**

### 1.6 Variants
Three variants generated from the **same IR** (not three separate generations): «فشرده» (merges sections, drops examples), «استاندارد» (as generated), «خلاقانه» (bolder persona voice, richer example). Deterministic, instant, offline. Each variant is re-scored; stored linked by `groupId`.

### 1.7 Improve-existing-prompt mode
Offline heuristic analyzer over the pasted prompt: section detection (role/format/constraint markers), placeholder detection, vagueness and banned-pattern scan, target-fit checks (e.g., image-model target but conversational prompt). Output: a Persian weakness report → rewrite through the same assembler. In AI mode, the provider performs the analysis using the same rubric. Result screen is identical for both paths.

### 1.8 AI mode and the PromptProvider design (Architect)
- **Pluggable interface** (sketch in §4.4) — providers register via Hilt multibinding; Settings lists what's installed.
- **Recommended first provider:** *OpenAI-compatible* with a **user-editable base URL + model name** — one code path covers OpenAI, OpenRouter, DeepSeek, Groq, and self-hosted Ollama/LM Studio. *(Pending your decision — Q2.)*
- **Meta-prompt approach:** in AI mode the engine itself builds the instruction sent to the LLM, using the **same 10-part anatomy and the same domain KB**. So AI mode inherits the structural guarantees; its output is scored by the same rubric.
- **Resilience:** timeout / 401–403 / no network / malformed JSON all map to clear Persian messages; clarification suggestions from the provider parse leniently with offline fallback.

### 1.9 DI: Hilt vs manual (visible disagreement, resolved)
- **Architect:** Hilt — industry standard, compile-time safety, and multibinding is a natural fit for the provider registry.
- **Developer:** manual DI (an `AppContainer`) — zero annotation-processing overhead, simpler for contributors, faster cold builds.
- **QA tiebreak for Hilt:** Room + ViewModel integrations are battle-tested, and `Set<@JvmSuppressWildcards PromptProvider>` makes the plug-in point a one-liner. Build cost at this app size is acceptable.
- **Decision: Hilt 2.60.1 via KSP.** *Dissent recorded:* all injection sites stay constructor-only so a future switch to manual DI is mechanical.

### 1.10 Module structure: single module vs multi-module (visible disagreement, resolved)
- **Architect:** `:core:engine` (pure JVM) + feature modules.
- **Developer + QA:** one `:app` module with strictly layered **packages**; the `domain`/`engine` packages import **zero Android APIs** by convention, so they remain pure-JVM unit-testable.
- **Decision: single module now.** The package tree maps 1:1 to future Gradle modules; the extraction order is documented (engine → data → features). *Architect's dissent recorded.*

### 1.11 Font & typography (QA licensing catch)
- **Spec suggests Vazirmatn — confirmed:** OFL-licensed (safe to bundle and redistribute), current release **v33.003**, excellent Persian/Arabic coverage, includes Persian digit glyphs. We will **bundle static weights** (Regular / Medium / Bold) rather than the variable font — variable-font rendering is unreliable below API 26, and we support API 24+.
- **`IRANSans.ttf` found in this repo:** IRANSans (FontIran) is free for *personal* use; bundling it in a distributed app requires a commercial license we cannot verify. **Not bundled by default** *(your call — Q5)*.
- Downloadable Google Fonts rejected: many target devices lack Play Services.

### 1.12 Persian numbers, dates, and bidi (Designer + Developer)
- All user-facing numbers rendered with Persian digits (۰–۹) via a converter util (never `String.format` defaults).
- **Jalali (Shamsi) dates** via a self-written, unit-tested converter (known-date vectors) — no external dependency; Persian month/weekday names; relative dates like «۳ ساعت پیش».
- The app is Persian-only: **RTL is forced at the composition root** (works even on an English system), `supportsRtl="true"`, default `values/strings.xml` is Persian.
- Mixed Persian/English (target names, `[BRACKETED]` variables) handled with explicit bidi wrapping; ZWNJ (نیم‌فاصله) discipline enforced in string review; Persian punctuation «،» everywhere.

### 1.13 Tone of voice (Copywriter)
Warm, semi-formal **written** Persian («شما», no heavy slang) — the audience spans business owners, students, and creators. Microcopy is short and concrete; empty states teach the anatomy in one sentence. English appears only for target-AI names and technical tokens (per spec).

### 1.14 Archive & storage (Developer + QA)
Room with a single `prompts` table; **schema export + migrations from day one**. Tags stored as a simple list column via a `TypeConverter` (autocomplete from existing tags); search via `LIKE` over title + text + tags (FTS deferred to v1.1 — honest sizing for realistic archive sizes). Export/import via the Storage Access Framework (no permissions needed); share via the standard share sheet (`ACTION_SEND`); every generation auto-saves (spec).

### 1.15 Security & network posture (Architect + QA)
- **Offline by default: the app performs zero network calls unless the user enables AI mode.**
- API key in **EncryptedSharedPreferences** (`androidx.security:security-crypto` — verified **1.1.0 stable**); never logged, never included in exports, no analytics anywhere.

### 1.16 Onboarding
No multi-screen tour. Smart empty states + one-line hints. (QA: fewer states to test and break; Designer: faster first run.)

### Decision log

| # | Decision | Rationale | Dissent recorded |
|---|----------|-----------|------------------|
| D1 | Home = 1 box + 1 CTA + collapsed options row | Speed + access compromise | — |
| D2 | Engine = Spec → Section IR → target rendering | Quality, testability, enables score/variants/rewrite | Dev's plain templates rejected |
| D3 | Clarify **before** generating; 2–4 questions; always skippable | No wasted generation; spec-compliant | Designer preferred after |
| D4 | Skipped questions → `[BRACKETED]` variables | Never guess silently | — |
| D5 | Score = «امتیاز کامل بودن ساختار», 10-part weighted rubric | Honest, actionable | — |
| D6 | Variants = IR transforms (فشرده/استاندارد/خلاقانه) | Deterministic, instant | — |
| D7 | Improve mode = heuristic analyzer + assembler rewrite | Works offline | — |
| D8 | First provider: OpenAI-compatible + editable base URL (pending Q2) | One code path, many backends | — |
| D9 | Hilt 2.60.1 via KSP | Standard, provider multibinding | Dev preferred manual DI |
| D10 | Single `:app` module, pure-Kotlin domain packages | Build simplicity, JVM tests | Architect wanted multi-module |
| D11 | Bundle Vazirmatn v33.003 static weights (OFL) | Legal + reliable on API 24+ | IRANSans licensing blocked |
| D12 | Persian digits + Jalali dates via self-written utils | No deps, fully testable | — |
| D13 | UI register: warm semi-formal written Persian | Broad audience | — |
| D14 | Tags as list column; LIKE search; FTS later | Right-sized for MVP | — |
| D15 | EncryptedSharedPreferences for API key; key never exported | Security | — |
| D16 | No onboarding tour; empty states | Speed + fewer failure modes | — |
| D17 | Room schema export + Migration objects from v1 | Future-proof | — |
| D18 | Bundle a light «عمومی» (general) KB in addition to the 3 rich domains | Needed when user doesn't pick a domain | — |

---

## 2. Final feature list — MVP (v1.0) vs later

### v1.0 (Phases 2–5)

| Area | v1.0 scope |
|------|-----------|
| **Home** | Idea box (char counter, 4,000-char cap), mode toggle «پرامپت جدید / بهبود پرامپت موجود», collapsed options row: domain (12 + عمومی), target AI, output language (فارسی / انگلیسی / مثل ورودی), detail level (سریع / استاندارد / حرفه‌ای). Primary CTA «طراحی پرامپت». |
| **Clarify flow** | 2–4 tap-to-answer chip questions (+ optional free text), «رد کردن» per question and globally, back navigation. |
| **Result** | Prompt card with per-section view, one-tap «کپی», quality score + 10-part breakdown, one-tap «بهبود» suggestions, variants «فشرده / استاندارد / خلاقانه», regenerate, share, auto-save confirmation. |
| **Improve mode** | Paste existing prompt → Persian weakness report → rewritten prompt (same result screen). |
| **Engine (offline, default)** | 10-part anatomy assembly, 6 target style adapters, structural score, variants, clarifying-question engine, improve analyzer. Zero network. |
| **AI mode (optional)** | `PromptProvider` interface + registry, one provider implementation (per Q2), API key + base URL + model in settings, connection test, Persian error mapping. |
| **Archive** | Auto-save all generations; search; domain filter; favorites; tags (with autocomplete); edit title/text/tags/notes; duplicate; delete (with confirm); re-copy; share sheet; JSON export/import via SAF. |
| **KB browser** | Browse ready domains (personas, terminology, structures, guardrails, examples); remaining domains listed as «به‌زودی» with roadmap note. |
| **Settings** | Theme (سیستم / روشن / تیره), defaults for domain/target/language/detail, AI mode config, about. |
| **Localization & theming** | Persian-only UI, forced RTL, bundled Vazirmatn, Persian digits, Jalali dates, Material 3 light + dark, accessible & responsive layouts. |
| **Data** | Room archive, DataStore settings, EncryptedSharedPreferences key, KB JSON assets (editable without code changes). |

### v1.1+ (roadmap — JSON structure and UI slots ready from day one)

- The remaining **9 domain knowledge bases** (content-only additions, no code changes).
- Native **Gemini / Anthropic** providers (in addition to OpenAI-compatible).
- **AI-generated clarifying questions** (provider-driven, JSON-parsed, offline fallback).
- Starter **template library** («پرامپت‌های آماده»).
- Share a prompt as a **styled image card**.
- FTS4 search; folders/collections; edit-history diff.
- User-importable custom knowledge bases.

### Deliberately not planned
Cloud accounts/sync or any server (spec forbids), web version, ads, analytics, prompt marketplace. The main screen stays minimal per spec.

---

## 3. Technology stack — versions verified on 2026-10-02

Verification method: live release metadata (GitHub Releases API for JetBrains/Google OSS), and the official Android release notes pages fetched today. No version below is guessed.

| Component | Choice | Version | Verified via |
|-----------|--------|---------|--------------|
| Language | Kotlin | **2.4.20** | JetBrains/kotlin GitHub releases |
| Build | Android Gradle Plugin | **9.4.0** (Sep 2026) | developer.android.com AGP release notes |
| Build | Gradle wrapper | **9.8.0** (AGP 9.4 requires ≥ 9.6.0) | gradle/gradle GitHub releases + AGP notes |
| UI | Jetpack Compose (BOM) | **2026.09.00** → Compose 1.12.1, Material 3 **1.4.0** | developer.android.com BOM mapping |
| DI | Hilt | **2.60.1** | google/dagger GitHub releases |
| Codegen | KSP | **2.3.12** (pairing with Kotlin 2.4.20 re-checked when the catalog is written) | google/ksp GitHub releases |
| Persistence | Room | **2.8.5** | developer.android.com Room releases |
| Navigation | Navigation Compose | **2.10.2** | developer.android.com Navigation releases |
| Preferences | DataStore Preferences | pinned in Phase 2 catalog | developer.android.com |
| Secrets | androidx.security:security-crypto | **1.1.0** (stable) | developer.android.com Security releases |
| JSON | kotlinx.serialization | **1.11.0** (1.12.0 is RC — not used) | Kotlin/kotlinx.serialization GitHub releases |
| Networking (AI mode) | OkHttp | 5.x — exact pin in Phase 2 catalog | to be pinned from official source |
| Desugaring | desugar_jdk_libs (for `java.time` on API < 26) | pinned in Phase 2 catalog | developer.android.com |
| Font | Vazirmatn | **v33.003** (OFL) | rastikerdar/vazirmatn GitHub releases |
| SDK | minSdk **24**, compileSdk/targetSdk **36** | Play requires API 36 for new apps since Aug 31, 2026 | Google Play policy (published deadlines) |
| JDK | 17+ (AGP 9.4 minimum) | | AGP 9.4 release notes |

**Honest build-verification constraint (QA):** this workspace has **no JDK/Android SDK and cannot reach Google Maven**, so Gradle builds cannot run here. Mitigations: (1) every dependency version above is pinned from official sources; (2) code is delivered complete and reviewed file-by-file; (3) knowledge-base JSON and pure-logic invariants are validated in-workspace with a Python validator (`tools/validate_kb.py`); (4) the README (Phase 5) gives exact Android Studio + `gradlew` APK instructions, which is where compilation is verified. If the environment gains network access to Google Maven later, we will additionally run a headless build.

---

## 4. Architecture

### 4.1 Layers (single `:app` module, strict packages)

```
┌────────────────────────── UI — Jetpack Compose (RTL, M3) ──────────────────────────┐
│  Home → Clarify → Result   ·   Archive / Detail   ·   KB Browser   ·   Settings    │
│  ViewModels (StateFlow<UiState>, unidirectional data flow, SavedStateHandle)       │
└───────────────────────────────────────┬────────────────────────────────────────────┘
                                        │ use cases (GeneratePrompt / ImprovePrompt)
┌───────────────────────────────────────▼────────────────────────────────────────────┐
│  DOMAIN — pure Kotlin, zero Android imports                                        │
│  PromptEngine: ClarificationEngine → PromptAssembler (Section IR) →                │
│    TargetStyleAdapter (text) → QualityScorer → VariantGenerator / PromptRewriter   │
│  PromptProvider (interface — plug point for AI mode) · repository interfaces       │
└───────────────────────────────────────┬────────────────────────────────────────────┘
                                        │
┌───────────────────────────────────────▼────────────────────────────────────────────┐
│  DATA                                                                              │
│  Room (archive) · KB JSON assets (loader + cache) · DataStore (settings)           │
│  EncryptedSharedPreferences (API key) · OkHttp provider impls · SAF export/import  │
└────────────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Generation pipeline (one pass, step by step)

1. Home collects a `PromptSpec` (idea, domain, target, output language, detail level, mode).
2. `ClarificationEngine` selects 2–4 highest-priority KB questions whose declared gaps are missing from the idea; UI shows chips; answers or skips return.
3. `PromptAssembler` builds the **Section IR**: persona (KB-matched, detail-aware), normalized objective, context, `[BRACKETED]` variables (from skipped questions), process steps, output format (KB structure templates), constraints (KB guardrails + global rules), example, quality criteria, clarification rule.
4. `VariantGenerator` optionally derives «فشرده» / «خلاقانه» IRs.
5. `TargetStyleAdapter` renders each IR into final text (see matrix below).
6. `QualityScorer` scores rendered text against the rubric and computes one-tap fixes.
7. `GeneratePromptUseCase` auto-saves via `PromptRepository` (variants share a `groupId`) and emits the result.
8. **AI mode** replaces steps 3–5 with `PromptProvider.generatePrompt()` (engine-built meta-prompt); steps 6–7 unchanged.

### 4.3 Target-model adaptation matrix

| Target AI | Rendering strategy | Section handling |
|-----------|--------------------|------------------|
| ChatGPT | Markdown `##` sections, bold labels | All 10 |
| Claude | XML-style tags `<role> … </role>` | All 10, tag-wrapped |
| Gemini | Markdown, grouped Instruction / Context / Format | All 10, grouped |
| DeepSeek | ChatGPT-style markdown | All 10 |
| Midjourney (image) | Comma-separated descriptive phrases + `--parameters` | Merged per spec: Role→subject+style, Context→scene/mood/lighting, Constraints→`--no` list, Output→`--ar/--v`; Process & Clarification omitted as genuinely irrelevant |
| Video generators (Sora / Runway / Kling) | Shot-by-shot descriptive blocks + duration/camera moves | Merged like image + shot list |
| هر مدلی (Any) | ChatGPT-style markdown | All 10 |

### 4.4 PromptProvider — design preview (final file lands in Phase 3)

```kotlin
// PromptSaz/app/src/main/kotlin/com/promptsaz/app/domain/provider/PromptProvider.kt
// Plug point for AI mode. Implementations live in data/remote and register via Hilt multibinding.

interface PromptProvider {
    val id: String                    // e.g. "openai_compatible"
    val displayName: String           // Persian label shown in Settings
    val supportsClarification: Boolean

    suspend fun isConfigured(): Boolean
    suspend fun testConnection(): ProviderHealth
    suspend fun generatePrompt(request: ProviderRequest): ProviderResponse
    suspend fun suggestClarifications(request: ProviderRequest): List<ClarifyingQuestion>
}

sealed interface ProviderHealth {
    data object Ok : ProviderHealth
    data class Failed(val persianMessage: String) : ProviderHealth  // ready to show to the user
}

data class ProviderRequest(
    val spec: PromptSpec,             // idea + domain + target + language + detail + answers
    val knowledge: DomainKnowledge?,  // loaded KB for the chosen domain
)

data class ProviderResponse(
    val promptText: String,
)
```

### 4.5 Data design summary (full schema in Phase 2)

**Room `prompts` table:** `id`, `groupId` (links variants), `title`, `promptText`, `originalIdea`, `domainId`, `targetAi`, `outputLanguage`, `detailLevel`, `score`, `isFavorite`, `tags` (List via converter), `notes`, `isAiGenerated`, `createdAt`, `updatedAt`. Indices: `createdAt`, `domainId`, `isFavorite`. Schema exported to JSON; migrations versioned.

**Settings (DataStore):** theme, default domain/target/language/detail, AI-mode enabled, provider id, base URL, model name. **Secure (EncryptedSharedPreferences):** API key only.

**KB assets (`assets/knowledge/`):** `_registry.json` (all 12 domains + status), one JSON per domain, a light `general.json`, and `kb.schema.json` for validation. Expandable without code changes (spec).

### 4.6 Knowledge-base JSON — top-level shape (full schema + content in Phase 2)

```jsonc
{
  "id": "marketing",
  "nameFa": "بازاریابی و تبلیغات",
  "status": "ready",                     // "ready" | "coming_soon"
  "personas":        [ { "id": "growth_marketer", "titleFa": "…", "expertiseFa": "…", "whenToUseFa": "…" } ],
  "terminology":     [ { "termFa": "…", "termEn": "…", "definitionFa": "…" } ],
  "outputStructures":[ { "id": "aida", "titleFa": "AIDA", "descriptionFa": "…", "templateFa": "…" } ],
  "guardrails":      [ { "id": "no-hype", "doFa": "…", "dontFa": "…", "whyFa": "…" } ],
  "failureModes":    [ { "symptomFa": "…", "fixFa": "…" } ],
  "examples":        [ { "id": "…", "titleFa": "…", "targetAi": "chatgpt", "promptFa": "…" } ],
  "clarifyingQuestions": [ { "id": "audience", "questionFa": "مخاطب اصلی کیست؟", "priority": 1,
                             "chips": [ { "id": "b2c", "labelFa": "مصرف‌کننده نهایی" } ],
                             "allowFreeText": true, "gapKeywordsFa": [ "مخاطب", "مشتری" ] } ]
}
```

Each of the 3 launch domains ships: ≥4 personas, ≥10 terminology entries, ≥4 output structures, ≥8 guardrails, ≥5 failure modes, ≥3 full example prompts, ≥6 clarifying questions — **real, high-quality Persian content, not placeholders.**

### 4.7 Quality-score rubric (weights sum to 100)

| # | Section | Weight | Rule checks |
|---|---------|-------:|-------------|
| 1 | نقش (Role) | 10 | Specific expert persona matched to task — not «دستیار عمومی» |
| 2 | هدف (Objective) | 12 | Explicit deliverable + the reason behind it |
| 3 | زمینه (Context) | 12 | Audience, situation, constraints present |
| 4 | ورودی‌ها (Inputs) | 10 | Bracketed variables for every unknown |
| 5 | روند (Process) | 10 | Ordered, concrete steps; zero filler |
| 6 | قالب خروجی (Output format) | 14 | Structure + length + tone + language explicit |
| 7 | محدودیت‌ها (Constraints) | 12 | Positive phrasing first, explicit prohibitions where needed, anti-hallucination |
| 8 | مثال‌ها (Examples) | 8 | ≥1 style-bearing example |
| 9 | معیار کیفیت (Quality criteria) | 7 | Silent self-check instruction present |
| 10 | قاعده شفاف‌سازی (Clarification) | 5 | "Ask ≤ N questions, don't guess" present |

For image/video targets, sections the adapter legitimately merges count as covered (spec allows omission only when genuinely irrelevant).

### 4.8 Illustrative output excerpt (ChatGPT target, marketing domain — abridged)

```text
## نقش
تو یک متخصص بازاریابی محتوایی با ۱۰ سال تجربه در کمپین‌های دیجیتال برای کسب‌وکارهای کوچک فارسی‌زبان هستی.

## هدف
نوشتن ۵ تیتر تبلیغاتی برای [نام محصول] با هدف افزایش نرخ کلیک صفحه فرود.

## ورودی‌ها
[نام محصول] · [مزیت رقابتی اصلی] · [مخاطب هدف]

## قالب خروجی
جدول ۵ ردیفی؛ ستون‌ها: تیتر | ساختار به‌کاررفته | دلیل اثرگذاری. هر تیتر حداکثر ۸ کلمه؛ لحن محاوره‌ای و مشخص.

## قاعده شفاف‌سازی
اگر اطلاعات ضروری درباره محصول یا مخاطب نداری، حداکثر ۳ سؤال بپرس؛ حدس نزن.
```

---

## 5. UX & RTL design principles

- **Screens (8 routes):** Home · Clarify · Result · Archive · Prompt detail · KB browser · KB domain · Settings. Single activity, Navigation Compose, arguments carry IDs; state survives process death (Room = source of truth + SavedStateHandle).
- **RTL:** forced `LayoutDirection.Rtl` at the theme root; all text `TextAlign.Start`; back arrows and chevrons mirrored; English tokens (ChatGPT, `[BRACKETS]`) wrapped for correct bidi.
- **Typography:** Vazirmatn Regular/Medium/Bold; sp-based sizes that survive system font scaling; min touch targets 48dp.
- **Theming:** Material 3; light + dark from one brand palette (deep teal family); dynamic color on Android 12+ where available.
- **Responsiveness:** single-column on phones (320dp → 480dp widths), comfortable two-pane usage left for v1.1; scrollable result sections for very long prompts.
- **Accessibility:** TalkBack labels in Persian for all interactive elements; contrast ≥ AA in both themes; no color-only signaling (score shown with number + label).
- **Errors (Persian, always actionable):** empty idea → inline hint «اول توضیح کوتاهی از چیزی که می‌خواهی بنویس»; clipboard failure → «کپی ناموفق بود؛ متن را انتخاب و دستی کپی کنید»; DB/import failures → specific message + retry; AI mode → mapped messages («کلید API نامعتبر است», «اتصال برقرار نشد — اینترنت یا نشانی سرور را بررسی کنید», «پاسخ سرور قابل خواندن نبود»).

---

## 6. QA — risk register (top items) and early edge-case list

| Risk | Mitigation |
|------|------------|
| Sandbox cannot compile (no JDK/SDK; Google Maven unreachable) | Verified version pins; complete reviewed code; Python KB validation; user-side build guide; re-check KSP↔Kotlin↔Hilt triangle when catalog is written |
| Version-trio incompatibility (Kotlin/KSP/Hilt/AGP) | Pin known-good set from official notes; document exact pins in `libs.versions.toml` |
| Font download blocked | GitHub codeload verified reachable (HTTP 200) for Vazirmatn v33.003; fallback: you drop the 3 TTFs into `res/font/` (we give exact links) |
| IRANSans bundled by mistake | Explicitly excluded; your confirmation required (Q5) |
| Bidi mixing Persian + English in prompts | Bidi wrapper util + test vectors with mixed strings |
| Room migrations later | Schema JSON export + Migration objects from version 1 |
| KB JSON typos break engine at runtime | `tools/validate_kb.py` runs schema + content checks on every KB file before delivery |
| Process death / rotation mid-flow | Room as source of truth; SavedStateHandle in ViewModels |
| Provider returns malformed output | Lenient parsing, offline fallback, Persian error |
| R8 stripping serialization models | Release build verified in Phase 5; consumer rules documented |

**Edge cases already on the Phase 5 checklist:** empty/whitespace/emoji-only idea; 4,000+ chars; mixed-language ideas; all questions skipped; clipboard on old devices; corrupt or foreign import file; duplicate import; DB errors; API timeout/401/no-key/no-network; double-tap generate (debounce); back-stack correctness; dark-theme contrast; 200% font scale; very long prompts; share sheet absent.

---

## 7. Project file tree

`[P#]` marks the phase in which each file is created.

```
PromptSaz/
├── README.md                                          [P5: build & APK guide]
├── .gitignore                                         [P2]
├── settings.gradle.kts                                [P2]
├── build.gradle.kts                                   [P2]
├── gradle.properties                                  [P2]
├── gradle/
│   ├── libs.versions.toml                             [P2: single source of truth for versions]
│   └── wrapper/gradle-wrapper.properties              [P2]
├── gradlew · gradlew.bat                              [P2]
├── docs/
│   ├── PHASE_1.md                                     (this document)
│   ├── KNOWLEDGE_BASE_GUIDE.md                        [P2: how to edit/extend KB JSON]
│   └── APK_BUILD.md                                   [P5]
├── tools/
│   └── validate_kb.py                                 [P2: JSON schema + content validation]
└── app/
    ├── build.gradle.kts                               [P2]
    ├── proguard-rules.pro                             [P2]
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml                    [P2]
        │   ├── kotlin/com/promptsaz/app/
        │   │   ├── PromptSazApplication.kt            [P2: @HiltAndroidApp]
        │   │   ├── MainActivity.kt                    [P4]
        │   │   ├── di/
        │   │   │   ├── AppModule.kt                   [P2]
        │   │   │   ├── DatabaseModule.kt              [P2]
        │   │   │   ├── KbModule.kt                    [P2]
        │   │   │   └── ProviderModule.kt              [P3: PromptProvider multibinding]
        │   │   ├── domain/
        │   │   │   ├── model/
        │   │   │   │   ├── PromptSpec.kt · GeneratedPrompt.kt · PromptSection.kt        [P3]
        │   │   │   │   ├── DomainCategory.kt · TargetAi.kt · OutputLanguage.kt · DetailLevel.kt  [P2]
        │   │   │   │   ├── VariantStyle.kt · QualityReport.kt · ClarifyingQuestion.kt · ImprovementReport.kt  [P3]
        │   │   │   │   └── KbModels.kt                [P2: DomainKnowledge, Persona, Guardrail, Example…]
        │   │   │   ├── engine/
        │   │   │   │   ├── PromptEngine.kt            [P3: facade]
        │   │   │   │   ├── assembler/PromptAssembler.kt  [P3: Section IR builder]
        │   │   │   │   ├── style/
        │   │   │   │   │   ├── TargetStyleAdapter.kt     [P3: interface]
        │   │   │   │   │   ├── ChatGptStyleAdapter.kt · ClaudeStyleAdapter.kt · GeminiStyleAdapter.kt
        │   │   │   │   │   ├── ImageModelStyleAdapter.kt · VideoModelStyleAdapter.kt · GenericStyleAdapter.kt  [P3]
        │   │   │   │   ├── clarify/ClarificationEngine.kt  [P3]
        │   │   │   │   ├── score/QualityScorer.kt         [P3: rubric + one-tap fixes]
        │   │   │   │   ├── variant/VariantGenerator.kt    [P3]
        │   │   │   │   └── improve/PromptAnalyzer.kt · PromptRewriter.kt  [P3]
        │   │   │   ├── provider/
        │   │   │   │   ├── PromptProvider.kt           [P3: interface — AI plug point]
        │   │   │   │   └── ProviderRegistry.kt         [P3]
        │   │   │   └── repository/
        │   │   │       ├── PromptRepository.kt · KbRepository.kt · SettingsRepository.kt  [P2: interfaces]
        │   │   │       └── GeneratePromptUseCase.kt · ImprovePromptUseCase.kt  [P3]
        │   │   ├── data/
        │   │   │   ├── db/
        │   │   │   │   ├── PromptSazDatabase.kt       [P2]
        │   │   │   │   ├── PromptEntity.kt · PromptDao.kt · Converters.kt  [P2]
        │   │   │   │   └── Migrations.kt              [P2: exported schema from v1]
        │   │   │   ├── kb/KbLoader.kt                 [P2: asset JSON → DomainKnowledge, cached]
        │   │   │   ├── remote/
        │   │   │   │   ├── OpenAiCompatibleProvider.kt  [P3]
        │   │   │   │   └── ProviderModels.kt          [P3: request/response DTOs]
        │   │   │   ├── settings/
        │   │   │   │   ├── SettingsStore.kt           [P2: DataStore]
        │   │   │   │   └── SecureKeyStore.kt          [P2: EncryptedSharedPreferences]
        │   │   │   ├── export/
        │   │   │   │   ├── ArchiveExporter.kt · ArchiveImporter.kt  [P4: SAF]
        │   │   │   │   └── ArchiveDto.kt              [P2]
        │   │   │   └── repository/
        │   │   │       └── PromptRepositoryImpl.kt · KbRepositoryImpl.kt · SettingsRepositoryImpl.kt  [P2]
        │   │   ├── ui/
        │   │   │   ├── theme/Theme.kt · Color.kt · Type.kt · Shape.kt  [P4: M3 + RTL + Vazirmatn]
        │   │   │   ├── nav/AppNavHost.kt · Routes.kt  [P4]
        │   │   │   ├── components/
        │   │   │   │   ├── PromptCard.kt · ScoreCard.kt · ChipGroup.kt · EmptyState.kt  [P4]
        │   │   │   │   └── ConfirmDialog.kt · ErrorBar.kt · PersianDateText.kt  [P4]
        │   │   │   ├── home/HomeScreen.kt · HomeViewModel.kt          [P4]
        │   │   │   ├── clarify/ClarifyScreen.kt · ClarifyViewModel.kt [P4]
        │   │   │   ├── result/ResultScreen.kt · ResultViewModel.kt    [P4]
        │   │   │   ├── archive/ArchiveScreen.kt · ArchiveViewModel.kt [P4]
        │   │   │   ├── archive/PromptDetailScreen.kt · PromptDetailViewModel.kt  [P4]
        │   │   │   ├── kb/KbBrowserScreen.kt · KbDomainScreen.kt · KbViewModel.kt [P4]
        │   │   │   └── settings/SettingsScreen.kt · SettingsViewModel.kt  [P4]
        │   │   └── util/
        │   │       ├── PersianDigits.kt · JalaliCalendar.kt · TimeAgo.kt  [P2]
        │   │       └── ClipboardUtils.kt · ShareUtils.kt · AppResult.kt  [P4]
        │   ├── res/
        │   │   ├── font/vazirmatn_regular.ttf · vazirmatn_medium.ttf · vazirmatn_bold.ttf  [P4]
        │   │   ├── values/strings.xml · themes.xml   [P4 — all Persian]
        │   │   ├── values-night/themes.xml           [P4]
        │   │   ├── drawable/ · mipmap-anydpi-v26/    [P4: adaptive launcher icon]
        │   │   └── xml/locales_config.xml · backup_rules.xml · data_extraction_rules.xml  [P4]
        │   └── assets/knowledge/
        │       ├── kb.schema.json                    [P2]
        │       ├── _registry.json                    [P2: 12 domains + عمومی + status]
        │       ├── general.json                      [P2: light general KB]
        │       └── marketing.json · social_media.json · programming.json  [P2: full KBs — pending Q1]
        ├── test/kotlin/com/promptsaz/app/            [P2–P3: engine goldens, rubric, Jalali, digits, KB loader]
        └── androidTest/kotlin/com/promptsaz/app/     [P5: DAO + critical Compose flows]
```

**Extraction path to modules (documented for later):** `domain/engine` → `:core:engine`, `data/*` → `:core:data`, each `ui/<screen>` → `:feature:<screen>`. Package boundaries already enforce the same seams.

---

## 8. Phase close

**(1) Completed**
- Full team brainstorming: 16 debates, 18 logged decisions, disagreements visible and resolved.
- Final feature list: MVP v1.0 vs later, plus a deliberate not-planned list.
- Technology stack with versions verified against live official sources on 2026-10-02.
- Architecture: layers, generation pipeline, target-adaptation matrix, PromptProvider design, data design, KB JSON shape, score rubric.
- UX/RTL principles and QA risk register with early edge cases.
- Complete project file tree, phase-mapped. Plan saved to `PromptSaz/docs/PHASE_1.md`.

**(2) Next — Phase 2 (upon approval)**
Gradle scaffold + version catalog · Room (entity, DAO, database, migrations, converters) · repositories · KB models + loader + schema + registry · **full Persian content for the 3 chosen domains + عمومی KB** · utils (Persian digits, Jalali calendar) with unit tests · `tools/validate_kb.py` · export/import DTOs.

**(3) Decisions needed from you** — asked as interactive questions alongside this document:
1. **Q1 — First 3 rich domains** (recommendation: بازاریابی و تبلیغات + شبکه‌های اجتماعی و سناریو ویدیو + برنامه‌نویسی و نرم‌افزار).
2. **Q2 — First AI provider** (recommendation: OpenAI-compatible with editable base URL).
3. **Q3 — Target-AI list for v1.0** (recommendation: compact 7-entry list).
4. **Q4 — App identity** (recommendation: «پرامپت‌ساز» / `com.promptsaz.app`).
5. **Q5 — Bundled font** (recommendation: Vazirmatn v33.003, OFL; IRANSans only if you hold a redistribution license).
