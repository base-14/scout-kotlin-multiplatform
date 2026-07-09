package io.base14.scout.android.instrumentation

import io.base14.scout.android.internal.CurrentScreen
import io.base14.scout.core.ScoutCore
import io.base14.scout.core.platform.epochMillis
import io.base14.scout.core.platform.isoUtc
import io.base14.scout.core.platform.randomUuidString
import io.base14.scout.core.semantics.ScoutAttributes
import io.base14.scout.core.semantics.ScoutSpans

internal class CrashInstrumentation(private val core: ScoutCore) {
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { report(throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun report(t: Throwable) {
        val type = t::class.qualifiedName ?: "Throwable"
        val message = t.message?.takeIf { it.isNotBlank() } ?: type
        val stack = t.stackTraceToString()
        val attrs =
            buildMap<String, Any> {
                putAll(core.sessionIdentityAttrs())
                put(ScoutAttributes.ERROR_TYPE, type)
                put(ScoutAttributes.ERROR_MESSAGE, message)
                put(ScoutAttributes.ERROR_STACK_TRACE, stack)
                put(ScoutAttributes.ERROR_FINGERPRINT, crashFingerprint(type, message, stack))
                put(ScoutAttributes.ERROR_TIME_SINCE_APP_START_MS, core.msSinceInit().toString())
                put(ScoutAttributes.CRASH_PREVIOUS_SESSION_ID, core.sessionId)
                put(ScoutAttributes.CRASH_TIMESTAMP, isoUtc(epochMillis()))
                put(ScoutAttributes.CRASH_STATUS, "started")
                put(ScoutAttributes.CRASH_LAST_SCREEN, CurrentScreen.name ?: "")
                put(ScoutAttributes.BREADCRUMBS, core.breadcrumbs.toJson())
            }
        runCatching { core.persistCrash(attrs) }
    }

    companion object {
        fun reportHandled(
            core: ScoutCore,
            t: Throwable,
            handled: Boolean,
        ) {
            core.emit(
                ScoutSpans.ERROR,
                mapOf(
                    ScoutAttributes.ERROR_ID to randomUuidString(),
                    ScoutAttributes.ERROR_TYPE to (t::class.qualifiedName ?: "Throwable"),
                    ScoutAttributes.ERROR_MESSAGE to (t.message?.takeIf { it.isNotBlank() } ?: (t::class.qualifiedName ?: "Throwable")),
                    ScoutAttributes.ERROR_STACK_TRACE to t.stackTraceToString(),
                    ScoutAttributes.ERROR_HANDLED to handled.toString(),
                    ScoutAttributes.ERROR_HANDLING to if (handled) "handled" else "unhandled",
                    ScoutAttributes.ERROR_SOURCE_TYPE to "android",
                    ScoutAttributes.ERROR_FINGERPRINT to
                        crashFingerprint(
                            t::class.qualifiedName ?: "Throwable",
                            t.message ?: "",
                            t.stackTraceToString(),
                        ),
                    ScoutAttributes.ERROR_TIME_SINCE_APP_START_MS to core.msSinceInit().toString(),
                    ScoutAttributes.BREADCRUMBS to core.breadcrumbs.toJson(),
                ),
                errorMessage = t.message ?: t::class.simpleName ?: "error",
            )
        }

        fun crashFingerprint(
            type: String,
            message: String,
            stack: String,
        ): String {
            val firstFrame = stack.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("at ") } ?: ""
            val hash = "$type|$message|$firstFrame".hashCode().toLong() and 0xFFFFFFFFL
            return hash.toString(16).padStart(8, '0')
        }
    }
}
