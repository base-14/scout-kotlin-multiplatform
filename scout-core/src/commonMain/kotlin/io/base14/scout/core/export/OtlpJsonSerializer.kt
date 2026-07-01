package io.base14.scout.core.export

import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object OtlpJsonSerializer {

    fun serialize(spans: List<SpanData>): String {
        if (spans.isEmpty()) return "{\"resourceSpans\":[]}"
        val resource = spans.first().resource
        val byScope = spans.groupBy { it.instrumentationScopeInfo.name to it.instrumentationScopeInfo.version }

        val root = buildJsonObject {
            putJsonArray("resourceSpans") {
                addJsonObject {
                    putJsonObject("resource") {
                        put("attributes", attrsToJson(resource.attributes))
                    }
                    putJsonArray("scopeSpans") {
                        for ((scope, scopedSpans) in byScope) {
                            addJsonObject {
                                putJsonObject("scope") {
                                    put("name", scope.first)
                                    scope.second?.let { put("version", it) }
                                }
                                putJsonArray("spans") {
                                    for (s in scopedSpans) add(spanToJson(s))
                                }
                            }
                        }
                    }
                }
            }
        }
        return root.toString()
    }

    fun serializeRaw(
        resourceAttrs: Map<String, String>,
        scopeName: String,
        scopeVersion: String,
        traceId: String,
        spanId: String,
        name: String,
        attributes: Map<String, String>,
        startNanos: Long,
        endNanos: Long,
        statusMessage: String,
    ): String = buildJsonObject {
        putJsonArray("resourceSpans") {
            addJsonObject {
                putJsonObject("resource") { put("attributes", stringAttrsToJson(resourceAttrs)) }
                putJsonArray("scopeSpans") {
                    addJsonObject {
                        putJsonObject("scope") {
                            put("name", scopeName)
                            put("version", scopeVersion)
                        }
                        putJsonArray("spans") {
                            addJsonObject {
                                put("traceId", traceId)
                                put("spanId", spanId)
                                put("name", name)
                                put("kind", 1)
                                put("startTimeUnixNano", startNanos.toString())
                                put("endTimeUnixNano", endNanos.toString())
                                put("attributes", stringAttrsToJson(attributes))
                                putJsonObject("status") {
                                    put("code", 2)
                                    put("message", statusMessage)
                                }
                            }
                        }
                    }
                }
            }
        }
    }.toString()

    private fun stringAttrsToJson(attrs: Map<String, String>): JsonArray = buildJsonArray {
        for ((k, v) in attrs) {
            addJsonObject {
                put("key", k)
                putJsonObject("value") { put("stringValue", v) }
            }
        }
    }

    private fun spanToJson(s: SpanData): JsonObject = buildJsonObject {
        put("traceId", s.spanContext.traceId)
        put("spanId", s.spanContext.spanId)
        if (s.parent.isValid) put("parentSpanId", s.parent.spanId)
        put("name", s.name)
        put("kind", kindCode(s.spanKind))
        put("startTimeUnixNano", s.startTimestamp.toString())
        put("endTimeUnixNano", (s.endTimestamp ?: s.startTimestamp).toString())
        put("attributes", attrsToJson(s.attributes))
        putJsonObject("status") {
            put("code", statusCode(s.status.statusCode))
            s.status.description?.let { put("message", it) }
        }
        if (s.events.isNotEmpty()) {
            putJsonArray("events") {
                for (e in s.events) {
                    addJsonObject {
                        put("timeUnixNano", e.timestamp.toString())
                        put("name", e.name)
                        put("attributes", attrsToJson(e.attributes))
                    }
                }
            }
        }
    }

    private fun attrsToJson(attrs: Map<String, Any>): JsonArray = buildJsonArray {
        for ((k, v) in attrs) {
            addJsonObject {
                put("key", k)
                putJsonObject("value") { putAnyValue(v) }
            }
        }
    }

    private fun JsonObjectBuilder.putAnyValue(v: Any) {
        when (v) {
            is String -> put("stringValue", v)
            is Boolean -> put("boolValue", v)
            is Int -> put("intValue", v.toString())
            is Long -> put("intValue", v.toString())
            is Double -> put("doubleValue", v)
            is Float -> put("doubleValue", v.toDouble())
            else -> put("stringValue", v.toString())
        }
    }

    private fun kindCode(k: SpanKind): Int = when (k) {
        SpanKind.INTERNAL -> 1
        SpanKind.SERVER -> 2
        SpanKind.CLIENT -> 3
        SpanKind.PRODUCER -> 4
        SpanKind.CONSUMER -> 5
    }

    private fun statusCode(c: StatusCode): Int = when (c) {
        StatusCode.UNSET -> 0
        StatusCode.OK -> 1
        StatusCode.ERROR -> 2
    }
}
