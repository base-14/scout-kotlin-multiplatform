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
                core.adoptExternalSessionId(crossCtx.sessionId, crossCtx.sessionStartTime, crossCtx.sampled)
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

    fun adoptExternalSessionId(
        id: String,
        startIso: String,
        sampled: Boolean,
    ) {
        runCatching { core()?.adoptExternalSessionId(id, startIso, sampled) }
    }

    private fun core(): ScoutCore? = Scout.coreInternal()
}
