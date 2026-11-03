package com.example.mynewsapp.tradecost

/**
 * The state behind the add-record screen: the side, the tax category, the price and shares text, and the fee
 * and the tax as the two fields show them (see [FeeTaxFields]). A field the user typed in keeps the typed
 * text; the other one follows the price, the shares, the side and the category.
 */
class TradeEntryForm(private val feeSettings: () -> FeeSettings) {
    var side: TradeSide = TradeSide.BUY
        private set
    var taxCategory: TaxCategory = TaxCategory.STOCK
        private set
    val feeText: String get() = fields.feeText
    val taxText: String get() = fields.taxText
    val feeIsManual: Boolean get() = fields.feeIsManual
    val taxIsManual: Boolean get() = fields.taxIsManual

    private var priceText: String? = null
    private var sharesText: String? = null
    private val fields = FeeTaxFields()

    /** Called with the current text of both fields every time either of them changes. */
    fun changeTrade(priceText: String?, sharesText: String?) {
        this.priceText = priceText
        this.sharesText = sharesText
        recompute()
    }

    /** Switching the side ends the editing of the fee and the tax first, as leaving the fields would. */
    fun selectSide(side: TradeSide) {
        endEditing()
        this.side = side
        recompute()
    }

    fun selectTaxCategory(category: TaxCategory) {
        taxCategory = category
        recompute()
    }

    fun editFee(text: String?) = fields.editFee(text ?: "")

    fun editTax(text: String?) = fields.editTax(text ?: "")

    /** A field that is left blank shows the computed value again and goes back to following the inputs. */
    fun endEditingFee() {
        fields.endEditingFee()
        recompute()
    }

    fun endEditingTax() {
        fields.endEditingTax()
        recompute()
    }

    /**
     * Ends the editing of both fields, then checks what would be saved. A buy never pays tax, so its tax is 0
     * whatever the tax field holds.
     */
    fun validateForSave(): TradeEntryResult {
        endEditing()
        val price = TradeInput.parsePrice(priceText)
            ?.takeIf { it.signum() > 0 }
            ?.toDouble()
            ?.takeIf { it.isFinite() }
        val shares = TradeInput.parseShares(sharesText)
        val fee = fields.fee
        val tax = if (side == TradeSide.SELL) fields.tax else 0
        if (price == null || shares == null || fee == null || tax == null) {
            // An empty computed field is the price's or the shares' fault, not the field's.
            val tradeIsValid = price != null && shares != null
            return TradeEntryResult.Invalid(
                priceInvalid = price == null,
                sharesInvalid = shares == null,
                feeInvalid = fee == null && (tradeIsValid || fields.feeIsManual),
                taxInvalid = tax == null && (tradeIsValid || fields.taxIsManual),
            )
        }
        return TradeEntryResult.Valid(price = price, shares = shares, side = side, fee = fee, tax = tax)
    }

    private fun endEditing() {
        fields.endEditingFee()
        fields.endEditingTax()
        recompute()
    }

    private fun recompute() {
        fields.refresh(computedFeeAndTax(), side)
    }

    private fun computedFeeAndTax(): FeeAndTax? {
        val price = TradeInput.parsePrice(priceText) ?: return null
        val shares = TradeInput.parseShares(sharesText) ?: return null
        return FeeCalculator.calculate(price, shares, side, feeSettings(), taxCategory)
    }
}

sealed class TradeEntryResult {
    data class Valid(val price: Double, val shares: Int, val side: TradeSide, val fee: Int, val tax: Int) :
        TradeEntryResult()

    data class Invalid(
        val priceInvalid: Boolean,
        val sharesInvalid: Boolean,
        val feeInvalid: Boolean,
        val taxInvalid: Boolean,
    ) : TradeEntryResult()
}
