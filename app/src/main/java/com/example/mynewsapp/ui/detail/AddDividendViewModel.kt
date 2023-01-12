package com.example.mynewsapp.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mynewsapp.db.CashDividend
import com.example.mynewsapp.db.StockDividend
import com.example.mynewsapp.repository.NewsRepository
import kotlinx.coroutines.launch

class AddDividendViewModel(val repository: NewsRepository): ViewModel() {

    fun insertCashDividend(dividend: CashDividend) {
        viewModelScope.launch {
            repository.insertCashDividend(dividend)
        }
    }
    fun insertStockDividend(dividend: StockDividend) {
        viewModelScope.launch {
            repository.insertStockDividend(dividend)
        }
    }
}