package com.example.mynewsapp.ui.detail

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.ui.adapter.StockHistoryAdapter
import com.example.mynewsapp.databinding.FragmentCandleStickChartBinding
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.LargeValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.abs

class CandleStickChartFragment: Fragment() {

    lateinit var binding: FragmentCandleStickChartBinding

    private lateinit var chartViewModel: CandleStickChartViewModel

    private val args: CandleStickChartFragmentArgs by navArgs()

    private lateinit var chart:CombinedChart

    private lateinit var historyAdapter: StockHistoryAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCandleStickChartBinding.inflate(inflater)
        chart = binding.candleStickChart
        //change toolbar title
        (requireActivity() as AppCompatActivity).supportActionBar?.title = "${args.stockName} ${args.stockNo}"

        val repository = (activity?.application as MyApplication).repository
        chartViewModel = CandleStickChartViewModel(repository)

        chartViewModel.getCandleStickData("", args.stockNo)

        chartViewModel.queryHistoryByStockNo(args.stockNo)

        Timber.d("on create view ${args.stockNo}")

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.stockPrice.text = String.format("%.2f", args.stockPrice.toFloat())
        //binding.dateStockPrice.text = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.now())
        binding.dateStockPrice.text = args.time


        setupListAdapter()
        setupChart()
        setupXLabelFormat()

        chartViewModel.originalCandleData.observe(viewLifecycleOwner) { data ->
            setupOnClickChart(data)
            initOpenCloseHighLowValue(data.last()[7])
        }


        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    chartViewModel.totalHistoryAmount.collect {
                        binding.textTotalAmount.text = it.toString()
                        binding.totalAssetValue.text = (it*args.stockPrice.toFloat()).toString()
                    }
                }
                launch {
                    chartViewModel.historyBuyAvgPrice.collect {
                        binding.avgBuyPrice.text = if (it > 0) it.toString() else "-"
                    }
                }
                launch {
                    chartViewModel.historySellAvgPrice.collect {
                        binding.avgSellPrice.text = if (it > 0) it.toString() else "-"
                    }
                }
            }
        }
    }

    private fun setupListAdapter() {
        historyAdapter = StockHistoryAdapter()
        historyAdapter.setStockPrice(args.stockPrice)
        historyAdapter.setListener { targetDate ->
            // when clicked record, highlight the day of investing record on chart

            chartViewModel.xLabels.value?.forEachIndexed { index, dateString ->

                if(dateString == targetDate){
                    try {
                        val highlight = Highlight(index.toFloat(), 0, -1)
                        highlight.dataIndex = 1
                        chart.highlightValue(highlight, false)
                    } catch (e: Exception) {
                        Log.e("touch history", e.toString())
                    }
                }
            }
        }
        binding.investHistoryRecyclerView.adapter = historyAdapter
        chartViewModel.investHistoryList.observe(viewLifecycleOwner) {
            historyAdapter.submitList(it)
        }
    }
    private fun setupChart() {
        chartViewModel.combinedData.observe(viewLifecycleOwner) { pair ->
            println("combinedData $pair")
            if (pair.first == null || pair.second == null) {
                println("pair is not completed")
                return@observe
            }
            val combinedData = CombinedData()
            combinedData.setData(pair.first)
            combinedData.setData(pair.second)
            chart.data = combinedData

            chart.invalidate()

            setupChartFormat()
        }
    }
    /**
     * format x label data
     */
    private fun setupXLabelFormat(){
        chartViewModel.xLabels.observe(viewLifecycleOwner) { xLabels ->
            chart.xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(xLabels)
                position = XAxis.XAxisPosition.BOTTOM
                labelRotationAngle = -25f
                setDrawGridLines(false)
            }

        }



    }

    private fun setupChartFormat(){

        chart.extraBottomOffset = 50F
        chart.apply{
            //左下方Legend
            legend.isEnabled = false
            //右下方description label
            description.isEnabled = false
            setScaleEnabled(false)
            axisRight.isEnabled = true
            axisRight.setDrawGridLines(false)
            axisRight.valueFormatter = LargeValueFormatter()
            axisRight.axisMaximum = barData.yMax * 10
            drawOrder = arrayOf(CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.CANDLE)
            barData.isHighlightEnabled = false
            candleData.isHighlightEnabled = true
            setDrawBorders(true)
        }
    }
    /**
     * click chart to show detail
     */
    private fun setupOnClickChart(allDatas: List<List<String>>){
        chart.setOnChartValueSelectedListener(object:OnChartValueSelectedListener{
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                //e : x:index,y: y value clicked
                val index = e!!.x.toInt()
                val openValue = allDatas[index][3]
                val closeValue = allDatas[index][6]
                val highValue = allDatas[index][4]
                val lowValue = allDatas[index][5]
                val selectedDate = allDatas[index][0]
                binding.apply {
                    open.text = getString(R.string.stockprice_open, openValue)
                    close.text = getString(R.string.stockprice_close, closeValue)
                    high.text = getString(R.string.stockprice_high, highValue)
                    low.text = getString(R.string.stockprice_low, lowValue)
                    date.text = getString(R.string.stockprice_date, selectedDate)
                }

            }

            override fun onNothingSelected() {

            }
        })
    }
    private fun initOpenCloseHighLowValue(stockpriceDiff:String){
        val priceDiffFloat = stockpriceDiff.toFloat()
        Timber.d("stockpriceDiff $priceDiffFloat")
        binding.apply {
            open.text = getString(R.string.stockprice_open, "")
            close.text = getString(R.string.stockprice_close, "")
            high.text = getString(R.string.stockprice_high, "")
            low.text = getString(R.string.stockprice_low, "")
            date.text = getString(R.string.stockprice_date, "")
            priceDiff.text = "${abs(priceDiffFloat)}"

            if (stockpriceDiff.toFloat() > 0f){
                stockInfo.setBackgroundColor(getColor(requireContext(),R.color.light_red))
                stockPrice.setTextColor(Color.RED)
                priceDiff.setTextColor(Color.RED)
                arrow.setColorFilter(Color.RED)
                arrow.setImageDrawable(getDrawable(requireContext(),R.drawable.ic_arrow_drop_up))
            } else if (stockpriceDiff.toFloat() < 0f) {
                val greenColor = getColor(requireContext(),R.color.green)
                stockInfo.setBackgroundColor(getColor(requireContext(),R.color.celadon))
                stockPrice.setTextColor(greenColor)
                priceDiff.setTextColor(greenColor)
                arrow.setColorFilter(greenColor)
                arrow.setImageDrawable(getDrawable(requireContext(),R.drawable.ic_arrow_drop_down))

            }
        }
    }
    private fun navigateToAddHistoryFragment() {
        val stockNo = args.stockNo
        findNavController().navigate(CandleStickChartFragmentDirections.actionCandleStickChartFragmentToAddHistoryFragment(stockNo))
    }
    private fun navigateToChatFragment() {

        findNavController().navigate(CandleStickChartFragmentDirections.actionCandleStickChartFragmentToChatFragment(args.stockNo))
    }
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.candle_stick_chart_fragment_option_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.addButton -> {
                navigateToAddHistoryFragment()
                true
            }
            R.id.go_to_chatRoom -> {
                navigateToChatFragment()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        chartViewModel.clearCandleStickData()
        super.onDestroyView()
    }
}