package com.example.mynewsapp.repository

import com.example.mynewsapp.model.Article
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

private const val REMOVED_TITLE = "[Removed]"

/**
 * The articles worth showing: with a title and a url, not a "[Removed]" placeholder, and the first one for each url.
 * Keeps the API order.
 */
fun usableArticles(articles: List<Article>): List<Article> = articles.filter { article ->
    val title = article.title?.trim()
    !title.isNullOrEmpty() && title != REMOVED_TITLE && !article.url.isNullOrBlank()
}.distinctBy { it.url }

@JsonClass(generateAdapter = true)
internal data class NewsApiError(val message: String?)

private val errorAdapter by lazy { Moshi.Builder().build().adapter(NewsApiError::class.java) }

/** The message of an unsuccessful response: the API's own `message` when there is one, otherwise "HTTP <code>". */
fun newsErrorMessage(code: Int, errorBody: String?): String {
    val message = try {
        errorBody?.takeIf { it.isNotBlank() }?.let { errorAdapter.fromJson(it)?.message }
    } catch (e: Exception) {
        null
    }
    return message?.takeIf { it.isNotBlank() } ?: "HTTP $code"
}
