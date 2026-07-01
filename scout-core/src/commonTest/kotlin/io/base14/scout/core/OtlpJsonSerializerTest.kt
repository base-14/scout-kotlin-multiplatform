package io.base14.scout.core

import io.base14.scout.core.export.OtlpJsonSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class OtlpJsonSerializerTest {

    @Test
    fun serializeRawProducesValidOtlpJson() {
        val json = OtlpJsonSerializer.serializeRaw(
            resourceAttrs = mapOf("service.name" to "demo", "os.name" to "Android"),
            scopeName = "base14.scout.android",
            scopeVersion = "0.1.3",
            traceId = "5b8efff798038103d269b633813fc60c",
            spanId = "eee19b7ec3c1b174",
            name = "screen_view",
            attributes = mapOf("session.id" to "s1", "screen.name" to "Home"),
            startNanos = 1000L,
            endNanos = 2000L,
            statusMessage = "resurrected",
        )

        Json.parseToJsonElement(json)

        assertTrue(json.contains("\"resourceSpans\""), json)
        assertTrue(json.contains("\"scopeSpans\""), json)
        assertTrue(json.contains("\"name\":\"base14.scout.android\""), json)
        assertTrue(json.contains("\"version\":\"0.1.3\""), json)
        assertTrue(json.contains("\"traceId\":\"5b8efff798038103d269b633813fc60c\""), json)
        assertTrue(json.contains("\"spanId\":\"eee19b7ec3c1b174\""), json)
        assertTrue(json.contains("\"name\":\"screen_view\""), json)
        assertTrue(json.contains("\"startTimeUnixNano\":\"1000\""), json)
        assertTrue(json.contains("\"endTimeUnixNano\":\"2000\""), json)
        assertTrue(json.contains("\"key\":\"session.id\""), json)
        assertTrue(json.contains("\"stringValue\":\"Home\""), json)
        assertTrue(json.contains("\"code\":2"), json)
    }
}
