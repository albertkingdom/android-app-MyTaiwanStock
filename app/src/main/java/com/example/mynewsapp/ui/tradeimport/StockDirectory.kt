package com.example.mynewsapp.ui.tradeimport

import android.content.Context
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.ui.tradeimport.core.StockDirectoryParser
import com.example.mynewsapp.ui.tradeimport.core.StockEntry

/**
 * The stocks the import can resolve names to: the app's stock list (listed and OTC), which can be
 * refreshed while the app runs, so a directory is built from the list as it is when [load] is called.
 */
class StockDirectory(val entries: List<StockEntry>) {
    private val codes = entries.map { it.code }.toSet()

    fun isKnownCode(code: String): Boolean = code in codes

    companion object {
        fun load(context: Context): StockDirectory {
            val repository = (context.applicationContext as MyApplication).stockListRepository
            return StockDirectory(StockDirectoryParser.parse(repository.searchStrings))
        }
    }
}
