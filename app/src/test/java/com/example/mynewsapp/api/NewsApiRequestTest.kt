package com.example.mynewsapp.api

import com.example.mynewsapp.repository.NewsQuery
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class NewsApiRequestTest {

    private lateinit var server: MockWebServer
    private lateinit var api: NewsAPI

    @Before
    fun setUp() {
        server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"status":"ok","totalResults":0,"articles":[]}"""))
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(NewsAPI::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun search(query: NewsQuery) = runBlocking {
        api.searchForNews(
            q = query.q,
            qInTitle = query.qInTitle,
            language = query.language,
            sortBy = query.sortBy,
            page = 1,
        )
    }

    @Test
    fun stock_query_sends_only_the_title_search() {
        search(NewsQuery.forStock("台積電"))

        val url = server.takeRequest().requestUrl!!
        assertEquals("/v2/everything", url.encodedPath)
        assertEquals("台積電", url.queryParameter("qInTitle"))
        assertEquals("publishedAt", url.queryParameter("sortBy"))
        assertFalse(url.queryParameterNames.contains("q"))
        assertFalse(url.queryParameterNames.contains("language"))
    }

    @Test
    fun taiwan_stock_query_sends_the_topic_in_chinese() {
        search(NewsQuery.TaiwanStock)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/v2/everything", url.encodedPath)
        assertEquals("台股", url.queryParameter("q"))
        assertEquals("zh", url.queryParameter("language"))
        assertEquals("publishedAt", url.queryParameter("sortBy"))
        assertFalse(url.queryParameterNames.contains("qInTitle"))
        assertTrue(url.queryParameterNames.contains("apiKey"))
    }
}
