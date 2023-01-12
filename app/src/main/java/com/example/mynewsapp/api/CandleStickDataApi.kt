package com.example.mynewsapp.api

import com.example.mynewsapp.model.CandleStickData
import com.example.mynewsapp.util.Constant.Companion.BASE_URL_CANDLE_STICK_DATA
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface CandleStickDataApi {
    //$BASE_URL_CANDLE_STICK_DATA/STOCK_DAY?response=json&date=20201010&stockNo=2330
    @GET
    suspend fun getCandleStickData(
        @Url
        urlString: String="$BASE_URL_CANDLE_STICK_DATA/STOCK_DAY",
        @Query("response")
        response: String = "json",
        @Query("date")
        date:String,
        @Query("stockNo")
        stockNo:String
    ):Response<CandleStickData>
}