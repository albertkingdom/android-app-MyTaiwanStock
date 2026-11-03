package com.example.mynewsapp.ui.detail

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getDrawable
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.ui.adapter.StockHistoryAdapter
import com.example.mynewsapp.databinding.FragmentCandleStickChartBinding
import com.example.mynewsapp.tradecost.HoldingsCalculator
import com.example.mynewsapp.tradecost.TradeInput
import com.example.mynewsapp.use_case.chart.CandleRows
import com.example.mynewsapp.use_case.chart.MovingAverage
import com.example.mynewsapp.util.getColorThemeRes
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.LargeValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import kotlin.math.abs

class CandleStickChartFragment: Fragment() {

    private var _binding: FragmentCandleStickChartBinding? = null
    private val binding get() = _binding!!
    // From the provider, so that it is cleared with the screen and its history query does not outlive it.
    private val chartViewModel: CandleStickChartViewModel by viewModels {
        CandleStickChartViewModel.Factory((requireActivity().application as MyApplication).repository)
    }

    private val args: CandleStickChartFragmentArgs by navArgs()

    private var _chart: CombinedChart? = null
    private val chart get() = _chart!!

    private lateinit var historyAdapter: StockHistoryAdapter

    var chartLabelColor = 0

    /** The current price passed in by the list, or null when it is not a number such as "-". */
    private val currentPrice: BigDecimal? by lazy { TradeInput.parsePrice(args.stockPrice)?.takeIf { it.signum() > 0 } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCandleStickChartBinding.inflate(inflater)
        _chart = binding.candleStickChart
        //change toolbar title
        (requireActivity() as AppCompatActivity).supportActionBar?.title = "${args.stockName} ${args.stockNo}"

        chartViewModel.getCandleStickData("", args.stockNo)

        chartViewModel.queryHistoryByStockNo(args.stockNo, currentPrice)

        Timber.d("on create view ${args.stockNo}")

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.stockPrice.text = HoldingsCalculator.format(currentPrice)
        //binding.dateStockPrice.text = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.now())
        binding.dateStockPrice.text = args.time

        chartLabelColor = requireContext().getColorThemeRes(android.R.attr.textColorPrimary)

        setupListAdapter()
        setupChart()
        setupXLabelFormat()

        chartViewModel.originalCandleData.observe(viewLifecycleOwner) { data ->
            setupOnClickChart(data)
            initOpenCloseHighLowValue(data.last()[CandleRows.CHANGE])
        }

        chartViewModel.candleDataUnavailable.observe(viewLifecycleOwner) { unavailable ->
            if (unavailable) {
                chart.setNoDataText(getString(R.string.candle_no_data))
                chart.clear()
                initOpenCloseHighLowValue("0")
            }
        }


        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    chartViewModel.holdingsSummary.collect { summary ->
                        binding.textTotalAmount.text = summary.amountText
                        binding.totalAssetValue.text = summary.marketValueText
                        binding.avgBuyPrice.text = summary.averageBuyPriceText
                        binding.avgSellPrice.text = summary.averageSellPriceText
                        binding.unknownFeeNote.visibility = if (summary.showsUnknownFeeNote) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun setupListAdapter() {
        historyAdapter = StockHistoryAdapter()
        historyAdapter.setStockPrice(currentPrice)
        historyAdapter.setListener { targetDate ->
            // when clicked record, highlight the day of investing record on chart

            // No chart data (for example the "無資料" state of an OTC stock): nothing to highlight.
            val data = chart.data ?: return@setListener
            chartViewModel.xLabels.value?.forEachIndexed { index, dateString ->

                if(dateString == targetDate){
                    try {
                        val highlight = Highlight(index.toFloat(), 0, -1)
                        // Highlight on the volume bars; the data index shifts when no moving average is drawn.
                        highlight.dataIndex = data.allData.indexOf(data.barData)
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

            if (pair.first == null || pair.second == null) {
                println("pair is not completed")
                return@observe
            }
            val candleDatas: CandleData = pair.first as CandleData
            val candleEntries = (candleDatas.dataSets[0] as CandleDataSet).values
            // A moving average line is drawn only when there are enough rows for its window.
            val averageDataSets: List<ILineDataSet> = listOf(5 to "#ffcc00", 10 to "#e238ec").mapNotNull { (window, color) ->
                val entries = calculateAverageEntries(candleEntries, window)
                if (entries.isEmpty()) return@mapNotNull null
                LineDataSet(entries, "Average $window").apply {
                    this.color = Color.parseColor(color)
                    setDrawCircles(false)// 禁用繪製數據點圓圈
                }
            }

            val combinedData = CombinedData()
            combinedData.setData(pair.first)
            combinedData.setData(pair.second)
            if (averageDataSets.isNotEmpty()) {
                val lineData = LineData(averageDataSets)
                lineData.setDrawValues(false)// 禁用顯示數據點值
                combinedData.setData(lineData)
            }



            chart.data = combinedData

            chart.invalidate()

            setupChartFormat()
        }
    }

    private fun calculateAverageEntries(candleEntries: List<CandleEntry>, windowSize: Int): List<Entry> =
        MovingAverage.compute(candleEntries.map { it.close }, windowSize)
            .map { (index, average) -> Entry(candleEntries[index].x, average) }

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
                textColor = chartLabelColor
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

            axisLeft.textColor = chartLabelColor
            axisRight.textColor = chartLabelColor
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
                val openValue = allDatas[index][CandleRows.OPEN]
                val closeValue = allDatas[index][CandleRows.CLOSE]
                val highValue = allDatas[index][CandleRows.HIGH]
                val lowValue = allDatas[index][CandleRows.LOW]
                val selectedDate = allDatas[index][CandleRows.DATE]
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
        // "X0.00" (ex-dividend) and other non-numbers count as no change.
        val priceDiffFloat = CandleRows.priceChange(stockpriceDiff)
        Timber.d("stockpriceDiff $priceDiffFloat")
        binding.apply {
            open.text = getString(R.string.stockprice_open, "")
            close.text = getString(R.string.stockprice_close, "")
            high.text = getString(R.string.stockprice_high, "")
            low.text = getString(R.string.stockprice_low, "")
            date.text = getString(R.string.stockprice_date, "")
            priceDiff.text = "${abs(priceDiffFloat)}"

            if (priceDiffFloat > 0f){
                stockInfo.setBackgroundColor(getColor(requireContext(),R.color.light_red))
                stockPrice.setTextColor(Color.RED)
                priceDiff.setTextColor(Color.RED)
                arrow.setColorFilter(Color.RED)
                arrow.setImageDrawable(getDrawable(requireContext(),R.drawable.ic_arrow_drop_up))
            } else if (priceDiffFloat < 0f) {
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
    private fun navigateToNewDividendFragment() {
        val stockNo = args.stockNo
        findNavController().navigate(CandleStickChartFragmentDirections.actionCandleStickChartFragmentToAddDividendFragment(stockNo))
    }
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.candle_stick_chart_fragment_option_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.addInvestRecord -> {
                navigateToAddHistoryFragment()
                true
            }
            R.id.addDividend -> {
                navigateToNewDividendFragment()
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
        _chart?.clear()
        _chart=null
        binding.investHistoryRecyclerView.adapter = null

        _binding=null
    }
}