package com.promptsaz.app.domain.engine.improve

import com.promptsaz.app.domain.engine.assembler.PromptAssembler
import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.PromptMode
import com.promptsaz.app.domain.model.PromptSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites a user's existing prompt: the original text is embedded as context
 * and the assembler rebuilds the full 10-part structure around the user's
 * intent — so the target AI gets the same request, but professionally framed.
 */
@Singleton
class PromptRewriter @Inject constructor(
    private val assembler: PromptAssembler,
) {

    fun rewrite(original: String, spec: PromptSpec, kb: DomainKnowledge?) =
        assembler.assemble(spec.copy(mode = PromptMode.IMPROVE, idea = original), kb)
}
