package com.example.mynewsapp.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Source(
    val name: String?
)