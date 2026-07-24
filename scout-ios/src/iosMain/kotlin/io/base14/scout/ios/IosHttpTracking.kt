package io.base14.scout.ios

import io.base14.scout.core.ScoutCore
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.setValue
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLProtocol
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSData
import platform.Foundation.NSURLProtocolMeta
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.valueForKey
import platform.darwin.NSObject

/**
 * HTTP auto-instrumentation living inside the Kotlin engine: a pass-through `NSURLProtocol`
 * that times each request and emits `http.request` through `ScoutEngine.reportHttp`.
 * Port of ScoutKit's `HttpTracking`/`ScoutURLProtocol`. Registered globally and injected
 * into `NSURLSessionConfiguration` defaults so Ktor/Darwin and custom sessions are covered.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosHttpTracking {
    private var installed = false
    const val HANDLED_KEY = "ScoutURLProtocolHandled"

    fun install() {
        if (installed) return
        installed = true
        NSURLProtocol.registerClass(ScoutUrlProtocol as kotlinx.cinterop.ObjCClass)
    }

    fun nowEpochNanos(): Long = (NSDate().timeIntervalSince1970 * 1_000_000_000).toLong()

    fun collectorHost(): String? =
        NSURL.URLWithString(ScoutEngine.collectorEndpoint)?.host
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal class ScoutUrlProtocol : NSURLProtocol, NSURLSessionDataDelegateProtocol {
    @kotlinx.cinterop.ObjCObjectBase.OverrideInit
    @Suppress("CONFLICTING_OVERLOADS")
    constructor(request: NSURLRequest, cachedResponse: platform.Foundation.NSCachedURLResponse?, client: platform.Foundation.NSURLProtocolClientProtocol?) : super(request, cachedResponse, client)

    private var session: NSURLSession? = null
    private var task: NSURLSessionDataTask? = null
    private var startNanos: Long = 0
    private var response: NSURLResponse? = null
    private var httpSpan: ScoutCore.ScoutSpan? = null

    companion object : NSURLProtocolMeta() {
        override fun canInitWithRequest(request: NSURLRequest): Boolean {
            if (NSURLProtocol.propertyForKey(IosHttpTracking.HANDLED_KEY, inRequest = request) != null) return false
            val url = request.URL ?: return false
            val scheme = url.scheme?.lowercase() ?: return false
            if (scheme != "http" && scheme != "https") return false
            val collector = IosHttpTracking.collectorHost()
            if (collector != null && url.host == collector) return false
            val absolute = url.absoluteString ?: ""
            if (ScoutEngine.ignoreUrlPatterns().any { it.isNotEmpty() && absolute.contains(it) }) return false
            return true
        }

        override fun canonicalRequestForRequest(request: NSURLRequest): NSURLRequest = request
    }

    override fun startLoading() {
        val mutable = request.mutableCopy() as NSMutableURLRequest
        NSURLProtocol.setProperty(true, forKey = IosHttpTracking.HANDLED_KEY, inRequest = mutable)
        startNanos = IosHttpTracking.nowEpochNanos()
        val method = (this.request.valueForKey("HTTPMethod") as? String) ?: "GET"
        val span = ScoutEngine.beginHttp(method, request.URL?.absoluteString ?: "", startNanos)
        httpSpan = span
        if (span != null) {
            val traceparent: String? = ScoutEngine.firstPartyTraceparent(request.URL?.host, span)
            if (traceparent != null) {
                mutable.setValue(traceparent, forHTTPHeaderField = "traceparent")
            }
        }
        val s = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration,
            delegate = this,
            delegateQueue = null,
        )
        session = s
        task = s.dataTaskWithRequest(mutable).also { it.resume() }
    }

    override fun stopLoading() {
        task?.cancel()
        session?.invalidateAndCancel()
    }

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (NSURLSessionResponseDisposition) -> Unit,
    ) {
        response = didReceiveResponse
        client?.URLProtocol(this, didReceiveResponse = didReceiveResponse, cacheStoragePolicy = platform.Foundation.NSURLCacheStoragePolicy.NSURLCacheStorageNotAllowed)
        completionHandler(NSURLSessionResponseAllow)
    }

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        client?.URLProtocol(this, didLoadData = didReceiveData)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        emit(didCompleteWithError)
        if (didCompleteWithError != null) {
            client?.URLProtocol(this, didFailWithError = didCompleteWithError)
        } else {
            client?.URLProtocolDidFinishLoading(this)
        }
    }

    private fun emit(error: NSError?) {
        val span = httpSpan ?: return
        httpSpan = null
        val method = (this.request.valueForKey("HTTPMethod") as? String) ?: "GET"
        val url = request.URL?.absoluteString ?: ""
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toLong()
            ?: if (error == null) 0L else -1L
        ScoutEngine.endHttp(
            span = span,
            method = method,
            url = url,
            statusCode = status,
            responseSize = response?.expectedContentLength ?: -1L,
            errorMessage = error?.localizedDescription,
            startEpochNanos = startNanos,
            endEpochNanos = IosHttpTracking.nowEpochNanos(),
        )
    }
}
