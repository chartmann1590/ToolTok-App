package com.tooltok.app

import kotlin.random.Random
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialAdPolicyTest {
    @Test
    fun firstPageLoadNeverShowsAnAd() {
        val policy = InterstitialAdPolicy(random = FixedRandom(0))

        assertFalse(policy.shouldShowForRoute("https://tooltok.vercel.app/", 100_000L))
    }

    @Test
    fun routeChangesCanShowAfterCooldown() {
        val policy = InterstitialAdPolicy(random = FixedRandom(0))

        assertFalse(policy.shouldShowForRoute("https://tooltok.vercel.app/", 100_000L))
        assertTrue(policy.shouldShowForRoute("https://tooltok.vercel.app/about", 200_000L))
    }

    @Test
    fun authAndAdminRoutesNeverShowAds() {
        val policy = InterstitialAdPolicy(random = FixedRandom(0))

        assertFalse(policy.shouldShowForRoute("https://tooltok.vercel.app/login", 200_000L))
        assertFalse(policy.shouldShowForRoute("https://tooltok.vercel.app/admin", 400_000L))
    }

    @Test
    fun deepScrollCanTriggerAdWithinEligiblePage() {
        val policy = InterstitialAdPolicy(random = FixedRandom(0))

        policy.shouldShowForRoute("https://tooltok.vercel.app/", 100_000L)

        assertFalse(policy.shouldShowForScroll("https://tooltok.vercel.app/", 1_000, 190_000L))
        assertTrue(policy.shouldShowForScroll("https://tooltok.vercel.app/", 2_400, 190_000L))
    }

    @Test
    fun scrollRespectsCooldownAfterAd() {
        val policy = InterstitialAdPolicy(random = FixedRandom(0))

        policy.shouldShowForRoute("https://tooltok.vercel.app/", 100_000L)
        assertTrue(policy.shouldShowForScroll("https://tooltok.vercel.app/", 2_500, 200_000L))
        policy.onAdShown(200_000L)

        assertFalse(policy.shouldShowForScroll("https://tooltok.vercel.app/", 5_000, 230_000L))
        assertTrue(policy.shouldShowForScroll("https://tooltok.vercel.app/", 7_500, 320_000L))
    }

    @Test
    fun sessionTickCanTriggerAfterInitialPageIsKnown() {
        val policy = InterstitialAdPolicy(random = FixedRandom(0))

        assertFalse(policy.shouldShowForSessionTick("https://tooltok.vercel.app/", 100_000L))
        assertTrue(policy.shouldShowForSessionTick("https://tooltok.vercel.app/", 200_000L))
    }

    private class FixedRandom(private val value: Int) : Random() {
        override fun nextBits(bitCount: Int): Int = value
    }
}
