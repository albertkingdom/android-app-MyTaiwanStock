package com.example.mynewsapp.ui.list


import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.adapter.FollowingListAdapter
import com.example.mynewsapp.databinding.FragmentEditFollowingListBinding
import com.example.mynewsapp.ui.list.AddFollowingListDialogFragment
import com.example.mynewsapp.ui.list.ListViewModel
import com.google.android.material.snackbar.Snackbar

class EditFollowingListFragment: Fragment(R.layout.fragment_edit_following_list) {

    private val listViewModel: ListViewModel by activityViewModels()
    lateinit var binding: FragmentEditFollowingListBinding
    val adapter: FollowingListAdapter = FollowingListAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //change toolbar title
        (requireActivity() as AppCompatActivity).supportActionBar?.title = "Edit Following List"

        binding = FragmentEditFollowingListBinding.bind(view)

        setupListAdapter()

        setupOnClickAddListButton()

        swipeToDelete()
    }
    private fun swipeToDelete() {

        ItemTouchHelper(object: ItemTouchHelper.SimpleCallback(0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT){
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

                val currentFollowingListItem = adapter.currentList[viewHolder.adapterPosition]

                listViewModel.deleteFollowingList(currentFollowingListItem.followingListId)

                Snackbar.make(
                    binding.root,
                    "追蹤清單${currentFollowingListItem.listName}已刪除",
                    Snackbar.LENGTH_LONG
                ).show()

                listViewModel.deleteListFromOnlineDB(currentFollowingListItem.listName)
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // the 1st item can not be deleted
                if (viewHolder.layoutPosition == 0) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
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
                val swipeBackground = ColorDrawable(resources.getColor(R.color.red, null))
                val deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.delete_icon)!!

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

        }).attachToRecyclerView(binding.followingListRecyclerview)
    }

    private fun setupListAdapter() {
        binding.followingListRecyclerview.adapter = adapter
        listViewModel.allFollowingList.observe(viewLifecycleOwner) { list ->
            println("allFollowingList..$list")
            adapter.submitList(list)
        }
    }
    private fun setupOnClickAddListButton() {
        binding.floatingBtn.setOnClickListener { view ->
            // open dialog
            val dialog = AddFollowingListDialogFragment()
            dialog.show(parentFragmentManager,"a")

        }
    }
}