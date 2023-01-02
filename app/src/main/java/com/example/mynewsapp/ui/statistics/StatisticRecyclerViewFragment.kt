package com.example.mynewsapp.ui.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.FragmentStatisticRecyclerviewBinding
import com.example.mynewsapp.model.StockStatistic
import com.example.mynewsapp.ui.adapter.StatisticAdapter
import timber.log.Timber

class StatisticRecyclerViewFragment(
    val stockStatistics:  List<StockStatistic>
): Fragment() {
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


        adapter.submitList(stockStatistics)
    }

}