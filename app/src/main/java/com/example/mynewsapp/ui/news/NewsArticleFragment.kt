package com.example.mynewsapp.ui.news

import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.FragmentNewsArticleBinding

class NewsArticleFragment:Fragment(R.layout.fragment_news_article) {
    lateinit var binding: FragmentNewsArticleBinding
    val args: NewsArticleFragmentArgs by navArgs()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentNewsArticleBinding.bind(view)

        val article = args.article
        binding.webView.webViewClient = WebViewClient()
        article.url?.let { binding.webView.loadUrl(it) }
    }
}