package com.example.mynewsapp.ui.news

import android.app.Application
import androidx.lifecycle.*
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.model.*
import com.example.mynewsapp.repository.NewsQuery
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.repository.newsErrorMessage
import com.example.mynewsapp.repository.usableArticles
import com.example.mynewsapp.util.Resource
import com.example.mynewsapp.util.isNetworkAvailable
import kotlinx.coroutines.*
import com.squareup.moshi.JsonDataException
import retrofit2.Response
import java.io.IOException

class NewsViewModel(
    val newsRepository: NewsRepository,
    application: Application,
    private val query: NewsQuery = NewsQuery.TaiwanStock
) :AndroidViewModel(application) {

    val news: MutableLiveData<Resource<NewsResponse>> = MutableLiveData()

    private var loadJob: Job? = null


    init {
        getNews()
    }


    fun getNews(page: Int = 1) {
        if (isNetworkAvailable(getApplication())) {
            // a newer request replaces the one still running, so an older answer cannot overwrite it
            loadJob?.cancel()
            loadJob = viewModelScope.launch {
                news.postValue(Resource.Loading())
                val result = try {
                    handleNewsResponse(newsRepository.searchNews(query, page))
                } catch (e: IOException) {
                    Resource.Error(getApplication<Application>().getString(R.string.news_connection_error))
                } catch (e: JsonDataException) {
                    Resource.Error(getApplication<Application>().getString(R.string.news_format_error))
                }
                news.postValue(result)
            }
        } else {
            news.postValue(Resource.Error("No Internet Connection"))
        }

    }


    //get news articles
    private suspend fun handleNewsResponse(response: Response<NewsResponse>): Resource<NewsResponse> {
        val body = response.body()
        if (response.isSuccessful && body != null) {
            return Resource.Success(body.copy(articles = usableArticles(body.articles)))
        }
        val errorBody = withContext(Dispatchers.IO) { response.errorBody()?.string() }
        return Resource.Error(newsErrorMessage(response.code(), errorBody))
    }



}

class NewsViewModelFactory(
    val newsRepository: NewsRepository,
    val application: MyApplication,
    val query: NewsQuery = NewsQuery.TaiwanStock
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NewsViewModel(newsRepository, application, query) as T
    }
}