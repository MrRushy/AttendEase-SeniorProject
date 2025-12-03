package com.example.attendeasecampuscompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView

class FriendsAdapter(
    private val friends: List<Friend>,
    private val onFriendClick: (Friend) -> Unit,
    private val onMessageClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val friendImage: CircleImageView = view.findViewById(R.id.friendImage)
        val friendName: TextView = view.findViewById(R.id.friendName)
        val friendMajor: TextView = view.findViewById(R.id.friendMajor)
        val messageButton: ImageButton = view.findViewById(R.id.messageButton)

        fun bind(friend: Friend) {
            friendName.text = friend.friendName
            friendMajor.text = friend.friendMajor

            if (friend.friendProfilePic.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(friend.friendProfilePic)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(friendImage)
            } else {
                friendImage.setImageResource(R.drawable.ic_profile_placeholder)
            }

            itemView.setOnClickListener {
                onFriendClick(friend)
            }

            messageButton.setOnClickListener {
                onMessageClick(friend)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(friends[position])
    }

    override fun getItemCount() = friends.size
}