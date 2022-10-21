package com.example.mynewsapp.ui.news

import android.app.Application
import androidx.lifecycle.*
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.model.*
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.util.Resource
import com.example.mynewsapp.util.isNetworkAvailable
import kotlinx.coroutines.*
import retrofit2.Response

class NewsViewModel(
    val newsRepository: NewsRepository,
    application: Application
) :AndroidViewModel(application) {

    val news: MutableLiveData<Resource<NewsResponse>> = MutableLiveData()


    init {
        getHeadlines()
    }


    fun getHeadlines(page: Int = 1) {
        if (isNetworkAvailable(getApplication())) {
            viewModelScope.launch {
                news.postValue(Resource.Loading())
                val response =
                    newsRepository.getHeadlines(country = "tw", category = "business", page = page)
                news.postValue(handleNewsResponse(response))
            }
        } else {
            news.postValue(Resource.Error("No Internet Connection"))
        }

    }






    //get news headlines
    private fun handleNewsResponse(response: Response<NewsResponse>): Resource<NewsResponse> {
        if (response.isSuccessful) {
            response.body()?.let { resultResponse ->
                return Resource.Success(resultResponse)
            }
        }
        return Resource.Error(response.message())
    }



}

class NewsViewModelFactory(
    val newsRepository: NewsRepository,
    val application: MyApplication
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NewsViewModel(newsRepository, application) as T
    }
}