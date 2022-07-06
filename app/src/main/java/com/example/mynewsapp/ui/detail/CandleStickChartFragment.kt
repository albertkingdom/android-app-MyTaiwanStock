package com.example.mynewsapp.ui.detail

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.adapter.StockHistoryAdapter
import com.example.mynewsapp.databinding.FragmentCandleStickChartBinding
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.LargeValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener

class CandleStickChartFragment: Fragment() {

    lateinit var binding: FragmentCandleStickChartBinding

    private lateinit var chartViewModel: CandleStickChartViewModel

    private val args: CandleStickChartFragmentArgs by navArgs()

    private lateinit var chart:CombinedChart

    private lateinit var historyAdapter: StockHistoryAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("candle fragment","onCreate")
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
        (requireActivity() as AppCompatActivity).supportActionBar?.title = args.stockNo

        val repository = (activity?.application as MyApplication).repository
        chartViewModel = CandleStickChartViewModel(repository)

        chartViewModel.getCandleStickData("", args.stockNo)

        chartViewModel.queryHistoryByStockNo(args.stockNo)

        Log.d("candle fragment","on create view ${args.stockNo}")

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("candle fragment","onViewCreated")

        binding.stockNo.text = args.stockNo
        binding.stockName.text = args.stockName
        binding.stockPrice.text = String.format("%.2f",args.stockPrice.toFloat())


        setupListAdapter()
        setupChart()
        setupXLabelFormat()




        chartViewModel.originalCandleData.observe(viewLifecycleOwner) { data ->
            setupOnClickChart(data)
            initOpenCloseHighLowValue(data.last()[7])
        }

    }

    private fun setupListAdapter() {
        historyAdapter = StockHistoryAdapter()
        historyAdapter.setStockPrice(args.stockPrice)
        historyAdapter.setListener { targetDate ->
            // when clicked record, highlight the day of investing record on chart
            //Log.d("targetDate", targetDate)
            chartViewModel.xLabels.value?.forEachIndexed { index, dateString ->
                //Log.d("CandleStickChartFragment", dateString)
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
        binding.apply {
            open.text = getString(R.string.stockprice_open, "")
            close.text = getString(R.string.stockprice_close, "")
            high.text = getString(R.string.stockprice_high, "")
            low.text = getString(R.string.stockprice_low, "")
            date.text = getString(R.string.stockprice_date, "")
            priceDiff.text = stockpriceDiff
            priceDiff.setTextColor(if (stockpriceDiff.toFloat() > 0f ) Color.RED else getColor(requireContext(),R.color.green))

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
                print("go to add history")
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
        //Log.d("candle fragment","on destroy view")
    }
}