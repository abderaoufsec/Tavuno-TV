package com.tavuno.tv.ui.screens.customize

import com.tavuno.tv.data.model.CustomizableItem
import com.tavuno.tv.data.model.CustomizationItem
import com.tavuno.tv.data.model.CustomizationPayload
import com.tavuno.tv.data.model.CustomizationSet

/**
 * The decision half of customization (Slice D), mirroring the backend's
 * `app/customize/ordering.py` so the screen and the API can never disagree.
 *
 * The screen edits a plain ordered list of [CustomizableItem]s and serializes the whole list on
 * save; nothing here touches the network or Compose, so the rules that matter — hidden items
 * sort last, positions become `sort_order`, an out-of-range move is a no-op — are unit-tested
 * rather than eyeballed on a TV.
 */
object CustomizeItems {

    /**
     * Seed the editable list from the catalog and the stored overrides.
     *
     * Mirrors `ORDER BY is_hidden, sort_order, item_id` on the read side: visible items first
     * in override order, then everything the profile never touched in catalog order, with
     * hidden items pushed to the bottom. An untouched profile therefore yields exactly the
     * catalog order.
     */
    fun seed(items: List<CustomizableItem>, overrides: CustomizationSet): List<CustomizableItem> {
        val hidden = overrides.items.filter { it.isHidden }.map { it.itemId }.toSet()
        val order = overrides.items
            .filterNot { it.isHidden }
            .associate { it.itemId to it.sortOrder }

        val visible = items.filterNot { it.id in hidden }
        val pinned = visible.filter { it.id in order }.sortedBy { order.getValue(it.id) }
        val unpinned = visible.filterNot { it.id in order }

        return pinned + unpinned + items.filter { it.id in hidden }.map { it.copy(isHidden = true) }
    }

    /** Move the item at [index] one place up. Out-of-range and already-first are no-ops. */
    fun moveUp(items: List<CustomizableItem>, index: Int): List<CustomizableItem> =
        if (index <= 0 || index >= items.size) items else swap(items, index, index - 1)

    /** Move the item at [index] one place down. Out-of-range and already-last are no-ops. */
    fun moveDown(items: List<CustomizableItem>, index: Int): List<CustomizableItem> =
        if (index < 0 || index >= items.size - 1) items else swap(items, index, index + 1)

    /**
     * Flip one item's visibility.
     *
     * Hiding moves the item to the bottom, so "hidden" reads as one place in the list
     * instead of an invisible gap the viewer cannot explain. Showing puts it back at the
     * end of the visible block, clear of everything already hidden.
     */
    fun toggleHidden(items: List<CustomizableItem>, index: Int): List<CustomizableItem> {
        if (index !in items.indices) return items
        val item = items[index]
        val moved = items.toMutableList().apply { removeAt(index) }
        if (item.isHidden) {
            // Back into the visible block, never appended past whatever is still hidden.
            val visibleCount = moved.count { !it.isHidden }
            moved.add(minOf(index, visibleCount), item.copy(isHidden = false))
        } else {
            moved.add(item.copy(isHidden = true))
        }
        return moved.toList()
    }

    private fun swap(items: List<CustomizableItem>, from: Int, to: Int): List<CustomizableItem> {
        val copy = items.toMutableList()
        copy[from] = copy[to]
        copy[to] = items[from]
        return copy
    }

    /**
     * Serialize the edited list as the replace-all payload.
     *
     * Every visible item is pinned at its own position, so the order the viewer built is the
     * order the catalog reads — an unpinned item would silently keep the catalog's natural
     * ordering and the rail would not match the screen. Hidden items are sent too, otherwise
     * the next save would resurrect them.
     */
    fun toPayload(items: List<CustomizableItem>): CustomizationPayload =
        CustomizationPayload(
            items = items.mapIndexed { index, item ->
                CustomizationItem(
                    itemId = item.id,
                    sortOrder = index,
                    isHidden = item.isHidden,
                )
            }
        )

    /** How many items are currently hidden — shown next to Save as a reminder of the stakes. */
    fun hiddenCount(items: List<CustomizableItem>): Int = items.count { it.isHidden }
}