package com.example.mynewsapp.use_case

import com.example.mynewsapp.model.PieSlice
import com.example.mynewsapp.model.StockStatistic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateAssetUseCaseTest {

    private val useCase = CalculateAssetUseCase()

    @Test
    fun `slices are each stock's share of the total in percent, in the given order`() {
        // 2500 + 2500 + 5000 = 10000 -> 25%, 25%, 50%
        val slices = useCase.calculatePieData(
            listOf(
                StockStatistic("2330", 5, 2500f),
                StockStatistic("0056", 100, 2500f),
                StockStatistic("0050", 40, 5000f)
            )
        )

        assertEquals(listOf("2330", "0056", "0050"), slices.map { it.stockNo })
        assertEquals(listOf(25f, 25f, 50f), slices.map { it.percent })
    }

    @Test
    fun `a single stock is the whole pie`() {
        val slices = useCase.calculatePieData(listOf(StockStatistic("0056", 100, 3200f)))

        assertEquals(listOf(PieSlice("0056", 100f)), slices)
    }

    @Test
    fun `uneven shares are divided by the sum`() {
        // 1000 + 3000 = 4000 -> 25%, 75%
        val slices = useCase.calculatePieData(
            listOf(StockStatistic("A", 1, 1000f), StockStatistic("B", 1, 3000f))
        )

        assertEquals(25f, slices[0].percent, 0.0001f)
        assertEquals(75f, slices[1].percent, 0.0001f)
    }

    @Test
    fun `an empty list gives no slices`() {
        assertEquals(emptyList<PieSlice>(), useCase.calculatePieData(emptyList()))
    }

    @Test
    fun `a total of zero gives no slices instead of NaN percentages`() {
        val slices = useCase.calculatePieData(
            listOf(StockStatistic("2330", 0, 0f), StockStatistic("0056", 0, 0f))
        )

        assertTrue("expected no slices but got $slices", slices.isEmpty())
    }

    @Test
    fun `values that cancel out to a total of zero give no slices`() {
        // More sold than bought for one stock can offset another: 500 + (-500) = 0.
        val slices = useCase.calculatePieData(
            listOf(StockStatistic("2330", 1, 500f), StockStatistic("0056", -1, -500f))
        )

        assertTrue("expected no slices but got $slices", slices.isEmpty())
    }
}
