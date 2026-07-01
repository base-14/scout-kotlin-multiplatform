package io.base14.scout.core

import io.base14.scout.core.bridge.BridgeCodec
import io.base14.scout.core.bridge.ForwardedSpan
import io.base14.scout.core.bridge.ForwardedStatus
import io.base14.scout.core.bridge.OwnerRecord
import io.base14.scout.core.bridge.SessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BridgeCodecTest {

    private fun fakeProducerBatch() = listOf(
        ForwardedSpan(
            scope = "base14.scout.flutter",
            name = "screen_view",
            startUnixNano = "1000",
            endUnixNano = "2000",
            attributes = mapOf("screen.name" to "Profile", "view.loading_type" to "initial_load"),
        ),
        ForwardedSpan(
            scope = "base14.scout.flutter",
            name = "http.request",
            kind = "CLIENT",
            traceId = "0af7651916cd43dd8448eb211c80319c",
            spanId = "b7ad6b7169203331",
            startUnixNano = "3000",
            endUnixNano = "4500",
            attributes = mapOf("http.request.method" to "GET", "url.full" to "https://api.example/x"),
            status = ForwardedStatus(code = "ERROR", message = "boom"),
        ),
    )

    @Test
    fun spanBatchRoundTrips() {
        val original = fakeProducerBatch()
        val decoded = BridgeCodec.decodeSpans(BridgeCodec.encodeSpans(original))
        assertEquals(original, decoded)
        assertTrue(decoded.none { it.attributes.containsKey("session.id") })
        assertEquals("base14.scout.flutter", decoded.first().scope)
        assertEquals("ERROR", decoded[1].status.code)
    }

    @Test
    fun sessionContextRoundTripsWithDottedKeys() {
        val ctx = SessionContext(
            sessionId = "s-123",
            sessionStartTime = "2026-06-29T10:00:00.000Z",
            sampleRate = "100.0",
            sampled = true,
            anonymousId = "anon-9",
            userId = "u-1",
            userAttributes = mapOf("user.email" to "a@b.com"),
            sessionAttributes = mapOf("tier" to "gold"),
        )
        val json = BridgeCodec.encodeContext(ctx)
        assertTrue(json.contains("\"session.id\""), json)
        assertTrue(json.contains("\"user.anonymous_id\""), json)
        assertEquals(ctx, BridgeCodec.decodeContext(json))
    }

    @Test
    fun contextFlattensToOwnerStampAttributes() {
        val attrs = SessionContext(
            sessionId = "s-1",
            sessionStartTime = "t",
            sampleRate = "100.0",
            sampled = false,
            anonymousId = "anon",
            userId = null,
            userAttributes = mapOf("user.plan" to "pro"),
        ).toAttributes()
        assertEquals("s-1", attrs["session.id"])
        assertEquals("user", attrs["session.type"])
        assertEquals("false", attrs["session.sampled"])
        assertEquals("pro", attrs["user.plan"])
        assertNull(attrs["user.id"])
    }

    @Test
    fun ownerRecordRoundTrips() {
        val rec = OwnerRecord(
            owner = OwnerRecord.OWNER_NATIVE,
            protocol = BridgeCodec.PROTOCOL_VERSION,
            context = SessionContext("s", "t", "1.0", true, "anon"),
        )
        val decoded = BridgeCodec.decodeOwner(BridgeCodec.encodeOwner(rec))
        assertEquals(rec, decoded)
        assertTrue(decoded!!.isOwned)
    }

    @Test
    fun malformedPayloadsDegradeInsteadOfThrowing() {
        assertEquals(emptyList(), BridgeCodec.decodeSpans("not json"))
        assertNull(BridgeCodec.decodeContext("{"))
        assertNull(BridgeCodec.decodeOwner("[]"))
    }

    @Test
    fun decodeToleratesUnknownFutureFields() {
        val withExtra = """{"spans":[{"scope":"s","name":"error","start_unix_nano":"1",""" +
            """"end_unix_nano":"2","attributes":{},"status":{"code":"OK"},"future_field":42}]}"""
        val decoded = BridgeCodec.decodeSpans(withExtra)
        assertEquals(1, decoded.size)
        assertEquals("error", decoded.first().name)
    }
}
