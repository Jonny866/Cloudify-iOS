package com.cloudify.music.ui.screens.auth

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudify.music.core.theme.*
import com.cloudify.music.core.util.CloudSyncManager
import com.cloudify.music.domain.model.UserProfile
import com.cloudify.music.domain.repository.UserRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAndFriendsScreen(
    userRepository: UserRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by userRepository.getCurrentUser().collectAsState(initial = UserProfile())
    val currentTheme by userRepository.getThemeColor().collectAsState(initial = "violet")

    val audioQuality by userRepository.getAudioQuality().collectAsState(initial = "Высокое (320 kbps)")
    val isCachingEnabled by userRepository.isCachingEnabled().collectAsState(initial = true)
    var cacheSizeMb by remember { mutableDoubleStateOf(userRepository.getCacheSizeMb()) }

    var showProfileEditor by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showEmailFallbackDialog by remember { mutableStateOf(false) }
    var fallbackEmailInput by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf(currentUser.name.ifBlank { "Пользователь" }) }
    var inputEmail by remember { mutableStateOf(currentUser.email) }
    var selectedAvatar by remember { mutableStateOf(currentUser.avatarUrl) }

    fun handleGoogleAccountSelected(email: String, displayName: String? = null, photoUrl: String? = null) {
        val finalEmail = email.trim().lowercase()
        val finalName = when {
            !displayName.isNullOrBlank() -> displayName
            finalEmail.contains("@") -> finalEmail.substringBefore("@")
                .replace(".", " ")
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            else -> "Пользователь Google"
        }
        val targetUserId = "google_" + Math.abs(finalEmail.hashCode())

        Toast.makeText(context, "Подключение к облаку...", Toast.LENGTH_SHORT).show()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
            try {
                // 1. Restore from server into Room FIRST
                val cloudSync = CloudSyncManager(context)
                val cloudRes = cloudSync.restoreFromServer(targetUserId, finalEmail)

                val restoredName = if (!cloudRes.serverUserName.isNullOrBlank()) cloudRes.serverUserName else finalName
                val restoredAvatar = if (!cloudRes.serverAvatarUrl.isNullOrBlank()) cloudRes.serverAvatarUrl else (photoUrl ?: selectedAvatar)

                val profile = UserProfile(
                    id = targetUserId,
                    name = restoredName,
                    email = finalEmail,
                    avatarUrl = restoredAvatar,
                    isLoggedIn = true
                )

                if (!cloudRes.success || (cloudRes.restoredFavorites == 0 && cloudRes.restoredPlaylists == 0)) {
                    cloudSync.syncToServer(profile)
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    userRepository.setLoggedInUser(profile)
                    if (cloudRes.success && (cloudRes.restoredFavorites > 0 || cloudRes.restoredPlaylists > 0)) {
                        Toast.makeText(
                            context,
                            "Облако: Восстановлено ${cloudRes.restoredFavorites} любимых, ${cloudRes.restoredPlaylists} плейлистов!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "Вход выполнен: $restoredName!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val fallback = UserProfile(
                        id = targetUserId,
                        name = finalName,
                        email = finalEmail,
                        avatarUrl = photoUrl ?: selectedAvatar,
                        isLoggedIn = true
                    )
                    userRepository.setLoggedInUser(fallback)
                    Toast.makeText(context, "Вход выполнен: $finalName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val systemAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) {
                handleGoogleAccountSelected(accountName)
                return@rememberLauncherForActivityResult
            }
        }
        showEmailFallbackDialog = true
    }

    // Real Gallery Image Picker (No presets allowed!)
    val galleryImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val destFile = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val localUriString = destFile.toURI().toString()
                selectedAvatar = localUriString
                // Update profile immediately
                scope.launch {
                    userRepository.setLoggedInUser(
                        currentUser.copy(
                            avatarUrl = localUriString,
                            name = currentUser.name.ifBlank { "Пользователь" }
                        )
                    )
                }
                Toast.makeText(context, "Фото профиля обновлено!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка сохранения фото: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Google Sign-In setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val profile = UserProfile(
                        id = account.id ?: "google_user",
                        name = account.displayName ?: "Пользователь Google",
                        email = account.email ?: "",
                        avatarUrl = account.photoUrl?.toString() ?: selectedAvatar,
                        isLoggedIn = true
                    )
                    scope.launch {
                        userRepository.setLoggedInUser(profile)
                    }
                    return@rememberLauncherForActivityResult
                }
            } catch (e: ApiException) {
                showProfileEditor = true
            }
        } else {
            showProfileEditor = true
        }
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            TopAppBar(
                title = { Text("Профиль и Настройки", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    // Explicit Home Icon Button (Always returns to Main screen!)
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = "Главная",
                            tint = VioletPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBlack)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // User Profile Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (currentUser.isLoggedIn) {
                            Brush.linearGradient(listOf(VioletDark, Color(0xFF1E1B4B)))
                        } else {
                            Brush.linearGradient(listOf(DarkSurfaceVariant, DarkSurface))
                        }
                    )
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clickable Avatar with Camera Icon Badge to upload own photo
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .clickable { galleryImagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentUser.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = currentUser.avatarUrl,
                                contentDescription = currentUser.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(2.dp, VioletPrimary, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF282828)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddAPhoto,
                                    contentDescription = "Добавить фото",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser.name.ifBlank { "Пользователь" },
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentUser.email.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentUser.email,
                                color = VioletLight,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (currentUser.isLoggedIn) Icons.Rounded.CheckCircle else Icons.Rounded.Person,
                                contentDescription = null,
                                tint = if (currentUser.isLoggedIn) VioletLight else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (currentUser.isLoggedIn) "Профиль активен" else "Гостевой режим",
                                color = if (currentUser.isLoggedIn) Color(0xFFDDD6FE) else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Upload Avatar Button (Prominent)
            OutlinedButton(
                onClick = { galleryImagePickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(VioletPrimary, VioletLight))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = null,
                    tint = VioletLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Загрузить своё фото из галереи", fontWeight = FontWeight.SemiBold)
            }

            // Authentication Buttons
            if (!currentUser.isLoggedIn) {
                Button(
                    onClick = {
                        try {
                            val chooseIntent = android.accounts.AccountManager.newChooseAccountIntent(
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
                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
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
                            text = "G ",
                            color = DeepBlack,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Войти через Google",
                            color = DeepBlack,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showProfileEditor = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ввести имя и настроить профиль", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showProfileEditor = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletLight),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Изменить имя")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                googleSignInClient.signOut()
                                userRepository.signOut()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPink),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Выйти")
                    }
                }
            }

            // App Settings Section
            Text(
                text = "Настройки приложения",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Theme Color (Фиолетовый vs Жёлтый)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Цветовая тема", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Фиолетовый или жёлтый стиль", color = TextMuted, fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isViolet = currentTheme == "violet"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isViolet) Color(0x338B5CF6) else DarkSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isViolet) 2.dp else 1.dp,
                                color = if (isViolet) Color(0xFF8B5CF6) else DarkBorder
                            ),
                            modifier = Modifier.clickable {
                                scope.launch { userRepository.setThemeColor("violet") }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF8B5CF6))
                                )
                                Text(
                                    text = "Фиолетовый",
                                    color = if (isViolet) TextPrimary else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isViolet) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        val isYellow = currentTheme == "yellow"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isYellow) Color(0x33FACC15) else DarkSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isYellow) 2.dp else 1.dp,
                                color = if (isYellow) Color(0xFFFACC15) else DarkBorder
                            ),
                            modifier = Modifier.clickable {
                                scope.launch { userRepository.setThemeColor("yellow") }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFACC15))
                                )
                                Text(
                                    text = "Жёлтый",
                                    color = if (isYellow) TextPrimary else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isYellow) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkBorder)

                // Audio Quality (Clickable with dialog!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showQualityDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Качество звука", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Битрейт потока и формат аудио", color = TextMuted, fontSize = 12.sp)
                    }
                    Text(audioQuality, color = VioletLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = DarkBorder)

                // Caching Toggle (Real!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Кэширование на устройство", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Автосохранение для быстрого запуска ($cacheSizeMb МБ занято)", color = TextMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isCachingEnabled,
                        onCheckedChange = { scope.launch { userRepository.setCachingEnabled(it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = DeepBlack, checkedTrackColor = VioletPrimary)
                    )
                }

                if (cacheSizeMb > 0.0) {
                    TextButton(
                        onClick = {
                            userRepository.clearAudioCache()
                            cacheSizeMb = userRepository.getCacheSizeMb()
                            Toast.makeText(context, "Кэш очищен", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Очистить кэш", color = AccentPink, fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = DarkBorder)

                // App Brand & Active Theme Icon Badge
                val isYellowTheme = currentTheme.equals("yellow", ignoreCase = true)
                val accentCol = if (isYellowTheme) Color(0xFFFACC15) else VioletPrimary
                val accentLightCol = if (isYellowTheme) Color(0xFFFEF08A) else VioletLight
                val containerCol = if (isYellowTheme) Color(0x33FACC15) else Color(0x338B5CF6)
                val logoRes = if (isYellowTheme) com.cloudify.music.R.drawable.ic_cloudify_logo_yellow else com.cloudify.music.R.drawable.ic_cloudify_logo_violet

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isYellowTheme) Color(0xFF1E1A0E) else Color(0xFF13101E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = logoRes),
                            contentDescription = "Иконка приложения",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cloudify v7.9",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isYellowTheme) "Иконка: Жёлтая" else "Иконка: Фиолетовая",
                            color = if (isYellowTheme) Color(0xFFFACC15) else VioletLight,
                            fontSize = 12.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentCol.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isYellowTheme) "Жёлтая" else "Фиолетовая",
                            color = if (isYellowTheme) Color(0xFFFACC15) else VioletLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder)

                // Cloud Server Sync (Cloudify Cloud Backup)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Облачная синхронизация",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Сохранение треков и плейлистов в вашем аккаунте",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = accentCol,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val cloud = CloudSyncManager(context)
                                    val ok = cloud.syncToServer(currentUser)
                                    if (ok) {
                                        Toast.makeText(context, "Плейлисты и треки сохранены в облаке!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Ошибка связи с сервером", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = containerCol),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Rounded.CloudUpload, contentDescription = null, tint = accentLightCol, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("В облако", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                if (currentUser.email.isBlank()) {
                                    showEmailFallbackDialog = true
                                } else {
                                    scope.launch {
                                        Toast.makeText(context, "Запрос к облаку...", Toast.LENGTH_SHORT).show()
                                        val cloud = CloudSyncManager(context)
                                        val res = cloud.restoreFromServer(currentUser.id, currentUser.email)
                                        if (res.success) {
                                            Toast.makeText(context, "Восстановлено: ${res.restoredFavorites} любимых, ${res.restoredPlaylists} плейлистов", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, res.message ?: "Нет сохраненных данных в облаке", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = containerCol),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Rounded.CloudDownload, contentDescription = null, tint = accentLightCol, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Из облака", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Audio Quality Dialog
    if (showQualityDialog) {
        val options = listOf(
            "Высокое (320 kbps)" to "Максимальное качество звука без сжатия",
            "Стандартное (128 kbps)" to "Оптимальный баланс качества и скорости",
            "Экономия трафика (64 kbps)" to "Быстрый старт при слабом интернете"
        )
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Качество звука", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { (title, desc) ->
                        val isSelected = audioQuality == title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch { userRepository.setAudioQuality(title) }
                                    showQualityDialog = false
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    scope.launch { userRepository.setAudioQuality(title) }
                                    showQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = VioletPrimary)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(desc, color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Закрыть", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }

    // Profile Setup / Name Edit Dialog
    if (showProfileEditor) {
        AlertDialog(
            onDismissRequest = { showProfileEditor = false },
            title = {
                Text(
                    text = if (currentUser.isLoggedIn) "Редактировать имя" else "Вход в Cloudify",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Ваше имя") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = VioletPrimary,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it },
                        label = { Text("Email (необязательно)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = VioletPrimary,
                            unfocusedBorderColor = DarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = inputName.trim().ifBlank { "Пользователь" }
                        val profile = UserProfile(
                            id = currentUser.id.ifBlank { "user_" + System.currentTimeMillis() },
                            name = finalName,
                            email = inputEmail.trim(),
                            avatarUrl = currentUser.avatarUrl,
                            isLoggedIn = true
                        )
                        scope.launch {
                            userRepository.setLoggedInUser(profile)
                        }
                        showProfileEditor = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                ) {
                    Text("Сохранить", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileEditor = false }) {
                    Text("Отмена", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceVariant
        )
    }

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
                        "Укажите ваш Google Email для синхронизации и резервного сохранения треков на сервере:",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = fallbackEmailInput,
                        onValueChange = { fallbackEmailInput = it },
                        label = { Text("Google Email") },
                        placeholder = { Text("например: myaccount@gmail.com") },
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
                            handleGoogleAccountSelected(fallbackEmailInput.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Войти", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailFallbackDialog = false }) {
                    Text("Отмена", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceVariant,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
