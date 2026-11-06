package com.cloudify.music.ui.screens.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudify.music.core.theme.*
import com.cloudify.music.domain.model.Playlist
import com.cloudify.music.domain.model.Track
import com.cloudify.music.ui.components.TrackActionBottomSheet
import com.cloudify.music.ui.components.TrackListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    currentTrackId: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTrackClick: (Track, List<Track>, String) -> Unit,
    onDownloadTrack: (Track) -> Unit = {},
    onDownloadAll: (List<Track>) -> Unit = {},
    onGoToArtist: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteTrackIds.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val isImportCompleted by viewModel.isImportCompleted.collectAsState(initial = false)
    val context = LocalContext.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (text.isNotBlank()) {
                    viewModel.importTracksFromText(text)
                } else {
                    Toast.makeText(context, "Выбранный файл пуст", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = DeepBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Моя медиатека",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.selectedTab == LibraryTab.PLAYLISTS) {
                        IconButton(onClick = { viewModel.openCreatePlaylistDialog(true) }) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Создать плейлист",
                                tint = VioletPrimary
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "Сортировка",
                                tint = TextSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                            containerColor = DarkSurfaceVariant
                        ) {
                            DropdownMenuItem(
                                text = { Text("По дате добавления", color = TextPrimary) },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.DATE_ADDED)
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("По названию", color = TextPrimary) },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.TITLE)
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("По исполнителю", color = TextPrimary) },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.ARTIST)
                                    sortMenuExpanded = false
                                }
                            )
                            HorizontalDivider(color = DarkBorder)
                            DropdownMenuItem(
                                text = { Text("Импорт треков (.txt)", color = VioletLight) },
                                onClick = {
                                    sortMenuExpanded = false
                                    filePickerLauncher.launch("*/*")
                                }
                            )
                        }
                    }
                }
            }

            // Tab Bar: Only "Любимые" and "Плейлисты" (Офлайн and История removed as requested)
            PrimaryTabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = DeepBlack,
                contentColor = VioletPrimary,
                divider = { HorizontalDivider(color = DarkBorder) }
            ) {
                Tab(
                    selected = state.selectedTab == LibraryTab.FAVORITES,
                    onClick = { viewModel.selectTab(LibraryTab.FAVORITES) },
                    text = { 
                        Text(
                            "Любимые (${state.favoriteTracks.size})", 
                            fontSize = 14.sp, 
                            fontWeight = if (state.selectedTab == LibraryTab.FAVORITES) FontWeight.Bold else FontWeight.Normal,
                            color = if (state.selectedTab == LibraryTab.FAVORITES) VioletLight else TextMuted
                        ) 
                    }
                )
                Tab(
                    selected = state.selectedTab == LibraryTab.PLAYLISTS,
                    onClick = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                    text = { 
                        Text(
                            "Плейлисты", 
                            fontSize = 14.sp, 
                            fontWeight = if (state.selectedTab == LibraryTab.PLAYLISTS) FontWeight.Bold else FontWeight.Normal,
                            color = if (state.selectedTab == LibraryTab.PLAYLISTS) VioletLight else TextMuted
                        ) 
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tab Content
            when (state.selectedTab) {
                LibraryTab.FAVORITES -> {
                    val notDownloadedTracks = remember(state.favoriteTracks, downloadedIds) {
                        state.favoriteTracks.filter { track ->
                            !downloadedIds.contains(track.id) &&
                            !track.isDownloaded &&
                            (track.localFilePath.isNullOrBlank() || !java.io.File(track.localFilePath).exists())
                        }
                    }
                    val notDownloadedCount = notDownloadedTracks.size

                    // Action Buttons Row: Download All + Import .txt + Shuffle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (notDownloadedCount > 0) {
                            Button(
                                onClick = { onDownloadAll(notDownloadedTracks) },
                                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = DeepBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (notDownloadedCount == state.favoriteTracks.size) "Скачать все" else "Скачать ($notDownloadedCount)",
                                    color = DeepBlack,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        if (!isImportCompleted) {
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletLight),
                                modifier = if (notDownloadedCount <= 0) Modifier.fillMaxWidth() else Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileUpload,
                                    contentDescription = "Импорт",
                                    tint = VioletLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Импорт .txt",
                                    color = VioletLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        if (state.favoriteTracks.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    val shuffled = state.favoriteTracks.shuffled()
                                    onTrackClick(shuffled.first(), shuffled, "Любимое")
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(20.dp),
                                modifier = if (notDownloadedCount <= 0 && isImportCompleted) Modifier.fillMaxWidth() else Modifier
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Перемешать",
                                    tint = VioletLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (notDownloadedCount <= 0 && isImportCompleted) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Перемешать все треки (${state.favoriteTracks.size})",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    if (state.favoriteTracks.isEmpty()) {
                        EmptyLibraryPlaceholder(
                            title = "Нет любимых треков",
                            subtitle = "Нажимайте на сердечко у любых песен или нажмите кнопку «Импорт .txt» для загрузки треков из Яндекс Музыки, ВК или Spotify"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 120.dp, start = 8.dp, end = 8.dp)
                        ) {
                            itemsIndexed(
                                items = state.favoriteTracks,
                                key = { index, track -> "fav_${track.id}_$index" },
                                contentType = { _, _ -> "track" }
                            ) { _, track ->
                                TrackListItem(
                                    track = track,
                                    isFavorite = true,
                                    isDownloaded = downloadedIds.contains(track.id),
                                    isCurrentTrack = currentTrackId == track.id,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering,
                                    onClick = { onTrackClick(track, state.favoriteTracks, "Любимое") },
                                    onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                    onMoreClick = { actionTrack = track }
                                )
                            }
                        }
                    }
                }

                LibraryTab.PLAYLISTS -> {
                    if (state.playlists.isEmpty()) {
                        EmptyLibraryPlaceholder(
                            title = "У вас пока нет плейлистов",
                            subtitle = "Нажмите +, чтобы создать первый плейлист и собрать свои любимые треки"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                items = state.playlists,
                                key = { index, pl -> "pl_${pl.id}_$index" },
                                contentType = { _, _ -> "playlist" }
                            ) { _, playlist ->
                                PlaylistItemCard(
                                    playlist = playlist,
                                    onPlay = {
                                        viewModel.playPlaylist(playlist) { tracks ->
                                            onTrackClick(tracks.first(), tracks, playlist.name)
                                        }
                                    },
                                    onDelete = { viewModel.deletePlaylist(playlist.id) }
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
                onFavoriteToggle = { viewModel.toggleFavorite(track) },
                onDownloadClick = { onDownloadTrack(track) },
                onAddToPlaylistClick = {},
                onGoToArtistClick = { artistName ->
                    onGoToArtist(artistName)
                }
            )
        }

        // Create Playlist Dialog
        if (state.isCreatePlaylistDialogOpen) {
            var playlistName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { viewModel.openCreatePlaylistDialog(false) },
                containerColor = DarkSurfaceVariant,
                title = { Text("Новый плейлист", color = TextPrimary) },
                text = {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        placeholder = { Text("Название плейлиста", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = VioletPrimary,
                            unfocusedBorderColor = DarkBorder
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (playlistName.isNotBlank()) {
                                viewModel.createPlaylist(playlistName.trim())
                                viewModel.openCreatePlaylistDialog(false)
                            }
                        }
                    ) {
                        Text("Создать", color = VioletLight, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.openCreatePlaylistDialog(false) }) {
                        Text("Отмена", color = TextMuted)
                    }
                }
            )
        }

        // Import Tracks Progress Dialog
        if (state.importState.isImporting) {
            val imp = state.importState
            AlertDialog(
                onDismissRequest = { if (imp.isFinished) viewModel.dismissImportDialog() },
                containerColor = DarkSurfaceVariant,
                title = {
                    Text(
                        text = if (imp.isFinished) "Импорт завершён" else "Импорт треков...",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (imp.error != null) {
                            Text(text = imp.error, color = AccentPink)
                        } else if (!imp.isFinished) {
                            Text(
                                text = "Обработка ${imp.current} из ${imp.total}",
                                color = VioletLight,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val progress = if (imp.total > 0) imp.current.toFloat() / imp.total.toFloat() else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = VioletPrimary,
                                trackColor = DarkSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = imp.currentLine,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "✓ Успешно добавлено в Любимые: ${imp.importedCount} из ${imp.total} треков в правильном порядке!",
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissImportDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                    ) {
                        Text(if (imp.isFinished) "Отлично" else "Отмена")
                    }
                }
            )
        }
    }
}

@Composable
private fun PlaylistItemCard(
    playlist: Playlist,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = playlist.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkBorder)
            )

            Column {
                Text(
                    text = playlist.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${playlist.trackCount} треков",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Удалить плейлист",
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyLibraryPlaceholder(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = VioletPrimary,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
