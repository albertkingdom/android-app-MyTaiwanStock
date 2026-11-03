package com.example.mynewsapp.stocklist.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.stocklist.StockMarket
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Refreshes the stock list in the background when it is stale. Failures are never reported to
 * the user and never retried here; the next time the app comes to the foreground decides again.
 */
class StockListRefreshWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as MyApplication
        try {
            val result = application.stockListRefresher.refreshIfNeeded()
            if (result.anyUpdated) {
                val entries = application.stockListRepository.entries
                Timber.i(
                    "stock list refreshed: tse=%d otc=%d",
                    entries.count { it.market == StockMarket.TSE },
                    entries.count { it.market == StockMarket.OTC }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "stock list refresh failed")
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "stock_list_refresh"

        /** Enqueues a refresh check; an already enqueued or running check is kept. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<StockListRefreshWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
