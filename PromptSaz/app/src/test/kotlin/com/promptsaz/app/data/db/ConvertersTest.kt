package com.promptsaz.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `string list round trips through json`() {
        val tags = listOf("فروش", "ریلز", "campaign")
        val json = converters.listToJson(tags)
        assertEquals(tags, converters.jsonToList(json))
    }

    @Test
    fun `empty list serializes to empty json array`() {
        assertEquals("[]", converters.listToJson(emptyList()))
        assertEquals(emptyList<String>(), converters.jsonToList("[]"))
    }

    @Test
    fun `null and blank degrade to empty list`() {
        assertEquals(emptyList<String>(), converters.jsonToList(null))
        assertEquals(emptyList<String>(), converters.jsonToList(""))
        assertEquals(emptyList<String>(), converters.jsonToList("   "))
    }

    @Test
    fun `malformed json degrades to empty list instead of crashing`() {
        assertEquals(emptyList<String>(), converters.jsonToList("{not valid json"))
    }

    @Test
    fun `null input serializes to empty array`() {
        assertEquals("[]", converters.listToJson(null))
    }
}
