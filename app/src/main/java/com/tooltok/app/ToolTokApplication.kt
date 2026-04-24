package com.tooltok.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

class ToolTokApplication : Application(), Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private companion object {
        const val TAG = "ToolTokAds"
        const val APP_OPEN_AD_MAX_AGE_MS = 4 * 60 * 60 * 1000L
    }

    private lateinit var appOpenAdManager: AppOpenAdManager
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super<Application>.onCreate()

        if (!BuildConfig.ADS_ENABLED) {
            Log.d(TAG, "Ads disabled in local.properties.")
            return
        }

        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        appOpenAdManager = AppOpenAdManager()

        MobileAds.initialize(this) {
            Log.d(TAG, "Mobile Ads initialized.")
            appOpenAdManager.loadAd(this)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        currentActivity?.let(appOpenAdManager::showAdIfAvailable)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED) return
        if (!appOpenAdManager.isShowingAd) {
            currentActivity = activity
        }
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }

    private inner class AppOpenAdManager {
        private var appOpenAd: AppOpenAd? = null
        private var isLoadingAd = false
        var isShowingAd = false
            private set
        private var loadTimeMs = 0L

        fun loadAd(context: Application) {
            if (!BuildConfig.ADS_ENABLED ||
                BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID.isBlank() ||
                isLoadingAd ||
                isAdAvailable()
            ) {
                return
            }

            isLoadingAd = true
            Log.d(TAG, "Loading app open ad.")

            AppOpenAd.load(
                context,
                BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        isLoadingAd = false
                        loadTimeMs = Date().time
                        Log.d(TAG, "App open ad loaded.")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        appOpenAd = null
                        isLoadingAd = false
                        Log.w(TAG, "App open ad failed to load: ${loadAdError.message}")
                    }
                }
            )
        }

        fun showAdIfAvailable(activity: Activity) {
            if (!BuildConfig.ADS_ENABLED || BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID.isBlank()) {
                return
            }

            val ad = appOpenAd
            if (isShowingAd) {
                Log.d(TAG, "App open ad already showing.")
                return
            }

            if (!isAdAvailable() || ad == null) {
                loadAd(activity.application)
                return
            }

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "App open ad shown.")
                }

                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isShowingAd = false
                    Log.d(TAG, "App open ad dismissed.")
                    loadAd(activity.application)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    appOpenAd = null
                    isShowingAd = false
                    Log.w(TAG, "App open ad failed to show: ${adError.message}")
                    loadAd(activity.application)
                }
            }

            isShowingAd = true
            ad.show(activity)
        }

        private fun isAdAvailable(): Boolean {
            val adAgeMs = Date().time - loadTimeMs
            return appOpenAd != null && adAgeMs < APP_OPEN_AD_MAX_AGE_MS
        }
    }
}
