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

    // Cleared in onDestroyView: on the back stack the fragment outlives its view.
    private var _binding: FragmentDividendRecyclerviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDividendRecyclerviewBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("onViewCreated")

        // Local, not a field: the adapter holds its RecyclerView through its observers.
        val adapter = DividendAdapter()
        adapter.clickDividendItemListener = { stockNo ->
            Timber.d("click on $stockNo")
            findNavController().navigate(StatisticFragmentDirections.actionStatisticFragmentToDividendDetailFragment(stockNo = stockNo))
        }
        binding.statisticRecyclerview.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            // collect flow value while the view is at least STARTED
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
