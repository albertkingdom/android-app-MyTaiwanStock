package com.example.mynewsapp.ui.tradeimport.core

import com.example.mynewsapp.stocklist.StockListRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StockDirectoryParserTest {

    @Test
    fun splits_at_the_first_space_only() {
        assertEquals(
            listOf(StockEntry("0050", "元大台灣50"), StockEntry("00739", "元大MSCI A股")),
            StockDirectoryParser.parse(listOf("0050 元大台灣50", "00739 元大MSCI A股")),
        )
    }

    @Test
    fun malformed_items_are_skipped() {
        assertEquals(emptyList<StockEntry>(), StockDirectoryParser.parse(listOf("", "0050", " ", "0050 ")))
    }

    /** The bundled stock list resolves the stocks of the sample screenshots. */
    @Test
    fun real_directory_resolves_the_sample_stocks() {
        val path = "src/main/assets/StockList.json"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        val cache = File.createTempFile("StockList", ".json").apply { delete(); deleteOnExit() }
        val repository = StockListRepository(cache) { file.inputStream() }
        val items = repository.searchStrings
        val resolver = StockNameResolver(StockDirectoryParser.parse(items))

        assertEquals("0050", resolver.resolve("元大台灣50"))
        assertEquals("2344", resolver.resolve("華邦電"))
        assertEquals("2330", resolver.resolve("台積電"))
        assertEquals("2379", resolver.resolve("瑞昱"))
        assertEquals("00757", resolver.resolve("統一FANG+"))
    }
}
