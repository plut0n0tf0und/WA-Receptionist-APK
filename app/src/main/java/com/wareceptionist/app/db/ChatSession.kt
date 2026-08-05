package com.wareceptionist.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val phoneNumber: String,
    val lastUpdated: Long
)
