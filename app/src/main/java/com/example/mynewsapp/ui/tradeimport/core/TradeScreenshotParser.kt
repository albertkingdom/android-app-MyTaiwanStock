package com.example.mynewsapp.ui.tradeimport.core

import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/**
 * Reads the trade list of the 投資先生 trade detail screen from positioned text boxes.
 *
 * Each trade is a two-line group: the upper line has the year fragment ("2026/"), stock name,
 * price and net amount; the lower line has the month/day, trade type, shares and profit or
 * loss. Lines are found by position, never by the order the text boxes come in. A larger y is
 * higher on the screen (see [TextBox]).
 *
 * Ported from the iOS app's `TradeScreenshotParser`. One intentional difference: every box text
 * is NFKC-normalized first, so full-width digits and signs are read as numbers.
 */
object TradeScreenshotParser {

    // Labels of the expanded detail block, the summary rows and the table header. A box with
    // one of these, and everything on its row, is not part of any trade.
    private val ignoredLabels = listOf(
        "委託書號", "持有成本", "價金", "手續費", "交易稅", "配息總額", "報酬率", "幣別", "沖抵明細",
        "總應收付", "總損益", "總價金", "總手續費", "總交易稅",
        "日期", "名稱", "價格/股數", "應收付/損益",
    )

    /**
     * Odd-lot trades only. Their share column is in shares. Whole-lot rows ("現股買進") are
     * recognized but flagged unsupported: it is unknown whether that column shows shares or lots.
     */
    val supportedTradeTypes: Set<String> = setOf(
        "盤中零股買進", "盤中零股賣出", "盤後零股買進", "盤後零股賣出",
    )

    /** Adjacent fragments can form one name within the name column; a fraction of the page width. */
    private const val NAME_FRAGMENT_GAP = 0.03

    /** The upper line must lie this many type-box heights above the lower line. */
    private const val UPPER_MIN = 0.5
    private const val UPPER_MAX = 2.5

    private val arrowTexts = setOf("V", "v", "^", "∨", "∧", "⌄", "⌃")

    private val anchorRegex = Regex("[\\u4E00-\\u9FFF]*(買進|賣出)$")
    private val numberRegex = Regex("^[-+]?(?=[\\d,]*\\d)[\\d,]+(\\.\\d+)?$")
    private val nameFragmentRegex = Regex("^[\\p{L}\\p{N}+＋\\-–—―─]+$")
    private val yearRegex = Regex("^(\\d{4})/")
    private val monthDayRegex = Regex("(\\d{1,2})/(\\d{1,2})")

    private class Line(val boxes: MutableList<TextBox>, val midY: Double, var height: Double)

    private class Anchor(val box: TextBox, val tradeType: String) {
        val height: Double get() = box.height
    }

    fun parse(boxes: List<TextBox>): List<ParsedTrade> {
        val normalized = boxes.map { it.copy(text = Normalizer.normalize(it.text, Normalizer.Form.NFKC)) }
        val lines = group(removeIgnored(normalized))
        val trades = mutableListOf<ParsedTrade>()
        for ((index, line) in lines.withIndex()) {
            val anchor = typeAnchor(line) ?: continue
            val upper = upperLine(index, lines, anchor.height)
            trades.add(makeTrade(line, anchor, upper))
        }
        return trades
    }

    // ignoring non-trade text

    private fun removeIgnored(boxes: List<TextBox>): List<TextBox> {
        val ignored = boxes.filter { box -> ignoredLabels.any { box.text.contains(it) } }
        return boxes.filter { box ->
            ignored.none { label -> abs(label.midY - box.midY) <= 0.5 * max(label.height, box.height) }
        }
    }

    // lines

    /**
     * Groups boxes into lines from top to bottom; boxes whose vertical centers are within half a
     * box height of the line share it. Each line is ordered left to right.
     */
    private fun group(boxes: List<TextBox>): List<Line> {
        val lines = mutableListOf<Line>()
        for (box in boxes.sortedByDescending { it.midY }) {
            val last = lines.lastOrNull()
            if (last != null && abs(last.midY - box.midY) <= 0.5 * max(last.height, box.height)) {
                last.boxes.add(box)
                last.height = max(last.height, box.height)
            } else {
                lines.add(Line(mutableListOf(box), box.midY, box.height))
            }
        }
        for (line in lines) line.boxes.sortBy { it.x }
        return lines
    }

    /** The trade type text ("盤中零股買進", "融資賣出"...) marks the lower line of a trade. */
    private fun typeAnchor(line: Line): Anchor? {
        for (box in line.boxes) {
            val match = anchorRegex.find(stripped(box.text)) ?: continue
            return Anchor(box, match.value)
        }
        return null
    }

    /** The nearest upper line, ignoring disclosure arrows outside the trade columns. */
    private fun upperLine(index: Int, lines: List<Line>, anchorHeight: Double): Line? {
        if (index <= 0) return null
        val lower = lines[index]
        val dataRightEdge = lower.boxes.maxOfOrNull { it.maxX } ?: 1.0
        for (candidateIndex in index - 1 downTo 0) {
            val candidate = lines[candidateIndex]
            val distance = (candidate.midY - lower.midY) / anchorHeight
            if (distance > UPPER_MAX) return null
            // The row's right-hand chevron can be recognized as a separate line of "V". Only skip
            // arrow-only lines beyond the data columns; never skip another trade.
            if (candidate.boxes.all { it.x > dataRightEdge && stripped(it.text) in arrowTexts }) continue
            if (distance < UPPER_MIN || candidate.boxes.none { yearFragment(stripped(it.text)) != null }) return null
            return candidate
        }
        return null
    }

    // building a trade

    private fun makeTrade(lower: Line, anchor: Anchor, upper: Line?): ParsedTrade {
        var name = ""
        var price: Double? = null
        var year: Int? = null
        var shareColumnRightEdge: Double? = null

        if (upper != null) {
            val numbers = mutableListOf<Pair<String, TextBox>>()
            var nameBox: TextBox? = null
            for (box in upper.boxes) {
                var text = stripped(box.text)
                val found = yearFragment(text)
                if (found != null) {
                    year = found.first
                    // The recognizer sometimes appends a stray enumeration comma to the year slash.
                    text = found.second.trim('、')
                }
                if (text.isEmpty()) continue
                val last = nameBox
                if (last != null && numbers.isEmpty() &&
                    box.x - last.maxX <= NAME_FRAGMENT_GAP &&
                    box.maxX <= anchor.box.maxX + NAME_FRAGMENT_GAP &&
                    isNameFragment(text)
                ) {
                    // Keep split Chinese, Latin, numeric and sign fragments in the name column.
                    name += text
                    nameBox = box
                } else if (isNumber(text)) {
                    numbers.add(text to box)
                } else if (name.isEmpty()) {
                    name = text
                    nameBox = box
                }
            }
            // Columns are price, then net amount; the net amount is not needed.
            price = numbers.firstOrNull()?.let { removingSeparators(it.first).toDoubleOrNull() }
            val priceBox = numbers.firstOrNull()?.second
            if (priceBox != null) {
                // Shares are right-aligned below the price, but can be wider than its text.
                // Separate their column from profit/loss using the gap between upper columns.
                shareColumnRightEdge = if (numbers.size > 1) {
                    (priceBox.maxX + numbers[1].second.x) / 2
                } else {
                    priceBox.maxX
                }
            }
        }

        var monthDay: Pair<Int, Int>? = null
        var shares: Int? = null
        var afterType = false
        for (box in lower.boxes) {
            val text = stripped(box.text)
            if (monthDay == null) monthDay = monthDayFragment(text)
            if (box === anchor.box) {
                afterType = true
                continue
            }
            if (afterType && shares == null && isNumber(text) && !text.contains(".")) {
                val rightEdge = shareColumnRightEdge
                if (rightEdge != null && !(box.midX > anchor.box.maxX && box.midX <= rightEdge)) continue
                shares = removingSeparators(text).toIntOrNull()
            }
        }

        val tradeType = anchor.tradeType
        return ParsedTrade(
            stockName = name,
            date = makeDate(year, monthDay),
            price = price,
            amount = shares,
            side = if (tradeType.endsWith("賣出")) TradeSide.SELL else TradeSide.BUY,
            tradeTypeText = tradeType,
            isSupportedType = tradeType in supportedTradeTypes,
        )
    }

    private fun makeDate(year: Int?, monthDay: Pair<Int, Int>?): LocalDate? {
        if (year == null || monthDay == null) return null
        return try {
            LocalDate.of(year, monthDay.first, monthDay.second)
        } catch (e: DateTimeException) {
            null
        }
    }

    // text helpers

    internal fun stripped(text: String): String = text.filterNot { it.isWhitespace() }

    private fun removingSeparators(text: String): String = text.replace(",", "")

    private fun isNumber(text: String): Boolean = numberRegex.matches(text)

    /** Stock-name characters, including the dash variants the resolver handles. */
    private fun isNameFragment(text: String): Boolean = nameFragmentRegex.matches(text)

    /** "2026/" or "2026/元大台灣50" (a merged text box) in stripped text: the year and the text after the slash. */
    private fun yearFragment(text: String): Pair<Int, String>? {
        val match = yearRegex.find(text) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        return year to text.substring(match.range.last + 1)
    }

    private fun monthDayFragment(text: String): Pair<Int, Int>? {
        val match = monthDayRegex.find(text) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        return month to day
    }
}
