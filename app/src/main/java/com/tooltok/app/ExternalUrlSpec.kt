package com.tooltok.app

import java.net.URI

data class ExternalUrlSpec(
    val rawUrl: String,
    val scheme: String
)

object ExternalUrlSpecParser {
    private val allowedSchemes = setOf("https", "http", "mailto", "tel", "sms", "intent")

    fun parse(url: String?): ExternalUrlSpec? {
        if (url.isNullOrBlank()) return null

        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null

        if (scheme !in allowedSchemes) return null

        return ExternalUrlSpec(rawUrl = uri.toString(), scheme = scheme)
    }
}
