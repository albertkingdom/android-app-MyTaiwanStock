package com.example.mynewsapp.tradecost

import java.math.BigDecimal

/**
 * Text typed in the trade fields. Only ASCII digits are accepted: Kotlin's `toIntOrNull` would also take
 * full-width digits such as "５", and `BigDecimal` would take "1e3".
 */
object TradeInput {
    /** A plain number: digits and at most one point, with at least one digit. Not checked to be above 0. */
    fun parsePrice(text: String?): BigDecimal? {
        val trimmed = text?.trim() ?: return null
        return if (isPlainDecimal(trimmed)) BigDecimal(trimmed) else null
    }

    /** A whole number of shares from 1 to 2147483647. */
    fun parseShares(text: String?): Int? = parseWholeNumber(text)?.takeIf { it >= 1 }

    /** A fee or a tax: a whole number of dollars from 0 to 2147483647. */
    fun parseFeeOrTax(text: String?): Int? = parseWholeNumber(text)

    internal fun isPlainDecimal(text: String): Boolean =
        text.isNotEmpty() &&
            text.all { it in '0'..'9' || it == '.' } &&
            text.count { it == '.' } <= 1 &&
            text.any { it in '0'..'9' }

    private fun parseWholeNumber(text: String?): Int? {
        val trimmed = text?.trim() ?: return null
        if (trimmed.isEmpty() || !trimmed.all { it in '0'..'9' }) return null
        // Digits only, so the value is not negative; more than 10 digits is above Int.MAX_VALUE.
        if (trimmed.trimStart('0').length > 10) return null
        val value = trimmed.toLong()
        return if (value <= Int.MAX_VALUE) value.toInt() else null
    }
}
