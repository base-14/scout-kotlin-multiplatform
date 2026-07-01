package io.base14.scout.core

import io.base14.scout.core.session.Sampler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SamplerTest {
    @Test
    fun fullRateAlwaysKeeps() = assertTrue(Sampler.decide("anything", 100.0))

    @Test
    fun zeroRateAlwaysDrops() = assertFalse(Sampler.decide("anything", 0.0))

    @Test
    fun deterministicForSameId() =
        assertEquals(Sampler.decide("abc", 37.0), Sampler.decide("abc", 37.0))

    @Test
    fun roughlyMatchesRate() {
        val n = 10_000
        var kept = 0
        for (i in 0 until n) if (Sampler.decide("session-$i", 10.0)) kept++
        assertTrue(kept in 700..1300, "expected ~1000 kept, got $kept")
    }
}
