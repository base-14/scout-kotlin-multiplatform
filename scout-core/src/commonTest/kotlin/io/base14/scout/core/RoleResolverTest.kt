package io.base14.scout.core

import io.base14.scout.core.bridge.InMemoryOwnerRegistry
import io.base14.scout.core.bridge.OwnerRecord
import io.base14.scout.core.bridge.Resolution
import io.base14.scout.core.bridge.RoleResolver
import io.base14.scout.core.bridge.SessionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoleResolverTest {

    private var counter = 0
    private fun mint(owner: String): () -> SessionContext = {
        SessionContext("session-$owner-${counter++}", "t", "100.0", true, "anon-$owner")
    }

    @Test
    fun firstToInitBecomesOwner() {
        val reg = InMemoryOwnerRegistry()
        val r = RoleResolver.resolve(ScoutRole.AUTO, reg, OwnerRecord.OWNER_NATIVE, mintContext = mint("native"))
        assertTrue(r is Resolution.Owner)
        assertEquals(OwnerRecord.OWNER_NATIVE, reg.read()?.owner)
    }

    @Test
    fun secondToInitAttachesToTheSameSession() {
        val reg = InMemoryOwnerRegistry()
        val first = RoleResolver.resolve(ScoutRole.AUTO, reg, OwnerRecord.OWNER_NATIVE, mintContext = mint("native"))
        val second = RoleResolver.resolve(ScoutRole.AUTO, reg, OwnerRecord.OWNER_FLUTTER, mintContext = mint("flutter"))
        assertTrue(first is Resolution.Owner)
        assertTrue(second is Resolution.Attached)
        assertEquals((first as Resolution.Owner).context.sessionId, (second as Resolution.Attached).context.sessionId)
    }

    @Test
    fun flutterFirstMeansFlutterOwnsAndNativeAttaches() {
        val reg = InMemoryOwnerRegistry()
        val flutter = RoleResolver.resolve(ScoutRole.AUTO, reg, OwnerRecord.OWNER_FLUTTER, mintContext = mint("flutter"))
        val native = RoleResolver.resolve(ScoutRole.AUTO, reg, OwnerRecord.OWNER_NATIVE, mintContext = mint("native"))
        assertTrue(flutter is Resolution.Owner)
        assertTrue(native is Resolution.Attached)
        assertEquals(OwnerRecord.OWNER_FLUTTER, reg.read()?.owner)
    }

    @Test
    fun attachedRoleWithNoOwnerYetIsPending() {
        val reg = InMemoryOwnerRegistry()
        val r = RoleResolver.resolve(ScoutRole.ATTACHED, reg, OwnerRecord.OWNER_FLUTTER, mintContext = mint("flutter"))
        assertEquals(Resolution.Pending, r)
        assertEquals(null, reg.read())
    }

    @Test
    fun pendingGuestAttachesOnceOwnerAppears() {
        val reg = InMemoryOwnerRegistry()
        assertEquals(Resolution.Pending, RoleResolver.resolve(ScoutRole.ATTACHED, reg, OwnerRecord.OWNER_FLUTTER, mintContext = mint("flutter")))
        RoleResolver.resolve(ScoutRole.AUTO, reg, OwnerRecord.OWNER_NATIVE, mintContext = mint("native"))
        val retry = RoleResolver.resolve(ScoutRole.ATTACHED, reg, OwnerRecord.OWNER_FLUTTER, mintContext = mint("flutter"))
        assertTrue(retry is Resolution.Attached)
        assertTrue(retry.context.sessionId.startsWith("session-native"))
    }

    @Test
    fun forcedOwnerCannotDisplaceASittingOwner() {
        val reg = InMemoryOwnerRegistry()
        RoleResolver.resolve(ScoutRole.OWNER, reg, OwnerRecord.OWNER_NATIVE, mintContext = mint("native"))
        val late = RoleResolver.resolve(ScoutRole.OWNER, reg, OwnerRecord.OWNER_FLUTTER, mintContext = mint("flutter"))
        assertTrue(late is Resolution.Attached)
        assertEquals(OwnerRecord.OWNER_NATIVE, reg.read()?.owner)
    }
}
