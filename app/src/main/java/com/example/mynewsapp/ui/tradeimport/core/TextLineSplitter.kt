package com.example.mynewsapp.ui.tradeimport.core

import kotlin.math.max
import kotlin.math.min

/** One recognized word with its pixel rectangle (origin at the top left, as the recognizer reports it). */
data class PixelElement(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/** The raw recognizer output of one image: its upright size and the words of each recognized line. */
data class RecognizedPage(val width: Int, val height: Int, val lines: List<List<PixelElement>>)

/**
 * Turns recognizer lines into the parser's text boxes.
 *
 * A recognizer line can span several columns of the trade list (for example
 * "2026/ 元大台灣50 112.55 -563"), which the parser cannot read as one box. The words of a line
 * are ordered left to right and split into separate boxes wherever the gap between neighbouring
 * words is more than [MAX_GAP_IN_LINE_HEIGHTS] times the line height, and wherever Chinese text
 * meets a number (for example the trade type "盤中零股買進" and the shares "5"). Other words closer
 * than that are joined with a space. A stock name split this way ("元大台灣" and "50") is joined
 * again by the parser's name-fragment rule.
 */
object TextLineSplitter {

    const val MAX_GAP_IN_LINE_HEIGHTS = 0.5

    fun boxes(page: RecognizedPage): List<TextBox> = page.lines.flatMap { split(it, page.width, page.height) }

    fun split(line: List<PixelElement>, imageWidth: Int, imageHeight: Int): List<TextBox> {
        if (line.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return emptyList()
        val sorted = line.sortedBy { it.left }
        val lineHeight = sorted.maxOf { it.bottom } - sorted.minOf { it.top }
        val maxGap = MAX_GAP_IN_LINE_HEIGHTS * lineHeight

        val groups = mutableListOf(mutableListOf(sorted.first()))
        var groupRight = sorted.first().right
        for (element in sorted.drop(1)) {
            val current = groups.last()
            val gap = element.left - groupRight
            if (gap <= maxGap && !isScriptBoundary(current.last().text, element.text)) {
                current.add(element)
                groupRight = max(groupRight, element.right)
            } else {
                groups.add(mutableListOf(element))
                groupRight = element.right
            }
        }
        return groups.map { group ->
            toTextBox(
                text = group.joinToString(" ") { it.text },
                left = group.minOf { it.left },
                top = group.minOf { it.top },
                right = group.maxOf { it.right },
                bottom = group.maxOf { it.bottom },
                imageWidth = imageWidth,
                imageHeight = imageHeight,
            )
        }
    }

    private fun isCjk(c: Char) = c in '\u4E00'..'\u9FFF'
    private fun isNumberStart(c: Char) = c.isDigit() || c == '-' || c == '+' || c == '.' || c == ','

    /** Chinese followed by a number, or a number followed by Chinese, starts a new box. */
    private fun isScriptBoundary(previous: String, next: String): Boolean {
        val last = previous.lastOrNull { !it.isWhitespace() } ?: return false
        val first = next.firstOrNull { !it.isWhitespace() } ?: return false
        return (isCjk(last) && isNumberStart(first)) || (last.isDigit() && isCjk(first))
    }

    /** Converts a pixel rectangle (origin top left) to the parser's normalized coordinates (origin bottom left). */
    fun toTextBox(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        imageWidth: Int,
        imageHeight: Int,
    ): TextBox {
        val w = imageWidth.toDouble()
        val h = imageHeight.toDouble()
        val x0 = min(max(left, 0), imageWidth)
        val x1 = min(max(right, 0), imageWidth)
        val y0 = min(max(top, 0), imageHeight)
        val y1 = min(max(bottom, 0), imageHeight)
        return TextBox(text, x0 / w, 1 - y1 / h, (x1 - x0) / w, (y1 - y0) / h)
    }

    /**
     * Formats [pages] as Kotlin test data, for the debug-only "copy recognized text" action. The
     * output builds the same [RecognizedPage] values, so a test can run the splitter and the parser.
     */
    fun toKotlinFixture(pages: List<RecognizedPage>): String = buildString {
        appendLine("listOf(")
        for (page in pages) {
            appendLine("    RecognizedPage(${page.width}, ${page.height}, listOf(")
            for (line in page.lines) {
                append("        listOf(")
                append(line.joinToString(", ") { e ->
                    "PixelElement(\"${escape(e.text)}\", ${e.left}, ${e.top}, ${e.right}, ${e.bottom})"
                })
                appendLine("),")
            }
            appendLine("    )),")
        }
        append(")")
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n")
}
