package com.cloudify.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.cloudify.music.core.database.AppDatabase
import com.cloudify.music.core.network.CloudifyApi
import com.cloudify.music.data.repository.MusicRepositoryImpl
import com.cloudify.music.data.repository.UserRepositoryImpl
import com.cloudify.music.domain.repository.MusicRepository
import com.cloudify.music.domain.repository.UserRepository
import com.cloudify.music.service.MusicPlayerController

class CloudifyApp : Application(), ImageLoaderFactory {

    lateinit var database: AppDatabase
        private set

    lateinit var api: CloudifyApi
        private set

    lateinit var musicRepository: MusicRepository
        private set

    lateinit var userRepository: UserRepository
        private set

    lateinit var playerController: MusicPlayerController
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        api = CloudifyApi.create()
        musicRepository = MusicRepositoryImpl(api, database.musicDao(), this)
        userRepository = UserRepositoryImpl(this)
        playerController = MusicPlayerController.getInstance(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
