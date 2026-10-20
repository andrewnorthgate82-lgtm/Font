package com.font.localaipoc

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONArray
import org.json.JSONObject

class StudioJsonTest {
    private fun response(ids: List<String> = listOf("a","b","c")) = JSONObject().put("proposals",JSONArray().apply {
        ids.forEach { id -> put(JSONObject().put("id",id).put("title","عنوان $id").put("rationale","زاویه $id").put("body","متن فارسی با نیم‌فاصله").put("caption","کپشن")) }
    }).toString()
    @Test fun parsesThreePersianSuggestions() {
        val result = StudioJson.parse(response())
        assertEquals(3,result.size)
        assertEquals("متن فارسی با نیم‌فاصله",(result[0].first.slides[0].layers[1] as com.font.studio.Layer.Text).text)
    }
    @Test fun permitsFencedJson() { assertEquals(3,StudioJson.parse("```json\n${response()}\n```").size) }
    @Test fun rejectsDuplicateIdeas() { assertThrows(IllegalArgumentException::class.java) { StudioJson.parse(response(listOf("a","a","a"))) } }
    @Test fun rejectsIncompleteSuggestions() { assertThrows(IllegalArgumentException::class.java) { StudioJson.parse(response(listOf("a","b"))) } }
    @Test fun rejectsUnstructuredOutput() { assertThrows(Exception::class.java) { StudioJson.parse("سه ایده پیشنهاد می‌کنم") } }
}
