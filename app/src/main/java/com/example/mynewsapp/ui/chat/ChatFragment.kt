package com.example.mynewsapp.ui.chat

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.mynewsapp.ui.adapter.MessageListAdapter
import com.example.mynewsapp.databinding.FragmentChatBinding
import com.example.mynewsapp.model.Message
import com.example.mynewsapp.model.User
import java.util.*
import kotlin.collections.HashMap


class ChatFragment : Fragment() {
    private val args: ChatFragmentArgs by navArgs()
    private lateinit var binding: FragmentChatBinding
    private val chatViewModel: ChatViewModel by viewModels()
    lateinit var adapter: MessageListAdapter
    lateinit var recyclerView: RecyclerView
    lateinit var sendMsgButton: Button
    lateinit var msgInput: EditText
    lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        chatViewModel.checkIsExistingChannel(args.stockNo)

        chatViewModel.checkIsSignIn()

        binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = binding.recyclerviewChat
        sendMsgButton = binding.buttonGchatSend
        msgInput = binding.editGchatMessage
        swipeRefresh = binding.swipeRefresh
        //change toolbar title
        (requireActivity() as AppCompatActivity).supportActionBar?.title = "${args.stockNo} chat room"

        adapter = MessageListAdapter()
        recyclerView.adapter = adapter

        swipeRefresh.isRefreshing = true
        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = false
        }
        chatViewModel.currentLoginUser.observe(viewLifecycleOwner) { firebaseUser ->
            val user = User(firebaseUser.uid, null)
            adapter.currentUser = user

        }


        chatViewModel.messageListLiveData.observe(viewLifecycleOwner) {
            swipeRefresh.isRefreshing = false
            //adapter.messageList = it
            //adapter.notifyDataSetChanged()
            adapter.updateMessageList(it)
            recyclerView.scrollToPosition(adapter.messageList.lastIndex)
        }

        sendMsgButton.setOnClickListener {
            val messageContent = msgInput.text.toString()
            val newMessage = Message(
                sender = User(id = chatViewModel.currentLoginUser.value!!.uid, nickname = "anonymous"),
                messageContent = messageContent,
                createdAt = null
            )

            val messageRepresentation: HashMap<String, Any?> = hashMapOf( "created" to Date(),
                "senderId" to newMessage.sender.id,
                "senderName" to newMessage.sender.nickname,
                "content" to newMessage.messageContent
            )
            chatViewModel.sendMessage(messageRepresentation)
            msgInput.text.clear()
        }
    }

}