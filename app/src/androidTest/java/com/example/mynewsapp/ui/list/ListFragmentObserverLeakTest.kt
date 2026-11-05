package com.example.mynewsapp.ui.list

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mynewsapp.R
import com.example.mynewsapp.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListFragmentObserverLeakTest {

    /**
     * A cold start from the share sheet opens the import screen in MainActivity.onCreate, so the
     * watchlist's view is torn down before its view lifecycle reaches CREATED. Its observers on the
     * activity-scoped ListViewModel must still go, or the ViewModel keeps the old view and fragment.
     */
    @Test
    fun coldStartFromShareIntent_leavesNoWatchlistObserverOnViewModel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://com.example.leaktest.provider/image/1"))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // Not ActivityScenario: it finds the activity by its intent, which MainActivity clears.
        val activity = instrumentation.startActivitySync(intent) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val navController = (activity.supportFragmentManager
                    .findFragmentById(R.id.StockNavHostFragment) as androidx.navigation.fragment.NavHostFragment)
                    .navController
                assertEquals(R.id.tradeImportFragment, navController.currentDestination?.id)
                // Only the watchlist observes this LiveData, and its view is gone.
                assertFalse(
                    "ListViewModel.stockPriceInfo still holds an observer of the destroyed watchlist view",
                    activity.listViewModel.stockPriceInfo.hasObservers(),
                )
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
