package io.base14.scout.core

data class ScoutConfig(
    val serviceName: String,
    val endpoint: String,
    val serviceVersion: String? = null,
    val environment: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val resourceAttributes: Map<String, String> = emptyMap(),

    val sessionSampleRate: Double = 1.0,
    val alwaysCaptureErrors: Boolean = true,

    val sessionTimeoutMinutes: Int = 30,
    val maxSessionDurationMinutes: Int = 60,

    val firstPartyHosts: List<String> = emptyList(),
    val ignoreUrlPatterns: List<String> = emptyList(),

    val enableScreenTracking: Boolean = true,
    val enableTapTracking: Boolean = true,
    val enableHttpTracking: Boolean = true,
    val enableErrorTracking: Boolean = true,
    val enableCrashTracking: Boolean = true,
    val enableAnrTracking: Boolean = true,
    val enableJankTracking: Boolean = true,
    val enableLifecycleTracking: Boolean = true,
    val enableStartupTracking: Boolean = true,
    val enableLogging: Boolean = true,
    val enableMetrics: Boolean = true,
    val enableMemoryMetrics: Boolean = false,
    val enableCpuMetrics: Boolean = false,
    val enableFrameMetrics: Boolean = false,

    val exportIntervalSeconds: Int = 30,
    val maxExportBatchSize: Int = 512,
    val maxQueueSize: Int = 2048,
    val maxRetries: Int = 0,
    val metricExportIntervalSeconds: Int? = null,
    val vitalsCollectionIntervalSeconds: Int = 60,

    val offlineBufferEnabled: Boolean = false,
    val offlineMaxTraceItems: Int = 0,
    val offlineMaxMetricItems: Int = 0,
    val offlineMaxLogItems: Int = 0,

    val anrThresholdMs: Long = 5_000,
    val longTaskThresholdMs: Long = 100,
    val frozenFrameThresholdMs: Long = 700,

    val maxOfflineStorageMb: Int = 5,

    val role: ScoutRole = ScoutRole.AUTO,

    val beforeSend: ((name: String, attributes: MutableMap<String, Any>) -> Boolean)? = null,

    val debugLogging: Boolean = false,
) {
    val effectiveExportIntervalSeconds: Int get() = exportIntervalSeconds.coerceAtLeast(1)
    val effectiveMaxExportBatchSize: Int get() = maxExportBatchSize.coerceAtLeast(1)
    val effectiveMaxQueueSize: Int get() = maxQueueSize.coerceAtLeast(1)
    val effectiveMaxRetries: Int get() = maxRetries.coerceAtLeast(0)
    val effectiveVitalsCollectionIntervalSeconds: Int get() = vitalsCollectionIntervalSeconds.coerceAtLeast(1)
    val effectiveMetricExportIntervalSeconds: Int
        get() = (metricExportIntervalSeconds ?: exportIntervalSeconds).coerceAtLeast(1)

    init {
        require(serviceName.isNotBlank()) { "ScoutConfig.serviceName must not be blank" }
        require(endpoint.isNotBlank()) { "ScoutConfig.endpoint must not be blank" }
        require(sessionSampleRate in 0.0..100.0) { "sessionSampleRate must be 0..100" }
    }
}

enum class ScoutRole { AUTO, OWNER, ATTACHED }
