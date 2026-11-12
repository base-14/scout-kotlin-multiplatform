package io.base14.scout.android

import io.base14.scout.core.ScoutCore
import io.base14.scout.core.ScoutRole
import io.base14.scout.core.bridge.BridgeCodec
import io.base14.scout.core.bridge.InMemoryOwnerRegistry
import io.base14.scout.core.bridge.OwnerRecord
import io.base14.scout.core.bridge.Resolution
import io.base14.scout.core.bridge.RoleResolver

object ScoutBridge {
    val registry = InMemoryOwnerRegistry()

    /** True when this process adopted another process's session (non-main process). */
    @Volatile
    internal var adoptedCrossProcess: Boolean = false
        private set

    @Volatile
    private var ownerChangedListener: ((String) -> Unit)? = null

    fun setOwnerChangedListener(listener: ((String) -> Unit)?) {
        ownerChangedListener = listener
    }

    internal fun resolveOnInit(
        appContext: android.content.Context,
        core: ScoutCore,
        role: ScoutRole,
    ): Resolution {
        if (role != ScoutRole.OWNER &&
            !io.base14.scout.android.internal.CrossProcessOwner.isMainProcess(appContext)
        ) {
            val crossCtx =
                io.base14.scout.android.internal.CrossProcessOwner.queryContext(appContext)
                    ?.let { BridgeCodec.decodeContext(it) }
            if (crossCtx != null) {
                core.adoptExternalSessionId(
                    crossCtx.sessionId,
                    crossCtx.sessionStartTime,
                    crossCtx.sampled,
                    crossCtx.samplingSource,
                )
                adoptedCrossProcess = true
                return Resolution.Attached(crossCtx)
            }
        }
        val resolution = RoleResolver.resolve(role, registry, OwnerRecord.OWNER_NATIVE) { core.bridgeContext() }
        if (resolution is Resolution.Owner) {
            core.onBridgeContextChanged = { ctx ->
                runCatching {
                    registry.update(OwnerRecord(OwnerRecord.OWNER_NATIVE, BridgeCodec.PROTOCOL_VERSION, ctx))
                    ownerChangedListener?.invoke(BridgeCodec.encodeContext(ctx))
                }
            }
        }
        return resolution
    }

    fun readOwner(): String? = runCatching { registry.read()?.let { BridgeCodec.encodeOwner(it) } }.getOrNull()

    fun context(): String? = runCatching { core()?.let { BridgeCodec.encodeContext(it.bridgeContext()) } }.getOrNull()

    fun ingestSpans(payloadJson: String) {
        runCatching { core()?.ingestForwardedSpans(payloadJson) }
    }

    fun ingestLogs(payloadJson: String) {
        runCatching { core()?.ingestForwardedLogs(payloadJson) }
    }

    fun ingestMetrics(payloadJson: String) {
        runCatching { core()?.ingestForwardedMetrics(payloadJson) }
    }

    fun pushBreadcrumbs(payloadJson: String) {
        runCatching { core()?.mergeBreadcrumbs(BridgeCodec.decodeBreadcrumbs(payloadJson)) }
    }

    fun setBreadcrumbs(payloadJson: String) {
        runCatching { core()?.replaceBreadcrumbs(BridgeCodec.decodeBreadcrumbsArray(payloadJson)) }
    }

    @JvmOverloads
    fun adoptExternalSessionId(
        id: String,
        startIso: String,
        sampled: Boolean,
        samplingSource: String? = null,
    ) {
        runCatching { core()?.adoptExternalSessionId(id, startIso, sampled, samplingSource) }
    }

    /**
     * Entry point for attached layers (e.g. scout-flutter) to forward the app's
     * per-user sampling value to the session owner. It applies from the next
     * session start. In a non-main process that adopted the main process's
     * session it is a no-op, because that process follows the owner's decision.
     */
    fun setUserSampling(value: Boolean?) {
        val core = core() ?: return
        if (adoptedCrossProcess) {
            if (core.config.debugLogging) println("SCOUTDBG setUserSampling ignored in non-main process")
            return
        }
        runCatching { core.setUserSampling(value) }
    }

    /**
     * Entry point for attached layers (e.g. scout-flutter) to forward the app's
     * runtime kill switch. Unlike [setUserSampling] it also applies in a
     * non-main process: each process decides its own `enabled`. A call before
     * `Scout.initialize` is held and applied at init.
     */
    fun setEnabled(value: Boolean) {
        runCatching { Scout.setEnabled(value) }
    }

    /** Whether the native engine's RUM is on (false before init). */
    fun isEnabled(): Boolean = Scout.isEnabled

    private fun core(): ScoutCore? = Scout.coreInternal()
}
