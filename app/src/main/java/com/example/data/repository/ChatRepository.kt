package com.example.data.repository

import com.example.data.api.OpenAiApiService
import com.example.data.local.ChatDao
import com.example.data.model.AppSettings
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException

class ChatRepository(
    private val chatDao: ChatDao,
    private val apiService: OpenAiApiService
) {
    val threads: Flow<List<ChatThread>> = chatDao.getThreadsFlow()
    val settingsFlow: Flow<AppSettings?> = chatDao.getSettingsFlow()

    fun getMessages(threadId: Int): Flow<List<ChatMessage>> {
        return chatDao.getMessagesFlow(threadId)
    }

    suspend fun getSettings(): AppSettings {
        var settings = chatDao.getSettings()
        if (settings == null) {
            settings = AppSettings()
            chatDao.saveSettings(settings)
        }
        return settings
    }

    suspend fun saveSettings(settings: AppSettings) {
        chatDao.saveSettings(settings.copy(id = 1))
    }

    suspend fun createThread(title: String, systemPrompt: String, modelName: String): Int {
        val thread = ChatThread(
            title = title,
            systemPrompt = systemPrompt,
            modelName = modelName
        )
        return chatDao.insertThread(thread).toInt()
    }

    suspend fun updateThreadTitle(threadId: Int, newTitle: String) {
        val thread = chatDao.getThreadById(threadId)
        if (thread != null) {
            chatDao.updateThread(thread.copy(title = newTitle))
        }
    }

    suspend fun deleteThread(threadId: Int) {
        val thread = chatDao.getThreadById(threadId)
        if (thread != null) {
            chatDao.deleteMessagesForThread(threadId)
            chatDao.deleteThread(thread)
        }
    }

    suspend fun saveMessage(threadId: Int, role: String, content: String) {
        val msg = ChatMessage(threadId = threadId, role = role, content = content)
        chatDao.insertMessage(msg)
    }

    suspend fun getThreadById(threadId: Int): ChatThread? {
        return chatDao.getThreadById(threadId)
    }

    suspend fun sendMessage(threadId: Int, userMessageContent: String): String {
        // 1. Persist the user message
        saveMessage(threadId, "user", userMessageContent)

        // 2. Load configurations
        val settings = getSettings()
        val thread = chatDao.getThreadById(threadId) ?: throw IOException("Thread not found")

        // 3. Gather messages in this thread
        val rawMessages = chatDao.getMessagesFlow(threadId).firstOrNull() ?: emptyList()

        // 4. Transform files into API format and prepend system prompt
        val messagesDto = mutableListOf<OpenAiApiService.ChatMessageDto>()
        
        // Add system message if configured
        val systemPrompt = thread.systemPrompt.ifBlank { settings.systemPrompt }
        if (systemPrompt.isNotBlank()) {
            messagesDto.add(OpenAiApiService.ChatMessageDto(role = "system", content = systemPrompt))
        }

        // Add history
        rawMessages.forEach { msg ->
            if (!msg.isPending) {
                messagesDto.add(OpenAiApiService.ChatMessageDto(role = msg.role, content = msg.content))
            }
        }

        // 5. Query open ai API
        val responseText = apiService.getChatCompletion(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            modelName = thread.modelName.ifBlank { settings.modelName },
            messages = messagesDto
        )

        // 6. Save assistant response
        saveMessage(threadId, "assistant", responseText)

        return responseText
    }
}
