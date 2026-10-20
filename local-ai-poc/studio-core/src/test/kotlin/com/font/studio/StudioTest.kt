package com.font.studio

import org.junit.Assert.*
import org.junit.Test

class StudioTest {
    private fun proposal(id: String = "a") = Proposal(id, "عنوان $id", "زاویهٔ پیشنهادی $id", listOf(Slide(listOf(Layer.Text("title", "پیش از بازنشر، بررسی کن", "headline")))))
    private fun project() = Project("p", "team-1", Brief("اعتبار خبر", "نوجوانان", ContentKind.MEDIA_LITERACY), emptyList(), listOf(Revision(1, proposal(), "کپشن", emptyList())))
    @Test fun manualApprovalIsMandatory() {
        assertThrows(IllegalArgumentException::class.java) { project().publishTicket(Platform.TELEGRAM) }
        assertEquals(1, project().approve("editor").publishTicket(Platform.TELEGRAM).revision)
    }
    @Test fun editingInvalidatesApprovalAndKeepsHistory() {
        val original = project().approve("editor")
        val edited = original.edit(proposal(), "متن تازه", emptyList())
        assertNull(edited.approval)
        assertEquals(2, edited.revisions.size)
        assertEquals("کپشن", edited.revisions.first().caption)
        assertThrows(IllegalArgumentException::class.java) { edited.publishTicket(Platform.INSTAGRAM) }
    }
    @Test fun unsupportedClaimsBlockApproval() {
        val p = project().edit(proposal(), "", listOf(Claim("ادعای نیازمند بررسی", emptySet())))
        assertThrows(IllegalArgumentException::class.java) { p.approve("editor") }
        assertNotNull(p.edit(proposal(), "", listOf(Claim("بررسی‌شده توسط کاربر", emptySet(), true))).approve("editor").approval)
    }
    @Test fun chartsNeedDataAndKnownSource() {
        val bad = proposal().copy(slides = listOf(Slide(listOf(Layer.Chart("c", listOf("الف"), listOf(Double.NaN), "missing")))))
        assertTrue(project().edit(bad, "", emptyList()).validationErrors().size >= 2)
    }
    @Test fun suggestionsMustBeThreeDifferentConcepts() {
        assertEquals(3, validateSuggestions(listOf(proposal("a"), proposal("b"), proposal("c"))).size)
        assertThrows(IllegalArgumentException::class.java) { validateSuggestions(listOf(proposal(), proposal(), proposal())) }
    }
    @Test fun releasesTextBeforeLoadingImageAndReusesWarmEngine() {
        val events = mutableListOf<String>()
        val coordinator = EngineCoordinator { kind ->
            events += "load:$kind"
            object : LocalEngine {
                override val kind = kind
                override fun close() { events += "close:$kind" }
            }
        }
        coordinator.run(EngineKind.TEXT) { }
        coordinator.run(EngineKind.TEXT) { }
        coordinator.run(EngineKind.IMAGE) { }
        coordinator.close()
        assertEquals(listOf("load:TEXT", "close:TEXT", "load:IMAGE", "close:IMAGE"), events)
    }
    @Test fun failedReleasePreventsLoadingAnotherModel() {
        var loads = 0
        val coordinator = EngineCoordinator { kind ->
            loads++
            object : LocalEngine {
                override val kind = kind
                override fun close() { error("native release failed") }
            }
        }
        coordinator.run(EngineKind.TEXT) { }
        assertThrows(IllegalStateException::class.java) { coordinator.run(EngineKind.IMAGE) { } }
        assertThrows(IllegalStateException::class.java) { coordinator.run(EngineKind.IMAGE) { } }
        assertEquals(1, loads)
    }
    @Test fun nestedInferenceCannotUnloadAnEngineInUse() {
        val coordinator = EngineCoordinator { kind -> object : LocalEngine {
            override val kind = kind
            override fun close() {}
        } }
        assertThrows(IllegalStateException::class.java) {
            coordinator.run(EngineKind.TEXT) { coordinator.run(EngineKind.IMAGE) { } }
        }
    }
}
