package io.base14.scout.core.export

import io.opentelemetry.kotlin.logging.model.ReadableLogRecord
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

internal object OtlpJsonLogSerializer {

    fun serialize(records: List<ReadableLogRecord>): String {
        if (records.isEmpty()) return "{\"resourceLogs\":[]}"
        val resource = records.first().resource
        val byScope = records.groupBy { it.instrumentationScopeInfo.name to it.instrumentationScopeInfo.version }
        val root = buildJsonObject {
            putJsonArray("resourceLogs") {
                addJsonObject {
                    putJsonObject("resource") { put("attributes", attrsToJson(resource.attributes)) }
                    putJsonArray("scopeLogs") {
                        for ((scope, recs) in byScope) {
                            addJsonObject {
                                putJsonObject("scope") {
                                    put("name", scope.first)
                                    scope.second?.let { put("version", it) }
                                }
                                putJsonArray("logRecords") { for (r in recs) add(recordToJson(r)) }
                            }
                        }
                    }
                }
            }
        }
        return root.toString()
    }

    private fun recordToJson(r: ReadableLogRecord): JsonObject = buildJsonObject {
        (r.timestamp ?: r.observedTimestamp)?.let { put("timeUnixNano", it.toString()) }
        r.observedTimestamp?.let { put("observedTimeUnixNano", it.toString()) }
        r.severityNumber?.let { put("severityNumber", it.severityNumber) }
        r.severityText?.let { put("severityText", it) }
        r.body?.let { putJsonObject("body") { put("stringValue", it.toString()) } }
        put("attributes", attrsToJson(r.attributes))
        if (r.spanContext.isValid) {
            put("traceId", r.spanContext.traceId)
            put("spanId", r.spanContext.spanId)
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
}
