package io.base14.scout.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.base14.scout.android.internal.DeviceResources
import io.base14.scout.core.ScoutConfig
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.platform.InMemoryKeyValueStore
import io.base14.scout.core.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ScoutInstrumentedTest {
    private lateinit var server: MockWebServer
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newCore(serviceName: String = "test-svc") =
        ScoutCore(
            config =
            ScoutConfig(
                serviceName = serviceName,
                serviceVersion = "9.9.9",
                endpoint = server.url("/").toString().trimEnd('/'),
                sessionSampleRate = 100.0,
            ),
            store = InMemoryKeyValueStore(),
            httpClient = HttpClient(OkHttp),
            platformResourceAttributes = DeviceResources.collect(context),
        )

    @Test
    fun deviceResourcesAreConformant() {
        val attrs = DeviceResources.collect(context)
        assertEquals("Android", attrs["os.name"])
        assertTrue(attrs.containsKey("device.model.name"))
        assertTrue(attrs.containsKey("host.arch"))
        assertTrue(attrs.containsKey("app.bundle_id"))
        assertTrue(attrs.containsKey("os.version"))
        assertTrue(attrs.containsKey("network.connection.type"))
    }

    @Test
    fun emitSpanReachesCollectorWithConformantPayload() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"partialSuccess\":{}}"))
        newCore().emitSpan(
            "screen_view",
            mapOf(
                "screen.name" to "HomeScreen",
                "view.id" to "v1",
                "view.loading_type" to "initial_load",
                "view.is_active" to true,
            ),
        )
        val req = server.takeRequest(15, TimeUnit.SECONDS)
        assertNotNull("no export reached the collector", req)
        assertEquals("/v1/traces", req!!.path)
        val body = req.body.readUtf8()

        assertContains(body, "base14.scout.android")
        assertContains(body, "screen_view")
        assertContains(body, "session.id")
        assertContains(body, "session.type")
        assertContains(body, "\"user\"")
        assertContains(body, "session.sample_rate")
        assertContains(body, "session.start_time")
        assertContains(body, "user.anonymous_id")
        assertContains(body, "HomeScreen")
        assertContains(body, "view.id")
        assertContains(body, "service.name")
        assertContains(body, "test-svc")
        assertContains(body, "os.name")
        assertContains(body, "Android")
    }

    @Test
    fun httpSpanCarriesNewKeysAndClientKind() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val span =
            newCore().beginSpan(
                "http.request",
                mapOf("http.request.method" to "GET", "url.full" to "https://api.example/x"),
                isClient = true,
            )
        assertNotNull(span)
        span!!.end(attributes = mapOf("http.response.status_code" to 200L, "http.duration_ms" to 12L))
        val req = server.takeRequest(15, TimeUnit.SECONDS)
        assertNotNull(req)
        val body = req!!.body.readUtf8()
        assertContains(body, "http.request")
        assertContains(body, "http.request.method")
        assertContains(body, "url.full")
        assertContains(body, "http.response.status_code")
        assertContains(body, "\"kind\":3")
    }

    @Test
    fun crashReplayCarriesDeadSessionIdentityStackAndTimeSince() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val core = newCore()
        core.persistCrash(
            mapOf(
                "session.id" to "dead-session-xyz",
                "session.start_time" to "2020-01-01T00:00:00.000Z",
                "session.sample_rate" to "100.0",
                "error.type" to "java.lang.RuntimeException",
                "error.message" to "boom",
                "error.stack_trace" to "java.lang.RuntimeException: boom\n\tat com.example.Foo.bar(Foo.kt:42)",
                "error.fingerprint" to "deadbeef",
                "error.time_since_app_start_ms" to "1234",
                "crash.previous_session_id" to "dead-session-xyz",
            ),
        )
        core.replayPendingCrash()
        val req = server.takeRequest(15, TimeUnit.SECONDS)
        assertNotNull("crash replay did not export", req)
        val body = req!!.body.readUtf8()

        assertContains(body, "\"name\":\"app_crash\"")
        assertTrue("crash replay must emit a single app_crash span, not a duplicate error span", !body.contains("\"name\":\"error\""))
        assertContains(body, "dead-session-xyz")
        assertContains(body, "2020-01-01T00:00:00.000Z")
        assertContains(body, "error.stack_trace")
        assertContains(body, "com.example.Foo.bar")
        assertContains(body, "\"unhandled\"")
        assertContains(body, "\"jvm_crash\"")
        assertContains(body, "error.time_since_app_start_ms")
        assertContains(body, "1234")
    }

    @Test
    fun ownerIngestsForwardedGuestSpanUnderProducerScopeAndSharedSession() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val core = newCore()
        val sharedSession = core.bridgeContext().sessionId

        val batch =
            io.base14.scout.core.bridge.BridgeCodec.encodeSpans(
                listOf(
                    io.base14.scout.core.bridge.ForwardedSpan(
                        scope = "base14.scout.flutter",
                        name = "screen_view",
                        startUnixNano = "1000",
                        endUnixNano = "2000",
                        attributes = mapOf("screen.name" to "FlutterProfile"),
                    ),
                ),
            )
        core.ingestForwardedSpans(batch)

        val req = server.takeRequest(15, TimeUnit.SECONDS)
        assertNotNull("owner did not export the ingested span", req)
        val body = req!!.body.readUtf8()
        assertContains(body, "base14.scout.flutter")
        assertContains(body, "FlutterProfile")
        assertContains(body, sharedSession)
    }

    @Test
    fun sessionPersistsAndResumesAcrossInstances() {
        val store = InMemoryKeyValueStore()
        val cfg = ScoutConfig(serviceName = "s", endpoint = "http://x")
        val first = SessionManager(cfg, store).sessionId()
        val second = SessionManager(cfg, store).sessionId()
        assertEquals(first, second)
    }

    @Test
    fun initializeIsIdempotentAndDoesNotCrash() {
        Scout.initialize(
            context,
            ScoutConfig(serviceName = "smoke", endpoint = server.url("/").toString().trimEnd('/'), sessionSampleRate = 100.0),
        )
        assertTrue(Scout.isInitialized)
        assertNotNull(Scout.sessionId)
        Scout.initialize(context, ScoutConfig(serviceName = "smoke2", endpoint = "http://other"))
        assertTrue(Scout.isInitialized)
    }

    private fun assertContains(
        haystack: String,
        needle: String,
    ) = assertTrue("expected payload to contain '$needle'", haystack.contains(needle))
}
