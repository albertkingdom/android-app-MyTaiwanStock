package com.example.mynewsapp.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class NewsApiKeyInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.enqueue(MockResponse().setBody("{}"))
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun send(apiKey: String, host: String, logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger { }): RecordedRequest {
        newsAwareClient(apiKey, host, logger)
            .newCall(Request.Builder().url(server.url("/v2/everything?q=x")).build())
            .execute()
            .close()
        return server.takeRequest()
    }

    @Test
    fun the_key_is_sent_as_a_header_to_the_news_host() {
        val request = send("abc123", host = server.hostName)

        assertEquals("abc123", request.getHeader("X-Api-Key"))
        assertFalse(request.requestUrl!!.queryParameterNames.contains("apiKey"))
    }

    @Test
    fun a_blank_key_adds_no_header() {
        assertNull(send("", host = server.hostName).getHeader("X-Api-Key"))
        server.enqueue(MockResponse().setBody("{}"))
        assertNull(send("   ", host = server.hostName).getHeader("X-Api-Key"))
    }

    @Test
    fun other_hosts_get_no_header() {
        assertNull(send("abc123", host = "newsapi.org").getHeader("X-Api-Key"))
    }

    @Test(expected = IOException::class)
    fun a_key_a_header_cannot_carry_fails_as_an_io_error() {
        send("\u201cabc\u201d", host = server.hostName)
    }

    @Test
    fun the_real_news_url_gets_the_header() {
        var seen: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(NewsApiKeyInterceptor("abc123"))
            .addInterceptor(Interceptor { chain ->
                seen = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody(null))
                    .build()
            })
            .build()

        client.newCall(Request.Builder().url("https://newsapi.org/v2/everything?qInTitle=台積電").build()).execute().close()

        assertEquals("abc123", seen!!.header("X-Api-Key"))
    }

    @Test
    fun the_key_is_redacted_in_the_log() {
        val lines = mutableListOf<String>()

        send("secretkey123", host = server.hostName, logger = { lines += it })

        assertTrue(lines.joinToString("\n"), lines.any { it == "X-Api-Key: ██" })
        assertTrue(lines.none { it.contains("secretkey123") })
    }
}
