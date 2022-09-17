package com.example.mynewsapp.ui.list

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.MainActivity
import com.example.mynewsapp.R
import com.example.mynewsapp.adapter.StockInfoAdapter
import com.example.mynewsapp.databinding.FragmentListBinding
import com.example.mynewsapp.model.MsgArray
import com.example.mynewsapp.model.WidgetStockData
import com.example.mynewsapp.util.Constant.Companion.NO_INTERNET_CONNECTION
import com.example.mynewsapp.util.Resource
import com.example.mynewsapp.widget.WidgetUtil.Companion.updateWidget
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber


class ListFragment : Fragment() {
    private lateinit var binding: FragmentListBinding
    private val listViewModel: ListViewModel by activityViewModels()

    private lateinit var stockAdapter: StockInfoAdapter
    private lateinit var swipeBackground: ColorDrawable
    private lateinit var deleteIcon: Drawable

    val TAG = "ListFragment"
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentListBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)

        checkIfFirstTimeAfterLoginAndGetOnlineData()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView: RecyclerView = binding.stockListRecyclerview
        //change toolbar title
//        (requireActivity() as AppCompatActivity).supportActionBar?.title = "自選股"


        stockAdapter = StockInfoAdapter(getStockNameToGetRelatedNews, navigateToCandelStickChartFragment)
        recyclerView.adapter = stockAdapter
        changeToLastViewedList()
        listViewModel.stockPriceInfo.observe(viewLifecycleOwner, Observer { response ->
            Timber.d("Observe TestStockPrice $response")

            when (response) {
                is Resource.Success -> {
                    response.data?.let { stockInfoResponse ->
                        Timber.d(stockInfoResponse.toString())

                        val listOfMsgArray = stockInfoResponse.msgArray
                        stockAdapter.setData(listOfMsgArray)

                        val listOfWidgetStockData = listOfMsgArray.map { msgArray ->
                            WidgetStockData(stockNo = msgArray.stockNo, stockPrice = msgArray.currentPrice, stockName = msgArray.stockName, yesterDayPrice = msgArray.lastDayPrice)
                        }

                        updateWidget(listOfWidgetStockData, requireContext())

                    }
                    binding.swipeRefresh.isRefreshing = false
                    toggleNetworkConnectionLostIcon(false)
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
                }
                is Resource.Loading -> {
                    binding.swipeRefresh.isRefreshing = true
                }
            }

        })


        // observe selected list id and get
        listViewModel.currentSelectedFollowingListId.observe(viewLifecycleOwner) { listId ->
            Timber.d("currentSelectedFollowingListId $listId")

        }


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

        setupOnClickFab()

        setupSwipeRefresh()


    }

    override fun onStart() {
        super.onStart()
        (activity as MainActivity).showMenuSelectorBtn()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            listViewModel.changeCurrentFollowingListId()
        }
    }

    private fun setupOnClickFab() {
        binding.floatingBtn.setOnClickListener {
            findNavController().navigate(ListFragmentDirections.actionStockListFragmentToAddStockFragment())

            it.visibility = View.INVISIBLE
        }
    }
    private val getStockNameToGetRelatedNews:(stockContent: MsgArray)->Unit = { stockContent->

//        val stockName = stockContent.stockName
//        viewModel.getRelatedNews(stockName)
//        findNavController().navigate(ListFragmentDirections.actionListFragmentToNewsFragment())
    }

    private val navigateToCandelStickChartFragment:(stockContent: MsgArray) -> Unit = {

        val stockNo = it.stockNo
        val stockPrice:String = if(it.currentPrice != "-") it.currentPrice else it.lastDayPrice
        val stockName = it.stockName

        findNavController().navigate(ListFragmentDirections.actionListFragmentToCandleStickChartFragment(stockNo,stockName,stockPrice))

    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.options_menu, menu)
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

    private fun changeToLastViewedList() {
        val index = requireContext().getSharedPreferences("sharedPref", AppCompatActivity.MODE_PRIVATE).getInt("currentList", 0)
        Timber.d("last viewed list: $index")
        listViewModel.changeLastViewedListIndex(index = index)
    }
    override fun onStop() {
        super.onStop()
        Timber.d("onstop")

        (activity as MainActivity).hideMenuSelectorBtn()
    }
}