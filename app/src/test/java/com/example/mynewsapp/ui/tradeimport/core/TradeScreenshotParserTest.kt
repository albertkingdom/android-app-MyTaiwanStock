package com.example.mynewsapp.ui.tradeimport.core

import com.example.mynewsapp.ui.tradeimport.core.TradeScreenshotFixtures.PixelBox
import com.example.mynewsapp.ui.tradeimport.core.TradeScreenshotFixtures.boxes
import com.example.mynewsapp.ui.tradeimport.core.TradeScreenshotFixtures.collapsedHeader
import com.example.mynewsapp.ui.tradeimport.core.TradeScreenshotFixtures.collapsedPage
import com.example.mynewsapp.ui.tradeimport.core.TradeScreenshotFixtures.px
import com.example.mynewsapp.ui.tradeimport.core.TradeScreenshotFixtures.row0Lower
import com.example.mynewsapp.ui.tradeimport.core.TradeScreenshotFixtures.row0Upper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from the iOS app's `TradeScreenshotParserTests.swift`, plus Android-only cases at the end. */
class TradeScreenshotParserTest {

    private val fixtures = TradeScreenshotFixtures

    private fun parse(boxes: List<TextBox>) = TradeScreenshotParser.parse(boxes)

    private fun summary(trade: ParsedTrade): String {
        val side = if (trade.side == TradeSide.BUY) "買" else "賣"
        return "${trade.stockName} $side ${trade.amount ?: "nil"} ${trade.price ?: "nil"} ${trade.date ?: "nil"}"
    }

    // collapsed and expanded screens

    @Test
    fun collapsed_screen_parses_both_odd_lot_buys() {
        val trades = parse(fixtures.collapsed)

        assertEquals(
            listOf("元大台灣50 買 5 112.55 2026-09-30", "華邦電 買 3 179.5 2026-10-01"),
            trades.map(::summary),
        )
        assertTrue(trades.all { it.isSupportedType })
        assertEquals(listOf("盤中零股買進", "盤中零股買進"), trades.map { it.tradeTypeText })
    }

    @Test
    fun expanded_screen_parses_a_buy_and_a_sell() {
        val trades = parse(fixtures.expanded)

        assertEquals(
            listOf("台積電 買 1 2470.0 2026-09-22", "瑞昱 賣 10 757.0 2026-09-23"),
            trades.map(::summary),
        )
    }

    @Test
    fun thousands_separator_is_removed() {
        val pixels = listOf(
            px("2026/", 40, 500, 158, 550), px("元大台灣50", 205, 500, 435, 550),
            px("1,125.50", 540, 500, 688, 550), px("-1,125,500", 840, 500, 960, 550),
            px("09/30", 40, 585, 158, 632), px("盤中零股買進", 197, 585, 443, 632),
            px("1,000", 600, 585, 688, 632), px("--", 936, 585, 960, 632),
        )

        assertEquals(listOf("元大台灣50 買 1000 1125.5 2026-09-30"), parse(boxes(pixels, collapsedPage)).map(::summary))
    }

    // broken layouts

    @Test
    fun top_row_cut_off_is_incomplete_and_borrows_nothing() {
        val trades = parse(fixtures.topRowCutOff)

        assertEquals(2, trades.size)
        assertEquals("", trades[0].stockName)
        assertNull(trades[0].price)
        assertNull(trades[0].date)
        assertEquals("華邦電 買 3 179.5 2026-10-01", summary(trades[1]))
    }

    @Test
    fun merged_text_boxes_still_parse() {
        assertEquals(listOf("元大台灣50 買 5 112.55 2026-09-30"), parse(fixtures.mergedBoxes).map(::summary))
    }

    @Test
    fun name_ending_with_plus_sign_parses() {
        val boxes = boxes(
            collapsedHeader + listOf(
                px("2026/", 40, 500, 158, 550), px("統一FANG+", 205, 500, 435, 550),
                px("112.55", 552, 500, 688, 550), px("-563", 870, 500, 960, 550),
            ) + row0Lower,
            collapsedPage,
        )

        assertEquals(listOf("統一FANG+ 買 5 112.55 2026-09-30"), parse(boxes).map(::summary))
    }

    @Test
    fun plus_sign_split_into_its_own_text_box_stays_in_the_name() {
        val boxes = boxes(
            collapsedHeader + listOf(
                px("2026/", 40, 500, 158, 550), px("統一FANG", 205, 500, 400, 550), px("+", 402, 500, 435, 550),
                px("112.55", 552, 500, 688, 550), px("-563", 870, 500, 960, 550),
            ) + row0Lower,
            collapsedPage,
        )

        assertEquals(listOf("統一FANG+ 買 5 112.55 2026-09-30"), parse(boxes).map(::summary))
    }

    @Test
    fun plus_sign_box_far_from_the_name_is_not_part_of_it() {
        val boxes = boxes(
            collapsedHeader + listOf(
                px("2026/", 40, 500, 158, 550), px("元大台灣50", 205, 500, 435, 550), px("+", 500, 500, 520, 550),
                px("112.55", 552, 500, 688, 550), px("1,234", 870, 500, 960, 550),
            ) + row0Lower,
            collapsedPage,
        )

        assertEquals(listOf("元大台灣50 買 5 112.55 2026-09-30"), parse(boxes).map(::summary))
    }

    @Test
    fun split_chinese_and_latin_name_fragments_resolve_to_the_stock_code() {
        val cases: List<List<PixelBox>> = listOf(
            listOf(px("統一", 205, 500, 300, 550), px("FANG", 305, 500, 400, 550), px("+", 402, 500, 435, 550)),
            listOf(px("統", 205, 500, 250, 550), px("—", 252, 500, 290, 550), px("FANG+", 292, 500, 435, 550)),
            listOf(px("統一", 205, 500, 300, 550), px("FANG+", 305, 500, 435, 550)),
        )
        val resolver = StockNameResolver(listOf(StockEntry("00757", "統一FANG+")))
        for (fragments in cases) {
            val boxes = boxes(
                listOf(
                    px("2026/", 40, 500, 158, 550),
                    px("144.20", 552, 500, 688, 550), px("-722", 870, 500, 960, 550),
                ) + fragments + row0Lower,
                collapsedPage,
            )
            val trades = parse(boxes)
            assertEquals(1, trades.size)
            assertEquals("00757", trades.first().let { resolver.resolve(it.stockName) })
            assertEquals(144.2, trades.first().price)
            assertEquals(5, trades.first().amount)
        }
    }

    @Test
    fun numeric_name_fragment_does_not_replace_the_price() {
        val boxes = boxes(
            listOf(
                px("2026/", 40, 500, 158, 550), px("元大台灣", 205, 500, 380, 550), px("50", 382, 500, 435, 550),
                px("112", 450, 500, 688, 550), px("-563", 870, 500, 960, 550),
            ) + row0Lower,
            collapsedPage,
        )
        val trades = parse(boxes)
        assertEquals("元大台灣50", trades.first().stockName)
        assertEquals(112.0, trades.first().price)
    }

    @Test
    fun user_fang_screenshot_vision_output_does_not_take_profit_loss_as_shares() {
        val trades = parse(fixtures.fangMissingShares)
        assertEquals(1, trades.size)
        assertEquals("統一FANG+", trades.first().stockName)
        assertEquals(144.2, trades.first().price)
        assertEquals("2026-10-20", trades.first().date.toString())
        // The recognizer missed 5 and read the profit/loss placeholder as 11.
        assertNull(trades.first().amount)
    }

    @Test
    fun wide_share_count_under_a_narrow_price_stays_in_the_share_column() {
        val boxes = boxes(
            listOf(
                px("2026/", 40, 500, 158, 550), px("台積電", 205, 500, 435, 550),
                px("9", 665, 500, 688, 550), px("9,000", 840, 500, 960, 550),
                px("10/20", 40, 585, 158, 632), px("盤中零股賣出", 197, 585, 443, 632),
                px("1,000", 560, 585, 688, 632), px("100", 870, 585, 960, 632),
            ),
            collapsedPage,
        )
        val trades = parse(boxes)
        assertEquals(9.0, trades.first().price)
        assertEquals(1000, trades.first().amount)
    }

    @Test
    fun missing_share_count_with_a_positive_profit_stays_incomplete() {
        val boxes = boxes(
            listOf(
                px("2026/", 40, 500, 158, 550), px("台積電", 205, 500, 435, 550),
                px("9", 665, 500, 688, 550), px("9,000", 840, 500, 960, 550),
                px("10/20", 40, 585, 158, 632), px("盤中零股賣出", 197, 585, 443, 632),
                px("100", 870, 585, 960, 632),
            ),
            collapsedPage,
        )
        assertNull(parse(boxes).first().amount)
    }

    // ignored text

    @Test
    fun summary_and_header_rows_alone_produce_no_trade() {
        assertTrue(parse(boxes(collapsedHeader, collapsedPage)).isEmpty())
    }

    @Test
    fun expanded_detail_text_does_not_create_or_change_trades() {
        val trades = parse(fixtures.expanded)

        assertEquals(2, trades.size)
        assertEquals("瑞昱", trades[1].stockName)
        assertEquals(10, trades[1].amount)
        assertEquals(757.0, trades[1].price)
    }

    // trade types

    @Test
    fun margin_trade_is_kept_but_marked_unsupported() {
        val trades = parse(fixtures.withMarginTrade)

        assertEquals(listOf("元大台灣50", "長榮"), trades.map { it.stockName })
        assertEquals(listOf(true, false), trades.map { it.isSupportedType })
        assertEquals("融資買進", trades[1].tradeTypeText)
        assertEquals("長榮 買 100 200.0 2026-10-02", summary(trades[1]))
    }

    @Test
    fun whole_lot_trades_are_unsupported_until_the_share_unit_is_confirmed() {
        for (type in listOf("現股買進", "現股賣出")) {
            val pixels = listOf(
                px("2026/", 40, 500, 158, 550), px("台積電", 205, 500, 435, 550),
                px("2470.00", 552, 500, 688, 550), px("-2,472,000", 830, 500, 960, 550),
                px("09/22", 40, 585, 158, 632), px(type, 215, 585, 425, 632),
                px("1", 665, 585, 688, 632), px("--", 936, 585, 960, 632),
            )

            val trades = parse(boxes(pixels, collapsedPage))

            assertEquals(type, listOf(false), trades.map { it.isSupportedType })
            assertEquals(listOf(type), trades.map { it.tradeTypeText })
        }
    }

    @Test
    fun all_supported_trade_types() {
        for (type in listOf("盤中零股買進", "盤中零股賣出", "盤後零股買進", "盤後零股賣出")) {
            val pixels = listOf(
                px("2026/", 40, 500, 158, 550), px("台積電", 205, 500, 435, 550),
                px("2470.00", 552, 500, 688, 550), px("-2,472", 870, 500, 960, 550),
                px("09/22", 40, 585, 158, 632), px(type, 197, 585, 443, 632),
                px("1", 665, 585, 688, 632), px("--", 936, 585, 960, 632),
            )

            val trades = parse(boxes(pixels, collapsedPage))

            assertEquals(type, 1, trades.size)
            assertTrue(type, trades[0].isSupportedType)
            assertEquals(type, if (type.endsWith("賣出")) TradeSide.SELL else TradeSide.BUY, trades[0].side)
        }
    }

    @Test
    fun unknown_trade_types_are_unsupported() {
        for (type in listOf("當沖買進", "定期定額買進", "融券賣出")) {
            val pixels = listOf(
                px("2026/", 40, 500, 158, 550), px("台積電", 205, 500, 435, 550),
                px("2470.00", 552, 500, 688, 550), px("-2,472", 870, 500, 960, 550),
                px("09/22", 40, 585, 158, 632), px(type, 197, 585, 443, 632),
                px("1", 665, 585, 688, 632), px("--", 936, 585, 960, 632),
            )

            assertEquals(type, listOf(false), parse(boxes(pixels, collapsedPage)).map { it.isSupportedType })
        }
    }

    // missing year

    @Test
    fun missing_year_leaves_date_empty_but_keeps_other_fields() {
        val trades = parse(fixtures.topRowCutOff)

        assertNull(trades[0].date)
        assertEquals(5, trades[0].amount)
    }

    // text boxes exactly as the iOS Vision framework returned them for the real screenshots

    @Test
    fun complete_six_trade_screenshot_resolves_all_names_and_upper_row_fields() {
        val trades = parse(fixtures.fullSixTrades)

        assertEquals(
            listOf(
                "元大台灣50 買 9 108.15 2026-10-19",
                "元大台灣50 買 10 107.9 2026-10-19",
                "元大台灣50 買 10 107.2 2026-10-20",
                "統一FANG+ 買 5 144.2 2026-10-20",
                "元大台灣50 買 10 107.05 2026-10-22",
                "華邦電 買 5 155.5 2026-10-22",
            ),
            trades.map(::summary),
        )
        val resolver = StockNameResolver(
            listOf(StockEntry("0050", "元大台灣50"), StockEntry("00757", "統一FANG+"), StockEntry("2344", "華邦電")),
        )
        assertEquals(
            listOf("0050", "0050", "0050", "00757", "0050", "2344"),
            trades.map { resolver.resolve(it.stockName) },
        )
    }

    @Test
    fun upper_row_search_does_not_cross_a_previous_trade_type() {
        val boxes = boxes(
            row0Upper + listOf(
                px("09/30", 40, 555, 158, 602), px("盤中零股買進", 197, 555, 443, 602),
                px("5", 665, 555, 688, 602),
                px("10/01", 40, 608, 158, 655), px("盤中零股買進", 197, 608, 443, 655),
                px("3", 665, 608, 688, 655),
            ),
            collapsedPage,
        )
        val trades = parse(boxes)
        assertEquals(2, trades.size)
        assertEquals("元大台灣50", trades[0].stockName)
        assertEquals("", trades[1].stockName)
        assertNull(trades[1].date)
        assertNull(trades[1].price)
        assertEquals(3, trades[1].amount)
    }

    @Test
    fun vision_output_of_the_collapsed_screenshot() {
        assertEquals(
            listOf("元大台灣50 買 5 112.55 2026-09-30", "華邦電 買 3 179.5 2026-10-01"),
            parse(fixtures.visionCollapsed).map(::summary),
        )
    }

    @Test
    fun vision_output_of_the_expanded_screenshot() {
        assertEquals(
            listOf("台積電 買 1 2470.0 2026-09-22", "瑞昱 賣 10 757.0 2026-09-23"),
            parse(fixtures.visionExpanded).map(::summary),
        )
    }

    // Android-only cases

    @Test
    fun full_width_digits_are_read_as_numbers() {
        val pixels = listOf(
            px("2026/", 40, 500, 158, 550), px("元大台灣５０", 205, 500, 435, 550),
            px("１１２.５５", 552, 500, 688, 550), px("-563", 870, 500, 960, 550),
            px("09/30", 40, 585, 158, 632), px("盤中零股買進", 197, 585, 443, 632),
            px("１０", 640, 585, 688, 632), px("--", 936, 585, 960, 632),
        )

        assertEquals(listOf("元大台灣50 買 10 112.55 2026-09-30"), parse(boxes(pixels, collapsedPage)).map(::summary))
    }

    @Test
    fun impossible_month_and_day_produce_no_date() {
        val pixels = listOf(
            px("2026/", 40, 500, 158, 550), px("元大台灣50", 205, 500, 435, 550),
            px("112.55", 552, 500, 688, 550), px("-563", 870, 500, 960, 550),
            px("02/30", 40, 585, 158, 632), px("盤中零股買進", 197, 585, 443, 632),
            px("5", 665, 585, 688, 632), px("--", 936, 585, 960, 632),
        )

        val trade = parse(boxes(pixels, collapsedPage)).single()
        assertNull(trade.date)
        assertEquals("元大台灣50", trade.stockName)
        assertEquals(5, trade.amount)
    }

    @Test
    fun a_stray_comma_box_is_not_taken_as_the_price() {
        val pixels = listOf(
            px("2026/", 40, 500, 158, 550), px("元大台灣50", 205, 500, 435, 550), px(",", 500, 500, 510, 550),
            px("112.55", 552, 500, 688, 550), px("-563", 870, 500, 960, 550),
        ) + row0Lower

        assertEquals(listOf("元大台灣50 買 5 112.55 2026-09-30"), parse(boxes(pixels, collapsedPage)).map(::summary))
    }
}
