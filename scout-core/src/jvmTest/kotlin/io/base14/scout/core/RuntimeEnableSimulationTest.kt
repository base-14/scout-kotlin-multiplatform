package io.base14.scout.core

import com.sun.net.httpserver.HttpServer
import io.base14.scout.core.bridge.BridgeCodec
import io.base14.scout.core.bridge.ForwardedLog
import io.base14.scout.core.bridge.ForwardedMetric
import io.base14.scout.core.bridge.ForwardedSpan
import io.base14.scout.core.export.ScoutLogLevel
import io.base14.scout.core.platform.InMemoryKeyValueStore
import io.base14.scout.core.platform.KeyValueStore
import io.base14.scout.core.semantics.ScoutAttributes
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * End-to-end check of the runtime kill switch (`enabled` / `setEnabled`)
 * against a local OTLP/JSON sink that counts every request it receives.
 *
 * Modelled on the customer case: a background service whose process is woken
 * outside working hours. The app computes "inside the work window" from its
 * cached schedule, passes it at init, and flips it when the window opens.
 */
class RuntimeEnableSimulationTest {
    private lateinit var sink: OtlpSink
    private lateinit var cacheDir: File

    @BeforeTest
    fun start() {
        sink = OtlpSink()
        cacheDir = Files.createTempDirectory("scout-enable").toFile()
    }

    @AfterTest
    fun stop() {
        sink.close()
        cacheDir.deleteRecursively()
    }

    private fun launch(
        store: KeyValueStore,
        enabled: Boolean,
        userSampling: Boolean? = null,
        offline: Boolean = false,
    ) = ScoutCore(
        config = ScoutConfig(
            serviceName = "enable-sim",
            endpoint = sink.baseUrl,
            sessionSampleRate = 100.0,
            alwaysCaptureErrors = true,
            exportIntervalSeconds = 1,
            metricExportIntervalSeconds = 1,
            offlineBufferEnabled = offline,
            userSampling = userSampling,
            enabled = enabled,
        ),
        store = store,
        httpClient = HttpClient(CIO),
        cacheDirPath = cacheDir.absolutePath,
    )

    /** Every signal the SDK can produce, all tagged with [marker]. */
    private fun ScoutCore.everything(marker: String) {
        val m = mapOf("marker" to marker)
        emitSpan("screen_view", m)
        emitSpan("error", m) // error-class: bypasses sampling while alwaysCaptureErrors is on
        emitSpan("app_crash", m)
        beginScreen("screen_view_root", m)?.end()
        beginSpan("http.request", m)?.end()
        log(ScoutLogLevel.ERROR, "log $marker", m)
        emitGauge("enable_sim.gauge", 1.0, "1", m)
        startVital("v")
        endVital("v")
        addBreadcrumb("tap", marker)
        ingestForwardedSpans(
            BridgeCodec.encodeSpans(
                listOf(ForwardedSpan("flutter", "fwd_span", startUnixNano = "1", endUnixNano = "2", attributes = m)),
            ),
        )
        ingestForwardedLogs(BridgeCodec.encodeLogs(listOf(ForwardedLog(scope = "flutter", body = "fwd", attributes = m))))
        ingestForwardedMetrics(BridgeCodec.encodeMetrics(listOf(ForwardedMetric(name = "fwd_metric", value = 1.0, attributes = m))))
        forceFlush()
    }

    @Test
    fun initDisabledMakesZeroRequestsAndCreatesNoSession() {
        val store = InMemoryKeyValueStore()
        // A session from earlier in the day, still within the idle timeout.
        launch(store, enabled = true).sessionId
        val persisted = store.getString("scout.session")

        // WorkManager wakes the process at 03:00: outside the work window.
        val scout = launch(store, enabled = false, offline = true)
        assertFalse(scout.isEnabled)
        scout.everything("night")
        scout.persistCrash(mapOf(ScoutAttributes.ERROR_TYPE to "Boom"))
        scout.replayPendingCrash()
        scout.resurrectRootSpan()

        sink.assertQuietFor(2_500)
        assertEquals(persisted, store.getString("scout.session"), "the persisted session must not be touched")
        assertTrue(offlineFiles().isEmpty(), "nothing may be buffered to disk while disabled: ${offlineFiles()}")
        assertEquals("[]", scout.breadcrumbs.toJson())
    }

    @Test
    fun disableMidSessionFlushesWhatWasRecordedThenDropsEverything() {
        val store = InMemoryKeyValueStore()
        val scout = launch(store, enabled = true)
        val session = scout.sessionId
        val openScreen = scout.beginScreen("screen_view", mapOf("marker" to "open-across-disable"))
        scout.emitSpan("screen_view", mapOf("marker" to "day"))
        scout.log(ScoutLogLevel.INFO, "day", mapOf("marker" to "day"))
        scout.emitGauge("enable_sim.gauge", 1.0, "1", mapOf("marker" to "day"))

        // The work window closes. What was recorded is flushed without waiting for forceFlush.
        scout.setEnabled(false)
        sink.awaitSpan("day")
        sink.awaitLog("day")
        sink.awaitMetric("day")
        assertTrue(store.getString("scout.session")!!.contains("\"ended\":true"), "the session ends at disable")

        sink.settle()
        val before = sink.requests.get()
        openScreen?.end() // started while enabled, ends while disabled: dropped
        scout.everything("after-disable")
        scout.setEnabled(false) // no-op repeat
        sink.assertQuietFor(2_500, since = before)
        assertTrue(sink.spans("open-across-disable").isEmpty())
        assertEquals(session, scout.sessionId, "the core getter keeps the ended id; it is never exported")
    }

    @Test
    fun enableStartsAFreshSessionWithSamplingResolvedAndResumesExport() {
        val store = InMemoryKeyValueStore()
        val earlier = launch(store, enabled = true).sessionId

        // Woken before the window; the app's cached remote config says this user is a "no".
        val scout = launch(store, enabled = false, userSampling = false)
        scout.setUser("expert-42") // identity changes while disabled are kept
        scout.everything("pre-window")
        sink.assertQuietFor(1_500)

        // One hour before the shift starts: the app turns RUM on.
        scout.setEnabled(true)
        assertTrue(scout.isEnabled)
        val fresh = scout.sessionId
        assertNotEquals(earlier, fresh)
        scout.everything("in-window")
        sink.awaitSpan("in-window", "error")
        sink.awaitLog("in-window")
        val err = sink.span("in-window", "error")
        assertEquals(fresh, err[ScoutAttributes.SESSION_ID])
        assertEquals(earlier, err[ScoutAttributes.SESSION_PREVIOUS_ID])
        assertEquals("user", err[ScoutAttributes.SESSION_SAMPLING_SOURCE])
        assertEquals("expert-42", err[ScoutAttributes.USER_ID])
        assertTrue(sink.spans("in-window", "screen_view").isEmpty(), "the user 'no' applies to the fresh session")
        assertTrue(sink.spans("pre-window").isEmpty())

        scout.setEnabled(true) // no-op repeat: no rotation
        assertEquals(fresh, scout.sessionId)
    }

    @Test
    fun spanOpenAcrossDisableAndEnableIsDropped() {
        val scout = launch(InMemoryKeyValueStore(), enabled = true)
        val open = scout.beginSpan("http.request", mapOf("marker" to "straddle"))
        scout.setEnabled(false)
        scout.setEnabled(true)
        open?.end()
        scout.emitSpan("screen_view", mapOf("marker" to "after-cycle"))
        scout.forceFlush()
        sink.awaitSpan("after-cycle")
        assertTrue(sink.spans("straddle").isEmpty())
    }

    @Test
    fun crashWhileDisabledIsNeverReplayed() {
        val store = InMemoryKeyValueStore()
        val scout = launch(store, enabled = true)
        scout.setEnabled(false)
        scout.persistCrash(mapOf(ScoutAttributes.ERROR_TYPE to "NightCrash", "marker" to "night-crash"))

        // Next launch is inside the window.
        val next = launch(store, enabled = true)
        next.runWhenEnabled { next.replayPendingCrash() }
        next.emitSpan("screen_view", mapOf("marker" to "next-launch"))
        next.forceFlush()
        sink.awaitSpan("next-launch")
        sink.settle()
        assertTrue(sink.spans("night-crash").isEmpty())
    }

    @Test
    fun crashFromAnEnabledRunWaitsForTheNextEnabledPoint() {
        val store = InMemoryKeyValueStore()
        launch(store, enabled = true).persistCrash(
            mapOf(ScoutAttributes.ERROR_TYPE to "DayCrash", "marker" to "day-crash"),
        )

        // The next launch starts disabled: the report is kept, not sent and not dropped.
        val scout = launch(store, enabled = false)
        var replayed = 0
        scout.runWhenEnabled {
            replayed++
            scout.replayPendingCrash()
        }
        sink.assertQuietFor(1_500)
        assertEquals(0, replayed)

        scout.setEnabled(true)
        assertEquals(1, replayed)
        scout.forceFlush()
        sink.awaitSpan("day-crash", "app_crash")
        assertEquals(scout.sessionId, sink.span("day-crash", "app_crash")[ScoutAttributes.SESSION_ID])
    }

    @Test
    fun crashReportsFromADisabledStretchAreRecognisedOnTheNextLaunch() {
        val store = InMemoryKeyValueStore()
        val beforeDisabled = System.currentTimeMillis() - 1
        launch(store, enabled = false) // process ends while disabled
        Thread.sleep(5)
        val next = launch(store, enabled = true)
        val during = System.currentTimeMillis()

        assertFalse(next.occurredWhileDisabled(beforeDisabled), "a crash from the earlier enabled run is kept")
        assertTrue(next.occurredWhileDisabled(during))
        assertTrue(next.crashReportOccurredWhileDisabled((during * 1_000).toString()), "micros")
        assertTrue(next.crashReportOccurredWhileDisabled(during.toString()), "millis")
        assertFalse(next.crashReportOccurredWhileDisabled((beforeDisabled / 1_000 - 1).toString()), "seconds")
        assertTrue(next.crashReportOccurredWhileDisabled("not-a-time"), "unknown time after a disabled run: drop")
        assertTrue(next.crashReportOccurredWhileDisabled(null))

        // This launch is enabled, so the marker is gone: the launch after it drops nothing.
        val third = launch(store, enabled = true)
        assertFalse(third.occurredWhileDisabled(System.currentTimeMillis()))
        assertFalse(third.crashReportOccurredWhileDisabled(null))
    }

    @Test
    fun bridgeContextTellsAttachedLayersWhenTheOwnerIsDisabled() {
        val scout = launch(InMemoryKeyValueStore(), enabled = true)
        val seen = CopyOnWriteArrayList<Boolean>()
        scout.onBridgeContextChanged = { seen += it.enabled }
        scout.setEnabled(false)
        assertFalse(scout.bridgeContext().enabled)
        scout.setEnabled(true)
        assertTrue(scout.bridgeContext().enabled)
        assertEquals(listOf(false, true), seen.toList())
    }

    private fun offlineFiles(): List<File> =
        File(cacheDir, "scout_offline").walkTopDown().filter { it.isFile }.toList()
}

/** OTLP/JSON sink that records every request on any path. */
private class OtlpSink : AutoCloseable {
    val requests = AtomicInteger(0)
    private val traces = CopyOnWriteArrayList<JsonElement>()
    private val logs = CopyOnWriteArrayList<JsonElement>()
    private val metrics = CopyOnWriteArrayList<JsonElement>()

    private val http: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            requests.incrementAndGet()
            val body = ex.requestBody.readBytes().decodeToString()
            when (ex.requestURI.path) {
                "/v1/traces" -> traces += Json.parseToJsonElement(body)
                "/v1/logs" -> logs += Json.parseToJsonElement(body)
                "/v1/metrics" -> metrics += Json.parseToJsonElement(body)
            }
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }
        start()
    }

    val baseUrl = "http://127.0.0.1:${http.address.port}"

    fun spans(marker: String, name: String? = null): List<Map<String, String>> =
        records(traces, "spans").filter { it["marker"] == marker && (name == null || it["@name"] == name) }

    fun span(marker: String, name: String) = spans(marker, name).single()

    fun awaitSpan(marker: String, name: String? = null) = await("span $name/$marker") { spans(marker, name).isNotEmpty() }
    fun awaitLog(marker: String) = await("log $marker") { records(logs, "logRecords").any { it["marker"] == marker } }
    fun awaitMetric(marker: String) = await("metric $marker") { records(metrics, "dataPoints").any { it["marker"] == marker } }

    /** Waits until no request has arrived for a full export interval. */
    fun settle() {
        var last = -1
        while (last != requests.get()) {
            last = requests.get()
            Thread.sleep(1_500)
        }
    }

    /** Fails if any request arrives within [ms] (counted from [since]). */
    fun assertQuietFor(ms: Long, since: Int = 0) {
        Thread.sleep(ms)
        assertEquals(since, requests.get(), "no OTLP request may be made while disabled")
    }

    private fun await(what: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(50)
        }
        fail("timed out waiting for $what")
    }

    private fun records(payloads: List<JsonElement>, arrayKey: String): List<Map<String, String>> {
        val out = ArrayList<Map<String, String>>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> for ((k, v) in e) {
                    if (k == arrayKey && v is JsonArray) {
                        v.forEach { rec -> out += flatten(rec.jsonObject) }
                    } else {
                        walk(v)
                    }
                }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        payloads.forEach(::walk)
        return out
    }

    private fun flatten(rec: JsonObject): Map<String, String> {
        val m = HashMap<String, String>()
        (rec["name"] as? JsonPrimitive)?.let { m["@name"] = it.content }
        rec["attributes"]?.jsonArray?.forEach { a ->
            val key = a.jsonObject["key"]!!.jsonPrimitive.content
            val v = a.jsonObject["value"]!!.jsonObject.values.first()
            m[key] = (v as? JsonPrimitive)?.content ?: v.toString()
        }
        return m
    }

    override fun close() = http.stop(0)
}
