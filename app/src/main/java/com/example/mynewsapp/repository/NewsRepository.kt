package com.example.mynewsapp.repository

import com.example.mynewsapp.model.NewsResponse
import com.example.mynewsapp.model.StockPriceInfoResponse
import com.example.mynewsapp.api.RetrofitInstance
import com.example.mynewsapp.api.RetrofitInstanceForGeneralUse
import com.example.mynewsapp.db.*


import com.example.mynewsapp.model.CandleStickData
import com.example.mynewsapp.util.Constant.Companion.API_KEY
import com.example.mynewsapp.util.Constant.Companion.BASE_URL_CANDLE_STICK_DATA
import com.example.mynewsapp.util.Constant.Companion.BASE_URL_NEWS
import com.example.mynewsapp.util.Constant.Companion.BASE_URL_STOCK_PRICE
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import timber.log.Timber

class NewsRepository(val stockDao: StockDao) {
    suspend fun searchNews(stockName:String="台積電", page:Int):Response<NewsResponse>{
        return RetrofitInstance.retrofitService.searchForNews(stockName, page)
    }

    suspend fun getHeadlines(country:String = "tw", page: Int = 1, category: String ="business"):Response<NewsResponse>{
        return RetrofitInstanceForGeneralUse.retrofitServiceForNews.getHeadlines(country = country, page=page, category = category )
    }

    suspend fun getStockPriceInfo(stockNo:String):Response<StockPriceInfoResponse>{
        return RetrofitInstanceForGeneralUse.retrofitServiceForStockInfo.getStockPriceInfo(stockNo=stockNo)
    }
    fun getStockPriceInfoRx(stockNo:String): Single<Response<StockPriceInfoResponse>> {
        return RetrofitInstanceForGeneralUse.retrofitServiceForStockInfo.getStockPriceInfoRx(stockNo=stockNo)
    }
    val allstocks: Flow<List<Stock>> =stockDao.getAllStocks()

    val allFollowingList: Flow<List<FollowingList>> = stockDao.getAllFollowingList()

    suspend fun getOneListWithStocks(followingListId: Int): FollowingListWithStock {
        return stockDao.getListsWithStocks(followingListId)
    }
    fun getOneListWithStocksRx(followingListId: Int): Flowable<FollowingListWithStock> {
        return stockDao.getListsWithStocksRx(followingListId)
    }
    suspend fun insertFollowingList(followingList: FollowingList): Int {
        val primaryKey = stockDao.insertFollowingList(followingList = followingList)
        Timber.d("repo insertFollowingList primary key $primaryKey")
        return primaryKey.toInt()
    }

    suspend fun deleteFollowingList(followingListId: Int) {
        stockDao.deleteFollowingList(followingListId)
        stockDao.deleteStockAfterDeleteFollowingList(followingListId)
    }

    suspend fun updateFollowingList(newListName: String, followingListId: Int) {
        stockDao.updateFollowingListName(newListName, followingListId)
    }
    suspend fun upsert(stock:Stock){
        stockDao.upsert(stock = stock)
    }
    fun getPriceByStockNo(stockNo: String): Single<Stock> {
        return stockDao.getPriceByStockNo(stockNo = stockNo).subscribeOn(Schedulers.io())
    }
    fun getStocksByStockNos(stockNos: List<String>): Single<List<Stock>> {
        return stockDao.getStocksByStockNos(stockNos)
            .subscribeOn(Schedulers.io())
    }
    fun updatePrice(stockNo: String, price: String): Completable {
        return stockDao.updatePrice(stockNo= stockNo, price = price).subscribeOn(Schedulers.io())
    }
    suspend fun deleteStockByStockNoAndListId(stockNo: String, followingListId: Int){
        stockDao.deleteStockByStockNoAndListId(stockNo, followingListId)
    }
    suspend fun getCandleStickData(currentDate:String, stockNo: String):Response<CandleStickData>{

        return  RetrofitInstanceForGeneralUse.retrofitServiceForCandleStickData.getCandleStickData(date = currentDate, stockNo = stockNo)
    }

    val allHistory: Flow<List<InvestHistory>> = stockDao.getAllHistory()
    
    fun queryHistoryByStockNo(stockNo: String): Flow<List<InvestHistory>> {
        return stockDao.getHistoryByStockNo(stockNo)
    }

    suspend fun insertHistory(investHistory: InvestHistory){
        stockDao.insertHistory(investHistory)
    }

    suspend fun deleteAllHistory(stockNo: String){
        stockDao.deleteAllHistory(stockNo)
    }

    suspend fun insertCashDividend(dividend: CashDividend) {
        stockDao.insertCashDividend(dividend)
    }

    suspend fun insertStockDividend(dividend: StockDividend) {
        stockDao.insertStockDividend(dividend)
    }

    fun getCashDividends() = stockDao.getAllCashDividend()

    fun getStockDividends() = stockDao.getAllStockDividend()

    suspend fun getCashDividendsBy(stockNo: String): List<CashDividend> {
        return stockDao.getCashDividendBy(stockNo)
    }

    suspend fun getStockDividendsBy(stockNo: String): List<StockDividend> {
        return stockDao.getStockDividendBy(stockNo)
    }
}