package com.example.mynewsapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.model.StockStatistic
import java.text.NumberFormat
import java.util.*
import kotlin.math.roundToInt

class StatisticAdapter: ListAdapter<StockStatistic, StatisticAdapter.StatisticViewHolder>(
    DIFF_CALLBACK
) {
    class StatisticViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val stockNoView: TextView = itemView.findViewById(R.id.stockNo)
        val assetView: TextView = itemView.findViewById(R.id.total_assets)
        val amountView: TextView = itemView.findViewById(R.id.stockAmount)

        fun setData(data: StockStatistic){
            stockNoView.text = data.stockNo

            val format: NumberFormat = NumberFormat.getCurrencyInstance()
            format.maximumFractionDigits = 0
            format.currency = Currency.getInstance("TWD")

            assetView.text = format.format(data.totalAssets.roundToInt())
            amountView.text = amountView.context.getString(R.string.stock_amount, data.amount)
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