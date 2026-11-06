package com.example.mynewsapp.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.core.content.ContextCompat.getColor
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.ItemStockinfoBinding
import com.example.mynewsapp.model.MsgArray
import com.example.mynewsapp.ui.list.StockRowFormatter
import com.example.mynewsapp.ui.list.Trend
import com.google.android.material.color.MaterialColors


class StockInfoAdapter(
    val toCandleStickChart: (Stock: MsgArray)->Unit,
    /** Called when a change label is tapped: switch between amount and percent. */
    private val onToggleChangeMode: () -> Unit,
):ListAdapter<MsgArray, StockInfoAdapter.StockViewHolder>(DiffCallback), Filterable {
    private var list = listOf<MsgArray>()
    private var showAsPercent: Boolean = false
    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<MsgArray>(){
            override fun areItemsTheSame(oldItem: MsgArray, newItem: MsgArray): Boolean {
                return oldItem.stockNo == newItem.stockNo
            }

            override fun areContentsTheSame(oldItem: MsgArray, newItem: MsgArray): Boolean {
                return oldItem == newItem
            }

        }
    }
    // set data for this adapter
    fun setData(list: List<MsgArray>?){
        this.list = list!!
        submitList(list)
    }

    /** Shows the change labels as percent or amount; rebinds the rows when the mode changes. */
    fun setMode(showAsPercent: Boolean) {
        if (this.showAsPercent == showAsPercent) return
        this.showAsPercent = showAsPercent
        notifyItemRangeChanged(0, itemCount)
    }
    class StockViewHolder(view: View):RecyclerView.ViewHolder(view){

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StockViewHolder {
      return StockViewHolder(ItemStockinfoBinding.inflate(LayoutInflater.from(parent.context),parent,false).root)
    }

    override fun onBindViewHolder(holder: StockViewHolder, position: Int) {
        val currentStock = getItem(position)
        val context = holder.itemView.context
        val row = StockRowFormatter.format(currentStock, showAsPercent, context.getString(R.string.watchlist_previous_close))

        ItemStockinfoBinding.bind(holder.itemView).apply {
            stockName.text = currentStock.stockName
            stockSubtitle.text = row.subtitle
            stockPrice.text = row.price
            stockPriceDiff.text = row.change
            val (background, text) = when (row.trend) {
                Trend.UP -> getColor(context, R.color.stock_up_container) to getColor(context, R.color.on_stock_up_container)
                Trend.DOWN -> getColor(context, R.color.stock_down_container) to getColor(context, R.color.on_stock_down_container)
                Trend.FLAT -> MaterialColors.getColor(stockPriceDiff, com.google.android.material.R.attr.colorSurfaceContainerHighest) to
                    MaterialColors.getColor(stockPriceDiff, com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
            stockPriceDiff.backgroundTintList = ColorStateList.valueOf(background)
            stockPriceDiff.setTextColor(text)

            root.setOnClickListener { toCandleStickChart(currentStock) }
            stockPriceDiff.setOnClickListener { onToggleChangeMode() }
        }
    }

// to filter with searchview in list fragment
    override fun getFilter(): Filter {
       return customFilter
    }

    private val customFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filteredList = ArrayList<MsgArray>()
            if (constraint == null || constraint.isEmpty()) {
                filteredList.addAll(list)

            } else {
                for (item in list) {
                    if (item.stockNo.contains(constraint)) {
                        filteredList.add(item)
                    }
                }
            }
            val results = FilterResults()
            results.values = filteredList
            return results

        }

        override fun publishResults(p0: CharSequence?, filterResults: FilterResults?) {
            submitList(filterResults?.values as MutableList<MsgArray>)
        }

    }
}