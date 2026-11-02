package com.example.mynewsapp.ui.detail

import androidx.lifecycle.*
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.tradecost.HoldingsCalculator
import com.example.mynewsapp.db.tradeLine
import com.example.mynewsapp.use_case.CalculateChartDataUseCase
import com.example.mynewsapp.util.GetDateString
import com.github.mikephil.charting.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal


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

    private var historyJob: Job? = null
    private val _holdingsSummary = MutableStateFlow(HoldingsSummary())
    val holdingsSummary = _holdingsSummary.asStateFlow()

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



    /** Keeps the overview in step with the records of the stock, including when no record is left. */
    fun queryHistoryByStockNo(stockNo: String, currentPrice: BigDecimal?) {
        investHistoryList = repository.queryHistoryByStockNo(stockNo).asLiveData()
        // The screen asks again each time its view is created; only the latest query keeps running.
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repository.queryHistoryByStockNo(stockNo)
                .catch { exception -> Timber.w(exception) }
                .collect { list -> _holdingsSummary.value = HoldingsSummary.of(list, currentPrice) }
        }

    }
    class Factory(private val repository: NewsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CandleStickChartViewModel(repository) as T
    }

    fun clearCandleStickData() {
        //candleStickData.value = null
    }

}

/** The overview of one stock as the detail screen shows it. */
data class HoldingsSummary(
    val amountText: String = "0",
    val marketValueText: String = "-",
    val averageBuyPriceText: String = "-",
    val averageSellPriceText: String = "-",
    val showsUnknownFeeNote: Boolean = false,
) {
    companion object {
        fun of(records: List<InvestHistory>, currentPrice: BigDecimal?): HoldingsSummary {
            val calculator = HoldingsCalculator(records.map { it.tradeLine() })
            return HoldingsSummary(
                amountText = calculator.amount.toString(),
                marketValueText = HoldingsCalculator.formatMarketValue(calculator.marketValue(currentPrice)),
                averageBuyPriceText = HoldingsCalculator.format(calculator.averageBuyPrice()),
                averageSellPriceText = HoldingsCalculator.format(calculator.averageSellPrice()),
                showsUnknownFeeNote = calculator.hasBuyWithUnknownFee,
            )
        }
    }
}

