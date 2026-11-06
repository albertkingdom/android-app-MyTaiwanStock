package com.example.mynewsapp.ui.list

import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.example.mynewsapp.R
import com.google.android.material.textfield.TextInputLayout

/**
 * Makes the dialog's positive button validate the name in [field] with [ListNameValidator]: a rejected
 * name shows an error under the field and keeps the dialog open; an accepted, trimmed name is passed
 * to [onValid] and the dialog closes. Call before the dialog is shown.
 */
fun AlertDialog.validateListNameOnConfirm(
    field: TextInputLayout,
    existingNames: () -> List<String>,
    renamingFrom: String?,
    onValid: (name: String) -> Unit,
) {
    setOnShowListener {
        getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val input = field.editText?.text?.toString().orEmpty()
            when (val result = ListNameValidator.validate(input, existingNames(), renamingFrom)) {
                ListNameResult.Blank -> field.error = context.getString(R.string.list_name_blank)
                ListNameResult.Duplicate -> field.error = context.getString(R.string.list_name_duplicate)
                is ListNameResult.Valid -> {
                    onValid(result.name)
                    dismiss()
                }
            }
        }
    }
    field.editText?.doAfterTextChanged { field.error = null }
}
