package com.example.mynewsapp.ui.list

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.adapter.ClickOnStarListener
import com.example.mynewsapp.adapter.StockIdAndNameAdapter
import com.example.mynewsapp.databinding.FragmentAddStockToListBinding

class AddStockFragment: Fragment() {

    companion object {
        val TAG = "AddStockFragment"
    }
    private val addStockViewModel: AddStockViewModel by viewModels()
    private val listViewModel: ListViewModel by activityViewModels()
    lateinit var binding: FragmentAddStockToListBinding
    lateinit var adapter: StockIdAndNameAdapter
    lateinit var recyclerView: RecyclerView
    lateinit var searchView: SearchView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        //change toolbar title
        (requireActivity() as AppCompatActivity).supportActionBar?.title = "Add Stock ID"

        adapter = StockIdAndNameAdapter()
        binding = FragmentAddStockToListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val stockNames: Array<String> = resources.getStringArray(R.array.countries_array)
        addStockViewModel.stockNames = stockNames.toMutableList()

        recyclerView = binding.stockIdNameRecyclerview
        recyclerView.adapter = adapter

        adapter.onClickStarListener = object : ClickOnStarListener {
            override fun singleClick(stockId: String) {
                listViewModel.addToStockList(stockId)
                listViewModel.uploadNewStockNoToOnlineDB(stockNo = stockId)
                //addStockViewModel.filterSearchQuery()
            }

        }
        addStockViewModel.filteredStockNames.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }

        listViewModel.stockIdsInCurrentList.observe(viewLifecycleOwner) { listOfStockIds ->
            // stock ids in current following list
            addStockViewModel.followingStockIds = listOfStockIds
        }

    }
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.add_stock_options_menu, menu)
        searchView = menu.findItem(R.id.app_bar_search).actionView as SearchView

        searchView.apply {
            maxWidth = Int.MAX_VALUE
            isIconified = false
            queryHint = "請輸入代號"
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(p0: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(text: String?): Boolean {
                    addStockViewModel.updateSearchQuery(text?:"")
                    addStockViewModel.filterSearchQuery()
                    return true
                }
            })
            // clear query when click clear button
            val clearButton = findViewById<ImageView>(R.id.search_close_btn)
            clearButton.setOnClickListener {
                searchView.setQuery("",false)
            }

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchView.clearFocus()
    }
}