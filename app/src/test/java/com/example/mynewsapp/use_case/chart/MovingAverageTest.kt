package com.example.mynewsapp.use_case.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovingAverageTest {

    private fun closes(count: Int) = (1..count).map { it.toFloat() }

    @Test
    fun row_count_examples() {
        // rows -> (5-day points, 10-day points)
        val expected = mapOf(0 to (0 to 0), 3 to (0 to 0), 5 to (1 to 0), 9 to (5 to 0), 10 to (6 to 1))
        for ((rows, points) in expected) {
            assertEquals("5-day points for $rows rows", points.first, MovingAverage.compute(closes(rows), 5).size)
            assertEquals("10-day points for $rows rows", points.second, MovingAverage.compute(closes(rows), 10).size)
        }
    }

    @Test
    fun values_are_window_averages_at_their_index() {
        val result = MovingAverage.compute(listOf(1f, 2f, 3f, 4f, 5f, 6f, 10f), 5)
        assertEquals(listOf(4, 5, 6), result.map { it.first })
        assertEquals(3f, result[0].second, 1e-4f)
        assertEquals(4f, result[1].second, 1e-4f)
        assertEquals(5.6f, result[2].second, 1e-4f)
    }

    @Test
    fun non_positive_window_yields_nothing() {
        assertTrue(MovingAverage.compute(closes(5), 0).isEmpty())
    }
}
