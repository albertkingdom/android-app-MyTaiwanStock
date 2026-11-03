package com.example.mynewsapp.stocklist

import com.example.mynewsapp.stocklist.StockListRefresher.MarketOutcome.KEPT_OLD_DATA
import com.example.mynewsapp.stocklist.StockListRefresher.MarketOutcome.SKIPPED
import com.example.mynewsapp.stocklist.StockListRefresher.MarketOutcome.UPDATED
import com.example.mynewsapp.stocklist.StockListRefresher.RefreshResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

private class FakeStockListFetcher : StockListFetching {
    val tseCalls = AtomicInteger()
    val otcCalls = AtomicInteger()
    var tse: () -> String = { "[]" }
    var otc: () -> String = { "[]" }

    /** When set, every fetch waits for it to complete. */
    var gate: CompletableDeferred<Unit>? = null

    /** Completed when the first fetch has started. */
    val entered = CompletableDeferred<Unit>()

    override suspend fun fetchTwse(): String {
        tseCalls.incrementAndGet()
        entered.complete(Unit)
        gate?.await()
        return tse()
    }

    override suspend fun fetchOtc(): String {
        otcCalls.incrementAndGet()
        entered.complete(Unit)
        gate?.await()
        return otc()
    }
}

private class InMemoryRefreshTimeStore : RefreshTimeStore {
    override var lastRefreshMillis: Long? = null
    override var lastFailedAttemptMillis: Long? = null
}

class StockListRefresherTest {

    private lateinit var directory: File
    private lateinit var bundledFile: File
    private lateinit var repository: StockListRepository
    private lateinit var fetcher: FakeStockListFetcher
    private lateinit var timeStore: InMemoryRefreshTimeStore
    private var currentTime = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("refresher").toFile()
        fetcher = FakeStockListFetcher()
        timeStore = InMemoryRefreshTimeStore()
        // Bundled list: 1380 listed + 1007 OTC entries, like the real data.
        bundledFile = File(directory, "bundled.json")
        bundledFile.writeText(StockListParser.encodeFile(entries(tse = 1380, otc = 1007)))
        repository = makeRepository(File(directory, "cache/StockList.json"))
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun makeRepository(cacheFile: File) = StockListRepository(cacheFile) { bundledFile.inputStream() }

    private fun entries(tse: Int, otc: Int): List<StockListEntry> =
        (0 until tse).map { StockListEntry(String.format("%04d", 1000 + it), "上市$it", StockMarket.TSE) } +
            (0 until otc).map { StockListEntry(String.format("%04d", 6000 + it), "上櫃$it", StockMarket.OTC) }

    private fun twseJson(count: Int): String =
        (0 until count).joinToString(",", "[", "]") {
            """{"Code":"${String.format("%04d", 1000 + it)}","Name":"新上市$it","TradeVolume":"1"}"""
        }

    private fun otcJson(count: Int): String =
        (0 until count).joinToString(",", "[", "]") {
            """{"SecuritiesCompanyCode":"${String.format("%04d", 6000 + it)}","CompanyName":"新上櫃$it","Close":"1"}"""
        }

    private fun makeRefresher(repo: StockListRepository = repository) =
        StockListRefresher(repo, fetcher, timeStore, now = { currentTime }, dispatcher = Dispatchers.Default)

    /** Marks a successful refresh `daysAgo` days before now, and creates the cache file. */
    private fun seedLastRefresh(daysAgo: Long) {
        repository.replace(repository.entries)
        timeStore.lastRefreshMillis = currentTime - daysAgo * day
    }

    private fun count(market: StockMarket) = repository.entries.count { it.market == market }

    private fun refresh(refresher: StockListRefresher = makeRefresher()) = runBlocking { refresher.refreshIfNeeded() }

    // refresh decision

    @Test
    fun six_days_after_last_refresh_does_not_refresh() {
        seedLastRefresh(daysAgo = 6)
        assertEquals(RefreshResult(SKIPPED, SKIPPED), refresh())
        assertEquals(0, fetcher.tseCalls.get() + fetcher.otcCalls.get())
    }

    @Test
    fun seven_days_after_last_refresh_refreshes() {
        seedLastRefresh(daysAgo = 7)
        fetcher.tse = { twseJson(1400) }
        fetcher.otc = { otcJson(1010) }
        assertEquals(RefreshResult(UPDATED, UPDATED), refresh())
        assertEquals(1400, count(StockMarket.TSE))
        assertEquals(1010, count(StockMarket.OTC))
        assertEquals(currentTime, timeStore.lastRefreshMillis)
    }

    @Test
    fun no_cache_refreshes_even_if_recently_refreshed() {
        timeStore.lastRefreshMillis = currentTime
        assertTrue(makeRefresher().isRefreshDue())
        fetcher.tse = { twseJson(1380) }
        fetcher.otc = { otcJson(1007) }
        assertEquals(RefreshResult(UPDATED, UPDATED), refresh())
    }

    @Test
    fun corrupted_cache_refreshes_even_if_refreshed_one_day_ago() {
        val cacheFile = File(directory, "corrupt/StockList.json").apply { parentFile.mkdirs(); writeText("garbage") }
        val repo = makeRepository(cacheFile)
        timeStore.lastRefreshMillis = currentTime - day
        assertTrue(makeRefresher(repo).isRefreshDue())
    }

    @Test
    fun last_refresh_in_the_future_is_due() {
        seedLastRefresh(daysAgo = -30)
        assertTrue(makeRefresher().isRefreshDue())
    }

    @Test
    fun never_refreshed_is_due() {
        assertTrue(makeRefresher().isRefreshDue())
    }

    @Test
    fun successful_refresh_records_time_so_next_check_is_skipped() {
        fetcher.tse = { twseJson(1380) }
        fetcher.otc = { otcJson(1007) }
        val refresher = makeRefresher()
        refresh(refresher)
        assertEquals(RefreshResult(SKIPPED, SKIPPED), refresh(refresher))
        assertEquals(1, fetcher.tseCalls.get())
        assertFalse(refresher.isRefreshDue())
    }

    // 80 percent guard

    @Test
    fun otc_below_80_percent_is_rejected_but_tse_still_updates() {
        fetcher.tse = { twseJson(1390) }
        fetcher.otc = { otcJson(700) } // below 1007 * 0.8 = 805.6
        assertEquals(RefreshResult(UPDATED, KEPT_OLD_DATA), refresh())
        assertEquals(1390, count(StockMarket.TSE))
        assertEquals(1007, count(StockMarket.OTC))
        assertTrue(repository.entries.any { it.name == "上櫃0" })
    }

    @Test
    fun guard_boundary_806_of_1007_is_adopted() {
        fetcher.tse = { twseJson(1380) }
        fetcher.otc = { otcJson(806) }
        assertEquals(UPDATED, refresh().otc)
        assertEquals(806, count(StockMarket.OTC))
    }

    @Test
    fun guard_same_count_is_adopted() {
        fetcher.tse = { twseJson(1380) }
        fetcher.otc = { otcJson(700) }
        assertEquals(UPDATED, refresh().tse)
        assertTrue(repository.entries.any { it.name == "新上市0" })
    }

    @Test
    fun empty_payload_is_rejected() {
        fetcher.tse = { "[]" }
        fetcher.otc = { "[]" }
        assertEquals(RefreshResult(KEPT_OLD_DATA, KEPT_OLD_DATA), refresh())
        assertNull(timeStore.lastRefreshMillis)
        assertEquals(1380, count(StockMarket.TSE))
    }

    // failures

    @Test
    fun network_and_decoding_errors_keep_old_data_and_do_not_record_time() {
        fetcher.tse = { throw SocketTimeoutException("timeout") }
        fetcher.otc = { "not json" }
        val before = repository.entries
        assertEquals(RefreshResult(KEPT_OLD_DATA, KEPT_OLD_DATA), refresh())
        assertEquals(before, repository.entries)
        assertNull(timeStore.lastRefreshMillis)
        assertFalse(repository.hasCache)
        assertFalse(File(directory, "cache/StockList.json").exists())
    }

    @Test
    fun unexpected_error_in_one_market_does_not_affect_the_other() {
        fetcher.tse = { twseJson(1390) }
        fetcher.otc = { throw IllegalStateException("boom") }
        assertEquals(RefreshResult(UPDATED, KEPT_OLD_DATA), refresh())
        assertEquals(1390, count(StockMarket.TSE))
        assertEquals(1007, count(StockMarket.OTC))
    }

    @Test
    fun cache_write_failure_keeps_old_data_and_does_not_record_time() {
        val blockedCache = File(directory, "blocked/StockList.json")
        File(blockedCache, "blocker").apply { parentFile.mkdirs(); writeText("x") }
        val repo = makeRepository(blockedCache)
        fetcher.tse = { twseJson(1390) }
        fetcher.otc = { otcJson(1010) }
        val before = repo.entries
        val result = runBlocking { makeRefresher(repo).refreshIfNeeded() }
        assertEquals(RefreshResult(KEPT_OLD_DATA, KEPT_OLD_DATA), result)
        assertEquals(before, repo.entries)
        assertNull(timeStore.lastRefreshMillis)
    }

    // back-off after a failed attempt

    @Test
    fun failed_attempt_is_not_retried_within_24_hours() {
        fetcher.tse = { throw SocketTimeoutException("timeout") }
        fetcher.otc = { throw SocketTimeoutException("timeout") }
        val refresher = makeRefresher()
        assertEquals(RefreshResult(KEPT_OLD_DATA, KEPT_OLD_DATA), refresh(refresher))
        assertEquals(currentTime, timeStore.lastFailedAttemptMillis)
        currentTime += 23 * 60 * 60 * 1000L
        assertFalse(refresher.isRefreshDue())
        assertEquals(RefreshResult(SKIPPED, SKIPPED), refresh(refresher))
        assertEquals(1, fetcher.tseCalls.get())
        currentTime += 60 * 60 * 1000L
        assertTrue(refresher.isRefreshDue())
    }

    @Test
    fun rejected_payloads_count_as_failed_attempt() {
        fetcher.tse = { "[]" }
        fetcher.otc = { otcJson(10) }
        refresh()
        assertEquals(currentTime, timeStore.lastFailedAttemptMillis)
        assertFalse(makeRefresher().isRefreshDue())
    }

    @Test
    fun successful_refresh_clears_failed_attempt() {
        timeStore.lastFailedAttemptMillis = currentTime - 25 * 60 * 60 * 1000L
        fetcher.tse = { twseJson(1380) }
        fetcher.otc = { otcJson(1007) }
        refresh()
        assertNull(timeStore.lastFailedAttemptMillis)
    }

    @Test
    fun failed_attempt_in_the_future_does_not_block_refresh() {
        timeStore.lastFailedAttemptMillis = currentTime + day
        assertTrue(makeRefresher().isRefreshDue())
    }

    @Test
    fun time_store_failure_after_replace_still_reports_update() {
        val throwingStore = object : RefreshTimeStore {
            override var lastRefreshMillis: Long?
                get() = null
                set(_) = throw IllegalStateException("disk full")
            override var lastFailedAttemptMillis: Long?
                get() = null
                set(_) = throw IllegalStateException("disk full")
        }
        fetcher.tse = { twseJson(1390) }
        fetcher.otc = { otcJson(1010) }
        val refresher = StockListRefresher(repository, fetcher, throwingStore, now = { currentTime }, dispatcher = Dispatchers.Default)
        assertEquals(RefreshResult(UPDATED, UPDATED), runBlocking { refresher.refreshIfNeeded() })
        assertEquals(1390, count(StockMarket.TSE))
    }

    // single flight

    @Test
    fun two_concurrent_requests_download_each_source_once() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        fetcher.gate = gate
        fetcher.tse = { twseJson(1380) }
        fetcher.otc = { otcJson(1007) }
        val refresher = makeRefresher()
        val first = async { refresher.refreshIfNeeded() }
        withTimeout(5_000) { fetcher.entered.await() }
        // Runs until it suspends on the in-flight refresh.
        val second = async(start = CoroutineStart.UNDISPATCHED) { refresher.refreshIfNeeded() }
        gate.complete(Unit)
        val results = withTimeout(5_000) { listOf(first.await(), second.await()) }
        assertEquals(1, fetcher.tseCalls.get())
        assertEquals(1, fetcher.otcCalls.get())
        assertEquals(RefreshResult(UPDATED, UPDATED), results[0])
        assertEquals(results[0], results[1])
    }

    @Test
    fun read_after_refresh_returns_new_list() {
        fetcher.tse = { twseJson(1380) }
        fetcher.otc = { otcJson(1007) }
        refresh()
        assertTrue(StockSearch.filter(repository.searchStrings, "新上櫃5").contains("6005 新上櫃5"))
    }
}
