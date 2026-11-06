package com.example.mynewsapp.ui.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.db.FollowingList
import com.example.mynewsapp.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditFollowingListDeleteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // Unique per run: the test uses the app's database.
    private val runId = System.currentTimeMillis()
    private val deletedName = "SwipeDelete$runId"
    private val keptName = "SwipeKeep$runId"

    /** Removes the test lists directly in the database, so cleanup is not cancelled with the activity. */
    @After
    fun removeTestLists() = runBlocking {
        val repository = (ApplicationProvider.getApplicationContext<MyApplication>()).repository
        repository.allFollowingList.first()
            .filter { it.listName == deletedName || it.listName == keptName }
            .forEach { repository.deleteFollowingList(it.followingListId) }
    }

    /**
     * Swiping a list away on the edit lists screen deletes it and shows a confirmation, without
     * crashing, and the list after it stays on screen.
     */
    @Test
    fun swipingAListAwayDeletesItAndKeepsTheNextOne() {
        composeRule.runOnUiThread {
            val viewModel = composeRule.activity.listViewModel
            viewModel.createFollowingList(FollowingList(followingListId = 0, listName = deletedName))
            viewModel.createFollowingList(FollowingList(followingListId = 0, listName = keptName))
            val navHost = composeRule.activity.supportFragmentManager
                .findFragmentById(R.id.StockNavHostFragment) as NavHostFragment
            navHost.navController.navigate(R.id.editFollowingListFragment)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(keptName)).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(deletedName).performTouchInput { swipeLeft() }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.activity.listViewModel.allFollowingList.value.orEmpty().none { it.listName == deletedName }
        }
        // The Snackbar is a View that animates in; Compose idling does not wait for it.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { onView(withText("追蹤清單${deletedName}已刪除")).check(matches(isDisplayed())) }.isSuccess
        }
        composeRule.onNodeWithText(keptName).assertIsDisplayed()
        assertFalse(composeRule.activity.isFinishing)
    }
}
