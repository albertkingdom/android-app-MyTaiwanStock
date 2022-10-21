package com.example.mynewsapp.ui.adapter


import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.model.StockHistory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


class StockHistoryAdapter: ListAdapter<InvestHistory, StockHistoryAdapter.StockHistoryViewHolder>(
    DiffCallback
) {
    private var stockPrice: String? = null
    private var clickItemListener: ((String) -> Unit)? = null

    class StockHistoryViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val statusView: TextView = itemView.findViewById(R.id.status)
        val dateView: TextView = itemView.findViewById(R.id.date)
        val priceView: TextView = itemView.findViewById(R.id.price)
        val amountView: TextView = itemView.findViewById(R.id.amount)
        val revenueView: TextView = itemView.findViewById(R.id.revenue)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): StockHistoryViewHolder {
       val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stock_history, parent,false)
        return StockHistoryViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: StockHistoryViewHolder,
        position: Int
    ) {
        val currentHistory = getItem(position)

        holder.apply {
            val revenue = calcRevenue(price = currentHistory.price)
            statusView.text = if (currentHistory.status == 0) "買" else "賣"
            dateView.text = formatDateLong(currentHistory.date)
            priceView.text = currentHistory.price.toString()
            amountView.text = currentHistory.amount.toString()
            revenueView.text = this.itemView.context.getString(R.string.stockhistory_revenue, revenue)

            if (revenue.toFloat() > 0) {
                revenueView.setTextColor(Color.RED)
            }else{
                revenueView.setTextColor(this.revenueView.resources.getColor(R.color.green, null))
            }
            // click to get the history date in 110/10/10 format
            itemView.setOnClickListener {
                val localDateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(currentHistory.date),TimeZone.getDefault().toZoneId())
                val year = (localDateTime.year - 1911).toString()
                val month = if(localDateTime.monthValue<10) "0${localDateTime.monthValue}" else localDateTime.monthValue
                val day = if(localDateTime.dayOfMonth<10) "0${localDateTime.dayOfMonth}" else localDateTime.dayOfMonth


                if (clickItemListener != null) {
                    clickItemListener!!("$year/$month/$day")
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<InvestHistory>() {
            override fun areItemsTheSame(oldItem: InvestHistory, newItem: InvestHistory): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: InvestHistory, newItem: InvestHistory): Boolean {
                return oldItem == newItem
            }

        }
    }

    private fun formatDate():String {
        val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        return LocalDate.now().format(formatter)

    }
    private fun formatDateLong(dateLong: Long):String {

        val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        return Instant.ofEpochMilli(dateLong).atZone(ZoneId.systemDefault()).format(formatter)

    }
    fun setStockPrice(price: String) {
        stockPrice = price
    }
    private fun calcRevenue(price: Double): String {
        var revenueStr: String = "-"
        val stockPriceDouble = stockPrice?.toDoubleOrNull()
        if (stockPrice != "-" && stockPriceDouble != null) {

            val result = (stockPriceDouble - price) / price * 100
            revenueStr = String.format("%.2f", result)

        }
        return revenueStr
    }
    fun setListener(listener:(String) -> Unit) {
        clickItemListener = listener
    }
}