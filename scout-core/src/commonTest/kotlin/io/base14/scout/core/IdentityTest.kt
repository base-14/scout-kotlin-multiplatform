package io.base14.scout.core

import io.base14.scout.core.identity.Identity
import io.base14.scout.core.platform.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentityTest {
    @Test
    fun anonymousIdPersistsAcrossInstances() {
        val store = InMemoryKeyValueStore()
        assertEquals(Identity(store).anonymousId, Identity(store).anonymousId)
    }

    @Test
    fun setUserHardPrefixesAttributes() {
        val id = Identity(InMemoryKeyValueStore())
        id.setUser("u1", mapOf("email" to "x@y.z", "user.plan" to "pro"))
        assertEquals("u1", id.userId)
        assertEquals("x@y.z", id.userAttributes["user.email"])
        assertEquals("pro", id.userAttributes["user.plan"])
    }

    @Test
    fun clearUserKeepsAnonymous() {
        val store = InMemoryKeyValueStore()
        val id = Identity(store)
        val anon = id.anonymousId
        id.setUser("u1", mapOf("email" to "x"))
        id.clearUser()
        assertNull(id.userId)
        assertTrue(id.userAttributes.isEmpty())
        assertEquals(anon, id.anonymousId)
    }
}
