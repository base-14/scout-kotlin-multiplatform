package io.base14.scout.core.bridge

import io.base14.scout.core.semantics.ScoutAttributes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionContext(
    @SerialName("session.id") val sessionId: String,
    @SerialName("session.start_time") val sessionStartTime: String,
    @SerialName("session.sample_rate") val sampleRate: String,
    @SerialName("session.sampled") val sampled: Boolean,
    @SerialName("user.anonymous_id") val anonymousId: String,
    @SerialName("user.id") val userId: String? = null,
    @SerialName("user.attributes") val userAttributes: Map<String, String> = emptyMap(),
    @SerialName("session.attributes") val sessionAttributes: Map<String, String> = emptyMap(),
) {
    fun toAttributes(): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        out[ScoutAttributes.SESSION_ID] = sessionId
        out[ScoutAttributes.SESSION_TYPE] = "user"
        out[ScoutAttributes.SESSION_START_TIME] = sessionStartTime
        out[ScoutAttributes.SESSION_SAMPLE_RATE] = sampleRate
        if (!sampled) out[ScoutAttributes.SESSION_SAMPLED] = "false"
        out[ScoutAttributes.USER_ANONYMOUS_ID] = anonymousId
        userId?.takeIf { it.isNotBlank() }?.let { out[ScoutAttributes.USER_ID] = it }
        for ((k, v) in userAttributes) out[k] = v
        for ((k, v) in sessionAttributes) out[k] = v
        return out
    }
}

@Serializable
data class ForwardedSpan(
    val scope: String,
    val name: String,
    val kind: String = "INTERNAL",
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("span_id") val spanId: String? = null,
    @SerialName("parent_span_id") val parentSpanId: String? = null,
    @SerialName("start_unix_nano") val startUnixNano: String,
    @SerialName("end_unix_nano") val endUnixNano: String,
    val attributes: Map<String, String> = emptyMap(),
    val status: ForwardedStatus = ForwardedStatus(),
)

@Serializable
data class ForwardedStatus(
    val code: String = "UNSET",
    val message: String? = null,
)

@Serializable
data class ForwardedSpanBatch(val spans: List<ForwardedSpan> = emptyList())

@Serializable
data class BridgeBreadcrumb(val type: String, val message: String, val time: String? = null)

@Serializable
data class BreadcrumbsBatch(val breadcrumbs: List<BridgeBreadcrumb> = emptyList())

@Serializable
data class ForwardedLog(
    val scope: String,
    @SerialName("severity_number") val severityNumber: Int = 9,
    @SerialName("severity_text") val severityText: String = "INFO",
    val body: String = "",
    @SerialName("timestamp_unix_nano") val timestampUnixNano: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

@Serializable
data class ForwardedLogBatch(val logs: List<ForwardedLog> = emptyList())

@Serializable
data class ForwardedMetric(
    val scope: String = "",
    val name: String,
    val value: Double = 0.0,
    val unit: String = "",
    @SerialName("timestamp_unix_nano") val timestampUnixNano: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

@Serializable
data class ForwardedMetricBatch(val metrics: List<ForwardedMetric> = emptyList())

@Serializable
data class OwnerRecord(
    val owner: String,
    val protocol: Int,
    val context: SessionContext? = null,
) {
    val isOwned: Boolean get() = owner != OWNER_NONE && context != null

    companion object {
        const val OWNER_NONE = "none"
        const val OWNER_NATIVE = "native"
        const val OWNER_FLUTTER = "flutter"
    }
}
