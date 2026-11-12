package io.base14.scout.core

import io.base14.scout.core.bridge.BridgeCodec
import io.base14.scout.core.bridge.SessionContext
import io.base14.scout.core.platform.InMemoryKeyValueStore
import io.base14.scout.core.session.SamplingSource
import io.base14.scout.core.session.SessionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeEnableTest {
    private var now = 1_700_000_000_000L
    private val clock = { now }

    private fun config(enabled: Boolean = true, rate: Double = 100.0, userSampling: Boolean? = null) =
        ScoutConfig(
            serviceName = "svc",
            endpoint = "http://localhost:4318",
            sessionSampleRate = rate,
            sessionTimeoutMinutes = 30,
            maxSessionDurationMinutes = 60,
            userSampling = userSampling,
            enabled = enabled,
        )

    private fun manager(store: InMemoryKeyValueStore = InMemoryKeyValueStore(), cfg: ScoutConfig = config()) =
        SessionManager(cfg, store, clock)

    // ── init disabled ────────────────────────────────────────────────────

    @Test
    fun enabledDefaultsToTrue() {
        assertTrue(ScoutConfig(serviceName = "svc", endpoint = "http://x").enabled)
    }

    @Test
    fun initDisabledMintsNoSessionAndPersistsNothing() {
        val store = InMemoryKeyValueStore()
        val sm = manager(store, config(enabled = false))
        assertFalse(sm.isEnabled)
        assertEquals("", sm.sessionId())
        assertNull(store.getString(SESSION_KEY))
    }

    @Test
    fun initDisabledDoesNotResumeOrExtendPersistedSession() {
        val store = InMemoryKeyValueStore()
        val first = manager(store).sessionId()
        val persisted = store.getString(SESSION_KEY)
        now += 5 * 60_000L
        val sm = manager(store, config(enabled = false))
        assertEquals(first, sm.sessionId(), "the getter reports the last (inactive) id")
        assertEquals(persisted, store.getString(SESSION_KEY), "lastActive must not be bumped")
    }

    @Test
    fun lifecycleAndRotationWhileDisabledTouchNothing() {
        val store = InMemoryKeyValueStore()
        manager(store).sessionId()
        val persisted = store.getString(SESSION_KEY)
        val sm = manager(store, config(enabled = false))
        val id = sm.sessionId()
        sm.onBackground()
        now += 31 * 60_000L
        sm.onForeground()
        sm.touch()
        sm.rotate()
        now += 61 * 60_000L // past max duration: still no rotation while disabled
        assertEquals(id, sm.sessionId())
        sm.adoptExternal("ext", sampled = true, startIso = "x")
        assertEquals(id, sm.sessionId())
        assertEquals(persisted, store.getString(SESSION_KEY))
    }

    // ── transitions ──────────────────────────────────────────────────────

    @Test
    fun disableEndsTheSessionSoNoLaterLaunchResumesIt() {
        val store = InMemoryKeyValueStore()
        val sm = manager(store)
        val id = sm.sessionId()
        assertTrue(sm.disable())
        now += 60_000L // well within the idle timeout
        val next = manager(store)
        assertNotEquals(id, next.sessionId())
        assertEquals(id, next.previousId())
    }

    @Test
    fun enableStartsAFreshSessionLinkedToThePreviousOne() {
        val store = InMemoryKeyValueStore()
        val sm = manager(store)
        val id = sm.sessionId()
        sm.disable()
        assertTrue(sm.enable())
        assertTrue(sm.isEnabled)
        assertNotEquals(id, sm.sessionId())
        assertEquals(id, sm.previousId())
    }

    @Test
    fun enableAfterInitDisabledStartsFreshEvenIfPersistedIsResumable() {
        val store = InMemoryKeyValueStore()
        val persisted = manager(store).sessionId()
        val sm = manager(store, config(enabled = false))
        sm.enable()
        assertNotEquals(persisted, sm.sessionId())
        assertEquals(persisted, sm.previousId())
    }

    @Test
    fun enableResolvesSamplingLikeANewSession() {
        // A pending user value decides the fresh session...
        val sm = manager(cfg = config(enabled = false, rate = 100.0))
        sm.userSampling.set(false)
        sm.enable()
        assertFalse(sm.sampled())
        assertEquals(SamplingSource.USER, sm.samplingSource())

        // ...and with none, the sessionSampleRate roll does.
        val fallback = manager(cfg = config(enabled = false, rate = 0.0))
        fallback.enable()
        assertFalse(fallback.sampled())
        assertEquals(SamplingSource.FALLBACK, fallback.samplingSource())
    }

    @Test
    fun repeatingTheCurrentValueIsANoOp() {
        val sm = manager()
        val id = sm.sessionId()
        var changes = 0
        sm.onSessionChanged = { changes++ }
        assertFalse(sm.enable())
        assertEquals(id, sm.sessionId())
        assertTrue(sm.disable())
        assertFalse(sm.disable())
        assertEquals(1, changes)
    }

    // ── pre-init hold ────────────────────────────────────────────────────

    @Test
    fun preInitCallWinsOverConfigAndIsClearedAfterUse() {
        val hold = PendingEnabled()
        assertTrue(hold.applyTo(config(enabled = true)).enabled, "nothing held: config value")
        hold.hold(false)
        assertFalse(hold.applyTo(config(enabled = true)).enabled)
        assertNull(hold.value)
        hold.hold(true)
        assertTrue(hold.applyTo(config(enabled = false)).enabled)
    }

    // ── bridge wire ──────────────────────────────────────────────────────

    @Test
    fun sessionContextCarriesEnabledAndLegacyMeansOn() {
        val ctx = SessionContext("s", "t", "1.0", true, "anon", enabled = false)
        val json = BridgeCodec.encodeContext(ctx)
        assertTrue(json.contains("\"scout.enabled\":false"))
        assertFalse(BridgeCodec.decodeContext(json)!!.enabled)
        val legacy =
            """{"session.id":"s","session.start_time":"t","session.sample_rate":"1.0","session.sampled":true,"user.anonymous_id":"a"}"""
        assertTrue(BridgeCodec.decodeContext(legacy)!!.enabled)
    }

    private companion object {
        const val SESSION_KEY = "scout.session"
    }
}
