package com.example.mynewsapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.model.DividendStatistic
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

class DividendDetailAdapter: ListAdapter<DividendStatistic, DividendDetailAdapter.DividendDetailViewHolder>(
    DIFF_CALLBACK
) {

    companion object {
        val DIFF_CALLBACK = object: DiffUtil.ItemCallback<DividendStatistic>() {
            override fun areItemsTheSame(
                oldItem: DividendStatistic,
                newItem: DividendStatistic
            ): Boolean {
                return oldItem.date == newItem.date
            }

            override fun areContentsTheSame(
                oldItem: DividendStatistic,
                newItem: DividendStatistic
            ): Boolean {
                return oldItem == newItem
            }

        }
    }

    inner class DividendDetailViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val dateTextView = itemView.findViewById<TextView>(R.id.date)
        val cashDividend: TextView = itemView.findViewById(R.id.cash)
        val stockDividend: TextView = itemView.findViewById(R.id.stock)

        fun setData(data: DividendStatistic){
            dateTextView.text = convertDateMillisToStringBy(date = data.date!!)
            cashDividend.text = data.cashAmount.toString()
            stockDividend.text = data.stockAmount.toString()

        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DividendDetailViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dividend_detail, parent, false)
        return DividendDetailViewHolder(view)
    }

    override fun onBindViewHolder(holder: DividendDetailViewHolder, position: Int) {
        holder.setData(getItem(position))
    }

    fun convertDateMillisToStringBy(date: Long): String {
        val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), TimeZone.getDefault().toZoneId())
        val year = (localDateTime.year).toString()
        val month = if(localDateTime.monthValue<10) "0${localDateTime.monthValue}" else localDateTime.monthValue
        val day = if(localDateTime.dayOfMonth<10) "0${localDateTime.dayOfMonth}" else localDateTime.dayOfMonth
        return "$year-$month-$day"
    }
}