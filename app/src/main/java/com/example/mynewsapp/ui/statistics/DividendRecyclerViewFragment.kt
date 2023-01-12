package com.example.mynewsapp.ui.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.mynewsapp.databinding.FragmentDividendRecyclerviewBinding
import com.example.mynewsapp.ui.adapter.DividendAdapter
import kotlinx.coroutines.launch
import timber.log.Timber


class DividendRecyclerViewFragment: Fragment() {
    private val statisticViewModel: StatisticViewModel by activityViewModels()

    lateinit var binding: FragmentDividendRecyclerviewBinding
    lateinit var adapter: DividendAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDividendRecyclerviewBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("onViewCreated")

        adapter = DividendAdapter()
        adapter.clickDividendItemListener = { stockNo ->
            Timber.d("click on $stockNo")
            findNavController().navigate(StatisticFragmentDirections.actionStatisticFragmentToDividendDetailFragment(stockNo = stockNo))
        }
        binding.statisticRecyclerview.adapter = adapter

        lifecycleScope.launch {
            // collect flow value when state is at least STARTED
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                statisticViewModel.combineData.collect {
                    val (listOfDividend,listOfStockStatistic) = it

                    adapter.submitList(listOfDividend)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("on resume")
    }
}