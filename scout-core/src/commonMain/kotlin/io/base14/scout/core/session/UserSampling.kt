package io.base14.scout.core.session

import io.base14.scout.core.platform.KeyValueStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.concurrent.Volatile

/** Where a session's `sampled` decision came from. */
@Serializable
enum class SamplingSource(val wire: String) {
    /** The app supplied a per-user value (`userSampling`). */
    @SerialName("user")
    USER("user"),

    /** No user value was in effect; the per-session `sessionSampleRate` roll decided. */
    @SerialName("fallback")
    FALLBACK("fallback"),
    ;

    companion object {
        fun fromWire(value: String?): SamplingSource? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The app-supplied per-user sampling value. It is consulted only when a session
 * is created, and never mid-session.
 *
 * - `true`: keep the session.
 * - `false`: drop the session's spans. Logs and metrics still flow.
 * - `null`: no opinion; fall back to the per-session roll at `sessionSampleRate`.
 *
 * The latest value is persisted, so a launch that supplies nothing at init
 * still honours it. A non-null init value wins over the stored one and is
 * persisted itself. A `null` init value means "no opinion" and keeps the
 * stored value.
 */
class UserSampling(
    private val store: KeyValueStore,
    initValue: Boolean?,
) {
    @Volatile
    var pending: Boolean? = initValue ?: read()
        private set

    init {
        if (initValue != null) write(initValue)
    }

    fun set(value: Boolean?) {
        pending = value
        write(value)
    }

    fun resolve(sessionId: String, sampleRatePercent: Double): Pair<Boolean, SamplingSource> {
        val value = pending
        return if (value != null) {
            value to SamplingSource.USER
        } else {
            Sampler.decide(sessionId, sampleRatePercent) to SamplingSource.FALLBACK
        }
    }

    private fun read(): Boolean? = when (store.getString(KEY)) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun write(value: Boolean?) {
        if (value == null) store.remove(KEY) else store.putString(KEY, value.toString())
    }

    companion object {
        const val KEY = "scout.user_sampling"
    }
}

/**
 * Whether a user change should end the current session and start a new one,
 * so the next user never inherits the previous user's sampling decision.
 *
 * Going from anonymous to the first id keeps the session, and so does
 * re-setting the same id. The rotation only happens while user sampling is
 * "in play": the current session was decided by a user value, or a user value
 * is pending. Apps that never use the feature therefore see no extra sessions.
 */
fun shouldRotateOnIdentityChange(
    previousUserId: String?,
    newUserId: String?,
    currentSource: SamplingSource,
    pending: Boolean?,
): Boolean {
    if (previousUserId == null) return false
    if (newUserId == previousUserId) return false
    return currentSource == SamplingSource.USER || pending != null
}
