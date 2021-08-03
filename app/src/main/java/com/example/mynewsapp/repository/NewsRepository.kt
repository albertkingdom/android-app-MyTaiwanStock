package com.example.mynewsapp.repository

import com.example.mynewsapp.model.NewsResponse
import com.example.mynewsapp.model.StockPriceInfoResponse
import com.example.mynewsapp.api.RetrofitInstance
import com.example.mynewsapp.api.RetrofitInstanceForStockPrice
import com.example.mynewsapp.db.Stock
import com.example.mynewsapp.db.StockDao
import kotlinx.coroutines.flow.Flow
import retrofit2.Response

class NewsRepository(val stockDao: StockDao) {
    suspend fun searchNews(stockName:String="台積電", page:Int):Response<NewsResponse>{
        return RetrofitInstance.retrofitService.searchForNews(stockName, page)
    }

    suspend fun getHeadlines(country:String, page: Int, category: String):Response<NewsResponse>{
        return RetrofitInstance.retrofitService.getHeadlines(country, category,page)
    }

    suspend fun getStockPriceInfo(stockNo:String):Response<StockPriceInfoResponse>{
        return RetrofitInstanceForStockPrice.retrofitService.getStockPriceInfo(stockNo)
    }

    val allstocks: Flow<List<Stock>> =stockDao.getAllStocks()

    suspend fun insert(stock:Stock){
        stockDao.insert(stock = stock)
    }

    suspend fun delStock(stockNo:String){
        stockDao.delete(stockNo)
    }
}