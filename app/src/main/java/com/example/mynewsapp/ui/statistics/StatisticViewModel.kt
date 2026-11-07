package com.example.mynewsapp.ui.statistics

import androidx.lifecycle.*
import com.example.mynewsapp.db.CashDividend
import com.example.mynewsapp.db.StockDividend
import com.example.mynewsapp.model.DividendStatistic
import com.example.mynewsapp.model.StockPriceInfoResponse
import com.example.mynewsapp.model.StockStatistic
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.use_case.CalculateAssetUseCase
import com.example.mynewsapp.use_case.CalculateDividendUseCase
import com.example.mynewsapp.util.Resource
import com.github.mikephil.charting.data.PieData
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StatisticViewModel(val repository: NewsRepository): ViewModel() {
    private val calculateAssetUseCase: CalculateAssetUseCase = CalculateAssetUseCase()
    private val calculateDividendUseCase = CalculateDividendUseCase()

    private val stockPriceInfo = MutableLiveData<Resource<StockPriceInfoResponse>>()
    // 依代號篩選股利
    private val _dividendsByStockNo = MutableLiveData<List<DividendStatistic>>()
    val dividendsByStockNo: LiveData<List<DividendStatistic>> = _dividendsByStockNo

    private val _pieDataFlow = MutableStateFlow(PieData())
    val pieDataFlow = _pieDataFlow.asStateFlow()
    // data for statistic page view pager
    private val _combineData = MutableStateFlow<Pair<List<DividendStatistic>,List<StockStatistic>>>(Pair(
        emptyList(), emptyList()
    ))
    val combineData = _combineData.asStateFlow()

    private val dividendFlow = repository.getCashDividends()
        .combine(repository.getStockDividends()) { cash: List<CashDividend>, stock: List<StockDividend> ->
            calculateDividendUseCase.calculateDividend(cash, stock)
        }

    private var collectJob: Job? = null

    /**
     * (Re)starts the collectors; the statistic screen calls this each time its view is created so
     * the totals are recalculated with the latest prices. The previous run is cancelled first, or
     * every new view would add collectors that live as long as the activity.
     */
    fun collectData() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            val investHistoryFlow = repository.allHistory.map { listOfHistory ->
                calculateAssetUseCase.calculatePieChartSourceData(listOfHistory, stockPriceInfo)
            }

            launch {
                dividendFlow.combine(investHistoryFlow) { dividendStatisticList: List<DividendStatistic>, stockStatisticList: List<StockStatistic> ->
                    Pair(dividendStatisticList, stockStatisticList)
                }.collect {
                    _combineData.value = it
                }
            }

            launch {
                investHistoryFlow
                    .map {
                        calculateAssetUseCase.calculatePieData(it)
                    }.collect {
                        _pieDataFlow.value = it
                    }
            }
        }
    }
    fun setStockPriceInfo(stockPrice: Resource<StockPriceInfoResponse>) {
        stockPriceInfo.value = stockPrice
    }

    fun getDividendBy(stockNo: String) {
        val dividends = mutableListOf<DividendStatistic>()
        viewModelScope.launch {
            val cashDividends = repository.getCashDividendsBy(stockNo)
            val stockDividends = repository.getStockDividendsBy(stockNo)

            for (cash in cashDividends) {
                dividends.add(DividendStatistic(stockNo = stockNo, cashAmount = cash.amount, stockAmount = 0F, date = cash.date))
            }

            for (stock in stockDividends) {
                dividends.add(DividendStatistic(stockNo = stockNo, cashAmount = 0, stockAmount = stock.amount, date = stock.date))
            }

            _dividendsByStockNo.value = dividends
        }
    }
}

class StatisticViewModelFactory(
    val repository: NewsRepository
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StatisticViewModel(repository) as T
    }
}