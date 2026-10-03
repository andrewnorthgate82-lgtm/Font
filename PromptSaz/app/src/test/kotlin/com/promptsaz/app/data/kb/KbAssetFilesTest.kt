package com.promptsaz.app.data.kb

import com.promptsaz.app.domain.model.DomainKnowledge
import com.promptsaz.app.domain.model.KbRegistry
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Parses the REAL knowledge-base assets shipped with the app, so a broken or
 * truncated JSON file fails the build instead of failing at runtime on a
 * user's phone.
 *
 * The test is skipped automatically when assets are not reachable from the
 * working directory (e.g. inside some CI sandboxes); tools/validate_kb.py
 * provides the authoritative standalone check.
 */
class KbAssetFilesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun assetFile(name: String): File? =
        listOf("src/main/assets/knowledge/$name", "app/src/main/assets/knowledge/$name")
            .map(::File)
            .firstOrNull { it.exists() }

    @Test
    fun `registry lists thirteen domains with four ready`() {
        val file = assetFile("_registry.json")
        assumeTrue("registry asset not reachable from working dir", file != null)
        val registry = json.decodeFromString<KbRegistry>(file!!.readText())
        assertEquals(1, registry.version)
        assertEquals(13, registry.domains.size)
        assertEquals(4, registry.domains.count { it.isReady })
        assertTrue(registry.domains.any { it.id == "general" })
        assertTrue(registry.domains.any { it.id == "marketing" })
        assertTrue(registry.domains.any { it.id == "social_media" })
        assertTrue(registry.domains.any { it.id == "programming" })
    }

    @Test
    fun `general knowledge base meets minimums`() {
        val kb = loadDomain("general.json") ?: return
        assertTrue(kb.personas.size >= 3)
        assertTrue(kb.terminology.size >= 5)
        assertTrue(kb.outputStructures.size >= 3)
        assertTrue(kb.guardrails.size >= 5)
        assertTrue(kb.failureModes.size >= 3)
        assertTrue(kb.examples.size >= 2)
        assertTrue(kb.clarifyingQuestions.size >= 4)
    }

    @Test
    fun `marketing knowledge base meets full minimums`() {
        val kb = loadDomain("marketing.json") ?: return
        assertFullDomain(kb, "marketing")
    }

    @Test
    fun `social media knowledge base meets full minimums`() {
        val kb = loadDomain("social_media.json") ?: return
        assertFullDomain(kb, "social_media")
    }

    @Test
    fun `programming knowledge base meets full minimums`() {
        val kb = loadDomain("programming.json") ?: return
        assertFullDomain(kb, "programming")
    }

    @Test
    fun `clarifying question priorities stay within the declared range`() {
        for (name in listOf("general.json", "marketing.json", "social_media.json", "programming.json")) {
            val kb = loadDomain(name) ?: return
            kb.clarifyingQuestions.forEach { question ->
                assertTrue(
                    "$name/${question.id}: priority ${question.priority} outside 1..9",
                    question.priority in 1..9,
                )
            }
        }
    }

    private fun loadDomain(fileName: String): DomainKnowledge? {
        val file = assetFile(fileName)
        assumeTrue("$fileName not reachable from working dir", file != null)
        return json.decodeFromString(file!!.readText())
    }

    private fun assertFullDomain(kb: DomainKnowledge, expectedId: String) {
        assertEquals(expectedId, kb.id)
        assertTrue("personas: ${kb.personas.size}", kb.personas.size >= 4)
        assertTrue("terminology: ${kb.terminology.size}", kb.terminology.size >= 10)
        assertTrue("outputStructures: ${kb.outputStructures.size}", kb.outputStructures.size >= 4)
        assertTrue("guardrails: ${kb.guardrails.size}", kb.guardrails.size >= 8)
        assertTrue("failureModes: ${kb.failureModes.size}", kb.failureModes.size >= 5)
        assertTrue("examples: ${kb.examples.size}", kb.examples.size >= 3)
        assertTrue("questions: ${kb.clarifyingQuestions.size}", kb.clarifyingQuestions.size >= 6)
        kb.examples.forEach { example ->
            assertTrue(
                "example ${example.id} too short (${example.promptFa.length})",
                example.promptFa.length >= 300,
            )
        }
    }
}
