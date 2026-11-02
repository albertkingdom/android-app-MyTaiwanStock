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
import com.example.mynewsapp.tradecost.HoldingsCalculator
import com.example.mynewsapp.tradecost.TradeSide
import com.example.mynewsapp.db.tradeLine
import com.example.mynewsapp.util.getColorThemeRes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


class StockHistoryAdapter: ListAdapter<InvestHistory, StockHistoryAdapter.StockHistoryViewHolder>(
    DiffCallback
) {
    private var stockPrice: BigDecimal? = null
    private var clickItemListener: ((String) -> Unit)? = null

    class StockHistoryViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val statusView: TextView = itemView.findViewById(R.id.status)
        val dateView: TextView = itemView.findViewById(R.id.date)
        val priceView: TextView = itemView.findViewById(R.id.price)
        val amountView: TextView = itemView.findViewById(R.id.amount)
        val revenueView: TextView = itemView.findViewById(R.id.revenue)
        val feeAndTaxView: TextView = itemView.findViewById(R.id.fee_and_tax)
        val primaryTextColor: Int = itemView.context.getColorThemeRes(android.R.attr.textColorPrimary)
        val greenColor: Int = itemView.resources.getColor(R.color.green, null)
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
            val context = itemView.context
            statusView.text = if (TradeSide.fromStatus(currentHistory.status) == TradeSide.BUY) "買" else "賣"
            dateView.text = formatDateLong(currentHistory.date)
            priceView.text = currentHistory.price.toString()
            amountView.text = currentHistory.amount.toString()
            feeAndTaxView.text = context.getString(
                R.string.history_fee_and_tax,
                currentHistory.fee?.toString() ?: "-",
                currentHistory.tax?.toString() ?: "-",
            )

            // A sell has no profit; the color follows the value as it is shown, rounded to two places.
            val profit = HoldingsCalculator.profitPercent(currentHistory.tradeLine(), stockPrice)
                ?.let { HoldingsCalculator.rounded(it) }
            if (profit == null) {
                revenueView.text = "-"
                revenueView.setTextColor(primaryTextColor)
            } else {
                revenueView.text = context.getString(R.string.stockhistory_revenue, profit.toPlainString())
                revenueView.setTextColor(
                    when {
                        profit.signum() > 0 -> Color.RED
                        profit.signum() < 0 -> greenColor
                        else -> primaryTextColor
                    }
                )
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
    /** The current price; null when it is not known, which shows no profit. */
    fun setStockPrice(price: BigDecimal?) {
        stockPrice = price
    }

    fun setListener(listener:(String) -> Unit) {
        clickItemListener = listener
    }
}