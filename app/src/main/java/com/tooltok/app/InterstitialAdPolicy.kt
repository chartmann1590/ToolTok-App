package com.tooltok.app

import java.net.URI
import kotlin.random.Random

/**
 * Keeps interstitial timing conservative:
 * - never on the first page load
 * - occasional on route changes
 * - occasional on deep scroll milestones in the feed
 * - never on admin/auth pages
 */
class InterstitialAdPolicy(
    private val random: Random = Random.Default,
    private val minMillisBetweenAds: Long = 90_000L,
    private val routeChancePercent: Int = 30,
    private val scrollChancePercent: Int = 35,
    private val sessionChancePercent: Int = 35,
    private val scrollMilestonePx: Int = 2_200,
) {
    private var lastPath: String? = null
    private var lastShownAtMs: Long = 0L
    private var lastScrollCheckpointPx: Int = 0

    fun shouldShowForRoute(url: String?, nowMs: Long): Boolean {
        val path = normalizedEligiblePath(url) ?: return false
        val previousPath = lastPath
        if (path == previousPath) return false

        lastPath = path
        lastScrollCheckpointPx = 0

        if (previousPath == null || !canShow(nowMs)) return false
        return random.nextInt(100) < routeChancePercent
    }

    fun shouldShowForScroll(url: String?, scrollY: Int, nowMs: Long): Boolean {
        val path = normalizedEligiblePath(url) ?: return false
        if (path != lastPath) {
            lastPath = path
            lastScrollCheckpointPx = 0
            return false
        }
        if (scrollY < scrollMilestonePx) return false
        if (scrollY - lastScrollCheckpointPx < scrollMilestonePx) return false

        lastScrollCheckpointPx = scrollY
        if (!canShow(nowMs)) return false
        return random.nextInt(100) < scrollChancePercent
    }

    fun shouldShowForSessionTick(url: String?, nowMs: Long): Boolean {
        val path = normalizedEligiblePath(url) ?: return false
        if (lastPath == null) {
            lastPath = path
            return false
        }
        if (!canShow(nowMs)) return false
        return random.nextInt(100) < sessionChancePercent
    }

    fun onAdShown(nowMs: Long) {
        lastShownAtMs = nowMs
        lastScrollCheckpointPx = 0
    }

    private fun canShow(nowMs: Long): Boolean {
        return nowMs - lastShownAtMs >= minMillisBetweenAds
    }

    private fun normalizedEligiblePath(url: String?): String? {
        val parsedUrl = try {
            URI(url ?: return null)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val host = parsedUrl.host ?: return null
        if (!host.equals(AppConfig.INTERNAL_HOST, ignoreCase = true)) return null

        val path = parsedUrl.path.orEmpty().ifBlank { "/" }
        return if (path == "/admin" || path.startsWith("/login") || path.startsWith("/signup") || path.startsWith("/auth")) {
            null
        } else {
            path
        }
    }
}
