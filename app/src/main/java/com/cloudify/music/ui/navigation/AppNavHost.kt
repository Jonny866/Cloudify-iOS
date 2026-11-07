package com.cloudify.music.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cloudify.music.core.theme.DarkSurface
import com.cloudify.music.core.theme.DeepBlack
import com.cloudify.music.core.theme.TextMuted
import com.cloudify.music.core.theme.VioletLight
import com.cloudify.music.core.theme.VioletPrimary
import com.cloudify.music.domain.repository.MusicRepository
import com.cloudify.music.domain.repository.UserRepository
import com.cloudify.music.service.MusicPlayerController
import com.cloudify.music.service.TrackDownloadManager
import com.cloudify.music.ui.components.MiniPlayer
import com.cloudify.music.ui.screens.artist.ArtistScreen
import com.cloudify.music.ui.screens.artist.ArtistViewModel
import com.cloudify.music.ui.screens.auth.ProfileAndFriendsScreen
import com.cloudify.music.ui.screens.home.HomeScreen
import com.cloudify.music.ui.screens.home.HomeViewModel
import com.cloudify.music.ui.screens.library.LibraryScreen
import com.cloudify.music.ui.screens.library.LibraryViewModel
import com.cloudify.music.ui.screens.player.FullPlayerScreen
import com.cloudify.music.ui.screens.search.SearchScreen
import com.cloudify.music.ui.screens.search.SearchViewModel
import com.cloudify.music.ui.screens.stats.StatsScreen
import com.cloudify.music.ui.screens.stats.StatsViewModel
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    musicRepository: MusicRepository,
    userRepository: UserRepository,
    playerController: MusicPlayerController
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val playerState by playerController.state.collectAsState()
    var isFullPlayerVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val homeViewModel = remember { HomeViewModel(musicRepository, userRepository) }
    val searchViewModel = remember { SearchViewModel(musicRepository) }
    val libraryViewModel = remember { LibraryViewModel(musicRepository, userRepository) }
    val statsViewModel = remember { StatsViewModel(musicRepository) }

    val downloadManager = remember { TrackDownloadManager(context, musicRepository) }

    val favoriteIds by homeViewModel.favoriteTrackIds.collectAsState()

    // Extract now-playing indicators from player state
    val currentTrackId = playerState.currentTrack?.id
    val isPlaying = playerState.isPlaying
    val isBuffering = playerState.isBuffering

    // Record real listening history and real listening duration
    LaunchedEffect(Unit) {
        playerController.onTrackPlay = { track ->
            scope.launch { musicRepository.recordPlayed(track) }
        }
        playerController.onTrackListenedSeconds = { seconds ->
            scope.launch { musicRepository.addListenedSeconds(seconds) }
        }
    }

    // Helper to navigate to the Artist screen
    val navigateToArtist: (String) -> Unit = { artistName ->
        navController.navigate(Screen.Artist.createRoute(artistName)) {
            launchSingleTop = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = DeepBlack,
            bottomBar = {
                Column {
                    // Floating Mini Player (visible when a track is chosen and full player is collapsed)
                    AnimatedVisibility(
                        visible = playerState.currentTrack != null && !isFullPlayerVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                        ) + fadeIn(animationSpec = tween(180)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(150))
                    ) {
                        MiniPlayer(
                            playerState = playerState,
                            progressFlow = playerController.progress,
                            isFavorite = favoriteIds.contains(playerState.currentTrack?.id),
                            onPlayPause = { playerController.togglePlayPause() },
                            onNext = { playerController.next() },
                            onFavoriteToggle = {
                                playerState.currentTrack?.let { homeViewModel.toggleFavorite(it) }
                            },
                            onClick = { isFullPlayerVisible = true }
                        )
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        containerColor = DarkSurface,
                        tonalElevation = 8.dp
                    ) {
                        BottomNavItem.items.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    val isFromDetail = currentRoute?.startsWith("artist") == true || currentRoute?.startsWith("auth") == true
                                    if (isFromDetail) {
                                        val popped = navController.popBackStack(item.route, inclusive = false)
                                        if (!popped) {
                                            navController.navigate(item.route) {
                                                popUpTo(Screen.Home.route) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    } else if (item.route == Screen.Home.route) {
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Home.route) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    } else if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title
                                    )
                                },
                                label = { Text(item.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = VioletPrimary,
                                    selectedTextColor = VioletLight,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                    indicatorColor = Color(0x338B5CF6)
                                )
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(paddingValues),
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
                popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                popExitTransition = { fadeOut(animationSpec = tween(180)) }
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onTrackClick = { track, list ->
                            playerController.playTrack(track, list, "Рекомендации")
                        },
                        onWaveClick = { tracks ->
                            if (tracks.isNotEmpty()) {
                                playerController.playTrack(tracks[0], tracks, "Моя волна")
                            }
                        },
                        onProfileClick = {
                            navController.navigate(Screen.Auth.route)
                        },
                        onArtistClick = navigateToArtist,
                        onLikedTracksClick = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onDownloadTrack = { track -> downloadManager.downloadTrack(track) }
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        viewModel = searchViewModel,
                        onTrackClick = { track, list ->
                            playerController.playTrack(track, list, "Поиск")
                        }
                    )
                }

                composable(Screen.Library.route) {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onTrackClick = { track, list, source ->
                            playerController.playTrack(track, list, source)
                        },
                        onDownloadTrack = { track -> downloadManager.downloadTrack(track) },
                        onDownloadAll = { tracks -> downloadManager.downloadAllTracks(tracks) },
                        onGoToArtist = navigateToArtist
                    )
                }

                composable(Screen.Stats.route) {
                    StatsScreen(viewModel = statsViewModel)
                }

                composable(Screen.Auth.route) {
                    ProfileAndFriendsScreen(
                        userRepository = userRepository,
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    route = Screen.Artist.route,
                    arguments = listOf(
                        navArgument("artistName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val artistName = backStackEntry.arguments?.getString("artistName")
                        ?.let { Uri.decode(it) } ?: return@composable

                    val artistViewModel = remember(artistName) {
                        ArtistViewModel(musicRepository).apply {
                            loadArtist(artistName)
                        }
                    }

                    ArtistScreen(
                        viewModel = artistViewModel,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onBack = { navController.popBackStack() },
                        onTrackClick = { track, list ->
                            playerController.playTrack(track, list, artistName)
                        },
                        onDownloadTrack = { track -> downloadManager.downloadTrack(track) }
                    )
                }
            }
        }

        // Full Screen Player Overlay with smooth bottom-up slide
        AnimatedVisibility(
            visible = isFullPlayerVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 380f
                )
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            FullPlayerScreen(
                playerState = playerState,
                progressFlow = playerController.progress,
                isFavorite = favoriteIds.contains(playerState.currentTrack?.id),
                onDismiss = { isFullPlayerVisible = false },
                onPlayPause = { playerController.togglePlayPause() },
                onNext = { playerController.next() },
                onPrevious = { playerController.previous() },
                onSeek = { playerController.seekTo(it) },
                onShuffleToggle = { playerController.toggleShuffle() },
                onRepeatToggle = { playerController.toggleRepeat() },
                onFavoriteToggle = { homeViewModel.toggleFavorite(it) },
                onVolumeChange = { playerController.setVolume(it) },
                onSetSleepTimer = { playerController.setSleepTimer(it) },
                onCancelSleepTimer = { playerController.cancelSleepTimer() },
                onToggleVideoMode = { playerController.toggleVideoMode() },
                onDownloadTrack = { track -> downloadManager.downloadTrack(track) },
                onArtistClick = { artistName ->
                    isFullPlayerVisible = false
                    navigateToArtist(artistName)
                }
            )
        }
    }
}
