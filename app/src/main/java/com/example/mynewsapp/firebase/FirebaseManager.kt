package com.example.mynewsapp.firebase

import com.example.mynewsapp.model.Message
import com.example.mynewsapp.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object FirebaseManager {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    suspend fun checkIsExistingChannel(channelName: String): Map<String, Any> = suspendCoroutine { continuation ->
        var isExisting = false
        var channelID = ""
        val channelReference: CollectionReference = db.collection("channels")
        channelReference.whereEqualTo("name", channelName).get()
            .addOnSuccessListener { snapshot ->

                for (document in snapshot) {
                    Timber.d("${document.id} => ${document.data}")

                }


                if (snapshot.size() > 0) {
                    isExisting = true
                    channelID = snapshot.documents[0].id
                    continuation.resume(mapOf("isExisting" to isExisting, "channelID" to channelID))

                } else {
                    isExisting = false

//                    createChannel(channelName)
                }
//                subscribeToTopic(channelName = channelName)
            }
            .addOnFailureListener { exception ->
                Timber.w("Error getting documents: ", exception)
                continuation.resumeWithException(exception)
            }
    }
    suspend fun createChannel(channelName: String): String = suspendCoroutine{ continuation ->
        println("createChannel")
        val channelReference: CollectionReference =
            db.collection("channels")
        val data = hashMapOf(
            "name" to channelName
        )
        channelReference.add(data)
            .addOnSuccessListener { documentRef ->
                Timber.d(documentRef.id)
                val channelID = documentRef.id
                continuation.resume(channelID)
            }
            .addOnFailureListener { e ->
                Timber.w("create channel error:", e)
                continuation.resumeWithException(e)
            }
    }

    fun getMessages(channelID: String): MutableList<Message> {
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
            }
            messageList.sortBy { message ->  message.createdAt}
        }
        return messageList
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
                    Instant.ofEpochSecond(sentDate.seconds), TimeZone.getDefault().toZoneId()))

            }
            else -> {

            }
        }
        return message
    }

    suspend fun sendMessage(message: HashMap<String, Any?>, channelID: String) = suspendCoroutine { continuation ->
        val reference = db.collection("channels/$channelID/thread")
        reference.add(message)
            .addOnSuccessListener { documentRef ->
                Timber.d("successfully add new document: ${documentRef.id}")
                continuation.resume(documentRef.id)
            }
            .addOnFailureListener { e ->
                Timber.w("Error adding document", e)
                continuation.resumeWithException(e)
            }
    }
    fun subscribeToTopic(channelName: String) {
        Firebase.messaging.subscribeToTopic("channel_$channelName")
            .addOnCompleteListener { task ->
                var msg = "Subscribed"
                if (!task.isSuccessful) {
                    msg = "Subscribe failed"
                }
                Timber.d(msg)
            }
    }
    fun unsubscribeToTopic(channelName: String) {
        Firebase.messaging.unsubscribeFromTopic("channel_$channelName")
            .addOnCompleteListener { task ->
                var msg = "Unsubscribed"
                if (!task.isSuccessful) {
                    msg = "Unsubscribe failed"
                }
                Timber.d(msg)
            }
    }
    suspend fun signIn() = suspendCoroutine {  continuation ->
        auth.signInAnonymously().addOnCompleteListener { authResult ->
            if (authResult.isSuccessful) {
                continuation.resume(auth.currentUser)
                Timber.d("sign in successfully")
            } else {
                Timber.w("signInAnonymously:failure", authResult.exception)
                continuation.resumeWithException(authResult.exception!!)
            }
        }
    }
    suspend fun fireBaseAuthWithGoogle(idToken: String)  = suspendCoroutine { continuation ->
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Timber.d("fireBaseAuthWithGoogle success $user")
                    if (user != null) {
                        continuation.resume(user)
                    }
                } else {
                    Timber.w("fireBaseAuthWithGoogle failure ${task.exception}")
                }
            }
        }
    fun checkIfLogin(): FirebaseUser? {
        return auth.currentUser
    }

    fun signOut() {
        auth.signOut()
    }

}