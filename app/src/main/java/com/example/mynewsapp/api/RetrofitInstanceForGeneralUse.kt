package com.example.mynewsapp.api

import com.example.mynewsapp.util.Constant.Companion.BASE_URL_CANDLE_STICK_DATA
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitInstanceForGeneralUse {
    private val logging = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
    private val client = OkHttpClient.Builder().addInterceptor(logging).build()
    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL_CANDLE_STICK_DATA) // default
        .addConverterFactory(MoshiConverterFactory.create())
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
        .client(client)
        .build()

    val retrofitServiceForCandleStickData: CandleStickDataApi by lazy {
        retrofit.create(CandleStickDataApi::class.java)
    }
    val retrofitServiceForStockInfo: StockInfoApi by lazy {
        retrofit.create(StockInfoApi::class.java)
    }
    val retrofitServiceForNews: NewsAPI by lazy {
        retrofit.create(NewsAPI::class.java)
    }
}