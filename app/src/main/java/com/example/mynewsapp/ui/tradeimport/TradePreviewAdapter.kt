package com.example.mynewsapp.ui.tradeimport

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.ItemTradePreviewBinding
import com.example.mynewsapp.ui.tradeimport.core.PreviewItem
import com.example.mynewsapp.ui.tradeimport.core.PreviewStatus
import com.example.mynewsapp.ui.tradeimport.core.TradeImportSession
import com.example.mynewsapp.ui.tradeimport.core.TradeSide

/** Rows of the import preview: a check box to select the trade, and a tap to edit it. */
class TradePreviewAdapter(
    private val onToggle: (PreviewItem) -> Unit,
    private val onEdit: (PreviewItem) -> Unit,
) : ListAdapter<PreviewItem, TradePreviewAdapter.ViewHolder>(Diff) {

    var isLocked = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    class ViewHolder(val binding: ItemTradePreviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemTradePreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val missing = context.getString(R.string.trade_import_missing)
        with(holder.binding) {
            val side = context.getString(if (item.side == TradeSide.SELL) R.string.trade_import_sell else R.string.trade_import_buy)
            val stockNo = item.stockNo ?: context.getString(R.string.trade_import_unknown_stock)
            title.text = context.getString(R.string.trade_import_row_title, stockNo, item.stockName.ifEmpty { missing }, side)
            detail.text = context.getString(
                R.string.trade_import_detail,
                item.amount?.toString() ?: missing,
                item.price?.let(TradeImportSession::formatPrice) ?: missing,
                item.date?.toString() ?: missing,
            )
            tradeType.text = item.tradeTypeText
            status.setText(
                when (item.status) {
                    PreviewStatus.IMPORTABLE -> R.string.trade_import_status_importable
                    PreviewStatus.DUPLICATE -> R.string.trade_import_status_duplicate
                    PreviewStatus.INCOMPLETE -> R.string.trade_import_status_incomplete
                    PreviewStatus.UNSUPPORTED -> R.string.trade_import_status_unsupported
                },
            )
            val selectable = item.status == PreviewStatus.IMPORTABLE || item.status == PreviewStatus.DUPLICATE
            selected.isChecked = item.isSelected
            selected.isEnabled = selectable && !isLocked
            selected.setOnClickListener { onToggle(item) }
            root.isEnabled = !isLocked
            root.setOnClickListener { onEdit(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<PreviewItem>() {
        override fun areItemsTheSame(oldItem: PreviewItem, newItem: PreviewItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PreviewItem, newItem: PreviewItem) = oldItem == newItem
    }
}
