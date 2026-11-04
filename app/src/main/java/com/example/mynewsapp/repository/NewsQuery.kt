package com.example.mynewsapp.repository

/** What a news screen asks the news API for. Parameters that are null are not sent. */
sealed class NewsQuery {
    abstract val q: String?
    abstract val qInTitle: String?
    abstract val language: String?
    val sortBy: String get() = SORT_BY

    /** General Taiwan stock market news, for the News tab. */
    object TaiwanStock : NewsQuery() {
        override val q: String? = "台股"
        override val qInTitle: String? = null
        override val language: String? = "zh"
    }

    /** Articles whose title contains the stock name, for the stock detail page. */
    data class Stock(val name: String) : NewsQuery() {
        override val q: String? = null
        override val qInTitle: String? = name
        override val language: String? = null
    }

    companion object {
        private const val SORT_BY = "publishedAt"

        /** A missing or blank [name] gives the general query instead of an empty title search. */
        fun forStock(name: String?): NewsQuery {
            val trimmed = name?.trim()
            return if (trimmed.isNullOrEmpty()) TaiwanStock else Stock(trimmed)
        }
    }
}
