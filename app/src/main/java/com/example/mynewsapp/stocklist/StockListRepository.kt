package com.example.mynewsapp.stocklist

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Source of truth for the tradable stock list (listed and OTC).
 *
 * Loads the local cache when it decodes to a non-empty list and otherwise the list bundled with
 * the app, so the list is never empty while the bundled list is valid. The current state is an
 * immutable snapshot swapped atomically, so reads from any thread need no lock and never see a
 * mix of old and new entries; after [replace] every later read returns the new list.
 */
class StockListRepository(
    private val cacheFile: File,
    private val bundledSource: () -> InputStream?,
) {
    private class Snapshot(
        val entries: List<StockListEntry>,
        val fromCache: Boolean,
        bundledMarkets: Map<String, StockMarket>,
    ) {
        val marketByCode: Map<String, StockMarket> = marketsOf(entries)
        val searchStrings: List<String> = entries.map { it.searchString }

        // Quotes for a followed code that is missing from a refreshed daily snapshot (for example
        // a suspended OTC stock) keep the market recorded in the bundled list.
        val lookup = StockMarketLookup(bundledMarkets + marketByCode)
    }

    @Volatile
    private var snapshot = Snapshot(emptyList(), fromCache = false, bundledMarkets = emptyMap())
    @Volatile
    private var bundledMarkets: Map<String, StockMarket> = emptyMap()
    private val writeLock = Any()

    init {
        reload()
    }

    val entries: List<StockListEntry> get() = snapshot.entries

    /** Entries in the "code name" form used by the add-stock search. */
    val searchStrings: List<String> get() = snapshot.searchStrings

    /** Whether the current list came from a valid refreshed cache (the bundled list does not count). */
    val hasCache: Boolean get() = snapshot.fromCache

    fun market(code: String): StockMarket? = snapshot.marketByCode[code]

    /** Exact match against the codes in the list; partial codes are rejected. */
    fun contains(code: String): Boolean = snapshot.marketByCode.containsKey(code)

    fun marketLookup(): StockMarketLookup = snapshot.lookup

    /** Cache first, then the bundled fallback. */
    fun reload() {
        val bundled = decodeOrNull { bundledSource()?.use { it.readBytes().toString(Charsets.UTF_8) } } ?: emptyList()
        val markets = marketsOf(bundled)
        bundledMarkets = markets
        val cached = decodeOrNull { cacheFile.takeIf { it.isFile }?.readText() }
        snapshot = if (!cached.isNullOrEmpty()) {
            Snapshot(cached, fromCache = true, bundledMarkets = markets)
        } else {
            Snapshot(bundled, fromCache = false, bundledMarkets = markets)
        }
    }

    /**
     * Persists [newEntries] atomically (temporary file in the same directory, then an atomic move)
     * and then makes them visible to every later read. On failure the in-memory list and the
     * existing cache file are unchanged.
     */
    @Throws(IOException::class)
    fun replace(newEntries: List<StockListEntry>) {
        synchronized(writeLock) {
            val directory = cacheFile.absoluteFile.parentFile
            if (directory != null && !directory.isDirectory && !directory.mkdirs()) {
                throw IOException("cannot create $directory")
            }
            val tmp = File(directory, cacheFile.name + ".tmp")
            try {
                FileOutputStream(tmp).use { stream ->
                    stream.write(StockListParser.encodeFile(newEntries).toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                Files.move(
                    tmp.toPath(), cacheFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: IOException) {
                // Best effort: never let the cleanup hide the original failure.
                tmp.delete()
                throw e
            }
            snapshot = Snapshot(newEntries, fromCache = true, bundledMarkets = bundledMarkets)
        }
    }

    private companion object {
        /** The first entry of a duplicated code wins. */
        fun marketsOf(entries: List<StockListEntry>): Map<String, StockMarket> =
            LinkedHashMap<String, StockMarket>().also { map ->
                for (entry in entries) if (!map.containsKey(entry.code)) map[entry.code] = entry.market
            }
    }

    private inline fun decodeOrNull(read: () -> String?): List<StockListEntry>? =
        try {
            read()?.let { StockListParser.decodeFile(it) }
        } catch (e: Exception) {
            null
        }
}
