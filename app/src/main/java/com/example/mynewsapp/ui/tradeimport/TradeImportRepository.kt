package com.example.mynewsapp.ui.tradeimport

import android.content.Context
import android.widget.Toast
import androidx.room.withTransaction
import com.example.mynewsapp.R
import com.example.mynewsapp.db.InvestHistory
import com.example.mynewsapp.db.Stock
import com.example.mynewsapp.db.StockDatabase
import com.example.mynewsapp.firebase.FirebaseManager
import com.example.mynewsapp.repository.NewsRepository
import com.example.mynewsapp.ui.tradeimport.core.ExistingTrade
import com.example.mynewsapp.ui.tradeimport.core.ImportPlan
import com.example.mynewsapp.ui.tradeimport.core.InvalidSelectionException
import com.example.mynewsapp.ui.tradeimport.core.ListChoice
import com.example.mynewsapp.ui.tradeimport.core.NothingWrittenFailure
import com.example.mynewsapp.ui.tradeimport.core.PreviewChangedException
import com.example.mynewsapp.ui.tradeimport.core.TradeSide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reads what the preview needs and writes a confirmed import the same way manual entry does. */
class TradeImportRepository(
    private val context: Context,
    private val database: StockDatabase,
    private val repository: NewsRepository,
) {
    suspend fun existingTrades(): List<ExistingTrade> = withContext(Dispatchers.IO) {
        repository.allHistory.first().map {
            ExistingTrade(
                stockNo = it.stockNo,
                epochMillis = it.date,
                price = it.price,
                amount = it.amount,
                side = if (it.status == TradeSide.SELL.status) TradeSide.SELL else TradeSide.BUY,
            )
        }
    }

    suspend fun lists(): List<ListChoice> = withContext(Dispatchers.IO) {
        repository.allFollowingList.first().map { ListChoice(it.followingListId, it.listName) }
    }

    suspend fun stocksInAnyList(): Set<String> = withContext(Dispatchers.IO) {
        repository.allstocks.first().map { it.stockNo }.toSet()
    }

    /** Reading the local records failed; nothing was written. */
    class ReadFailedException(cause: Throwable) : Exception(cause), NothingWrittenFailure

    /** Building the plan or the local write failed; the transaction was rolled back, nothing was written. */
    class NothingWrittenException(cause: Throwable) : Exception(cause), NothingWrittenFailure

    /**
     * Builds the plan with [buildPlan] and writes it in [TradeImportScope], so leaving the screen
     * cancels neither the local write nor the uploads. [buildPlan] runs on the main thread.
     *
     * Imports are written one at a time ([TradeImportScope.writeLock]); the stored records, the
     * stocks already in a list and the chosen list are read inside that lock, right before the plan
     * is built. The records and the list entries are stored in one Room transaction, so a failure
     * writes nothing. The uploads start after the commit; like manual entry they are not awaited and
     * do nothing when the user is signed out.
     *
     * The outcome is always shown as a message from the app scope, so the user sees it even when the
     * import screen was closed or replaced. The result completes with the number of records, or fails
     * with a [NothingWrittenFailure].
     */
    fun write(buildPlan: (existingTrades: List<ExistingTrade>, stocksInAnyList: Set<String>) -> ImportPlan): Deferred<Int> {
        val localDone = CompletableDeferred<Int>()
        TradeImportScope.scope.launch {
            val committed = try {
                TradeImportScope.writeLock.withLock {
                    val (stored, inLists) = try {
                        existingTrades() to stocksInAnyList()
                    } catch (e: Exception) {
                        throw ReadFailedException(e)
                    }
                    val plan = try {
                        withContext(Dispatchers.Main) { buildPlan(stored, inLists) }
                    } catch (e: Exception) {
                        throw if (e is NothingWrittenFailure) e else NothingWrittenException(e)
                    }
                    // The list can be renamed or deleted in another app window while the preview is open.
                    val list = try {
                        plan.list?.let { chosen -> lists().firstOrNull { it.id == chosen.id } }
                    } catch (e: Exception) {
                        throw ReadFailedException(e)
                    }
                    val stocksToAdd = if (list == null) emptyList() else plan.stocksToAdd
                    try {
                        database.withTransaction {
                            for (record in plan.records) {
                                repository.insertHistory(
                                    InvestHistory(
                                        id = 0,
                                        stockNo = record.stockNo,
                                        date = record.epochMillis,
                                        price = record.price,
                                        amount = record.amount,
                                        status = record.side.status,
                                    ),
                                )
                            }
                            if (list != null) {
                                for (stockNo in stocksToAdd) repository.upsert(Stock(stockNo, list.id, "0"))
                            }
                        }
                    } catch (e: Exception) {
                        throw NothingWrittenException(e)
                    }
                    Committed(plan, list, stocksToAdd)
                }
            } catch (e: CancellationException) {
                localDone.completeExceptionally(e)
                throw e
            } catch (t: Throwable) {
                // Everything above runs before the commit or inside the rolled-back transaction, so
                // nothing was written. Throwable: an OutOfMemoryError must not crash the app.
                val e = if (t is NothingWrittenFailure) t else NothingWrittenException(t)
                localDone.completeExceptionally(e)
                showMessage(
                    context.getString(
                        when (e) {
                            is InvalidSelectionException -> R.string.trade_import_invalid_selection
                            is PreviewChangedException -> R.string.trade_import_preview_changed
                            is ReadFailedException -> R.string.trade_import_read_failed
                            else -> R.string.trade_import_write_failed
                        },
                    ),
                )
                return@launch
            }
            // After the commit nothing can turn the import into a reported failure.
            localDone.complete(committed.plan.records.size)
            upload(committed.plan, committed.list, committed.stocksToAdd)
            val listGone = committed.plan.list != null && committed.list == null
            showMessage(
                context.getString(
                    if (listGone) R.string.trade_import_done_list_gone else R.string.trade_import_done,
                    committed.plan.records.size,
                ),
            )
        }
        return localDone
    }

    private class Committed(val plan: ImportPlan, val list: ListChoice?, val stocksToAdd: List<String>)

    /** Uploads like manual entry: not awaited, and a failing upload never affects the local records. */
    private fun upload(plan: ImportPlan, list: ListChoice?, stocksToAdd: List<String>) {
        for (record in plan.records) {
            try {
                FirebaseManager.uploadHistoryToOnlineDB(
                    price = record.price,
                    amount = record.amount,
                    date = record.epochMillis,
                    stockNo = record.stockNo,
                    status = record.side.status,
                )
            } catch (e: Exception) {
                // The local record stays.
            }
        }
        if (list == null) return
        for (stockNo in stocksToAdd) {
            TradeImportScope.scope.launch {
                try {
                    FirebaseManager.uploadNewStockNoToOnlineDB(stockNo, list.name)
                } catch (e: Exception) {
                    // The stock stays in the local list.
                }
            }
        }
    }

    /** Showing the message never turns a finished import into a reported failure. */
    private suspend fun showMessage(text: String) = withContext(Dispatchers.Main) {
        try {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Nothing else to do; the records are as written.
        }
    }
}
