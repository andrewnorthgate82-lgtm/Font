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
    fun `registry lists thirteen domains and all of them are ready`() {
        val file = assetFile("_registry.json")
        assumeTrue("registry asset not reachable from working dir", file != null)
        val registry = json.decodeFromString<KbRegistry>(file!!.readText())
        assertEquals(1, registry.version)
        assertEquals(13, registry.domains.size)
        assertEquals(
            "every shipped domain must be ready — no coming_soon placeholders",
            13,
            registry.domains.count { it.isReady },
        )
        val expected = setOf(
            "general", "marketing", "social_media", "programming",
            "copywriting_sales", "education_teaching", "ai_image", "ai_video",
            "writing_translation", "research_analysis", "business_strategy",
            "data_analysis", "productivity",
        )
        assertEquals(expected, registry.domains.map { it.id }.toSet())
    }

    @Test
    fun `every ready domain ships a parseable kb file that meets minimums`() {
        val registryFile = assetFile("_registry.json")
        assumeTrue("registry asset not reachable from working dir", registryFile != null)
        val registry = json.decodeFromString<KbRegistry>(registryFile!!.readText())
        for (entry in registry.domains.filter { it.isReady }) {
            val kbFile = entry.kbFile
            assumeTrue("$kbFile not declared", kbFile != null)
            val file = assetFile(kbFile!!)
            assumeTrue("$kbFile not reachable from working dir", file != null)
            val kb = json.decodeFromString<DomainKnowledge>(file!!.readText())
            assertEquals(entry.id, kb.id)
            if (kb.id == "general") {
                assertGeneralDomain(kb)
            } else {
                assertFullDomain(kb, entry.id)
            }
        }
    }

    @Test
    fun `clarifying question priorities stay within the declared range`() {
        val registryFile = assetFile("_registry.json")
        assumeTrue("registry asset not reachable from working dir", registryFile != null)
        val registry = json.decodeFromString<KbRegistry>(registryFile!!.readText())
        for (entry in registry.domains.filter { it.isReady }) {
            val file = assetFile(entry.kbFile ?: continue) ?: return
            val kb = json.decodeFromString<DomainKnowledge>(file.readText())
            kb.clarifyingQuestions.forEach { question ->
                assertTrue(
                    "${entry.id}/${question.id}: priority ${question.priority} outside 1..9",
                    question.priority in 1..9,
                )
            }
        }
    }

    @Test
    fun `every full domain keeps at least one design persona and one image example`() {
        val registryFile = assetFile("_registry.json")
        assumeTrue("registry asset not reachable from working dir", registryFile != null)
        val registry = json.decodeFromString<KbRegistry>(registryFile!!.readText())
        for (entry in registry.domains.filter { it.isReady && it.id != "general" }) {
            val file = assetFile(entry.kbFile ?: continue) ?: return
            val kb = json.decodeFromString<DomainKnowledge>(file.readText())
            assertTrue(
                "${entry.id}: expected at least one design/image persona",
                kb.personas.any { persona ->
                    listOf("طراح", "تصویر", "بصری", "گرافیک", "عکاس").any { persona.titleFa.contains(it) }
                },
            )
            assertTrue(
                "${entry.id}: expected at least one midjourney image-prompt example",
                kb.examples.any { it.targetAi == "midjourney" },
            )
            assertTrue(
                "${entry.id}: expected at least one non-image example",
                kb.examples.any { it.targetAi != "midjourney" },
            )
        }
    }

    private fun assertGeneralDomain(kb: DomainKnowledge) {
        assertTrue(kb.personas.size >= 3)
        assertTrue(kb.terminology.size >= 5)
        assertTrue(kb.outputStructures.size >= 3)
        assertTrue(kb.guardrails.size >= 5)
        assertTrue(kb.failureModes.size >= 3)
        assertTrue(kb.examples.size >= 2)
        assertTrue(kb.clarifyingQuestions.size >= 4)
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
