package com.example.mynewsapp.ui.detail

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.DatePicker
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.FragmentAddHistoryBinding
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.util.InputDataStatus
import com.example.mynewsapp.util.hideKeyboard
import timber.log.Timber
import java.util.*


class AddHistoryFragment: Fragment(), AdapterView.OnItemSelectedListener {
    private lateinit var binding: FragmentAddHistoryBinding
    private lateinit var addHistoryViewModel: AddHistoryViewModel
    private val args: AddHistoryFragmentArgs by navArgs()
    var dateSelected: Long? = null
    var defaultCustomFeeDiscount: String? = ""
    var defaultCustomFee: String? = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAddHistoryBinding.inflate(inflater, container, false)
        val repository = (activity?.application as MyApplication).repository
        addHistoryViewModel = AddHistoryViewModel(repository)
        //viewModel =(activity as MainActivity).viewModel
        //change toolbar title
        (requireActivity() as AppCompatActivity).supportActionBar?.title = "新增一筆紀錄"

        setHasOptionsMenu(true)

        loadSetting()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.stockNo.text = args.stockNo

        binding.date.editText?.inputType = InputType.TYPE_NULL

        binding.date.editText?.setOnClickListener {
            println("click date picker")
            it.hideKeyboard()
            showDatePickerDialog()
        }

        binding.feeOptionSpinner.onItemSelectedListener = this


    }
    private fun showDatePickerDialog(){
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val datePickerDialog = DatePickerDialog(requireContext(), object :DatePickerDialog.OnDateSetListener {
            override fun onDateSet(p0: DatePicker?, year: Int, month: Int, day: Int) {
                binding.date.editText?.setText(getString(R.string.add_stock_date_string, year, month+1, day))
                // set selected date in milliseconds
                calendar.set(year,month,day)
                dateSelected = calendar.timeInMillis
            }

        }, year, month, day)
        datePickerDialog.show()
        datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK);
        datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK);
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
        val stockNo = binding.stockNo.text.toString()
        val amount = binding.amount.editText?.text.toString().toIntOrNull()
        val price = binding.price.editText?.text.toString().toDoubleOrNull()
        val date = dateSelected

        val isBuy = if (binding.chipBuy.isChecked) 0 else 1

        val dataStatus = addHistoryViewModel.checkDataInput(stockNo = stockNo, amount = amount, price = price , date = date)

        when (dataStatus) {
            InputDataStatus.InvalidStockNo -> {
                binding.stockNo.error = "不可為空白"
                return
            }
            InputDataStatus.InvalidAmount -> {
                binding.amount.error = "必須大於0"
                return
            }
            InputDataStatus.InvalidPrice -> {
                binding.price.error = "必須大於0"
                return
            }
            InputDataStatus.InvalidDate -> {
                binding.date.error = "請選擇日期"
                return
            }
            else -> {
                val newHistory = InvestHistory(0, stockNo = stockNo, amount = amount!!, price = price!!, status = isBuy, date = date!!)

                addHistoryViewModel.insertHistory(newHistory)
                addHistoryViewModel.uploadHistoryToOnlineDB(price = price, amount = amount, date = date, stockNo = stockNo, status = isBuy)
                findNavController().popBackStack()
            }
        }

    }
    // For spinner
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
        Timber.d("spinner onItemSelected pos $pos")
        binding.feeDiscountSpinner.visibility = View.GONE
        binding.feeOneDollar.visibility = View.GONE
        binding.feeCustom.visibility = View.GONE
        when(pos) {
            0 -> {
                binding.feeDiscountSpinner.visibility = View.VISIBLE
            }
            1 -> {
                binding.feeOneDollar.visibility = View.VISIBLE
            }
            2 -> {
                binding.feeCustom.visibility = View.VISIBLE
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {

    }

    // load preference
    private fun loadSetting() {
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())

        defaultCustomFeeDiscount = sp.getString("fee_discount", "")
        defaultCustomFee = sp.getString("fee_custom", "")
        Timber.d("fee discount index $defaultCustomFeeDiscount")
        Timber.d("fee custom $defaultCustomFee")


        binding.feeDiscountSpinner.post(object: Runnable{
            override fun run() {
                // set spinner default value
                Timber.d("defaultCustomFeeDiscount $defaultCustomFeeDiscount")
                defaultCustomFeeDiscount?.let { str ->
                    if (str.isNotEmpty()) {
                        binding.feeDiscountSpinner.setSelection(str.toInt() / 10 - 1)
                    }
                }
            }
        })
        binding.feeCustom.post(object : Runnable {
            override fun run() {
                defaultCustomFee?.let { str -> binding.feeCustom.setText(str) }
            }
        })
    }
}