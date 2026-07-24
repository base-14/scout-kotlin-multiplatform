package io.base14.scout.android.http

import io.base14.scout.android.Scout
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.platform.epochNanos
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class ScoutOkHttpInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val core = Scout.coreInternal() ?: return chain.proceed(chain.request())
        if (!core.config.enableHttpTracking) return chain.proceed(chain.request())
        val original = chain.request()
        val url = original.url.toString()
        if (url.startsWith(core.config.endpoint) || core.config.ignoreUrlPatterns.any { url.contains(it) }) {
            return chain.proceed(original)
        }

        val startNanos = epochNanos()
        val span =
            core.beginSpan(
                name = ScoutSpans.HTTP_REQUEST,
                attributes =
                mapOf(
                    ScoutAttributes.HTTP_METHOD to original.method,
                    ScoutAttributes.URL_FULL to url,
                ),
                startNanos = startNanos,
                isClient = true,
            )

        val request =
            if (span != null && isFirstParty(core, original.url.host)) {
                original.newBuilder().header("traceparent", span.traceparent).build()
            } else {
                original
            }

        var response: Response? = null
        var errorMsg: String? = null
        try {
            response = chain.proceed(request)
            return response
        } catch (e: IOException) {
            errorMsg = e.message ?: e::class.simpleName
            throw e
        } finally {
            val endNanos = epochNanos()
            val attrs =
                mutableMapOf<String, Any>(
                    ScoutAttributes.HTTP_DURATION_MS to (endNanos - startNanos) / 1_000_000L,
                )
            response?.let { r ->
                attrs[ScoutAttributes.HTTP_STATUS_CODE] = r.code
                val len = r.body?.contentLength() ?: -1L
                if (len >= 0) attrs[ScoutAttributes.HTTP_BODY_SIZE] = len
                attrs[ScoutAttributes.HTTP_ROUTE] = r.request.url.encodedPath
            }
            errorMsg?.let { attrs[ScoutAttributes.HTTP_ERROR] = it }
            val statusCode = response?.code ?: 0
            val isError = errorMsg != null || statusCode >= 400
            span?.end(
                endNanos = endNanos,
                attributes = attrs,
                errorMessage = if (isError) (errorMsg ?: "HTTP $statusCode") else null,
            )
        }
    }

    private fun isFirstParty(
        core: ScoutCore,
        host: String,
    ): Boolean {
        val hosts = core.config.firstPartyHosts
        return hosts.any { pattern ->
            if (pattern.startsWith("*.")) {
                val suffix = pattern.substring(1)
                host == pattern.substring(2) || host.endsWith(suffix)
            } else {
                host == pattern
            }
        }
    }
}
