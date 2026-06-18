package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.room.Room
import com.example.data.api.OpenAiApiService
import com.example.data.local.ChatDatabase
import com.example.data.model.AppSettings
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.repository.ChatRepository
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ChatViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    // Lazy instantiation of Database, Service and Repository
    private val database: ChatDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            ChatDatabase::class.java,
            "ai_chat_assistant_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    private val apiService by lazy { OpenAiApiService() }
    private val repository by lazy { ChatRepository(database.chatDao(), apiService) }
    private val sharedPrefs by lazy { getSharedPreferences("nexus_gpt_prefs", android.content.Context.MODE_PRIVATE) }

    // Inject our custom ChatViewModel
    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(repository, sharedPrefs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(viewModel: ChatViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val userInitials by viewModel.userInitials.collectAsState()

    var hasAnimationCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            hasAnimationCompleted = false
        }
    }

    if (!isLoggedIn) {
        com.example.ui.LoginPage(
            viewModel = viewModel,
            onLoginSuccess = {}
        )
        return
    }

    if (!hasAnimationCompleted) {
        com.example.ui.LogoAnimationScreen(
            onAnimationComplete = {
                hasAnimationCompleted = true
            }
        )
        return
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val threads by viewModel.filteredThreads.collectAsState()
    val activeThreadId by viewModel.selectedThreadId.collectAsState()
    val activeMessages by viewModel.activeMessages.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            ) {
                DrawerPane(
                    threads = threads,
                    activeThreadId = activeThreadId,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { viewModel.updateSearchQuery(it) },
                    onSelectThread = { id ->
                        viewModel.selectThread(id)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewChatClicked = {
                        viewModel.startNewThread(
                            title = "New Conversation",
                            systemPrompt = settings.systemPrompt,
                            modelOverride = settings.modelName
                        )
                        coroutineScope.launch { drawerState.close() }
                    },
                    onDeleteThread = { id ->
                        viewModel.deleteThread(id)
                    },
                    isLoggedIn = isLoggedIn,
                    userName = userName,
                    userEmail = userEmail,
                    userInitials = userInitials,
                    onLogoutClicked = {
                        viewModel.logoutUser()
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        val currentThread = threads.find { it.id == activeThreadId }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentThread?.title ?: "Nexus GPT",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF10B981), CircleShape) // Emerald green
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = (if (currentThread != null && currentThread.modelName.isNotBlank()) currentThread.modelName else "GPT-4o").uppercase() + " CONNECTED",
                                    fontSize = 9.sp,
                                    letterSpacing = 1.sp,
                                    color = if (isSystemInDarkTheme()) Color(0xFFCCC2DC) else Color(0xFF49454F),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Navigation Menu")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showProfileDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(if (isSystemInDarkTheme()) Color(0xFF10A37F) else Color(0xFF059669), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userInitials,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (activeThreadId == null) {
                    OnboardingPane(
                        userSettings = settings,
                        onSuggestionClicked = { suggestion ->
                            viewModel.handleSuggestionClicked(suggestion)
                        },
                        onCreateNewChat = {
                            viewModel.startNewThread(
                                title = "New Conversation",
                                systemPrompt = settings.systemPrompt,
                                modelOverride = settings.modelName
                            )
                        }
                    )
                } else {
                    ChatPane(
                        messages = activeMessages,
                        isTyping = isTyping,
                        errorMessage = errorMessage,
                        onSendMessage = { prompt -> viewModel.sendMessage(prompt) },
                        onClearError = { viewModel.clearError() },
                        onConfigureSettings = { showSettingsDialog = true }
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = settings,
            onDismiss = { showSettingsDialog = false },
            onSave = { baseUrl, apiKey, model, instructions ->
                viewModel.saveSettings(baseUrl, apiKey, model, instructions)
                showSettingsDialog = false
            }
        )
    }

    if (showProfileDialog) {
        ProfileDialog(
            name = userName,
            email = userEmail,
            initials = userInitials,
            onDismiss = { showProfileDialog = false },
            onLogout = {
                viewModel.logoutUser()
                showProfileDialog = false
            },
            onOpenSettings = {
                showSettingsDialog = true
                showProfileDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialog(
    name: String,
    email: String,
    initials: String,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color(0xFF10A37F),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Your User Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.radialGradient(colors = listOf(Color(0xFF10A37F), Color(0xFF059669))),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = name.ifBlank { "Guest User" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = email.ifBlank { "guest@nexusgpt.local" },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Account Tier", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(
                                text = if (email.contains("guest")) "Frictionless Guest Pass" else "Verified Premium Account",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (email.contains("guest")) MaterialTheme.colorScheme.onSurface else Color(0xFF10A37F)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settings button
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Settings", fontSize = 11.sp)
                }

                // Logout button
                Button(
                    onClick = onLogout,
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(imageVector = Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sign Out", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun DrawerPane(
    threads: List<ChatThread>,
    activeThreadId: Int?,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSelectThread: (Int) -> Unit,
    onNewChatClicked: () -> Unit,
    onDeleteThread: (Int) -> Unit,
    isLoggedIn: Boolean,
    userName: String,
    userEmail: String,
    userInitials: String,
    onLogoutClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // App header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Conversations",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // New Chat Button
        Button(
            onClick = onNewChatClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("new_chat_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat Thread", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Input Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Filter conversations...", fontSize = 14.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        // Active Conversations Header
        Text(
            text = "Active Chats",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Threads Scrollable list
        if (threads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No prior chats found",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(threads, key = { it.id }) { thread ->
                    val isActive = thread.id == activeThreadId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable { onSelectThread(thread.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = thread.title,
                                fontSize = 14.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(Date(thread.createdAt)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(
                            onClick = { onDeleteThread(thread.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Conversation Thread",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Beautiful User profile footer in drawer
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isSystemInDarkTheme()) Color(0xFF10A37F) else Color(0xFF059669),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userInitials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userName.ifBlank { "Guest User" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = userEmail.ifBlank { "guest@nexusgpt.local" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onLogoutClicked,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Log Out",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun OnboardingPane(
    userSettings: AppSettings,
    onSuggestionClicked: (String) -> Unit,
    onCreateNewChat: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Render the generated onboarding illustration!
            Image(
                painter = painterResource(id = R.drawable.img_onboarding_1781754453708),
                contentDescription = "AI Floating Assistant Hologram Illustrative Art",
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to Nexus GPT",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Configure custom gateways to Ollama, Groq, LM Studio, or OpenAI. Local SQLite secures all conversational logs.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action to spin up manual dialog
            Button(
                onClick = onCreateNewChat,
                modifier = Modifier.testTag("get_started_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start New Chat Session")
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Quick starters
            Text(
                text = "💡 QUICK CONVERSATION STARTERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val suggestions = listOf(
                "💡 Brainstorm Android features",
                "💻 Kotlin Flow vs LiveData",
                "📝 Write professional email",
                "🌎 Translate to Japanese"
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSystemInDarkTheme()) Color(0xFF211F26) else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isSystemInDarkTheme()) Color(0xFF49454F) else Color(0xFF79747E),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onSuggestionClicked(suggestions[0]) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = suggestions[0],
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSystemInDarkTheme()) Color(0xFF211F26) else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isSystemInDarkTheme()) Color(0xFF49454F) else Color(0xFF79747E),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onSuggestionClicked(suggestions[1]) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = suggestions[1],
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSystemInDarkTheme()) Color(0xFF211F26) else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isSystemInDarkTheme()) Color(0xFF49454F) else Color(0xFF79747E),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onSuggestionClicked(suggestions[2]) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = suggestions[2],
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSystemInDarkTheme()) Color(0xFF211F26) else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isSystemInDarkTheme()) Color(0xFF49454F) else Color(0xFF79747E),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onSuggestionClicked(suggestions[3]) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = suggestions[3],
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatPane(
    messages: List<ChatMessage>,
    isTyping: Boolean,
    errorMessage: String?,
    onSendMessage: (String) -> Unit,
    onClearError: () -> Unit,
    onConfigureSettings: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var inputQuery by remember { mutableStateOf("") }

    // Scroll to the latest message automatically
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (messages.isEmpty() && !isTyping) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "New Conversation Started",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Type your query below to chat with Nexus GPT",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                MessageBubble(message = msg)
            }

            if (isTyping) {
                item {
                    PendingResponseBubble()
                }
            }

            if (errorMessage != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSystemInDarkTheme()) Color(0xFF4A1525) else Color(0xFFFFEBEE))
                            .border(1.dp, if (isSystemInDarkTheme()) Color(0xFF900C3F) else Color(0xFFEF5350), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error notification icon",
                                tint = if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else Color(0xFFD32F2F),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generation Error",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else Color(0xFFC62828)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage,
                            fontSize = 12.sp,
                            color = if (isSystemInDarkTheme()) Color(0xFFFFDAD6) else Color(0xFF212121),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val lastUserMsg = messages.lastOrNull { it.role == "user" }
                                    if (lastUserMsg != null) {
                                        onClearError()
                                        onSendMessage(lastUserMsg.content)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSystemInDarkTheme()) Color(0xFF900C3F) else Color(0xFFD32F2F),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("Regenerate Response", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            
                            TextButton(
                                onClick = onConfigureSettings,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else Color(0xFFD32F2F)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Setup Gateway", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = onClearError,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss error notification",
                                    tint = if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else Color(0xFF7F7F7F),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // Geometric Balance Dock Container
        Surface(
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
            color = if (isSystemInDarkTheme()) Color(0xFF211F26) else Color(0xFFF3EDF7),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Outer bar of shape rounded-[28px] (pill-shaped entry bar)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (isSystemInDarkTheme()) Color(0xFF312E37) else Color(0xFFE7E0EC))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onConfigureSettings,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Open Custom Endpoints Dialog",
                            tint = if (isSystemInDarkTheme()) Color(0xFFCCC2DC) else Color(0xFF49454F),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = { 
                            Text(
                                text = "Ask anything...", 
                                fontSize = 14.sp,
                                color = if (isSystemInDarkTheme()) Color(0xFFCCC2DC) else Color(0xFF49454F)
                            ) 
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input")
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(28.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputQuery.isNotBlank() && !isTyping) {
                                    onSendMessage(inputQuery)
                                    inputQuery = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            focusedTextColor = if (isSystemInDarkTheme()) Color(0xFFE6E1E5) else Color(0xFF1D1B20),
                            unfocusedTextColor = if (isSystemInDarkTheme()) Color(0xFFE6E1E5) else Color(0xFF1D1B20)
                        )
                    )

                    IconButton(
                        onClick = { 
                            Toast.makeText(context, "Voice input not configured", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Speech Voice Input",
                            tint = if (isSystemInDarkTheme()) Color(0xFFCCC2DC) else Color(0xFF49454F),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                FloatingActionButton(
                    onClick = {
                        if (inputQuery.isNotBlank() && !isTyping) {
                            onSendMessage(inputQuery)
                            inputQuery = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_button"),
                    shape = CircleShape,
                    containerColor = if (isSystemInDarkTheme()) Color(0xFFD0BCFF) else Color(0xFF6750A4),
                    contentColor = if (isSystemInDarkTheme()) Color(0xFF382A5C) else Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Submit Chat Message",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val isDark = isSystemInDarkTheme()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    if (isUser) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF2F2F2F) else Color(0xFFF4F4F4)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.content,
                        color = if (isDark) Color(0xFFECECEC) else Color(0xFF1F1F1F),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.testTag("user_message_text")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        fontSize = 9.sp,
                        color = if (isDark) Color(0xFF9F9F9F) else Color(0xFF7F7F7F),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = Color(0xFF10A37F),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Nexus GPT Spark avatar",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp)
            ) {
                val showKpiHeader = message.content.contains("KPI") || message.content.contains("growth") || message.content.contains("analyzed")
                if (showKpiHeader) {
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF10B981) else Color(0xFF059669),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "KPI INSIGHTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF10B981) else Color(0xFF059669),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Text(
                    text = message.content,
                    color = if (isDark) Color(0xFFECECEC) else Color(0xFF1F1F1F),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.testTag("bot_message_text")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        fontSize = 9.sp,
                        color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
                    )

                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy text to clipboard",
                        tint = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575),
                        modifier = Modifier
                            .size(13.dp)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(message.content))
                                Toast.makeText(context, "Copied response", Toast.LENGTH_SHORT).show()
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun PendingResponseBubble() {
    var animateDot by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400)
            animateDot = (animateDot + 1) % 4
        }
    }

    val suffix = when (animateDot) {
        1 -> "."
        2 -> ".."
        3 -> "..."
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            modifier = Modifier.widthIn(max = 200.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI is thinking$suffix",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var baseUrl by remember { mutableStateOf(currentSettings.baseUrl) }
    var apiKey by remember { mutableStateOf(currentSettings.apiKey) }
    var modelName by remember { mutableStateOf(currentSettings.modelName) }
    var systemPrompt by remember { mutableStateOf(currentSettings.systemPrompt) }
    var keyVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "API Configuration",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base Gateway URL", fontSize = 12.sp) },
                    placeholder = { Text("https://api.openai.com/v1") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("base_url_input"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Authorization Bearer API Key", fontSize = 12.sp) },
                    placeholder = { Text("sk-or-AnythingKey") },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) "Hide Key" else "Reveal Key",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("api_key_input"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Default Model Identifier", fontSize = 12.sp) },
                    placeholder = { Text("gpt-4o-mini") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("model_input"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("Global Assistant Prompt", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3
                )

                Text(
                    text = "Supports local host connections like Ollama (http://10.0.2.2:11434/v1) and remote providers like Groq, Mistral, and OpenAI.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(baseUrl, apiKey, modelName, systemPrompt) },
                        modifier = Modifier.testTag("save_settings_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Gateway", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
