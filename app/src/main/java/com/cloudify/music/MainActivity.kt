package com.cloudify.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.cloudify.music.core.theme.CloudifyTheme
import com.cloudify.music.core.theme.DeepBlack
import com.cloudify.music.core.util.CloudSyncManager
import com.cloudify.music.ui.navigation.AppNavHost
import com.cloudify.music.ui.screens.auth.WelcomeAuthScreen
import com.cloudify.music.ui.screens.splash.SplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission result
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as CloudifyApp
        val initialTheme = app.userRepository.getCurrentThemeColor()
        if (initialTheme.equals("yellow", ignoreCase = true)) {
            setTheme(R.style.Theme_Cloudify_Yellow)
        } else {
            setTheme(R.style.Theme_Cloudify)
        }
        super.onCreate(savedInstanceState)

        askNotificationPermission()

        setContent {
            val initialTheme = remember { app.userRepository.getCurrentThemeColor() }
            val themeColor by app.userRepository.getThemeColor().collectAsState(initial = initialTheme)
            val currentUser by app.userRepository.getCurrentUser().collectAsState(initial = null)
            var isSplashVisible by remember { mutableStateOf(true) }

            // Automatic background self-healing cloud sync on startup for logged-in users
            LaunchedEffect(currentUser?.email) {
                val user = currentUser
                if (user != null && user.isLoggedIn && user.email.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val localCount = app.database.musicDao().getAllFavorites().first().size
                            if (localCount == 0) {
                                CloudSyncManager(this@MainActivity).restoreFromServer(user.id, user.email)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            CloudifyTheme(themeColor = themeColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepBlack
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (currentUser != null) {
                            if (currentUser!!.isLoggedIn) {
                                AppNavHost(
                                    musicRepository = app.musicRepository,
                                    userRepository = app.userRepository,
                                    playerController = app.playerController
                                )
                            } else {
                                WelcomeAuthScreen(
                                    userRepository = app.userRepository,
                                    onLoginSuccess = {}
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isSplashVisible,
                            enter = fadeIn(),
                            exit = fadeOut(animationSpec = tween(350))
                        ) {
                            SplashScreen(
                                themeColor = themeColor,
                                onFinish = { isSplashVisible = false }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
