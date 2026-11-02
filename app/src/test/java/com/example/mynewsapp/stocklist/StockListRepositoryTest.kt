package com.example.mynewsapp.stocklist

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class StockListRepositoryTest {

    private lateinit var directory: File
    private lateinit var cacheFile: File
    private lateinit var bundledFile: File

    private val bundledEntries = listOf(StockListEntry("2330", "台積電", StockMarket.TSE))
    private val cachedEntries = listOf(
        StockListEntry("9999", "快取股", StockMarket.TSE),
        StockListEntry("6488", "環球晶", StockMarket.OTC),
    )

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("stocklist").toFile()
        cacheFile = File(directory, "cache/StockList.json")
        bundledFile = File(directory, "bundled.json")
        write(bundledEntries, bundledFile)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun write(entries: List<StockListEntry>, file: File) {
        file.parentFile.mkdirs()
        file.writeText(StockListParser.encodeFile(entries))
    }

    private fun makeRepository(bundled: () -> java.io.InputStream? = { bundledFile.inputStream() }) =
        StockListRepository(cacheFile, bundled)

    private fun tmpFiles(): List<File> =
        directory.walkTopDown().filter { it.name.endsWith(".tmp") }.toList()

    @Test
    fun valid_cache_is_used() {
        write(cachedEntries, cacheFile)
        val repository = makeRepository()
        assertEquals(cachedEntries, repository.entries)
        assertTrue(repository.hasCache)
    }

    @Test
    fun missing_cache_falls_back_to_bundled_list() {
        val repository = makeRepository()
        assertEquals(bundledEntries, repository.entries)
        assertFalse(repository.hasCache)
    }

    @Test
    fun corrupted_cache_falls_back_to_bundled_list() {
        cacheFile.parentFile.mkdirs()
        cacheFile.writeText("not json")
        val repository = makeRepository()
        assertEquals(bundledEntries, repository.entries)
        assertFalse(repository.hasCache)
    }

    @Test
    fun empty_cache_falls_back_to_bundled_list() {
        write(emptyList(), cacheFile)
        val repository = makeRepository()
        assertEquals(bundledEntries, repository.entries)
        assertFalse(repository.entries.isEmpty())
        assertFalse(repository.hasCache)
    }

    @Test
    fun bundled_source_that_throws_is_treated_as_missing() {
        val repository = makeRepository { throw IOException("no asset") }
        assertTrue(repository.entries.isEmpty())
        assertNull(repository.market("2330"))
    }

    @Test
    fun search_strings_market_and_contains() {
        write(cachedEntries, cacheFile)
        val repository = makeRepository()
        assertTrue(repository.searchStrings.contains("6488 環球晶"))
        assertEquals(StockMarket.OTC, repository.market("6488"))
        assertEquals(StockMarket.TSE, repository.market("9999"))
        assertNull(repository.market("0000"))
        assertTrue(repository.contains("6488"))
        assertFalse(repository.contains("23"))
        assertFalse(repository.contains(""))
    }

    @Test
    fun validation_examples_against_bundled_list() {
        val repository = StockListRepository(cacheFile) { TestAssets.bundledStockList().inputStream() }
        assertTrue(repository.contains("2330"))
        assertTrue(repository.contains("6488"))
        assertFalse(repository.contains("23"))
        assertFalse(repository.contains(""))
    }

    @Test
    fun duplicate_code_uses_first_entry_for_market() {
        write(
            listOf(
                StockListEntry("1234", "第一筆", StockMarket.OTC),
                StockListEntry("1234", "第二筆", StockMarket.TSE),
            ),
            cacheFile
        )
        assertEquals(StockMarket.OTC, makeRepository().market("1234"))
    }

    @Test
    fun replace_updates_memory_and_persists_to_cache() {
        val repository = makeRepository()
        repository.replace(cachedEntries)
        assertEquals(cachedEntries, repository.entries)
        assertTrue(repository.hasCache)
        assertTrue(repository.searchStrings.contains("6488 環球晶"))
        assertEquals(cachedEntries, makeRepository().entries)
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun replace_fails_when_cache_path_is_a_directory() {
        File(cacheFile, "blocker").apply { parentFile.mkdirs(); writeText("x") }
        val repository = makeRepository()
        try {
            repository.replace(cachedEntries)
            fail("expected IOException")
        } catch (expected: IOException) {
        }
        assertEquals(bundledEntries, repository.entries)
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun replace_failure_leaves_existing_cache_byte_identical() {
        write(cachedEntries, cacheFile)
        val before = cacheFile.readBytes()
        File(cacheFile.parentFile, cacheFile.name + ".tmp/blocker").apply { parentFile.mkdirs(); writeText("x") }
        val repository = makeRepository()
        try {
            repository.replace(bundledEntries)
            fail("expected IOException")
        } catch (expected: IOException) {
        }
        assertEquals(cachedEntries, repository.entries)
        assertArrayEquals(before, cacheFile.readBytes())
    }

    @Test
    fun market_lookup_reflects_current_list() {
        val repository = makeRepository()
        assertEquals("tse_6488.tw", repository.marketLookup().exChannel("6488"))
        repository.replace(cachedEntries)
        assertEquals("otc_6488.tw", repository.marketLookup().exChannel("6488"))
    }

    @Test
    fun market_lookup_falls_back_to_bundled_market_for_codes_missing_from_cache() {
        write(listOf(StockListEntry("6488", "環球晶", StockMarket.OTC)), bundledFile)
        write(listOf(StockListEntry("2330", "台積電", StockMarket.TSE)), cacheFile)
        val repository = makeRepository()
        assertFalse(repository.contains("6488"))
        assertEquals("otc_6488.tw|tse_2330.tw", repository.marketLookup().exChannelQuery(listOf("6488", "2330")))
    }

    @Test
    fun current_list_market_wins_over_bundled_market() {
        write(listOf(StockListEntry("1234", "轉上市", StockMarket.OTC)), bundledFile)
        val repository = makeRepository()
        repository.replace(listOf(StockListEntry("1234", "轉上市", StockMarket.TSE)))
        assertEquals("tse_1234.tw", repository.marketLookup().exChannel("1234"))
    }

    @Test
    fun real_bundled_list_contains_otc_stock_and_new_etf() {
        val repository = StockListRepository(cacheFile) { TestAssets.bundledStockList().inputStream() }
        assertTrue(repository.searchStrings.contains("6488 環球晶"))
        assertEquals(StockMarket.OTC, repository.market("6488"))
        assertTrue(repository.contains("00929"))
        assertEquals(StockMarket.TSE, repository.market("00929"))
        assertEquals(StockMarket.TSE, repository.market("2330"))
        assertEquals(1380, repository.entries.count { it.market == StockMarket.TSE })
        assertEquals(1007, repository.entries.count { it.market == StockMarket.OTC })
    }
}
