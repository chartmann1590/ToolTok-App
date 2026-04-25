package com.tooltok.app

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        onView(withId(R.id.swipeRefreshLayout)).check(matches(isDisplayed()))
        onView(withId(R.id.webView)).check(matches(isDisplayed()))
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
        val tallHtml =
            """
            <!doctype html>
            <html>
              <body style="margin:0;background:#111;color:#fff;">
                <div style="height:3200px;padding:24px;">
                  <h1>ToolTok feed</h1>
                  <p>Scrollable content for refresh gating validation.</p>
                </div>
              </body>
            </html>
            """.trimIndent()

        activityRule.scenario.onActivity { activity ->
            activity.findViewById<WebView>(R.id.webView).loadDataWithBaseURL(
                AppConfig.BASE_URL,
                tallHtml,
                "text/html",
                "utf-8",
                null
            )
        }

        waitForScrollRange(activityRule.scenario)

        activityRule.scenario.onActivity { activity ->
            val webView = activity.findViewById<WebView>(R.id.webView)
            webView.scrollTo(0, 900)
        }

        waitForScrollOffset(activityRule.scenario, minimumScrollY = 400)

        activityRule.scenario.onActivity { activity ->
            assertTrue(activity.canWebViewScrollUp())
        }
    }

    @Test
    fun rotationKeepsWebViewAlive() {
        activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        waitForBaseUrl(activityRule.scenario)
        onView(withId(R.id.webView)).check(matches(isDisplayed()))
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

    private fun waitForScrollRange(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 10_000L
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var scrollRange = 0

        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                val webView = activity.findViewById<WebView>(R.id.webView)
                scrollRange = ((webView.contentHeight * webView.scale).toInt() - webView.height).coerceAtLeast(0)
            }

            if (scrollRange > 0) {
                return
            }

            SystemClock.sleep(250)
        }

        fail("Expected WebView to have vertical scroll range but got $scrollRange")
    }

    private fun waitForScrollOffset(
        scenario: ActivityScenario<MainActivity>,
        minimumScrollY: Int,
        timeoutMs: Long = 10_000L
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var scrollY = 0

        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                scrollY = activity.findViewById<WebView>(R.id.webView).scrollY
            }

            if (scrollY >= minimumScrollY) {
                return
            }

            SystemClock.sleep(250)
        }

        fail("Expected WebView scrollY >= $minimumScrollY but was $scrollY")
    }
}
