package com.cloudify.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudify.music.core.theme.DarkBorder
import com.cloudify.music.core.theme.DarkSurface
import com.cloudify.music.core.theme.DarkSurfaceVariant
import com.cloudify.music.core.theme.DeepBlack
import com.cloudify.music.core.theme.TextMuted
import com.cloudify.music.core.theme.TextPrimary
import com.cloudify.music.core.theme.TextSecondary
import com.cloudify.music.core.theme.VioletPrimary
import com.cloudify.music.domain.model.Artist
import com.cloudify.music.domain.model.Track
import com.cloudify.music.ui.components.SectionHeader
import com.cloudify.music.ui.components.TrackListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onTrackClick: (Track, List<Track>) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteTrackIds.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()

    Scaffold(
        containerColor = DeepBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Text(
                text = "Поиск музыки",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )

            // Search Bar
            TextField(
                value = state.query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = { Text("Трек, артист или жанр...", color = TextMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Поиск",
                        tint = VioletPrimary
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Очистить",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            // Filter Chips (Все, Треки, Артисты, Альбомы)
            if (state.results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.activeFilter == SearchFilter.ALL,
                            onClick = { viewModel.setFilter(SearchFilter.ALL) },
                            label = { Text("Все") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VioletPrimary,
                                selectedLabelColor = DeepBlack
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.activeFilter == SearchFilter.TRACKS,
                            onClick = { viewModel.setFilter(SearchFilter.TRACKS) },
                            label = { Text("Треки") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VioletPrimary,
                                selectedLabelColor = DeepBlack
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.activeFilter == SearchFilter.ARTISTS,
                            onClick = { viewModel.setFilter(SearchFilter.ARTISTS) },
                            label = { Text("Исполнители") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VioletPrimary,
                                selectedLabelColor = DeepBlack
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.activeFilter == SearchFilter.ALBUMS,
                            onClick = { viewModel.setFilter(SearchFilter.ALBUMS) },
                            label = { Text("Альбомы") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VioletPrimary,
                                selectedLabelColor = DeepBlack
                            )
                        )
                    }
                }
            }

            // Results or Suggestions
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (state.isSearching) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = VioletPrimary)
                        }
                    }
                } else if (state.query.isNotBlank()) {
                    // If filter is ARTISTS, display prominent artist cards first
                    if (state.activeFilter == SearchFilter.ARTISTS) {
                        item {
                            val matchingArtists = state.popularArtists.filter {
                                it.name.contains(state.query, ignoreCase = true)
                            }.ifEmpty { state.popularArtists.take(4) }

                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                matchingArtists.forEach { artist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { viewModel.performSearch(artist.name) }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        AsyncImage(
                                            model = artist.artworkUrl,
                                            contentDescription = artist.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                                .background(DarkSurface)
                                        )
                                        Column {
                                            Text(
                                                text = artist.name,
                                                color = TextPrimary,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Исполнитель • ${artist.monthlyListeners}",
                                                color = TextSecondary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search Results
                    itemsIndexed(
                        items = state.filteredResults,
                        key = { index, track -> "search_${track.id}_$index" },
                        contentType = { _, _ -> "track" }
                    ) { _, track ->
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            TrackListItem(
                                track = track,
                                isFavorite = favoriteIds.contains(track.id),
                                isDownloaded = downloadedIds.contains(track.id),
                                onClick = { onTrackClick(track, state.filteredResults) },
                                onFavoriteToggle = { viewModel.toggleFavorite(track) }
                            )
                        }
                    }
                } else {
                    // Search History
                    if (state.searchHistory.isNotEmpty()) {
                        item {
                            SectionHeader(title = "История поиска")
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                state.searchHistory.forEach { queryItem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.performSearch(queryItem) }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.History,
                                                contentDescription = null,
                                                tint = TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = queryItem,
                                                color = TextPrimary,
                                                fontSize = 15.sp
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteHistoryItem(queryItem) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Удалить",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Popular Artists Carousel
                    if (state.popularArtists.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Популярные исполнители")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(
                                    items = state.popularArtists,
                                    key = { index, artist -> "artist_${artist.name}_$index" },
                                    contentType = { _, _ -> "artist" }
                                ) { _, artist ->
                                    Column(
                                        modifier = Modifier
                                            .width(96.dp)
                                            .clickable { viewModel.performSearch(artist.name) },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AsyncImage(
                                            model = artist.artworkUrl,
                                            contentDescription = artist.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(90.dp)
                                                .clip(CircleShape)
                                                .background(DarkSurface)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = artist.name,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Исполнитель",
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Popular Genres
                    item {
                        SectionHeader(title = "Популярные жанры")
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            state.popularGenres.chunked(2).forEach { rowGenres ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowGenres.forEach { genre ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(60.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(DarkSurfaceVariant)
                                                .clickable { viewModel.performSearch(genre) }
                                                .padding(14.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = genre,
                                                color = TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
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
    }
}
