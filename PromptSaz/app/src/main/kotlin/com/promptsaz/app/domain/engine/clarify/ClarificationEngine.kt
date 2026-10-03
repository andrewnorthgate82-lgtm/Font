package com.promptsaz.app.domain.engine.clarify

import com.promptsaz.app.domain.engine.facts.FactCategory
import com.promptsaz.app.domain.engine.facts.IdeaFactsExtractor
import com.promptsaz.app.domain.engine.routing.OutputRouter
import com.promptsaz.app.domain.engine.routing.TaskCategory
import com.promptsaz.app.domain.model.ClarifyChip
import com.promptsaz.app.domain.model.ClarifyingQuestion
import com.promptsaz.app.domain.model.DomainKnowledge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks 2-5 clarifying questions before generation.
 *
 * A question is skipped when:
 *  - the user's idea already contains one of its gap keywords, OR
 *  - the answer is already covered by an extracted fact (name, date,
 *    duration, platform, subject…), OR
 *  - the question is task-scoped (appliesTo) and the idea is not that kind
 *    of task — poster questions (حضوری/آنلاین، ثبت‌نام، ساعت، سبک و رنگ)
 *    only appear for event/design work.
 * Nothing the user already said is ever asked again. The rest are returned
 * sorted by priority; at most [MAX_QUESTIONS] are asked.
 */
@Singleton
class ClarificationEngine @Inject constructor() {

    /** Question ids whose answer an extracted fact already provides. */
    private val questionFactMap = mapOf(
        "product-service" to listOf(FactCategory.SUBJECT, FactCategory.EVENT),
        "content-topic" to listOf(FactCategory.SUBJECT, FactCategory.EVENT),
        "platform" to listOf(FactCategory.PLATFORM),
        "audience" to listOf(FactCategory.AUDIENCE),
        "price" to listOf(FactCategory.PRICE),
        "instructor" to listOf(FactCategory.INSTRUCTOR),
        "date" to listOf(FactCategory.DATE),
        "duration" to listOf(FactCategory.DURATION),
        "event-mode" to listOf(FactCategory.MODE),
        "registration" to listOf(FactCategory.REGISTRATION),
        "event-time" to listOf(FactCategory.TIME),
        "location" to listOf(FactCategory.LOCATION),
    )

    fun questionsFor(kb: DomainKnowledge?, idea: String): List<ClarifyingQuestion> {
        val source = kb?.clarifyingQuestions ?: return emptyList()
        if (idea.isBlank()) return emptyList()
        val normalizedIdea = normalize(idea)
        val facts = IdeaFactsExtractor.extract(idea)
        val factCategories = facts.entries.map { it.category }.toSet()
        val taskCategory = OutputRouter.taskCategory(idea)
        val isEvent = FactCategory.EVENT in factCategories
        return source
            .filter { question -> questionApplies(question.appliesTo, taskCategory, isEvent) }
            .filter { question ->
                val keywordAnswered = question.gapKeywordsFa.any { keyword ->
                    normalizedIdea.contains(normalize(keyword))
                }
                val factAnswered = questionFactMap[question.id]
                    ?.any { it in factCategories } == true
                !keywordAnswered && !factAnswered
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

    /** A task-scoped question applies only when the idea is that kind of task. */
    private fun questionApplies(appliesTo: List<String>, task: TaskCategory, isEvent: Boolean): Boolean {
        if (appliesTo.isEmpty()) return true
        val taskName = task.name.lowercase()
        return appliesTo.any { scope ->
            scope == taskName || (scope == "event" && isEvent)
        }
    }

    private fun normalize(text: String): String = text
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("\u200c", " ")
        .lowercase()
        .filter { it.isLetterOrDigit() || it.isWhitespace() }

    private companion object {
        const val MAX_QUESTIONS = 5
    }
}
