package com.example.mynewsapp.ui.statistics

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.ui.adapter.StatisticAdapter
import com.example.mynewsapp.databinding.FragmentStatisticBinding
import com.example.mynewsapp.ui.list.ListViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber

class StatisticFragment: Fragment() {
    lateinit var binding: FragmentStatisticBinding

    lateinit var statisticViewModel: StatisticViewModel
    private val listViewModel: ListViewModel by activityViewModels()
    lateinit var pieChart: PieChart
    lateinit var textRemind: TextView
    lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StatisticAdapter


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val repository = (activity?.application as MyApplication).repository
        statisticViewModel = StatisticViewModel(repository)


        binding = FragmentStatisticBinding.inflate(inflater)
        pieChart = binding.pieChart
        textRemind = binding.textNoData


        observeListViewModel()


        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        recyclerView = binding.recyclerView
        adapter = StatisticAdapter()
        recyclerView.adapter = adapter

        setupListAdapter()

        setupChart()

    }

    private fun observeListViewModel() {
        listViewModel.stockPriceInfo.observe(viewLifecycleOwner) {
            statisticViewModel.setStockPriceInfo(it)
        }
    }

    private fun setupListAdapter() {
        statisticViewModel.investStatisticsList.observe(viewLifecycleOwner) { listOfStockStatistic ->
            Timber.d("listOfStockStatistic $listOfStockStatistic")
            if(listOfStockStatistic.isEmpty()){
                textRemind.visibility = View.VISIBLE
                pieChart.visibility = View.GONE
                showDialog()
                return@observe
            }
            adapter.submitList(listOfStockStatistic)
        }
    }

    private fun setupChart() {
        statisticViewModel.pieData.observe(viewLifecycleOwner) { pieData ->
            if (pieData.dataSetCount == 0){
                return@observe
            }
            pieChart.apply {
                data = pieData
                data.setDrawValues(true)
                data.setValueFormatter(PercentFormatter(this)) // display % on chart
                data.setValueTextSize(12f)
                setUsePercentValues(true) // display % on chart
                invalidate() //refresh
                legend.isEnabled = false
                val descriptions = Description() // @ bottom right of chart
                descriptions.text = "" // remove description
                description = descriptions
            }

        }
    }

    private fun showDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.AddFollowingListDialogTheme)
            .setTitle("投資紀錄")
            .setMessage("請新增至少一筆投資紀錄")
            .setPositiveButton("Ok", DialogInterface.OnClickListener { _, _ ->  })
            .show()
    }
}