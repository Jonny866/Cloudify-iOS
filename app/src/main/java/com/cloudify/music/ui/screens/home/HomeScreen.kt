package com.cloudify.music.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudify.music.core.theme.*
import com.cloudify.music.domain.model.Artist
import com.cloudify.music.domain.model.Track
import com.cloudify.music.ui.components.SectionHeader
import com.cloudify.music.ui.components.TrackActionBottomSheet
import com.cloudify.music.ui.components.TrackListItem

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    currentTrackId: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    onWaveClick: (List<Track>) -> Unit,
    onProfileClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    onLikedTracksClick: () -> Unit = {},
    onDownloadTrack: (Track) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteTrackIds.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    val pagerState = rememberPagerState(pageCount = { state.favoriteReleases.size.coerceAtLeast(1) })
    val uniqueRecent = remember(state.recentTracks) {
        state.recentTracks.distinctBy { it.id }.take(4)
    }
    val uniquePopular = remember(state.popularForYou) {
        state.popularForYou.distinctBy { it.id }
    }
    val uniqueArtists = remember(state.popularArtists) {
        state.popularArtists.distinctBy { it.name }
    }

    Scaffold(
        containerColor = DeepBlack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Top Bar: Profile Avatar & Filter Pills (Exact Spotify Style)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Profile Avatar
                    if (state.user.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = state.user.avatarUrl,
                            contentDescription = "Профиль",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DarkSurface)
                                .clickable(onClick = onProfileClick)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF282828))
                                .clickable(onClick = onProfileClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "Профиль",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Pills: Все, Музыка, Подкасты, Аудиокниги
                    val filterTabs = listOf("Все", "Музыка", "Подкасты", "Аудиокниги")
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filterTabs, key = { it }) { tab ->
                            val isSelected = state.activeFilterTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) VioletPrimary else Color(0xFF2A2A2A))
                                    .clickable { viewModel.setFilterTab(tab) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = tab,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Cloudify Theme Brand Logo
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isYellowTheme) Color(0xFF1E1A0E) else Color(0xFF13101E))
                            .clickable(onClick = onProfileClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = appLogoResource),
                            contentDescription = "Cloudify",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Quick Access 2-Column Grid (8 Cards)
            if (state.quickAccess.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.quickAccess.chunked(2).forEach { rowCards ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCards.forEach { card ->
                                    QuickAccessCard(
                                        item = card,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            if (card.isFavorites) {
                                                onLikedTracksClick()
                                            } else if (card.tracks.isNotEmpty()) {
                                                onTrackClick(card.tracks[0], card.tracks)
                                            } else {
                                                onArtistClick(card.title)
                                            }
                                        }
                                    )
                                }
                                if (rowCards.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Featured "Новый релиз исполнителя" Block (HorizontalPager for multiple favorite artists)
            val releases = state.favoriteReleases
            if (releases.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        if (releases.size == 1) {
                            val release = releases[0]
                            FeaturedReleaseCard(
                                artistName = release.artist.name,
                                artistArtwork = release.artist.artworkUrl,
                                track = release.track,
                                isFavorite = favoriteIds.contains(release.track.id),
                                onArtistClick = onArtistClick,
                                onTrackClick = onTrackClick,
                                onFavoriteToggle = { viewModel.toggleFavorite(release.track) },
                                onMoreClick = { actionTrack = release.track }
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxWidth()
                                ) { page ->
                                    val release = releases.getOrNull(page) ?: return@HorizontalPager
                                    FeaturedReleaseCard(
                                        artistName = release.artist.name,
                                        artistArtwork = release.artist.artworkUrl,
                                        track = release.track,
                                        isFavorite = favoriteIds.contains(release.track.id),
                                        onArtistClick = onArtistClick,
                                        onTrackClick = onTrackClick,
                                        onFavoriteToggle = { viewModel.toggleFavorite(release.track) },
                                        onMoreClick = { actionTrack = release.track }
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(releases.size) { index ->
                                        val isSelected = pagerState.currentPage == index
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 3.dp)
                                                .size(if (isSelected) 8.dp else 5.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) VioletPrimary else TextMuted.copy(alpha = 0.4f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (state.newReleaseTrack != null) {
                item {
                    val track = state.newReleaseTrack
                    if (track != null) {
                        val artistName = state.newReleaseArtist?.name ?: track.artist
                        val artistArtwork = state.newReleaseArtist?.artworkUrl ?: track.artworkUrl
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            FeaturedReleaseCard(
                                artistName = artistName,
                                artistArtwork = artistArtwork,
                                track = track,
                                isFavorite = favoriteIds.contains(track.id),
                                onArtistClick = onArtistClick,
                                onTrackClick = onTrackClick,
                                onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                onMoreClick = { actionTrack = track }
                            )
                        }
                    }
                }
            }
            else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Новый релиз исполнителя",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = VioletLight,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Чтобы отобразить информацию, послушайте несколько любимых треков и артистов",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
            // "Недавно прослушанные треки"
            if (uniqueRecent.isNotEmpty()) {
                item {
                    SectionHeader(title = "Недавно прослушанные треки")
                }

                itemsIndexed(
                    items = uniqueRecent,
                    key = { index, track -> "recent_${track.id}_$index" },
                    contentType = { _, _ -> "track" }
                ) { _, track ->
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        TrackListItem(
                            track = track,
                            isFavorite = favoriteIds.contains(track.id),
                            isDownloaded = downloadedIds.contains(track.id),
                            isCurrentTrack = currentTrackId == track.id,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onClick = { onTrackClick(track, uniqueRecent) },
                            onFavoriteToggle = { viewModel.toggleFavorite(track) },
                            onMoreClick = { actionTrack = track }
                        )
                    }
                }
            }

            // Популярное для тебя
            if (uniquePopular.isNotEmpty()) {
                item {
                    SectionHeader(title = "Популярно для тебя")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = uniquePopular,
                            key = { index, track -> "popular_${track.id}_$index" },
                            contentType = { _, _ -> "popular_track" }
                        ) { _, track ->
                            PopularCard(
                                track = track,
                                onClick = { onTrackClick(track, uniquePopular) }
                            )
                        }
                    }
                }
            }

            // Популярные исполнители
            if (uniqueArtists.isNotEmpty()) {
                item {
                    SectionHeader(title = "Популярные исполнители")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = uniqueArtists,
                            key = { index, artist -> "artist_${artist.name}_$index" },
                            contentType = { _, _ -> "artist" }
                        ) { _, artist ->
                            ArtistCard(
                                artist = artist,
                                onClick = { onArtistClick(artist.name) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Track Action Bottom Sheet (from 3-dots)
    actionTrack?.let { track ->
        TrackActionBottomSheet(
            track = track,
            isFavorite = favoriteIds.contains(track.id),
            isDownloaded = downloadedIds.contains(track.id),
            onDismiss = { actionTrack = null },
            onFavoriteToggle = { viewModel.toggleFavorite(track) },
            onDownloadClick = { onDownloadTrack(track) },
            onAddToPlaylistClick = {},
            onGoToArtistClick = onArtistClick
        )
    }
}

@Composable
fun QuickAccessCard(
    item: QuickAccessItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF242424))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.isFavorites) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF450AF5), Color(0xFFC4EFD9))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .background(DarkSurface)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
    }
}

@Composable
fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(105.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = artist.name.take(1).uppercase(),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = VioletLight
            )
            AsyncImage(
                model = artist.artworkUrl,
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Исполнитель",
            color = TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PopularCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = track.getDisplayArtwork(LocalContext.current),
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FeaturedReleaseCard(
    artistName: String,
    artistArtwork: String,
    track: Track,
    isFavorite: Boolean,
    onArtistClick: (String) -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onFavoriteToggle: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Header with artist avatar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clickable { onArtistClick(artistName) }
                .padding(bottom = 10.dp)
        ) {
            AsyncImage(
                model = artistArtwork.ifBlank { track.artworkUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
            )
            Column {
                Text(
                    text = "Новый релиз исполнителя",
                    fontSize = 12.sp,
                    color = TextMuted
                )
                Text(
                    text = artistName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // Split Big Release Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceVariant)
                .clickable { onTrackClick(track, listOf(track)) }
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = track.getDisplayArtwork(LocalContext.current),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Сингл",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onMoreClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Опции",
                                tint = TextSecondary
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onFavoriteToggle,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "В любимые",
                                    tint = if (isFavorite) AccentPink else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(VioletPrimary)
                                    .clickable { onTrackClick(track, listOf(track)) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Слушать",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
