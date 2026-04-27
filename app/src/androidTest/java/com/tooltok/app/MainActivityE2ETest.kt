package com.tooltok.app

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityE2ETest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun launchShowsWebShell() {
        activityRule.scenario.onActivity { activity ->
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.swipeRefreshLayout).visibility)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.webView).visibility)
        }
    }

    @Test
    fun launchAssignsBaseUrlToWebView() {
        waitForBaseUrl(activityRule.scenario)
    }

    @Test
    fun bannerContainerMatchesResolvedAdConfiguration() {
        activityRule.scenario.onActivity { activity ->
            val bannerView = activity.findViewById<View>(R.id.bannerAdContainer)
            assertNotNull(bannerView)
            assertEquals(View.GONE, bannerView.visibility)
        }
    }

    @Test
    fun bannerInsetKeepsWebContentAboveAd() {
        activityRule.scenario.onActivity { activity ->
            activity.updateBannerInsetForTesting(heightPx = 96, visible = true)
            assertEquals(96, activity.currentWebViewBottomInset())
            activity.updateBannerInsetForTesting(heightPx = 0, visible = false)
            assertEquals(0, activity.currentWebViewBottomInset())
        }
    }

    @Test
    fun scrolledWebViewBlocksSwipeRefresh() {
        activityRule.scenario.onActivity { activity ->
            val swipeRefresh = activity.findViewById<View>(R.id.swipeRefreshLayout) as androidx.swiperefreshlayout.widget.SwipeRefreshLayout
            assertEquals(false, swipeRefresh.isEnabled)
        }
    }

    @Test
    fun rotationKeepsWebViewAlive() {
        activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        waitForBaseUrl(activityRule.scenario)
        activityRule.scenario.onActivity { activity ->
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.webView).visibility)
        }
    }

    private fun waitForBaseUrl(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 10_000L
    ) {
        val expected = AppConfig.BASE_URL.removeSuffix("/")
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var lastUrl: String? = null

        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                lastUrl = activity.findViewById<WebView>(R.id.webView).url
            }

            if (lastUrl?.removeSuffix("/") == expected) {
                return
            }

            SystemClock.sleep(250)
        }

        fail("Expected WebView to load $expected but was $lastUrl")
    }

}
