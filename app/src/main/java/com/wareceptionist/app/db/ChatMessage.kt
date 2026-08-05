package com.wareceptionist.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["phoneNumber"],
            childColumns = ["sessionPhone"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionPhone")]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionPhone: String,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long
)
