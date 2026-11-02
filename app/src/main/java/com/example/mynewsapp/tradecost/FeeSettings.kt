package com.example.mynewsapp.tradecost

import java.math.BigDecimal

/**
 * The brokerage fee settings chosen on the fee settings page. The fee is the standard rate multiplied by a
 * discount from 0.1 to 10, where 10 means no discount. The discount is kept without trailing zeros, so that
 * 10.0 and 10 are the same setting and are shown the same way.
 */
class FeeSettings(
    discount: BigDecimal = NO_DISCOUNT,
    val wholeLotMinimumFee: Int = DEFAULT_WHOLE_LOT_MINIMUM_FEE,
    val oddLotMinimumFee: Int = DEFAULT_ODD_LOT_MINIMUM_FEE,
) {
    val discount: BigDecimal = discount.stripTrailingZeros()

    /** The line the settings screen shows, for example "2.8 折・最低 20 / 1 元" or "無折扣・最低 20 / 1 元". */
    val summaryText: String
        get() {
            val discountText = if (discount.compareTo(NO_DISCOUNT) == 0) "無折扣" else "${discount.toPlainString()} 折"
            return "${discountText}・最低 $wholeLotMinimumFee / $oddLotMinimumFee 元"
        }

    /** Where [writeTo] writes, such as a SharedPreferences editor. */
    interface PreferenceWriter {
        fun putString(key: String, value: String)
        fun putInt(key: String, value: Int)
    }

    /** Writes the three values together, in the form [fromStored] reads. */
    fun writeTo(writer: PreferenceWriter) {
        writer.putString(DISCOUNT_KEY, discount.toPlainString())
        writer.putInt(WHOLE_LOT_MINIMUM_KEY, wholeLotMinimumFee)
        writer.putInt(ODD_LOT_MINIMUM_KEY, oddLotMinimumFee)
    }

    override fun equals(other: Any?): Boolean =
        other is FeeSettings &&
            discount == other.discount &&
            wholeLotMinimumFee == other.wholeLotMinimumFee &&
            oddLotMinimumFee == other.oddLotMinimumFee

    override fun hashCode(): Int = (discount.hashCode() * 31 + wholeLotMinimumFee) * 31 + oddLotMinimumFee

    override fun toString(): String =
        "FeeSettings(discount=${discount.toPlainString()}, wholeLotMinimumFee=$wholeLotMinimumFee, " +
            "oddLotMinimumFee=$oddLotMinimumFee)"

    companion object {
        val NO_DISCOUNT: BigDecimal = BigDecimal.TEN
        const val DEFAULT_WHOLE_LOT_MINIMUM_FEE = 20
        const val DEFAULT_ODD_LOT_MINIMUM_FEE = 1

        const val DISCOUNT_KEY = "fee_discount_decimal"
        const val WHOLE_LOT_MINIMUM_KEY = "fee_minimum_whole_lot"
        const val ODD_LOT_MINIMUM_KEY = "fee_minimum_odd_lot"

        /** The earlier list setting: a percentage of the standard fee from "10" to "100". Read, never written. */
        const val LEGACY_DISCOUNT_PERCENT_KEY = "fee_discount"

        private val MINIMUM_DISCOUNT = BigDecimal("0.1")

        /**
         * Reads the settings from stored preference values without writing anything. Without a valid saved
         * discount, the earlier percentage setting is read as that percentage divided by 10; without either,
         * there is no discount. A minimum fee that is not stored as an Int from 0 to 2147483647 is its default.
         * The earlier fixed fee ("fee_custom") is ignored.
         */
        fun fromStored(values: Map<String, *>): FeeSettings = FeeSettings(
            discount = storedDiscount(values),
            wholeLotMinimumFee = storedMinimumFee(values[WHOLE_LOT_MINIMUM_KEY], DEFAULT_WHOLE_LOT_MINIMUM_FEE),
            oddLotMinimumFee = storedMinimumFee(values[ODD_LOT_MINIMUM_KEY], DEFAULT_ODD_LOT_MINIMUM_FEE),
        )

        /**
         * A discount typed on the settings page: a plain number from 0.1 to 10 with at most two decimals.
         * Trailing zeros do not count, as on iOS: "2.800" is 2.8.
         */
        fun parseDiscount(text: String?): BigDecimal {
            val trimmed = text?.trim()
            if (trimmed == null || !TradeInput.isPlainDecimal(trimmed)) {
                throw FeeSettingsException(FeeSettingsException.Reason.INVALID_DISCOUNT)
            }
            val value = BigDecimal(trimmed).stripTrailingZeros()
            if (value < MINIMUM_DISCOUNT || value > NO_DISCOUNT || value.scale() > 2) {
                throw FeeSettingsException(FeeSettingsException.Reason.INVALID_DISCOUNT)
            }
            return value
        }

        /**
         * Validates the three values of the settings page together. The error names the first invalid value in
         * the order discount, whole-lot minimum fee, odd-lot minimum fee. Nothing is written here.
         */
        fun parse(discountText: String?, wholeLotMinimumText: String?, oddLotMinimumText: String?): FeeSettings {
            val discount = parseDiscount(discountText)
            val wholeLot = TradeInput.parseFeeOrTax(wholeLotMinimumText)
                ?: throw FeeSettingsException(FeeSettingsException.Reason.INVALID_WHOLE_LOT_MINIMUM_FEE)
            val oddLot = TradeInput.parseFeeOrTax(oddLotMinimumText)
                ?: throw FeeSettingsException(FeeSettingsException.Reason.INVALID_ODD_LOT_MINIMUM_FEE)
            return FeeSettings(discount, wholeLot, oddLot)
        }

        private fun storedDiscount(values: Map<String, *>): BigDecimal {
            val saved = values[DISCOUNT_KEY] as? String
            if (saved != null) {
                try {
                    return parseDiscount(saved)
                } catch (_: FeeSettingsException) {
                    // Fall through to the earlier setting.
                }
            }
            val percent = (values[LEGACY_DISCOUNT_PERCENT_KEY] as? String)?.trim()
            if (percent != null && percent.isNotEmpty() && percent.length <= 3 && percent.all { it in '0'..'9' }) {
                val value = percent.toInt()
                if (value in 1..100) return BigDecimal(value).divide(BigDecimal.TEN)
            }
            return NO_DISCOUNT
        }

        /** Only an Int counts: a String "20", a Float, a Long or a Boolean was not saved by this page. */
        private fun storedMinimumFee(value: Any?, default: Int): Int =
            if (value is Int && value >= 0) value else default
    }
}

class FeeSettingsException(val reason: Reason) : Exception(reason.message) {
    enum class Reason(val message: String) {
        INVALID_DISCOUNT("折數請輸入 0.1 到 10 的數字，最多兩位小數"),
        INVALID_WHOLE_LOT_MINIMUM_FEE("整股最低手續費請輸入 0 到 2147483647 的整數"),
        INVALID_ODD_LOT_MINIMUM_FEE("零股最低手續費請輸入 0 到 2147483647 的整數"),
    }
}
