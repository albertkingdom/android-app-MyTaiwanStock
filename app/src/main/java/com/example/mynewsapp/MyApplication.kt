package com.example.mynewsapp

import android.app.Application
import com.example.mynewsapp.db.StockDatabase
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.stocklist.OkHttpStockListFetcher
import com.example.mynewsapp.stocklist.StockListRefresher
import com.example.mynewsapp.stocklist.StockListRepository
import com.example.mynewsapp.stocklist.android.SharedPreferencesRefreshTimeStore
import timber.log.Timber
import java.io.File
import kotlin.concurrent.thread

class MyApplication:Application() {
    val database by lazy { StockDatabase.getDatabase(this) }
    val repository by lazy { NewsRepository(database.stockDao()) }

    /** Listed and OTC stock list: refreshed cache first, otherwise the bundled asset. */
    val stockListRepository by lazy {
        StockListRepository(File(filesDir, "StockList.json")) { assets.open("StockList.json") }
    }
    val stockListRefresher by lazy {
        StockListRefresher(
            repository = stockListRepository,
            fetcher = OkHttpStockListFetcher(),
            timeStore = SharedPreferencesRefreshTimeStore(this)
        )
    }

    init {
        println("MyApplication initialize!!!")
    }

    override fun onCreate() {
        super.onCreate()
        // initialize timber in application class
        Timber.plant(Timber.DebugTree());
        // Load the stock list off the main thread; the first UI read waits on the lazy lock.
        thread(name = "stock-list-preload") { stockListRepository }
    }

}