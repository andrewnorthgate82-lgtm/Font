package com.promptsaz.app.domain.engine.clarify

import com.promptsaz.app.domain.model.ClarifyChip
import com.promptsaz.app.domain.model.ClarifyingQuestion
import com.promptsaz.app.domain.model.DomainKnowledge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks 2-4 clarifying questions before generation.
 *
 * A question is skipped when the user's idea already contains one of its
 * gap keywords (the gap is considered answered). The rest are returned sorted
 * by priority; at most [MAX_QUESTIONS] are asked.
 */
@Singleton
class ClarificationEngine @Inject constructor() {

    fun questionsFor(kb: DomainKnowledge?, idea: String): List<ClarifyingQuestion> {
        val source = kb?.clarifyingQuestions ?: return emptyList()
        if (idea.isBlank()) return emptyList()
        val normalizedIdea = normalize(idea)
        return source
            .filter { question ->
                question.gapKeywordsFa.none { keyword -> normalizedIdea.contains(normalize(keyword)) }
            }
            .sortedWith(compareBy({ it.priority }, { it.id }))
            .take(MAX_QUESTIONS)
            .map { question ->
                ClarifyingQuestion(
                    id = question.id,
                    questionFa = question.questionFa,
                    chips = question.chips.map { ClarifyChip(it.id, it.labelFa) },
                    allowFreeText = question.allowFreeText,
                    priority = question.priority,
                )
            }
    }

    private fun normalize(text: String): String = text
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("\u200c", " ")
        .lowercase()
        .filter { it.isLetterOrDigit() || it.isWhitespace() }

    private companion object {
        const val MAX_QUESTIONS = 4
    }
}
