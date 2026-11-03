package com.example.mynewsapp.ui.list

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.mynewsapp.model.StockIdNameStar
import com.example.mynewsapp.stocklist.StockSearch
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import timber.log.Timber

class AddStockViewModel: ViewModel() {

    private var searchQuery = ""
    /** Read on every filter so a list refreshed while this screen is open is used right away. */
    var stockNamesProvider: () -> List<String> = { emptyList() }
    var followingStockIds = listOf<String>()
    val filteredStockNames = MutableLiveData<List<StockIdNameStar>>()
    val compositeDisposable = CompositeDisposable()
    fun updateSearchQuery(query: String) {
        searchQuery = query
    }
    fun filterSearchQuery() {
        compositeDisposable.clear()

        val observer = object : Observer<List<String>> {
            override fun onSubscribe(d: Disposable) {
                compositeDisposable.add(d)
            }

            override fun onNext(listOfString: List<String>) {

                val stockNameAndStarList = listOfString.map { stockIdName ->
                    val stockId = StockSearch.codeFromInput(stockIdName)
                    if (followingStockIds.indexOf(stockId) != -1) {
                        return@map StockIdNameStar(stockIdName, true)
                    }


                    StockIdNameStar(stockIdName, false)
                }
                filteredStockNames.value = stockNameAndStarList
            }

            override fun onError(e: Throwable) {
                Timber.e(e.toString())
            }

            override fun onComplete() {
                Timber.d("onComplete")
            }

        }
        Observable
            .just(searchQuery)
            .map { query ->
                StockSearch.filter(stockNamesProvider(), query)
            }
            .subscribe(observer)
    }
}