package com.findora.app.core.domain.compare

/** What happened when a listing was offered to the compare selection. */
enum class CompareAddResult {
    Added,
    AlreadyAdded,

    /** The selection already holds [CompareSelection.MAX_ITEMS] listings; nothing changed. */
    Full,
}

/** Pure rules of the compare selection: ordered, no duplicates, at most [MAX_ITEMS]. */
object CompareSelection {
    /** Three columns still fit side by side on a phone in portrait. */
    const val MAX_ITEMS = 3

    fun add(
        selection: List<String>,
        propertyId: String,
    ): Pair<List<String>, CompareAddResult> =
        when {
            propertyId in selection -> selection to CompareAddResult.AlreadyAdded
            selection.size >= MAX_ITEMS -> selection to CompareAddResult.Full
            else -> (selection + propertyId) to CompareAddResult.Added
        }

    fun move(
        selection: List<String>,
        fromIndex: Int,
        toIndex: Int,
    ): List<String> {
        if (fromIndex !in selection.indices) return selection
        val target = toIndex.coerceIn(selection.indices)
        return if (target == fromIndex) {
            selection
        } else {
            selection.toMutableList().apply { add(target, removeAt(fromIndex)) }
        }
    }
}
