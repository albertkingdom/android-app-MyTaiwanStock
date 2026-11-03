package com.example.mynewsapp.ui.tradeimport.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ported from the iOS app's `StockNameResolverTests.swift`. The iOS tests that read the iOS
 * stock list use inline entries here; the OTC case (環球晶) is left out because the Android
 * directory has listed stocks only.
 */
class StockNameResolverTest {

    private val resolver = StockNameResolver(
        listOf(
            StockEntry("0050", "元大台灣50"), StockEntry("2344", "華邦電"), StockEntry("2330", "台積電"),
            StockEntry("2379", "瑞昱"), StockEntry("00850", "元大MSCI A股"),
            StockEntry("8888", "重複名"), StockEntry("8889", "重複名"),
        ),
    )

    @Test
    fun names_from_the_screenshots_resolve_to_their_codes() {
        assertEquals("0050", resolver.resolve("元大台灣50"))
        assertEquals("2344", resolver.resolve("華邦電"))
        assertEquals("2330", resolver.resolve("台積電"))
        assertEquals("2379", resolver.resolve("瑞昱"))
    }

    @Test
    fun list_name_with_spaces_matches_ocr_text_without_spaces() {
        assertEquals("00850", resolver.resolve("元大MSCIA股"))
        assertEquals("00850", resolver.resolve("元大MSCI A股"))
    }

    @Test
    fun ocr_text_with_stray_spaces_matches() {
        assertEquals("0050", resolver.resolve("元大 台灣50"))
        assertEquals("2330", resolver.resolve(" 台積電 "))
    }

    @Test
    fun full_width_and_half_width_forms_match() {
        assertEquals("0050", StockNameResolver(listOf(StockEntry("0050", "元大台灣50"))).resolve("元大台灣５０"))
        assertEquals(
            "00850",
            StockNameResolver(listOf(StockEntry("00850", "元大MSCI A股"))).resolve("元大ＭＳＣＩ　Ａ股"),
        )
    }

    @Test
    fun unknown_name_is_not_resolved() {
        assertNull(resolver.resolve("不存在的股票"))
        assertNull(resolver.resolve(""))
    }

    @Test
    fun partial_names_are_not_fuzzy_matched() {
        assertNull(resolver.resolve("台積"))
        assertNull(resolver.resolve("元大台灣"))
    }

    @Test
    fun duplicate_names_are_not_resolved() {
        assertNull(resolver.resolve("重複名"))
    }

    @Test
    fun name_ending_with_plus_sign_resolves() {
        val resolver = StockNameResolver(listOf(StockEntry("00757", "統一FANG+")))

        assertEquals("00757", resolver.resolve("統一FANG+"))
        assertEquals("00757", resolver.resolve("統一FANG＋"))
    }

    @Test
    fun ocr_reading_the_character_one_as_a_dash_still_resolves() {
        val resolver = StockNameResolver(listOf(StockEntry("00757", "統一FANG+")))

        for (dash in listOf('—', '–', '―', '─', 'ー')) {
            assertEquals("dash U+%04X".format(dash.code), "00757", resolver.resolve("統${dash}FANG+"))
        }
    }

    @Test
    fun ascii_hyphen_is_not_treated_as_the_character_one() {
        val resolver = StockNameResolver(listOf(StockEntry("1", "富邦美債1-3"), StockEntry("2", "富邦美債1一3")))

        assertEquals("1", resolver.resolve("富邦美債1-3"))
    }

    @Test
    fun ascii_hyphen_read_for_the_character_one_resolves_when_nothing_else_matches() {
        val resolver = StockNameResolver(listOf(StockEntry("00757", "統一FANG+"), StockEntry("1", "富邦美債1-3")))

        assertEquals("00757", resolver.resolve("統-FANG+"))
        assertEquals("1", resolver.resolve("富邦美債1-3"))
    }

    @Test
    fun hyphen_retry_does_not_resolve_an_ambiguous_name() {
        val resolver = StockNameResolver(listOf(StockEntry("1", "甲一乙"), StockEntry("2", "甲-乙")))

        assertEquals("2", resolver.resolve("甲-乙"))
        assertEquals("1", resolver.resolve("甲一乙"))
    }
}
