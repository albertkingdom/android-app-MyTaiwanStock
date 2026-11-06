package com.example.mynewsapp.ui.list

sealed interface ListNameResult {
    /** [name] is the trimmed name to save. */
    data class Valid(val name: String) : ListNameResult
    data object Blank : ListNameResult
    data object Duplicate : ListNameResult
}

/**
 * Checks a list name from the add-list and rename dialogs. Lists are kept in the online database by
 * name, so two lists must not share one.
 */
object ListNameValidator {
    /** [renamingFrom] is the current name of the list being renamed, or null when adding a list. */
    fun validate(input: String, existingNames: List<String>, renamingFrom: String?): ListNameResult {
        val name = input.trim()
        if (name.isEmpty()) return ListNameResult.Blank
        val takenByAnother = existingNames.any { it.trim() == name && it != renamingFrom }
        return if (takenByAnother) ListNameResult.Duplicate else ListNameResult.Valid(name)
    }
}
