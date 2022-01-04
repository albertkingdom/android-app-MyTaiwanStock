package com.example.mynewsapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.model.StockStatistic
import kotlin.math.roundToInt

class StatisticAdapter: ListAdapter<StockStatistic, StatisticAdapter.StatisticViewHolder>(
    DIFF_CALLBACK) {
    class StatisticViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val stockNoView: TextView = itemView.findViewById(R.id.stockNo)
        val assetView: TextView = itemView.findViewById(R.id.total_assets)

        fun setData(data: StockStatistic){
            stockNoView.text = data.stockNo
            assetView.text = data.totalAssets.roundToInt().toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatisticViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stock_statistic, parent, false)
        return StatisticViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatisticViewHolder, position: Int) {
        holder.setData(getItem(position))
    }

    companion object {
        val DIFF_CALLBACK = object: DiffUtil.ItemCallback<StockStatistic>() {
            override fun areItemsTheSame(
                oldItem: StockStatistic,
                newItem: StockStatistic
            ): Boolean {
                return oldItem.stockNo == newItem.stockNo
            }

            override fun areContentsTheSame(
                oldItem: StockStatistic,
                newItem: StockStatistic
            ): Boolean {
               return oldItem == newItem
            }

        }
    }
}