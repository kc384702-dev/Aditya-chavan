package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_threads")
data class ChatThread(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val systemPrompt: String = "You are a helpful, highly polished AI assistant.",
    val modelName: String = "gpt-4o-mini"
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val threadId: Int,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPending: Boolean = false
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1, // Single-row database configuration
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val modelName: String = "gpt-4o-mini",
    val systemPrompt: String = "You are a helpful, descriptive AI Assistant. Respond in elegant formatting."
)
