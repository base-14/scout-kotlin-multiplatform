package io.base14.scout.core.export

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter

class ScoutOtlpJsonSpanExporter(
    endpoint: String,
    private val extraHeaders: Map<String, String>,
    private val httpClient: HttpClient,
    private val debug: Boolean = false,
    private val maxRetries: Int = 0,
) : SpanExporter {

    private val url = endpoint.trimEnd('/') + "/v1/traces"

    override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
        if (telemetry.isEmpty()) return OperationResultCode.Success
        val body = OtlpJsonSerializer.serialize(telemetry)
        var attempt = 0
        while (attempt <= maxRetries.coerceAtLeast(0)) {
            val ok = try {
                val response: HttpResponse = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    extraHeaders.forEach { (k, v) -> header(k, v) }
                    setBody(body)
                }
                if (debug) println("SCOUTDBG spans n=${telemetry.size} -> $url status=${response.status.value}")
                response.status.value in 200..299
            } catch (t: Throwable) {
                if (debug) println("SCOUTDBG spans n=${telemetry.size} -> $url EXCEPTION ${t::class.simpleName}: ${t.message}")
                false
            }
            if (ok) return OperationResultCode.Success
            attempt++
        }
        return OperationResultCode.Failure
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override suspend fun shutdown(): OperationResultCode {
        httpClient.close()
        return OperationResultCode.Success
    }
}
