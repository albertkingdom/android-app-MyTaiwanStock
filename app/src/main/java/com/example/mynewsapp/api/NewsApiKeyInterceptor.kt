package com.example.mynewsapp.api

import com.example.mynewsapp.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException

private const val NEWS_API_HOST = "newsapi.org"
private const val API_KEY_HEADER = "X-Api-Key"

/**
 * Sends the news API key as a header, only to [host] and only when there is a key, so it never reaches other hosts or a URL.
 * A key that cannot be sent as a header fails the request with an [IOException].
 */
class NewsApiKeyInterceptor(
    private val apiKey: String,
    private val host: String = NEWS_API_HOST,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (apiKey.isBlank() || request.url.host != host) return chain.proceed(request)
        val withKey = try {
            request.newBuilder().header(API_KEY_HEADER, apiKey).build()
        } catch (e: IllegalArgumentException) {
            // a key with characters a header cannot carry (for example curly quotes) must not crash the caller
            throw IOException("The news API key has characters that cannot be sent in a header", e)
        }
        return chain.proceed(withKey)
    }
}

/**
 * The HTTP client of the Retrofit instances. The key interceptor comes first, so the logging interceptor sees the
 * header and prints it redacted.
 */
fun newsAwareClient(
    apiKey: String = BuildConfig.NEWS_API_KEY,
    host: String = NEWS_API_HOST,
    logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
): OkHttpClient {
    val logging = HttpLoggingInterceptor(logger).setLevel(HttpLoggingInterceptor.Level.BODY)
    logging.redactHeader(API_KEY_HEADER)
    return OkHttpClient.Builder()
        .addInterceptor(NewsApiKeyInterceptor(apiKey, host))
        .addInterceptor(logging)
        .build()
}
