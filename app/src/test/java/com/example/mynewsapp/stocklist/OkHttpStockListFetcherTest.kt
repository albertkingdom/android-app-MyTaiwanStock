package com.example.mynewsapp.stocklist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class OkHttpStockListFetcherTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fetcher() = OkHttpStockListFetcher(
        twseUrl = server.url("/twse").toString(),
        otcUrl = server.url("/otc").toString(),
    )

    @Test
    fun ok_response_returns_body() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"Code":"2330","Name":"台積電"}]"""))
        server.enqueue(MockResponse().setBody("[]"))
        val fetcher = fetcher()
        assertEquals("""[{"Code":"2330","Name":"台積電"}]""", fetcher.fetchTwse())
        assertEquals("/twse", server.takeRequest().path)
        assertEquals("[]", fetcher.fetchOtc())
        assertEquals("/otc", server.takeRequest().path)
    }

    @Test(expected = IOException::class)
    fun non_200_status_throws() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        runBlocking { fetcher().fetchOtc() }
    }

    @Test(expected = IOException::class)
    fun redirect_or_no_content_is_not_success() {
        server.enqueue(MockResponse().setResponseCode(204))
        runBlocking { fetcher().fetchTwse() }
    }

    @Test
    fun default_client_timeouts() {
        val client = OkHttpStockListFetcher.defaultClient()
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(5 * 60_000, client.callTimeoutMillis)
    }

    @Test
    fun cancelling_the_coroutine_cancels_a_stalled_download() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(Dispatchers.Default) {
            try {
                fetcher().fetchOtc()
            } catch (e: IOException) {
            }
        }
        delay(300)
        withTimeout(2_000) {
            job.cancel()
            job.join()
        }
    }
}
