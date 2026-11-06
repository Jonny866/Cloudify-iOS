package com.cloudify.music.domain.repository

import com.cloudify.music.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getCurrentUser(): Flow<UserProfile>
    suspend fun setLoggedInUser(profile: UserProfile)
    suspend fun signOut()

    // Real Audio Quality Settings
    fun getAudioQuality(): Flow<String>
    suspend fun setAudioQuality(quality: String)

    // Real Caching Settings
    fun isCachingEnabled(): Flow<Boolean>
    suspend fun setCachingEnabled(enabled: Boolean)

    fun getCacheSizeMb(): Double
    fun clearAudioCache()

    // Dynamic Theme Color (violet / yellow)
    fun getThemeColor(): Flow<String>
    fun getCurrentThemeColor(): String
    suspend fun setThemeColor(color: String)

    // Track whether import has been completed once
    fun isImportCompleted(): Flow<Boolean>
    suspend fun setImportCompleted(completed: Boolean)
}
