package com.example.mynewsapp.ui.chat

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.mynewsapp.model.Message
import com.example.mynewsapp.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap

class ChatViewModel: ViewModel() {

    val db = Firebase.firestore
    val auth: FirebaseAuth = Firebase.auth
    var currentLoginUser: MutableLiveData<FirebaseUser> = MutableLiveData()

    private var channelID: String? = null

    var messageListLiveData: MutableLiveData<MutableList<Message>> = MutableLiveData()




    fun checkIsExistingChannel(channelName: String) {
        var channelReference: CollectionReference = db.collection("channels")

        var isExisting = false


        channelReference.whereEqualTo("name", channelName).get()
            .addOnSuccessListener { snapshot ->

                for (document in snapshot) {
                    Timber.d("${document.id} => ${document.data}")

                }


                if (snapshot.size() > 0) {
                    isExisting = true
                    channelID = snapshot.documents[0].id
                    Timber.d("checkIsExistingChannel channel id...$channelID")
                    getMessages()
                    return@addOnSuccessListener
                } else {
                    createChannel(channelName)
                }
            }
            .addOnFailureListener { exception ->
                Timber.w("Error getting documents: ", exception)
            }
    }
    private fun createChannel(channelName: String) {
        println("createChannel")
        var channelReference: CollectionReference =
            db.collection("channels")

        val data = hashMapOf(
            "name" to channelName
        )
        channelReference.add(data)
            .addOnSuccessListener { documentRef ->
                Timber.d(documentRef.id)
                channelID = documentRef.id
                getMessages()
            }
            .addOnFailureListener { e ->
                Timber.w("create channel error:", e)
            }


    }
    private fun getMessages() {
        Timber.d("getMessages")
        val reference = db.collection("channels/$channelID/thread")
        val messageList = mutableListOf<Message>()
        Timber.d("channel id...$channelID")
        reference.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Timber.w("Listen failed.", e)
                return@addSnapshotListener
            }
            snapshot?.documentChanges?.forEach {
                val message = handleDocumentChange(it)
                messageList.add(message)
                Timber.d("message...$message")
//                messageListLiveDate.value?.add(message)
            }
            messageList.sortBy { message ->  message.createdAt}
            Timber.d("messageList... $messageList")

            messageListLiveData.value = messageList



        }

    }

    private fun handleDocumentChange(change: DocumentChange): Message {
        lateinit var message: Message
        when(change.type) {
            DocumentChange.Type.ADDED -> {
                val data = change.document.data
                val sender = User(id = data["senderId"] as String, nickname = data["senderName"] as String)
                val content = data["content"] as String
                val sentDate = data["created"] as Timestamp //Timestamp is an object

                message = Message(sender = sender, messageContent = content, createdAt = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(sentDate.seconds),TimeZone.getDefault().toZoneId()))

            }
            else -> {

            }
        }
        return message
    }
    private fun signIn() {
        auth.signInAnonymously().addOnCompleteListener() { authResult ->
            if (authResult.isSuccessful) {
                currentLoginUser.value = auth.currentUser
                Timber.d("sign in successfully")
            } else {
                Timber.w("signInAnonymously:failure", authResult.exception)
            }
        }
    }

    fun checkIsSignIn() {
        if (auth.currentUser != null) {
            currentLoginUser.value = auth.currentUser
        } else {
            signIn()
        }

    }

    fun sendMessage(message: HashMap<String, Any?>) {
        val reference = db.collection("channels/$channelID/thread")
        reference.add(message)
            .addOnSuccessListener { documentRef ->
                Timber.d("successfully add new document: ${documentRef.id}")
            }
            .addOnFailureListener { e ->
                Timber.w("Error adding document", e)
            }
    }
}