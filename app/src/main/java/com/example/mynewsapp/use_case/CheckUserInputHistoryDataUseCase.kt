package com.example.mynewsapp.use_case

import com.example.mynewsapp.util.InputDataStatus

class CheckUserInputHistoryDataUseCase {
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
}