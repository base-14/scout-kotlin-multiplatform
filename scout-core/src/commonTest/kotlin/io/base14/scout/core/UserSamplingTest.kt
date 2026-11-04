package io.base14.scout.core

import io.base14.scout.core.bridge.BridgeCodec
import io.base14.scout.core.bridge.SessionContext
import io.base14.scout.core.platform.InMemoryKeyValueStore
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.session.SamplingSource
import io.base14.scout.core.session.SessionManager
import io.base14.scout.core.session.UserSampling
import io.base14.scout.core.session.shouldRotateOnIdentityChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserSamplingTest {
    private var now = 1_700_000_000_000L
    private val clock = { now }

    private fun config(rate: Double = 100.0, userSampling: Boolean? = null) =
        ScoutConfig(
            serviceName = "svc",
            endpoint = "http://localhost:4318",
            sessionSampleRate = rate,
            sessionTimeoutMinutes = 30,
            maxSessionDurationMinutes = 60,
            userSampling = userSampling,
        )

    private fun manager(store: InMemoryKeyValueStore = InMemoryKeyValueStore(), cfg: ScoutConfig = config()) =
        SessionManager(cfg, store, clock)

    // ── §3 decision table ────────────────────────────────────────────────

    @Test
    fun userTrueKeepsEvenAtZeroRate() {
        val sm = manager(cfg = config(rate = 0.0, userSampling = true))
        assertTrue(sm.sampled())
        assertEquals(SamplingSource.USER, sm.samplingSource())
    }

    @Test
    fun userFalseDropsEvenAtFullRate() {
        val sm = manager(cfg = config(rate = 100.0, userSampling = false))
        assertFalse(sm.sampled())
        assertEquals(SamplingSource.USER, sm.samplingSource())
    }

    @Test
    fun nullFallsBackToRate() {
        assertTrue(manager(cfg = config(rate = 100.0)).sampled())
        assertFalse(manager(cfg = config(rate = 0.0)).sampled())
        assertEquals(SamplingSource.FALLBACK, manager().samplingSource())
    }

    @Test
    fun userFalseStillAllowsLogsAndMetricsButFallbackFalseDoesNot() {
        assertTrue(manager(cfg = config(userSampling = false)).allowsLogsAndMetrics())
        assertFalse(manager(cfg = config(rate = 0.0)).allowsLogsAndMetrics())
        assertTrue(manager(cfg = config(rate = 100.0)).allowsLogsAndMetrics())
    }

    // ── precedence and persistence ───────────────────────────────────────

    @Test
    fun initValueIsPersistedAndWinsOverStored() {
        val store = InMemoryKeyValueStore()
        store.putString(UserSampling.KEY, "false")
        val us = UserSampling(store, initValue = true)
        assertEquals(true, us.pending)
        assertEquals("true", store.getString(UserSampling.KEY))
    }

    @Test
    fun nullInitKeepsStoredValue() {
        val store = InMemoryKeyValueStore()
        store.putString(UserSampling.KEY, "false")
        assertEquals(false, UserSampling(store, initValue = null).pending)
    }

    @Test
    fun setNullClearsStoredValue() {
        val store = InMemoryKeyValueStore()
        val us = UserSampling(store, initValue = true)
        us.set(null)
        assertNull(us.pending)
        assertNull(store.getString(UserSampling.KEY))
    }

    @Test
    fun storedValueDecidesNextLaunchWithoutInitValue() {
        val store = InMemoryKeyValueStore()
        manager(store).userSampling.set(false)
        now += 31 * 60_000L // session expired: the next launch starts a new one
        val next = manager(store)
        assertFalse(next.sampled())
        assertEquals(SamplingSource.USER, next.samplingSource())
    }

    // ── never mid-session ────────────────────────────────────────────────

    @Test
    fun runtimeChangeDoesNotTouchCurrentSessionButAppliesAfterRotation() {
        val sm = manager()
        val first = sm.sessionId()
        sm.userSampling.set(false)
        assertEquals(first, sm.sessionId())
        assertTrue(sm.sampled())
        assertEquals(SamplingSource.FALLBACK, sm.samplingSource())

        sm.rotate()
        assertNotEquals(first, sm.sessionId())
        assertFalse(sm.sampled())
        assertEquals(SamplingSource.USER, sm.samplingSource())
    }

    @Test
    fun maxDurationRotationPicksUpNewValue() {
        val sm = manager()
        val first = sm.sessionId()
        sm.userSampling.set(false)
        now += 61 * 60_000L
        assertNotEquals(first, sm.sessionId())
        assertFalse(sm.sampled())
    }

    @Test
    fun idleRotationPicksUpNewValue() {
        val sm = manager()
        val first = sm.sessionId()
        sm.onBackground()
        sm.userSampling.set(false)
        now += 31 * 60_000L
        sm.onForeground()
        assertNotEquals(first, sm.sessionId())
        assertFalse(sm.sampled())
    }

    @Test
    fun resumedSessionKeepsItsDecisionAndSource() {
        val store = InMemoryKeyValueStore()
        val first = manager(store, config(userSampling = false))
        val id = first.sessionId()
        now += 60_000L
        // Relaunch with a different init value: the resumed session must not change.
        val resumed = manager(store, config(userSampling = true))
        assertEquals(id, resumed.sessionId())
        assertFalse(resumed.sampled())
        assertEquals(SamplingSource.USER, resumed.samplingSource())
    }

    @Test
    fun legacyRecordWithoutSourceDecodesAsFallback() {
        val store = InMemoryKeyValueStore()
        store.putString(
            "scout.session",
            """{"id":"legacy","startedAt":$now,"lastActive":$now,"sampled":true,"startIso":"x"}""",
        )
        val sm = manager(store)
        assertEquals("legacy", sm.sessionId())
        assertEquals(SamplingSource.FALLBACK, sm.samplingSource())
    }

    @Test
    fun adoptExternalCarriesSource() {
        val sm = manager()
        sm.adoptExternal("ext", sampled = false, startIso = "x", source = SamplingSource.USER)
        assertEquals(SamplingSource.USER, sm.samplingSource())
        assertTrue(sm.allowsLogsAndMetrics())
    }

    // ── §5 user-switch rotation matrix ───────────────────────────────────

    @Test
    fun anonymousToFirstIdNeverRotates() {
        assertFalse(shouldRotateOnIdentityChange(null, "a", SamplingSource.USER, true))
    }

    @Test
    fun sameIdNeverRotates() {
        assertFalse(shouldRotateOnIdentityChange("a", "a", SamplingSource.USER, false))
    }

    @Test
    fun switchRotatesOnlyWhenInPlay() {
        assertTrue(shouldRotateOnIdentityChange("a", "b", SamplingSource.USER, null))
        assertTrue(shouldRotateOnIdentityChange("a", "b", SamplingSource.FALLBACK, false))
        assertFalse(shouldRotateOnIdentityChange("a", "b", SamplingSource.FALLBACK, null))
    }

    @Test
    fun logoutRotatesOnlyWhenCurrentSessionWasUserDecided() {
        assertTrue(shouldRotateOnIdentityChange("a", null, SamplingSource.USER, null))
        assertFalse(shouldRotateOnIdentityChange("a", null, SamplingSource.FALLBACK, null))
    }

    // ── bridge wire ──────────────────────────────────────────────────────

    @Test
    fun sessionContextRoundTripsSamplingSource() {
        val ctx = SessionContext("s", "t", "1.0", false, "anon", samplingSource = "user")
        val decoded = BridgeCodec.decodeContext(BridgeCodec.encodeContext(ctx))!!
        assertEquals("user", decoded.samplingSource)
        assertEquals("user", decoded.toAttributes()[ScoutAttributes.SESSION_SAMPLING_SOURCE])
    }

    @Test
    fun legacySessionContextHasNoSource() {
        val legacy =
            """{"session.id":"s","session.start_time":"t","session.sample_rate":"1.0","session.sampled":true,"user.anonymous_id":"a"}"""
        val decoded = BridgeCodec.decodeContext(legacy)!!
        assertNull(decoded.samplingSource)
        assertFalse(decoded.toAttributes().containsKey(ScoutAttributes.SESSION_SAMPLING_SOURCE))
    }
}
