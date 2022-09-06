package com.example.mynewsapp.ui.list

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.mynewsapp.R
import com.example.mynewsapp.db.FollowingList
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddFollowingListDialogFragment: DialogFragment() {
    private val listViewModel: ListViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.AddFollowingListDialogTheme)

        return builder.setView(R.layout.dialog_add_following_list)
            .setTitle("新增追蹤清單")
            .setPositiveButton("新增", DialogInterface.OnClickListener { dialog, id ->
                val newListName = (dialog as Dialog).findViewById<EditText>(R.id.edit_text_new_list_name)?.text.toString()
                val newFollowingList = FollowingList(followingListId = 0, listName = newListName)
                listViewModel.createFollowingList(newFollowingList)

                listViewModel.uploadListToOnlineDB(listName = newListName)
//                dismiss()
            })
            .setNegativeButton("Cancel", DialogInterface.OnClickListener { dialog, id ->
                dialog.cancel()
            })
            .create()
    }



}