package com.example.mynewsapp.ui.statistics

import android.graphics.Color
import com.example.mynewsapp.model.PieSlice
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlin.random.Random

/**
 * Shows [slices] on this chart with a percentage label on each slice. The PieData is built here and
 * handed only to this chart, so it, and the PercentFormatter(chart) inside it, live and die with
 * the chart.
 */
fun PieChart.showPieData(slices: List<PieSlice>) {
    val dataSet = PieDataSet(slices.map { PieEntry(it.percent, it.stockNo) }, "stockNo.")
    dataSet.colors = slices.map { Color.argb(255, Random.nextInt(0, 255), Random.nextInt(0, 255), Random.nextInt(0, 255)) }

    val pieData = PieData(dataSet)
    pieData.setDrawValues(true)
    pieData.setValueFormatter(PercentFormatter(this)) // display % on chart
    pieData.setValueTextSize(12f)

    setUsePercentValues(true) // display % on chart
    data = pieData
    legend.isEnabled = false
    val descriptions = Description() // @ bottom right of chart
    descriptions.text = "" // remove description
    description = descriptions
    invalidate() //refresh
}
