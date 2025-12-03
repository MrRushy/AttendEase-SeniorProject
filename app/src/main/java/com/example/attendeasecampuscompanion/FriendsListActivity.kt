package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FriendsListActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: TextView
    private lateinit var friendsRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var adapter: FriendsAdapter
    private val friendsList = mutableListOf<Friend>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends_list)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()
        setupRecyclerView()
        loadFriends()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        friendsRecyclerView = findViewById(R.id.friendsRecyclerView)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)

        backButton.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = FriendsAdapter(
            friendsList,
            onFriendClick = { friend -> viewProfile(friend) },
            onMessageClick = { friend -> openChat(friend) }
        )

        friendsRecyclerView.layoutManager = LinearLayoutManager(this)
        friendsRecyclerView.adapter = adapter
    }

    private fun loadFriends() {
        val userId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        friendsRecyclerView.visibility = View.GONE

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

                adapter.notifyDataSetChanged()

                if (friendsList.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    friendsRecyclerView.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    friendsRecyclerView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            }
    }

    private fun viewProfile(friend: Friend) {
        val intent = Intent(this, UserProfileActivity::class.java)
        intent.putExtra("userId", friend.friendId)
        startActivity(intent)
    }

    private fun openChat(friend: Friend) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("otherUserId", friend.friendId)
            putExtra("otherUserName", friend.friendName)
            putExtra("otherUserPic", friend.friendProfilePic)
        }
        startActivity(intent)
    }
}