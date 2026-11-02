package com.example.mynewsapp.stocklist

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StockMarketLookupTest {

    private val lookup = StockMarketLookup(mapOf("6488" to StockMarket.OTC, "2330" to StockMarket.TSE))

    @Test
    fun market_for_known_and_unknown_codes() {
        assertEquals(StockMarket.OTC, lookup.market("6488"))
        assertEquals(StockMarket.TSE, lookup.market("2330"))
        assertEquals(StockMarket.TSE, lookup.market("9999"))
    }

    @Test
    fun ex_channel_prefix_examples() {
        assertEquals("tse_2330.tw", lookup.exChannel("2330"))
        assertEquals("otc_6488.tw", lookup.exChannel("6488"))
        assertEquals("tse_9999.tw", lookup.exChannel("9999"))
    }

    @Test
    fun mixed_query_keeps_order() {
        assertEquals("otc_6488.tw|tse_2330.tw", lookup.exChannelQuery(listOf("6488", "2330")))
        assertEquals("tse_2330.tw|otc_6488.tw", lookup.exChannelQuery(listOf("2330", "6488")))
    }

    @Test
    fun empty_query() {
        assertEquals("", lookup.exChannelQuery(emptyList()))
    }

    @Test
    fun empty_lookup_defaults_every_code_to_tse() {
        assertEquals("tse_6488.tw|tse_2330.tw", StockMarketLookup(emptyMap()).exChannelQuery(listOf("6488", "2330")))
    }

    @Test
    fun lookup_from_repository_gives_same_query() {
        val directory = Files.createTempDirectory("lookup").toFile()
        try {
            val bundled = File(directory, "bundled.json")
            bundled.writeText(
                StockListParser.encodeFile(
                    listOf(StockListEntry("2330", "台積電", StockMarket.TSE), StockListEntry("6488", "環球晶", StockMarket.OTC))
                )
            )
            val repository = StockListRepository(File(directory, "cache.json")) { bundled.inputStream() }
            assertEquals("otc_6488.tw|tse_2330.tw", repository.marketLookup().exChannelQuery(listOf("6488", "2330")))
        } finally {
            directory.deleteRecursively()
        }
    }
}
