package com.example.mynewsapp.use_case.chart

object MovingAverage {
    /**
     * Simple moving average of [closes] over [window] rows. Each point is (index, average of the
     * [window] closes ending at index); fewer rows than [window] yield no points.
     */
    fun compute(closes: List<Float>, window: Int): List<Pair<Int, Float>> {
        if (window <= 0 || closes.size < window) return emptyList()
        return (window - 1 until closes.size).map { index ->
            var sum = 0.0
            for (i in index - window + 1..index) sum += closes[i]
            index to (sum / window).toFloat()
        }
    }
}
