package com.example.mynewsapp.tradecost

/**
 * The fee and the tax of one trade as two text fields show them. A field the user typed in keeps the typed
 * text; the other one follows the estimate.
 */
class FeeTaxFields {
    var feeText: String = ""
        private set
    var taxText: String = ""
        private set
    var feeIsManual: Boolean = false
        private set
    var taxIsManual: Boolean = false
        private set

    /** The fee and the tax as numbers; null while the text is empty or not a whole number from 0 to 2147483647. */
    val fee: Int? get() = TradeInput.parseFeeOrTax(feeText)
    val tax: Int? get() = TradeInput.parseFeeOrTax(taxText)

    /** Typing keeps the text as it is, an empty text included, so the user can type a new value. */
    fun editFee(text: String) {
        feeText = text
        feeIsManual = true
    }

    fun editTax(text: String) {
        taxText = text
        taxIsManual = true
    }

    /** Leaving a blank field goes back to the estimate; call [refresh] afterwards to show it. */
    fun endEditingFee() {
        if (feeText.isBlank()) feeIsManual = false
    }

    fun endEditingTax() {
        if (taxText.isBlank()) taxIsManual = false
    }

    /**
     * Shows the estimate in the fields the user did not type. A buy pays no tax, so a tax typed for a sell is
     * forgotten and the estimate of 0 is shown.
     */
    fun refresh(estimate: FeeAndTax?, side: TradeSide) {
        if (side == TradeSide.BUY) taxIsManual = false
        if (!feeIsManual) feeText = estimate?.fee?.toString() ?: ""
        if (!taxIsManual) taxText = estimate?.tax?.toString() ?: ""
    }
}
