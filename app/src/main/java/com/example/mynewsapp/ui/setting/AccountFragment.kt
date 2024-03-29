package com.example.mynewsapp.ui.setting

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.FragmentAccountBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import timber.log.Timber

class AccountFragment: Fragment() {
    lateinit var googleSignInClient: GoogleSignInClient
    lateinit var binding: FragmentAccountBinding
    lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
        binding = FragmentAccountBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        bindingView()

        checkIfLogin()
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true) //show back button
        return binding.root
    }
    private fun checkIfLogin() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            updateUI(currentUser)
        } else {
            updateUI(null)
        }
    }
    private fun bindingView() {
        binding.googleSignInButton.setOnClickListener {
            Timber.d("click google sign in button")
            signIn()
        }
        binding.signOutButton.setOnClickListener {
            Timber.d("click sign out button")
            signOut()
        }
    }
    private fun signIn() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }
    private fun signOut() {
        auth.signOut()
        updateUI(null)
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                Timber.d("${account.id}")
                fireBaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Timber.w("google sign in failed. $e")
            }
        }
    }
    private fun fireBaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener {  task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Timber.d("fireBaseAuthWithGoogle success $user")
                    if (user != null) {
                        updateUI(user)
                        editPreference()
                    }
                } else {
                    Timber.w("fireBaseAuthWithGoogle failure ${task.exception}")
                }
            }

    }
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {

            val name = user.displayName
            val email = user.email
            val userImageUrl = user.photoUrl

            Timber.d("user name $name, email $email, image $userImageUrl")
            binding.username.text = email
            binding.googleSignInButton.visibility = View.INVISIBLE
            binding.signOutButton.visibility = View.VISIBLE

            if (userImageUrl!==null) {
                Glide
                    .with(this)
                    .load(userImageUrl)
                    .centerCrop()
                    .into(binding.userImage)
            } else {
                binding.userImage.setImageResource(R.drawable.ic_baseline_person)
            }

        } else {
            binding.signOutButton.visibility = View.INVISIBLE
            binding.googleSignInButton.visibility = View.VISIBLE
            binding.username.text = ""
            binding.userImage.setImageResource(R.drawable.ic_baseline_person)
        }


    }
   private fun editPreference() {
       // modify preference
       val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
       val editor = sp.edit()
       editor.putBoolean("firstTimeAfterLogin", true)
       editor.apply()
   }
    companion object {
        const val RC_SIGN_IN = 9001
    }
}