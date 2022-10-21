package com.example.mynewsapp.ui.detail

import androidx.lifecycle.*
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.use_case.CalculateChartDataUseCase
import com.example.mynewsapp.util.GetDateString
import com.github.mikephil.charting.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class CandleStickChartViewModel(val repository: NewsRepository): ViewModel() {
    val calculateChartDataUseCase = CalculateChartDataUseCase()
    private val _candleData = MutableLiveData<CandleData>()
    private val candleData: LiveData<CandleData> = _candleData
    private val _barData = MutableLiveData<BarData>()
    private val barData: LiveData<BarData> = _barData
    val combinedData = combine(candleData, barData) { candleData, barData ->
        Pair(
            candleData,
            barData
        )
    }
    private val _xLabels = MutableLiveData<List<String>>()
    val xLabels: LiveData<List<String>> = _xLabels//x axis label list
    private var _investHistoryList: MutableLiveData<List<InvestHistory>> = MutableLiveData()
    var investHistoryList: LiveData<List<InvestHistory>> = _investHistoryList
    private val _originalCandleData = MutableLiveData<List<List<String>>>()
    val originalCandleData: LiveData<List<List<String>>> = _originalCandleData

    // helper method to combine two liveData
    fun <A, B, C> combine(
        liveData1: LiveData<A>,
        liveData2: LiveData<B>,
        onChanged: (A?, B?) -> C
    ): MediatorLiveData<C> {
        return MediatorLiveData<C>().apply {
            addSource(liveData1) {
                value = onChanged(liveData1.value, liveData2.value)
            }
            addSource(liveData2) {
                value = onChanged(liveData1.value, liveData2.value)
            }
        }
    }
    fun getCandleStickData(currentDate: String, stockNo: String) {

            viewModelScope.launch(Dispatchers.IO) {
                //candleStickData.postValue(Resource.Loading())

                val currentMonthStr = GetDateString.outputCurrentDateString()
                val lastMonthStr = GetDateString.outputLastMonthDateString()
                val responseCurrentMonth =
                    repository.getCandleStickData(currentMonthStr, stockNo)
                val responseLastMonth = repository.getCandleStickData(lastMonthStr, stockNo)

                println("responseCurrentMonth ${responseCurrentMonth.body()}")

                // concat multiple month candle stick data
                val candleStickDataList = mutableListOf<List<String>>()

                candleStickDataList.addAll(responseLastMonth.body()?.data!!)

                if (responseCurrentMonth.body()?.data != null) {
                    candleStickDataList.addAll(responseCurrentMonth.body()?.data!!)
                }

                    if (!responseCurrentMonth.isSuccessful || !responseLastMonth.isSuccessful) {
                        //candleStickData.value = Resource.Error("There's error in fetching candle stick data.")
                    } else {
                        withContext(Dispatchers.Main) {
                            //candleStickData.value = Resource.Success(candleStickDataList)
                            _candleData.value = calculateChartDataUseCase.generateCandleDataSet(candleStickDataList, stockNo)
                            _barData.value = calculateChartDataUseCase.generateBarData(candleStickDataList)
                            _xLabels.value = calculateChartDataUseCase.generateXLabels(candleStickDataList)
                            _originalCandleData.value = candleStickDataList
                        }
                    }


            }

    }



    fun queryHistoryByStockNo(stockNo: String) {
        investHistoryList = repository.queryHistoryByStockNo(stockNo).asLiveData()
    }
    fun clearCandleStickData() {
        //candleStickData.value = null
    }
}