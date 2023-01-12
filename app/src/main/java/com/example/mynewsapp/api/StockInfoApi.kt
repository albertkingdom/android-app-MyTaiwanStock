package com.example.mynewsapp.api

import com.example.mynewsapp.model.StockPriceInfoResponse
import com.example.mynewsapp.util.Constant.Companion.BASE_URL_STOCK_PRICE
import io.reactivex.rxjava3.core.Single
import retrofit2.Response
import retrofit2.http.*


interface StockInfoApi {
    //${BASE_URL_STOCK_PRICE}api/getStockInfo.jsp?ex_ch=tse_2330.tw&json=1

    @GET
    suspend fun getStockPriceInfo(
        @Url
        urlString: String = "${BASE_URL_STOCK_PRICE}api/getStockInfo.jsp",
        @Query("ex_ch")
        stockNo: String,
        @Query("json")
        isJson: String = "1"
    ): Response<StockPriceInfoResponse>

    @GET
    fun getStockPriceInfoRx(
        @Url
        urlString: String = "${BASE_URL_STOCK_PRICE}api/getStockInfo.jsp",
        @Query("ex_ch")
        stockNo: String,
        @Query("json")
        isJson: String = "1"
    ): Single<Response<StockPriceInfoResponse>>
}