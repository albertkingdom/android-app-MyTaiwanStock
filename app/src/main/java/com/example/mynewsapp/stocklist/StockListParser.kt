package com.example.mynewsapp.stocklist

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import okio.Buffer
import java.io.IOException

/**
 * Turns the official open-data payloads into [StockListEntry] values, and reads and writes the
 * on-disk list file (`{"entries":[{"code","name","market"}]}`) shared by the cache and the
 * bundled asset.
 *
 * Uses Moshi's streaming API so only the needed fields are read and no reflection or code
 * generation is involved. The OTC filter must stay identical to `keep_otc` in
 * scripts/generate_stock_list.py. Every failure is reported as an [IOException].
 */
object StockListParser {

    @Throws(IOException::class)
    fun parseTwse(json: String): List<StockListEntry> =
        parseRows(json, codeField = "Code", nameField = "Name").mapNotNull { (code, name) ->
            makeEntry(code, name, StockMarket.TSE)
        }

    @Throws(IOException::class)
    fun parseOtc(json: String): List<StockListEntry> =
        parseRows(json, codeField = "SecuritiesCompanyCode", nameField = "CompanyName").mapNotNull { (code, name) ->
            if (isKeptOtcCode(code.trim())) makeEntry(code, name, StockMarket.OTC) else null
        }

    /**
     * Keeps stocks (up to 5 characters) and ETFs (start with "00"); drops warrants,
     * which are 6-character codes starting with 70 to 73.
     */
    fun isKeptOtcCode(code: String): Boolean = code.length <= 5 || code.startsWith("00")

    /** Combines both markets sorted by code; a code present in both keeps the TWSE entry. */
    fun merge(tse: List<StockListEntry>, otc: List<StockListEntry>): List<StockListEntry> {
        val tseCodes = tse.map { it.code }.toHashSet()
        return (tse + otc.filter { it.code !in tseCodes }).sortedBy { it.code }
    }

    fun encodeFile(entries: List<StockListEntry>): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { writer ->
            writer.beginObject()
            writer.name("entries")
            writer.beginArray()
            for (entry in entries) {
                writer.beginObject()
                writer.name("code").value(entry.code)
                writer.name("name").value(entry.name)
                writer.name("market").value(entry.market.prefix)
                writer.endObject()
            }
            writer.endArray()
            writer.endObject()
        }
        return buffer.readUtf8()
    }

    @Throws(IOException::class)
    fun decodeFile(json: String): List<StockListEntry> = wrapErrors {
        val reader = JsonReader.of(Buffer().writeUtf8(json))
        val entries = mutableListOf<StockListEntry>()
        var sawEntries = false
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "entries") {
                reader.skipValue()
                continue
            }
            sawEntries = true
            reader.beginArray()
            while (reader.hasNext()) {
                var code: String? = null
                var name: String? = null
                var market: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "code" -> code = reader.nextString()
                        "name" -> name = reader.nextString()
                        "market" -> market = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                val parsedMarket = market?.let { StockMarket.fromPrefix(it) }
                if (code == null || name == null || parsedMarket == null) {
                    throw IOException("invalid entry: code=$code name=$name market=$market")
                }
                entries.add(StockListEntry(code, name, parsedMarket))
            }
            reader.endArray()
        }
        reader.endObject()
        if (!sawEntries) throw IOException("missing entries")
        entries
    }

    private fun parseRows(json: String, codeField: String, nameField: String): List<Pair<String, String>> = wrapErrors {
        val reader = JsonReader.of(Buffer().writeUtf8(json))
        val rows = mutableListOf<Pair<String, String>>()
        reader.beginArray()
        while (reader.hasNext()) {
            var code: String? = null
            var name: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    codeField -> code = reader.nextString()
                    nameField -> name = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (code == null || name == null) throw IOException("row without $codeField or $nameField")
            rows.add(code to name)
        }
        reader.endArray()
        rows
    }

    private fun makeEntry(code: String, name: String, market: StockMarket): StockListEntry? {
        val trimmedCode = code.trim()
        val trimmedName = name.trim()
        if (trimmedCode.isEmpty() || trimmedName.isEmpty()) return null
        return StockListEntry(trimmedCode, trimmedName, market)
    }

    private inline fun <T> wrapErrors(block: () -> T): T =
        try {
            block()
        } catch (e: JsonDataException) {
            throw IOException(e.message, e)
        }
}
