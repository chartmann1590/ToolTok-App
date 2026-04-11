package com.tooltok.app

import java.net.URI

object AppUrlPolicy {
    fun isInternal(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false

        if (scheme != "https" && scheme != "http") return false

        return host == AppConfig.INTERNAL_HOST
    }

    fun shouldOpenExternally(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (isInternal(url)) return false

        return ExternalUrlSpecParser.parse(url) != null
    }
}
