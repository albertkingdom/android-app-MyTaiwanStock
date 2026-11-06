package com.example.mynewsapp.ui.list

import org.junit.Assert.assertEquals
import org.junit.Test

class ListNameValidatorTest {

    private val existing = listOf("Default", "長期持有")

    // One test per row of the spec example "Validating a list name".
    @Test
    fun `a new name is accepted`() {
        assertEquals(ListNameResult.Valid("高股息"), ListNameValidator.validate("高股息", existing, renamingFrom = null))
    }

    @Test
    fun `an accepted name is trimmed`() {
        assertEquals(ListNameResult.Valid("高股息"), ListNameValidator.validate("  高股息 ", existing, renamingFrom = null))
    }

    @Test
    fun `a blank name is rejected`() {
        assertEquals(ListNameResult.Blank, ListNameValidator.validate("   ", existing, renamingFrom = null))
    }

    @Test
    fun `an existing name is rejected when adding`() {
        assertEquals(ListNameResult.Duplicate, ListNameValidator.validate("長期持有", existing, renamingFrom = null))
    }

    @Test
    fun `renaming a list to its own name is accepted`() {
        assertEquals(ListNameResult.Valid("長期持有"), ListNameValidator.validate("長期持有", existing, renamingFrom = "長期持有"))
    }

    @Test
    fun `renaming a list to another list's name is rejected`() {
        assertEquals(ListNameResult.Duplicate, ListNameValidator.validate("Default", existing, renamingFrom = "長期持有"))
    }
}
