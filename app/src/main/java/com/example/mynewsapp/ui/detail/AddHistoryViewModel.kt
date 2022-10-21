package com.example.mynewsapp.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.albertkingdom.mystockapp.model.History
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.use_case.CheckUserInputHistoryDataUseCase
import com.example.mynewsapp.util.InputDataStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class AddHistoryViewModel(val repository: NewsRepository): ViewModel() {
    var auth: FirebaseAuth = FirebaseAuth.getInstance()
    val db = Firebase.firestore
    val checkUserInputHistoryDataUseCase = CheckUserInputHistoryDataUseCase()

    fun insertHistory(investHistory: InvestHistory) {
        viewModelScope.launch {
            repository.insertHistory(investHistory)
        }
    }

    fun checkDataInput(stockNo: String, amount: Int?, price: Double?, date: Long?): InputDataStatus {
       return checkUserInputHistoryDataUseCase.checkDataInput(stockNo, amount, price, date)
    }

    fun uploadHistoryToOnlineDB(price: Double, amount: Int, date: Long, stockNo: String, status: Int) {
        val email = auth.currentUser?.email ?: return
        val newHistory = History(price = price, amount = amount, email = email, time = date, stockNo = stockNo, status = status)
        db.collection("history").document().set(newHistory)
    }
}