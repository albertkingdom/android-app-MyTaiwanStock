package com.example.mynewsapp.ui.list

import android.app.Application
import androidx.lifecycle.*
import androidx.work.*
import com.albertkingdom.mystockapp.model.FavList
import com.albertkingdom.mystockapp.model.History
import com.example.mynewsapp.ui.widget.UpdateWidgetPeriodicTask
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.db.*
import com.example.mynewsapp.firebase.FirebaseManager
import com.example.mynewsapp.model.MsgArray
import com.example.mynewsapp.model.StockPriceInfoResponse
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.util.Constant.Companion.NO_INTERNET_CONNECTION
import com.example.mynewsapp.util.Constant.Companion.WORKER_INPUT_DATA_KEY
import com.example.mynewsapp.util.Resource
import com.example.mynewsapp.util.isNetworkAvailable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.toObject
import com.google.firebase.ktx.Firebase
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.*
import retrofit2.Response
import timber.log.Timber
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit


// inherit AndroidViewModel to obtain context
class ListViewModel(
    val repository: NewsRepository,
    application: Application
) : AndroidViewModel(application) {

    private val sharedPref = application.getSharedPreferences(SHARED_PREF, Application.MODE_PRIVATE)

    /**
     * The selected tab position; the only source of the selection. Read here (not in the list screen)
     * so a cold start that opens another screen first still starts the price pipeline on this list.
     */
    private var lastViewedListIndex = sharedPref.getInt(KEY_CURRENT_LIST, 0)
    val currentSelectedFollowingListId: MutableLiveData<Int> = MutableLiveData(0)

    private val _selectedListPosition = MutableLiveData<Int>()
    /** The tab the list screen shows as selected; always the list whose stocks are shown. */
    val selectedListPosition: LiveData<Int> = _selectedListPosition

    val allFollowingList: LiveData<List<FollowingList>> = repository.allFollowingList.asLiveData()

    /**
     * The list names, for the tabs. Observed by MainActivity for its side effects, so they also run
     * when the list screen has no view: an empty database gets a "Default" list, and every new set of
     * lists (added, renamed or deleted) keeps the selection (see [keepSelection]).
     */
    val followingListNames: LiveData<List<String>> = allFollowingList.map { lists ->
        if (lists.isEmpty()) {
            createFollowingList(FollowingList(followingListId = 0, listName = "Default"))
        } else {
            keepSelection(lists)
        }
        lists.map { it.listName }
    }

    /**
     * Keeps the selected list selected when the lists change. If it still exists only its position is
     * updated (another list before it was added or deleted), without refetching prices. If it is gone
     * (deleted) or nothing is selected yet, the saved position is selected, clamped to the lists.
     */
    private fun keepSelection(lists: List<FollowingList>) {
        val position = lists.indexOfFirst { it.followingListId == currentSelectedFollowingListId.value }
        if (position < 0) {
            selectList(lastViewedListIndex, lists)
        } else if (position != lastViewedListIndex || _selectedListPosition.value != position) {
            savePosition(position)
        }
    }

    private val _showAsPercent = MutableLiveData(false)
    /** Whether the change labels show percent instead of amount; kept while the list screen is rebuilt. */
    val showAsPercent: LiveData<Boolean> = _showAsPercent

    fun toggleShowAsPercent() {
        _showAsPercent.value = !(_showAsPercent.value ?: false)
    }

    val stockPriceInfo = MutableLiveData<Resource<StockPriceInfoResponse>>()

    val stockIdsInCurrentList = MutableLiveData<List<String>>() // a copy of following stock ids

    val compositeDisposable = CompositeDisposable()

//    val db = Firebase.firestore
    var auth: FirebaseAuth = FirebaseAuth.getInstance()

    /*
    1. change following list id
    2. fetch single list -> followingListWithStocks
    3. stockPriceInfo
     */
    private fun setupGetStockPriceDataPipe(followingListId: Int) {
        Timber.d("start price pipeline for list $followingListId")
        compositeDisposable.clear() // cancel existing subscription
        val observable = Observable.just(followingListId)

        val observer = object : Observer<Resource<StockPriceInfoResponse>> {
            override fun onSubscribe(d: Disposable) {
                compositeDisposable.add(d)
            }

            override fun onNext(t: Resource<StockPriceInfoResponse>) {
                stockPriceInfo.value = t
            }

            override fun onError(e: Throwable) {
                stockPriceInfo.value = e.message?.let { Resource.Error(it) }
            }

            override fun onComplete() {
            }
        }
        // repeat every 5 min
        observable
            .subscribeOn(Schedulers.io())
            .repeatWhen { complete -> complete.delay(5, TimeUnit.MINUTES) }
            .flatMap { num ->
                // use flowable so that new data will be auto emitted
                fetchSingleListRx(num).toObservable()
            }
            .observeOn(AndroidSchedulers.mainThread())
            .flatMap { list ->
                getCurrentStockIds(list)
                Observable.just(list)
            }
            .observeOn(Schedulers.io())
            .flatMap { list ->
                if (list.stocks.isEmpty() && isNetworkAvailable(getApplication())) {
                    // return empty array, don't fetch api
                    Observable.just(Resource.Success(StockPriceInfoResponse(listOf())))
                } else if (isNetworkAvailable(getApplication())) {

                    val stockNoStringList = retrieveStockNoStringList(list)
                    setupWorkManagerForUpdateWidget(stockNoStringList)
                    getStockPriceInfoRx(stockNoStringList).toObservable()

                } else {
                    Observable.error(Throwable(NO_INTERNET_CONNECTION))
                }
            }
//            .retry(2)
            .retryWhen { errors ->
                errors.zipWith(Observable.range(1, 3)) { error, retryCount ->
                    if (retryCount < 3) {
                        Timber.d("Retrying... attempt $retryCount")
                        Observable.timer(1, TimeUnit.MINUTES)
                    } else {
                        Observable.error(error)
                    }
                }.flatMap { it }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(observer)
    }
    /**
     * Selects the list at [position] (clamped to the lists), saves it as the last viewed list and
     * starts its price pipeline. Does nothing while there is no list.
     */
    fun selectList(position: Int, lists: List<FollowingList>? = allFollowingList.value) {
        lists ?: return
        val clamped = ListTabSelection.selectedPosition(position, lists.size) ?: return
        val followingListId = lists[clamped].followingListId
        currentSelectedFollowingListId.value = followingListId
        savePosition(clamped)
        setupGetStockPriceDataPipe(followingListId)
    }

    private fun savePosition(position: Int) {
        lastViewedListIndex = position
        sharedPref.edit().putInt(KEY_CURRENT_LIST, position).apply()
        _selectedListPosition.value = position
    }

    /** The selected list's name, read when needed so a renamed list syncs under its new name. */
    fun currentListName(): String? =
        allFollowingList.value?.find { it.followingListId == currentSelectedFollowingListId.value }?.listName

    fun createFollowingList(followingList: FollowingList) {
        viewModelScope.launch {
            repository.insertFollowingList(followingList)
        }
    }

    fun changeCurrentFollowingListId() {
        currentSelectedFollowingListId.value?.let { setupGetStockPriceDataPipe(it) }
    }

    private fun retrieveStockNoStringList(followingListWithStocks: FollowingListWithStock): List<String> {
        return followingListWithStocks.stocks.map { stock -> stock.stockNo }
    }

    private fun setupWorkManagerForUpdateWidget(stockNos: List<String>) {
        val WORK_TAG = "fetch_stock_price_update_widget"
        // convert to json string
        val moshi: Moshi = Moshi.Builder().build()
        val type: Type = Types.newParameterizedType(
            List::class.java,
            String::class.java
        )
        val jsonAdapter = moshi.adapter<List<String>>(type)
        val inputData = Data.Builder()
            .putString(WORKER_INPUT_DATA_KEY, jsonAdapter.toJson(stockNos))
            .build()
        // 建立work request
        val workRequest =
            PeriodicWorkRequestBuilder<UpdateWidgetPeriodicTask>(15L, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .setInputData(inputData)
                .build()


        // 註冊work request 到system
        // 重複任務(or一次性任務)
        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun fetchSingleListRx(followingListId: Int): Flowable<FollowingListWithStock> {
        return repository.getOneListWithStocksRx(followingListId)
            .distinctUntilChanged()
            .share()
    }
    fun deleteFollowingList(followingListId: Int) {
        viewModelScope.launch {
            repository.deleteFollowingList(followingListId)
        }
    }

    fun updateFollowingListName(newListName: String, followingListId: Int) {
        viewModelScope.launch {
            repository.updateFollowingList(newListName, followingListId)
        }
    }
    private fun getStockPriceInfoRx(stockList: List<String>): Single<Resource<StockPriceInfoResponse>> {
        val stockListString: String = getApplication<MyApplication>().stockListRepository
            .marketLookup()
            .exChannelQuery(stockList)
        return  repository.getStockPriceInfoRx(stockListString)
            .flatMap { response -> handleStockPriceInfoResponse(response) }
    }

    private fun handleStockPriceInfoResponse(response: Response<StockPriceInfoResponse>): Single<Resource<StockPriceInfoResponse>> {
        return if (response.isSuccessful) {
            response.body()?.let { resultResponse ->
                updatePriceInDatabase(resultResponse.msgArray)
                    .andThen(Single.just(Resource.Success(resultResponse)))
            } ?: Single.just(Resource.Error(response.message()))
        } else {
             Single.just(Resource.Error(response.message()))
        }
    }

    private fun updatePriceInDatabase(dataList: List<MsgArray>): Completable {
        val stockNosToUpdate = dataList.filter { it.currentPrice == "-" }.map { it.stockNo }
        return repository.getStocksByStockNos(stockNosToUpdate)
            .flatMapCompletable { stocks ->
                Completable.merge(dataList.map { msgArray ->
                    if (msgArray.currentPrice != "-") {
                        repository.updatePrice(msgArray.stockNo, msgArray.currentPrice)
                    } else {
                        stocks.find { it.stockNo == msgArray.stockNo }?.let { stock ->
                            val lastPrice = stock.price
                            val newMsgArray = MsgArray(stockNo = msgArray.stockNo, stockName = msgArray.stockName, lastDayPrice = msgArray.lastDayPrice, currentPrice = lastPrice, ch = msgArray.ch, time = msgArray.time)
                            newMsgArray
                        }
                        Completable.complete()
                    }
                })
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }
    fun addToStockList(stockNo: String, followingListId: Int = currentSelectedFollowingListId.value!!) {
        viewModelScope.launch {
            if (stockIdsInCurrentList.value?.indexOf(stockNo) == -1) {
                repository.upsert(stock = Stock(stockNo, followingListId, "0"))
            }
        }
    }

    fun deleteStockByStockNoAndListId(
        stockNo: String,
        followingListId: Int = currentSelectedFollowingListId.value!!
    ) {
        viewModelScope.launch {
            repository.deleteStockByStockNoAndListId(stockNo, followingListId)
            changeCurrentFollowingListId()
            FirebaseManager.unsubscribeToTopic(channelName = stockNo)
        }
    }
    private fun getCurrentStockIds(list: FollowingListWithStock) {
        val stockIds = list.stocks.map { stock ->
            stock.stockNo
        }
        stockIdsInCurrentList.value = stockIds
    }

    fun deleteAllHistory(stockNo: String) {
        viewModelScope.launch {
            repository.deleteAllHistory(stockNo)
        }
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }

    fun uploadListToOnlineDB(listName: String) {
        viewModelScope.launch {
            FirebaseManager.uploadFollowingList(listName = listName)
        }
    }


    fun uploadNewStockNoToOnlineDB(stockNo: String) {
        val listName = currentListName() ?: return
        Timber.d("upload stock $stockNo to online list $listName")
        viewModelScope.launch {
            FirebaseManager.uploadNewStockNoToOnlineDB(stockNo, listName)
        }
    }
    private fun getLocalListIdByListNameOrNull(newListName: String) : Int? {
        return allFollowingList.value?.find { it.listName == newListName }?.followingListId
    }
    private suspend fun isStockNumberInFollowingList(followingListId: Int, newStockNo: String): Boolean {
        val result = repository.getOneListWithStocks(followingListId = followingListId).stocks.find { it.stockNo == newStockNo }
        return result != null
    }

    private fun syncFollowingList() {
        viewModelScope.launch {
            val documents = FirebaseManager.syncFollowingList()

            if (documents != null) {
                for (document in documents) {
                    val favList = document.toObject<FavList>()
                    val existedFollowingListId = getLocalListIdByListNameOrNull(newListName = favList.name!!)

                    if (existedFollowingListId != null) {
                        updateExistingFollowingList(existedFollowingListId, favList.stocks ?: listOf())
                    } else {
                        createNewFollowingList(favList.name!!, favList.stocks ?: listOf())
                    }
                }
            }
        }
    }

    private fun syncInvestHistory() {
        viewModelScope.launch {
            val documents = FirebaseManager.syncInvestHistory()

            if (documents != null ) {
                for (document in documents) {
                    val history = document.toObject<History>()
                    Timber.d("history $history")

                    val newHistory = InvestHistory(
                        0, stockNo = history.stockNo!!,
                        amount = history.amount!!,
                        price = history.price!!,
                        status = history.status!!,
                        date = history.time!!
                    )

                    insertHistory(newHistory)
                }
            }
        }
    }

    private suspend fun updateExistingFollowingList(followingListId: Int, stockNos: List<String>) {
        for (stockNo in stockNos) {
            if (!isStockNumberInFollowingList(followingListId, stockNo)) {
                val newStock = Stock(stockNo, followingListId, "0")
                repository.upsert(newStock)
            }
        }
    }

    private suspend fun createNewFollowingList(listName: String, stockNos: List<String>) {
        val newFollowingList = FollowingList(followingListId = 0, listName = listName)
        val followingListId = repository.insertFollowingList(newFollowingList)

        for (stockNo in stockNos) {
            val newStock = Stock(stockNo, followingListId, "0")
            repository.upsert(newStock)
        }
    }

    private suspend fun insertHistory(newHistory: InvestHistory) {
        repository.insertHistory(newHistory)
    }

    fun deleteStockNoFromOnlineDB(stockNo: String) {
        val listName = currentListName() ?: return
        viewModelScope.launch {
            FirebaseManager.deleteStockNo(stockNo, listName)
        }
    }
    fun deleteListFromOnlineDB(listName: String) {
        viewModelScope.launch {
            FirebaseManager.deleteList(listName)
        }
    }
    fun getAllOnlineDBDataAndSaveToLocal() {
        syncFollowingList()
        syncInvestHistory()
    }

    companion object {
        /** Also read by the trade import screen to preselect the last viewed list. */
        const val SHARED_PREF = "sharedPref"
        const val KEY_CURRENT_LIST = "currentList"
    }
}

class ListViewModelFactory(val repository: NewsRepository, val application: MyApplication): ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
       return ListViewModel(repository = repository, application = application) as T
    }

}






