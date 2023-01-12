package com.example.mynewsapp.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.mynewsapp.ui.statistics.DividendRecyclerViewFragment
import com.example.mynewsapp.ui.statistics.StatisticRecyclerViewFragment
import timber.log.Timber

class StatisticRecyclerViewAdapter(
    fm: FragmentManager,
    lifecycle: Lifecycle
): FragmentStateAdapter(fm, lifecycle) {

    override fun getItemCount(): Int {
        return 2
    }

    override fun createFragment(position: Int): Fragment {

        if (position==0) {
            return StatisticRecyclerViewFragment()
        }
        return DividendRecyclerViewFragment()
    }
}