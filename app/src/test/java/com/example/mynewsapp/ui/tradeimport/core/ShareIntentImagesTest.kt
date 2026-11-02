package com.example.mynewsapp.ui.tradeimport.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareIntentImagesTest {

    private val send = ShareIntentImages.ACTION_SEND
    private val sendMultiple = ShareIntentImages.ACTION_SEND_MULTIPLE

    @Test
    fun single_image_is_selected() {
        assertEquals(listOf("a"), ShareIntentImages.select(send, "image/png", "a", null))
    }

    @Test
    fun multiple_images_are_selected_in_order() {
        assertEquals(listOf("a", "b"), ShareIntentImages.select(sendMultiple, "image/*", null, listOf("a", null, "b", "a")))
    }

    @Test
    fun clip_data_is_used_when_the_stream_extra_is_missing() {
        assertEquals(listOf("c"), ShareIntentImages.select(send, "image/jpeg", null, null, listOf("c")))
        assertEquals(listOf("a"), ShareIntentImages.select(send, "image/jpeg", "a", null, listOf("c")))
    }

    @Test
    fun other_actions_types_or_empty_shares_select_nothing() {
        assertTrue(ShareIntentImages.select("android.intent.action.VIEW", "image/png", "a", null).isEmpty())
        assertTrue(ShareIntentImages.select(send, "text/plain", "a", null).isEmpty())
        assertTrue(ShareIntentImages.select(send, null, "a", null).isEmpty())
        assertTrue(ShareIntentImages.select<String>(send, "image/png", null, null).isEmpty())
        assertTrue(ShareIntentImages.select<String>(sendMultiple, "image/png", null, emptyList()).isEmpty())
        assertTrue(ShareIntentImages.select(null, "image/png", "a", null).isEmpty())
    }

    @Test
    fun only_content_uris_of_other_apps_are_read() {
        val own = "com.albertkingdom.mystockapp"
        assertTrue(ShareIntentImages.isAllowedSource("content", "com.google.android.apps.photos.contentprovider", own))
        assertTrue(ShareIntentImages.isAllowedSource("content", "media", own))
        assertFalse(ShareIntentImages.isAllowedSource("file", null, own))
        assertFalse(ShareIntentImages.isAllowedSource("file", "", own))
        assertFalse(ShareIntentImages.isAllowedSource("content", own, own))
        assertFalse(ShareIntentImages.isAllowedSource("content", "$own.firebaseinitprovider", own))
        assertFalse(ShareIntentImages.isAllowedSource("content", "media;$own.provider", own))
        assertFalse(ShareIntentImages.isAllowedSource("content", null, own))
        assertFalse(ShareIntentImages.isAllowedSource(null, "media", own))
        assertFalse(ShareIntentImages.isAllowedSource("content", "0@$own.provider", own))
        assertFalse(ShareIntentImages.isAllowedSource("content", "10@$own", own))
        assertTrue(ShareIntentImages.isAllowedSource("content", "0@media", own))
    }
}
