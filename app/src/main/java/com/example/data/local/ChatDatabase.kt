package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.data.model.AppSettings
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // Thread operations
    @Query("SELECT * FROM chat_threads ORDER BY id DESC")
    fun getThreadsFlow(): Flow<List<ChatThread>>

    @Query("SELECT * FROM chat_threads WHERE id = :id")
    suspend fun getThreadById(id: Int): ChatThread?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThread): Long

    @Update
    suspend fun updateThread(thread: ChatThread)

    @Delete
    suspend fun deleteThread(thread: ChatThread)

    // Message operations
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestamp ASC, id ASC")
    fun getMessagesFlow(threadId: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: Int)

    // Settings operations
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)
}

@Database(entities = [ChatThread::class, ChatMessage::class, AppSettings::class], version = 1, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
