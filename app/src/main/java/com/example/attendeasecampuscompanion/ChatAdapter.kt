package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ChatAdapter(
    private val chats: List<Chat>,
    private val currentUserId: String,
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userImage: CircleImageView = view.findViewById(R.id.chatUserImage)
        val userName: TextView = view.findViewById(R.id.chatUserName)
        val lastMessage: TextView = view.findViewById(R.id.chatLastMessage)
        val timestamp: TextView = view.findViewById(R.id.chatTimestamp)
        val unreadIndicator: View = view.findViewById(R.id.unreadIndicator)

        fun bind(chat: Chat) {
            val otherUserId = chat.participants.find { it != currentUserId } ?: ""
            val otherUserName = chat.participantNames[otherUserId] ?: "Unknown"
            val otherUserPic = chat.participantProfilePics[otherUserId] ?: ""

            userName.text = otherUserName

            val messagePrefix = if (chat.lastMessageSenderId == currentUserId) "You: " else ""
            lastMessage.text = "$messagePrefix${chat.lastMessage}"

            timestamp.text = getTimeAgo(chat.lastMessageTimestamp)

            val unreadCount = chat.unreadCount[currentUserId] ?: 0
            unreadIndicator.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE

            if (otherUserPic.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(otherUserPic)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(userImage)
            } else {
                userImage.setImageResource(R.drawable.ic_profile_placeholder)
            }

            itemView.setOnClickListener {
                onChatClick(chat)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount() = chats.size

    private fun getTimeAgo(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }
}