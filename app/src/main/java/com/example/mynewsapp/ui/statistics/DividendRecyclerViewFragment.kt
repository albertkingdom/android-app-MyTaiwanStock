package com.example.mynewsapp.ui.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.mynewsapp.databinding.FragmentDividendRecyclerviewBinding
import com.example.mynewsapp.model.DividendStatistic
import com.example.mynewsapp.ui.adapter.DividendAdapter
import timber.log.Timber


class DividendRecyclerViewFragment(
    val dividendStatistics:  List<DividendStatistic>
): Fragment() {

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
        adapter = DividendAdapter()
        adapter.clickDividendItemListener = { stockNo ->
            Timber.d("click on $stockNo")
            findNavController().navigate(StatisticFragmentDirections.actionStatisticFragmentToDividendDetailFragment(stockNo = stockNo))
        }
        binding.statisticRecyclerview.adapter = adapter

        adapter.submitList(dividendStatistics)
    }
}