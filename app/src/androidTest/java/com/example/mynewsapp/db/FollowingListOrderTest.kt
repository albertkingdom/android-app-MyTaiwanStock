package com.example.mynewsapp.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The watchlist tabs and the saved tab position rely on lists always coming back in id order. */
@RunWith(AndroidJUnit4::class)
class FollowingListOrderTest {
    private lateinit var db: StockDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, StockDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun listsAreReturnedInIdOrder() = runBlocking {
        val dao = db.stockDao()
        dao.insertFollowingList(FollowingList(followingListId = 3, listName = "高股息"))
        dao.insertFollowingList(FollowingList(followingListId = 1, listName = "Default"))
        dao.insertFollowingList(FollowingList(followingListId = 2, listName = "長期持有"))

        assertEquals(listOf(1, 2, 3), dao.getAllFollowingList().first().map { it.followingListId })
    }
}
