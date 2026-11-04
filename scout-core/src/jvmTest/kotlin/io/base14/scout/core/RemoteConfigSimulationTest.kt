package io.base14.scout.core

import com.sun.net.httpserver.HttpServer
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * End-to-end check of user-based sampling against a production-like setup:
 *
 * - A dummy remote-config server applies per-user targeting rules and serves
 *   `scout_user_sampling` over HTTP. Rules can be re-published mid-test, like
 *   a console publish.
 * - The app-side client works like Firebase Remote Config or a flag SDK: it
 *   fetches, caches the last-fetched value in the app's own storage, and
 *   exposes that cached value synchronously at the next cold start.
 * - The same server acts as the OTLP collector, so assertions are on what the
 *   SDK actually exported over HTTP.
 */
class RemoteConfigSimulationTest {
    private lateinit var server: DummyBackend
    private var now = 1_700_000_000_000L

    @BeforeTest
    fun start() {
        server = DummyBackend()
    }

    @AfterTest
    fun stop() = server.close()

    private fun launch(store: KeyValueStore, userSampling: Boolean?) = ScoutCore(
        config = ScoutConfig(
            serviceName = "rc-sim",
            endpoint = server.baseUrl,
            sessionSampleRate = 100.0, // fallback keeps everything, so any drop is the user value
            sessionTimeoutMinutes = 30,
            maxSessionDurationMinutes = 0,
            exportIntervalSeconds = 1,
            metricExportIntervalSeconds = 1,
            userSampling = userSampling,
        ),
        store = store,
        httpClient = HttpClient(CIO),
        clock = { now },
    )

    /** One "screen" of activity: a span, an error span, a log, and a gauge, all tagged with [marker]. */
    private fun ScoutCore.activity(marker: String) {
        emitSpan("screen_view", mapOf("marker" to marker))
        emitSpan("error", mapOf("marker" to marker))
        log(io.base14.scout.core.export.ScoutLogLevel.INFO, "log $marker", mapOf("marker" to marker))
        emitGauge("rc_sim.gauge", 1.0, "1", mapOf("marker" to marker))
        forceFlush()
    }

    /** Ends the current session the way production does: background past the idle timeout, then foreground. */
    private fun ScoutCore.idleTimeout() {
        sessionManager.onBackground()
        now += 31 * 60_000L
        sessionManager.onForeground()
    }

    @Test
    fun remoteConfigDrivesPerUserSessionsEndToEnd() {
        // Remote config console: alice is in the "no" cohort, bob in the "yes" cohort, everyone else unset.
        server.publish(mapOf("alice" to false, "bob" to true))
        val sdkStore = InMemoryKeyValueStore() // the SDK's own persistent store
        val appStore = InMemoryKeyValueStore() // the app's remote-config cache
        val rc = AppRemoteConfig(server.baseUrl, appStore)

        // ── Launch 1: fresh install, nothing cached, so no opinion. Fallback session.
        var scout = launch(sdkStore, rc.cachedValue())
        val anonSession = scout.sessionId
        scout.activity("anon")
        server.awaitSpan("anon", "screen_view")
        assertEquals("fallback", server.span("anon", "screen_view")[ScoutAttributes.SESSION_SAMPLING_SOURCE])

        // alice logs in. The app fetches her value and passes it on, then calls setUser.
        // Anonymous → first id keeps the session, so her "no" waits for the next session start.
        scout.setUserSampling(rc.fetch("alice"))
        scout.setUser("alice")
        assertEquals(anonSession, scout.sessionId)
        scout.activity("alice-same-session")
        server.awaitSpan("alice-same-session", "screen_view")

        // ── Next session (idle timeout): alice's "no" applies. Spans are dropped except
        // errors; logs and metrics keep flowing, tagged as user-decided.
        scout.idleTimeout()
        val aliceSession = scout.sessionId
        assertNotEquals(anonSession, aliceSession)
        scout.activity("alice-no")
        server.awaitSpan("alice-no", "error")
        server.awaitLog("alice-no")
        server.awaitMetric("alice-no")
        assertTrue(server.spans("alice-no", "screen_view").isEmpty(), "a 'no' user's screen spans must be dropped")
        val err = server.span("alice-no", "error")
        assertEquals("false", err[ScoutAttributes.SESSION_SAMPLED])
        assertEquals("user", err[ScoutAttributes.SESSION_SAMPLING_SOURCE])
        val log = server.log("alice-no")
        assertEquals(aliceSession, log[ScoutAttributes.SESSION_ID])
        assertEquals("user", log[ScoutAttributes.SESSION_SAMPLING_SOURCE])
        assertEquals("false", log[ScoutAttributes.SESSION_SAMPLED])

        // ── Cold start mid-session: the app passes its cached value (false) at init and the
        // resumed session keeps its decision.
        scout = launch(sdkStore, rc.cachedValue())
        assertEquals(aliceSession, scout.sessionId)
        scout.setUser("alice")

        // ── The console moves alice to "yes". A background refresh delivers it mid-session:
        // the current session must NOT switch behaviour.
        server.publish(mapOf("alice" to true, "bob" to true))
        scout.setUserSampling(rc.fetch("alice"))
        scout.activity("alice-refresh-same-session")
        server.awaitSpan("alice-refresh-same-session", "error")
        assertTrue(server.spans("alice-refresh-same-session", "screen_view").isEmpty())

        // ...and it applies at the next session start.
        scout.idleTimeout()
        scout.activity("alice-yes")
        server.awaitSpan("alice-yes", "screen_view")
        assertEquals("user", server.span("alice-yes", "screen_view")[ScoutAttributes.SESSION_SAMPLING_SOURCE])

        // ── Account switch alice → bob without logout. The app fetches bob's value first, then
        // setUser rotates, so bob never inherits alice's session.
        server.publish(mapOf("alice" to false, "bob" to false))
        val beforeSwitch = scout.sessionId
        scout.setUserSampling(rc.fetch("bob"))
        scout.setUser("bob")
        assertNotEquals(beforeSwitch, scout.sessionId)
        scout.activity("bob-no")
        server.awaitSpan("bob-no", "error")
        assertTrue(server.spans("bob-no", "screen_view").isEmpty())
        assertEquals("bob", server.span("bob-no", "error")[ScoutAttributes.USER_ID])

        // ── Logout: clearUser resets the value and rotates into a fallback session.
        val beforeLogout = scout.sessionId
        scout.clearUser()
        rc.clear()
        assertNotEquals(beforeLogout, scout.sessionId)
        scout.activity("logged-out")
        server.awaitSpan("logged-out", "screen_view")
        assertEquals("fallback", server.span("logged-out", "screen_view")[ScoutAttributes.SESSION_SAMPLING_SOURCE])

        // ── Next launch after logout: nothing stored and nothing cached, so still fallback.
        now += 31 * 60_000L
        scout = launch(sdkStore, rc.cachedValue())
        scout.activity("relaunch-anon")
        server.awaitSpan("relaunch-anon", "screen_view")
        assertEquals("fallback", server.span("relaunch-anon", "screen_view")[ScoutAttributes.SESSION_SAMPLING_SOURCE])
    }

    @Test
    fun appThatNeverUsesTheFeatureSeesNoExtraSessions() {
        val scout = launch(InMemoryKeyValueStore(), null)
        val s = scout.sessionId
        scout.setUser("alice")
        scout.setUser("bob")
        scout.clearUser()
        assertEquals(s, scout.sessionId, "no rotation when user sampling is not in play")
    }
}

/** App-side remote-config client, modelled on Firebase Remote Config: fetch, cache, read cached at startup. */
private class AppRemoteConfig(private val baseUrl: String, private val cache: KeyValueStore) {
    private val http = java.net.http.HttpClient.newHttpClient()

    fun cachedValue(): Boolean? = cache.getString(KEY)?.toBooleanStrictOrNull()

    fun fetch(userId: String): Boolean? {
        val uri = URI("$baseUrl/remote-config?user=${URLEncoder.encode(userId, Charsets.UTF_8)}")
        val body = http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).body()
        val value = Json.parseToJsonElement(body).jsonObject["values"]?.jsonObject?.get(KEY)?.jsonPrimitive?.booleanOrNull
        if (value == null) cache.remove(KEY) else cache.putString(KEY, value.toString())
        return value
    }

    fun clear() = cache.remove(KEY)

    companion object {
        const val KEY = "scout_user_sampling"
    }
}

/** Remote-config server with per-user targeting, plus an OTLP/JSON sink. */
private class DummyBackend : AutoCloseable {
    @Volatile private var rules: Map<String, Boolean> = emptyMap()

    @Volatile private var version = 0
    private val traces = CopyOnWriteArrayList<JsonElement>()
    private val logs = CopyOnWriteArrayList<JsonElement>()
    private val metrics = CopyOnWriteArrayList<JsonElement>()

    private val http: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/remote-config") { ex ->
            val user = ex.requestURI.query?.split('&')?.firstOrNull { it.startsWith("user=") }?.substringAfter('=')
            val value = user?.let { rules[java.net.URLDecoder.decode(it, Charsets.UTF_8)] }
            val values = if (value == null) "{}" else """{"scout_user_sampling":$value}"""
            val body = """{"version":$version,"values":$values}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        for ((path, sink) in listOf("/v1/traces" to traces, "/v1/logs" to logs, "/v1/metrics" to metrics)) {
            createContext(path) { ex ->
                sink += Json.parseToJsonElement(ex.requestBody.readBytes().decodeToString())
                ex.sendResponseHeaders(200, -1)
                ex.close()
            }
        }
        start()
    }

    val baseUrl = "http://127.0.0.1:${http.address.port}"

    fun publish(newRules: Map<String, Boolean>) {
        rules = newRules
        version++
    }

    fun spans(marker: String, name: String): List<Map<String, String>> =
        records(traces, "spans").filter { it["marker"] == marker && it["@name"] == name }

    fun span(marker: String, name: String) = spans(marker, name).single()

    fun log(marker: String) = records(logs, "logRecords").single { it["marker"] == marker }

    fun awaitSpan(marker: String, name: String) = await("span $name/$marker") { spans(marker, name).isNotEmpty() }
    fun awaitLog(marker: String) = await("log $marker") { records(logs, "logRecords").any { it["marker"] == marker } }
    fun awaitMetric(marker: String) = await("metric $marker") { records(metrics, "dataPoints").any { it["marker"] == marker } }

    private fun await(what: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(50)
        }
        fail("timed out waiting for $what")
    }

    /** Flattens every OTLP record under arrays named [arrayKey] into attribute maps (plus `@name`). */
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
