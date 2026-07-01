package io.base14.scout.core.semantics

object ScoutSpans {
    const val SCREEN_VIEW = "screen_view"
    const val SCREEN_LOAD = "screen_load"
    const val VIEW_SESSION = "view_session"
    const val APP_STARTUP = "app_startup"
    const val APP_LIFECYCLE_CHANGED = "app_lifecycle.changed"
    const val USER_INTERACTION = "user_interaction"
    const val HTTP_REQUEST = "http.request"
    const val ERROR = "error"
    const val APP_CRASH = "app_crash"
    const val NATIVE_CRASH = "native_crash"
    const val ANR = "anr"
    const val LONG_TASK = "long_task"
    const val FROZEN_FRAME = "frozen_frame"
    const val APP_VITAL = "app_vital"

    val ERROR_CLASS = setOf(ERROR, APP_CRASH, NATIVE_CRASH, ANR, "ui_hang")
}
