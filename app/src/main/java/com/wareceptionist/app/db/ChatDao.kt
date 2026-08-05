package com.wareceptionist.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Query("SELECT * FROM chat_sessions WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getSession(phone: String): ChatSession?

    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE sessionPhone = :phone ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(phone: String): List<ChatMessage>
    
    @Query("DELETE FROM chat_sessions WHERE phoneNumber = :phone")
    suspend fun deleteSession(phone: String)
}
