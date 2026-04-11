package com.tooltok.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalUrlSpecParserTest {
    @Test
    fun `parses supported external schemes`() {
        val github = ExternalUrlSpecParser.parse("https://github.com/chartmann1590/ToolTok-App")
        val mailto = ExternalUrlSpecParser.parse("mailto:test@example.com")

        assertNotNull(github)
        assertEquals("https", github?.scheme)
        assertNotNull(mailto)
        assertEquals("mailto", mailto?.scheme)
    }

    @Test
    fun `rejects unsupported or invalid schemes`() {
        assertNull(ExternalUrlSpecParser.parse("ftp://example.com/file"))
        assertNull(ExternalUrlSpecParser.parse("not a url"))
        assertNull(ExternalUrlSpecParser.parse(null))
    }
}
