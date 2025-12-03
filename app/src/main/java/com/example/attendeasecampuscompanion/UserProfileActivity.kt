package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView

class UserProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: TextView
    private lateinit var profileImage: CircleImageView
    private lateinit var nameText: TextView
    private lateinit var majorText: TextView
    private lateinit var campusText: TextView
    private lateinit var bioText: TextView
    private lateinit var friendStatusCard: MaterialCardView
    private lateinit var friendStatusText: TextView
    private lateinit var addFriendButton: Button
    private lateinit var messageButton: Button
    private lateinit var privacyNotice: TextView
    private lateinit var progressBar: ProgressBar

    private var userId: String = ""
    private var targetUser: User? = null
    private var isFriend: Boolean = false
    private var hasPendingRequest: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        userId = intent.getStringExtra("userId") ?: ""

        if (userId.isEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        loadUserProfile()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        profileImage = findViewById(R.id.profileImage)
        nameText = findViewById(R.id.nameText)
        majorText = findViewById(R.id.majorText)
        campusText = findViewById(R.id.campusText)
        bioText = findViewById(R.id.bioText)
        friendStatusCard = findViewById(R.id.friendStatusCard)
        friendStatusText = findViewById(R.id.friendStatusText)
        addFriendButton = findViewById(R.id.addFriendButton)
        messageButton = findViewById(R.id.messageButton)
        privacyNotice = findViewById(R.id.privacyNotice)
        progressBar = findViewById(R.id.progressBar)

        backButton.setOnClickListener { finish() }

        addFriendButton.setOnClickListener { handleAddFriend() }
        messageButton.setOnClickListener { openChat() }
    }

    private fun loadUserProfile() {
        progressBar.visibility = View.VISIBLE

        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE

                if (document.exists()) {
                    targetUser = document.toObject(User::class.java)
                    displayUserInfo()
                    checkFriendshipStatus()
                } else {
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayUserInfo() {
        val user = targetUser ?: return

        nameText.text = "${user.firstName} ${user.lastName}"
        majorText.text = user.major
        campusText.text = "${user.campus} Campus"
        bioText.text = if (user.bio.isEmpty()) "No bio available" else user.bio

        if (user.profilePictureUrl.isNotEmpty()) {
            Glide.with(this)
                .load(user.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(profileImage)
        }

        val privacy = user.settings["profilePrivacy"] as? String ?: "public"
        if (privacy == "private") {
            privacyNotice.visibility = View.VISIBLE
            privacyNotice.text = "🔒 This user has a private profile"
        }
    }

    private fun checkFriendshipStatus() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Users").document(currentUserId).collection("Friends")
            .document(userId)
            .get()
            .addOnSuccessListener { friendDoc ->
                if (friendDoc.exists() && friendDoc.getString("status") == "active") {
                    isFriend = true
                    updateUIForFriend()
                } else {
                    checkPendingRequest()
                }
            }
    }

    private fun checkPendingRequest() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Users").document(currentUserId).collection("FriendRequests")
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                hasPendingRequest = !documents.isEmpty
                updateUIForNonFriend()
            }
            .addOnFailureListener {
                updateUIForNonFriend()
            }
    }

    private fun updateUIForFriend() {
        friendStatusCard.visibility = View.VISIBLE
        friendStatusText.text = "✓ Friends"
        friendStatusText.setTextColor(getColor(R.color.success_green))

        addFriendButton.visibility = View.GONE
        messageButton.visibility = View.VISIBLE
        messageButton.isEnabled = true
    }

    private fun updateUIForNonFriend() {
        friendStatusCard.visibility = View.GONE
        messageButton.visibility = View.GONE

        if (hasPendingRequest) {
            addFriendButton.text = "Request Sent"
            addFriendButton.isEnabled = false
            addFriendButton.alpha = 0.6f
        } else {
            addFriendButton.text = "Add Friend"
            addFriendButton.isEnabled = true
            addFriendButton.alpha = 1.0f
        }

        addFriendButton.visibility = View.VISIBLE
    }

    private fun handleAddFriend() {
        if (hasPendingRequest) return

        val currentUserId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE

        db.collection("Users").document(currentUserId)
            .get()
            .addOnSuccessListener { currentUserDoc ->
                val currentUser = currentUserDoc.toObject(User::class.java) ?: return@addOnSuccessListener
                val target = targetUser ?: return@addOnSuccessListener

                val requestId = db.collection("Users").document(userId)
                    .collection("FriendRequests").document().id

                val sentRequest = FriendRequest(
                    requestId = requestId,
                    fromUserId = currentUserId,
                    toUserId = userId,
                    fromUserName = "${currentUser.firstName} ${currentUser.lastName}",
                    fromUserMajor = currentUser.major,
                    fromUserProfilePic = currentUser.profilePictureUrl,
                    status = "pending",
                    timestamp = System.currentTimeMillis(),
                    type = "sent"
                )

                val receivedRequest = sentRequest.copy(type = "received")

                db.collection("Users").document(currentUserId)
                    .collection("FriendRequests").document(requestId)
                    .set(sentRequest)

                db.collection("Users").document(userId)
                    .collection("FriendRequests").document(requestId)
                    .set(receivedRequest)
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        hasPendingRequest = true
                        updateUIForNonFriend()
                        Toast.makeText(this, "Friend request sent!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Failed to send request", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun openChat() {
        if (!isFriend) {
            Toast.makeText(this, "You can only message friends", Toast.LENGTH_SHORT).show()
            return
        }

        val user = targetUser ?: return

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("otherUserId", userId)
            putExtra("otherUserName", "${user.firstName} ${user.lastName}")
            putExtra("otherUserPic", user.profilePictureUrl)
        }
        startActivity(intent)
    }
}