package com.example.mynewsapp.use_case.chart

/**
 * Rows of the Taiwan Stock Exchange STOCK_DAY response:
 * 0 date, 1 shares traded, 2 value, 3 open, 4 high, 5 low, 6 close, 7 change, 8 transactions.
 * Numbers can contain thousands separators.
 */
object CandleRows {
    const val DATE = 0
    const val SHARES = 1
    const val VALUE = 2
    const val OPEN = 3
    const val HIGH = 4
    const val LOW = 5
    const val CLOSE = 6
    const val CHANGE = 7

    private val numericColumns = listOf(SHARES, VALUE, OPEN, HIGH, LOW, CLOSE)

    /** Concatenates two months (null counts as no rows) and drops rows that cannot be charted. */
    fun merge(lastMonth: List<List<String>>?, currentMonth: List<List<String>>?): List<List<String>> =
        sanitize(lastMonth.orEmpty() + currentMonth.orEmpty())

    /** Keeps rows that have every column and numeric shares, value and prices (drops "--" suspended days). */
    fun sanitize(rows: List<List<String>>): List<List<String>> =
        rows.filter { row -> row.size > CHANGE && numericColumns.all { number(row[it]) != null } }

    fun number(text: String): Float? = text.replace(",", "").trim().toFloatOrNull()

    /** The price change column, e.g. "+1.50" or "-2.25"; anything else (such as "X0.00") is 0. */
    fun priceChange(text: String): Float = number(text) ?: 0f
}
