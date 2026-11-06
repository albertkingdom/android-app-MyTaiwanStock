package com.example.mynewsapp.ui.list

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.mynewsapp.R
import com.example.mynewsapp.db.FollowingList
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

class AddFollowingListDialogFragment: DialogFragment() {
    private val listViewModel: ListViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_following_list, null)
        val field = view.findViewById<TextInputLayout>(R.id.outlinedTextField)
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AddFollowingListDialogTheme)
            .setView(view)
            .setTitle("新增追蹤清單")
            // The listener is replaced below so a rejected name keeps the dialog open.
            .setPositiveButton("新增", null)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            .create()
        dialog.validateListNameOnConfirm(
            field = field,
            existingNames = { listViewModel.allFollowingList.value.orEmpty().map { it.listName } },
            renamingFrom = null,
            onValid = ::addList,
        )
        return dialog
    }

    private fun addList(name: String) {
        listViewModel.createFollowingList(FollowingList(followingListId = 0, listName = name))
        listViewModel.uploadListToOnlineDB(listName = name)
    }
}
