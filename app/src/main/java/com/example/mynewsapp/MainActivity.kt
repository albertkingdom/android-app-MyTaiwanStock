package com.example.mynewsapp

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ListPopupWindow
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController

import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.*
import com.example.mynewsapp.databinding.ActivityMainBinding
import com.example.mynewsapp.ui.*
import com.example.mynewsapp.ui.list.ListFragmentDirections
import com.example.mynewsapp.ui.list.ListViewModel
import com.example.mynewsapp.ui.list.ListViewModelFactory
import com.google.android.material.navigation.NavigationBarView


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    lateinit var listViewModel: ListViewModel
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

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.StockNavHostFragment) as NavHostFragment
        navController = navHostFragment.navController
        navView.setupWithNavController(navController)


        appBarConfiguration = AppBarConfiguration(setOf(R.id.stockListFragment, R.id.news, R.id.statisticFragment, R.id.settings))
        setupActionBarWithNavController(navController, appBarConfiguration)

        setupOnClickBottomNav()

        setupAppBarMenu()

    }
    override fun onSupportNavigateUp(): Boolean {

        return navController.navigateUp(appBarConfiguration)
    }

    private fun setupOnClickBottomNav() {
        // determine whether to reload tabs based on current selected bottom nav menu items
        navView.setOnItemSelectedListener(object : NavigationBarView.OnItemSelectedListener {
            override fun onNavigationItemSelected(item: MenuItem): Boolean {
                Log.d("Activity", "onNavigationItemSelected")

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
        showMenuSelectorBtn()
    }


    fun showMenuSelectorBtn() {
        binding.showListMenuButton.visibility = View.VISIBLE
        setupAppBarMenu()
    }
    fun hideMenuSelectorBtn() {
        binding.showListMenuButton.visibility = View.GONE
    }
    private fun setupAppBarMenu() {
        val menuSelectorButton = binding.showListMenuButton
        val listPopupWindow = ListPopupWindow(this, null, R.attr.listPopupWindowStyle)

        // Set button as the list popup's anchor
        listPopupWindow.anchorView = menuSelectorButton



        listViewModel.appBarMenuItemNameList.observe(this) { list ->

            // setup adapter
            val adapter = ArrayAdapter(this, R.layout.menu_list_selector_item, list)
            listPopupWindow.setAdapter(adapter)
            // setup click listener for app bar menu item
            listPopupWindow.setOnItemClickListener { adapterView, view, position, id ->

                // when click edit button
                if (position == list.lastIndex) {
                    println("click editing btn of following list")
                    findNavController(R.id.StockNavHostFragment).navigate(ListFragmentDirections.actionStockListFragmentToEditFollowingListFragment())
                    // Dismiss popup.
                    listPopupWindow.dismiss()

                } else {
                    listViewModel.changeCurrentFollowingList(position)
                    listPopupWindow.dismiss()

                }

            }
        }


        // Show list popup window on button click.
        menuSelectorButton.setOnClickListener { v: View? -> listPopupWindow.show() }

        // title of app bar menu
        listViewModel.appBarMenuButtonTitle.observe(this) { title ->
            menuSelectorButton.text = title
        }
    }



}