package com.example.mynewsapp.ui.detail

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.FragmentAddHistoryBinding
import com.example.mynewsapp.tradecost.TaxCategory
import com.example.mynewsapp.tradecost.TradeSide
import com.example.mynewsapp.ui.setting.FeeSettingsStore
import com.example.mynewsapp.util.hideKeyboard
import kotlinx.coroutines.launch
import java.util.Calendar

class AddHistoryFragment : Fragment() {
    private var _binding: FragmentAddHistoryBinding? = null
    private val binding get() = _binding!!
    private val args: AddHistoryFragmentArgs by navArgs()
    private val addHistoryViewModel: AddHistoryViewModel by viewModels {
        val repository = (requireActivity().application as MyApplication).repository
        // The settings cannot change while this screen is open.
        val settings = FeeSettingsStore(requireContext().applicationContext).load()
        AddHistoryViewModel.Factory(repository) { settings }
    }

    /** True while the screen writes the view model's state into the views, so that no listener reacts to it. */
    private var isRendering = false


    private val priceField: EditText? get() = binding.price.editText
    private val amountField: EditText? get() = binding.amount.editText
    private val feeField: EditText? get() = binding.fee.editText
    private val taxField: EditText? get() = binding.tax.editText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddHistoryBinding.inflate(inflater, container, false)
        (requireActivity() as AppCompatActivity).supportActionBar?.title = "新增一筆紀錄"
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.stockNo.text = args.stockNo

        binding.date.editText?.inputType = InputType.TYPE_NULL
        binding.date.editText?.setOnClickListener {
            it.hideKeyboard()
            showDatePickerDialog()
        }

        binding.taxCategorySpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            TaxCategory.values().map { it.title },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { addHistoryViewModel.state.collect { render(it) } }
                launch {
                    addHistoryViewModel.saveOutcome.collect { outcome ->
                        if (outcome != null) {
                            addHistoryViewModel.consumeSaveOutcome()
                            show(outcome)
                        }
                    }
                }
            }
        }
    }

    /**
     * The views have their restored text and checked state by now. Listeners are attached only after that, so
     * that restored values are not taken as typed by the user; the computed values are then drawn again.
     */
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // After the process was ended the view model is new; what the views cannot keep comes from the saved state.
        if (savedInstanceState != null && addHistoryViewModel.isNew) {
            TradeSide.values().getOrNull(savedInstanceState.getInt(STATE_SIDE, -1))
                ?.let { addHistoryViewModel.selectSide(it) }
            TaxCategory.values().getOrNull(savedInstanceState.getInt(STATE_TAX_CATEGORY, -1))
                ?.let { addHistoryViewModel.selectTaxCategory(it) }
            if (savedInstanceState.containsKey(STATE_DATE)) addHistoryViewModel.date = savedInstanceState.getLong(STATE_DATE)
            tradeInputChanged()
            savedInstanceState.getString(STATE_MANUAL_FEE)?.let { addHistoryViewModel.editFee(it) }
            savedInstanceState.getString(STATE_MANUAL_TAX)?.let { addHistoryViewModel.editTax(it) }
        }
        addHistoryViewModel.isNew = false
        if (addHistoryViewModel.date == null) binding.date.editText?.setText("")

        priceField?.doAfterTextChanged { if (!isRendering) tradeInputChanged(); binding.price.error = null }
        amountField?.doAfterTextChanged { if (!isRendering) tradeInputChanged(); binding.amount.error = null }
        feeField?.doAfterTextChanged {
            if (!isRendering) addHistoryViewModel.editFee(it?.toString())
            binding.fee.error = null
        }
        taxField?.doAfterTextChanged {
            if (!isRendering) addHistoryViewModel.editTax(it?.toString())
            binding.tax.error = null
        }
        feeField?.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) addHistoryViewModel.endEditingFee() }
        taxField?.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) addHistoryViewModel.endEditingTax() }

        binding.chipSell.setOnCheckedChangeListener { _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener
            endEditingFields()
            addHistoryViewModel.selectSide(if (isChecked) TradeSide.SELL else TradeSide.BUY)
        }
        binding.taxCategorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // The spinner also reports the selection it was given; the view model ignores an unchanged one.
                if (!isRendering) addHistoryViewModel.selectTaxCategory(TaxCategory.values()[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        tradeInputChanged()
        render(addHistoryViewModel.state.value)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = addHistoryViewModel.state.value
        outState.putInt(STATE_SIDE, state.side.ordinal)
        outState.putInt(STATE_TAX_CATEGORY, state.taxCategory.ordinal)
        addHistoryViewModel.date?.let { outState.putLong(STATE_DATE, it) }
        addHistoryViewModel.manualFeeText?.let { outState.putString(STATE_MANUAL_FEE, it) }
        addHistoryViewModel.manualTaxText?.let { outState.putString(STATE_MANUAL_TAX, it) }
    }

    private fun tradeInputChanged() {
        addHistoryViewModel.changeTrade(priceField?.text?.toString(), amountField?.text?.toString())
    }

    private fun render(state: TradeEntryUiState) {
        val binding = _binding ?: return
        isRendering = true
        try {
            feeField?.setTextIfChanged(state.feeText)
            taxField?.setTextIfChanged(state.taxText)
            binding.chipBuy.isChecked = state.side == TradeSide.BUY
            binding.chipSell.isChecked = state.side == TradeSide.SELL
            if (binding.taxCategorySpinner.selectedItemPosition != state.taxCategory.ordinal) {
                binding.taxCategorySpinner.setSelection(state.taxCategory.ordinal)
            }
            val taxVisibility = if (state.side == TradeSide.SELL) View.VISIBLE else View.GONE
            binding.taxCategoryRow.visibility = taxVisibility
            binding.taxRow.visibility = taxVisibility
        } finally {
            isRendering = false
        }
    }

    private fun EditText.setTextIfChanged(value: String) {
        if (text?.toString() == value) return
        setText(value)
        if (hasFocus()) setSelection(value.length)
    }

    /** A menu item or a chip does not take the focus, so the editing of the fee and the tax is ended here. */
    private fun endEditingFields() {
        addHistoryViewModel.endEditingFee()
        addHistoryViewModel.endEditingTax()
        val focused = activity?.currentFocus
        if (focused == feeField || focused == taxField) {
            focused?.clearFocus()
            focused?.hideKeyboard()
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val datePickerDialog = DatePickerDialog(requireContext(), { _, pickedYear, pickedMonth, pickedDay ->
            binding.date.editText?.setText(getString(R.string.add_stock_date_string, pickedYear, pickedMonth + 1, pickedDay))
            binding.date.error = null
            calendar.set(pickedYear, pickedMonth, pickedDay)
            addHistoryViewModel.date = calendar.timeInMillis
        }, year, month, day)
        datePickerDialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.add_history_fragment_option_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.finishButton -> {
                addHistoryToDB()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addHistoryToDB() {
        endEditingFields()
        view?.hideKeyboard()
        addHistoryViewModel.save(
            stockNo = binding.stockNo.text.toString(),
            memo = binding.memo.editText?.text?.toString().orEmpty(),
        )
    }

    private fun show(outcome: SaveOutcome) {
        when (outcome) {
            is SaveOutcome.Saved -> findNavController().popBackStack()
            is SaveOutcome.Failed -> Toast.makeText(requireContext(), R.string.save_failed, Toast.LENGTH_SHORT).show()
            is SaveOutcome.Invalid -> {
                val errors = outcome.errors
                binding.stockNo.error = if (errors.stockNo) getString(R.string.stock_no_invalid) else null
                binding.amount.error = if (errors.shares) getString(R.string.shares_invalid) else null
                binding.price.error = if (errors.price) getString(R.string.price_invalid) else null
                binding.fee.error = if (errors.fee) getString(R.string.fee_or_tax_invalid) else null
                binding.tax.error = if (errors.tax) getString(R.string.fee_or_tax_invalid) else null
                binding.date.error = if (errors.date) getString(R.string.date_invalid) else null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val STATE_SIDE = "side"
        const val STATE_TAX_CATEGORY = "taxCategory"
        const val STATE_DATE = "date"
        const val STATE_MANUAL_FEE = "manualFee"
        const val STATE_MANUAL_TAX = "manualTax"
    }
}
