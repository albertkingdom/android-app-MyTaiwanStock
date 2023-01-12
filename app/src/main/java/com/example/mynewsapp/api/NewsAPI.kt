package com.example.mynewsapp.api

import com.example.mynewsapp.model.NewsResponse
import com.example.mynewsapp.util.Constant
import com.example.mynewsapp.util.Constant.Companion.API_KEY
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface NewsAPI {

    @GET("v2/everything")
    suspend fun searchForNews(
        @Query("q")
        stockName:String = "台積電",
        @Query("page")
        page:Int=1,
        @Query("apiKey")
        apiKey:String = API_KEY
    ):Response<NewsResponse>

    //${ Constant.BASE_URL_NEWS }v2/top-headlines?country=$country&page=$page&category=$category&apiKey=$API_KEY
    @GET
    suspend fun getHeadlines(
        @Url
        urlString: String = "${ Constant.BASE_URL_NEWS }v2/top-headlines",
        @Query("country")
        country:String = "tw",
        @Query("category")
        category: String = "business",
        @Query("page")
        page:Int=1,
        @Query("apiKey")
        apiKey:String = API_KEY
    ):Response<NewsResponse>
}