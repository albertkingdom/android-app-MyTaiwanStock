package com.example.mynewsapp.ui.tradeimport.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLineSplitterTest {

    private fun e(text: String, left: Int, right: Int, top: Int = 500, bottom: Int = 550) =
        PixelElement(text, left, top, right, bottom)

    @Test
    fun wide_column_gaps_split_one_line_into_boxes() {
        // Line height 50, so gaps above 25 px split.
        val line = listOf(e("2026/", 40, 158), e("元大台灣50", 205, 435), e("112.55", 552, 688), e("-563", 870, 960))

        val boxes = TextLineSplitter.split(line, 1034, 2000)

        assertEquals(listOf("2026/", "元大台灣50", "112.55", "-563"), boxes.map { it.text })
    }

    @Test
    fun small_gaps_join_words_with_a_space() {
        val line = listOf(e("元大", 205, 300), e("台灣50", 310, 435), e("1,125", 552, 640), e(".50", 645, 688))

        assertEquals(listOf("元大 台灣50", "1,125 .50"), TextLineSplitter.split(line, 1034, 2000).map { it.text })
    }

    @Test
    fun chinese_text_and_a_number_are_separate_boxes_even_when_close() {
        val line = listOf(
            e("09/30", 40, 158), e("盤中零股買進", 165, 443), e("5", 450, 470),
        )

        assertEquals(listOf("09/30", "盤中零股買進", "5"), TextLineSplitter.split(line, 1034, 2000).map { it.text })
    }

    @Test
    fun close_lower_line_words_still_parse_end_to_end() {
        // The trade type and the shares are closer than half a line height.
        val page = RecognizedPage(
            1034, 2000,
            listOf(
                listOf(e("2026/", 40, 158), e("元大台灣", 205, 380), e("50", 385, 435), e("112.55", 552, 688), e("-563", 870, 960)),
                listOf(
                    e("09/30", 40, 158, 585, 632), e("盤中零股買進", 197, 443, 585, 632),
                    e("5", 450, 470, 585, 632), e("--", 936, 960, 585, 632),
                ),
            ),
        )

        val trade = TradeScreenshotParser.parse(TextLineSplitter.boxes(page)).single()

        assertEquals("元大台灣50", trade.stockName)
        assertEquals(5, trade.amount)
        assertEquals(112.55, trade.price)
    }

    @Test
    fun a_gap_of_exactly_half_the_line_height_joins() {
        val line = listOf(e("A", 0, 100), e("B", 125, 200), e("C", 226, 300))

        assertEquals(listOf("A B", "C"), TextLineSplitter.split(line, 1000, 1000).map { it.text })
    }

    @Test
    fun words_are_ordered_left_to_right_before_measuring_gaps() {
        val line = listOf(e("-563", 870, 960), e("2026/", 40, 158), e("112.55", 552, 688))

        assertEquals(listOf("2026/", "112.55", "-563"), TextLineSplitter.split(line, 1034, 2000).map { it.text })
    }

    @Test
    fun pixel_rectangles_are_converted_like_the_fixtures() {
        val box = TextLineSplitter.toTextBox("2026/", 40, 500, 158, 550, 1034, 2000)
        val expected = TradeScreenshotFixtures.boxes(listOf(TradeScreenshotFixtures.px("2026/", 40, 500, 158, 550)), TradeScreenshotFixtures.collapsedPage).single()

        assertEquals(expected, box)
    }

    @Test
    fun merged_box_covers_all_its_words() {
        val box = TextLineSplitter.split(listOf(e("統一", 205, 300, 505, 552), e("FANG+", 310, 435, 500, 550)), 1034, 2000).single()

        assertEquals(TextLineSplitter.toTextBox("統一 FANG+", 205, 500, 435, 552, 1034, 2000), box)
    }

    @Test
    fun empty_line_or_image_gives_no_box() {
        assertTrue(TextLineSplitter.split(emptyList(), 100, 100).isEmpty())
        assertTrue(TextLineSplitter.split(listOf(e("A", 0, 10)), 0, 100).isEmpty())
    }

    @Test
    fun whole_rows_from_a_recognizer_parse_end_to_end() {
        // A recognizer that returns each row of the collapsed screenshot as one line.
        val page = RecognizedPage(
            1034, 2000,
            listOf(
                listOf(e("2026/", 40, 158), e("元大台灣50", 205, 435), e("112.55", 552, 688), e("-563", 870, 960)),
                listOf(
                    e("09/30", 40, 158, 585, 632), e("盤中零股買進", 197, 443, 585, 632),
                    e("5", 665, 688, 585, 632), e("--", 936, 960, 585, 632),
                ),
            ),
        )

        val trade = TradeScreenshotParser.parse(TextLineSplitter.boxes(page)).single()

        assertEquals("元大台灣50", trade.stockName)
        assertEquals(112.55, trade.price)
        assertEquals(5, trade.amount)
        assertEquals("2026-09-30", trade.date.toString())
    }

    @Test
    fun fixture_export_escapes_text_and_keeps_every_value() {
        val text = TextLineSplitter.toKotlinFixture(
            listOf(RecognizedPage(10, 20, listOf(listOf(PixelElement("a\"b$", 1, 2, 3, 4))))),
        )

        assertTrue(text, text.contains("RecognizedPage(10, 20, listOf("))
        assertTrue(text, text.contains("PixelElement(\"a\\\"b\\$\", 1, 2, 3, 4)"))
    }
}
