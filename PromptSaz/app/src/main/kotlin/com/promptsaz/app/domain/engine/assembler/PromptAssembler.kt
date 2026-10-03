package com.promptsaz.app.domain.engine.assembler

import com.promptsaz.app.domain.model.ClarifyingAnswer
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.KbExample
import com.promptsaz.app.domain.model.KbOutputStructure
import com.promptsaz.app.domain.model.KbPersona
import com.promptsaz.app.domain.model.KbTerm
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.domain.model.PromptSection
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.SectionKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the 10-part section IR from a spec + knowledge base.
 * Every body string is dense Persian; sections are omitted only where the
 * detail level or a media target genuinely makes them irrelevant.
 */
@Singleton
class PromptAssembler @Inject constructor() {

    fun assemble(spec: PromptSpec, kb: DomainKnowledge?): List<PromptSection> {
        val idea = spec.idea.trim()
        val quick = spec.detailLevel == DetailLevel.QUICK
        val expert = spec.detailLevel == DetailLevel.EXPERT
        val answered = spec.answers.filter { !it.skipped }
        val skipped = spec.answers.filter { it.skipped }
        val sections = mutableListOf<PromptSection>()

        sections += role(spec, kb, quick, expert)
        sections += objective(spec, idea)
        sections += context(spec, kb, idea, answered, expert)
        sections += inputs(spec, skipped, idea)
        if (!spec.targetAi.isImageOrVideoModel) {
            sections += process(spec, kb, quick, expert)
        }
        sections += outputFormat(spec, kb, answered, quick)
        sections += constraints(kb, quick, expert)
        if (!quick && !spec.targetAi.isImageOrVideoModel) {
            sections += examples(kb, spec)
        }
        if (!quick) {
            sections += quality(kb, spec)
        }
        if (!spec.targetAi.isImageOrVideoModel) {
            sections += clarification(spec)
        }
        return sections
    }

    /** Short archive title, always Persian, capped at 40 characters. */
    fun makeTitle(spec: PromptSpec, domainName: String?): String {
        val cleanIdea = spec.idea.trim().replace("\n", " ").replace(Regex("\\s+"), " ")
        val prefix = domainName ?: "پرامپت"
        val ideaPart = if (cleanIdea.length > IDEA_TITLE_CHARS) {
            cleanIdea.take(IDEA_TITLE_CHARS).trim() + "…"
        } else if (cleanIdea.isEmpty()) {
            "بدون توضیح"
        } else {
            cleanIdea
        }
        val title = "$prefix: $ideaPart"
        return if (title.length > TITLE_MAX) title.take(TITLE_MAX - 1).trim() + "…" else title
    }

    // --- section builders ------------------------------------------------------

    private fun role(spec: PromptSpec, kb: DomainKnowledge?, quick: Boolean, expert: Boolean): PromptSection {
        val persona = selectPersona(spec, kb)
        val body = buildString {
            if (persona != null) {
                append("تو ${persona.titleFa} هستی؛ ${persona.expertiseFa}.")
                if (!quick && persona.whenToUseFa.isNotBlank()) {
                    append(" ${persona.whenToUseFa}.")
                }
            } else {
                append("تو یک متخصص باتجربه در حوزه «${kb?.nameFa ?: "موضوع درخواست"}» با نگاه عملی و نتیجه‌محور هستی.")
            }
            if (expert && kb != null) {
                val terms = relevantTerms(spec.idea, kb, 2)
                if (terms.isNotEmpty()) {
                    append(" این مفاهیم را درست به‌کار ببر: ")
                    append(terms.joinToString("، ") { "${it.termFa} (${it.termEn})" })
                    append(".")
                }
            }
        }
        return PromptSection(SectionKind.ROLE, "نقش", body)
    }

    private fun objective(spec: PromptSpec, idea: String): PromptSection {
        val body = buildString {
            if (spec.mode == PromptMode.IMPROVE) {
                append("بازنویسی حرفه‌ای و ساخت‌یافته درخواست پیوست‌شده در بخش زمینه؛ نیت اصلی کاربر دقیقاً حفظ شود و خروجی، یک پرامپت کامل و بی‌نقص باشد.")
            } else {
                append("انجام دقیق این درخواست و تحویل خروجی نهایی آماده استفاده: «$idea».")
                append(
                    when {
                        idea.contains("کد") || idea.contains("برنامه") || idea.contains("اپ") ->
                            " خروجی، کد کامل و قابل اجراست."
                        idea.contains("تحلیل") || idea.contains("بررسی") || idea.contains("مقایسه") ->
                            " خروجی، تحلیل نهایی قابل تصمیم‌گیری است."
                        else -> " خروجی، متن یا نتیجه نهایی بدون جای خالی است."
                    },
                )
            }
        }
        return PromptSection(SectionKind.OBJECTIVE, "هدف", body)
    }

    private fun context(
        spec: PromptSpec,
        kb: DomainKnowledge?,
        idea: String,
        answered: List<ClarifyingAnswer>,
        expert: Boolean,
    ): PromptSection {
        val lines = mutableListOf<String>()
        if (spec.mode == PromptMode.IMPROVE) {
            lines += "متن اصلی پرامپت کاربر (بازنویسی شود):"
            lines += "«${idea.trim()}»"
        } else {
            lines += "ایده اصلی کاربر: «$idea»."
        }
        answered.forEach { answer ->
            lines += "${answer.questionFa} → ${answer.value}"
        }
        if (expert && kb != null) {
            lines += "حوزه کار: ${kb.nameFa}."
        }
        if (lines.size == 1) {
            lines += "اطلاعات تکمیلی که در ورودی‌ها نیست را فرض نکن؛ ناقص بودن را طبق قاعده شفاف‌سازی بپرس."
        }
        return PromptSection(SectionKind.CONTEXT, "زمینه", lines.joinToString("\n"))
    }

    private fun inputs(spec: PromptSpec, skipped: List<ClarifyingAnswer>, idea: String): PromptSection {
        val variables = skipped.map { answer ->
            "- [${variableToken(answer.questionId)}]: ${answer.questionFa}"
        }.toMutableList()
        Regex("\\[([A-Za-z0-9_ ]{2,30})\\]").findAll(idea).forEach { match ->
            val token = "[${match.groupValues[1].trim().uppercase().replace(' ', '_')}]"
            if (variables.none { it.startsWith("$token:") }) {
                variables += "- $token: مقدار را کاربر تعیین می‌کند"
            }
        }
        val body = if (variables.isEmpty()) {
            "همه ورودی‌های لازم در متن مشخص است؛ متغیر جدید اضافه نکن."
        } else {
            variables.distinct().joinToString("\n")
        }
        return PromptSection(SectionKind.INPUTS, "ورودی‌ها و متغیرها", body)
    }

    private fun process(spec: PromptSpec, kb: DomainKnowledge?, quick: Boolean, expert: Boolean): PromptSection {
        val steps = when (spec.domainId) {
            "marketing" -> mutableListOf(
                "۱) بینش اصلی مخاطب را از بخش زمینه استخراج کن.",
                "۲) یک پیام اصلی انتخاب کن و همه خروجی را حول آن بساز.",
                "۳) خروجی را دقیقاً در قالب خواسته‌شده تولید کن.",
                "۴) هر ادعا را به ورودی یا مزیت اعلام‌شده گره بزن.",
            )
            "social_media" -> mutableListOf(
                "۱) پیام اصلی را در یک جمله تعریف کن.",
                "۲) قلاب (شروع محتوا) را با زاویه‌ای غیرکلیشه‌ای بنویس.",
                "۳) بدنه را با ریتم تند و بدون مکث بساز.",
                "۴) پایان‌بندی را با یک اقدام مشخص ببند.",
            )
            "programming" -> mutableListOf(
                "۱) ورودی، خروجی و محدودیت‌ها را فهرست کن.",
                "۲) ساده‌ترین راه‌حل کامل را طراحی کن و تصمیم مهم را در دو جمله توضیح بده.",
                "۳) کد کامل و قابل اجرا بنویس؛ بدون شبه‌کد.",
                "۴) تست برای حالت عادی، مرزی و خطا بنویس.",
            )
            else -> mutableListOf(
                "۱) خواسته را در یک جمله دقیق بازنویسی کن.",
                "۲) ساختار پاسخ را طبق قالب خروجی چیدن شروع کن.",
                "۳) محتوای اصلی را کامل و بدون حاشیه تولید کن.",
                "۴) پاسخ را با معیارهای کیفیت بسنج و اصلاح کن.",
            )
        }
        if (expert) steps += "${steps.size + 1}) قبل از پاسخ نهایی، چک‌لیست بخش کیفیت را بی‌صدا مرور کن."
        val body = if (quick) steps.take(3).joinToString("\n") else steps.joinToString("\n")
        return PromptSection(SectionKind.PROCESS, "روند کار", body)
    }

    private fun outputFormat(
        spec: PromptSpec,
        kb: DomainKnowledge?,
        answered: List<ClarifyingAnswer>,
        quick: Boolean,
    ): PromptSection {
        val structure = selectStructure(spec, kb)
        val tone = answered.firstOrNull { it.questionId in TONE_QUESTION_IDS }?.value
        val length = answered.firstOrNull { it.questionId in LENGTH_QUESTION_IDS }?.value
        val lines = mutableListOf<String>()
        if (structure != null) {
            lines += "ساختار: ${structure.titleFa} — ${structure.templateFa}"
        } else {
            lines += "ساختار: پاسخ بخش‌بندی‌شده با تیتر کوتاه؛ ابتدا نتیجه اصلی، سپس جزئیات."
        }
        lines += "زبان پاسخ: ${languageRule(spec)}."
        lines += "لحن: ${tone ?: "نیمه‌رسمی روان"}."
        lines += "طول: ${length ?: when (spec.detailLevel) {
            DetailLevel.QUICK -> "کوتاه؛ حداکثر ۱۵۰ کلمه."
            DetailLevel.STANDARD -> "متوسط؛ ۱۵۰ تا ۴۰۰ کلمه."
            DetailLevel.EXPERT -> "کامل و بدون حاشیه؛ طول تابع کامل بودن است."
        }}"
        if (quick) lines += "پیش‌درآمد و جمع‌بندی تکراری ننویس."
        return PromptSection(SectionKind.OUTPUT_FORMAT, "قالب خروجی", lines.joinToString("\n"))
    }

    private fun constraints(kb: DomainKnowledge?, quick: Boolean, expert: Boolean): PromptSection {
        val count = when {
            quick -> 3
            expert -> null // all
            else -> 5
        }
        val selected = kb?.guardrails ?: emptyList()
        val chosen = count?.let { selected.take(it) } ?: selected
        val lines = chosen.map { "- ${it.doFa}؛ ${it.dontFa}." }.toMutableList()
        lines += "- از عدد، منبع یا آماری که در ورودی نیست استفاده نکن؛ مطلب نامطمئن را همین‌طور نگو."
        lines += "- مستقیم وارد محتوا شو؛ مقدمه‌چینی و جمله انگیزشی ننویس."
        return PromptSection(SectionKind.CONSTRAINTS, "محدودیت‌ها و نگه‌داشت‌ها", lines.joinToString("\n"))
    }

    private fun examples(kb: DomainKnowledge?, spec: PromptSpec): PromptSection {
        val example = selectExample(kb, spec)
        val body = if (example != null) {
            "سبک و لحن مطلوب شبیه این نمونه است (فقط سبک، نه محتوا):\n«${example.promptFa.trim().take(EXAMPLE_SNIPPET_CHARS).trimEnd()}…»"
        } else {
            "پیش از تولید، یک نمونه کوچک از ساختار خروجی در ذهن بساز و خروجی نهایی را هم‌سبک آن بنویس."
        }
        return PromptSection(SectionKind.EXAMPLES, "مثال‌ها", body)
    }

    private fun quality(kb: DomainKnowledge?, spec: PromptSpec): PromptSection {
        val criteria = mutableListOf(
            "۱) انطباق کامل با قالب خروجی.",
            "۲) اتصال هر ادعا و عدد به ورودی‌ها.",
            "۳) بدون حاشیه، تکرار و کلی‌گویی.",
            "۴) یکدستی زبان و لحن در کل پاسخ.",
        )
        kb?.failureModes?.firstOrNull()?.let { mode ->
            criteria += "${criteria.size + 1}) پرهیز از این خطا: ${mode.symptomFa}."
        }
        val body = "پیش از ارسال، پاسخ را بی‌صدا با این معیارها بسنج و در صورت نقص اصلاح کن:\n" +
            criteria.joinToString("\n")
        return PromptSection(SectionKind.QUALITY, "معیارهای کیفیت", body)
    }

    private fun clarification(spec: PromptSpec): PromptSection {
        val max = if (spec.detailLevel == DetailLevel.QUICK) 2 else 3
        val body = "اگر اطلاعات ضروری برای شروع ناقص است، پیش از پاسخ حداکثر $max سؤال کوتاه و مشخص بپرس، " +
            "پاسخ کاربر را بگیر و بعد ادامه بده؛ به‌جای سؤال، حدس نزن."
        return PromptSection(SectionKind.CLARIFICATION, "قاعده شفاف‌سازی", body)
    }

    // --- knowledge-base selection helpers --------------------------------------

    private fun selectPersona(spec: PromptSpec, kb: DomainKnowledge?): KbPersona? {
        val personas = kb?.personas ?: return null
        if (personas.isEmpty()) return null
        val text = tokenize(spec.idea + " " + spec.answers.joinToString(" ") { it.value ?: "" })
        if (text.isEmpty()) return personas.first()
        return personas.maxByOrNull { persona ->
            tokenize(persona.titleFa + " " + persona.expertiseFa + " " + persona.whenToUseFa)
                .count { it in text }
        } ?: personas.first()
    }

    private fun selectStructure(spec: PromptSpec, kb: DomainKnowledge?): KbOutputStructure? {
        val structures = kb?.outputStructures ?: return null
        if (structures.isEmpty()) return null
        val text = tokenize(spec.idea + " " + spec.answers.joinToString(" ") { it.value ?: "" })
        if (text.isEmpty()) return structures.first()
        return structures.maxByOrNull { structure ->
            tokenize(structure.titleFa + " " + structure.descriptionFa).count { it in text }
        } ?: structures.first()
    }

    private fun selectExample(kb: DomainKnowledge?, spec: PromptSpec): KbExample? {
        val examples = kb?.examples ?: return null
        if (examples.isEmpty()) return null
        val exact = examples.firstOrNull { it.targetAi == spec.targetAi.id }
        val any = examples.firstOrNull { it.targetAi == "any" }
        return exact ?: any ?: examples.first()
    }

    private fun relevantTerms(idea: String, kb: DomainKnowledge, max: Int): List<KbTerm> {
        val text = tokenize(idea)
        val matched = kb.terminology.filter { term ->
            tokenize(term.termFa + " " + term.termEn).any { it in text }
        }
        return (matched + kb.terminology).distinctBy { it.termEn }.take(max)
    }

    private fun languageRule(spec: PromptSpec): String = when (spec.outputLanguage) {
        com.promptsaz.app.domain.model.OutputLanguage.PERSIAN -> "فارسی روان"
        com.promptsaz.app.domain.model.OutputLanguage.ENGLISH -> "انگلیسی"
        com.promptsaz.app.domain.model.OutputLanguage.SAME_AS_INPUT -> "همان زبان ورودی کاربر"
    }

    private fun tokenize(text: String): Set<String> =
        text.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 }
            .map { it.lowercase() }
            .toSet()

    private fun variableToken(questionId: String): String = when (questionId) {
        "audience" -> "AUDIENCE"
        "product-service", "content-topic" -> "TOPIC_OR_PRODUCT"
        "campaign-goal", "goal", "purpose" -> "GOAL"
        "platform" -> "PLATFORM"
        "brand-tone", "tone" -> "TONE"
        "competitive-edge" -> "COMPETITIVE_EDGE"
        "length-limit", "video-length", "length" -> "LENGTH"
        "cta" -> "CALL_TO_ACTION"
        "language-stack" -> "TECH_STACK"
        "skill-level" -> "SKILL_LEVEL"
        "output-type" -> "OUTPUT_TYPE"
        "context" -> "CONTEXT_DETAILS"
        "environment" -> "ENVIRONMENT"
        "testing-need" -> "TESTING"
        "format" -> "FORMAT"
        else -> questionId.replace('-', '_').uppercase()
    }

    private companion object {
        const val TITLE_MAX = 40
        const val IDEA_TITLE_CHARS = 26
        const val EXAMPLE_SNIPPET_CHARS = 220
        val TONE_QUESTION_IDS = setOf("brand-tone", "tone")
        val LENGTH_QUESTION_IDS = setOf("length-limit", "video-length", "length")
    }
}
