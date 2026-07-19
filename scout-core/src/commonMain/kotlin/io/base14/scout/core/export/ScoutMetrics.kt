package io.base14.scout.core.export

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val maxExportBatchSize: Int = 512,
    private val maxQueueSize: Int = 2048,
    private val exportIntervalSeconds: Int = 30,
    private val maxRetries: Int = 0,
    private val debug: Boolean = false,
) {
    private val url = endpoint.trimEnd('/') + "/v1/metrics"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val mutex = Mutex()
    private val buffer = ArrayList<MetricPoint>()

    private val ticker: Job = scope.launch {
        val intervalMs = exportIntervalSeconds.coerceAtLeast(1) * 1000L
        while (isActive) {
            delay(intervalMs)
            drainAndSend()
        }
    }

    fun emit(points: List<MetricPoint>) {
        if (points.isEmpty()) return
        scope.launch {
            val full =
                mutex.withLock {
                    for (p in points) {
                        if (buffer.size >= maxQueueSize) buffer.removeAt(0)
                        buffer.add(p)
                    }
                    buffer.size >= maxExportBatchSize
                }
            if (full) drainAndSend()
        }
    }

    fun flush() {
        scope.launch { drainAndSend() }
    }

    fun shutdown() {
        ticker.cancel()
        scope.launch { drainAndSend() }
    }

    private suspend fun drainAndSend() {
        val batch =
            mutex.withLock {
                if (buffer.isEmpty()) return
                val b = ArrayList(buffer)
                buffer.clear()
                b
            }
        sendWithRetry(batch)
    }

    private suspend fun sendWithRetry(points: List<MetricPoint>) {
        val body = serialize(points)
        var attempt = 0
        while (attempt <= maxRetries.coerceAtLeast(0)) {
            val ok =
                runCatching {
                    val response: HttpResponse =
                        httpClient.post(url) {
                            contentType(ContentType.Application.Json)
                            extraHeaders.forEach { (k, v) -> header(k, v) }
                            setBody(body)
                        }
                    if (debug) println("SCOUTDBG metrics n=${points.size} -> $url status=${response.status.value}")
                    response.status.value in 200..299
                }.onFailure {
                    if (debug) println("SCOUTDBG metrics n=${points.size} -> $url EXCEPTION ${it::class.simpleName}: ${it.message}")
                }.getOrDefault(false)
            if (ok) return
            attempt++
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
