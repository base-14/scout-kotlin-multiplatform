package io.base14.scout.core

import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutResourceAttributes
import io.base14.scout.core.semantics.ScoutSpans
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticsConformanceTest {

    @Test
    fun scopeName() = assertEquals("base14.scout.android", SCOUT_SCOPE_NAME)

    @Test
    fun spanNames() {
        assertEquals("screen_view", ScoutSpans.SCREEN_VIEW)
        assertEquals("screen_load", ScoutSpans.SCREEN_LOAD)
        assertEquals("view_session", ScoutSpans.VIEW_SESSION)
        assertEquals("app_startup", ScoutSpans.APP_STARTUP)
        assertEquals("app_lifecycle.changed", ScoutSpans.APP_LIFECYCLE_CHANGED)
        assertEquals("user_interaction", ScoutSpans.USER_INTERACTION)
        assertEquals("http.request", ScoutSpans.HTTP_REQUEST)
        assertEquals("error", ScoutSpans.ERROR)
        assertEquals("app_crash", ScoutSpans.APP_CRASH)
        assertEquals("native_crash", ScoutSpans.NATIVE_CRASH)
        assertEquals("anr", ScoutSpans.ANR)
        assertEquals("long_task", ScoutSpans.LONG_TASK)
        assertEquals("frozen_frame", ScoutSpans.FROZEN_FRAME)
        assertEquals("app_vital", ScoutSpans.APP_VITAL)
    }

    @Test
    fun errorClassBypassesSampling() {
        assertEquals(setOf("error", "app_crash", "native_crash", "anr", "ui_hang"), ScoutSpans.ERROR_CLASS)
    }

    @Test
    fun sessionAndIdentityKeys() {
        assertEquals("session.id", ScoutAttributes.SESSION_ID)
        assertEquals("session.type", ScoutAttributes.SESSION_TYPE)
        assertEquals("session.sample_rate", ScoutAttributes.SESSION_SAMPLE_RATE)
        assertEquals("session.sampled", ScoutAttributes.SESSION_SAMPLED)
        assertEquals("session.start_time", ScoutAttributes.SESSION_START_TIME)
        assertEquals("session.previous_id", ScoutAttributes.SESSION_PREVIOUS_ID)
        assertEquals("user.id", ScoutAttributes.USER_ID)
        assertEquals("user.anonymous_id", ScoutAttributes.USER_ANONYMOUS_ID)
    }

    @Test
    fun httpKeysAreNewSpecKeysOnly() {
        assertEquals("http.request.method", ScoutAttributes.HTTP_METHOD)
        assertEquals("url.full", ScoutAttributes.URL_FULL)
        assertEquals("http.response.status_code", ScoutAttributes.HTTP_STATUS_CODE)
        assertEquals("http.response.body.size", ScoutAttributes.HTTP_BODY_SIZE)
        assertEquals("http.route", ScoutAttributes.HTTP_ROUTE)
    }

    @Test
    fun screenAndViewKeys() {
        assertEquals("screen.name", ScoutAttributes.SCREEN_NAME)
        assertEquals("screen.load_time", ScoutAttributes.SCREEN_LOAD_TIME)
        assertEquals("view.id", ScoutAttributes.VIEW_ID)
        assertEquals("view.loading_type", ScoutAttributes.VIEW_LOADING_TYPE)
        assertEquals("view.time_spent", ScoutAttributes.VIEW_TIME_SPENT)
        assertEquals("app_startup.type", ScoutAttributes.APP_STARTUP_TYPE)
        assertEquals("app_startup.duration", ScoutAttributes.APP_STARTUP_DURATION)
    }

    @Test
    fun resourceKeys() {
        assertEquals("service.name", ScoutResourceAttributes.SERVICE_NAME)
        assertEquals("service.version", ScoutResourceAttributes.SERVICE_VERSION)
        assertEquals("os.name", ScoutResourceAttributes.OS_NAME)
        assertEquals("os.version", ScoutResourceAttributes.OS_VERSION)
        assertEquals("host.arch", ScoutResourceAttributes.HOST_ARCH)
        assertEquals("device.model.name", ScoutResourceAttributes.DEVICE_MODEL_NAME)
        assertEquals("network.connection.type", ScoutResourceAttributes.NETWORK_CONNECTION_TYPE)
    }
}
