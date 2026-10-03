package com.promptsaz.app.domain.model

/**
 * How dense and long the generated prompt should be.
 */
enum class DetailLevel(val id: String, val labelFa: String, val descriptionFa: String) {
    QUICK(
        id = "quick",
        labelFa = "سریع",
        descriptionFa = "کوتاه و کاربردی؛ فقط بخش‌های ضروری",
    ),
    STANDARD(
        id = "standard",
        labelFa = "استاندارد",
        descriptionFa = "تعادل کامل بین جزئیات و طول",
    ),
    EXPERT(
        id = "expert",
        labelFa = "حرفه‌ای",
        descriptionFa = "بیشترین دقت با همه ۱۰ بخش و مثال",
    ),
    ;

    companion object {
        fun fromId(value: String): DetailLevel =
            entries.firstOrNull { it.id == value } ?: STANDARD
    }
}
