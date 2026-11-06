import SwiftUI

public enum LibrarySection: String, CaseIterable {
    case favorites = "Любимые"
    case playlists = "Плейлисты"
    case artists = "Артисты"
    case downloads = "Скачанные"
}

public struct LibraryScreenView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var theme = ThemeManager.shared

    public let onArtistClick: (String) -> Void

    @State private var selectedSection: LibrarySection = .favorites
    @State private var showCreatePlaylistSheet: Bool = false
    @State private var newPlaylistName: String = ""
    @State private var actionTrack: Track? = nil

    public var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("Моя медиатека")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.textPrimary)

                Spacer()

                if repo.isSyncing {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: theme.currentTheme.primary))
                        .scaleEffect(0.8)
                }

                if selectedSection == .playlists {
                    Button(action: {
                        showCreatePlaylistSheet = true
                    }) {
                        Image(systemName: "plus")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(theme.currentTheme.primary)
                            .frame(width: 36, height: 36)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 10)

            // Section Filter Pills
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(LibrarySection.allCases, id: \.self) { section in
                        let isSelected = selectedSection == section
                        Button(action: {
                            withAnimation(.easeInOut(duration: 0.15)) {
                                selectedSection = section
                            }
                        }) {
                            Text(section.rawValue)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(isSelected ? .black : .textPrimary)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(isSelected ? theme.currentTheme.primary : Color(hex: 0x2A2A2A))
                                .clipShape(Capsule())
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 12)

            // Content per section
            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    switch selectedSection {
                    case .favorites:
                        favoritesContent
                    case .playlists:
                        playlistsContent
                    case .artists:
                        artistsContent
                    case .downloads:
                        downloadsContent
                    }

                    Spacer(minLength: 120)
                }
            }
        }
        .background(Color.deepBlack.edgesIgnoringSafeArea(.all))
        .alert("Новый плейлист", isPresented: $showCreatePlaylistSheet) {
            TextField("Название плейлиста", text: $newPlaylistName)
            Button("Создать") {
                if !newPlaylistName.trimmingCharacters(in: .whitespaces).isEmpty {
                    repo.createPlaylist(name: newPlaylistName.trimmingCharacters(in: .whitespaces))
                    newPlaylistName = ""
                }
            }
            Button("Отмена", role: .cancel) { newPlaylistName = "" }
        }
        .sheet(item: $actionTrack) { track in
            TrackActionSheet(
                track: track,
                isFavorite: repo.isFavorite(track.id),
                isDownloaded: repo.downloadedIds.contains(track.id),
                onFavoriteToggle: { repo.toggleFavorite(track) },
                onDownloadClick: { repo.downloadTrack(track) },
                onGoToArtist: { onArtistClick(track.artist) },
                onDismiss: { actionTrack = nil }
            )
            .presentationDetents([.fraction(0.4)])
        }
    }

    // Favorites Content
    private var favoritesContent: some View {
        VStack(spacing: 0) {
            // Action Bar (Count, Shuffle, Play All)
            HStack {
                Text("\(repo.favorites.count) треков")
                    .font(.system(size: 13))
                    .foregroundColor(.textMuted)

                Spacer()

                Button(action: {
                    if !repo.favorites.isEmpty {
                        let shuffled = repo.favorites.shuffled()
                        player.playTrack(shuffled[0], in: shuffled)
                    }
                }) {
                    Image(systemName: "shuffle")
                        .font(.system(size: 20))
                        .foregroundColor(theme.currentTheme.primary)
                        .frame(width: 38, height: 38)
                }

                Button(action: {
                    if let first = repo.favorites.first {
                        player.playTrack(first, in: repo.favorites)
                    }
                }) {
                    ZStack {
                        Circle()
                            .fill(theme.currentTheme.primary)
                            .frame(width: 44, height: 44)

                        Image(systemName: "play.fill")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.black)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            // Tracks
            ForEach(repo.favorites) { track in
                TrackRowView(
                    track: track,
                    isCurrent: player.currentTrack?.id == track.id,
                    isPlaying: player.isPlaying,
                    isFavorite: true,
                    onPlay: {
                        player.playTrack(track, in: repo.favorites)
                        repo.addRecentTrack(track)
                    },
                    onFavoriteToggle: {
                        repo.toggleFavorite(track)
                    },
                    onMoreClick: {
                        actionTrack = track
                    }
                )
            }
        }
    }

    // Playlists Content
    private var playlistsContent: some View {
        VStack(spacing: 12) {
            ForEach(repo.playlists) { pl in
                HStack(spacing: 14) {
                    AsyncImage(url: URL(string: pl.artworkUrl)) { img in
                        img.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.darkSurfaceVariant
                    }
                    .frame(width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 8))

                    VStack(alignment: .leading, spacing: 4) {
                        Text(pl.name)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.textPrimary)

                        Text("Плейлист • \(pl.trackCount) треков")
                            .font(.system(size: 12))
                            .foregroundColor(.textSecondary)
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.system(size: 14))
                        .foregroundColor(.textMuted)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
            }
        }
    }

    // Artists Content
    private var artistsContent: some View {
        VStack(spacing: 12) {
            ForEach(Array(repo.favoriteArtists), id: \.self) { artistName in
                Button(action: {
                    onArtistClick(artistName)
                }) {
                    HStack(spacing: 14) {
                        ZStack {
                            Circle()
                                .fill(Color.darkSurfaceVariant)
                                .frame(width: 52, height: 52)
                            Text(String(artistName.prefix(1)).uppercased())
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(theme.currentTheme.primary)
                        }

                        VStack(alignment: .leading, spacing: 3) {
                            Text(artistName)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.textPrimary)

                            Text("Исполнитель • В избранном")
                                .font(.system(size: 12))
                                .foregroundColor(.textSecondary)
                        }

                        Spacer()

                        Image(systemName: "chevron.right")
                            .font(.system(size: 14))
                            .foregroundColor(.textMuted)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                }
            }
        }
    }

    // Downloads Content
    private var downloadsContent: some View {
        VStack(spacing: 0) {
            if repo.downloadedTracks.isEmpty {
                VStack(spacing: 12) {
                    Spacer(minLength: 60)
                    Image(systemName: "arrow.down.circle")
                        .font(.system(size: 48))
                        .foregroundColor(.textMuted)
                    Text("Нет скачанных треков")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.textPrimary)
                    Text("Нажмите '...' на любом треке для скачивания офлайн")
                        .font(.system(size: 13))
                        .foregroundColor(.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
            } else {
                ForEach(repo.downloadedTracks) { track in
                    TrackRowView(
                        track: track,
                        isCurrent: player.currentTrack?.id == track.id,
                        isPlaying: player.isPlaying,
                        isFavorite: repo.isFavorite(track.id),
                        onPlay: {
                            player.playTrack(track, in: repo.downloadedTracks)
                        },
                        onFavoriteToggle: {
                            repo.toggleFavorite(track)
                        },
                        onMoreClick: {
                            actionTrack = track
                        }
                    )
                }
            }
        }
    }
}
