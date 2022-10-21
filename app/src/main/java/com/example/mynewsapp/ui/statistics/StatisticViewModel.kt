package com.example.mynewsapp.ui.statistics

import androidx.lifecycle.*
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.model.StockPriceInfoResponse
import com.example.mynewsapp.model.StockStatistic
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.use_case.CalculateAssetUseCase
import com.example.mynewsapp.util.Resource
import com.github.mikephil.charting.data.PieData

class StatisticViewModel(val repository: NewsRepository): ViewModel() {
    val calculateAssetUseCase: CalculateAssetUseCase = CalculateAssetUseCase()
    private val allInvestHistoryList: LiveData<List<InvestHistory>> = repository.allHistory.asLiveData()

    private val stockPriceInfo = MutableLiveData<Resource<StockPriceInfoResponse>>()

    var investStatisticsList: LiveData<List<StockStatistic>> = Transformations.map(allInvestHistoryList) { listOfInvestHistory ->

        calculateAssetUseCase.calculatePieChartSourceData(listOfInvestHistory, stockPriceInfo)
    }

    var pieData: LiveData<PieData> = Transformations.map(investStatisticsList) { listOfStockStatistic ->
        calculateAssetUseCase.calculatePieData(listOfStockStatistic)
    }

    fun setStockPriceInfo(stockPrice: Resource<StockPriceInfoResponse>) {
        stockPriceInfo.value = stockPrice
    }

}