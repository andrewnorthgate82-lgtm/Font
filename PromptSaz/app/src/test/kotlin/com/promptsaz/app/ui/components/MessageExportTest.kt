package com.promptsaz.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file-delivery contract of the گفتگو tab: when the user asks for a file,
 * the model must return the complete content in one fenced code block, and
 * these helpers turn that block into a real saved file (like the download
 * button in ChatGPT / Gemini).
 */
class MessageExportTest {

    @Test
    fun `exports the largest fenced block and strips the fences`() {
        val reply = """
            فایل نهایی آماده شد. چند خط توضیح کوتاه:

            ```markdown
            # پایگاه دانش چیستا
            ## دامنه: general
            محتوای کامل…
            ```

            اگر جای دیگری هم کد کوتاه بود:
            `+ inline code`
        """.trimIndent()

        val exported = MessageExport.exportContent(reply)

        assertTrue("must contain the file body", exported.contains("# پایگاه دانش چیستا"))
        assertTrue("must contain the domain", exported.contains("## دامنه: general"))
        assertFalse("fences must be stripped", exported.contains("```"))
        assertFalse("explanation must not leak in", exported.contains("توضیح کوتاه"))
    }

    @Test
    fun `prefers the longest block when the model uses several`() {
        val reply = """
            خلاصه:
            ```kotlin
            val a = 1
            ```
            و فایل اصلی:
            ```markdown
            خط یک
            خط دو
            خط سه
            ```
        """.trimIndent()

        val exported = MessageExport.exportContent(reply)

        assertTrue(exported.contains("خط سه"))
        assertFalse(exported.contains("val a = 1"))
    }

    @Test
    fun `an unclosed fence still exports everything that follows it`() {
        val reply = "بخش ۱ از ۲:\n```markdown\n# بخش اول\nمحتوا ادامه دارد"

        val exported = MessageExport.exportContent(reply)

        assertTrue(exported.contains("# بخش اول"))
        assertTrue(exported.contains("محتوا ادامه دارد"))
        assertFalse(exported.contains("```"))
    }

    @Test
    fun `falls back to the whole reply when there is no fence`() {
        val reply = "پاسخ سادهٔ گفتگویی بدون هیچ بلوک کدی."

        assertEquals(reply + "\n", MessageExport.exportContent(reply))
    }

    @Test
    fun `suggests the exact filename the model mentioned`() {
        // the real shape of the reply that motivated this feature
        val reply = "فایل نهایی `chista-knowledge-base-FINAL.md` آماده شد."

        assertEquals("chista-knowledge-base-FINAL.md", MessageExport.suggestedFileName(reply))
    }

    @Test
    fun `suggests a persian default when no filename is mentioned`() {
        assertEquals(
            MessageExport.DEFAULT_FILE_NAME,
            MessageExport.suggestedFileName("فقط یک پاسخ معمولی بدون نام فایل."),
        )
    }
}
