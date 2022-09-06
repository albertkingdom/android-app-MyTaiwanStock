package com.example.mynewsapp.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertkingdom.mystockapp.model.History
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.util.InputDataStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class AddHistoryViewModel(val repository: NewsRepository): ViewModel() {
    var auth: FirebaseAuth = FirebaseAuth.getInstance()
    val db = Firebase.firestore

    fun insertHistory(investHistory: InvestHistory) {
        viewModelScope.launch {
            repository.insertHistory(investHistory)
        }
    }

    fun checkDataInput(stockNo: String, amount: Int?, price: Double?, date: Long?): InputDataStatus {
        if (stockNo.isEmpty()) {
            return InputDataStatus.InvalidStockNo

        } else if (amount == 0 || amount == null) {
           return InputDataStatus.InvalidAmount

        } else if (price == 0.0 || price == null) {
            return InputDataStatus.InvalidPrice

        } else if (date == null) {
            return InputDataStatus.InvalidDate
        }
        return InputDataStatus.OK
    }

    fun uploadHistoryToOnlineDB(price: Double, amount: Int, date: Long, stockNo: String, status: Int) {
        val email = auth.currentUser?.email ?: return
        val newHistory = History(price = price, amount = amount, email = email, time = date, stockNo = stockNo, status = status)
        db.collection("history").document().set(newHistory)
    }
}