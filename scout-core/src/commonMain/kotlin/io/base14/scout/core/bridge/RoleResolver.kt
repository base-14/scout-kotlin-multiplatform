package io.base14.scout.core.bridge

import io.base14.scout.core.ScoutRole

sealed class Resolution {
    data class Owner(val context: SessionContext) : Resolution()

    data class Attached(val context: SessionContext) : Resolution()

    object Pending : Resolution()
}

object RoleResolver {
    fun resolve(
        role: ScoutRole,
        registry: OwnerRegistry,
        self: String,
        protocol: Int = BridgeCodec.PROTOCOL_VERSION,
        mintContext: () -> SessionContext,
    ): Resolution {
        registry.read()?.takeIf { it.isOwned }?.let {
            return Resolution.Attached(it.context!!)
        }
        return when (role) {
            ScoutRole.ATTACHED -> Resolution.Pending
            ScoutRole.AUTO, ScoutRole.OWNER -> {
                val context = mintContext()
                if (registry.claim(OwnerRecord(self, protocol, context))) {
                    Resolution.Owner(context)
                } else {
                    registry.read()?.context?.let { Resolution.Attached(it) } ?: Resolution.Pending
                }
            }
        }
    }
}
