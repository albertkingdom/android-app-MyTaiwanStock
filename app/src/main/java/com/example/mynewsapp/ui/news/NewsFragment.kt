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
import com.example.mynewsapp.repository.NewsQuery
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

        val stockName = arguments?.getString(ARG_STOCK_NAME)
        val newsViewModelFactory = NewsViewModelFactory(repository, (activity?.application as MyApplication), NewsQuery.forStock(stockName))
        newsViewModel = ViewModelProvider(this, newsViewModelFactory).get(NewsViewModel::class.java)

        return super.onCreateView(inflater, container, savedInstanceState)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //change toolbar title
        binding = FragmentNewsBinding.bind(view)

        arguments?.getString(ARG_STOCK_NAME)?.takeIf { it.isNotBlank() }?.let { stockName ->
            (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.news_title_for_stock, stockName.trim())
        }

        val recyclerView: RecyclerView = binding.newsRecyclerview

        newsAdapter = NewsAdapter()
        newsAdapter.setClickListener{

            val webpage: Uri = Uri.parse(it.url ?: return@setClickListener)
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
                        binding.emptyView.visibility = if (newsResponse.articles.isEmpty()) View.VISIBLE else View.GONE
                    }
                    binding.swipeRefresh.isRefreshing = false
                }
                is Resource.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    response.message?.let { message ->
                        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
                    }
                }
                is Resource.Loading -> {
                    binding.swipeRefresh.isRefreshing = true
                }
            }
        })

        binding.swipeRefresh.setOnRefreshListener {
            newsViewModel.getNews()
        }
    }


    companion object {
        /** Bundle key of the stock name; without it the screen shows the Taiwan stock market news. */
        const val ARG_STOCK_NAME = "stockName"
    }
}
