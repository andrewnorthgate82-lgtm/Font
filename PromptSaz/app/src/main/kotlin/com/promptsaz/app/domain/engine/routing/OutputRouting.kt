package com.promptsaz.app.domain.engine.routing

import com.promptsaz.app.domain.model.ClarifyingAnswer
import com.promptsaz.app.domain.model.PromptSpec

/**
 * Routes an idea to the right DELIVERABLE (what the prompt asks for) and
 * TASK CATEGORY (which constraints/process apply).
 *
 * Rules (per user feedback):
 *  - Design words (پوستر، بنر، لوگو، کاور، طراحی، تصویر…) route to design
 *    work; platform names (اینستاگرام، تلگرام…) NEVER change the deliverable.
 *  - A content calendar is ONLY produced when the user explicitly asks for
 *    a calendar or publishing schedule.
 */
enum class OutputType {
    /** Ordinary structured text (copy, article, code, plan…). */
    TEXT,

    /** A ready-to-paste prompt for image-generation models. */
    IMAGE_PROMPT,

    /** A designer brief (بریف برای طراح گرافیک). */
    DESIGN_BRIEF,

    /** Text + designer brief + image prompt combined. */
    ALL,
}

enum class TaskCategory { TEXT, DESIGN, CALENDAR, VIDEO }

object OutputRouter {

    /** Domain ids whose very nature fixes the deliverable. */
    const val IMAGE_DOMAIN_ID = "ai_image"
    const val VIDEO_DOMAIN_ID = "ai_video"

    /** Answer chip labels of the «خروجی چه باشد؟» question (KB: deliverable-type). */
    private val answerToType = mapOf(
        "متن و ساختار" to OutputType.TEXT,
        "پرامپت برای ساخت تصویر" to OutputType.IMAGE_PROMPT,
        "بریف برای طراح" to OutputType.DESIGN_BRIEF,
        "همه‌ی موارد" to OutputType.ALL,
    )

    private val designWords =
        listOf("پوستر", "بنر", "لوگو", "آرم", "کاور", "طراحی", "طرح بصری", "تصویرسازی", "اینفوگرافیک")
    private val imageModelWords =
        listOf("میدجرنی", "midjourney", "دالی", "dall-e", "پرامپت تصویر", "پرامپت ساخت تصویر", "تصویر با هوش مصنوعی")
    private val calendarWords =
        listOf("تقویم", "برنامه انتشار", "برنامه‌ی انتشار", "برنامه‌ریزی محتوا", "برنامه ریزی محتوا")
    private val videoWords = listOf(
        "ویدیو", "ریلز", "شورتز", "تیزر", "ویدیویی", "سناریو", "اسکریپت ویدیو",
        "سورا", "sora", "رانوی", "runway", "کلینگ", "kling",
    )

    /** Platform/social tokens that must not influence persona/structure selection. */
    val platformTokens = setOf(
        "اینستاگرام", "تلگرام", "واتساپ", "لینکدین", "یوتیوب", "توییتر", "ایکس", "فیسبوک",
        "آپارات", "وبسایت", "سایت", "وبلاگ", "گوگل", "ایمیل", "پیج", "کانال",
    )

    /**
     * Resolves the deliverable: explicit user answer wins; otherwise keyword
     * default (image-model mention → image prompt; design words → designer
     * brief; anything else → text).
     */
    fun outputType(spec: PromptSpec): OutputType {
        val answer: ClarifyingAnswer? = spec.answers.firstOrNull { it.questionId == "deliverable-type" && !it.skipped }
        answer?.value?.let { value ->
            answerToType.entries.firstOrNull { value.contains(it.key) }?.let { return it.value }
        }
        // The image-generation domain is inherently image prompts.
        if (spec.domainId == IMAGE_DOMAIN_ID) return OutputType.IMAGE_PROMPT
        return defaultOutputType(spec.idea)
    }

    fun defaultOutputType(idea: String): OutputType {
        return when {
            matchesAny(idea, imageModelWords) -> OutputType.IMAGE_PROMPT
            matchesAny(idea, designWords) -> OutputType.DESIGN_BRIEF
            else -> OutputType.TEXT
        }
    }

    fun taskCategory(idea: String): TaskCategory {
        return when {
            matchesAny(idea, calendarWords) -> TaskCategory.CALENDAR
            matchesAny(idea, videoWords) -> TaskCategory.VIDEO
            matchesAny(idea, designWords) -> TaskCategory.DESIGN
            else -> TaskCategory.TEXT
        }
    }

    /**
     * Task category that respects the user's explicit deliverable answer:
     * «متن و ساختار» downgrades a design-worded idea back to a text task,
     * «بریف برای طراح» upgrades a text-worded idea to a design task.
     */
    fun taskCategory(spec: PromptSpec): TaskCategory {
        val ideaCategory = taskCategory(spec.idea)
        val answer: ClarifyingAnswer? =
            spec.answers.firstOrNull { it.questionId == "deliverable-type" && !it.skipped }
        val answerType = answer?.value?.let { value ->
            answerToType.entries.firstOrNull { value.contains(it.key) }?.value
        }
        return when {
            answerType == OutputType.TEXT && ideaCategory == TaskCategory.DESIGN -> TaskCategory.TEXT
            answerType == OutputType.DESIGN_BRIEF && ideaCategory == TaskCategory.TEXT -> TaskCategory.DESIGN
            // The video-generation domain makes every idea a video task.
            spec.domainId == VIDEO_DOMAIN_ID && ideaCategory == TaskCategory.TEXT -> TaskCategory.VIDEO
            else -> ideaCategory
        }
    }

    /**
     * Single words must match as whole tokens (so «اسکریپت» does not fire on
     * «جاوااسکریپت»); multi-word phrases match as substrings.
     */
    private fun matchesAny(idea: String, words: List<String>): Boolean {
        val normalized = idea.replace("ي", "ی").replace("ك", "ک")
        val tokens = normalized
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 }
            .map { it.lowercase() }
            .toSet()
        return words.any { word ->
            if (word.contains(' ')) {
                normalized.contains(word)
            } else {
                // Whole-token match with Persian-suffix tolerance (پوستری، ویدیویی…),
                // but never a suffix of another word (اسکریپت must not fire on جاوااسکریپت).
                tokens.any { it == word.lowercase() || it.startsWith(word.lowercase()) }
            }
        }
    }
}
