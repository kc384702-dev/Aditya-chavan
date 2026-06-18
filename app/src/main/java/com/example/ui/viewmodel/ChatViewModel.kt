package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppSettings
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

class ChatViewModel(
    private val repository: ChatRepository,
    private val sharedPrefs: android.content.SharedPreferences
) : ViewModel() {

    // Login and user profile states
    private val _isLoggedIn = MutableStateFlow(sharedPrefs.getBoolean("is_logged_in", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userName = MutableStateFlow(sharedPrefs.getString("user_name", "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow(sharedPrefs.getString("user_email", "") ?: "")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userInitials = MutableStateFlow(sharedPrefs.getString("user_initials", "G") ?: "G")
    val userInitials: StateFlow<String> = _userInitials.asStateFlow()

    fun loginUser(email: String, name: String) {
        val initials = getInitialsFromName(name)
        sharedPrefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("user_name", name)
            .putString("user_email", email)
            .putString("user_initials", initials)
            .apply()

        _userName.value = name
        _userEmail.value = email
        _userInitials.value = initials
        _isLoggedIn.value = true
    }

    fun signupUser(email: String, name: String) {
        loginUser(email, name)
    }

    fun loginAsGuest() {
        val name = "Guest User"
        val email = "guest@nexusgpt.local"
        val initials = "GU"
        sharedPrefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("user_name", name)
            .putString("user_email", email)
            .putString("user_initials", initials)
            .apply()

        _userName.value = name
        _userEmail.value = email
        _userInitials.value = initials
        _isLoggedIn.value = true
    }

    fun logoutUser() {
        sharedPrefs.edit()
            .putBoolean("is_logged_in", false)
            .putString("user_name", "")
            .putString("user_email", "")
            .putString("user_initials", "G")
            .apply()

        _userName.value = ""
        _userEmail.value = ""
        _userInitials.value = "G"
        _isLoggedIn.value = false
    }

    private fun getInitialsFromName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "U"
        val parts = trimmed.split("\\s+".toRegex())
        return if (parts.size >= 2) {
            val first = parts[0].take(1).uppercase()
            val second = parts[1].take(1).uppercase()
            first + second
        } else {
            trimmed.take(2).uppercase()
        }
    }

    // List of all threads
    val threads: StateFlow<List<ChatThread>> = repository.threads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings config
    val settingsFlow: StateFlow<AppSettings> = repository.settingsFlow
        .combine(flowOf(true)) { s, _ -> s ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    // Selected thread ID
    private val _selectedThreadId = MutableStateFlow<Int?>(null)
    val selectedThreadId: StateFlow<Int?> = _selectedThreadId.asStateFlow()

    // Flag representing if assistant is answering
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    // Temporary network or parsing error string
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Search query for historical chats list
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        // Auto-initialize settings on load
        viewModelScope.launch {
            repository.getSettings()
        }
    }

    // Filter threads based on search queries
    val filteredThreads: StateFlow<List<ChatThread>> = combine(threads, searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter { it.title.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observe active messages reactively
    val activeMessages: StateFlow<List<ChatMessage>> = selectedThreadId.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            repository.getMessages(id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectThread(threadId: Int?) {
        _selectedThreadId.value = threadId
        clearError()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun saveSettings(baseUrl: String, apiKey: String, modelName: String, systemPrompt: String) {
        viewModelScope.launch {
            val updated = AppSettings(
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelName = modelName,
                systemPrompt = systemPrompt
            )
            repository.saveSettings(updated)
        }
    }

    fun startNewThread(title: String = "New Conversation", systemPrompt: String = "", modelOverride: String = "") {
        viewModelScope.launch {
            val configuredSettings = repository.getSettings()
            val mName = modelOverride.ifBlank { configuredSettings.modelName }
            val sysPrompt = systemPrompt.ifBlank { configuredSettings.systemPrompt }

            val newId = repository.createThread(
                title = title,
                systemPrompt = sysPrompt,
                modelName = mName
            )
            selectThread(newId)
        }
    }

    fun renameThread(threadId: Int, newTitle: String) {
        viewModelScope.launch {
            repository.updateThreadTitle(threadId, newTitle)
        }
    }

    fun deleteThread(threadId: Int) {
        viewModelScope.launch {
            repository.deleteThread(threadId)
            if (selectedThreadId.value == threadId) {
                _selectedThreadId.value = threads.value.firstOrNull { it.id != threadId }?.id
            }
        }
    }

    fun sendMessage(content: String) {
        val activeId = selectedThreadId.value ?: return
        if (content.isBlank() || _isTyping.value) return

        viewModelScope.launch {
            _isTyping.value = true
            _errorMessage.value = null
            
            // If the thread title is "New Conversation", rename it based on the first prompt!
            val thread = repository.getThreadById(activeId)
            if (thread != null && thread.title == "New Conversation") {
                val cleanedTitle = if (content.length > 25) content.take(22) + "..." else content
                repository.updateThreadTitle(activeId, cleanedTitle)
            }

            try {
                repository.sendMessage(activeId, content)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: e.message ?: "An unknown network error occurred"
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun handleSuggestionClicked(suggestionText: String) {
        viewModelScope.launch {
            val configuredSettings = repository.getSettings()
            val newId = repository.createThread(
                title = "New Conversation",
                systemPrompt = configuredSettings.systemPrompt,
                modelName = configuredSettings.modelName
            )
            selectThread(newId)
            sendMessage(suggestionText)
        }
    }
}

class ChatViewModelFactory(
    private val repository: ChatRepository,
    private val sharedPrefs: android.content.SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repository, sharedPrefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
