package com.promptsaz.app.domain.model

/**
 * Output language of the *generated prompt text* (not the app UI, which is always Persian).
 */
enum class OutputLanguage(val id: String, val labelFa: String) {
    PERSIAN(id = "fa", labelFa = "فارسی"),
    ENGLISH(id = "en", labelFa = "انگلیسی"),
    SAME_AS_INPUT(id = "auto", labelFa = "مثل ورودی"),
    ;

    companion object {
        fun fromId(value: String): OutputLanguage =
            entries.firstOrNull { it.id == value } ?: SAME_AS_INPUT
    }
}
