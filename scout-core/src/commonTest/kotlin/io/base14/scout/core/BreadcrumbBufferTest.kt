package io.base14.scout.core

import io.base14.scout.core.breadcrumb.BreadcrumbBuffer
import io.base14.scout.core.platform.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreadcrumbBufferTest {

    @Test
    fun restoresPreviousSessionTrailForNativeCrash() {
        val store = InMemoryKeyValueStore()
        BreadcrumbBuffer(store = store).add("navigation", "screen: Home")

        val next = BreadcrumbBuffer(store = store)
        assertTrue(next.previousSessionJson.contains("screen: Home"), next.previousSessionJson)
        assertEquals(0, next.snapshot().size)
    }

    @Test
    fun previousSessionJsonIsEmptyArrayWhenNothingPersisted() {
        assertEquals("[]", BreadcrumbBuffer(store = InMemoryKeyValueStore()).previousSessionJson)
    }

    @Test
    fun capsAtMaxFifo() {
        val buffer = BreadcrumbBuffer(max = 3)
        repeat(5) { buffer.add("nav", "m$it") }
        val items = buffer.snapshot()
        assertEquals(3, items.size)
        assertEquals("m2", items.first().message)
        assertEquals("m4", items.last().message)
    }

    @Test
    fun jsonContainsFields() {
        val buffer = BreadcrumbBuffer()
        buffer.add("tap", "hello")
        val json = buffer.toJson()
        assertTrue(json.contains("\"type\":\"tap\""), json)
        assertTrue(json.contains("\"message\":\"hello\""), json)
        assertTrue(json.contains("\"time\":"), json)
    }
}
