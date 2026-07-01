package io.base14.scout.core.export

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class MetricPoint(
    val name: String,
    val value: Double,
    val unit: String,
    val attributes: Map<String, Any>,
    val timeNanos: Long,
)

class ScoutMetricEmitter(
    endpoint: String,
    private val extraHeaders: Map<String, String>,
    private val httpClient: HttpClient,
    private val resourceAttrs: Map<String, String>,
    private val scopeName: String,
    private val scopeVersion: String,
) {
    private val url = endpoint.trimEnd('/') + "/v1/metrics"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun emit(points: List<MetricPoint>) {
        if (points.isEmpty()) return
        val body = serialize(points)
        scope.launch {
            runCatching {
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    extraHeaders.forEach { (k, v) -> header(k, v) }
                    setBody(body)
                }
            }
        }
    }

    private fun serialize(points: List<MetricPoint>): String {
        val byMetric = points.groupBy { it.name to it.unit }
        val root = buildJsonObject {
            putJsonArray("resourceMetrics") {
                addJsonObject {
                    putJsonObject("resource") {
                        put("attributes", attrsToJson(resourceAttrs.mapValues { it.value as Any }))
                    }
                    putJsonArray("scopeMetrics") {
                        addJsonObject {
                            putJsonObject("scope") {
                                put("name", scopeName)
                                put("version", scopeVersion)
                            }
                            putJsonArray("metrics") {
                                for ((key, pts) in byMetric) {
                                    addJsonObject {
                                        put("name", key.first)
                                        put("unit", key.second)
                                        putJsonObject("gauge") {
                                            putJsonArray("dataPoints") {
                                                for (p in pts) {
                                                    addJsonObject {
                                                        put("asDouble", p.value)
                                                        put("timeUnixNano", p.timeNanos.toString())
                                                        put("attributes", attrsToJson(p.attributes))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return root.toString()
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
