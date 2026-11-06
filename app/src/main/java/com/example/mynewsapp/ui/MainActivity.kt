package com.example.mynewsapp.ui


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.navigation.navOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.*
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.ActivityMainBinding
import com.example.mynewsapp.stocklist.android.StockListRefreshWorker
import com.example.mynewsapp.ui.list.ListViewModel
import com.example.mynewsapp.ui.list.ListViewModelFactory
import com.example.mynewsapp.ui.statistics.StatisticViewModel
import com.example.mynewsapp.ui.statistics.StatisticViewModelFactory
import com.example.mynewsapp.ui.tradeimport.TradeImportFragmentArgs
import com.example.mynewsapp.ui.tradeimport.core.ShareIntentImages
import com.google.android.material.navigation.NavigationBarView
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    lateinit var listViewModel: ListViewModel
    lateinit var statisticViewModel: StatisticViewModel
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    lateinit var navView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.my_toolbar))

        navView = binding.navView


        val listViewModelFactory = ListViewModelFactory((application as MyApplication).repository, application as MyApplication)
        listViewModel = ViewModelProvider(this, listViewModelFactory)
            .get(ListViewModel::class.java)

        val statisticViewModelFactory = StatisticViewModelFactory((application as MyApplication).repository)
        statisticViewModel = ViewModelProvider(this, statisticViewModelFactory).get(StatisticViewModel::class.java)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.StockNavHostFragment) as NavHostFragment
        navController = navHostFragment.navController
        navView.setupWithNavController(navController)


        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.stockListFragment,
            R.id.news,
            R.id.statisticFragment,
            R.id.settings,
            //R.id.account_page
        ))
        setupActionBarWithNavController(navController, appBarConfiguration)

        setupOnClickBottomNav()

        observeFollowingLists()

        checkIntentExtra()

        if (savedInstanceState == null) handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Opens the trade screenshot import for images shared from the system share sheet. */
    private fun handleShareIntent(intent: Intent?) {
        intent ?: return
        // Reopening the task from Recents replays its original intent; that share was handled already.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val action = intent.action
        val clipItems = intent.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } }.orEmpty()
        val uris = ShareIntentImages.select(
            action = action,
            mimeType = intent.type,
            single = if (action == Intent.ACTION_SEND) IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) else null,
            multiple = if (action == Intent.ACTION_SEND_MULTIPLE) {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                null
            },
            clipItems = clipItems,
        )
        if (uris.isEmpty()) return
        val allowed = uris.filter { ShareIntentImages.isAllowedSource(it.scheme, it.authority, packageName) }
        if (allowed.size < uris.size) {
            // For example a file: URI from an older app; say why some or all images are missing.
            Toast.makeText(this, R.string.trade_import_share_refused, Toast.LENGTH_LONG).show()
        }
        // Handled once: a recreated activity does not open the import again.
        setIntent(Intent())
        if (allowed.isEmpty()) return

        // Switch tabs first: an import screen kept in the stock tab's saved back stack is then visible here.
        if (navView.selectedItemId != R.id.stock) navView.selectedItemId = R.id.stock
        val importOpen = try {
            navController.getBackStackEntry(R.id.tradeImportFragment)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
        // Replacing an open import is safe: a confirmed import keeps writing in the app scope and
        // reports its result itself; an unconfirmed one writes nothing.
        val args = TradeImportFragmentArgs(allowed.map(Uri::toString).toTypedArray()).toBundle()
        val options = if (importOpen) navOptions { popUpTo(R.id.tradeImportFragment) { inclusive = true } } else null
        navController.navigate(R.id.action_global_tradeImportFragment, args, options)
    }

    override fun onStart() {
        super.onStart()
        // The only place the stock list refresh check is triggered; the worker decides whether
        // the list is stale, so the staleness check reads no file or preference on the main thread.
        StockListRefreshWorker.enqueue(applicationContext)
    }

    fun checkIntentExtra() {
        val stockNumberSelectedOnWidget = intent.getStringExtra("stockNo")
    }

    override fun onSupportNavigateUp(): Boolean {

        return navController.navigateUp(appBarConfiguration)
    }

    private fun setupOnClickBottomNav() {
        // determine whether to reload tabs based on current selected bottom nav menu items
        navView.setOnItemSelectedListener(object : NavigationBarView.OnItemSelectedListener {
            override fun onNavigationItemSelected(item: MenuItem): Boolean {
                Timber.d("onNavigationItemSelected")

                // newly selected menu is the current one -> don't reload
                if (item.itemId == navView.selectedItemId) {
                    return false
                }
                // newly selected menu is not the current one -> load new tab
                NavigationUI.onNavDestinationSelected(item, navController)
                return true
            }

        })
        // pop up to default fragment when re-select bottom tab
        /*
        navView.setOnItemReselectedListener(object: NavigationBarView.OnItemReselectedListener {
            override fun onNavigationItemReselected(item: MenuItem) {
                Log.d("Activity", "onNavigationItemReselected")
                navController.popBackStack(item.itemId, inclusive = false)
            }
        })
        */
    }

    /**
     * Keeps the list names observed for their side effects (a Default list for an empty database, and
     * starting the price pipeline on the saved list), also when another screen opens first.
     */
    private fun observeFollowingLists() {
        listViewModel.followingListNames.observe(this) { }
    }
}
