package com.example.mynewsapp.ui.list


import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color


import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.ui.adapter.FollowingListAdapter
import com.example.mynewsapp.databinding.FragmentEditFollowingListBinding
import com.example.mynewsapp.db.FollowingList
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
        //(requireActivity() as AppCompatActivity).supportActionBar?.title = "Edit Following List"

        binding = FragmentEditFollowingListBinding.bind(view)
        view.findViewById<ComposeView>(R.id.compose_view).setContent {
            MyList(viewModel = listViewModel)
        }
//        setupListAdapter()

//        setupOnClickAddListButton()

//        swipeToDelete()
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

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun MyList(viewModel: ListViewModel) {

        val items by viewModel.allFollowingList.observeAsState(initial = emptyList())
        val showDialog = remember { mutableStateOf(false) }
        val clickedItemId = remember { mutableIntStateOf(-1) }
        val onClickListName = { item: FollowingList ->
            println("click name = ${item.listName} id = ${item.followingListId}")
            showDialog.value = true
            clickedItemId.intValue = item.followingListId
        }
        val textState = remember { mutableStateOf(TextFieldValue()) }

        fun onConfirmation() {
            println("click confirm button ${clickedItemId.intValue}")
            viewModel.updateFollowingListName(textState.value.text, followingListId = clickedItemId.intValue)
            showDialog.value = false
        }
        if (showDialog.value) {
            Dialog(onDismissRequest = { showDialog.value = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    val keyboardController = LocalSoftwareKeyboardController.current

                    Text("請重新命名追蹤清單",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 20.sp
                    )
                    OutlinedTextField(
                        value = textState.value,
                        onValueChange = { textState.value = it },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                            }
                        ),
                        modifier = Modifier.padding(12.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { showDialog.value = false },
                            modifier = Modifier.padding(8.dp),
                        ) {
                            Text("Dismiss")
                        }
                        TextButton(
                            onClick = { onConfirmation() },
                            modifier = Modifier.padding(8.dp),
                        ) {
                            Text("Confirm")
                        }
                    }

                }
            }
        }

        LazyColumn {
            items(items) { item ->
                MyItemRow(item = item, onClick=onClickListName)
            }
        }
    }




    @Composable
    fun MyItemRow(item: FollowingList, onClick: (FollowingList)-> Unit) {
        // ... 渲染单个列表项的代码
//        Text(item.listName)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)


        ) {
            // ... 其他代码
            Text(text = item.listName,
                fontSize = 24.sp,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )
            Button(
                onClick = { onClick(item) },
                colors = ButtonDefaults.buttonColors(Color.Transparent)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_edit),  // Assume ic_edit is your pen icon
                    contentDescription = null,  // Decorative icon: null content description
                    modifier = Modifier.size(24.dp),
                    tint = Color.DarkGray
                )
            }

        }
    }


    @Preview
    @Composable
    fun ComposablePreview() {
        val item = FollowingList(followingListId = 0, listName = "default")
        MyItemRow(item, onClick = { println("click ${item.listName}")})
    }
}