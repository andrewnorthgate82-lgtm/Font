package com.font.localaipoc

import com.font.studio.*
import org.json.JSONObject

/** Untrusted model text is parsed as data, never interpreted as code or actions. */
object StudioJson {
    fun parse(raw: String): List<Pair<Proposal, String>> {
        require(raw.length <= 100_000) { "Response too large" }
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = JSONObject(cleaned).getJSONArray("proposals")
        require(array.length() == 3)
        val decoded = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val id = o.getString("id")
            val title = o.getString("title")
            val body = o.getString("body")
            val caption = o.getString("caption")
            require(body.isNotBlank() && caption.isNotBlank())
            Proposal(id, title, o.getString("rationale"), listOf(Slide(listOf(Layer.Text("$id-title",title,"headline"),Layer.Text("$id-body",body,"body"))))) to caption
        }
        validateSuggestions(decoded.map { it.first })
        return decoded
    }
}
