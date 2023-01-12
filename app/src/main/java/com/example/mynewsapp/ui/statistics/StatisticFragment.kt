package com.example.mynewsapp.ui.statistics

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.FragmentStatisticBinding
import com.example.mynewsapp.ui.adapter.StatisticRecyclerViewAdapter
import com.example.mynewsapp.ui.list.ListViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class StatisticFragment: Fragment() {
    lateinit var binding: FragmentStatisticBinding

    val statisticViewModel: StatisticViewModel by activityViewModels()
    private val listViewModel: ListViewModel by activityViewModels()

    lateinit var pieChart: PieChart
    lateinit var textRemind: TextView
    lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StatisticRecyclerViewAdapter


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding = FragmentStatisticBinding.inflate(inflater)
        pieChart = binding.pieChart
        textRemind = binding.textNoData

        observeListViewModel()
        statisticViewModel.collectData()

        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPagerAdapter()

        setupChart()
    }

    private fun observeListViewModel() {
        listViewModel.stockPriceInfo.observe(viewLifecycleOwner) {
            statisticViewModel.setStockPriceInfo(it)
        }
    }

    private fun setupViewPagerAdapter() {
        adapter = StatisticRecyclerViewAdapter(childFragmentManager, lifecycle)

        binding.viewPager.adapter = adapter

        val tabMediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "庫存"
                1 -> "股利"
                else -> return@TabLayoutMediator
            }
        }
        tabMediator.attach()
    }

    private fun setupChart() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                statisticViewModel.pieDataFlow.collect { pieData ->
                    if (pieData.dataSetCount == 0){
                        return@collect
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