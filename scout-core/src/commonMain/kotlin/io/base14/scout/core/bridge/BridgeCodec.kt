package io.base14.scout.core.bridge

import kotlinx.serialization.json.Json

object BridgeCodec {
    const val PROTOCOL_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encodeSpans(spans: List<ForwardedSpan>): String =
        json.encodeToString(ForwardedSpanBatch.serializer(), ForwardedSpanBatch(spans))

    fun decodeSpans(payload: String): List<ForwardedSpan> =
        runCatching { json.decodeFromString(ForwardedSpanBatch.serializer(), payload).spans }
            .getOrDefault(emptyList())

    fun encodeContext(context: SessionContext): String =
        json.encodeToString(SessionContext.serializer(), context)

    fun decodeContext(payload: String): SessionContext? =
        runCatching { json.decodeFromString(SessionContext.serializer(), payload) }.getOrNull()

    fun encodeOwner(record: OwnerRecord): String =
        json.encodeToString(OwnerRecord.serializer(), record)

    fun decodeOwner(payload: String): OwnerRecord? =
        runCatching { json.decodeFromString(OwnerRecord.serializer(), payload) }.getOrNull()

    fun encodeBreadcrumbs(batch: BreadcrumbsBatch): String =
        json.encodeToString(BreadcrumbsBatch.serializer(), batch)

    fun decodeBreadcrumbs(payload: String): List<BridgeBreadcrumb> =
        runCatching { json.decodeFromString(BreadcrumbsBatch.serializer(), payload).breadcrumbs }
            .getOrDefault(emptyList())

    fun encodeLogs(logs: List<ForwardedLog>): String =
        json.encodeToString(ForwardedLogBatch.serializer(), ForwardedLogBatch(logs))

    fun decodeLogs(payload: String): List<ForwardedLog> =
        runCatching { json.decodeFromString(ForwardedLogBatch.serializer(), payload).logs }
            .getOrDefault(emptyList())
}
