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

class SpanResurrector(
    endpoint: String,
    private val extraHeaders: Map<String, String>,
    private val httpClient: HttpClient,
) {
    private val url = endpoint.trimEnd('/') + "/v1/traces"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun post(body: String) {
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
}
