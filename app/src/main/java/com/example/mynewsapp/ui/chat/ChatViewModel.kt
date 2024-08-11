package com.example.mynewsapp.ui.chat

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mynewsapp.firebase.FirebaseManager
import com.example.mynewsapp.model.Message
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.collections.HashMap

class ChatViewModel: ViewModel() {

    var currentLoginUser: MutableLiveData<FirebaseUser> = MutableLiveData()

    private var currentChannelID: String? = null

    var messageListLiveData: MutableLiveData<MutableList<Message>> = MutableLiveData()




    fun checkIsExistingChannel(channelName: String) {
        Timber.d("checkIsExistingChannel $channelName")
        viewModelScope.launch(Dispatchers.IO) {
            val result = FirebaseManager.checkIsExistingChannel(channelName=channelName)
            val isExisting = result["isExisting"] as Boolean
            var channelID = result["channelID"] as String
            if (!isExisting) {
                channelID = FirebaseManager.createChannel(channelName = channelName)
            }
            currentChannelID = channelID
            getMessages(channelID = channelID)

            FirebaseManager.subscribeToTopic(channelName = channelName)
        }
    }


    private fun getMessages(channelID: String) {
        Timber.d("getMessages")
        val messages= FirebaseManager.getMessages(channelID = channelID)
        messageListLiveData.postValue(messages)
    }



    fun checkIsSignIn() {
        if (FirebaseManager.auth.currentUser == null) {
            viewModelScope.launch(Dispatchers.Main) {
                currentLoginUser.value = FirebaseManager.signIn()
            }
        } else {
            currentLoginUser.value = FirebaseManager.auth.currentUser
        }
    }

    fun sendMessage(message: HashMap<String, Any?>) {
        viewModelScope.launch {
            FirebaseManager.sendMessage(message = message, channelID = currentChannelID!!)
        }
    }
}