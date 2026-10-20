package com.font.studio

/** Android-independent project contract; engines and persistence are adapters, not UI code. */
enum class ContentKind { MEDIA_LITERACY, POSTER, INFOGRAPHIC, ROMANTIC_QUOTE, PRODUCT, OCCASION }
enum class Platform { INSTAGRAM, TELEGRAM, EITAA, RUBIKA }
data class Brief(val topic: String, val audience: String, val kind: ContentKind) {
    init { require(topic.isNotBlank()); require(audience.isNotBlank()) }
}
data class Source(val id: String, val title: String, val reference: String) {
    init { require(id.isNotBlank()); require(title.isNotBlank()); require(reference.isNotBlank()) }
}
data class Claim(val text: String, val sourceIds: Set<String>, val confirmedByUser: Boolean = false)
sealed interface Layer {
    val id: String
    data class Text(override val id: String, val text: String, val role: String) : Layer
    data class Image(override val id: String, val localAssetId: String, val generated: Boolean) : Layer
    /** Values come from supplied data, never a rendered diffusion image. */
    data class Chart(override val id: String, val labels: List<String>, val values: List<Double>, val sourceId: String) : Layer
}
data class Slide(val layers: List<Layer>)
data class Proposal(val id: String, val title: String, val rationale: String, val slides: List<Slide>)
data class Revision(val number: Int, val proposal: Proposal, val caption: String, val claims: List<Claim>)
data class Approval(val revision: Int, val reviewer: String)
data class Project(
    val id: String,
    val workspaceId: String,
    val brief: Brief,
    val sources: List<Source>,
    val revisions: List<Revision>,
    val approval: Approval? = null,
) {
    init {
        require(id.isNotBlank() && workspaceId.isNotBlank())
        require(revisions.isNotEmpty())
        require(revisions.map { it.number } == (1..revisions.size).toList())
    }
    val current: Revision get() = revisions.last()
    /** Any edit invalidates approval; old versions remain available for undo. */
    fun edit(proposal: Proposal, caption: String, claims: List<Claim>): Project =
        copy(revisions = revisions + Revision(current.number + 1, proposal, caption, claims), approval = null)
    fun approve(reviewer: String): Project {
        require(reviewer.isNotBlank())
        require(validationErrors().isEmpty()) { validationErrors().joinToString("; ") }
        return copy(approval = Approval(current.number, reviewer))
    }
    fun validationErrors(): List<String> = buildList {
        val p = current.proposal
        if (p.title.isBlank() || p.slides.isEmpty()) add("Empty proposal")
        val layers = p.slides.flatMap { it.layers }
        if (p.slides.any { it.layers.isEmpty() }) add("Empty slide")
        if (layers.any { it.id.isBlank() } || layers.map { it.id }.distinct().size != layers.size) add("Invalid layer IDs")
        if (layers.filterIsInstance<Layer.Text>().any { it.text.isBlank() }) add("Empty text")
        if (layers.filterIsInstance<Layer.Image>().any { it.localAssetId.isBlank() }) add("Missing image asset")
        val sourceIds = sources.map { it.id }.toSet()
        current.claims.forEach {
            if (it.text.isBlank()) add("Empty claim")
            if (it.sourceIds.any { id -> id !in sourceIds }) add("Unknown claim source")
            if (it.sourceIds.isEmpty() && !it.confirmedByUser) add("Claim needs source or explicit user confirmation")
        }
        layers.filterIsInstance<Layer.Chart>().forEach {
            if (it.labels.isEmpty() || it.labels.size != it.values.size || it.values.any { v -> !v.isFinite() }) add("Invalid chart data")
            if (it.sourceId !in sourceIds) add("Chart needs a known source")
        }
    }
}
data class PublishTicket(val projectId: String, val workspaceId: String, val revision: Int, val platform: Platform)
/** This creates an approved snapshot reference, not a live publisher or durable queue. */
fun Project.publishTicket(platform: Platform): PublishTicket {
    require(approval?.revision == current.number) { "Manual approval of the current revision is required" }
    require(validationErrors().isEmpty())
    return PublishTicket(id, workspaceId, current.number, platform)
}
