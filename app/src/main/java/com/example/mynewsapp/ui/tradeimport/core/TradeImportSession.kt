package com.example.mynewsapp.ui.tradeimport.core

import java.time.Instant
import java.time.LocalDate

/** A stock list the imported stocks can be added to. Names are not unique, so the id identifies it. */
data class ListChoice(val id: Int, val name: String)

/** A buy/sell record already stored on the device. */
data class ExistingTrade(
    val stockNo: String,
    val epochMillis: Long,
    val price: Double,
    val amount: Int,
    val side: TradeSide,
)

enum class PreviewStatus { IMPORTABLE, DUPLICATE, INCOMPLETE, UNSUPPORTED }

data class PreviewItem(
    val id: Int,
    val stockName: String,
    val stockNo: String?,
    val date: LocalDate?,
    val price: Double?,
    val amount: Int?,
    val side: TradeSide,
    val tradeTypeText: String,
    val isSupportedType: Boolean,
    val status: PreviewStatus,
    val isSelected: Boolean,
)

/** A record to write: [epochMillis] is already moved to a free millisecond. */
data class NewRecord(
    val stockNo: String,
    val epochMillis: Long,
    val price: Double,
    val amount: Int,
    val side: TradeSide,
)

/** What a confirmed import writes. [list] is null when the stocks are not added to a list. */
data class ImportPlan(val records: List<NewRecord>, val stocksToAdd: List<String>, val list: ListChoice?)

/** A confirmation that failed before anything was written; the preview can be confirmed again. */
interface NothingWrittenFailure

/** A selected trade is not complete and valid; nothing is written. */
class InvalidSelectionException : Exception("A selected trade is incomplete or invalid"), NothingWrittenFailure

/** Records stored since the preview opened changed the preview; nothing is written. */
class PreviewChangedException : Exception("The preview changed; confirm again"), NothingWrittenFailure

/**
 * The preview of a screenshot import: every recognized trade with a status, whether it is
 * selected, and the list the imported stocks go into. Nothing is written here; [buildImportPlan]
 * says what to write. Ported from the non-UI part of the iOS app's `TradeImportViewModel`,
 * without the fee and the tax.
 */
class TradeImportSession(
    trades: List<ParsedTrade>,
    private val resolver: StockNameResolver,
    private val isKnownCode: (String) -> Boolean,
    existingTrades: List<ExistingTrade>,
    val lists: List<ListChoice>,
    currentListId: Int?,
    /** Today in Taipei; a trade dated after it is incomplete (a misread year, for example). */
    private val today: LocalDate = LocalDate.now(TAIPEI),
) {
    private var existingKeys: Set<TradeKey> = keysOf(existingTrades)

    var items: List<PreviewItem> = trades.mapIndexed { index, trade ->
        PreviewItem(
            id = index,
            stockName = trade.stockName,
            stockNo = resolver.resolve(trade.stockName),
            date = trade.date,
            price = trade.price,
            amount = trade.amount,
            side = trade.side,
            tradeTypeText = trade.tradeTypeText,
            isSupportedType = trade.isSupportedType,
            status = PreviewStatus.INCOMPLETE,
            isSelected = false,
        )
    }
        // Internal so tests can build states the UI cannot reach.
        internal set

    /** The current list when it exists, otherwise the first list, otherwise none. */
    var selectedList: ListChoice? = lists.firstOrNull { it.id == currentListId } ?: lists.firstOrNull()
        private set

    init {
        refreshStatuses(applyDefaultSelection = true)
    }

    // selection

    val isImportEnabled: Boolean get() = items.any { it.isSelected }

    fun toggleSelection(id: Int) {
        val item = items.firstOrNull { it.id == id } ?: return
        // Incomplete and unsupported trades can never be selected.
        if (item.status != PreviewStatus.IMPORTABLE && item.status != PreviewStatus.DUPLICATE) return
        replace(item.copy(isSelected = !item.isSelected))
    }

    // target list

    val showsNoListNotice: Boolean get() = selectedList == null

    /** Selects the list with [id], or "do not add to a list" for null. An unknown id is ignored. */
    fun selectList(id: Int?) {
        if (id == null) {
            selectedList = null
            return
        }
        selectedList = lists.firstOrNull { it.id == id } ?: return
    }

    // editing

    /**
     * Applies every field of the trade editor at once. The stock number text is trimmed and
     * upper-cased; empty means no stock number. The status is computed once, for the final
     * values, so a status that changes only in between does not change the selection.
     */
    fun applyEdit(id: Int, stockNoText: String, price: Double?, amount: Int?, date: LocalDate?, side: TradeSide) =
        edit(id) { item ->
            val stockNo = parseStockNo(stockNoText)
            // A typed code shows its directory name (empty when unknown), so the row never pairs a code
            // with another stock's name.
            val name = if (stockNo != item.stockNo) stockNo?.let(resolver::nameOf).orEmpty() else item.stockName
            item.copy(stockName = name, stockNo = stockNo, price = price, amount = amount, date = date, side = side)
        }

    private fun edit(id: Int, change: (PreviewItem) -> PreviewItem) {
        val item = items.firstOrNull { it.id == id } ?: return
        replace(change(item))
        refreshStatuses(applyDefaultSelection = false)
    }

    private fun replace(item: PreviewItem) {
        items = items.map { if (it.id == item.id) item else it }
    }

    // importing

    /**
     * Validates every selected trade and returns what to write. Throws [InvalidSelectionException]
     * when one is invalid, so a bad value never leaves a half-finished import behind.
     *
     * [existingTrades] and [stocksInAnyList] are read again when the user confirms. When records stored
     * since the preview opened (for example by another import of the same screenshot) change a status
     * or a selection, the preview is updated and [PreviewChangedException] is thrown instead.
     */
    fun buildImportPlan(existingTrades: List<ExistingTrade>, stocksInAnyList: Set<String>): ImportPlan {
        val freshKeys = keysOf(existingTrades)
        if (freshKeys != existingKeys) {
            val before = items
            existingKeys = freshKeys
            refreshStatuses(applyDefaultSelection = false)
            if (items != before) throw PreviewChangedException()
        }
        val usedTimestamps = existingTrades.map { it.epochMillis }.toSet()
        val selected = items.filter { it.isSelected }
        if (!selected.all { it.isSupportedType && isComplete(it, today) && isKnownCode(it.stockNo!!) }) {
            throw InvalidSelectionException()
        }

        val used = usedTimestamps.toMutableSet()
        val records = selected.map { item ->
            NewRecord(
                stockNo = item.stockNo!!,
                epochMillis = storedMillis(item.date!!, used),
                price = item.price!!,
                amount = item.amount!!,
                side = item.side,
            )
        }
        val list = selectedList
        val stocksToAdd = if (list == null) {
            emptyList()
        } else {
            // A stock lives in one list on this device; one that is already in any list is not moved.
            records.map { it.stockNo }.distinct().filter { it !in stocksInAnyList }
        }
        return ImportPlan(records, stocksToAdd, list)
    }

    // statuses

    private data class TradeKey(
        val stockNo: String,
        val day: LocalDate,
        val price: Float,
        val amount: Int,
        val side: TradeSide,
    )

    /**
     * Recomputes every status. A trade whose status changed is selected when it became importable
     * and deselected otherwise; a trade whose status is unchanged keeps its choice.
     */
    private fun refreshStatuses(applyDefaultSelection: Boolean) {
        val batchKeys = mutableSetOf<TradeKey>()
        items = items.map { item ->
            val newStatus = when {
                !item.isSupportedType -> PreviewStatus.UNSUPPORTED
                !isComplete(item, today) || !isKnownCode(item.stockNo!!) -> PreviewStatus.INCOMPLETE
                else -> {
                    val tradeKey = key(item.stockNo!!, item.date!!, item.price!!, item.amount!!, item.side)
                    val isDuplicate = tradeKey in existingKeys || tradeKey in batchKeys
                    batchKeys.add(tradeKey)
                    if (isDuplicate) PreviewStatus.DUPLICATE else PreviewStatus.IMPORTABLE
                }
            }
            val changed = newStatus != item.status
            item.copy(
                status = newStatus,
                isSelected = if (applyDefaultSelection || changed) newStatus == PreviewStatus.IMPORTABLE else item.isSelected,
            )
        }
    }

    companion object {
        /** `InvestHistory.amount` is read by the iOS app as a 16-bit integer. */
        val VALID_SHARES = 1..Short.MAX_VALUE.toInt()

        fun isComplete(item: PreviewItem, today: LocalDate): Boolean {
            val price = item.price
            val amount = item.amount
            val date = item.date
            return item.stockNo != null && date != null && !date.isAfter(today) &&
                price != null && price > 0 &&
                amount != null && amount in VALID_SHARES
        }

        /**
         * iOS stores prices as Float and uploads them widened to Double (112.55 becomes
         * 112.55000305175781), so prices are compared as Float.
         */
        private fun key(stockNo: String, day: LocalDate, price: Double, amount: Int, side: TradeSide) =
            TradeKey(stockNo, day, price.toFloat(), amount, side)

        private fun keysOf(trades: List<ExistingTrade>): Set<TradeKey> =
            trades.map { key(it.stockNo, dayOf(it.epochMillis), it.price, it.amount, it.side) }.toSet()

        private fun dayOf(epochMillis: Long): LocalDate =
            Instant.ofEpochMilli(epochMillis).atZone(TAIPEI).toLocalDate()

        /** Noon on [day] in Taipei, moved forward one millisecond at a time until unused. */
        fun storedMillis(day: LocalDate, used: MutableSet<Long>): Long {
            var candidate = day.atTime(12, 0).atZone(TAIPEI).toInstant().toEpochMilli()
            while (!used.add(candidate)) candidate += 1
            return candidate
        }

        /** Editor input: trimmed and upper-cased; empty means no stock number. */
        fun parseStockNo(text: String): String? = text.trim().uppercase().ifEmpty { null }

        private val priceInput = Regex("^\\d+(\\.\\d+)?$")
        private val decimalComma = Regex("^\\d+,\\d{1,2}$")
        private val amountInput = Regex("^\\d{1,3}(,\\d{3})*$|^\\d+$")

        /**
         * Editor input: trimmed; a single comma followed by one or two digits is a decimal mark (keyboards
         * of some locales type it), any other comma is a thousands separator. Anything that is not a plain
         * decimal number, such as "Infinity", means no value.
         */
        fun parsePrice(text: String): Double? {
            val trimmed = text.trim()
            val normalized = if (decimalComma.matches(trimmed)) trimmed.replace(',', '.') else trimmed.replace(",", "")
            return if (priceInput.matches(normalized)) normalized.toDoubleOrNull() else null
        }

        /** Editor input: trimmed whole number, thousands separators allowed; anything else means no value. */
        fun parseAmount(text: String): Int? {
            val trimmed = text.trim()
            return if (amountInput.matches(trimmed)) trimmed.replace(",", "").toIntOrNull() else null
        }

        /** A price as people write it: no trailing zeros and never in scientific notation ("580", "112.55"). */
        fun formatPrice(price: Double): String =
            java.math.BigDecimal(price.toString()).stripTrailingZeros().toPlainString()
    }
}
