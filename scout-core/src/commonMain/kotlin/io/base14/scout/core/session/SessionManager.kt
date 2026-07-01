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
)

class SessionManager(
    private val config: ScoutConfig,
    private val store: KeyValueStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = ScoutLock()
    private var session: PersistedSession = restoreOrCreate()
    private var backgroundedAt: Long? = null

    var onSessionChanged: (() -> Unit)? = null

    private fun restoreOrCreate(): PersistedSession {
        val raw = store.getString(KEY)
        if (raw != null) {
            val prev = runCatching { json.decodeFromString<PersistedSession>(raw) }.getOrNull()
            if (prev != null && isResumable(prev)) {
                return prev.copy(lastActive = epochMillis()).also { persist(it) }
            }
            return create(previousId = prev?.id)
        }
        return create(previousId = null)
    }

    private fun isResumable(s: PersistedSession): Boolean {
        val now = epochMillis()
        val withinIdle = now - s.lastActive < config.sessionTimeoutMinutes * 60_000L
        val withinMax = config.maxSessionDurationMinutes == 0 ||
            now - s.startedAt < config.maxSessionDurationMinutes * 60_000L
        return withinIdle && withinMax
    }

    private fun create(previousId: String?): PersistedSession {
        val now = epochMillis()
        val id = randomUuidString()
        val sampled = Sampler.decide(id, config.sessionSampleRate)
        return PersistedSession(id, now, now, sampled, isoUtc(now), previousId)
            .also {
                persist(it)
                onSessionChanged?.invoke()
            }
    }

    private fun persist(s: PersistedSession) {
        store.putString(KEY, json.encodeToString(PersistedSession.serializer(), s))
    }

    fun current(): PersistedSession = lock.withLock {
        if (config.maxSessionDurationMinutes > 0 &&
            epochMillis() - session.startedAt >= config.maxSessionDurationMinutes * 60_000L
        ) {
            session = create(previousId = session.id)
        }
        session
    }

    fun sessionId(): String = current().id
    fun startTimeIso(): String = current().startIso
    fun sampled(): Boolean = current().sampled
    fun previousId(): String? = current().previousId

    fun touch(): Unit = lock.withLock {
        session = session.copy(lastActive = epochMillis())
        persist(session)
    }

    fun onBackground(): Unit = lock.withLock {
        backgroundedAt = epochMillis()
        session = session.copy(lastActive = epochMillis())
        persist(session)
    }

    fun onForeground(): Unit = lock.withLock {
        val bg = backgroundedAt
        if (bg != null && epochMillis() - bg >= config.sessionTimeoutMinutes * 60_000L) {
            session = create(previousId = session.id)
        }
        backgroundedAt = null
        session = session.copy(lastActive = epochMillis())
        persist(session)
    }

    fun adoptExternal(id: String, sampled: Boolean, startIso: String): Unit = lock.withLock {
        val now = epochMillis()
        session = PersistedSession(id, now, now, sampled, startIso, session.id)
        persist(session)
        onSessionChanged?.invoke()
    }

    companion object {
        private const val KEY = "scout.session"
    }
}
