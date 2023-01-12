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
import com.example.mynewsapp.databinding.FragmentStatisticRecyclerviewBinding
import com.example.mynewsapp.ui.adapter.StatisticAdapter
import kotlinx.coroutines.launch
import timber.log.Timber

class StatisticRecyclerViewFragment: Fragment() {
    private val statisticViewModel: StatisticViewModel by activityViewModels()

    lateinit var binding: FragmentStatisticRecyclerviewBinding
    lateinit var adapter: StatisticAdapter



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentStatisticRecyclerviewBinding.inflate(inflater)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = StatisticAdapter()
        binding.statisticRecyclerview.adapter = adapter


        lifecycleScope.launch {
            // collect flow value when state is at least STARTED
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                statisticViewModel.combineData.collect {
                    val (listOfDividend,listOfStockStatistic) = it

                    adapter.submitList(listOfStockStatistic)
                }
            }
        }
    }

}