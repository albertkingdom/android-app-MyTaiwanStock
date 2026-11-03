package com.example.mynewsapp.ui.list

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import com.example.mynewsapp.stocklist.StockSearch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout


class AddStockDialogFragment : DialogFragment() {
    /**
     * this dialog is for adding new stockNo
     */
    private val listViewModel: ListViewModel by activityViewModels()


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val stockListRepository = (requireActivity().application as MyApplication).stockListRepository
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.AddFollowingListDialogTheme)
        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_list_item_1, stockListRepository.searchStrings
        )
        val view: View = layoutInflater.inflate(R.layout.dialog_add_newstock, null)
        val textView = view.findViewById(R.id.input_stockNo) as AutoCompleteTextView
        val inputLayout = view.findViewById(R.id.outlinedTextField) as TextInputLayout

        textView.setAdapter(adapter)
        textView.doAfterTextChanged { inputLayout.error = null }
        builder.setView(view)
            .setTitle("加入追蹤")
            // The click listener is replaced in setOnShowListener so an invalid code keeps the dialog open.
            .setPositiveButton("新增", null)
            .setNegativeButton("Cancel", DialogInterface.OnClickListener { dialog, id ->
                dialog.cancel()
            })

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val stockNoInput = StockSearch.codeFromInput(textView.text.toString())
                if (stockListRepository.contains(stockNoInput)) {
                    inputLayout.error = null
                    listViewModel.addToStockList(stockNoInput, listViewModel.currentSelectedFollowingListId.value!!)
                    listViewModel.changeCurrentFollowingListId()
                    dismiss()
                } else {
                    inputLayout.error = getString(R.string.stock_no_not_found)
                }
            }
        }
        return dialog
    }

}
