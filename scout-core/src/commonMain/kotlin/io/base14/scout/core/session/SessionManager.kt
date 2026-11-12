package io.base14.scout.core.session

import io.base14.scout.core.ScoutConfig
import io.base14.scout.core.platform.KeyValueStore
import io.base14.scout.core.platform.ScoutLock
import io.base14.scout.core.platform.epochMillis
import io.base14.scout.core.platform.isoUtc
import io.base14.scout.core.platform.randomUuidString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

@Serializable
data class PersistedSession(
    val id: String,
    val startedAt: Long,
    val lastActive: Long,
    val sampled: Boolean,
    val startIso: String,
    val previousId: String? = null,
    val samplingSource: SamplingSource = SamplingSource.FALLBACK,
    /** Set when RUM was turned off mid-session; an ended session is never resumed. */
    val ended: Boolean = false,
)

class SessionManager(
    private val config: ScoutConfig,
    private val store: KeyValueStore,
    private val clock: () -> Long = ::epochMillis,
    enabled: Boolean = config.enabled,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = ScoutLock()
    val userSampling = UserSampling(store, config.userSampling)

    /**
     * While false there is no live session: nothing is created, resumed,
     * rotated, touched or persisted. [sessionId] keeps returning the last known
     * id (or "" if there never was one), which callers must not export.
     */
    @Volatile
    var isEnabled: Boolean = enabled
        private set

    private var session: PersistedSession = if (enabled) restoreOrCreate() else peekPersisted()
    private var backgroundedAt: Long? = null

    var onSessionChanged: (() -> Unit)? = null

    private fun restoreOrCreate(): PersistedSession {
        val raw = store.getString(KEY)
        if (raw != null) {
            val prev = runCatching { json.decodeFromString<PersistedSession>(raw) }.getOrNull()
            if (prev != null && isResumable(prev)) {
                return prev.copy(lastActive = clock()).also { persist(it) }
            }
            return create(previousId = prev?.id)
        }
        return create(previousId = null)
    }

    /** Reads the persisted session without extending it, for a launch that starts disabled. */
    private fun peekPersisted(): PersistedSession =
        store.getString(KEY)?.let { raw -> runCatching { json.decodeFromString<PersistedSession>(raw) }.getOrNull() }
            ?: PersistedSession(id = "", startedAt = 0L, lastActive = 0L, sampled = false, startIso = "", ended = true)

    private fun isResumable(s: PersistedSession): Boolean {
        if (s.ended) return false
        val now = clock()
        val withinIdle = now - s.lastActive < config.sessionTimeoutMinutes * 60_000L
        val withinMax = config.maxSessionDurationMinutes == 0 ||
            now - s.startedAt < config.maxSessionDurationMinutes * 60_000L
        return withinIdle && withinMax
    }

    private fun create(previousId: String?): PersistedSession {
        val now = clock()
        val id = randomUuidString()
        val (sampled, source) = userSampling.resolve(id, config.sessionSampleRate)
        if (config.debugLogging) println("SCOUTDBG session id=$id sampled=$sampled source=${source.wire}")
        return PersistedSession(id, now, now, sampled, isoUtc(now), previousId, source)
            .also { persist(it) }
    }

    private fun persist(s: PersistedSession) {
        store.putString(KEY, json.encodeToString(PersistedSession.serializer(), s))
    }

    fun current(): PersistedSession {
        var changed = false
        val result = lock.withLock {
            if (isEnabled &&
                config.maxSessionDurationMinutes > 0 &&
                clock() - session.startedAt >= config.maxSessionDurationMinutes * 60_000L
            ) {
                session = create(previousId = session.id)
                changed = true
            }
            session
        }
        if (changed) onSessionChanged?.invoke()
        return result
    }

    fun sessionId(): String = current().id
    fun startTimeIso(): String = current().startIso
    fun sampled(): Boolean = current().sampled
    fun previousId(): String? = current().previousId
    fun samplingSource(): SamplingSource = current().samplingSource

    /**
     * Logs and metrics are dropped only for sessions sampled out by the
     * fallback roll. A user-"no" session still sends them; only its spans
     * are dropped.
     */
    fun allowsLogsAndMetrics(): Boolean =
        current().let { it.sampled || it.samplingSource == SamplingSource.USER }

    /** Ends the current session and starts a new one, which resolves its sampling decision again. */
    fun rotate() {
        val rotated = lock.withLock {
            if (isEnabled) session = create(previousId = session.id)
            isEnabled
        }
        if (rotated) onSessionChanged?.invoke()
    }

    /**
     * Turns sessions off: the current session ends (persisted as ended, so no
     * later launch resumes it) and nothing is created or extended until
     * [enable]. Returns false if already disabled.
     */
    fun disable(): Boolean {
        val changed = lock.withLock {
            if (!isEnabled) return@withLock false
            isEnabled = false
            backgroundedAt = null
            if (session.id.isNotEmpty()) {
                session = session.copy(lastActive = clock(), ended = true)
                persist(session)
            }
            true
        }
        if (changed) onSessionChanged?.invoke()
        return changed
    }

    /**
     * Turns sessions back on with a fresh session (new id, sampling resolved
     * like any new session, `previous_id` linked to the last known one).
     * Returns false if already enabled.
     */
    fun enable(): Boolean {
        val changed = lock.withLock {
            if (isEnabled) return@withLock false
            session = create(previousId = session.id.takeIf { it.isNotEmpty() })
            backgroundedAt = null
            isEnabled = true
            true
        }
        if (changed) onSessionChanged?.invoke()
        return changed
    }

    fun touch(): Unit = lock.withLock {
        if (!isEnabled) return@withLock
        session = session.copy(lastActive = clock())
        persist(session)
    }

    fun onBackground(): Unit = lock.withLock {
        if (!isEnabled) return@withLock
        backgroundedAt = clock()
        session = session.copy(lastActive = clock())
        persist(session)
    }

    fun onForeground() {
        var changed = false
        lock.withLock {
            if (!isEnabled) return@withLock
            val bg = backgroundedAt
            if (bg != null && clock() - bg >= config.sessionTimeoutMinutes * 60_000L) {
                session = create(previousId = session.id)
                changed = true
            }
            backgroundedAt = null
            session = session.copy(lastActive = clock())
            persist(session)
        }
        if (changed) onSessionChanged?.invoke()
    }

    fun adoptExternal(
        id: String,
        sampled: Boolean,
        startIso: String,
        source: SamplingSource = SamplingSource.FALLBACK,
    ) {
        lock.withLock {
            // A disabled engine has no session to replace; a later enable starts its own.
            if (!isEnabled) return@withLock false
            val now = clock()
            session = PersistedSession(id, now, now, sampled, startIso, session.id, source)
            persist(session)
            true
        }.let { adopted -> if (adopted) onSessionChanged?.invoke() }
    }

    companion object {
        private const val KEY = "scout.session"
    }
}
