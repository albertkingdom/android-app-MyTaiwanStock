package com.example.mynewsapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.databinding.ItemFollowingListBinding
import com.example.mynewsapp.db.FollowingList

class FollowingListAdapter: ListAdapter<FollowingList, FollowingListAdapter.FollowingListViewHolder>(
    DIFF_CALLBACK
) {
    class FollowingListViewHolder(view: View): RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowingListViewHolder {
        return FollowingListViewHolder(
            ItemFollowingListBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
        )
    }

    override fun onBindViewHolder(holder: FollowingListViewHolder, position: Int) {
        val currentFollowingList = getItem(position)
        ItemFollowingListBinding.bind(holder.itemView).apply {
            listName.text = currentFollowingList.listName
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FollowingList>() {
            override fun areItemsTheSame(oldItem: FollowingList, newItem: FollowingList): Boolean {
                return oldItem.followingListId == newItem.followingListId
            }

            override fun areContentsTheSame(
                oldItem: FollowingList,
                newItem: FollowingList
            ): Boolean {
                return oldItem == newItem
            }

        }
    }


}