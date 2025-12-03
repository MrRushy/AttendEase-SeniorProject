package com.example.attendeasecampuscompanion

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MessagingFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var backButton: TextView
    private lateinit var chatsRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var fabNewChat: FloatingActionButton

    private lateinit var adapter: ChatAdapter
    private val chatsList = mutableListOf<Chat>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messaging, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        backButton = view.findViewById(R.id.backButton)
        chatsRecyclerView = view.findViewById(R.id.chatsRecyclerView)
        emptyState = view.findViewById(R.id.emptyState)
        progressBar = view.findViewById(R.id.progressBar)
        fabNewChat = view.findViewById(R.id.fabNewChat)

        setupRecyclerView()
        loadChats()

        backButton.setOnClickListener {
            requireActivity().finish()
        }

        fabNewChat.setOnClickListener {
            startActivity(Intent(requireContext(), NewChatActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadChats()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(
            chatsList,
            currentUserId = auth.currentUser?.uid ?: "",
            onChatClick = { chat -> openChat(chat) }
        )

        chatsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        chatsRecyclerView.adapter = adapter
    }

    private fun loadChats() {
        val userId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        chatsRecyclerView.visibility = View.GONE

        db.collection("Chats")
            .whereArrayContains("participants", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (!isAdded) return@addSnapshotListener

                progressBar.visibility = View.GONE

                if (error != null) {
                    showEmptyState()
                    return@addSnapshotListener
                }

                chatsList.clear()
                snapshots?.documents?.forEach { doc ->
                    val chat = doc.toObject(Chat::class.java)
                    if (chat != null) {
                        chatsList.add(chat.copy(chatId = doc.id))
                    }
                }

                adapter.notifyDataSetChanged()

                if (chatsList.isEmpty()) {
                    showEmptyState()
                } else {
                    emptyState.visibility = View.GONE
                    chatsRecyclerView.visibility = View.VISIBLE
                }
            }
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        chatsRecyclerView.visibility = View.GONE
    }

    private fun openChat(chat: Chat) {
        val currentUserId = auth.currentUser?.uid ?: return
        val otherUserId = chat.participants.find { it != currentUserId } ?: return

        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("chatId", chat.chatId)
            putExtra("otherUserId", otherUserId)
            putExtra("otherUserName", chat.participantNames[otherUserId])
            putExtra("otherUserPic", chat.participantProfilePics[otherUserId])
        }
        startActivity(intent)
    }
}