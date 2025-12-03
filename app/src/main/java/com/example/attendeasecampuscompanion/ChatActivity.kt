package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import de.hdodenhof.circleimageview.CircleImageView

class ChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: TextView
    private lateinit var chatPartnerImage: CircleImageView
    private lateinit var chatPartnerName: TextView
    private lateinit var chatPartnerStatus: TextView
    private lateinit var viewProfileButton: ImageButton
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton

    private lateinit var adapter: MessageAdapter
    private val messagesList = mutableListOf<Message>()

    private var chatId: String = ""
    private var otherUserId: String = ""
    private var otherUserName: String = ""
    private var otherUserPic: String = ""
    private var currentUserName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        chatId = intent.getStringExtra("chatId") ?: ""
        otherUserId = intent.getStringExtra("otherUserId") ?: ""
        otherUserName = intent.getStringExtra("otherUserName") ?: "User"
        otherUserPic = intent.getStringExtra("otherUserPic") ?: ""

        initViews()
        setupUI()
        loadCurrentUserName()

        if (chatId.isEmpty() && otherUserId.isNotEmpty()) {
            findOrCreateChat()
        } else {
            loadMessages()
        }
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        chatPartnerImage = findViewById(R.id.chatPartnerImage)
        chatPartnerName = findViewById(R.id.chatPartnerName)
        chatPartnerStatus = findViewById(R.id.chatPartnerStatus)
        viewProfileButton = findViewById(R.id.viewProfileButton)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
    }

    private fun setupUI() {
        chatPartnerName.text = otherUserName

        if (otherUserPic.isNotEmpty()) {
            Glide.with(this)
                .load(otherUserPic)
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(chatPartnerImage)
        }

        adapter = MessageAdapter(messagesList, auth.currentUser?.uid ?: "")
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        messagesRecyclerView.adapter = adapter

        backButton.setOnClickListener { finish() }

        viewProfileButton.setOnClickListener {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("userId", otherUserId)
            startActivity(intent)
        }

        sendButton.setOnClickListener { sendMessage() }
    }

    private fun loadCurrentUserName() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java)
                currentUserName = "${user?.firstName} ${user?.lastName}"
            }
    }

    private fun findOrCreateChat() {
        val currentUserId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE

        db.collection("Chats")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                var existingChatId: String? = null

                for (doc in documents) {
                    val participants = doc.get("participants") as? List<*>
                    if (participants?.contains(otherUserId) == true) {
                        existingChatId = doc.id
                        break
                    }
                }

                if (existingChatId != null) {
                    chatId = existingChatId
                    loadMessages()
                } else {
                    createNewChat()
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading chat", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createNewChat() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Users").document(currentUserId).get()
            .addOnSuccessListener { currentUserDoc ->
                val currentUser = currentUserDoc.toObject(User::class.java)
                val currentName = "${currentUser?.firstName} ${currentUser?.lastName}"
                val currentPic = currentUser?.profilePictureUrl ?: ""

                val newChatRef = db.collection("Chats").document()
                chatId = newChatRef.id

                val chat = hashMapOf(
                    "chatId" to chatId,
                    "participants" to listOf(currentUserId, otherUserId),
                    "participantNames" to mapOf(
                        currentUserId to currentName,
                        otherUserId to otherUserName
                    ),
                    "participantProfilePics" to mapOf(
                        currentUserId to currentPic,
                        otherUserId to otherUserPic
                    ),
                    "lastMessage" to "",
                    "lastMessageTimestamp" to 0L,
                    "lastMessageSenderId" to "",
                    "unreadCount" to mapOf(
                        currentUserId to 0,
                        otherUserId to 0
                    )
                )

                newChatRef.set(chat)
                    .addOnSuccessListener {
                        loadMessages()
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Error creating chat", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun loadMessages() {
        if (chatId.isEmpty()) return

        progressBar.visibility = View.VISIBLE

        db.collection("Chats").document(chatId).collection("Messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    return@addSnapshotListener
                }

                messagesList.clear()
                snapshots?.documents?.forEach { doc ->
                    val message = doc.toObject(Message::class.java)
                    if (message != null) {
                        messagesList.add(message)
                    }
                }

                adapter.notifyDataSetChanged()

                if (messagesList.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    messagesRecyclerView.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    messagesRecyclerView.visibility = View.VISIBLE
                    messagesRecyclerView.scrollToPosition(messagesList.size - 1)
                }

                markMessagesAsRead()
            }
    }

    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return

        val currentUserId = auth.currentUser?.uid ?: return

        if (chatId.isEmpty()) {
            Toast.makeText(this, "Chat not ready yet", Toast.LENGTH_SHORT).show()
            return
        }

        val messageRef = db.collection("Chats").document(chatId).collection("Messages").document()
        val messageId = messageRef.id

        val message = Message(
            messageId = messageId,
            senderId = currentUserId,
            senderName = currentUserName,
            text = text,
            timestamp = System.currentTimeMillis(),
            read = false
        )

        messageRef.set(message)
            .addOnSuccessListener {
                messageInput.text.clear()
                updateChatMetadata(text, currentUserId)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateChatMetadata(lastMessage: String, senderId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("Chats").document(chatId)
            .get()
            .addOnSuccessListener { doc ->
                val currentUnread = doc.get("unreadCount") as? Map<String, Long> ?: emptyMap()
                val otherUserUnread = (currentUnread[otherUserId] ?: 0L) + 1

                db.collection("Chats").document(chatId)
                    .update(
                        mapOf(
                            "lastMessage" to lastMessage,
                            "lastMessageTimestamp" to System.currentTimeMillis(),
                            "lastMessageSenderId" to senderId,
                            "unreadCount.$otherUserId" to otherUserUnread
                        )
                    )
            }
    }

    private fun markMessagesAsRead() {
        val currentUserId = auth.currentUser?.uid ?: return
        if (chatId.isEmpty()) return

        db.collection("Chats").document(chatId)
            .update("unreadCount.$currentUserId", 0)
    }
}