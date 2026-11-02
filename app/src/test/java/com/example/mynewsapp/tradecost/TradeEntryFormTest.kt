package com.example.mynewsapp.tradecost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TradeEntryFormTest {
    private val noDiscount = FeeSettings(BigDecimal.TEN)

    private fun makeForm(settings: FeeSettings = noDiscount) = TradeEntryForm { settings }

    private fun TradeEntryForm.saved(): TradeEntryResult.Valid {
        val result = validateForSave()
        assertTrue("expected a valid record, was $result", result is TradeEntryResult.Valid)
        return result as TradeEntryResult.Valid
    }

    private fun TradeEntryForm.rejected(): TradeEntryResult.Invalid {
        val result = validateForSave()
        assertTrue("expected an invalid record, was $result", result is TradeEntryResult.Invalid)
        return result as TradeEntryResult.Invalid
    }

    // computing

    @Test
    fun theFeeFollowsTheFinalTextOfTheSharesFieldWhileItIsTyped() {
        val form = makeForm()
        val fees = listOf("1", "10", "100", "1000").map { typed ->
            form.changeTrade("100", typed)
            form.feeText
        }

        // 100 x 1 is 0.14 dollars, which the odd-lot minimum raises to 1; 1000 shares is 142.5, rounded down.
        assertEquals(listOf("1", "1", "14", "142"), fees)
    }

    @Test
    fun theFeeFollowsTheFinalTextOfThePriceFieldWhileItIsTyped() {
        val form = makeForm()
        val fees = listOf("1", "10", "105", "105.", "105.5").map { typed ->
            form.changeTrade(typed, "1000")
            form.feeText
        }

        // 1000 shares are charged the whole-lot minimum of 20 until the formula passes it.
        assertEquals(listOf("20", "20", "149", "149", "150"), fees)
    }

    @Test
    fun theOddLotMinimumFeeFromTheSettingsIsShown() {
        val form = makeForm(FeeSettings(BigDecimal(6), oddLotMinimumFee = 5))

        form.changeTrade("112.55", "5")

        assertEquals("5", form.feeText)
    }

    @Test
    fun theDiscountFromTheSettingsIsUsed() {
        val form = makeForm(FeeSettings(BigDecimal("2.8")))

        form.changeTrade("100", "1000")

        // 100000 x 0.001425 x 0.28 = 39.9
        assertEquals("39", form.feeText)
    }

    @Test
    fun theSettingsAreReadAgainForEachComputation() {
        var settings = noDiscount
        val form = TradeEntryForm { settings }
        form.changeTrade("100", "1000")
        settings = FeeSettings(BigDecimal(6))

        form.changeTrade("100", "1000")

        assertEquals("85", form.feeText)
    }

    @Test
    fun thereIsNoComputedValueWithoutAUsablePriceAndShares() {
        val inputs = listOf(
            null to "1000", "" to "1000", "100" to null, "100" to "", "abc" to "1000", "100" to "x",
            "0" to "1000", "100" to "0", "1e5" to "1000", "100" to "-5", "Infinity" to "1000", "." to "1000",
        )
        for ((price, shares) in inputs) {
            val form = makeForm()

            form.changeTrade(price, shares)

            assertEquals("$price x $shares", "", form.feeText)
            assertEquals("$price x $shares", "", form.taxText)
        }
    }

    @Test
    fun aBuyHasATaxOfZero() {
        val form = makeForm()

        form.changeTrade("100", "1000")

        assertEquals(TradeSide.BUY, form.side)
        assertEquals("0", form.taxText)
    }

    @Test
    fun aSellComputesTheTaxFromTheChosenCategory() {
        val expected = mapOf(
            TaxCategory.STOCK to "300", TaxCategory.DAY_TRADE to "150", TaxCategory.ETF to "100",
            TaxCategory.EXEMPT to "0",
        )
        for ((category, tax) in expected) {
            val form = makeForm()
            form.changeTrade("100", "1000")
            form.selectSide(TradeSide.SELL)

            form.selectTaxCategory(category)

            assertEquals(category.name, tax, form.taxText)
        }
    }

    @Test
    fun aSellStartsWithGeneralStock() {
        assertEquals(TaxCategory.STOCK, makeForm().taxCategory)
    }

    @Test
    fun switchingFromBuyToSellComputesTheTax() {
        val form = makeForm(FeeSettings(BigDecimal(6)))
        form.changeTrade("105", "1000")
        assertEquals("0", form.taxText)

        form.selectSide(TradeSide.SELL)

        assertEquals("315", form.taxText)
    }

    @Test
    fun switchingBackToBuyShowsNoTax() {
        val form = makeForm()
        form.changeTrade("105", "1000")
        form.selectSide(TradeSide.SELL)

        form.selectSide(TradeSide.BUY)

        assertEquals("0", form.taxText)
    }

    @Test
    fun aFeeBeyondWhatCanBeStoredIsNotComputed() {
        val form = makeForm()

        form.changeTrade("99999999999999999999", "30000")

        assertEquals("", form.feeText)
    }

    // manual values

    @Test
    fun aTypedFeeIsKeptWhenTheSharesChange() {
        val form = makeForm()
        form.changeTrade("100", "1000")
        form.editFee("50")

        form.changeTrade("100", "2000")

        assertEquals("50", form.feeText)
    }

    @Test
    fun aTypedFeeIsKeptWhenThePriceOrTheSideChange() {
        val form = makeForm()
        form.changeTrade("100", "1000")
        form.editFee("50")

        form.changeTrade("200", "1000")
        form.selectSide(TradeSide.SELL)

        assertEquals("50", form.feeText)
    }

    @Test
    fun aTypedTaxIsKeptWhenThePriceOrTheCategoryChange() {
        val form = makeForm()
        form.selectSide(TradeSide.SELL)
        form.changeTrade("100", "1000")
        form.editTax("123")

        form.changeTrade("200", "1000")
        form.selectTaxCategory(TaxCategory.ETF)

        assertEquals("123", form.taxText)
    }

    @Test
    fun theOtherFieldKeepsFollowingWhenOnlyOneWasTyped() {
        val form = makeForm()
        form.selectSide(TradeSide.SELL)
        form.changeTrade("100", "1000")
        form.editFee("50")

        form.changeTrade("100", "2000")

        assertEquals("600", form.taxText)
    }

    @Test
    fun aDeletedFeeStaysEmptyWhileItIsEdited() {
        val form = makeForm()
        form.changeTrade("100", "1000")
        assertEquals("142", form.feeText)

        form.editFee("")
        form.changeTrade("100", "2000")

        assertEquals("", form.feeText)
    }

    @Test
    fun aNumberTypedAfterDeletingEverythingIsStored() {
        val form = makeForm()
        form.changeTrade("100", "1000")
        form.editFee("")
        form.editFee("5")
        form.editFee("50")
        form.endEditingFee()

        val saved = form.saved()

        assertEquals("50", form.feeText)
        assertEquals(50, saved.fee)
    }

    @Test
    fun leavingAnEmptyFeeShowsTheComputedValueAgain() {
        val form = makeForm()
        form.changeTrade("100", "1000")
        form.editFee("")

        form.endEditingFee()

        assertEquals("142", form.feeText)
    }

    @Test
    fun leavingAnEmptyFeeGoesBackToComputingIt() {
        val form = makeForm()
        form.changeTrade("100", "1000")
        form.editFee("")
        form.endEditingFee()

        form.changeTrade("100", "2000")

        assertEquals("285", form.feeText)
    }

    @Test
    fun leavingAnEmptyTaxShowsTheComputedValueAgain() {
        val form = makeForm()
        form.selectSide(TradeSide.SELL)
        form.changeTrade("100", "1000")
        form.editTax("")

        form.endEditingTax()

        assertEquals("300", form.taxText)
    }

    @Test
    fun leavingAFilledFeeKeepsItManual() {
        val form = makeForm()
        form.changeTrade("100", "1000")
        form.editFee("50")
        form.endEditingFee()

        form.changeTrade("100", "2000")

        assertEquals("50", form.feeText)
    }

    @Test
    fun switchingTheSideWhileTheFeeIsEmptyShowsTheComputedFee() {
        val form = makeForm()
        form.changeTrade("100", "1000")
        form.editFee("")

        form.selectSide(TradeSide.SELL)

        assertEquals("142", form.feeText)
    }

    @Test
    fun switchingToABuyForgetsATypedTax() {
        val form = makeForm()
        form.changeTrade("100", "1000")
        form.selectSide(TradeSide.SELL)
        form.editTax("30")

        form.selectSide(TradeSide.BUY)
        form.selectSide(TradeSide.SELL)

        assertEquals("300", form.taxText)
    }

    // saving

    @Test
    fun savingWithAnEmptyFeeThatIsStillBeingEditedStoresTheComputedFee() {
        val form = makeForm()
        form.changeTrade("100", "1000")
        form.editFee("")

        val saved = form.saved()

        assertEquals(142, saved.fee)
        assertEquals("142", form.feeText)
    }

    @Test
    fun aBuyIsSavedWithTheFeeFromTheFieldAndATaxOfZero() {
        val form = makeForm()
        form.changeTrade("105", "1000")

        assertEquals(TradeEntryResult.Valid(price = 105.0, shares = 1000, side = TradeSide.BUY, fee = 149, tax = 0), form.saved())
    }

    @Test
    fun aBuyIgnoresATaxTypedBeforeSwitchingBack() {
        val form = makeForm()
        form.changeTrade("105", "1000")
        form.selectSide(TradeSide.SELL)
        form.editTax("999")
        form.selectSide(TradeSide.BUY)

        val saved = form.saved()

        assertEquals(0, saved.tax)
        assertEquals(TradeSide.BUY, saved.side)
    }

    @Test
    fun aSellIsSavedWithTheTaxFromTheField() {
        val form = makeForm()
        form.changeTrade("105", "1000")
        form.selectSide(TradeSide.SELL)
        form.selectTaxCategory(TaxCategory.ETF)

        val saved = form.saved()

        assertEquals(TradeSide.SELL, saved.side)
        assertEquals(149, saved.fee)
        assertEquals(105, saved.tax)
    }

    @Test
    fun aTypedValueIsWhatIsSaved() {
        val form = makeForm()
        form.changeTrade("105", "1000")
        form.selectSide(TradeSide.SELL)
        form.editFee("77")
        form.editTax("88")

        val saved = form.saved()

        assertEquals(77, saved.fee)
        assertEquals(88, saved.tax)
    }

    @Test
    fun aPriceIsSavedAsTheNumberItReads() {
        val form = makeForm()
        form.changeTrade(" 112.55 ", "5")

        assertEquals(112.55, form.saved().price, 0.0)
    }

    @Test
    fun moreThan32767SharesAreAccepted() {
        val form = makeForm()
        form.changeTrade("100", "32768")

        assertEquals(32768, form.saved().shares)
    }

    @Test
    fun aBadPriceIsRejected() {
        for (price in listOf("", "0", "-1", "abc", "Infinity", "1e50", ".")) {
            val form = makeForm()
            form.changeTrade(price, "1000")
            form.editFee("50")

            val result = form.rejected()

            assertTrue(price, result.priceInvalid)
            assertTrue(price, !result.sharesInvalid && !result.feeInvalid && !result.taxInvalid)
        }
    }

    @Test
    fun aHugePriceThatIsNotFiniteAsADoubleIsRejected() {
        val form = makeForm()
        form.changeTrade("9".repeat(400), "1000")
        form.editFee("50")

        assertTrue(form.rejected().priceInvalid)
    }

    @Test
    fun badSharesAreRejected() {
        for (shares in listOf("", "0", "-5", "1.5", "99999999999")) {
            val form = makeForm()
            form.changeTrade("100", shares)
            form.editFee("50")

            assertTrue(shares, form.rejected().sharesInvalid)
        }
    }

    @Test
    fun aBadFeeIsRejected() {
        for (fee in listOf("-5", "1.5", "5.5", "abc", "2147483648", "9999999999")) {
            val form = makeForm()
            form.changeTrade("100", "1000")
            form.editFee(fee)

            val result = form.rejected()

            assertTrue(fee, result.feeInvalid)
            assertTrue(fee, !result.priceInvalid && !result.sharesInvalid && !result.taxInvalid)
        }
    }

    @Test
    fun anEmptyFeeIsNotBlamedWhenThePriceOrTheSharesAreTheProblem() {
        val form = makeForm()
        form.selectSide(TradeSide.SELL)
        form.changeTrade("", "1000")

        val result = form.rejected()

        assertTrue(result.priceInvalid)
        assertTrue(!result.feeInvalid && !result.taxInvalid)
    }

    @Test
    fun aTypedBadFeeIsReportedEvenWhenThePriceIsBad() {
        val form = makeForm()
        form.changeTrade("", "1000")
        form.editFee("abc")

        val result = form.rejected()

        assertTrue(result.priceInvalid)
        assertTrue(result.feeInvalid)
    }

    @Test
    fun anEmptyFeeIsRejectedWhenNothingCanBeComputed() {
        // Price and shares are valid, but the fee does not fit in a record, so the field stays empty.
        val form = makeForm()
        form.changeTrade("99999999999999999999", "30000")

        val result = form.rejected()

        assertTrue(result.feeInvalid)
        assertTrue(!result.priceInvalid && !result.sharesInvalid)
    }

    @Test
    fun aBadTaxIsRejectedForASell() {
        val form = makeForm()
        form.selectSide(TradeSide.SELL)
        form.changeTrade("100", "1000")
        form.editTax("-1")

        assertTrue(form.rejected().taxInvalid)
    }

    @Test
    fun aBadTaxIsNotLookedAtForABuy() {
        val form = makeForm()
        form.selectSide(TradeSide.SELL)
        form.changeTrade("100", "1000")
        form.editTax("-1")
        form.selectSide(TradeSide.BUY)

        assertEquals(0, form.saved().tax)
    }
}
