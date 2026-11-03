package com.example.mynewsapp.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.firebase.FirebaseManager
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.tradecost.FeeSettings
import com.example.mynewsapp.tradecost.TaxCategory
import com.example.mynewsapp.tradecost.TradeEntryForm
import com.example.mynewsapp.tradecost.TradeEntryResult
import com.example.mynewsapp.tradecost.TradeSide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

/** What the add-record screen shows that it does not type itself. */
data class TradeEntryUiState(
    val feeText: String = "",
    val taxText: String = "",
    val side: TradeSide = TradeSide.BUY,
    val taxCategory: TaxCategory = TaxCategory.STOCK,
)

/** The fields that block saving; every invalid field is reported at once. */
data class TradeEntryErrors(
    val stockNo: Boolean = false,
    val shares: Boolean = false,
    val price: Boolean = false,
    val fee: Boolean = false,
    val tax: Boolean = false,
    val date: Boolean = false,
)

sealed class SaveOutcome {
    object Saved : SaveOutcome()
    object Failed : SaveOutcome()
    data class Invalid(val errors: TradeEntryErrors) : SaveOutcome()
}

class AddHistoryViewModel(
    private val repository: NewsRepository,
    feeSettings: () -> FeeSettings,
) : ViewModel() {
    private val form = TradeEntryForm(feeSettings)
    private val _state = MutableStateFlow(TradeEntryUiState())
    val state: StateFlow<TradeEntryUiState> = _state.asStateFlow()

    /** True until the screen has drawn this view model once; a new one after process death needs the saved state. */
    var isNew = true

    /** The chosen trade date in milliseconds; kept here so that it survives a configuration change. */
    var date: Long? = null

    fun changeTrade(priceText: String?, sharesText: String?) = update { form.changeTrade(priceText, sharesText) }

    fun selectSide(side: TradeSide) {
        if (side != form.side) update { form.selectSide(side) }
    }

    fun selectTaxCategory(category: TaxCategory) {
        if (category != form.taxCategory) update { form.selectTaxCategory(category) }
    }

    fun editFee(text: String?) = update { form.editFee(text) }

    fun editTax(text: String?) = update { form.editTax(text) }

    fun endEditingFee() = update { form.endEditingFee() }

    fun endEditingTax() = update { form.endEditingTax() }

    private val _saveOutcome = MutableStateFlow<SaveOutcome?>(null)

    /** The result of the last [save], until the screen calls [consumeSaveOutcome]. */
    val saveOutcome: StateFlow<SaveOutcome?> = _saveOutcome.asStateFlow()
    private var isSaving = false

    /** The typed fee and tax, or null where the field follows the computation; kept across process death. */
    val manualFeeText: String? get() = form.feeText.takeIf { form.feeIsManual }
    val manualTaxText: String? get() = form.taxText.takeIf { form.taxIsManual }

    /**
     * Writes the record with the fee and the tax the fields show, after ending their editing. The write runs in
     * the view model's scope, so that a configuration change does not cancel it. The fee, the tax and the memo
     * stay on the device; the online copy keeps the fields it had.
     */
    fun save(stockNo: String, memo: String) {
        if (isSaving) return
        val result = form.validateForSave()
        publish()
        val tradeDate = date
        if (result !is TradeEntryResult.Valid || tradeDate == null || stockNo.isEmpty()) {
            val invalid = result as? TradeEntryResult.Invalid
            _saveOutcome.value = SaveOutcome.Invalid(
                TradeEntryErrors(
                    stockNo = stockNo.isEmpty(),
                    shares = invalid?.sharesInvalid == true,
                    price = invalid?.priceInvalid == true,
                    fee = invalid?.feeInvalid == true,
                    tax = invalid?.taxInvalid == true,
                    date = tradeDate == null,
                )
            )
            return
        }
        val record = InvestHistory(
            id = 0,
            stockNo = stockNo,
            date = tradeDate,
            price = result.price,
            amount = result.shares,
            status = result.side.status,
            fee = result.fee,
            tax = result.tax,
            memo = memo,
        )
        isSaving = true
        viewModelScope.launch {
            try {
                repository.insertHistory(record)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Could not save the record")
                isSaving = false
                _saveOutcome.value = SaveOutcome.Failed
                return@launch
            }
            // The record is on the device now; a failed upload does not undo that.
            try {
                FirebaseManager.uploadHistoryToOnlineDB(result.price, result.shares, tradeDate, stockNo, result.side.status)
            } catch (e: Exception) {
                Timber.w(e, "Could not upload the record")
            }
            _saveOutcome.value = SaveOutcome.Saved
        }
    }

    fun consumeSaveOutcome() {
        _saveOutcome.value = null
    }

    private inline fun update(change: () -> Unit) {
        change()
        publish()
    }

    private fun publish() {
        _state.value = TradeEntryUiState(form.feeText, form.taxText, form.side, form.taxCategory)
    }

    class Factory(
        private val repository: NewsRepository,
        private val feeSettings: () -> FeeSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddHistoryViewModel(repository, feeSettings) as T
    }
}
