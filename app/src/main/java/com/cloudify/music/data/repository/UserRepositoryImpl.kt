package com.cloudify.music.data.repository

import android.content.Context
import com.cloudify.music.core.util.AppIconManager
import com.cloudify.music.domain.model.UserProfile
import com.cloudify.music.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class UserRepositoryImpl(private val context: Context) : UserRepository {

    private val prefs = context.getSharedPreferences("cloudify_user_prefs", Context.MODE_PRIVATE)

    private val _currentUserFlow = MutableStateFlow(loadUserFromPrefs())
    val currentUserFlow = _currentUserFlow.asStateFlow()

    private val _audioQualityFlow = MutableStateFlow(
        prefs.getString("audio_quality", "Высокое (320 kbps)") ?: "Высокое (320 kbps)"
    )

    private val _cachingEnabledFlow = MutableStateFlow(
        prefs.getBoolean("is_caching_enabled", true)
    )

    private val _themeColorFlow = MutableStateFlow(
        prefs.getString("app_theme_color", "violet") ?: "violet"
    )

    private val _importCompletedFlow = MutableStateFlow(
        prefs.getBoolean("has_imported_tracks", false)
    )

    init {
        AppIconManager.setAppIcon(context, _themeColorFlow.value)
    }

    override fun getCurrentUser(): Flow<UserProfile> = _currentUserFlow

    override suspend fun setLoggedInUser(profile: UserProfile) {
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("user_id", profile.id)
            .putString("user_name", profile.name)
            .putString("user_email", profile.email)
            .putString("avatar_url", profile.avatarUrl)
            .apply()

        _currentUserFlow.value = profile.copy(isLoggedIn = true)
    }

    override suspend fun signOut() {
        prefs.edit()
            .putBoolean("is_logged_in", false)
            .remove("user_id")
            .remove("user_name")
            .remove("user_email")
            .remove("avatar_url")
            .apply()
        _currentUserFlow.value = UserProfile(isLoggedIn = false)
    }

    override fun getAudioQuality(): Flow<String> = _audioQualityFlow.asStateFlow()

    override suspend fun setAudioQuality(quality: String) {
        prefs.edit().putString("audio_quality", quality).apply()
        _audioQualityFlow.value = quality
    }

    override fun isCachingEnabled(): Flow<Boolean> = _cachingEnabledFlow.asStateFlow()

    override suspend fun setCachingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_caching_enabled", enabled).apply()
        _cachingEnabledFlow.value = enabled
    }

    override fun getCacheSizeMb(): Double {
        val cacheDir = File(context.cacheDir, "cloudify_audio_cache")
        if (!cacheDir.exists()) return 0.0
        val bytes = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        return Math.round((bytes / (1024.0 * 1024.0)) * 10.0) / 10.0
    }

    override fun clearAudioCache() {
        val cacheDir = File(context.cacheDir, "cloudify_audio_cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
    }

    override fun getThemeColor(): Flow<String> = _themeColorFlow.asStateFlow()

    override fun getCurrentThemeColor(): String {
        return prefs.getString("app_theme_color", "violet") ?: "violet"
    }

    override suspend fun setThemeColor(color: String) {
        prefs.edit().putString("app_theme_color", color).apply()
        _themeColorFlow.value = color
        AppIconManager.setAppIcon(context, color)
    }

    override fun isImportCompleted(): Flow<Boolean> = _importCompletedFlow.asStateFlow()

    override suspend fun setImportCompleted(completed: Boolean) {
        prefs.edit().putBoolean("has_imported_tracks", completed).apply()
        _importCompletedFlow.value = completed
    }

    private fun loadUserFromPrefs(): UserProfile {
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        if (!isLoggedIn) return UserProfile()

        return UserProfile(
            id = prefs.getString("user_id", "guest") ?: "guest",
            name = prefs.getString("user_name", "Пользователь") ?: "Пользователь",
            email = prefs.getString("user_email", "") ?: "",
            avatarUrl = prefs.getString("avatar_url", "") ?: "",
            isLoggedIn = true
        )
    }
}
