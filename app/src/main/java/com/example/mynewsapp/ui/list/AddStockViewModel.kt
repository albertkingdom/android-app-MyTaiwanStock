package com.example.mynewsapp.ui.list

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.mynewsapp.model.StockIdNameStar
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import timber.log.Timber

class AddStockViewModel: ViewModel() {

    private var searchQuery = ""
    var stockNames = mutableListOf<String>()
    var followingStockIds = listOf<String>()
    val filteredStockNames = MutableLiveData<List<StockIdNameStar>>()
    val compositeDisposable = CompositeDisposable()

    init {
        filterSearchQuery()
    }
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
                    val stockId = stockIdName.split(" ").first()
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
            .map {
                stockNames.filter { string ->
                   string.contains(searchQuery)
               }
            }
            .subscribe(observer)
    }
}