package com.example.mynewsapp.ui.tradeimport.core

/**
 * Decides which items of a share intent are images to import. Generic over the URI type so it can
 * be tested without the Android framework.
 */
object ShareIntentImages {
    /** Value of `Intent.ACTION_SEND`. */
    const val ACTION_SEND = "android.intent.action.SEND"

    /** Value of `Intent.ACTION_SEND_MULTIPLE`. */
    const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"

    /**
     * Whether a shared URI can be read on the sender's behalf: only `content:` URIs of another app.
     * `file:` URIs and this app's own providers are refused, so a sender cannot make the app open its
     * private files.
     */
    fun isAllowedSource(scheme: String?, authority: String?, ownPackage: String): Boolean =
        scheme.equals("content", ignoreCase = true) &&
            !authority.isNullOrEmpty() &&
            authority.split(';')
                // A "<user id>@" prefix names the same provider for another user.
                .map { it.replace(userIdPrefix, "") }
                .none { it == ownPackage || it.startsWith("$ownPackage.") }

    private val userIdPrefix = Regex("^\\d+@")

    /**
     * The images to import: [single] for `ACTION_SEND`, [multiple] for `ACTION_SEND_MULTIPLE`, or
     * the intent's [clipItems] when `EXTRA_STREAM` is missing. Empty for any other action, for a
     * MIME type that is not an image, or when there is no item.
     */
    fun <T : Any> select(
        action: String?,
        mimeType: String?,
        single: T?,
        multiple: List<T?>?,
        clipItems: List<T?> = emptyList(),
    ): List<T> {
        if (mimeType == null || !mimeType.startsWith("image/")) return emptyList()
        val streams = when (action) {
            ACTION_SEND -> listOfNotNull(single)
            ACTION_SEND_MULTIPLE -> multiple.orEmpty().filterNotNull()
            else -> return emptyList()
        }
        return streams.ifEmpty { clipItems.filterNotNull() }.distinct()
    }
}
