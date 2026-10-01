package com.tavuno.tv.ui.screens.customize

import com.tavuno.tv.data.model.CustomizableItem
import com.tavuno.tv.data.model.CustomizationItem
import com.tavuno.tv.data.model.CustomizationSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the client's copy of the backend's ordering rules, because the two must agree: a screen
 * that saved order the catalog then re-sorted would look like the save silently failed.
 */
class CustomizeItemsTest {

    private fun item(id: Int, name: String = "Item $id", hidden: Boolean = false) =
        CustomizableItem(id = id, name = name, logoUrl = null, isHidden = hidden)

    private fun items(vararg ids: Int) = ids.map { item(it) }

    private fun overrides(vararg entries: Pair<Int, Int>, hidden: Set<Int> = emptySet()) =
        CustomizationSet(
            kind = "live_channel",
            items = entries.map { (id, order) ->
                CustomizationItem(itemId = id, sortOrder = order, isHidden = false)
            } + hidden.map { CustomizationItem(itemId = it, sortOrder = 0, isHidden = true) }
        )

    // --- seeding ---

    @Test
    fun `an untouched profile keeps the catalog order`() {
        val catalog = items(1, 2, 3)
        assertEquals(catalog, CustomizeItems.seed(catalog, CustomizationSet("live_channel")))
    }

    @Test
    fun `pinned items float to the front in override order`() {
        val seeded = CustomizeItems.seed(
            items(1, 2, 3, 4),
            overrides(3 to 0, 1 to 1),
        )
        assertEquals(listOf(3, 1, 2, 4), seeded.map { it.id })
    }

    @Test
    fun `hidden items sink to the bottom and keep their flag`() {
        val seeded = CustomizeItems.seed(
            items(1, 2, 3),
            overrides(hidden = setOf(2)),
        )
        assertEquals(listOf(1, 3, 2), seeded.map { it.id })
        assertTrue(seeded.last().isHidden)
        assertFalse(seeded.first().isHidden)
    }

    @Test
    fun `hidden wins over pinned for the same item`() {
        // Same rule as build_overrides: a row that is both pinned and hidden only hides, so
        // it cannot consume a sort slot while being invisible.
        val seeded = CustomizeItems.seed(items(1, 2, 3), overrides(2 to 0, hidden = setOf(2)))
        assertEquals(listOf(1, 3, 2), seeded.map { it.id })
        assertTrue(seeded.last().isHidden)
    }

    @Test
    fun `an override for a channel the catalog no longer lists is ignored`() {
        val seeded = CustomizeItems.seed(items(1, 2), overrides(99 to 0, 2 to 1))
        assertEquals(listOf(2, 1), seeded.map { it.id })
    }

    // --- moves ---

    @Test
    fun `move up swaps with the item above`() {
        assertEquals(listOf(1, 3, 2), CustomizeItems.moveUp(items(1, 2, 3), 2).map { it.id })
    }

    @Test
    fun `move down swaps with the item below`() {
        assertEquals(listOf(2, 1, 3), CustomizeItems.moveDown(items(1, 2, 3), 0).map { it.id })
    }

    @Test
    fun `moving past either end changes nothing`() {
        val list = items(1, 2, 3)
        assertEquals(list, CustomizeItems.moveUp(list, 0))
        assertEquals(list, CustomizeItems.moveDown(list, 2))
        assertEquals(list, CustomizeItems.moveUp(list, 9))
        assertEquals(list, CustomizeItems.moveDown(list, -1))
    }

    // --- visibility ---

    @Test
    fun `hide moves the item to the bottom and flags it`() {
        val result = CustomizeItems.toggleHidden(items(1, 2, 3), 0)
        assertEquals(listOf(2, 3, 1), result.map { it.id })
        assertTrue(result.last().isHidden)
        assertEquals(1, CustomizeItems.hiddenCount(result))
    }

    @Test
    fun `show returns the item to the visible block and clears the flag`() {
        val hiddenFirst = CustomizeItems.toggleHidden(items(1, 2, 3), 0)
        val restored = CustomizeItems.toggleHidden(hiddenFirst, 2)
        // Hide sank it to the bottom; Show brings it back to the end of what is visible.
        assertEquals(listOf(2, 3, 1), restored.map { it.id })
        assertTrue(restored.none { it.isHidden })
    }

    @Test
    fun `toggling an out-of-range row changes nothing`() {
        val list = items(1, 2)
        assertEquals(list, CustomizeItems.toggleHidden(list, 5))
    }

    // --- payload ---

    @Test
    fun `payload pins every visible item at its position`() {
        val payload = CustomizeItems.toPayload(items(5, 3, 9))
        assertEquals(
            listOf(
                CustomizationItem(itemId = 5, sortOrder = 0, isHidden = false),
                CustomizationItem(itemId = 3, sortOrder = 1, isHidden = false),
                CustomizationItem(itemId = 9, sortOrder = 2, isHidden = false),
            ),
            payload.items,
        )
    }

    @Test
    fun `payload carries hidden items so the next save cannot resurrect them`() {
        val edited = CustomizeItems.toggleHidden(items(1, 2, 3), 1)
        val payload = CustomizeItems.toPayload(edited)
        assertEquals(3, payload.items.size)
        assertTrue(payload.items.single { it.itemId == 2 }.isHidden)
    }

    @Test
    fun `a seeded list round trips through the payload unchanged`() {
        val seeded = CustomizeItems.seed(items(1, 2, 3, 4), overrides(4 to 0, hidden = setOf(1)))
        val payload = CustomizeItems.toPayload(seeded)
        // Re-seeding from the payload's own set must reproduce the same order and flags —
        // this is what proves Save does not shuffle the rail behind the viewer's back.
        assertEquals(seeded, CustomizeItems.seed(items(1, 2, 3, 4), CustomizationSet("live_channel", payload.items)))
    }
}