package com.albertkingdom.mystockapp.model

import java.util.*

data class History(
    val id: String = UUID.randomUUID().toString(),
    val price: Double? = null,
    val amount: Int? = null,
    val stockNo: String? = null,
    val time: Long? = null,
    val email: String? = null,
    val status: Int? = 0 //0: buy, 1: sell
)
