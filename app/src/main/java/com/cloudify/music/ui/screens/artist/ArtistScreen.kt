package com.cloudify.music.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudify.music.core.theme.*
import com.cloudify.music.domain.model.Playlist
import com.cloudify.music.domain.model.Track
import com.cloudify.music.ui.components.PlayingEqualizerBars
import com.cloudify.music.ui.components.TrackActionBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    viewModel: ArtistViewModel,
    currentTrackId: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onDownloadTrack: (Track) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteTrackIds.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val favoriteArtists by viewModel.favoriteArtistNames.collectAsState()

    val detail = state.detail
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()

    Scaffold(
        containerColor = DeepBlack
    ) { padding ->
        if (state.isLoading || detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = VioletPrimary)
            }
        } else {
            val artist = detail.artist
            val isArtistFav = favoriteArtists.contains(artist.name)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Spotify Artist Hero Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        AsyncImage(
                            model = artist.artworkUrl,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dark Gradient Overlay for legible typography
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.4f),
                                            Color.Transparent,
                                            DeepBlack.copy(alpha = 0.95f),
                                            DeepBlack
                                        )
                                    )
                                )
                        )

                        // Back Arrow
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(16.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Назад",
                                tint = TextPrimary
                            )
                        }

                        // Artist Name & Verification Badge
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = artist.name,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Верифицирован",
                                    tint = VioletLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Подтверждено Cloudify",
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Monthly Listeners
                item {
                    Text(
                        text = "Слушателей за месяц: ${artist.monthlyListeners}",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Action Bar (Follow/Favorite, Shuffle, Big Play Button)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AsyncImage(
                                model = artist.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, DarkBorder, CircleShape)
                            )

                            // Subscribe / In Favorites Pill Button
                            Button(
                                onClick = { viewModel.toggleArtistFavorite() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isArtistFav) Color(0xFF282828) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(20.dp),
                                border = if (!isArtistFav) ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.linearGradient(listOf(TextSecondary, TextSecondary))
                                ) else null,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = if (isArtistFav) "В избранном" else "Подписаться",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Опции",
                                    tint = TextMuted
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (detail.allTracks.isNotEmpty()) {
                                        val shuffled = detail.allTracks.shuffled()
                                        onTrackClick(shuffled[0], shuffled)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Перемешать",
                                    tint = VioletLight,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Big Violet Play Button
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(VioletPrimary)
                                    .clickable {
                                        if (detail.popularTracks.isNotEmpty()) {
                                            onTrackClick(detail.popularTracks[0], detail.popularTracks)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Слушать",
                                    tint = DeepBlack,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }

                // New Release Banner
                if (detail.newRelease != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .clickable { onTrackClick(detail.newRelease, detail.allTracks) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = detail.newRelease.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Column {
                                    Text(
                                        text = "Послушать новый трек",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = detail.newRelease.title,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = TextMuted
                            )
                        }
                    }
                }

                // Section: Popular Tracks (Numbered List 1..5)
                item {
                    Text(
                        text = "Популярное",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                itemsIndexed(
                    items = detail.popularTracks,
                    key = { index, track -> "popular_${track.id}_$index" },
                    contentType = { _, _ -> "artist_track" }
                ) { index, track ->
                    val isCur = currentTrackId == track.id
                    val isFav = favoriteIds.contains(track.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track, detail.popularTracks) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Track number or playing equalizer
                        Box(
                            modifier = Modifier.width(24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (isCur && isPlaying) {
                                PlayingEqualizerBars(isPlaying = true)
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isCur) VioletLight else TextMuted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isCur) VioletLight else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (downloadedIds.contains(track.id)) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowCircleDown,
                                        contentDescription = "Скачано",
                                        tint = VioletLight,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(DarkBorder)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("E", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = track.playsCount,
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.toggleTrackFavorite(track) }) {
                            Icon(
                                imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "В избранное",
                                tint = if (isFav) AccentPink else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = { actionTrack = track }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Опции",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Section: Albums & EPs (Альбомы)
                if (detail.albums.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Альбомы и EP",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${detail.albums.size} релиза",
                                fontSize = 12.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                        ) {
                            itemsIndexed(
                                items = detail.albums,
                                key = { index, album -> "album_${album.id}_$index" }
                            ) { _, album ->
                                ArtistAlbumCard(
                                    album = album,
                                    artistName = artist.name,
                                    onClick = { viewModel.openAlbum(album) },
                                    onPlayClick = {
                                        viewModel.playAlbumDirectly(album) { firstTrack, albumTracks ->
                                            onTrackClick(firstTrack, albumTracks)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Section: All Releases & Tracks from SoundCloud
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Все треки и релизы (SoundCloud)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                itemsIndexed(
                    items = detail.allTracks.drop(5),
                    key = { index, track -> "all_${track.id}_$index" },
                    contentType = { _, _ -> "artist_track" }
                ) { index, track ->
                    val isCur = currentTrackId == track.id
                    val isFav = favoriteIds.contains(track.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track, detail.allTracks) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isCur) VioletLight else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (downloadedIds.contains(track.id)) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowCircleDown,
                                        contentDescription = "Скачано",
                                        tint = VioletLight,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = track.artist,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.toggleTrackFavorite(track) }) {
                            Icon(
                                imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "В избранное",
                                tint = if (isFav) AccentPink else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = { actionTrack = track }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Опции",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Action Bottom Sheet
    actionTrack?.let { track ->
        TrackActionBottomSheet(
            track = track,
            isFavorite = favoriteIds.contains(track.id),
            isDownloaded = downloadedIds.contains(track.id),
            onDismiss = { actionTrack = null },
            onFavoriteToggle = { viewModel.toggleTrackFavorite(track) },
            onDownloadClick = { onDownloadTrack(track) },
            onAddToPlaylistClick = {},
            onGoToArtistClick = {}
        )
    }

    // Album Details Bottom Sheet
    selectedAlbum?.let { albumState ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeAlbum() },
            containerColor = DarkSurfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Header: Album cover, title, artist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AsyncImage(
                        model = albumState.album.artworkUrl,
                        contentDescription = albumState.album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkBorder)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = albumState.album.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${state.artistName} • Альбом",
                            fontSize = 13.sp,
                            color = VioletLight,
                            fontWeight = FontWeight.Medium
                        )
                        val count = if (albumState.tracks.isNotEmpty()) albumState.tracks.size else albumState.album.trackCount
                        Text(
                            text = "$count треков",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }

                // Action buttons: "Слушать всё" and "Перемешать"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (albumState.tracks.isNotEmpty()) {
                                onTrackClick(albumState.tracks.first(), albumState.tracks)
                                viewModel.closeAlbum()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, tint = DeepBlack, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Слушать всё", color = DeepBlack, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            if (albumState.tracks.isNotEmpty()) {
                                val shuffled = albumState.tracks.shuffled()
                                onTrackClick(shuffled.first(), shuffled)
                                viewModel.closeAlbum()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF282828))
                    ) {
                        Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 10.dp, horizontal = 20.dp))

                // Tracklist inside album
                if (albumState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = VioletPrimary, modifier = Modifier.size(28.dp))
                    }
                } else if (albumState.tracks.isEmpty()) {
                    Text(
                        text = "В этом альбоме пока нет треков",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                    ) {
                        itemsIndexed(
                            items = albumState.tracks,
                            key = { idx, trk -> "album_trk_${trk.id}_$idx" }
                        ) { idx, trk ->
                            val isCur = currentTrackId == trk.id
                            val isFav = favoriteIds.contains(trk.id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onTrackClick(trk, albumState.tracks)
                                        viewModel.closeAlbum()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = if (isCur) VioletLight else TextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(26.dp)
                                )

                                AsyncImage(
                                    model = trk.artworkUrl,
                                    contentDescription = trk.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trk.title,
                                        color = if (isCur) VioletLight else TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = trk.artist,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = { viewModel.toggleTrackFavorite(trk) }) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "В избранное",
                                        tint = if (isFav) AccentPink else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistAlbumCard(
    album: Playlist,
    artistName: String,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(142.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(142.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = album.artworkUrl,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Play floating badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(VioletPrimary)
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Слушать альбом",
                    tint = DeepBlack,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.name,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Альбом • ${album.trackCount} треков",
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
