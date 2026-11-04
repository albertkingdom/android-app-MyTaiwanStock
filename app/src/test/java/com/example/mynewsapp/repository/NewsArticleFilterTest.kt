package com.example.mynewsapp.repository

import com.example.mynewsapp.model.Article
import com.example.mynewsapp.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsArticleFilterTest {

    private fun article(title: String?, url: String?) = Article(
        author = null,
        description = null,
        publishedAt = "2026-11-03T08:00:00Z",
        source = Source("source"),
        title = title,
        url = url,
        urlToImage = null,
    )

    @Test
    fun articles_without_title_or_url_are_dropped() {
        val kept = article("a", "https://example.com/a")
        val result = usableArticles(
            listOf(article(null, "https://example.com/1"), article("b", null), article("  ", "https://example.com/2"), article("c", " "), kept),
        )
        assertEquals(listOf(kept), result)
    }

    @Test
    fun removed_placeholder_articles_are_dropped() {
        val kept = article("kept", "https://example.com/k")
        assertEquals(listOf(kept), usableArticles(listOf(article("[Removed]", "https://removed.com"), kept)))
    }

    @Test
    fun repeated_urls_keep_only_the_first_article() {
        val first = article("first", "https://example.com/same")
        val copy = article("syndicated copy", "https://example.com/same")
        val other = article("other", "https://example.com/other")
        assertEquals(listOf(first, other), usableArticles(listOf(first, copy, other)))
    }

    @Test
    fun the_api_order_is_kept() {
        val first = article("first", "https://example.com/1")
        val second = article("second", "https://example.com/2")
        val third = article("third", "https://example.com/3")
        assertEquals(listOf(first, second, third), usableArticles(listOf(first, second, third)))
    }

    @Test
    fun error_message_comes_from_the_error_body() {
        val body = """{"status":"error","code":"rateLimited","message":"too many"}"""
        assertEquals("too many", newsErrorMessage(429, body))
    }

    @Test
    fun error_message_falls_back_to_the_status_code() {
        assertEquals("HTTP 429", newsErrorMessage(429, ""))
        assertEquals("HTTP 429", newsErrorMessage(429, null))
        assertEquals("HTTP 500", newsErrorMessage(500, "<html>oops</html>"))
        assertEquals("HTTP 401", newsErrorMessage(401, """{"status":"error","message":""}"""))
    }
}
