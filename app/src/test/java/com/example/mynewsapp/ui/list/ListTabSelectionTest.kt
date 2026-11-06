package com.example.mynewsapp.ui.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListTabSelectionTest {

    @Test
    fun `a saved position inside the tabs is kept`() {
        assertEquals(0, ListTabSelection.selectedPosition(savedPosition = 0, listCount = 3))
        assertEquals(2, ListTabSelection.selectedPosition(savedPosition = 2, listCount = 3))
    }

    @Test
    fun `a saved position past the end selects the last tab`() {
        assertEquals(2, ListTabSelection.selectedPosition(savedPosition = 3, listCount = 3))
    }

    @Test
    fun `a negative saved position selects the first tab`() {
        assertEquals(0, ListTabSelection.selectedPosition(savedPosition = -1, listCount = 3))
    }

    @Test
    fun `no lists means no tab`() {
        assertNull(ListTabSelection.selectedPosition(savedPosition = 0, listCount = 0))
    }
}
