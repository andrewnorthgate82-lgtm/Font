package com.promptsaz.app.domain.engine.assembler

import com.promptsaz.app.domain.engine.facts.FactCategory
import com.promptsaz.app.domain.engine.facts.IdeaFacts
import com.promptsaz.app.domain.engine.facts.IdeaFactsExtractor
import com.promptsaz.app.domain.engine.routing.OutputRouter
import com.promptsaz.app.domain.engine.routing.OutputType
import com.promptsaz.app.domain.engine.routing.TaskCategory
import com.promptsaz.app.domain.model.ClarifyingAnswer
import com.promptsaz.app.domain.model.DetailLevel
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.KbExample
import com.promptsaz.app.domain.model.KbPersona
import com.promptsaz.app.domain.model.KbTerm
import com.promptsaz.app.domain.model.OutputLanguage
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.domain.model.PromptSection
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.SectionKind
import com.promptsaz.app.util.toPersianDigits
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the 10-part section IR from a spec + knowledge base.
 *
 * Design principles (per user feedback, applies to every domain):
 *  - Facts (names, dates, durations, platforms…) are EXTRACTED from the idea
 *    and listed once as «اطلاعات قطعی»; the raw idea sentence is never
 *    repeated, and nothing the user already said becomes a question or a
 *    [BRACKET] placeholder.
 *  - The deliverable (text / designer brief / image prompt) is routed from
 *    the idea + the «خروجی چه باشد؟» answer; platform names never change it,
 *    and a content calendar only appears when explicitly requested.
 *  - No raw knowledge-base metadata (persona «when to use», failure symptoms)
 *    leaks into the prompt; failure modes become direct instructions.
 *  - Every number in the Persian output is a Persian digit.
 */
@Singleton
class PromptAssembler @Inject constructor() {

    fun assemble(spec: PromptSpec, kb: DomainKnowledge?): List<PromptSection> {
        val idea = spec.idea.trim()
        val quick = spec.detailLevel == DetailLevel.QUICK
        val expert = spec.detailLevel == DetailLevel.EXPERT
        val facts = IdeaFactsExtractor.extract(idea)
        val outputType = OutputRouter.outputType(spec)
        val taskCategory = OutputRouter.taskCategory(spec)
        val answered = spec.answers.filter { !it.skipped }
        val variables = variablesFor(spec, facts, idea)
        val sections = mutableListOf<PromptSection>()

        sections += role(spec, kb, facts, outputType, taskCategory, quick, expert)
        sections += objective(spec, facts, outputType, taskCategory, variables, idea)
        sections += context(spec, kb, idea, facts, answered, expert)
        sections += inputs(variables)
        if (!isImageOrVideo(spec, outputType)) {
            sections += process(spec, kb, outputType, taskCategory, quick, expert)
        }
        sections += outputFormat(spec, kb, facts, outputType, taskCategory, answered, quick)
        sections += constraints(kb, outputType, taskCategory, quick, expert, facts)
        if (!quick && !isImageOrVideo(spec, outputType)) {
            sections += examples(kb, spec, outputType, taskCategory)
        }
        if (!quick && !isImageOrVideo(spec, outputType)) {
            sections += quality(kb, spec, outputType, taskCategory)
        }
        if (!isImageOrVideo(spec, outputType)) {
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

    private fun role(
        spec: PromptSpec,
        kb: DomainKnowledge?,
        facts: IdeaFacts,
        outputType: OutputType,
        taskCategory: TaskCategory,
        quick: Boolean,
        expert: Boolean,
    ): PromptSection {
        val persona = selectPersona(spec, kb, outputType, taskCategory)
        val body = buildString {
            when {
                persona != null -> append("تو ${persona.titleFa} هستی؛ ${persona.expertiseFa}.")
                taskCategory == TaskCategory.DESIGN || outputType == OutputType.DESIGN_BRIEF ->
                    append(
                        "تو کارگردان هنری و طراح گرافیک باتجربه هستی؛ متخصص پوستر، بنر و هویت بصری " +
                            "با تسلط بر تایپوگرافی فارسی و محدودیت‌های چاپ و نمایش دیجیتال.",
                    )
                else -> append(
                    "تو یک متخصص باتجربه در حوزه «${kb?.nameFa ?: "موضوع درخواست"}» با نگاه عملی و نتیجه‌محور هستی.",
                )
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

    private fun objective(
        spec: PromptSpec,
        facts: IdeaFacts,
        outputType: OutputType,
        taskCategory: TaskCategory,
        variables: List<Variable>,
    ): PromptSection {
        val body = buildString {
            if (spec.mode == PromptMode.IMPROVE) {
                append("بازنویسی حرفه‌ای و ساخت‌یافته پرامپت پیوست‌شده در بخش زمینه؛ نیت اصلی کاربر دقیقاً حفظ شود.")
            } else {
                append("تحویل نهایی: ${deliverableFa(outputType, taskCategory, facts)}. ")
                append("هدف، پاسخ کامل و آماده استفاده به درخواست کاربر در بخش زمینه است.")
            }
            append(
                if (variables.isEmpty()) {
                    " خروجی بدون جای خالی و آماده انتشار باشد."
                } else {
                    " مقادیر [متغیر] را در متن نهایی با اطلاعات واقعی جایگزین کن و هیچ جای خالی رها نکن."
                },
            )
        }
        return PromptSection(SectionKind.OBJECTIVE, "هدف", body)
    }

    private fun context(
        spec: PromptSpec,
        kb: DomainKnowledge?,
        idea: String,
        facts: IdeaFacts,
        answered: List<ClarifyingAnswer>,
        expert: Boolean,
    ): PromptSection {
        val lines = mutableListOf<String>()
        if (spec.mode == PromptMode.IMPROVE) {
            lines += "پرامپت اصلی کاربر (بازنویسی شود):"
            lines += "«${idea.trim()}»"
        } else {
            lines += "درخواست کاربر: «$idea»"
        }
        if (facts.entries.isNotEmpty()) {
            lines += ""
            lines += "اطلاعات قطعی (از متن کاربر؛ این‌ها را دقیقاً همین‌طور استفاده کن و تغییرشان نده):"
            facts.entries.forEach { fact -> lines += "- ${fact.labelFa}: ${fact.valueFa}" }
        }
        if (answered.isNotEmpty()) {
            lines += ""
            lines += "پاسخ‌های کاربر به سؤال‌ها:"
            answered.forEach { answer -> lines += "- ${answer.questionFa} ${answer.value}" }
        }
        if (expert && kb != null) {
            lines += "حوزه کاری: ${kb.nameFa}."
        }
        return PromptSection(SectionKind.CONTEXT, "زمینه", lines.joinToString("\n"))
    }

    private fun inputs(variables: List<Variable>): PromptSection {
        val body = if (variables.isEmpty()) {
            "همه اطلاعات لازم در بخش زمینه آمده است؛ متغیر جدید تعریف نکن و چیزی را که کاربر گفته دوباره نپرس."
        } else {
            variables.distinctBy { it.token }.joinToString("\n") { variable ->
                "- [${variable.token}]: ${variable.descriptionFa}"
            }
        }
        return PromptSection(SectionKind.INPUTS, "ورودی‌ها و متغیرها", body)
    }

    private fun process(
        spec: PromptSpec,
        kb: DomainKnowledge?,
        outputType: OutputType,
        taskCategory: TaskCategory,
        quick: Boolean,
        expert: Boolean,
    ): PromptSection {
        val steps: MutableList<String> = when {
            outputType == OutputType.DESIGN_BRIEF || (outputType == OutputType.ALL && taskCategory == TaskCategory.DESIGN) ->
                mutableListOf(
                    "۱) پیام اصلی و پیام‌های ثانویه را از اطلاعات قطعی استخراج کن.",
                    "۲) سلسله‌مراتب متن را بچین: عنوان اصلی، زیرعنوان، جزئیات کلیدی.",
                    "۳) عناصر اجباری (نام، تاریخ، مدرس یا برند، دعوت به اقدام) را مشخص کن.",
                    "۴) سبک بصری، رنگ و حال‌وهوا را متناسب با پیام پیشنهاد بده.",
                    "۵) بریف نهایی را کامل و بدون ابهام بنویس.",
                )
            outputType == OutputType.IMAGE_PROMPT ->
                mutableListOf(
                    "۱) سوژه اصلی و اجزای تصویر را از اطلاعات قطعی تعیین کن.",
                    "۲) سبک بصری، نور، پالت رنگی و زاویه دید را انتخاب کن.",
                    "۳) متن‌های روی تصویر (اگر لازم است) را دقیق مشخص کن.",
                    "۴) پرامپت نهایی تصویر را کامل بنویس.",
                )
            taskCategory == TaskCategory.CALENDAR ->
                mutableListOf(
                    "۱) بازه زمانی و تعداد محتوا را از درخواست کاربر تعیین کن.",
                    "۲) ستون‌های تقویم را بر اساس هدف انتخاب کن.",
                    "۳) برای هر روز، موضوع و قالب مشخص پیشنهاد بده.",
                    "۴) تقویم را کامل و قابل اجرا تحویل بده.",
                )
            spec.domainId == "marketing" ->
                mutableListOf(
                    "۱) بینش اصلی مخاطب را از بخش زمینه استخراج کن.",
                    "۲) یک پیام اصلی انتخاب کن و همه خروجی را حول آن بساز.",
                    "۳) خروجی را دقیقاً در قالب خواسته‌شده تولید کن.",
                    "۴) هر ادعا را به اطلاعات قطعی یا مزیت اعلام‌شده گره بزن.",
                )
            spec.domainId == "social_media" ->
                mutableListOf(
                    "۱) پیام اصلی را در یک جمله تعریف کن.",
                    "۲) قلاب (شروع محتوا) را با زاویه‌ای غیرکلیشه‌ای بنویس.",
                    "۳) بدنه را با ریتم تند و بدون مکث بساز.",
                    "۴) پایان‌بندی را با یک اقدام مشخص ببند.",
                )
            spec.domainId == "programming" ->
                mutableListOf(
                    "۱) ورودی، خروجی و محدودیت‌ها را فهرست کن.",
                    "۲) ساده‌ترین راه‌حل کامل را طراحی کن و تصمیم مهم را در دو جمله توضیح بده.",
                    "۳) کد کامل و قابل اجرا بنویس؛ بدون شبه‌کد.",
                    "۴) تست برای حالت عادی، مرزی و خطا بنویس.",
                )
            else ->
                mutableListOf(
                    "۱) خواسته را در یک جمله دقیق بازنویسی کن.",
                    "۲) ساختار پاسخ را طبق قالب خروجی بچین.",
                    "۳) محتوای اصلی را کامل و بدون حاشیه تولید کن.",
                    "۴) پاسخ را با معیارهای کیفیت بسنج و اصلاح کن.",
                )
        }
        if (expert) steps += "${fa(steps.size + 1)}) پیش از پاسخ نهایی، چک‌لیست بخش کیفیت را بی‌صدا مرور کن."
        val body = if (quick) steps.take(3).joinToString("\n") else steps.joinToString("\n")
        return PromptSection(SectionKind.PROCESS, "روند کار", body)
    }

    private fun outputFormat(
        spec: PromptSpec,
        kb: DomainKnowledge?,
        facts: IdeaFacts,
        outputType: OutputType,
        taskCategory: TaskCategory,
        answered: List<ClarifyingAnswer>,
        quick: Boolean,
    ): PromptSection {
        val tone = answered.firstOrNull { it.questionId in TONE_QUESTION_IDS }?.value
        val length = answered.firstOrNull { it.questionId in LENGTH_QUESTION_IDS }?.value
        val lines = mutableListOf<String>()

        when {
            outputType == OutputType.DESIGN_BRIEF || (outputType == OutputType.ALL && taskCategory == TaskCategory.DESIGN) -> {
                lines += "ساختار: بریف طراحی برای طراح گرافیک با این بخش‌ها — ۱) هدف و پیام اصلی ۲) مشخصات فنی (سایز و نسبت تصویر) ۳) سلسله‌مراتب متن (عنوان، زیرعنوان، جزئیات) ۴) عناصر اجباری ۵) سبک و حال‌وهوای بصری ۶) ملاحظات فنی."
                lines += "نسبت تصویر: ${ratioFor(facts)}."
            }
            outputType == OutputType.IMAGE_PROMPT -> {
                lines += "ساختار: یک پاراگراف پرامپت نهایی برای مدل تولید تصویر؛ ترتیب اجزا: سوژه، سبک، ترکیب‌بندی، نور، پالت رنگی؛ در پایان پارامترهای فنی (نسبت تصویر و جزئیات)."
                lines += "نسبت تصویر: ${ratioFor(facts)}."
            }
            outputType == OutputType.ALL -> {
                lines += "ساختار: سه بخش مجزا با تیتر واضح — ۱) متن و ساختار محتوا ۲) بریف طراحی گرافیک ۳) پرامپت تولید تصویر."
            }
            taskCategory == TaskCategory.CALENDAR -> {
                lines += "ساختار: جدول تقویم محتوایی با ستون‌های: روز | موضوع | قالب | پیام کوتاه | دعوت به اقدام."
            }
            else -> {
                val structure = selectStructure(spec, kb)
                if (structure != null) {
                    lines += "ساختار: ${structure.titleFa} — ${structure.templateFa}"
                } else {
                    lines += "ساختار: پاسخ بخش‌بندی‌شده با تیتر کوتاه؛ ابتدا نتیجه اصلی، سپس جزئیات."
                }
            }
        }
        lines += "زبان پاسخ: ${languageRule(spec)}."
        if (tone != null || !quick) lines += "لحن: ${tone ?: "نیمه‌رسمی روان"}."
        lines += "طول: ${length ?: when (spec.detailLevel) {
            DetailLevel.QUICK -> "کوتاه؛ حداکثر ${fa(150)} کلمه."
            DetailLevel.STANDARD -> "متوسط؛ ${fa(150)} تا ${fa(400)} کلمه."
            DetailLevel.EXPERT -> "کامل و بدون حاشیه؛ طول تابع کامل بودن است."
        }}"
        if (quick) lines += "پیش‌درآمد و جمع‌بندی تکراری ننویس."
        return PromptSection(SectionKind.OUTPUT_FORMAT, "قالب خروجی", lines.joinToString("\n"))
    }

    private fun constraints(
        kb: DomainKnowledge?,
        outputType: OutputType,
        taskCategory: TaskCategory,
        quick: Boolean,
        expert: Boolean,
        facts: IdeaFacts,
    ): PromptSection {
        val count = when {
            quick -> 3
            expert -> null // all
            else -> 5
        }
        val selected = kb?.guardrails ?: emptyList()
        val chosen = count?.let { selected.take(it) } ?: selected
        val lines = chosen.map { "- ${it.doFa}؛ ${it.dontFa}." }.toMutableList()
        when {
            outputType == OutputType.DESIGN_BRIEF || taskCategory == TaskCategory.DESIGN ->
                lines += listOf(
                    "- نسبت تصویر با پلتفرم هدف هماهنگ باشد (${ratioFor(facts)}).",
                    "- متن روی تصویر کوتاه و خوانا با حداکثر سه سطح سلسله‌مراتب (عنوان، زیرعنوان، جزئیات).",
                    "- فونت فارسی استاندارد و خوانا؛ کنتراست متن و پس‌زمینه کافی باشد.",
                    "- جای مشخص و واضح برای اطلاعات کلیدی (تاریخ، نام مدرس یا برند، ثبت‌نام یا تماس) در نظر بگیر.",
                    "- رنگ‌ها با هم هویت بصری واحد بسازند؛ از شلوغی بصری پرهیز کن.",
                )
            outputType == OutputType.IMAGE_PROMPT ->
                lines += listOf(
                    "- پرامپت تصویر یکپارچه و بدون تناقض بین اجزا باشد.",
                    "- متن فارسی روی تصویر را دقیق و کوتاه مشخص کن؛ از جمله‌های بلند روی تصویر پرهیز کن.",
                )
            taskCategory == TaskCategory.CALENDAR ->
                lines += listOf(
                    "- هر روز تقویم موضوع تکراری و بدون هدف نداشته باشد.",
                    "- قالب هر محتوا با محدودیت واقعی پلتفرم هماهنگ باشد.",
                )
            taskCategory == TaskCategory.VIDEO ->
                lines += listOf(
                    "- ریتم محتوا تند باشد و در ثانیه‌های اول قلاب اصلی بیاید.",
                    "- برای هر پلان، تصویر و حرکت دوربین مشخص تعریف شود.",
                )
        }
        lines += "- از عدد، منبع یا آماری که در ورودی نیست استفاده نکن؛ مطلب نامطمئن را همین‌طور نگو."
        lines += "- مستقیم وارد محتوا شو؛ مقدمه‌چینی و جمله انگیزشی ننویس."
        return PromptSection(SectionKind.CONSTRAINTS, "محدودیت‌ها و نگه‌داشت‌ها", lines.joinToString("\n"))
    }

    private fun examples(kb: DomainKnowledge?, spec: PromptSpec, outputType: OutputType, taskCategory: TaskCategory): PromptSection {
        val example = selectExample(kb, spec, outputType, taskCategory)
        val body = if (example != null) {
            "سبک و سطح جزئیات مطلوب شبیه این نمونهٔ کامل است (فقط سبک، نه محتوا):\n«${example.promptFa.trim()}»"
        } else {
            "پیش از تولید، یک نمونهٔ کوچک از ساختار خروجی در ذهن بساز و خروجی نهایی را هم‌سبک آن بنویس."
        }
        return PromptSection(SectionKind.EXAMPLES, "مثال‌ها", body)
    }

    private fun quality(kb: DomainKnowledge?, spec: PromptSpec, outputType: OutputType, taskCategory: TaskCategory): PromptSection {
        val criteria = mutableListOf(
            "۱) انطباق کامل با قالب خروجی.",
            "۲) اتصال هر ادعا و عدد به اطلاعات قطعی و ورودی‌ها.",
            "۳) بدون حاشیه، تکرار و کلی‌گویی.",
            "۴) یکدستی زبان، لحن و ارقام فارسی در کل پاسخ.",
        )
        kb?.failureModes?.firstOrNull()?.let { mode ->
            // The KB fix is already phrased as an instruction — use it directly.
            criteria += "${fa(criteria.size + 1)}) ${mode.fixFa}"
        }
        if (taskCategory == TaskCategory.DESIGN || outputType == OutputType.DESIGN_BRIEF) {
            criteria += "${fa(criteria.size + 1)}) خوانایی متن در اندازهٔ کوچک و اولویت‌بندی صحیح اطلاعات روی طرح."
        }
        val body = "پیش از ارسال، پاسخ را بی‌صدا با این معیارها بسنج و در صورت نقض، اصلاحش کن:\n" +
            criteria.joinToString("\n")
        return PromptSection(SectionKind.QUALITY, "معیارهای کیفیت", body)
    }

    private fun clarification(spec: PromptSpec): PromptSection {
        val max = if (spec.detailLevel == DetailLevel.QUICK) 2 else 3
        val body = "اگر اطلاعات ضروری برای شروع ناقص است، پیش از پاسخ حداکثر ${fa(max)} سؤال کوتاه و مشخص بپرس، " +
            "پاسخ کاربر را بگیر و بعد ادامه بده؛ به‌جای سؤال، حدس نزن."
        return PromptSection(SectionKind.CLARIFICATION, "قاعده شفاف‌سازی", body)
    }

    // --- variables --------------------------------------------------------------

    private data class Variable(val token: String, val descriptionFa: String)

    /** Question ids whose answer is already covered by an extracted fact. */
    private val questionFactMap = mapOf(
        "product-service" to listOf(FactCategory.SUBJECT, FactCategory.EVENT),
        "content-topic" to listOf(FactCategory.SUBJECT, FactCategory.EVENT),
        "platform" to listOf(FactCategory.PLATFORM),
        "audience" to listOf(FactCategory.AUDIENCE),
        "brand-tone" to emptyList(),
        "price" to listOf(FactCategory.PRICE),
        "instructor" to listOf(FactCategory.INSTRUCTOR),
        "date" to listOf(FactCategory.DATE),
        "duration" to listOf(FactCategory.DURATION),
    )

    private fun variablesFor(spec: PromptSpec, facts: IdeaFacts, idea: String): List<Variable> {
        val variables = mutableListOf<Variable>()
        spec.answers.filter { it.skipped }.forEach { answer ->
            // Routing questions are resolved by the engine when skipped —
            // they must never become placeholders.
            if (answer.questionId == "deliverable-type" || answer.questionId == "output-type") {
                return@forEach
            }
            val covered = questionFactMap[answer.questionId]
                ?.any { facts.has(it) } == true
            if (!covered) {
                variables += Variable(variableToken(answer.questionId), answer.questionFa)
            }
        }
        Regex("\\[([A-Za-z0-9_ ]{2,30})\\]").findAll(idea).forEach { match ->
            val token = match.groupValues[1].trim().uppercase().replace(' ', '_')
            if (variables.none { it.token == token }) {
                variables += Variable(token, "مقدار را کاربر تعیین می‌کند")
            }
        }
        return variables
    }

    // --- knowledge-base selection helpers --------------------------------------

    private fun selectPersona(
        spec: PromptSpec,
        kb: DomainKnowledge?,
        outputType: OutputType,
        taskCategory: TaskCategory,
    ): KbPersona? {
        val personas = kb?.personas ?: return null
        if (personas.isEmpty()) return null
        val designTask = taskCategory == TaskCategory.DESIGN ||
            outputType == OutputType.DESIGN_BRIEF ||
            outputType == OutputType.IMAGE_PROMPT
        val text = scoringTokens(spec.idea + " " + spec.answers.joinToString(" ") { it.value ?: "" })
        if (text.isEmpty()) return if (designTask) designFallback(personas) else personas.first()
        val scored = personas.map { persona ->
            val personaTokens = tokenize(
                persona.titleFa + " " + persona.expertiseFa,
            )
            var score = personaTokens.count { it in text }
            if (designTask && personaTokens.any { it in DESIGN_PERSONA_TOKENS }) score += DESIGN_BOOST
            persona to score
        }
        val best = scored.maxByOrNull { it.second } ?: return personas.first()
        return if (best.second > 0) best.first else if (designTask) designFallback(personas) else null
    }

    /** Prefer a design persona when the task is visual and no token matched. */
    private fun designFallback(personas: List<KbPersona>): KbPersona? =
        personas.firstOrNull { persona ->
            tokenize(persona.titleFa + " " + persona.expertiseFa).any { it in DESIGN_PERSONA_TOKENS }
        }

    private fun selectStructure(spec: PromptSpec, kb: DomainKnowledge?): com.promptsaz.app.domain.model.KbOutputStructure? {
        val structures = kb?.outputStructures ?: return null
        if (structures.isEmpty()) return null
        val ideaText = spec.idea + " " + spec.answers.joinToString(" ") { it.value ?: "" }
        val normalizedIdea = normalizeForMatch(ideaText)
        // Gated structures (keywordsFa) only join the pool on explicit request.
        val pool = structures.filter { structure ->
            structure.keywordsFa.isEmpty() ||
                structure.keywordsFa.any { keyword -> normalizedIdea.contains(normalizeForMatch(keyword)) }
        }
        if (pool.isEmpty()) return null
        val text = scoringTokens(ideaText)
        if (text.isEmpty()) return null
        val scored = pool.map { structure ->
            // A title match is a much stronger signal than a body match.
            val titleTokens = tokenize(structure.titleFa)
            val bodyTokens = tokenize(structure.descriptionFa + " " + structure.templateFa)
            structure to (titleTokens.count { it in text } * TITLE_WEIGHT +
                bodyTokens.count { it in text })
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second > 0) best.first else null
    }

    /** Normalizes Persian text for keyword matching (letter variants + ZWNJ). */
    private fun normalizeForMatch(text: String): String = text
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("\u200c", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun selectExample(
        kb: DomainKnowledge?,
        spec: PromptSpec,
        outputType: OutputType,
        taskCategory: TaskCategory,
    ): KbExample? {
        val examples = kb?.examples ?: return null
        if (examples.isEmpty()) return null
        val ideaTokens = scoringTokens(spec.idea)
        // An example of the wrong medium (video example for a text task, design
        // example for a video task) must never be offered as a model.
        val eligible = examples.filter { example -> matchesTaskMedium(example, taskCategory) }
        if (eligible.isEmpty()) return null
        val scored = eligible.map { example ->
            val titleTokens = tokenize(example.titleFa)
            val bodyTokens = tokenize(example.promptFa)
            var score = titleTokens.count { it in ideaTokens } * TITLE_WEIGHT +
                bodyTokens.count { it in ideaTokens }
            if (taskCategory == TaskCategory.DESIGN &&
                (titleTokens + bodyTokens).any { it in DESIGN_PERSONA_TOKENS }
            ) {
                score += DESIGN_BOOST
            }
            // Examples written for this exact model — or for any model — get a small boost.
            if (example.targetAi == "any" || example.targetAi == spec.targetAi.id) score += 1
            example to score
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second >= MIN_EXAMPLE_RELEVANCE) best.first else null
    }

    /** True when the example's medium (text / design / video) fits the task. */
    private fun matchesTaskMedium(example: KbExample, taskCategory: TaskCategory): Boolean {
        val tokens = tokenize(example.titleFa) + tokenize(example.promptFa)
        val isDesignExample = tokens.any { it in DESIGN_PERSONA_TOKENS }
        val isVideoExample = VIDEO_EXAMPLE_TOKENS.any { marker -> tokens.any { marker in it } }
        return !(
            (isDesignExample && taskCategory != TaskCategory.DESIGN) ||
                (isVideoExample && taskCategory != TaskCategory.VIDEO)
            )
    }

    private fun relevantTerms(idea: String, kb: DomainKnowledge, max: Int): List<KbTerm> {
        val text = tokenize(idea)
        val matched = kb.terminology.filter { term ->
            tokenize(term.termFa + " " + term.termEn).any { it in text }
        }
        return (matched + kb.terminology).distinctBy { it.termEn }.take(max)
    }

    private fun languageRule(spec: PromptSpec): String = when (spec.outputLanguage) {
        OutputLanguage.PERSIAN -> "فارسی روان"
        OutputLanguage.ENGLISH -> "انگلیسی"
        OutputLanguage.SAME_AS_INPUT -> "همان زبان درخواست کاربر"
    }

    private fun ratioFor(facts: IdeaFacts): String = when {
        facts.valueOf(FactCategory.PLATFORM)?.contains("اینستاگرام") == true -> "۴:۵ (یا ۱:۱) برای پست اینستاگرام"
        facts.valueOf(FactCategory.PLATFORM)?.contains("استوری") == true -> "۹:۱۶"
        else -> "۴:۵ برای پست؛ ۹:۱۶ برای استوری"
    }

    private fun deliverableFa(outputType: OutputType, taskCategory: TaskCategory, facts: IdeaFacts): String {
        val target = facts.valueOf(FactCategory.EVENT) ?: facts.valueOf(FactCategory.SUBJECT) ?: ""
        val suffix = if (target.isNotBlank()) " برای $target" else ""
        return when {
            outputType == OutputType.DESIGN_BRIEF ||
                (outputType == OutputType.ALL && taskCategory == TaskCategory.DESIGN) ->
                "بریف کامل طراحی${suffix}"
            outputType == OutputType.IMAGE_PROMPT -> "پرامپت آمادهٔ ساخت تصویر${suffix}"
            outputType == OutputType.ALL -> "بستهٔ کامل (متن + بریف طراحی + پرامپت تصویر)${suffix}"
            taskCategory == TaskCategory.CALENDAR -> "تقویم محتوایی کامل${suffix}"
            else -> "متن ساخت‌یافتهٔ کامل${suffix}"
        }
    }

    /** Tokens used for relevance scoring — platform names removed per user rule. */
    private fun scoringTokens(text: String): Set<String> =
        tokenize(text).filterNot { it in OutputRouter.platformTokens }.toSet()

    private fun tokenize(text: String): Set<String> =
        text.replace("ي", "ی").replace("ك", "ک")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 }
            .map { it.lowercase() }
            .toSet()

    private fun fa(number: Int): String = number.toPersianDigits()

    private fun isImageOrVideo(spec: PromptSpec, outputType: OutputType): Boolean =
        spec.targetAi.isImageOrVideoModel

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
        const val DESIGN_BOOST = 5
        const val MIN_EXAMPLE_RELEVANCE = 2
        const val TITLE_WEIGHT = 3
        val TONE_QUESTION_IDS = setOf("brand-tone", "tone")
        val LENGTH_QUESTION_IDS = setOf("length-limit", "video-length", "length")
        val DESIGN_PERSONA_TOKENS = setOf(
            "پوستر", "طراح", "طراحی", "گرافیک", "هنری", "بصری", "بنر", "لوگو", "کاور", "هویت",
        )
        val VIDEO_EXAMPLE_TOKENS = listOf("ویدیو", "ریلز", "شورتز", "تیزر", "صحنه", "شات", "دوربین")
    }
}
