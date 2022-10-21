package com.example.mynewsapp.ui.list

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import com.example.mynewsapp.R
import android.widget.*
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class AddStockDialogFragment : DialogFragment() {
    /**
     * this dialog is for adding new stockNo
     */
    private val listViewModel: ListViewModel by activityViewModels()



    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext(), R.style.AddFollowingListDialogTheme)
        val countries: Array<String> = resources.getStringArray(R.array.countries_array)
        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_list_item_1, countries
        )
        val view: View = layoutInflater.inflate(R.layout.dialog_add_newstock, null)
        val textView = view.findViewById(R.id.input_stockNo) as AutoCompleteTextView

        textView.setAdapter(adapter)
        builder.setView(view)
            .setTitle("加入追蹤")
            .setPositiveButton("新增", DialogInterface.OnClickListener { dialog, id ->
                val textView = (dialog as Dialog).findViewById(R.id.input_stockNo) as AutoCompleteTextView

                val stockNoInput = textView.text.toString().split(" ")[0]
                if(stockNoInput.isNotEmpty()) {
                    listViewModel.addToStockList(stockNoInput, listViewModel.currentSelectedFollowingListId.value!!)
                    //viewModel.getOneFollowingListWithStocks()
                    listViewModel.changeCurrentFollowingListId()
                    dismiss()

                }
            })
            .setNegativeButton("Cancel", DialogInterface.OnClickListener { dialog, id ->
                dialog.cancel()
            })





        return builder.create()
    }

}