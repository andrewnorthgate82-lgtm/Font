package com.font.studio

enum class EngineKind { TEXT, IMAGE }
/** Adapters must release all native resources synchronously in close(). */
interface LocalEngine : AutoCloseable {
    val kind: EngineKind
}
/**
 * Serializes complete inference operations, not just model loading.
 * Switching engines closes the previous one before allocating the next one.
 * On operation failure, discard the engine rather than reuse uncertain native state.
 * This contract cannot prove OS memory reclamation: real-device PSS tests are still required.
 */
class EngineCoordinator(private val factory: (EngineKind) -> LocalEngine) : AutoCloseable {
    private var active: LocalEngine? = null
    private var poisoned = false
    private var inOperation = false
    @Synchronized fun <T> run(kind: EngineKind, operation: (LocalEngine) -> T): T {
        check(!poisoned) { "Release failed; restart the coordinator/process before allocating another engine" }
        check(!inOperation) { "Nested inference operations are forbidden" }
        inOperation = true
        try {
            if (active?.kind != kind) {
                release()
                val loaded = factory(kind)
                active = loaded
                check(loaded.kind == kind) { "Engine factory returned the wrong kind" }
            }
            return operation(checkNotNull(active))
        } catch (error: Throwable) {
            try { release() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        } finally { inOperation = false }
    }
    private fun release() {
        val previous = active ?: return
        active = null
        try { previous.close() } catch (error: Throwable) { poisoned = true; throw error }
    }
    @Synchronized override fun close() {
        check(!inOperation) { "Cannot release an engine during inference" }
        release()
    }
}

/** Real local text adapter must return three distinct validated concepts, not demo text. */
interface ProposalGenerator {
    fun propose(brief: Brief, sources: List<Source>): List<Proposal>
}
fun validateSuggestions(proposals: List<Proposal>): List<Proposal> {
    require(proposals.size == 3) { "Three alternatives are required" }
    require(proposals.map { it.id }.distinct().size == 3)
    require(proposals.map { it.title.trim() }.distinct().size == 3)
    require(proposals.all { it.id.isNotBlank() && it.title.isNotBlank() && it.rationale.isNotBlank() && it.slides.isNotEmpty() })
    return proposals.toList()
}
