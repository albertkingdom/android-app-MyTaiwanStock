package com.example.mynewsapp.tradecost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeeTaxFieldsTest {
    private val estimate = FeeAndTax(fee = 6, tax = 22)

    private fun refreshed(side: TradeSide = TradeSide.SELL) = FeeTaxFields().apply { refresh(estimate, side) }

    @Test
    fun theEstimateFillsBothFields() {
        val fields = refreshed()

        assertEquals("6", fields.feeText)
        assertEquals("22", fields.taxText)
        assertFalse(fields.feeIsManual || fields.taxIsManual)
    }

    @Test
    fun withoutAnEstimateTheFieldsAreEmpty() {
        val fields = refreshed()

        fields.refresh(null, TradeSide.SELL)

        assertEquals("", fields.feeText)
        assertEquals("", fields.taxText)
    }

    @Test
    fun aTypedFeeSurvivesANewEstimateWhileTheTaxFollows() {
        val fields = refreshed()
        fields.editFee("3")

        fields.refresh(FeeAndTax(12, 44), TradeSide.SELL)

        assertEquals("3", fields.feeText)
        assertTrue(fields.feeIsManual)
        assertEquals("44", fields.taxText)
    }

    @Test
    fun aTypedTaxSurvivesANewEstimate() {
        val fields = refreshed()
        fields.editTax("30")

        fields.refresh(FeeAndTax(12, 44), TradeSide.SELL)

        assertEquals("30", fields.taxText)
        assertEquals("12", fields.feeText)
    }

    @Test
    fun aDeletedFeeStaysEmptyWhileItIsEdited() {
        val fields = refreshed()
        fields.editFee("3")
        fields.editFee("")

        fields.refresh(estimate, TradeSide.SELL)

        assertEquals("", fields.feeText)
        assertTrue(fields.feeIsManual)
    }

    @Test
    fun leavingAnEmptyFeeGoesBackToTheEstimate() {
        val fields = refreshed()
        fields.editFee("  ")

        fields.endEditingFee()
        fields.refresh(estimate, TradeSide.SELL)

        assertEquals("6", fields.feeText)
        assertFalse(fields.feeIsManual)
    }

    @Test
    fun leavingAnEmptyTaxGoesBackToTheEstimate() {
        val fields = refreshed()
        fields.editTax("")

        fields.endEditingTax()
        fields.refresh(estimate, TradeSide.SELL)

        assertEquals("22", fields.taxText)
        assertFalse(fields.taxIsManual)
    }

    @Test
    fun leavingAFilledFieldKeepsItManual() {
        val fields = refreshed()
        fields.editFee("3")

        fields.endEditingFee()

        assertTrue(fields.feeIsManual)
    }

    @Test
    fun aBuyHasATaxOfZeroAndForgetsATypedTax() {
        val fields = refreshed()
        fields.editTax("30")

        fields.refresh(FeeAndTax(6, 0), TradeSide.BUY)
        assertEquals("0", fields.taxText)
        assertFalse(fields.taxIsManual)

        fields.refresh(estimate, TradeSide.SELL)
        assertEquals("22", fields.taxText)
    }

    @Test
    fun wholeNumbersInRangeAreValid() {
        for (text in listOf("0", "5", "2147483647")) {
            val fields = FeeTaxFields()
            fields.editFee(text)
            fields.editTax(text)

            assertEquals(text.toInt(), fields.fee)
            assertEquals(text.toInt(), fields.tax)
        }
    }

    @Test
    fun anythingElseIsNotAFeeOrTax() {
        for (text in listOf("-5", "5.5", "abc", "1e3", "2147483648", "", "  ")) {
            val fields = FeeTaxFields()
            fields.editFee(text)
            fields.editTax(text)

            assertNull(text, fields.fee)
            assertNull(text, fields.tax)
        }
    }
}
