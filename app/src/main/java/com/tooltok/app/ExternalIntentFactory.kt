package com.tooltok.app

import android.content.Intent
import android.net.Uri

object ExternalIntentFactory {
    fun create(url: String): Intent {
        val spec = requireNotNull(ExternalUrlSpecParser.parse(url)) { "Unsupported external URL: $url" }
        val uri = Uri.parse(spec.rawUrl)
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }
}
