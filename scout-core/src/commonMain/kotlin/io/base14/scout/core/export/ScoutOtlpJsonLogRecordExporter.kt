package io.base14.scout.core.export

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.model.ReadableLogRecord

class ScoutOtlpJsonLogRecordExporter(
    endpoint: String,
    private val extraHeaders: Map<String, String>,
    private val httpClient: HttpClient,
    private val debug: Boolean = false,
    private val maxRetries: Int = 0,
) : LogRecordExporter {

    private val url = endpoint.trimEnd('/') + "/v1/logs"

    override suspend fun export(telemetry: List<ReadableLogRecord>): OperationResultCode {
        if (telemetry.isEmpty()) return OperationResultCode.Success
        val body = OtlpJsonLogSerializer.serialize(telemetry)
        var attempt = 0
        while (attempt <= maxRetries.coerceAtLeast(0)) {
            val ok = try {
                val response: HttpResponse = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    extraHeaders.forEach { (k, v) -> header(k, v) }
                    setBody(body)
                }
                if (debug) println("SCOUTDBG logs n=${telemetry.size} -> $url status=${response.status.value}")
                response.status.value in 200..299
            } catch (t: Throwable) {
                if (debug) println("SCOUTDBG logs n=${telemetry.size} -> $url EXCEPTION ${t::class.simpleName}: ${t.message}")
                false
            }
            if (ok) return OperationResultCode.Success
            attempt++
        }
        return OperationResultCode.Failure
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
