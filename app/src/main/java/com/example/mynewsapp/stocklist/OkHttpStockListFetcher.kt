package com.example.mynewsapp.stocklist

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException
import java.util.concurrent.TimeUnit

/** Downloads the official open-data stock lists; any non-200 response is an [IOException]. */
class OkHttpStockListFetcher(
    private val client: OkHttpClient = defaultClient(),
    private val twseUrl: String = TWSE_URL,
    private val otcUrl: String = OTC_URL,
) : StockListFetching {

    companion object {
        const val TWSE_URL = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL"
        const val OTC_URL = "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_daily_close_quotes"

        /**
         * Fails when connecting or waiting for data takes 30 seconds, or when the whole download
         * takes 5 minutes (the Taipei Exchange payload is several megabytes).
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    override suspend fun fetchTwse(): String = fetch(twseUrl)

    override suspend fun fetchOtc(): String = fetch(otcUrl)

    /** Cancelling the coroutine cancels the HTTP call, so a stalled download never blocks a refresh. */
    private suspend fun fetch(url: String): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = runCatching {
                    response.use {
                        if (it.code != 200) throw IOException("HTTP ${it.code} for $url")
                        it.body?.string() ?: throw IOException("empty body for $url")
                    }
                }
                continuation.resumeWith(body)
            }
        })
    }
}
