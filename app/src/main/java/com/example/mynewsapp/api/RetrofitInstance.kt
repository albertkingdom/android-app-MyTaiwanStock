package com.example.mynewsapp.api

import com.example.mynewsapp.util.Constant.Companion.BASE_URL_NEWS
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitInstance {
    private val client = newsAwareClient()
    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL_NEWS)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
   val retrofitService: NewsAPI by lazy {
       retrofit.create(NewsAPI::class.java)
   }
}