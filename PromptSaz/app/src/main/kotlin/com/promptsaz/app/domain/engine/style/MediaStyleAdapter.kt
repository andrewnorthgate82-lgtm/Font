package com.promptsaz.app.domain.engine.style

import com.promptsaz.app.domain.engine.facts.IdeaFactsExtractor
import com.promptsaz.app.domain.model.PromptSection
import com.promptsaz.app.domain.model.PromptSpec

/**
 * Image-model (Midjourney) adapter: comma-separated visual descriptors plus
 * parameters. Roles, process steps and clarification rules are genuinely
 * irrelevant here, so those parts are intentionally folded into the scene
 * description (allowed by the product spec).
 *
 * Per user feedback the image is built WITHOUT any text (image models break
 * Persian letters); a separate «لایهٔ متن» block lists the Persian text the
 * user adds later in Canva/Photoshop, and the negative list forbids text,
 * watermarks, logos and garbled letters.
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
            append(", large clean empty areas for text, sharp focus, high detail")
        }
        val prompt = descriptor.take(MAX_WORDS_CHUNK).trim() +
            " --ar 4:5 --v 6 --no text, watermark, logo, letters"

        val textLayer = IdeaFactsExtractor.extract(spec.idea).entries
        return if (textLayer.isEmpty()) {
            prompt
        } else {
            prompt + "\n\nلایهٔ متن (فارسی — جدا از پرامپت، بعداً روی تصویر اضافه کن):\n" +
                textLayer.joinToString("\n") { fact -> "- ${fact.labelFa}: ${fact.valueFa}" }
        }
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
