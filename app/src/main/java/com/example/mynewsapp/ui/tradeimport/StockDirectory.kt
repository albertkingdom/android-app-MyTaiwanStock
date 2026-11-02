package com.example.mynewsapp.ui.tradeimport

import android.content.Context
import com.example.mynewsapp.R
import com.example.mynewsapp.ui.tradeimport.core.StockDirectoryParser
import com.example.mynewsapp.ui.tradeimport.core.StockEntry

/**
 * The stocks the import can resolve names to. Today this is the listed-stock array the add-stock
 * screen uses; replace [load] when the OTC stock directory is available.
 */
class StockDirectory(val entries: List<StockEntry>) {
    private val codes = entries.map { it.code }.toSet()

    fun isKnownCode(code: String): Boolean = code in codes

    companion object {
        @Volatile
        private var cached: StockDirectory? = null

        /** Parsed once per process; the directory is a bundled resource. */
        fun load(context: Context): StockDirectory = cached ?: synchronized(this) {
            cached ?: StockDirectory(
                StockDirectoryParser.parse(context.resources.getStringArray(R.array.countries_array).toList()),
            ).also { cached = it }
        }
    }
}
