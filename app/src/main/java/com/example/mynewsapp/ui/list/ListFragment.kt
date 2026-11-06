package com.example.mynewsapp.ui.list

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.FragmentCandleStickChartBinding
import com.example.mynewsapp.ui.adapter.StockInfoAdapter
import com.example.mynewsapp.databinding.FragmentListBinding
import com.example.mynewsapp.model.MsgArray
import com.example.mynewsapp.model.WidgetStockData
import com.example.mynewsapp.util.Constant.Companion.NO_INTERNET_CONNECTION
import com.example.mynewsapp.util.Resource
import com.example.mynewsapp.ui.widget.WidgetUtil.Companion.updateWidget
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class ListFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val listViewModel: ListViewModel by activityViewModels()

    private lateinit var stockAdapter: StockInfoAdapter
    private lateinit var swipeBackground: ColorDrawable
    private lateinit var deleteIcon: Drawable
    /** Selects the tapped list; removed while the tabs are changed from code. */
    private val tabSelectedListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) = listViewModel.selectList(tab.position)
        override fun onTabUnselected(tab: TabLayout.Tab) = Unit
        override fun onTabReselected(tab: TabLayout.Tab) = Unit
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)

        checkIfFirstTimeAfterLoginAndGetOnlineData()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView: RecyclerView = binding.stockListRecyclerview


        stockAdapter = StockInfoAdapter(
            navigateToCandleStickChartFragment,
            onToggleChangeMode = listViewModel::toggleShowAsPercent,
        )
        recyclerView.adapter = stockAdapter
        recyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL).apply { isLastItemDecorated = false }
        )
        setupListTabs()
        binding.headerToggle.setOnClickListener { listViewModel.toggleShowAsPercent() }
        listViewModel.showAsPercent.observe(viewLifecycleOwner) { stockAdapter.setMode(it) }
        listViewModel.stockPriceInfo.observe(viewLifecycleOwner, Observer { response ->
            Timber.d("Observe TestStockPrice $response")
            when (response) {
                is Resource.Success -> {
                    response.data?.let { stockInfoResponse ->
                        Timber.d(stockInfoResponse.toString())

                        val listOfMsgArray = stockInfoResponse.msgArray
                        stockAdapter.setData(listOfMsgArray)

                    }
                    binding.swipeRefresh.isRefreshing = false
                    toggleNetworkConnectionLostIcon(false)

                    toggleShimmerLoadingEffect(false)
                }
                is Resource.Error -> {
                    response.message?.let { message ->
                        Timber.e("An error occured: $message")
                        Snackbar.make(view, "An error occured: $message", Snackbar.LENGTH_LONG).show()
                        binding.swipeRefresh.isRefreshing = false
                        when (message) {
                            NO_INTERNET_CONNECTION -> toggleNetworkConnectionLostIcon(true)
                        }
                    }
                   toggleShimmerLoadingEffect(false)
                }
                is Resource.Loading -> {
                    binding.swipeRefresh.isRefreshing = true
                }
            }

        })


        /**
         * swipe to delete a stockNo from db
         */
        ItemTouchHelper(object: ItemTouchHelper.SimpleCallback(0,ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT){
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

                val currentStockItem = stockAdapter.currentList[viewHolder.adapterPosition]

                listViewModel.deleteStockByStockNoAndListId(currentStockItem.stockNo)
                listViewModel.deleteAllHistory(currentStockItem.stockNo)
                Snackbar.make(view, "追蹤股票代號已刪除",Snackbar.LENGTH_LONG).show()

                stockAdapter.notifyItemChanged(viewHolder.layoutPosition)
                listViewModel.deleteStockNoFromOnlineDB(currentStockItem.stockNo)
            }


            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                // draw background and icon when swipe
                swipeBackground = ColorDrawable(resources.getColor(R.color.red, null))
                deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.delete_icon)!!

                val itemView = viewHolder.itemView
                val iconMargin = (itemView.height - deleteIcon.intrinsicHeight) / 2


                if (dX > 0) {
                    // swipe right
                    swipeBackground.setBounds(itemView.left, itemView.top, dX.toInt(), itemView.bottom)
                    deleteIcon.setBounds(itemView.left + iconMargin, itemView.top + iconMargin, itemView.left + iconMargin + deleteIcon.intrinsicWidth, itemView.bottom - iconMargin)

                } else {
                    swipeBackground.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    deleteIcon.setBounds(itemView.right - iconMargin - deleteIcon.intrinsicWidth, itemView.top + iconMargin, itemView.right - iconMargin, itemView.bottom - iconMargin)

                }
                swipeBackground.draw(c)

                c.save()
                if (dX > 0 ) {
                    c.clipRect(itemView.left, itemView.top, dX.toInt(), itemView.bottom)
                } else {
                    c.clipRect(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)

                }
                deleteIcon.draw(c)
                c.restore()
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }

        }).attachToRecyclerView(recyclerView)

        setupAddStockButton()

        setupSwipeRefresh()


    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
        toggleShimmerLoadingEffect(true)
    }

    /**
     * One tab per list, following the ViewModel: the names rebuild or rename the tabs, and the
     * selected position (the list whose stocks are shown) picks the tab. Code changes to the tabs run
     * without the listener, so only a tap selects a list.
     */
    private fun setupListTabs() {
        val tabs = binding.listTabs
        tabs.addOnTabSelectedListener(tabSelectedListener)
        listViewModel.followingListNames.observe(viewLifecycleOwner) { names ->
            tabs.removeOnTabSelectedListener(tabSelectedListener)
            if (tabs.tabCount == names.size) {
                names.forEachIndexed { index, name -> tabs.getTabAt(index)?.text = name }
            } else {
                tabs.removeAllTabs()
                names.forEach { tabs.addTab(tabs.newTab().setText(it), false) }
                listViewModel.selectedListPosition.value?.let { selectTab(it) }
            }
            tabs.addOnTabSelectedListener(tabSelectedListener)
        }
        listViewModel.selectedListPosition.observe(viewLifecycleOwner) { position ->
            tabs.removeOnTabSelectedListener(tabSelectedListener)
            selectTab(position)
            tabs.addOnTabSelectedListener(tabSelectedListener)
        }
        binding.addListButton.setOnClickListener { showAddListDialog() }
    }

    /** Selects the tab and scrolls it into view; skipped while the tabs are not built yet. */
    private fun selectTab(position: Int) {
        val tabs = binding.listTabs
        val tab = tabs.getTabAt(position) ?: return
        tabs.selectTab(tab)
        tabs.post { _binding?.listTabs?.setScrollPosition(position, 0f, true) }
    }

    private fun showAddListDialog() {
        AddFollowingListDialogFragment().show(childFragmentManager, "addFollowingList")
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            listViewModel.changeCurrentFollowingListId()
        }
    }

    private fun setupAddStockButton() {
        binding.addStockButton.setOnClickListener {
            findNavController().navigate(ListFragmentDirections.actionStockListFragmentToAddStockFragment())
        }
    }

    private val navigateToCandleStickChartFragment:(stockContent: MsgArray) -> Unit = {

        val stockNo = it.stockNo
        val stockPrice:String = if(it.currentPrice != "-") it.currentPrice else it.lastDayPrice
        val stockName = it.stockName
        val time = it.time

        findNavController().navigate(ListFragmentDirections.actionListFragmentToCandleStickChartFragment(stockNo,stockName,stockPrice,time))

    }

    private fun toggleShimmerLoadingEffect(show:Boolean) {
        if (!show) {
            // The view's scope: the delayed update must not run after the view is gone.
            viewLifecycleOwner.lifecycleScope.launch {
                delay(500) // show shimmer animation fo 0.5s
                binding.shimmerFrameLayout.stopShimmer()
                binding.shimmerFrameLayout.visibility = View.GONE
                binding.swipeRefresh.visibility = View.VISIBLE
            }
        } else {
            binding.shimmerFrameLayout.startShimmer()
            binding.swipeRefresh.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.list_fragment_options_menu, menu)
        val searchView = menu.findItem(R.id.app_bar_search).actionView as SearchView
        searchView.apply {
            queryHint = "請輸入代號"
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(p0: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(text: String?): Boolean {
                    Timber.d("query text $text")
                    stockAdapter.filter.filter(text)
                    return true
                }

            })

        }


    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_list -> showAddListDialog()
            R.id.menu_rename_list -> showRenameListDialog()
            R.id.menu_delete_list -> showDeleteListDialog()
            R.id.menu_import_screenshot -> {
                // A quick second tap must not open a second import screen.
                if (findNavController().currentDestination?.id == R.id.stockListFragment) {
                    findNavController().navigate(R.id.action_global_tradeImportFragment)
                }
            }
            R.id.menu_manage_lists ->
                findNavController().navigate(ListFragmentDirections.actionStockListFragmentToEditFollowingListFragment())
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** Renames the selected list; a blank name or another list's name keeps the dialog open. */
    private fun showRenameListDialog() {
        val listId = listViewModel.currentSelectedFollowingListId.value ?: return
        val currentName = listViewModel.currentListName() ?: return
        val view = layoutInflater.inflate(R.layout.dialog_add_following_list, null)
        val field = view.findViewById<TextInputLayout>(R.id.outlinedTextField)
        field.editText?.setText(currentName)
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AddFollowingListDialogTheme)
            .setTitle(R.string.watchlist_rename_list)
            .setView(view)
            // The listener is replaced so a rejected name keeps the dialog open.
            .setPositiveButton(R.string.watchlist_save, null)
            .setNegativeButton(R.string.watchlist_cancel, null)
            .create()
        dialog.validateListNameOnConfirm(
            field = field,
            existingNames = { listViewModel.allFollowingList.value.orEmpty().map { it.listName } },
            renamingFrom = currentName,
        ) { name -> listViewModel.updateFollowingListName(name, listId) }
        dialog.show()
    }

    /** Deletes the selected list and its stocks after a confirmation that names the list. */
    private fun showDeleteListDialog() {
        val listId = listViewModel.currentSelectedFollowingListId.value ?: return
        val name = listViewModel.currentListName() ?: return
        MaterialAlertDialogBuilder(requireContext(), R.style.AddFollowingListDialogTheme)
            .setMessage(getString(R.string.watchlist_delete_list_message, name))
            .setPositiveButton(R.string.watchlist_delete) { _, _ -> listViewModel.deleteFollowingList(listId) }
            .setNegativeButton(R.string.watchlist_cancel, null)
            .show()
    }

    private fun toggleNetworkConnectionLostIcon(isNetworkError: Boolean) {
        when (isNetworkError) {
            true -> {
                binding.networkNotAvailable.visibility = View.VISIBLE
                binding.stockListRecyclerview.visibility = View.GONE
            }
            false -> {
                binding.networkNotAvailable.visibility = View.GONE
                binding.stockListRecyclerview.visibility = View.VISIBLE
            }
        }
    }
    private fun checkIfFirstTimeAfterLoginAndGetOnlineData() {
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = sp.edit()
        val firstTimeAfterLogin = sp.getBoolean("firstTimeAfterLogin", false)
        val isLogin = listViewModel.auth.currentUser?.email != null
        if (firstTimeAfterLogin && isLogin) {
            Timber.d("isFirstTimeAfterLogin true")
            // show alert
            val alertBuilder = MaterialAlertDialogBuilder(requireContext(), R.style.AddFollowingListDialogTheme)
            alertBuilder
                .setMessage("您剛才登入，將下載備份在雲端的資料，是否同意？")
                .setPositiveButton("Agree") { _, _ ->
                    // sync from firebase
                    listViewModel.getAllOnlineDBDataAndSaveToLocal()
                }
                .setNegativeButton("Disagree") { _, _ -> }
                .show()

        }
        editor.putBoolean("firstTimeAfterLogin", false)
        editor.apply()
    }

    override fun onDestroyView() {
        // The view lifecycle removes these observers only if it reached CREATED. A view torn down
        // before that (a cold start from the share sheet replaces this screen in onCreate) would
        // otherwise stay registered on the activity-scoped ViewModel and keep this view alive.
        listViewModel.stockPriceInfo.removeObservers(viewLifecycleOwner)
        listViewModel.followingListNames.removeObservers(viewLifecycleOwner)
        listViewModel.selectedListPosition.removeObservers(viewLifecycleOwner)
        listViewModel.showAsPercent.removeObservers(viewLifecycleOwner)
        // The adapter outlives the view in this field; detach it so it does not keep the RecyclerView.
        binding.stockListRecyclerview.adapter = null
        super.onDestroyView()
        _binding = null
    }
}