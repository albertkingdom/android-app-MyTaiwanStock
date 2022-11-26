package com.example.mynewsapp.ui.news

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.ui.adapter.NewsAdapter
import com.example.mynewsapp.databinding.FragmentNewsBinding
import com.example.mynewsapp.util.Resource
import com.google.android.material.snackbar.Snackbar


class NewsFragment : Fragment(R.layout.fragment_news) {

    private lateinit var newsViewModel: NewsViewModel

    private lateinit var binding: FragmentNewsBinding
    private lateinit var newsAdapter: NewsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val repository = (activity?.application as MyApplication).repository

        val newsViewModelFactory = NewsViewModelFactory(repository, (activity?.application as MyApplication))
        newsViewModel = ViewModelProvider(this, newsViewModelFactory).get(NewsViewModel::class.java)

        //newsViewModel.getHeadlines()
        return super.onCreateView(inflater, container, savedInstanceState)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //change toolbar title
        binding = FragmentNewsBinding.bind(view)

        val recyclerView: RecyclerView = binding.newsRecyclerview

        newsAdapter = NewsAdapter()
        newsAdapter.setClickListener{

            val webpage: Uri = Uri.parse(it.url)
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()

            customTabsIntent.launchUrl(requireContext(), webpage)

        }
        recyclerView.adapter = newsAdapter


        newsViewModel.news.observe(viewLifecycleOwner, Observer { response ->
            when (response) {
                is Resource.Success -> {
                    response.data?.let { newsResponse ->
                        newsAdapter.submitList(newsResponse.articles)
                    }
                    binding.swipeRefresh.isRefreshing = false
                }
                is Resource.Error -> {
                    response.message?.let { message ->
                        Snackbar.make(view, "An error occured: $message", Snackbar.LENGTH_LONG).show()
                    }
                }
                is Resource.Loading -> {
                    binding.swipeRefresh.isRefreshing = true
                }
            }
        })

        binding.swipeRefresh.setOnRefreshListener {
            newsViewModel.getHeadlines()
        }
    }

}