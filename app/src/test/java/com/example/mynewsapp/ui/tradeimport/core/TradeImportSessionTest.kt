package com.example.mynewsapp.ui.tradeimport.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Ported from the iOS app's `TradeImportViewModelTests.swift` without the fee and tax cases,
 * plus the Android-specific list and price rules.
 */
class TradeImportSessionTest {

    private val entries = listOf(
        StockEntry("0050", "元大台灣50"), StockEntry("2344", "華邦電"),
        StockEntry("2330", "台積電"), StockEntry("2379", "瑞昱"),
    )
    private val myList = ListChoice(1, "我的清單")
    private val today = LocalDate.of(2026, 11, 2)

    private fun day(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    private fun taipeiMillis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(TAIPEI).toInstant().toEpochMilli()

    private fun parsed(
        name: String = "元大台灣50",
        price: Double? = 112.55,
        amount: Int? = 5,
        date: LocalDate? = day(2026, 9, 30),
        side: TradeSide = TradeSide.BUY,
        type: String = "盤中零股買進",
        supported: Boolean = true,
    ) = ParsedTrade(name, date, price, amount, side, type, supported)

    private fun existing(
        stockNo: String = "0050",
        millis: Long = taipeiMillis(2026, 9, 30),
        price: Double = 112.55,
        amount: Int = 5,
        side: TradeSide = TradeSide.BUY,
    ) = ExistingTrade(stockNo, millis, price, amount, side)

    private fun session(
        trades: List<ParsedTrade>,
        existing: List<ExistingTrade> = emptyList(),
        lists: List<ListChoice> = listOf(myList),
        current: Int? = myList.id,
    ): TradeImportSession {
        storedRecords = existing
        val codes = entries.map { it.code }.toSet()
        return TradeImportSession(trades, StockNameResolver(entries), { it in codes }, existing, lists, current, today)
    }

    /** The records the latest session was opened with; still stored when the user confirms. */
    private var storedRecords: List<ExistingTrade> = emptyList()

    private fun TradeImportSession.statuses() = items.map { it.status }
    private fun TradeImportSession.selections() = items.map { it.isSelected }
    /** Used timestamps come from stored records; a stock outside the preview keeps the statuses unchanged. */
    private fun TradeImportSession.plan(used: Set<Long> = emptySet(), inLists: Set<String> = emptySet()) =
        buildImportPlan(storedRecords + used.map { existing(stockNo = "9999", millis = it) }, inLists)

    // The editor saves every field at once through applyEdit; these change one field the same way.
    private fun TradeImportSession.editItem(id: Int, change: (PreviewItem) -> PreviewItem) {
        val edited = change(items.first { it.id == id })
        applyEdit(id, edited.stockNo.orEmpty(), edited.price, edited.amount, edited.date, edited.side)
    }
    private fun TradeImportSession.setStockNo(id: Int, text: String) {
        val item = items.first { it.id == id }
        applyEdit(id, text, item.price, item.amount, item.date, item.side)
    }
    private fun TradeImportSession.setPrice(id: Int, price: Double?) = editItem(id) { it.copy(price = price) }
    private fun TradeImportSession.setAmount(id: Int, amount: Int?) = editItem(id) { it.copy(amount = amount) }
    private fun TradeImportSession.setDate(id: Int, date: LocalDate?) = editItem(id) { it.copy(date = date) }
    private fun TradeImportSession.setSide(id: Int, side: TradeSide) = editItem(id) { it.copy(side = side) }

    // statuses

    @Test
    fun complete_trade_is_importable_and_selected() {
        val s = session(listOf(parsed()))

        assertEquals(PreviewStatus.IMPORTABLE, s.items[0].status)
        assertTrue(s.items[0].isSelected)
        assertEquals("0050", s.items[0].stockNo)
    }

    @Test
    fun existing_record_makes_a_duplicate_that_is_not_selected_but_can_be_selected() {
        // Stored with a time of day, as a manual entry would be.
        val s = session(listOf(parsed()), existing = listOf(existing(millis = taipeiMillis(2026, 9, 30, 14, 3))))

        assertEquals(PreviewStatus.DUPLICATE, s.items[0].status)
        assertFalse(s.items[0].isSelected)

        s.toggleSelection(s.items[0].id)

        assertTrue(s.items[0].isSelected)
    }

    @Test
    fun duplicate_key_examples() {
        val s = session(
            listOf(
                parsed(), // same trade
                parsed(side = TradeSide.SELL, type = "盤中零股賣出"), // other direction
                parsed(amount = 6), // other shares
                parsed(date = day(2026, 10, 1)), // other day
            ),
            existing = listOf(existing()),
        )

        assertEquals(
            listOf(PreviewStatus.DUPLICATE, PreviewStatus.IMPORTABLE, PreviewStatus.IMPORTABLE, PreviewStatus.IMPORTABLE),
            s.statuses(),
        )
    }

    @Test
    fun record_synced_from_ios_with_a_float_price_is_a_duplicate() {
        val s = session(listOf(parsed()), existing = listOf(existing(price = 112.55f.toDouble())))

        assertEquals(PreviewStatus.DUPLICATE, s.items[0].status)
    }

    @Test
    fun different_prices_are_not_duplicates() {
        val s = session(listOf(parsed(price = 112.56)), existing = listOf(existing(price = 112.55f.toDouble())))

        assertEquals(PreviewStatus.IMPORTABLE, s.items[0].status)
    }

    @Test
    fun same_trade_twice_in_a_batch_marks_the_second_as_duplicate() {
        val s = session(listOf(parsed(), parsed()))

        assertEquals(listOf(PreviewStatus.IMPORTABLE, PreviewStatus.DUPLICATE), s.statuses())
        assertEquals(listOf(true, false), s.selections())
    }

    @Test
    fun incomplete_trades_cannot_be_selected() {
        val s = session(
            listOf(
                parsed("不存在的股票"), parsed(price = null), parsed(amount = null), parsed(price = 0.0),
                parsed(date = null),
            ),
        )

        assertEquals(setOf(PreviewStatus.INCOMPLETE), s.statuses().toSet())
        assertTrue(s.items.none { it.isSelected })

        s.toggleSelection(s.items[0].id)

        assertFalse(s.items[0].isSelected)
    }

    @Test
    fun a_trade_dated_after_today_is_incomplete() {
        // For example a misread year.
        val s = session(listOf(parsed(date = today), parsed(date = today.plusDays(1)), parsed(date = day(2028, 9, 30))))

        assertEquals(listOf(PreviewStatus.IMPORTABLE, PreviewStatus.INCOMPLETE, PreviewStatus.INCOMPLETE), s.statuses())
    }

    @Test
    fun share_count_must_be_between_1_and_32767() {
        val s = session(listOf(0, 1, 32767, 32768, 33000).map { parsed(amount = it) })

        assertEquals(
            listOf(
                PreviewStatus.INCOMPLETE, PreviewStatus.IMPORTABLE, PreviewStatus.IMPORTABLE,
                PreviewStatus.INCOMPLETE, PreviewStatus.INCOMPLETE,
            ),
            s.statuses(),
        )
    }

    @Test
    fun unsupported_type_is_not_selectable() {
        val s = session(listOf(parsed(type = "融資買進", supported = false)))

        assertEquals(PreviewStatus.UNSUPPORTED, s.items[0].status)
        s.toggleSelection(s.items[0].id)
        assertFalse(s.items[0].isSelected)
    }

    @Test
    fun status_priority_is_unsupported_then_incomplete_then_duplicate() {
        val s = session(
            listOf(
                parsed("不存在的股票", type = "融資買進", supported = false), // unsupported and incomplete
                parsed(amount = 33000), // incomplete
                parsed(), // duplicate
            ),
            existing = listOf(existing()),
        )

        assertEquals(listOf(PreviewStatus.UNSUPPORTED, PreviewStatus.INCOMPLETE, PreviewStatus.DUPLICATE), s.statuses())
    }

    // editing

    @Test
    fun entering_an_unknown_stock_number_keeps_the_trade_incomplete() {
        val s = session(listOf(parsed("不存在的股票")))

        s.setStockNo(s.items[0].id, "23")

        assertEquals(PreviewStatus.INCOMPLETE, s.items[0].status)
        assertFalse(s.items[0].isSelected)
    }

    @Test
    fun entering_a_listed_stock_number_makes_it_importable_and_selects_it() {
        val s = session(listOf(parsed("不存在的股票")))

        s.setStockNo(s.items[0].id, "2344")

        assertEquals(PreviewStatus.IMPORTABLE, s.items[0].status)
        assertTrue(s.items[0].isSelected)
        assertEquals("2344", s.items[0].stockNo)
    }

    @Test
    fun stock_number_input_is_trimmed_and_upper_cased() {
        val codes = setOf("00635U")
        val s = TradeImportSession(listOf(parsed("不存在的股票")), StockNameResolver(emptyList()), { it in codes }, emptyList(), emptyList(), null, today)

        s.setStockNo(s.items[0].id, " 00635u ")

        assertEquals("00635U", s.items[0].stockNo)
        assertEquals(PreviewStatus.IMPORTABLE, s.items[0].status)

        s.setStockNo(s.items[0].id, "  ")

        assertNull(s.items[0].stockNo)
    }

    @Test
    fun a_typed_code_shows_its_directory_name() {
        val s = session(listOf(parsed("不存在的股票")))

        s.setStockNo(s.items[0].id, "2330")
        assertEquals("台積電", s.items[0].stockName)

        // An unknown code shows no name rather than the previous stock's name.
        s.setStockNo(s.items[0].id, "9999")
        assertEquals("", s.items[0].stockName)
    }

    @Test
    fun editor_number_input_rules() {
        assertEquals(1125.5, TradeImportSession.parsePrice(" 1,125.5 ")!!, 0.0)
        assertEquals(1000.0, TradeImportSession.parsePrice("1,000")!!, 0.0)
        // A decimal comma, as some locales' keyboards type it.
        assertEquals(112.55, TradeImportSession.parsePrice("112,55")!!, 0.0)
        assertEquals(112.5, TradeImportSession.parsePrice("112,5")!!, 0.0)
        assertNull(TradeImportSession.parsePrice("abc"))
        assertNull(TradeImportSession.parsePrice("Infinity"))
        assertNull(TradeImportSession.parsePrice("NaN"))
        assertNull(TradeImportSession.parsePrice("-5"))
        assertNull(TradeImportSession.parsePrice("1e3"))
        assertEquals(1000, TradeImportSession.parseAmount("1,000"))
        assertEquals(32768, TradeImportSession.parseAmount("32768"))
        assertNull(TradeImportSession.parseAmount("1.5"))
        assertNull(TradeImportSession.parseAmount("1,00"))
        assertNull(TradeImportSession.parseAmount(""))
    }

    @Test
    fun prices_are_shown_without_trailing_zeros_or_exponents() {
        assertEquals("580", TradeImportSession.formatPrice(580.0))
        assertEquals("112.55", TradeImportSession.formatPrice(112.55))
        assertEquals("2470", TradeImportSession.formatPrice(2470.0))
        assertEquals("12345678", TradeImportSession.formatPrice(12345678.0))
    }

    @Test
    fun edit_that_creates_a_duplicate_deselects_the_trade() {
        val s = session(listOf(parsed(amount = 6)), existing = listOf(existing()))
        assertTrue(s.items[0].isSelected)

        s.setAmount(s.items[0].id, 5)

        assertEquals(PreviewStatus.DUPLICATE, s.items[0].status)
        assertFalse(s.items[0].isSelected)
    }

    @Test
    fun filling_in_the_missing_date_makes_the_trade_selectable() {
        val s = session(listOf(parsed(date = null)))
        assertEquals(PreviewStatus.INCOMPLETE, s.items[0].status)

        s.setDate(s.items[0].id, day(2026, 9, 30))

        assertEquals(PreviewStatus.IMPORTABLE, s.items[0].status)
        assertTrue(s.items[0].isSelected)
    }

    @Test
    fun editing_an_earlier_trade_updates_later_duplicates() {
        val s = session(listOf(parsed(), parsed()))
        assertEquals(listOf(PreviewStatus.IMPORTABLE, PreviewStatus.DUPLICATE), s.statuses())

        s.setAmount(s.items[0].id, 7)

        assertEquals(listOf(PreviewStatus.IMPORTABLE, PreviewStatus.IMPORTABLE), s.statuses())
        assertEquals(listOf(true, true), s.selections())
    }

    @Test
    fun unchanged_status_keeps_the_users_choice() {
        val s = session(listOf(parsed(), parsed("華邦電", price = 179.5, amount = 3)))
        s.toggleSelection(s.items[0].id)

        s.setSide(s.items[1].id, TradeSide.SELL)

        assertEquals(listOf(false, true), s.selections())
    }

    @Test
    fun an_editor_save_is_applied_as_one_change() {
        // Changing only the stock number would make it a duplicate of the stored 2344 buy; changing the
        // side as well makes the final status the same as before, so the user's choice is kept.
        val s = session(listOf(parsed("台積電", price = 179.5, amount = 3)), existing = listOf(existing("2344", price = 179.5, amount = 3)))
        s.toggleSelection(s.items[0].id)
        assertFalse(s.items[0].isSelected)

        s.applyEdit(s.items[0].id, "2344", 179.5, 3, day(2026, 9, 30), TradeSide.SELL)

        assertEquals(PreviewStatus.IMPORTABLE, s.items[0].status)
        assertFalse(s.items[0].isSelected)
        assertEquals("2344", s.items[0].stockNo)
        assertEquals(TradeSide.SELL, s.items[0].side)
    }

    // import button

    @Test
    fun import_is_enabled_only_while_something_is_selected() {
        val s = session(listOf(parsed()))
        assertTrue(s.isImportEnabled)

        s.toggleSelection(s.items[0].id)

        assertFalse(s.isImportEnabled)
    }

    // target list

    @Test
    fun default_list_is_the_current_list() {
        val a = ListChoice(1, "A")
        val b = ListChoice(2, "B")
        val s = session(listOf(parsed()), lists = listOf(a, b), current = 2)

        assertEquals(b, s.selectedList)
        assertFalse(s.showsNoListNotice)
    }

    @Test
    fun unknown_current_list_falls_back_to_the_first_list() {
        val a = ListChoice(1, "A")
        val b = ListChoice(2, "B")

        assertEquals(a, session(listOf(parsed()), lists = listOf(a, b), current = 0).selectedList)
        assertEquals(a, session(listOf(parsed()), lists = listOf(a, b), current = null).selectedList)
    }

    @Test
    fun lists_with_the_same_name_are_told_apart_by_id() {
        val first = ListChoice(1, "Same")
        val second = ListChoice(2, "Same")
        val s = session(listOf(parsed()), lists = listOf(first, second), current = 2)

        assertEquals(second, s.selectedList)
        assertEquals(second, s.plan().list)
    }

    @Test
    fun without_any_list_the_default_is_no_list() {
        val s = session(listOf(parsed()), lists = emptyList(), current = null)

        assertNull(s.selectedList)
        assertTrue(s.showsNoListNotice)
    }

    @Test
    fun choosing_no_list_shows_the_notice_and_an_unknown_id_is_ignored() {
        val s = session(listOf(parsed()))

        s.selectList(99)
        assertEquals(myList, s.selectedList)

        s.selectList(null)
        assertNull(s.selectedList)
        assertTrue(s.showsNoListNotice)
    }

    // writing

    @Test
    fun confirm_writes_only_the_selected_trades() {
        val s = session(listOf(parsed(), parsed("華邦電", price = 179.5, amount = 3), parsed("台積電", price = 2470.0, amount = 1)))
        s.toggleSelection(s.items[2].id) // deselect 台積電

        val plan = s.plan()

        assertEquals(2, plan.records.size)
        assertEquals(listOf("0050", "2344"), plan.records.map { it.stockNo })
    }

    @Test
    fun odd_lot_buy_is_stored_in_shares_at_noon_taipei() {
        val record = session(listOf(parsed())).plan().records.single()

        assertEquals(NewRecord("0050", taipeiMillis(2026, 9, 30, 12), 112.55, 5, TradeSide.BUY), record)
        assertEquals(0, record.side.status)
    }

    @Test
    fun same_day_trades_get_distinct_timestamps_on_the_same_taipei_day() {
        val times = session(listOf(parsed(), parsed("華邦電", price = 179.5, amount = 3))).plan().records.map { it.epochMillis }
        val noon = taipeiMillis(2026, 9, 30, 12)

        assertEquals(listOf(noon, noon + 1), times)
    }

    @Test
    fun timestamp_already_used_by_a_stored_record_is_skipped() {
        val noon = taipeiMillis(2026, 9, 30, 12)

        val record = session(listOf(parsed())).plan(used = setOf(noon, noon + 1)).records.single()

        assertEquals(noon + 2, record.epochMillis)
    }

    @Test
    fun one_invalid_selected_trade_blocks_the_whole_import() {
        val s = session(listOf(parsed(), parsed("華邦電", price = 179.5, amount = 33000)))
        // Not reachable through the UI; the plan must still refuse.
        s.items = s.items.map { if (it.id == 1) it.copy(isSelected = true) else it }

        assertThrows(InvalidSelectionException::class.java) { s.plan() }
    }

    @Test
    fun a_record_stored_after_the_preview_opened_updates_the_preview_instead_of_writing() {
        // The same screenshot imported elsewhere while this preview was open.
        val s = session(listOf(parsed(), parsed("華邦電", price = 179.5, amount = 3)))
        assertEquals(listOf(true, true), s.selections())

        assertThrows(PreviewChangedException::class.java) {
            s.buildImportPlan(listOf(existing(millis = taipeiMillis(2026, 9, 30, 12))), emptySet())
        }

        assertEquals(listOf(PreviewStatus.DUPLICATE, PreviewStatus.IMPORTABLE), s.statuses())
        assertEquals(listOf(false, true), s.selections())
        // Confirming again with the same records writes only the remaining trade.
        val plan = s.buildImportPlan(listOf(existing(millis = taipeiMillis(2026, 9, 30, 12))), emptySet())
        assertEquals(listOf("2344"), plan.records.map { it.stockNo })
        assertEquals(taipeiMillis(2026, 9, 30, 12) + 1, plan.records.single().epochMillis)
    }

    @Test
    fun a_duplicate_selected_by_hand_is_written() {
        val s = session(listOf(parsed()), existing = listOf(existing()))
        s.toggleSelection(s.items[0].id)

        assertEquals(1, s.plan().records.size)
    }

    // adding stocks to the list

    @Test
    fun stocks_already_in_any_list_are_not_added() {
        val s = session(listOf(parsed(), parsed("華邦電", price = 179.5, amount = 3)))

        val plan = s.plan(inLists = setOf("0050"))

        assertEquals(listOf("2344"), plan.stocksToAdd)
        assertEquals(myList, plan.list)
    }

    @Test
    fun a_stock_with_two_trades_is_added_once() {
        val s = session(
            listOf(
                parsed("瑞昱", price = 757.0, amount = 10, side = TradeSide.SELL, type = "盤中零股賣出"),
                parsed("瑞昱", price = 750.0, amount = 10),
            ),
        )

        assertEquals(listOf("2379"), s.plan().stocksToAdd)
    }

    @Test
    fun deselected_trades_do_not_add_their_stock() {
        val s = session(listOf(parsed("台積電", price = 2470.0, amount = 1), parsed("華邦電", price = 179.5, amount = 3)))
        s.toggleSelection(s.items[0].id)

        val plan = s.plan(inLists = setOf("2344"))

        assertTrue(plan.stocksToAdd.isEmpty())
        assertEquals(listOf("2344"), plan.records.map { it.stockNo })
    }

    @Test
    fun choosing_no_list_writes_records_but_changes_no_list() {
        val s = session(listOf(parsed()))
        s.selectList(null)

        val plan = s.plan()

        assertEquals(1, plan.records.size)
        assertTrue(plan.stocksToAdd.isEmpty())
        assertNull(plan.list)
    }
}
