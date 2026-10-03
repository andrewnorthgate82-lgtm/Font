package com.promptsaz.app.domain.engine.style

import com.promptsaz.app.domain.model.PromptSection
import com.promptsaz.app.domain.model.PromptSpec

/**
 * Image-model (Midjourney) adapter: comma-separated visual descriptors plus
 * parameters. Roles, process steps and clarification rules are genuinely
 * irrelevant here, so those parts are intentionally folded into the scene
 * description (allowed by the product spec).
 */
class ImageModelStyleAdapter : TargetStyleAdapter {

    override fun render(sections: List<PromptSection>, spec: PromptSpec): String {
        val subject = spec.idea.trim().replace("\n", ", ").replace(Regex("\\s+"), " ")
        val style = if (subject.contains("محصول") || subject.contains("پکیج")) {
            "professional product photography, soft studio lighting, clean background"
        } else {
            "cinematic composition, natural lighting, cohesive color palette"
        }
        val descriptor = buildString {
            append("$subject, ")
            append(style)
            append(", sharp focus, high detail")
        }
        return descriptor.take(MAX_WORDS_CHUNK).trim() + " --ar 4:5 --v 6 --no text, watermark"
    }

    private companion object {
        const val MAX_WORDS_CHUNK = 420
    }
}

/**
 * Video-generator (Sora / Runway / Kling) adapter: shot-by-shot blocks with
 * duration, subject, camera movement, lighting and audio mood.
 */
class VideoModelStyleAdapter : TargetStyleAdapter {

    override fun render(sections: List<PromptSection>, spec: PromptSpec): String {
        val subject = spec.idea.trim().replace("\n", " ").replace(Regex("\\s+"), " ")
        return listOf(
            "ویدیوی کوتاه ۲۰ ثانیه‌ای درباره: $subject",
            "صحنه ۱ (۰ تا ۳ ثانیه): نمای نزدیک از سوژه با نور طبیعی؛ حرکت آرام دوربین به جلو.",
            "صحنه ۲ (۳ تا ۸ ثانیه): سوژه در حالت اصلی کار یا حرکت؛ دوربین دنبال‌کننده و نرم.",
            "صحنه ۳ (۸ تا ۱۴ ثانیه): نمای میانی از نتیجه یا واکنش؛ تغییر زاویه برای ریتم.",
            "صحنه ۴ (۱۴ تا ۲۰ ثانیه): قاب پایانی ثابت با پس‌زمینه ساده.",
            "پالت رنگی هماهنگ؛ نور یکنواخت؛ ریتم تند بدون مکث؛ صدای پس‌زمینه ملایم و هماهنگ با حال‌وهوا؛ بدون متن و واترمارک روی تصویر.",
        ).joinToString("\n")
    }
}
