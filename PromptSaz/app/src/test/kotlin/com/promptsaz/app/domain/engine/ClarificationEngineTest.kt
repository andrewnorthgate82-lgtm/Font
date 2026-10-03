package com.promptsaz.app.domain.engine

import com.promptsaz.app.domain.engine.clarify.ClarificationEngine
import com.promptsaz.app.domain.model.ClarifyingAnswer
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.domain.model.PromptSpec
import com.promptsaz.app.domain.model.TargetAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarificationEngineTest {

    private val engine = ClarificationEngine()

    private val kb = DomainKnowledge(
        id = "test",
        nameFa = "آزمون",
        status = "ready",
        personas = emptyList(),
        terminology = emptyList(),
        outputStructures = emptyList(),
        guardrails = emptyList(),
        failureModes = emptyList(),
        examples = emptyList(),
        clarifyingQuestions = listOf(
            com.promptsaz.app.domain.model.KbClarifyingQuestion(
                id = "audience", questionFa = "مخاطب کیست؟", priority = 1,
                chips = listOf(com.promptsaz.app.domain.model.KbChip("a", "همه")),
                gapKeywordsFa = listOf("مخاطب", "مشتری"),
            ),
            com.promptsaz.app.domain.model.KbClarifyingQuestion(
                id = "platform", questionFa = "کدام پلتفرم؟", priority = 2,
                chips = emptyList(), gapKeywordsFa = listOf("اینستاگرام"),
            ),
            com.promptsaz.app.domain.model.KbClarifyingQuestion(
                id = "tone", questionFa = "لحن؟", priority = 3,
                chips = emptyList(), gapKeywordsFa = emptyList(),
            ),
            com.promptsaz.app.domain.model.KbClarifyingQuestion(
                id = "goal", questionFa = "هدف؟", priority = 4,
                chips = emptyList(), gapKeywordsFa = emptyList(),
            ),
            com.promptsaz.app.domain.model.KbClarifyingQuestion(
                id = "budget", questionFa = "بودجه؟", priority = 5,
                chips = emptyList(), gapKeywordsFa = emptyList(),
            ),
            com.promptsaz.app.domain.model.KbClarifyingQuestion(
                id = "extra", questionFa = "اضافه؟", priority = 6,
                chips = emptyList(), gapKeywordsFa = emptyList(),
            ),
        ),
    )

    @Test
    fun `asks at most four questions sorted by priority`() {
        val questions = engine.questionsFor(kb, "یک ایده ساده")
        assertEquals(4, questions.size)
        assertEquals(listOf(1, 2, 3, 4), questions.map { it.priority })
    }

    @Test
    fun `gap keywords remove already-answered questions`() {
        val questions = engine.questionsFor(kb, "می‌خواهم برای مشتری‌هایم در اینستاگرام تبلیغ بدهم")
        assertTrue(questions.none { it.id == "audience" })
        assertTrue(questions.none { it.id == "platform" })
        assertTrue(questions.isNotEmpty())
    }

    @Test
    fun `blank idea asks nothing`() {
        assertTrue(engine.questionsFor(kb, "   ").isEmpty())
    }

    @Test
    fun `null kb asks nothing`() {
        assertTrue(engine.questionsFor(null, "یک ایده").isEmpty())
    }
}
