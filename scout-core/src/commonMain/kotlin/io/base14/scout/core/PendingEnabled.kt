package io.base14.scout.core

import kotlin.concurrent.Volatile

/**
 * Holds a `setEnabled` call made before the engine is initialised. At init it
 * is applied to the config, where it wins over `ScoutConfig.enabled`, and then
 * cleared. Platform entry points (scout-android `Scout`, scout-ios
 * `ScoutEngine`) each keep one.
 */
class PendingEnabled {
    @Volatile
    var value: Boolean? = null
        private set

    fun hold(enabled: Boolean) {
        value = enabled
    }

    /** Returns [config] with the held value applied, and clears it. */
    fun applyTo(config: ScoutConfig): ScoutConfig {
        val held = value ?: return config
        value = null
        return config.copy(enabled = held)
    }
}
