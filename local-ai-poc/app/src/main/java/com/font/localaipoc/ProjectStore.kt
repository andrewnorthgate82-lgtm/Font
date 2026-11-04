package com.font.localaipoc

import com.font.studio.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

/** Versioned local storage. Approval is deliberately not restored from an imported file. */
object ProjectCodec {
    private fun <T> array(items: List<T>, convert: (T) -> Any) = JSONArray().apply { items.forEach { put(convert(it)) } }
    private fun texts(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    fun encode(p: Project): String = JSONObject().apply {
        put("schema",2); put("id",p.id); put("workspace",p.workspaceId)
        put("topic",p.brief.topic); put("audience",p.brief.audience); put("kind",p.brief.kind.name)
        put("sources",array(p.sources) { JSONObject().put("id",it.id).put("title",it.title).put("reference",it.reference) })
        put("revisions",array(p.revisions) { r -> JSONObject().apply {
            put("number",r.number); put("caption",r.caption)
            put("claims",array(r.claims) { JSONObject().put("text",it.text).put("sourceIds",JSONArray(it.sourceIds.toList())).put("confirmed",it.confirmedByUser) })
            put("proposal",JSONObject().apply {
                put("id",r.proposal.id); put("title",r.proposal.title); put("rationale",r.proposal.rationale)
                put("slides",array(r.proposal.slides) { s -> JSONObject().put("layers",array(s.layers) { l -> JSONObject().apply {
                    put("id",l.id)
                    when(l) {
                        is Layer.Text -> { put("type","text"); put("text",l.text); put("role",l.role) }
                        is Layer.Image -> { put("type","image"); put("asset",l.localAssetId); put("generated",l.generated) }
                        is Layer.Chart -> { put("type","chart"); put("labels",JSONArray(l.labels)); put("values",JSONArray(l.values)); put("source",l.sourceId) }
                    }
                } }) })
            })
        } })
    }.toString()
    private fun <T> read(a: JSONArray, transform: (JSONObject) -> T): List<T> = (0 until a.length()).map { transform(a.getJSONObject(it)) }
    fun decode(raw: String): Project {
        require(raw.length <= 8_000_000) { "Project too large" }
        val d = JSONObject(raw)
        val schema = d.getInt("schema")
        require(schema == 1 || schema == 2) { "Unsupported project version" }
        val brief = Brief(d.getString("topic"),d.getString("audience"),ContentKind.valueOf(d.getString("kind")))
        val revisions = read(d.getJSONArray("revisions")) { r ->
            if (schema == 1) {
                Revision(1,Proposal("restored",r.getString("title"),r.getString("rationale"),listOf(Slide(listOf(Layer.Text("title",r.getString("title"),"headline"),Layer.Text("body",r.getString("body"),"body"))))),r.getString("caption"),emptyList())
            } else {
                val p = r.getJSONObject("proposal")
                val slides = read(p.getJSONArray("slides")) { s -> Slide(read(s.getJSONArray("layers")) { l ->
                    val id = l.getString("id")
                    when(l.getString("type")) {
                        "text" -> Layer.Text(id,l.getString("text"),l.getString("role"))
                        "image" -> Layer.Image(id,l.getString("asset"),l.getBoolean("generated"))
                        "chart" -> Layer.Chart(id,texts(l.getJSONArray("labels")),l.getJSONArray("values").let { a -> (0 until a.length()).map { a.getDouble(it) } },l.getString("source"))
                        else -> error("Unsupported layer type")
                    }
                }) }
                val claims = read(r.getJSONArray("claims")) { c -> Claim(c.getString("text"),texts(c.getJSONArray("sourceIds")).toSet(),c.getBoolean("confirmed")) }
                Revision(r.getInt("number"),Proposal(p.getString("id"),p.getString("title"),p.getString("rationale"),slides),r.getString("caption"),claims)
            }
        }.let { if (schema == 1) it.mapIndexed { i,r -> r.copy(number=i+1) } else it }
        val sources = if (schema == 1) emptyList() else read(d.getJSONArray("sources")) { Source(it.getString("id"),it.getString("title"),it.getString("reference")) }
        return Project(d.getString("id"),if(schema==1) "personal" else d.getString("workspace"),brief,sources,revisions)
    }
}

data class ProjectList(val projects: List<Project>, val unreadableCount: Int)
class ProjectStore(root: File, private val workspace: String = "personal") {
    private val directory: File
    init {
        require(workspace.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        directory = File(root,workspace)
        check(directory.isDirectory || directory.mkdirs())
    }
    private fun file(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid project ID" }
        return File(directory,"$id.json")
    }
    fun load(id: String): Project = ProjectCodec.decode(file(id).readText()).also {
        require(it.id == id && it.workspaceId == workspace) { "Project identity mismatch" }
    }
    fun save(p: Project) {
        require(p.workspaceId == workspace)
        val target = file(p.id)
        val bytes = ProjectCodec.encode(p).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 8_000_000)
        val temporary = Files.createTempFile(directory.toPath(),"draft-",".tmp")
        try {
            java.io.FileOutputStream(temporary.toFile()).use { it.write(bytes); it.fd.sync() }
            try { Files.move(temporary,target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary,target.toPath(),StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }
    fun list(): ProjectList {
        var bad = 0
        val projects = directory.listFiles().orEmpty().filter { it.extension == "json" }.sortedByDescending { it.lastModified() }.mapNotNull {
            try { load(it.nameWithoutExtension) } catch (_: Exception) { bad++; null }
        }
        return ProjectList(projects,bad)
    }
    /** Idempotent, non-destructive migration. Never overwrite a project edited in the new version. */
    fun migrateLegacy(legacy: File) {
        if (!legacy.exists()) return
        val p = ProjectCodec.decode(legacy.readText())
        if (!file(p.id).exists()) save(p)
    }
}
