package com.example.mynewsapp.api

import com.example.mynewsapp.util.Constant.Companion.BASE_URL_NEWS
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitInstance {
    private val logging = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
    private val client = OkHttpClient.Builder().addInterceptor(logging).build()
    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL_NEWS)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
   val retrofitService: NewsAPI by lazy {
       retrofit.create(NewsAPI::class.java)
   }
}