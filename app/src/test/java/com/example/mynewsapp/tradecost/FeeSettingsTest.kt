package com.example.mynewsapp.tradecost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

class FeeSettingsTest {
    private fun assertDecimal(expected: String, actual: BigDecimal) =
        assertTrue("expected $expected, was $actual", BigDecimal(expected).compareTo(actual) == 0)

    private fun assertRejected(reason: FeeSettingsException.Reason, block: () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (e: FeeSettingsException) {
            assertEquals(reason, e.reason)
        }
    }

    // reading stored values

    @Test
    fun withNothingSavedThereIsNoDiscount() {
        assertDecimal("10", FeeSettings.fromStored(emptyMap<String, Any>()).discount)
    }

    @Test
    fun theEarlierPercentageIsReadAsADiscountWithoutBeingWritten() {
        val stored = mutableMapOf<String, Any>(FeeSettings.LEGACY_DISCOUNT_PERCENT_KEY to "60")

        val settings = FeeSettings.fromStored(stored)

        assertDecimal("6", settings.discount)
        assertEquals(mapOf(FeeSettings.LEGACY_DISCOUNT_PERCENT_KEY to "60"), stored)
    }

    @Test
    fun everyEarlierPercentageIsReadAsTenthsOfIt() {
        for (percent in 10..100 step 10) {
            val settings = FeeSettings.fromStored(mapOf(FeeSettings.LEGACY_DISCOUNT_PERCENT_KEY to "$percent"))

            assertDecimal("${percent / 10}", settings.discount)
        }
    }

    @Test
    fun anEarlierPercentageOutOfRangeMeansNoDiscount() {
        for (value in listOf("0", "101", "abc", "", "6.5", "-10")) {
            val settings = FeeSettings.fromStored(mapOf(FeeSettings.LEGACY_DISCOUNT_PERCENT_KEY to value))

            assertDecimal("10", settings.discount)
        }
    }

    @Test
    fun aSavedDiscountWinsOverTheEarlierPercentage() {
        val settings = FeeSettings.fromStored(
            mapOf(FeeSettings.LEGACY_DISCOUNT_PERCENT_KEY to "60", FeeSettings.DISCOUNT_KEY to "2.8")
        )

        assertDecimal("2.8", settings.discount)
    }

    @Test
    fun aSavedDiscountOutOfRangeFallsBackToTheEarlierPercentageOrNoDiscount() {
        assertDecimal("10", FeeSettings.fromStored(mapOf(FeeSettings.DISCOUNT_KEY to "11")).discount)
        assertDecimal(
            "5",
            FeeSettings.fromStored(
                mapOf(FeeSettings.DISCOUNT_KEY to "11", FeeSettings.LEGACY_DISCOUNT_PERCENT_KEY to "50")
            ).discount,
        )
    }

    @Test
    fun aDiscountNotSavedAsTextIsIgnored() {
        assertDecimal("10", FeeSettings.fromStored(mapOf(FeeSettings.DISCOUNT_KEY to 2.8f)).discount)
    }

    // typed discount

    @Test
    fun aDiscountFromPointOneToTenWithTwoDecimalsIsAccepted() {
        val accepted = listOf(
            "2.8" to "2.8", "10" to "10", "0.1" to "0.1", "6" to "6", " 0.65 " to "0.65", "9.99" to "9.99",
            "10.0" to "10", "10.00" to "10", "2.80" to "2.8", "2.850" to "2.85", "2.800" to "2.8",
        )
        for ((text, value) in accepted) {
            assertDecimal(value, FeeSettings.parseDiscount(text))
        }
    }

    @Test
    fun aDiscountOutOfRangeOrNotAPlainNumberIsRefused() {
        val refused = listOf(
            "0", "11", "10.01", "0.05", "2.855", "-1", "abc", "", "1e1", "2.8折", "1.2.3", "0x6", ".", "２.８",
        )
        for (text in refused) {
            assertRejected(FeeSettingsException.Reason.INVALID_DISCOUNT) { FeeSettings.parseDiscount(text) }
        }
        assertRejected(FeeSettingsException.Reason.INVALID_DISCOUNT) { FeeSettings.parseDiscount(null) }
    }

    // minimum fees

    @Test
    fun withNothingSavedTheMinimumFeesAreTheDefaults() {
        val settings = FeeSettings.fromStored(emptyMap<String, Any>())

        assertEquals(20, settings.wholeLotMinimumFee)
        assertEquals(1, settings.oddLotMinimumFee)
    }

    @Test
    fun theSavedMinimumFeesAreReadBack() {
        val settings = FeeSettings.fromStored(
            mapOf(FeeSettings.WHOLE_LOT_MINIMUM_KEY to 30, FeeSettings.ODD_LOT_MINIMUM_KEY to 5)
        )

        assertEquals(30, settings.wholeLotMinimumFee)
        assertEquals(5, settings.oddLotMinimumFee)
    }

    @Test
    fun zeroAndTheLargestRecordAmountAreSavedMinimumFees() {
        val settings = FeeSettings.fromStored(
            mapOf(FeeSettings.WHOLE_LOT_MINIMUM_KEY to 0, FeeSettings.ODD_LOT_MINIMUM_KEY to Int.MAX_VALUE)
        )

        assertEquals(0, settings.wholeLotMinimumFee)
        assertEquals(Int.MAX_VALUE, settings.oddLotMinimumFee)
    }

    @Test
    fun aMinimumFeeThatIsNotAWholeNumberInRangeFallsBackToTheDefault() {
        // 25L is a Long and true is a Boolean; neither was saved as an Int by the settings page.
        val invalidValues: List<Any> = listOf("20", 20.5f, 20.5, -1, 2_147_483_648L, 25L, true)
        for (value in invalidValues) {
            val settings = FeeSettings.fromStored(
                mapOf(FeeSettings.WHOLE_LOT_MINIMUM_KEY to value, FeeSettings.ODD_LOT_MINIMUM_KEY to value)
            )

            assertEquals("whole-lot minimum for $value", 20, settings.wholeLotMinimumFee)
            assertEquals("odd-lot minimum for $value", 1, settings.oddLotMinimumFee)
        }
    }

    /** The earlier fixed fee is ignored: the formula with the default discount applies. */
    @Test
    fun theEarlierFixedFeeIsIgnored() {
        val settings = FeeSettings.fromStored(mapOf("fee_custom" to "30"))

        val result = FeeCalculator.calculate(BigDecimal(100), 1000, TradeSide.BUY, settings, TaxCategory.STOCK)

        assertEquals(FeeSettings(), settings)
        assertEquals(142, result?.fee)
    }

    // the three values together

    @Test
    fun anInvalidDiscountIsRefused() {
        for (text in listOf("0", "11", "2.855", "abc", "")) {
            assertRejected(FeeSettingsException.Reason.INVALID_DISCOUNT) { FeeSettings.parse(text, "20", "1") }
        }
    }

    @Test
    fun anInvalidWholeLotMinimumFeeIsRefused() {
        for (text in listOf("-1", "20.5", "abc", "", "2147483648")) {
            assertRejected(FeeSettingsException.Reason.INVALID_WHOLE_LOT_MINIMUM_FEE) {
                FeeSettings.parse("2.8", text, "1")
            }
        }
    }

    @Test
    fun anInvalidOddLotMinimumFeeIsRefused() {
        for (text in listOf("-1", "20.5", "abc", "", "2147483648")) {
            assertRejected(FeeSettingsException.Reason.INVALID_ODD_LOT_MINIMUM_FEE) {
                FeeSettings.parse("2.8", "20", text)
            }
        }
    }

    @Test
    fun aBadDiscountIsReportedBeforeTheMinimumFees() {
        assertRejected(FeeSettingsException.Reason.INVALID_DISCOUNT) { FeeSettings.parse("11", "-1", "abc") }
    }

    @Test
    fun aBadWholeLotMinimumFeeIsReportedBeforeTheOddLotOne() {
        assertRejected(FeeSettingsException.Reason.INVALID_WHOLE_LOT_MINIMUM_FEE) {
            FeeSettings.parse("2.8", "-1", "abc")
        }
    }

    /** Collects what [FeeSettings.writeTo] writes, as SharedPreferences would store it. */
    private class MapWriter(val values: MutableMap<String, Any> = mutableMapOf()) : FeeSettings.PreferenceWriter {
        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun putInt(key: String, value: Int) {
            values[key] = value
        }
    }

    @Test
    fun validValuesAreStoredTogetherAndReadBack() {
        val settings = FeeSettings.parse("2.80", "30", "5")
        val writer = MapWriter(mutableMapOf("fee_custom" to "30", FeeSettings.LEGACY_DISCOUNT_PERCENT_KEY to "60"))

        settings.writeTo(writer)
        val read = FeeSettings.fromStored(writer.values)

        assertEquals(
            mapOf(
                "fee_custom" to "30",
                FeeSettings.LEGACY_DISCOUNT_PERCENT_KEY to "60",
                FeeSettings.DISCOUNT_KEY to "2.8",
                FeeSettings.WHOLE_LOT_MINIMUM_KEY to 30,
                FeeSettings.ODD_LOT_MINIMUM_KEY to 5,
            ),
            writer.values,
        )
        assertEquals(settings, read)
        assertDecimal("2.8", read.discount)
        assertEquals(30, read.wholeLotMinimumFee)
        assertEquals(5, read.oddLotMinimumFee)
    }

    @Test
    fun noDiscountIsStoredAsTenNotInScientificNotation() {
        val writer = MapWriter()

        FeeSettings.parse("10.0", "20", "1").writeTo(writer)

        assertEquals("10", writer.values[FeeSettings.DISCOUNT_KEY])
    }

    @Test
    fun settingsThatDifferOnlyInTrailingZerosAreEqual() {
        val a = FeeSettings(BigDecimal("10.0"))
        val b = FeeSettings(BigDecimal.TEN)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // summary

    @Test
    fun theSummaryShowsTheDiscountAndBothMinimumFees() {
        assertEquals("2.8 折・最低 20 / 1 元", FeeSettings(BigDecimal("2.80")).summaryText)
    }

    @Test
    fun theSummaryOfNoDiscountSaysSo() {
        assertEquals("無折扣・最低 20 / 1 元", FeeSettings(BigDecimal("10.0")).summaryText)
        assertEquals("無折扣・最低 20 / 1 元", FeeSettings.fromStored(emptyMap<String, Any>()).summaryText)
    }

    @Test
    fun aMinimumFeeOfZeroIsShownAsZero() {
        assertEquals("6 折・最低 0 / 5 元", FeeSettings(BigDecimal(6), 0, 5).summaryText)
    }

    @Test
    fun theEarlierPercentageIsSummarizedAsADiscount() {
        assertEquals(
            "6 折・最低 20 / 1 元",
            FeeSettings.fromStored(mapOf(FeeSettings.LEGACY_DISCOUNT_PERCENT_KEY to "60")).summaryText,
        )
    }
}
