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



class DividendAdapter: ListAdapter<DividendStatistic, DividendAdapter.DividendViewHolder>(
    DIFF_CALLBACK
) {
    var clickDividendItemListener: ((String) -> Unit)? = null

    inner class DividendViewHolder(itemView: View):RecyclerView.ViewHolder(itemView) {
        val stockNo: TextView = itemView.findViewById(R.id.stockNo)
        val cashDividend: TextView = itemView.findViewById(R.id.cash_amount)
        val stockDividend: TextView = itemView.findViewById(R.id.stock_amount)

        fun setData(data: DividendStatistic){
            stockNo.text = data.stockNo
            cashDividend.text = data.cashAmount.toString()
            stockDividend.text = data.stockAmount.toString()

        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DividendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dividend, parent, false)
        return DividendViewHolder(view)
    }

    override fun onBindViewHolder(holder: DividendViewHolder, position: Int) {
        holder.setData(getItem(position))
        holder.itemView.setOnClickListener {
            clickDividendItemListener?.let { it1 -> it1(holder.stockNo.text.toString()) }
        }
    }

    companion object {
        val DIFF_CALLBACK = object: DiffUtil.ItemCallback<DividendStatistic>() {
            override fun areItemsTheSame(
                oldItem: DividendStatistic,
                newItem: DividendStatistic
            ): Boolean {
                return oldItem.stockNo == newItem.stockNo
            }

            override fun areContentsTheSame(
                oldItem: DividendStatistic,
                newItem: DividendStatistic
            ): Boolean {
                return oldItem == newItem
            }

        }
    }

}