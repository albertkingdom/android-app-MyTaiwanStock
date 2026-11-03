package com.example.mynewsapp.stocklist

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Downloads the raw open-data payloads. Kept behind an interface so tests can inject results. */
interface StockListFetching {
    suspend fun fetchTwse(): String
    suspend fun fetchOtc(): String
}

/** Where the time of the last successful refresh is kept. */
interface RefreshTimeStore {
    var lastRefreshMillis: Long?

    /** When the last refresh that adopted nothing (or could not be saved) ran. */
    var lastFailedAttemptMillis: Long?
}

/**
 * Refreshes the stock list from the official open data when it is stale.
 *
 * A refresh starts when no valid refreshed list is cached or the last successful refresh is at
 * least 7 days old. The two markets are judged independently: a market is adopted only when it
 * was fetched and decoded successfully and has at least 80 percent of the entries that market
 * currently has. Anything else keeps that market's existing entries. Only one refresh runs at a
 * time: a call made while another is in progress waits for and returns that refresh's result.
 */
class StockListRefresher(
    private val repository: StockListRepository,
    private val fetcher: StockListFetching,
    private val timeStore: RefreshTimeStore,
    private val now: () -> Long = System::currentTimeMillis,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    enum class MarketOutcome { UPDATED, SKIPPED, KEPT_OLD_DATA }

    data class RefreshResult(val tse: MarketOutcome, val otc: MarketOutcome) {
        val anyUpdated: Boolean get() = tse == MarketOutcome.UPDATED || otc == MarketOutcome.UPDATED

        companion object {
            val SKIPPED = RefreshResult(MarketOutcome.SKIPPED, MarketOutcome.SKIPPED)
        }
    }

    companion object {
        const val REFRESH_INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val MINIMUM_RATIO = 0.8

        /** Wait after a failed attempt so the multi-megabyte download is not repeated on every launch. */
        const val FAILED_ATTEMPT_BACKOFF_MILLIS = 24L * 60 * 60 * 1000
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Any()
    private var inFlight: Deferred<RefreshResult>? = null

    fun isRefreshDue(): Boolean {
        val current = now()
        // A time in the future means the clock was wrong when it was recorded.
        val failed = timeStore.lastFailedAttemptMillis
        if (failed != null && current >= failed && current - failed < FAILED_ATTEMPT_BACKOFF_MILLIS) return false
        if (!repository.hasCache) return true
        val last = timeStore.lastRefreshMillis ?: return true
        return current < last || current - last >= REFRESH_INTERVAL_MILLIS
    }

    suspend fun refreshIfNeeded(): RefreshResult {
        val deferred: Deferred<RefreshResult>
        val created: Boolean
        synchronized(lock) {
            val existing = inFlight
            if (existing != null) {
                deferred = existing
                created = false
            } else {
                if (!isRefreshDue()) return RefreshResult.SKIPPED
                deferred = scope.async(start = CoroutineStart.LAZY) { performRefresh() }
                inFlight = deferred
                created = true
            }
        }
        if (created) {
            deferred.invokeOnCompletion {
                synchronized(lock) { if (inFlight === deferred) inFlight = null }
            }
            deferred.start()
        }
        return deferred.await()
    }

    private suspend fun performRefresh(): RefreshResult {
        val current = repository.entries
        val currentTse = current.filter { it.market == StockMarket.TSE }
        val currentOtc = current.filter { it.market == StockMarket.OTC }

        val (fetchedTse, fetchedOtc) = coroutineScope {
            val tse = async { loadMarket { StockListParser.parseTwse(fetcher.fetchTwse()) } }
            val otc = async { loadMarket { StockListParser.parseOtc(fetcher.fetchOtc()) } }
            tse.await() to otc.await()
        }

        val tseAdopted = isAcceptable(fetchedTse, currentTse.size)
        val otcAdopted = isAcceptable(fetchedOtc, currentOtc.size)
        if (!tseAdopted && !otcAdopted) return failedAttempt()

        val merged = StockListParser.merge(
            tse = if (tseAdopted) fetchedTse!! else currentTse,
            otc = if (otcAdopted) fetchedOtc!! else currentOtc,
        )
        try {
            repository.replace(merged)
        } catch (e: Exception) {
            return failedAttempt()
        }
        // The new list is live from here on, so the result reports it even if the times cannot be stored.
        try {
            timeStore.lastRefreshMillis = now()
            timeStore.lastFailedAttemptMillis = null
        } catch (e: Exception) {
            // The next launch then simply refreshes again.
        }
        return RefreshResult(
            tse = if (tseAdopted) MarketOutcome.UPDATED else MarketOutcome.KEPT_OLD_DATA,
            otc = if (otcAdopted) MarketOutcome.UPDATED else MarketOutcome.KEPT_OLD_DATA,
        )
    }

    private fun failedAttempt(): RefreshResult {
        try {
            timeStore.lastFailedAttemptMillis = now()
        } catch (e: Exception) {
            // Not recording the attempt only means the next launch tries again.
        }
        return RefreshResult(MarketOutcome.KEPT_OLD_DATA, MarketOutcome.KEPT_OLD_DATA)
    }

    /** A market that fails for any reason yields null; cancellation is never swallowed. */
    private suspend fun loadMarket(work: suspend () -> List<StockListEntry>): List<StockListEntry>? =
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun isAcceptable(fetched: List<StockListEntry>?, currentCount: Int): Boolean {
        if (fetched.isNullOrEmpty()) return false
        return fetched.size >= currentCount * MINIMUM_RATIO
    }
}
