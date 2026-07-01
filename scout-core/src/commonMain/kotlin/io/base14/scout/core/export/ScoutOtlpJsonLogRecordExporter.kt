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
) : LogRecordExporter {

    private val url = endpoint.trimEnd('/') + "/v1/logs"

    override suspend fun export(telemetry: List<ReadableLogRecord>): OperationResultCode {
        if (telemetry.isEmpty()) return OperationResultCode.Success
        return try {
            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                extraHeaders.forEach { (k, v) -> header(k, v) }
                setBody(OtlpJsonLogSerializer.serialize(telemetry))
            }
            if (response.status.value in 200..299) OperationResultCode.Success else OperationResultCode.Failure
        } catch (t: Throwable) {
            OperationResultCode.Failure
        }
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
