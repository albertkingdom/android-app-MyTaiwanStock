package com.example.mynewsapp.ui.detail

import androidx.lifecycle.*
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.use_case.CalculateChartDataUseCase
import com.example.mynewsapp.use_case.chart.CandleRows
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.example.mynewsapp.util.GetDateString
import com.github.mikephil.charting.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


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

    private val _totalHistoryAmount = MutableStateFlow<Int>(0)
    val totalHistoryAmount = _totalHistoryAmount.asStateFlow()

    private val _historyBuyAvgPrice = MutableStateFlow(0.0)
    val historyBuyAvgPrice = _historyBuyAvgPrice.asStateFlow()

    private val _historySellAvgPrice = MutableStateFlow(0.0)
    val historySellAvgPrice = _historySellAvgPrice.asStateFlow()

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
    /** True when no candle rows could be loaded (for example an over-the-counter stock). */
    private val _candleDataUnavailable = MutableLiveData(false)
    val candleDataUnavailable: LiveData<Boolean> = _candleDataUnavailable

    fun getCandleStickData(currentDate: String, stockNo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentMonthStr = GetDateString.outputCurrentDateString()
            val lastMonthStr = GetDateString.outputLastMonthDateString()
            val (lastMonthRows, currentMonthRows) = coroutineScope {
                val lastMonth = async { fetchCandleRows(lastMonthStr, stockNo) }
                val currentMonth = async { fetchCandleRows(currentMonthStr, stockNo) }
                lastMonth.await() to currentMonth.await()
            }

            // One sanitized list feeds candles, bars, x labels and the tap handlers so their
            // indices always line up.
            val candleStickDataList = CandleRows.merge(lastMonthRows, currentMonthRows)

            withContext(Dispatchers.Main) {
                if (candleStickDataList.isEmpty()) {
                    _candleDataUnavailable.value = true
                    return@withContext
                }
                _candleDataUnavailable.value = false
                _candleData.value = calculateChartDataUseCase.generateCandleDataSet(candleStickDataList, stockNo)
                _barData.value = calculateChartDataUseCase.generateBarData(candleStickDataList)
                _xLabels.value = calculateChartDataUseCase.generateXLabels(candleStickDataList)
                _originalCandleData.value = candleStickDataList
            }
        }
    }

    /** Rows of one month, or null when the request fails or has no data (e.g. OTC stocks). */
    private suspend fun fetchCandleRows(date: String, stockNo: String): List<List<String>>? =
        try {
            val response = repository.getCandleStickData(date, stockNo)
            if (response.isSuccessful) response.body()?.data else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "candle data request failed for $stockNo")
            null
        }



    fun queryHistoryByStockNo(stockNo: String) {
        investHistoryList = repository.queryHistoryByStockNo(stockNo).asLiveData()
        viewModelScope.launch {
            repository.queryHistoryByStockNo(stockNo)
                .catch { exception -> Timber.w(exception) }
                .collect { list ->
                    if (list.isEmpty()) return@collect
                    val totalBuyAmount = list.filter { it.status == 0 }.map { it.amount }
                        .fold(0) { acc, i -> acc + i }
                    val totalSellAmount = list.filter { it.status == 1 }.map { it.amount }
                        .fold(0) { acc, i -> acc + i }
                    val avgBuyPrice = if (totalBuyAmount > 0) {
                        list.filter { it.status == 0 }.map { it.amount * it.price }
                            .reduce { acc, d -> acc + d } / totalBuyAmount
                    } else {
                        0
                    }
                    val avgSellPrice = if (totalSellAmount > 0) {
                        list.filter { it.status == 1 }.map { it.amount * it.price }
                            .reduce { acc, d -> acc + d } / totalSellAmount
                    } else {
                        0
                    }

                    _totalHistoryAmount.value = totalBuyAmount - totalSellAmount
                    _historyBuyAvgPrice.value = avgBuyPrice.toDouble()
                    _historySellAvgPrice.value = avgSellPrice.toDouble()
                }
        }

    }
    fun clearCandleStickData() {
        //candleStickData.value = null
    }

}