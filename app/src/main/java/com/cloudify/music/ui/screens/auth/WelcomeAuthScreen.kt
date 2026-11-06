package com.cloudify.music.ui.screens.auth

import android.accounts.AccountManager
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudify.music.core.theme.*
import com.cloudify.music.core.util.CloudSyncManager
import com.cloudify.music.domain.model.UserProfile
import com.cloudify.music.domain.repository.UserRepository
import kotlinx.coroutines.*

@Composable
fun WelcomeAuthScreen(
    userRepository: UserRepository,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isSignUpMode by remember { mutableStateOf(false) } // Default to "Вход" for returning users
    var inputName by remember { mutableStateOf("") }
    var inputEmail by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var loginStatusText by remember { mutableStateOf("") }

    var showEmailFallbackDialog by remember { mutableStateOf(false) }
    var fallbackEmailInput by remember { mutableStateOf("") }

    /**
     * Bulletproof Login + Cloud Restore:
     * Runs in an independent CoroutineScope(Dispatchers.IO + SupervisorJob())
     * so it CANNOT be cancelled when WelcomeAuthScreen leaves composition.
     * Restores all favorites/playlists into Room FIRST, then sets loggedInUser.
     */
    fun performLoginWithRestore(email: String, displayName: String? = null, photoUrl: String? = null) {
        val finalEmail = email.trim().lowercase()
        if (finalEmail.isBlank()) {
            Toast.makeText(context, "Пожалуйста, укажите Email", Toast.LENGTH_SHORT).show()
            return
        }

        val finalName = when {
            !displayName.isNullOrBlank() -> displayName
            finalEmail.contains("@") -> finalEmail.substringBefore("@")
                .replace(".", " ")
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            else -> "Пользователь Cloudify"
        }

        isLoggingIn = true
        loginStatusText = "Подключение к серверу и поиск треков..."

        // Global IO CoroutineScope so unmounting WelcomeAuthScreen cannot cancel network/DB operations!
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val cloudSync = CloudSyncManager(context)
                val targetUserId = "google_" + Math.abs(finalEmail.hashCode())

                android.util.Log.d("CloudifyAuth", "Starting cloud restore for $finalEmail (id=$targetUserId)...")
                val cloudRes = cloudSync.restoreFromServer(
                    userId = targetUserId,
                    email = finalEmail
                )

                val restoredName = if (!cloudRes.serverUserName.isNullOrBlank()) cloudRes.serverUserName else finalName
                val restoredAvatar = if (!cloudRes.serverAvatarUrl.isNullOrBlank()) cloudRes.serverAvatarUrl else (photoUrl ?: "")

                val profile = UserProfile(
                    id = targetUserId,
                    name = restoredName,
                    email = finalEmail,
                    avatarUrl = restoredAvatar,
                    isLoggedIn = true
                )

                if (!cloudRes.success || (cloudRes.restoredFavorites == 0 && cloudRes.restoredPlaylists == 0)) {
                    // New account: create user profile on server
                    cloudSync.syncToServer(profile)
                }

                withContext(Dispatchers.Main) {
                    if (cloudRes.success && (cloudRes.restoredFavorites > 0 || cloudRes.restoredPlaylists > 0)) {
                        Toast.makeText(
                            context,
                            "Облако: Восстановлено ${cloudRes.restoredFavorites} любимых треков!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "Вход выполнен: $restoredName!", Toast.LENGTH_SHORT).show()
                    }

                    // Room database is already populated, now activate session
                    userRepository.setLoggedInUser(profile)
                    isLoggingIn = false
                    onLoginSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("CloudifyAuth", "Restore error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val fallbackProfile = UserProfile(
                        id = "google_" + Math.abs(finalEmail.hashCode()),
                        name = finalName,
                        email = finalEmail,
                        avatarUrl = photoUrl ?: "",
                        isLoggedIn = true
                    )
                    userRepository.setLoggedInUser(fallbackProfile)
                    isLoggingIn = false
                    Toast.makeText(context, "Вход выполнен (офлайн)", Toast.LENGTH_SHORT).show()
                    onLoginSuccess()
                }
            }
        }
    }

    // Android Native System Account Picker (Requires ZERO Firebase / Google Cloud setup)
    val systemAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) {
                performLoginWithRestore(accountName)
                return@rememberLauncherForActivityResult
            }
        }
        showEmailFallbackDialog = true
    }

    val isYellow = isYellowTheme
    val accentGlow = if (isYellow) Color(0x35FACC15) else Color(0x358B5CF6)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        // Ambient background aura
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .size(320.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(accentGlow, Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // App Logo
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF13101E)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = appLogoResource),
                    contentDescription = "Cloudify Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Cloudify",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )

            Text(
                text = "Музыка без границ и блокировок",
                color = if (isYellow) Color(0xFFFDE047) else VioletLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Mode Selector Tabs: Вход / Регистрация
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isSignUpMode) VioletPrimary else Color.Transparent)
                            .clickable { isSignUpMode = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Вход",
                            color = if (!isSignUpMode) (if (isYellow) DeepBlack else Color.White) else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSignUpMode) VioletPrimary else Color.Transparent)
                            .clickable { isSignUpMode = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Регистрация",
                            color = if (isSignUpMode) (if (isYellow) DeepBlack else Color.White) else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Card Container
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Google Button (1-Click Google Sign-In & Instant Cloud Restore)
                    Button(
                        onClick = {
                            try {
                                val chooseIntent = AccountManager.newChooseAccountIntent(
                                    null,
                                    null,
                                    arrayOf("com.google"),
                                    null,
                                    null,
                                    null,
                                    null
                                )
                                systemAccountPickerLauncher.launch(chooseIntent)
                            } catch (e: Exception) {
                                showEmailFallbackDialog = true
                            }
                        },
                        enabled = !isLoggingIn,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "G",
                                color = Color(0xFF4285F4),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isSignUpMode) "Продолжить с Google" else "Войти через Google",
                                color = Color(0xFF1F1F1F),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Divider "или"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = DarkBorder)
                        Text(
                            text = if (isSignUpMode) "  ИЛИ РУЧНАЯ РЕГИСТРАЦИЯ  " else "  ИЛИ ВХОД ПО EMAIL  ",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = DarkBorder)
                    }

                    if (isSignUpMode) {
                        // Registration: Name + Email
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("Ваше имя или никнейм") },
                            placeholder = { Text("Например: Александр") },
                            singleLine = true,
                            enabled = !isLoggingIn,
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Person, contentDescription = null, tint = VioletLight)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = VioletPrimary,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = inputEmail,
                            onValueChange = { inputEmail = it },
                            label = { Text("Email (для облака и плейлистов)") },
                            placeholder = { Text("myaccount@gmail.com") },
                            singleLine = true,
                            enabled = !isLoggingIn,
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Email, contentDescription = null, tint = TextMuted)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = VioletPrimary,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Submit Registration
                        Button(
                            onClick = {
                                val finalName = inputName.trim().ifBlank { "Слушатель Cloudify" }
                                val finalEmail = inputEmail.trim().lowercase()

                                if (finalEmail.isNotBlank()) {
                                    performLoginWithRestore(finalEmail, displayName = finalName)
                                } else {
                                    // Offline account
                                    val profile = UserProfile(
                                        id = "user_" + System.currentTimeMillis(),
                                        name = finalName,
                                        isLoggedIn = true
                                    )
                                    scope.launch {
                                        userRepository.setLoggedInUser(profile)
                                        Toast.makeText(context, "Добро пожаловать, $finalName!", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    }
                                }
                            },
                            enabled = !isLoggingIn,
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Создать профиль и начать",
                                color = if (isYellow) DeepBlack else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Login Mode: Enter Email to restore everything from server!
                        OutlinedTextField(
                            value = inputEmail,
                            onValueChange = { inputEmail = it },
                            label = { Text("Email вашего аккаунта") },
                            placeholder = { Text("например: jonnylermentov02@gmail.com") },
                            singleLine = true,
                            enabled = !isLoggingIn,
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Email, contentDescription = null, tint = VioletLight)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = VioletPrimary,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Submit Login & Restore
                        Button(
                            onClick = {
                                if (inputEmail.isBlank()) {
                                    Toast.makeText(context, "Введите ваш Email для входа", Toast.LENGTH_SHORT).show()
                                } else {
                                    performLoginWithRestore(inputEmail.trim())
                                }
                            },
                            enabled = !isLoggingIn,
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Войти и восстановить музыку",
                                color = if (isYellow) DeepBlack else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Fast guest login
                    TextButton(
                        onClick = {
                            val profile = UserProfile(
                                id = "guest_" + System.currentTimeMillis(),
                                name = "Гость",
                                isLoggedIn = true
                            )
                            scope.launch {
                                userRepository.setLoggedInUser(profile)
                                onLoginSuccess()
                            }
                        },
                        enabled = !isLoggingIn,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Войти как гость без сохранения", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Email Fallback Dialog
        if (showEmailFallbackDialog) {
            AlertDialog(
                onDismissRequest = { showEmailFallbackDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("G", color = Color(0xFF4285F4), fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Вход через Google", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Укажите ваш Google Email для входа и восстановления библиотеки треков с сервера:",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = fallbackEmailInput,
                            onValueChange = { fallbackEmailInput = it },
                            label = { Text("Google Email") },
                            placeholder = { Text("например: jonnylermentov02@gmail.com") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = VioletPrimary,
                                unfocusedBorderColor = DarkBorder,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (fallbackEmailInput.isNotBlank()) {
                                showEmailFallbackDialog = false
                                performLoginWithRestore(fallbackEmailInput.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Войти", color = if (isYellow) DeepBlack else Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmailFallbackDialog = false }) {
                        Text("Отмена", color = TextMuted)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Fullscreen Loading Overlay during cloud sync
        if (isLoggingIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = VioletPrimary,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "Синхронизация Cloudify",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = loginStatusText,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
