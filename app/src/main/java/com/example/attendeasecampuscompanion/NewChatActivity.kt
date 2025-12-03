package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NewChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: TextView
    private lateinit var searchInput: EditText
    private lateinit var friendsRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var adapter: FriendSelectAdapter
    private val friendsList = mutableListOf<Friend>()
    private val filteredFriends = mutableListOf<Friend>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_chat)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()
        setupRecyclerView()
        setupSearch()
        loadFriends()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        searchInput = findViewById(R.id.searchInput)
        friendsRecyclerView = findViewById(R.id.friendsRecyclerView)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)

        backButton.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = FriendSelectAdapter(filteredFriends) { friend ->
            openChatWithFriend(friend)
        }

        friendsRecyclerView.layoutManager = LinearLayoutManager(this)
        friendsRecyclerView.adapter = adapter
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterFriends(s.toString())
            }
        })
    }

    private fun loadFriends() {
        val userId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE

        db.collection("Users").document(userId).collection("Friends")
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE

                friendsList.clear()
                for (doc in documents) {
                    val friend = doc.toObject(Friend::class.java)
                    friendsList.add(friend)
                }

                filteredFriends.clear()
                filteredFriends.addAll(friendsList)
                adapter.notifyDataSetChanged()

                updateEmptyState()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                updateEmptyState()
            }
    }

    private fun filterFriends(query: String) {
        filteredFriends.clear()

        if (query.isEmpty()) {
            filteredFriends.addAll(friendsList)
        } else {
            friendsList.forEach { friend ->
                if (friend.friendName.lowercase().contains(query.lowercase())) {
                    filteredFriends.add(friend)
                }
            }
        }

        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (filteredFriends.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            friendsRecyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            friendsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun openChatWithFriend(friend: Friend) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("otherUserId", friend.friendId)
            putExtra("otherUserName", friend.friendName)
            putExtra("otherUserPic", friend.friendProfilePic)
        }
        startActivity(intent)
        finish()
    }
}