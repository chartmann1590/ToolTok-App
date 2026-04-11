package com.tooltok.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUrlPolicyTest {
    @Test
    fun `tooltok host stays internal`() {
        assertTrue(AppUrlPolicy.isInternal("https://tooltok.vercel.app/"))
        assertTrue(AppUrlPolicy.isInternal("https://tooltok.vercel.app/video/abc"))
    }

    @Test
    fun `other hosts open externally`() {
        assertFalse(AppUrlPolicy.isInternal("https://github.com/chartmann1590/ToolTok-App"))
        assertTrue(AppUrlPolicy.shouldOpenExternally("https://github.com/chartmann1590/ToolTok-App"))
        assertTrue(AppUrlPolicy.shouldOpenExternally("mailto:test@example.com"))
    }

    @Test
    fun `invalid inputs are rejected`() {
        assertFalse(AppUrlPolicy.isInternal(null))
        assertFalse(AppUrlPolicy.isInternal("not a url"))
        assertFalse(AppUrlPolicy.shouldOpenExternally("ftp://example.com/file"))
    }
}
