package com.example.mynewsapp.ui.tradeimport

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.DialogTradeEditBinding
import com.example.mynewsapp.ui.tradeimport.core.PreviewItem
import com.example.mynewsapp.ui.tradeimport.core.TAIPEI
import com.example.mynewsapp.ui.tradeimport.core.TradeImportSession
import com.example.mynewsapp.ui.tradeimport.core.TradeSide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.ZoneId

/** Edits the stock number, price, shares, date and direction of one previewed trade. */
object TradeEditDialog {

    fun interface OnSave {
        fun save(stockNoText: String, priceText: String, amountText: String, date: LocalDate?, side: TradeSide)
    }

    /** Shows the editor; the caller dismisses the returned dialog when its view is destroyed. */
    fun show(context: Context, item: PreviewItem, onSave: OnSave): AlertDialog {
        val binding = DialogTradeEditBinding.inflate(LayoutInflater.from(context))
        var date: LocalDate? = item.date
        var datePicker: DatePickerDialog? = null

        fun showDate() {
            binding.date.text = date?.toString() ?: context.getString(R.string.trade_import_edit_pick_date)
        }

        binding.stockNo.setText(item.stockNo.orEmpty())
        binding.price.setText(item.price?.let(TradeImportSession::formatPrice).orEmpty())
        binding.amount.setText(item.amount?.toString().orEmpty())
        binding.side.check(if (item.side == TradeSide.SELL) R.id.sell else R.id.buy)
        showDate()
        binding.date.setOnClickListener {
            val initial = date ?: LocalDate.now(TAIPEI)
            datePicker = DatePickerDialog(
                context,
                { _, year, month, day ->
                    date = LocalDate.of(year, month + 1, day)
                    showDate()
                },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth,
            ).also {
                // Trades in a screenshot are never in the future.
                // The picker reads maxDate in the device time zone; today is today in Taipei.
                it.datePicker.maxDate =
                    LocalDate.now(TAIPEI).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                it.show()
            }
        }

        return MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_App_TradeEditDialog)
            .setTitle(R.string.trade_import_edit_title)
            .setView(binding.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm_button) { _, _ ->
                onSave.save(
                    binding.stockNo.text?.toString().orEmpty(),
                    binding.price.text?.toString().orEmpty(),
                    binding.amount.text?.toString().orEmpty(),
                    date,
                    if (binding.side.checkedRadioButtonId == R.id.sell) TradeSide.SELL else TradeSide.BUY,
                )
            }
            .setOnDismissListener { datePicker?.dismiss() }
            .show()
    }
}
