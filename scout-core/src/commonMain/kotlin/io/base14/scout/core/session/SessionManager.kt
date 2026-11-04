package io.base14.scout.core.session

import io.base14.scout.core.ScoutConfig
import io.base14.scout.core.platform.KeyValueStore
import io.base14.scout.core.platform.ScoutLock
import io.base14.scout.core.platform.epochMillis
import io.base14.scout.core.platform.isoUtc
import io.base14.scout.core.platform.randomUuidString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersistedSession(
    val id: String,
    val startedAt: Long,
    val lastActive: Long,
    val sampled: Boolean,
    val startIso: String,
    val previousId: String? = null,
    val samplingSource: SamplingSource = SamplingSource.FALLBACK,
)

class SessionManager(
    private val config: ScoutConfig,
    private val store: KeyValueStore,
    private val clock: () -> Long = ::epochMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = ScoutLock()
    val userSampling = UserSampling(store, config.userSampling)
    private var session: PersistedSession = restoreOrCreate()
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

    private fun isResumable(s: PersistedSession): Boolean {
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
            if (config.maxSessionDurationMinutes > 0 &&
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
        lock.withLock { session = create(previousId = session.id) }
        onSessionChanged?.invoke()
    }

    fun touch(): Unit = lock.withLock {
        session = session.copy(lastActive = clock())
        persist(session)
    }

    fun onBackground(): Unit = lock.withLock {
        backgroundedAt = clock()
        session = session.copy(lastActive = clock())
        persist(session)
    }

    fun onForeground() {
        var changed = false
        lock.withLock {
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
            val now = clock()
            session = PersistedSession(id, now, now, sampled, startIso, session.id, source)
            persist(session)
        }
        onSessionChanged?.invoke()
    }

    companion object {
        private const val KEY = "scout.session"
    }
}
