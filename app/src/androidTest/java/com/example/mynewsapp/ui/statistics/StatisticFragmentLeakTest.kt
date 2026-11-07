package com.example.mynewsapp.ui.statistics

import android.content.Intent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mynewsapp.R
import com.example.mynewsapp.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

/**
 * Opening the dividend detail puts the statistic screen on the back stack with its view destroyed;
 * coming back creates a new view. Nothing may keep the old view, and the screen's collectors must
 * not pile up on each round trip.
 */
@RunWith(AndroidJUnit4::class)
class StatisticFragmentLeakTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity

    @Before
    fun launch() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity = instrumentation.startActivitySync(intent) as MainActivity
        instrumentation.waitForIdleSync()
        onMain { navController().navigate(R.id.statisticFragment) }
    }

    @After
    fun finish() {
        onMain { activity.finish() }
    }

    @Test
    fun openingDividendDetail_releasesTheStatisticView() {
        val viewRef = statisticViewRef()

        openDividendDetail()
        awaitCollected(viewRef)

        assertNull("The statistic screen's destroyed view is still reachable", viewRef.get())
    }

    @Test
    fun roundTripsToDividendDetail_doNotAddCollectors() {
        val viewModel = activity.statisticViewModel
        assertEquals("pieDataFlow collectors before the round trips", 1, pieDataCollectors(viewModel))

        repeat(3) {
            openDividendDetail()
            onMain { navController().popBackStack() }
            instrumentation.waitForIdleSync()
        }

        assertEquals("pieDataFlow collectors after 3 round trips", 1, pieDataCollectors(viewModel))
    }

    private fun openDividendDetail() {
        onMain {
            navController().navigate(
                StatisticFragmentDirections.actionStatisticFragmentToDividendDetailFragment(stockNo = "2330")
            )
        }
        instrumentation.waitForIdleSync()
        assertEquals(R.id.dividendDetailFragment, onMain { navController().currentDestination?.id })
    }

    /** In its own frame so no dead local on the test thread keeps the view reachable. */
    private fun statisticViewRef(): WeakReference<View> = onMain { WeakReference(statisticFragment().requireView()) }

    private fun pieDataCollectors(viewModel: StatisticViewModel): Int = onMain {
        // _pieDataFlow is private; its subscriber count is the number of live chart collectors.
        val field = StatisticViewModel::class.java.getDeclaredField("_pieDataFlow").apply { isAccessible = true }
        (field.get(viewModel) as MutableStateFlow<*>).subscriptionCount.value
    }

    private fun awaitCollected(ref: WeakReference<View>) {
        var tries = 0
        while (ref.get() != null && tries++ < 20) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Thread.sleep(100)
        }
    }

    private fun navController(): NavController =
        (activity.supportFragmentManager.findFragmentById(R.id.StockNavHostFragment) as NavHostFragment).navController

    private fun statisticFragment(): Fragment =
        (activity.supportFragmentManager.findFragmentById(R.id.StockNavHostFragment) as NavHostFragment)
            .childFragmentManager.fragments.first { it is StatisticFragment }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        instrumentation.waitForIdleSync()
        return result!!.getOrThrow()
    }
}
