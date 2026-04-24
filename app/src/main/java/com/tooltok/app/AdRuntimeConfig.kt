package com.tooltok.app

object AdRuntimeConfig {
    @Volatile
    var overrideAdsEnabled: Boolean? = null

    fun adsEnabled(): Boolean = overrideAdsEnabled ?: BuildConfig.ADS_ENABLED
}
