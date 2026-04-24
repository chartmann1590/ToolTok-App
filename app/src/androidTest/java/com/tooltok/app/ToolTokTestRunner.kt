package com.tooltok.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class ToolTokTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        AdRuntimeConfig.overrideAdsEnabled = false
        return super.newApplication(cl, ToolTokApplication::class.java.name, context)
    }
}
