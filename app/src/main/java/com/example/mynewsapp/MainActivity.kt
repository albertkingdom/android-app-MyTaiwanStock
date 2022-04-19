package com.example.mynewsapp

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.annotation.MenuRes
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ListPopupWindow
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController

import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.mynewsapp.databinding.ActivityMainBinding
import com.example.mynewsapp.db.FollowingList
import com.example.mynewsapp.ui.ChatViewModel
import com.example.mynewsapp.ui.ListFragmentDirections
import com.example.mynewsapp.ui.NewsViewModel
import com.example.mynewsapp.ui.NewsViewModelFactory


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var viewModel: NewsViewModel
    val chatViewModel: ChatViewModel by viewModels()
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.my_toolbar))
        val navView: BottomNavigationView = binding.navView

       // Initialize view model ==============
        val viewModelFactory = NewsViewModelFactory((application as MyApplication).repository,
            application as MyApplication
        )
        viewModel = ViewModelProvider(this, viewModelFactory)
            .get(NewsViewModel::class.java)
        //================
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.StockNavHostFragment) as NavHostFragment
        val navController = navHostFragment.navController
        navView.setupWithNavController(navController)

        appBarConfiguration = AppBarConfiguration(setOf(R.id.listFragment, R.id.newsFragment, R.id.statisticFragment))

        setupActionBarWithNavController(navController, appBarConfiguration)


    }
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.StockNavHostFragment)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    fun showMenuSelectorBtn(menuItems: List<FollowingList>) {
        binding.showListMenuButton.visibility = View.VISIBLE
        setupMenuSelectorBtn(menuItems)
    }
    fun hideMenuSelectorBtn() {
        binding.showListMenuButton.visibility = View.INVISIBLE
    }
    private fun setupMenuSelectorBtn(menuItems: List<FollowingList>) {
        val menuSelectorButton = binding.showListMenuButton
        val listPopupWindow = ListPopupWindow(this, null, R.attr.listPopupWindowStyle)


        // Set button as the list popup's anchor
        listPopupWindow.anchorView = menuSelectorButton


        // Set list popup's content
        val listNameArray = menuItems.map { list -> list.listName }
        val listNameArrayAndEdit = mutableListOf<String>()
        listNameArrayAndEdit.addAll(listNameArray)
        listNameArrayAndEdit.add("Edit...")
        val adapter = ArrayAdapter(this, R.layout.menu_list_selector_item, listNameArrayAndEdit)
        listPopupWindow.setAdapter(adapter)
        // Set list popup's item click listener
        listPopupWindow.setOnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
            // Respond to list popup window item click.
            if (position == listNameArrayAndEdit.lastIndex) {
                // go to edit following list page
                println("click editing btn of following list")
                findNavController(R.id.StockNavHostFragment).navigate(ListFragmentDirections.actionListFragmentToEditFollowingListFragment())
                // Dismiss popup.
                listPopupWindow.dismiss()
                return@setOnItemClickListener
            }
            println("click on list menu $position name = ${listNameArray[position]}")
            viewModel.currentSelectedFollowingListId.postValue(menuItems[position].followingListId)
            //menuSelectorButton.text = menuItems[position].listName
            // Dismiss popup.
            listPopupWindow.dismiss()
        }

        // Show list popup window on button click.
        menuSelectorButton.setOnClickListener { v: View? -> listPopupWindow.show() }

        viewModel.currentSelectedFollowingListId.observe(this, { listId ->
            menuSelectorButton.text =
                menuItems.find { list -> list.followingListId.equals(listId) }?.listName
        })
    }

}