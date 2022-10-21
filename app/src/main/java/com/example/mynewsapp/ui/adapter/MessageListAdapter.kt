package com.example.mynewsapp.ui.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mynewsapp.R
import com.example.mynewsapp.model.Message
import com.example.mynewsapp.model.User
import timber.log.Timber
import java.time.format.DateTimeFormatter

class MessageListAdapter(): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val VIEW_TYPE_MESSAGE_SENT = 1
    val VIEW_TYPE_MESSAGE_RECEIVED = 2

    var messageList = listOf<Message>()
    var currentUser: User? = null

    class ReceivedMessageHolder(view: View): RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.text_gchat_message_other)
        val dateText: TextView = view.findViewById(R.id.text_gchat_date_other)
        val timeText: TextView = view.findViewById(R.id.text_gchat_timestamp_other)
        val senderName: TextView = view.findViewById(R.id.text_gchat_user_other)

        fun bind(message: Message) {
            messageText.text = message.messageContent
            val dateFormatter = DateTimeFormatter.ofPattern("MMM dd")
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val dateString = message.createdAt?.format(dateFormatter)
            val timeString = message.createdAt?.format(timeFormatter)
            dateText.text = dateString
            timeText.text = timeString
            senderName.text = message.sender.nickname
            Timber.d("bind ReceivedMessageHolder")

        }
    }
    class SentMessageHolder(view: View): RecyclerView.ViewHolder(view){
        val messageText: TextView = view.findViewById(R.id.text_gchat_message_me)
        val dateText: TextView = view.findViewById(R.id.text_gchat_date_me)
        val timeText: TextView = view.findViewById(R.id.text_gchat_timestamp_me)
        fun bind(message: Message) {
            val dateFormatter = DateTimeFormatter.ofPattern("MMM dd")
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val dateString = message.createdAt?.format(dateFormatter)
            val timeString = message.createdAt?.format(timeFormatter)
            dateText.text = dateString
            timeText.text = timeString
            messageText.text = message.messageContent

            Timber.d("bind SentMessageHolder")
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        return if (message.sender.id == currentUser?.id) {
            VIEW_TYPE_MESSAGE_SENT
        } else {
            VIEW_TYPE_MESSAGE_RECEIVED
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        if (viewType == VIEW_TYPE_MESSAGE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_me, parent, false)
            return SentMessageHolder(view)
        }

        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_other, parent, false)
        return ReceivedMessageHolder(view)


    }



    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messageList[position]

        when (holder.itemViewType) {
            VIEW_TYPE_MESSAGE_RECEIVED -> {
               (holder as ReceivedMessageHolder).bind(message)
            }
            VIEW_TYPE_MESSAGE_SENT -> {
                (holder as SentMessageHolder).bind(message)
            }
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    fun updateMessageList(list: List<Message>) {
        messageList = list
        notifyDataSetChanged()
    }

}