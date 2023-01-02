package com.example.mynewsapp.ui.statistics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.example.mynewsapp.databinding.FragmentDividendDetailFragmentBinding
import com.example.mynewsapp.ui.adapter.DividendDetailAdapter
import timber.log.Timber

class DividendDetailFragment: Fragment() {
    lateinit var binding: FragmentDividendDetailFragmentBinding
    lateinit var adapter: DividendDetailAdapter
    val statisticViewModel: StatisticViewModel by activityViewModels()
    private val args: DividendDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDividendDetailFragmentBinding.inflate(inflater)

        statisticViewModel.getDividendBy(args.stockNo)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = DividendDetailAdapter()
        binding.recyclerView.adapter = adapter

        statisticViewModel.dividendsByStockNo.observe(viewLifecycleOwner) { list ->

            adapter.submitList(list)
        }

    }
}