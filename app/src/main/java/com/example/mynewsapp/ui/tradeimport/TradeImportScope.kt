package com.example.mynewsapp.ui.tradeimport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex

/**
 * App-wide scope for writing an import. Closing the import screen does not cancel it, so the local
 * records and the online uploads always finish together.
 */
object TradeImportScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Imports are written one at a time, even from two activity instances (a share opens a new
     * instance), so each one sees the timestamps the previous one used.
     */
    val writeLock = Mutex()
}
