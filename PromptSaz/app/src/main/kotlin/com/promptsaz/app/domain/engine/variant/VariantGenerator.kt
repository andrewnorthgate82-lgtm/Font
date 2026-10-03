package com.promptsaz.app.domain.engine.variant

import com.promptsaz.app.domain.model.PromptSection
import com.promptsaz.app.domain.model.SectionKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives «فشرده» and «خلاقانه» variants from the same section IR —
 * deterministic and instant (no second generation pass).
 */
@Singleton
class VariantGenerator @Inject constructor() {

    fun concise(sections: List<PromptSection>): List<PromptSection> = sections
        .filter { it.kind !in DROPPED_FOR_CONCISE }
        .map { section ->
            when (section.kind) {
                SectionKind.CONSTRAINTS -> section.copy(
                    body = section.body.lines().take(4).joinToString("\n"),
                )
                SectionKind.PROCESS -> section.copy(
                    body = section.body.lines().take(3).joinToString("\n"),
                )
                else -> section
            }
        }

    fun creative(sections: List<PromptSection>): List<PromptSection> = sections.map { section ->
        when (section.kind) {
            SectionKind.CONSTRAINTS -> section.copy(
                body = section.body + "\n- یک زاویه غیرکلیشه‌ای و به‌یادماندنی پیدا کن؛ اما هرگز به قیمت واقع‌گرایی و دقت نه.",
            )
            SectionKind.QUALITY -> section.copy(
                body = section.body + "\n${section.body.lines().size}) تازگی: ایده‌ی اصلی از کلیشه‌های تکراری فاصله بگیرد.",
            )
            else -> section
        }
    }

    private companion object {
        val DROPPED_FOR_CONCISE = setOf(SectionKind.EXAMPLES, SectionKind.QUALITY)
    }
}
