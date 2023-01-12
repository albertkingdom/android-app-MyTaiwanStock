package com.example.mynewsapp.db

import android.database.Observable
import androidx.room.*
import io.reactivex.rxjava3.core.Flowable
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    @Query("SELECT * FROM stocks")
    fun getAllStocks(): Flow<List<Stock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stock:Stock)

    @Query("DELETE FROM stocks WHERE stockNo = :stockNumberToDel")
    suspend fun delete(stockNumberToDel:String)

    @Query("DELETE FROM stocks WHERE stockNo = :stockNo AND parentFollowingListId = :followingListId")
    suspend fun deleteStockByStockNoAndListId(stockNo: String, followingListId: Int)

    @Query("SELECT * FROM investHistory")
    fun getAllHistory(): Flow<List<InvestHistory>>

    @Query("SELECT * FROM investHistory WHERE stockNo = :stockNo")
    fun getHistoryByStockNo(stockNo: String): Flow<List<InvestHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(investHistory: InvestHistory)

    @Query("DELETE FROM investHistory WHERE stockNo = :stockNo")
    suspend fun deleteAllHistory(stockNo:String)

    // Following List
    @Query("SELECT * FROM followingList")
    fun getAllFollowingList(): Flow<List<FollowingList>>

    @Insert
    suspend fun insertFollowingList(followingList: FollowingList): Long

    @Query("DELETE FROM followingList WHERE followingListId = :followingListId")
    suspend fun deleteFollowingList(followingListId: Int)

    @Query("DELETE FROM stocks WHERE parentFollowingListId = :followingListId")
    suspend fun deleteStockAfterDeleteFollowingList(followingListId: Int)

    @Transaction
    @Query("SELECT * FROM followingList WHERE followingListId = :followingListId")
    suspend fun getListsWithStocks(followingListId: Int): FollowingListWithStock

    @Transaction
    @Query("SELECT * FROM followingList WHERE followingListId = :followingListId")
    fun getListsWithStocksRx(followingListId: Int): Flowable<FollowingListWithStock>

    @Transaction
    @Query("SELECT * FROM followingList")
    suspend fun getAllListsWithStocks(): List<FollowingListWithStock>

    @Insert
    suspend fun insertCashDividend(dividend: CashDividend)

    @Insert
    suspend fun insertStockDividend(dividend: StockDividend)

    @Query("SELECT * FROM cashDividend")
    fun getAllCashDividend(): Flow<List<CashDividend>>

    @Query("SELECT * FROM stockDividend")
    fun getAllStockDividend(): Flow<List<StockDividend>>

    @Query("SELECT * FROM cashDividend WHERE stockNo = :stockNo")
    suspend fun getCashDividendBy(stockNo: String): List<CashDividend>

    @Query("SELECT * FROM stockDividend WHERE stockNo = :stockNo")
    suspend fun getStockDividendBy(stockNo: String): List<StockDividend>
}