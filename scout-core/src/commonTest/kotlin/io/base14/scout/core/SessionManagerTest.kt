package io.base14.scout.core

import io.base14.scout.core.platform.InMemoryKeyValueStore
import io.base14.scout.core.session.SessionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {
    private fun config(timeout: Int = 30, maxDuration: Int = 60, rate: Double = 100.0) =
        ScoutConfig(
            serviceName = "svc",
            endpoint = "http://localhost:4318",
            sessionTimeoutMinutes = timeout,
            maxSessionDurationMinutes = maxDuration,
            sessionSampleRate = rate,
        )

    @Test
    fun mintsNewSession() {
        val sm = SessionManager(config(), InMemoryKeyValueStore())
        assertTrue(sm.sessionId().isNotBlank())
        assertNull(sm.previousId())
        assertTrue(sm.sampled())
    }

    @Test
    fun resumesPersistedSessionWithinTimeout() {
        val store = InMemoryKeyValueStore()
        val first = SessionManager(config(), store).sessionId()
        val second = SessionManager(config(), store).sessionId()
        assertEquals(first, second)
    }

    @Test
    fun adoptExternalReplacesId() {
        val sm = SessionManager(config(), InMemoryKeyValueStore())
        sm.adoptExternal("external-1", sampled = true, startIso = "2026-01-01T00:00:00.000Z")
        assertEquals("external-1", sm.sessionId())
    }

    @Test
    fun startTimeStableAcrossReads() {
        val sm = SessionManager(config(), InMemoryKeyValueStore())
        assertEquals(sm.startTimeIso(), sm.startTimeIso())
    }
}
