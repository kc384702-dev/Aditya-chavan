package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginPage(
    viewModel: ChatViewModel,
    onLoginSuccess: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    // Background gradient theme - Dark Cosmic palette
    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF0F0F11), Color(0xFF1A1B22))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF7F8FA), Color(0xFFE9EDF0))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val isTabletOrWidescreen = maxWidth >= 550.dp
            
            if (isTabletOrWidescreen) {
                // Adaptive Mobile Mockup Frame rendering
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Viewing in Device Simulator Frame",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF10A37F) else Color(0xFF059669),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .width(365.dp)
                            .height(760.dp)
                            .shadow(24.dp, shape = RoundedCornerShape(42.dp))
                            .border(
                                width = 6.dp,
                                color = if (isDark) Color(0xFF2E3035) else Color(0xFFCCCCCC),
                                shape = RoundedCornerShape(42.dp)
                            )
                            .clip(RoundedCornerShape(42.dp))
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        // Simulated Device Notch & Status bar
                        DeviceStatusBar(isDark)
                        
                        // Main Login UI within the frame
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 40.dp) // Leave area for Status Bar
                        ) {
                            LoginFormContent(
                                viewModel = viewModel,
                                onLoginSuccess = onLoginSuccess
                            )
                        }
                    }
                }
            } else {
                // Regular Native full mobile screen
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginFormContent(
                        viewModel = viewModel,
                        onLoginSuccess = onLoginSuccess
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceStatusBar(isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (isDark) Color(0xFF171717) else Color(0xFFF4F4F4))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Time
        Text(
            text = "9:41 AM",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else Color.Black
        )
        // Camera Notch Placeholder
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(if (isDark) Color(0xFF2E3035) else Color(0xFFCCCCCC))
        )
        // Indicators
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "Simulated Wifi",
                tint = if (isDark) Color.White else Color.Black,
                modifier = Modifier.size(14.dp)
            )
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = "Simulated Battery status",
                tint = if (isDark) Color.White else Color.Black,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun LoginFormContent(
    viewModel: ChatViewModel,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isSignUpMode by remember { mutableStateOf(false) }
    
    var emailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var localError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section: Brand Illustration
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            // Render premium generated GPT Logo with fallback
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .size(86.dp)
                    .border(
                        2.dp,
                        Brush.linearGradient(listOf(Color(0xFF10A37F), Color(0xFF0DCD9D))),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F0F11)),
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    val logoRes = context.resources.getIdentifier("ic_nexus_gpt_logo", "drawable", context.packageName)
                    if (logoRes != 0) {
                        Image(
                            painter = painterResource(id = logoRes),
                            contentDescription = "Nexus GPT Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.radialGradient(colors = listOf(Color(0xFF10A37F), Color(0xFF059669)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = "Fallback AI Brain Icon",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isSignUpMode) "Create Account" else "Welcome to Nexus GPT",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isSignUpMode) "Sign up to start chatting with ChatGPT models" else "Sign in to carry on conversation logs sync",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Center section: Input Forms
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode Selector Slide Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!isSignUpMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            isSignUpMode = false
                            localError = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign In",
                        color = if (!isSignUpMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSignUpMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            isSignUpMode = true
                            localError = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Register",
                        color = if (isSignUpMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Text inputs
            if (isSignUpMode) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("name_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Secret Password") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input"),
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            // Local Helper Error Alert
            AnimatedVisibility(visible = localError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = localError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Button
            Button(
                onClick = {
                    if (emailInput.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput).matches()) {
                        localError = "Please input a valid email address."
                        return@Button
                    }
                    if (passwordInput.length < 5) {
                        localError = "Password must be at least 5 characters."
                        return@Button
                    }
                    if (isSignUpMode && nameInput.isBlank()) {
                        localError = "Display Name cannot be empty during sign up."
                        return@Button
                    }

                    coroutineScope.launch {
                        isLoading = true
                        localError = null
                        // High fidelity simulated auth delay
                        delay(1200)
                        isLoading = false
                        
                        if (isSignUpMode) {
                            viewModel.signupUser(emailInput.trim(), nameInput.trim())
                        } else {
                            // Automatically extract a fallback nickname from email if none is stored
                            val userDisplayNameState = if (emailInput.contains("@")) emailInput.substringBefore("@").replace(".", " ").capitalize() else "User"
                            viewModel.loginUser(emailInput.trim(), userDisplayNameState)
                        }
                        onLoginSuccess()
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("auth_action_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10A37F),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isSignUpMode) "Register Account" else "Secure Log In",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Sandbox Accounts and Divider
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)))
                Text(
                    text = "Sandbox Quick Logins",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Admin sandbox button
                Button(
                    onClick = {
                        emailInput = "admin@nexusgpt.com"
                        passwordInput = "admin12345"
                        nameInput = "John Doe"
                    },
                    modifier = Modifier.weight(1f).height(38.dp),
                    colors = ButtonDefaults.outlinedButtonColors(),
                    shape = RoundedCornerShape(19.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Admin Demo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Super User sandbox button
                Button(
                    onClick = {
                        emailInput = "superdeveloper@gmail.com"
                        passwordInput = "devs12345"
                        nameInput = "Jane Dev"
                    },
                    modifier = Modifier.weight(1f).height(38.dp),
                    colors = ButtonDefaults.outlinedButtonColors(),
                    shape = RoundedCornerShape(19.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Developer Demo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bottom section: Skip as Guest
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = "Continue frictionless as a preview guest",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Skip and Enter",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSignUpMode) MaterialTheme.colorScheme.secondary else Color(0xFF10A37F),
                modifier = Modifier
                    .clickable {
                        viewModel.loginAsGuest()
                        onLoginSuccess()
                    }
                    .padding(8.dp)
                    .testTag("skip_login_button")
            )
        }
    }
}
