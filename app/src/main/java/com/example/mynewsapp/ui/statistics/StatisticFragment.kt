package com.example.mynewsapp.ui.statistics

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.FragmentStatisticBinding
import com.example.mynewsapp.ui.adapter.StatisticRecyclerViewAdapter
import com.example.mynewsapp.ui.list.ListViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class StatisticFragment: Fragment() {
    // Cleared in onDestroyView: on the back stack the fragment outlives its view.
    private var _binding: FragmentStatisticBinding? = null
    private val binding get() = _binding!!

    val statisticViewModel: StatisticViewModel by activityViewModels()
    private val listViewModel: ListViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentStatisticBinding.inflate(inflater)

        observeListViewModel()
        statisticViewModel.collectData()

        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPagerAdapter()

        setupChart()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeListViewModel() {
        listViewModel.stockPriceInfo.observe(viewLifecycleOwner) {
            statisticViewModel.setStockPriceInfo(it)
        }
    }

    private fun setupViewPagerAdapter() {
        // The view's lifecycle, not the fragment's: the adapter observes it and holds the ViewPager.
        binding.viewPager.adapter = StatisticRecyclerViewAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)

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
        // Scoped to the view so each new view replaces, rather than adds to, the collector.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                statisticViewModel.pieDataFlow.collect { pieData ->
                    if (pieData.dataSetCount == 0){
                        return@collect
                    }
                    binding.pieChart.showPieData(pieData)
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