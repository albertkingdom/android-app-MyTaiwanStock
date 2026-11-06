package com.example.mynewsapp.ui.list

/** Which watchlist tab to select for a saved position, which can be stale after lists change. */
object ListTabSelection {
    /** The saved position clamped to the tabs; null when there is no list (and so no tab). */
    fun selectedPosition(savedPosition: Int, listCount: Int): Int? =
        if (listCount <= 0) null else savedPosition.coerceIn(0, listCount - 1)
}
