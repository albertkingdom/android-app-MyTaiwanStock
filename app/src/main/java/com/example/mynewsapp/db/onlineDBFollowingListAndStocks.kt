package com.example.mynewsapp.db

data class OnlineDBFollowingListAndStocks(
    val followingList: List<OnlineFollowingList>? = null
)
data class OnlineFollowingList(
    val listName: String? = null,
    val stocks: List<String>? = null
)
