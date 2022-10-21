package com.example.mynewsapp.use_case

import android.graphics.Color
import android.graphics.Paint
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import java.text.NumberFormat

class CalculateChartDataUseCase {
    fun generateCandleDataSet(data: List<List<String>>, stockNo: String): CandleData {
        val candleStickEntry = data.mapIndexed { index, day ->

            CandleEntry(
                index.toFloat(),
                day[4].toFloat(),//high
                day[5].toFloat(), //low
                day[3].toFloat(), //open
                day[6].toFloat() //close
            )

        }
        val candleDataSet = CandleDataSet(candleStickEntry, stockNo)
        candleDataSet.apply {
            decreasingColor = Color.parseColor("#008000")
            increasingColor = Color.RED
            decreasingPaintStyle = Paint.Style.FILL
            increasingPaintStyle = Paint.Style.FILL
            setDrawValues(false)
            shadowColorSameAsCandle = true
            axisDependency = YAxis.AxisDependency.LEFT
        }

        return CandleData(candleDataSet)
    }

    fun generateBarData(data: List<List<String>>): BarData {
        val barEntries = data.mapIndexed { index, day ->
            BarEntry(index.toFloat(), NumberFormat.getInstance().parse(day[2]).toFloat())
        }
        val barDataSet = BarDataSet(barEntries, "volume")
        barDataSet.apply {
            axisDependency = YAxis.AxisDependency.RIGHT
            setDrawValues(false)
            color = Color.LTGRAY

        }
        return BarData(barDataSet)
    }

    fun generateXLabels(data: List<List<String>>):List<String> {

        return data.map { day->
            day[0]
        }
    }
}