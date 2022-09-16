package com.example.mynewsapp

import android.app.Application
import com.example.mynewsapp.db.StockDatabase
import com.example.mynewsapp.repository.NewsRepository
import timber.log.Timber

class MyApplication:Application() {
    val database by lazy { StockDatabase.getDatabase(this) }
    val repository by lazy { NewsRepository(database.stockDao()) }

    init {
        println("MyApplication initialize!!!")
    }

    override fun onCreate() {
        super.onCreate()
        // initialize timber in application class
        Timber.plant(Timber.DebugTree());
    }

}