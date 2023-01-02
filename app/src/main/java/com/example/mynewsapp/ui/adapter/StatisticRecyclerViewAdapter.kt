package com.example.mynewsapp.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.mynewsapp.model.DividendStatistic
import com.example.mynewsapp.model.StockStatistic
import com.example.mynewsapp.ui.statistics.DividendRecyclerViewFragment
import com.example.mynewsapp.ui.statistics.StatisticRecyclerViewFragment
import timber.log.Timber

class StatisticRecyclerViewAdapter(
    fa: FragmentActivity
): FragmentStateAdapter(fa) {
    var statistics = listOf<StockStatistic>()
    var dividends = listOf<DividendStatistic>()


    override fun getItemCount(): Int {
        return 2
    }

    override fun createFragment(position: Int): Fragment {

        if (position==0) {
            return StatisticRecyclerViewFragment(statistics)
        }
        return DividendRecyclerViewFragment(dividends)
    }
}