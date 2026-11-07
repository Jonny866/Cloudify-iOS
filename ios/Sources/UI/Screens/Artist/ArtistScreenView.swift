import SwiftUI

public struct ArtistScreenView: View {
    public let artistName: String
    @Binding public var selectedTab: MainTab
    public let onBack: () -> Void
    public var onSelectTab: ((MainTab) -> Void)? = nil
    public var onOpenFullPlayer: (() -> Void)? = nil

    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var theme = ThemeManager.shared

    @State private var artistDetail: ArtistDetail? = nil
    @State private var isLoading: Bool = true
    @State private var selectedAlbum: Album? = nil
    @State private var actionTrack: Track? = nil

    public init(
        artistName: String,
        selectedTab: Binding<MainTab>,
        onBack: @escaping () -> Void,
        onSelectTab: ((MainTab) -> Void)? = nil,
        onOpenFullPlayer: (() -> Void)? = nil
    ) {
        self.artistName = artistName
        self._selectedTab = selectedTab
        self.onBack = onBack
        self.onSelectTab = onSelectTab
        self.onOpenFullPlayer = onOpenFullPlayer
    }

    public var body: some View {
        ZStack(alignment: .top) {
            Color.deepBlack.edgesIgnoringSafeArea(.all)

            if isLoading {
                VStack {
                    Spacer()
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: theme.currentTheme.primary))
                        .scaleEffect(1.2)
                    Spacer()
                }
            } else if let detail = artistDetail {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 0) {
                        // Hero Header
                        heroHeader(detail: detail)

                        // Monthly Listeners
                        Text("Слушателей за месяц: \(detail.artist.monthlyListeners)")
                            .font(.system(size: 13))
                            .foregroundColor(.textMuted)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)

                        // Action Bar (Follow/Subscribe, Shuffle, Big Play)
                        actionBar(detail: detail)

                        // Numbered Top 5 Popular Tracks
                        popularTracksSection(detail: detail)

                        // "Альбомы и EP" Carousel
                        if !detail.albums.isEmpty {
                            albumsSection(detail: detail)
                        }

                        // All Releases from SoundCloud
                        allTracksSection(detail: detail)

                        Spacer(minLength: 160)
                    }
                }
            }

            // Top Floating Back Button
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.backward")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.textPrimary)
                        .padding(10)
                        .background(Color.black.opacity(0.6))
                        .clipShape(Circle())
                }
                .padding(.leading, 16)
                .padding(.top, 50)

                Spacer()
            }

            // Docked Stack: Mini Player + Custom Tab Bar
            VStack(spacing: 0) {
                Spacer()

                MiniPlayerView(onOpenFullPlayer: {
                    onOpenFullPlayer?()
                })

                CustomTabBar(selectedTab: Binding(
                    get: { selectedTab },
                    set: { newTab in
                        selectedTab = newTab
                        onSelectTab?(newTab)
                    }
                ))
                .background(
                    Color.darkSurface
                        .ignoresSafeArea(edges: .bottom)
                )
            }
        }
        .task {
            loadArtistDetails()
        }
        .sheet(item: $selectedAlbum) { album in
            AlbumDetailSheet(
                album: album,
                onTrackClick: { track in
                    player.playTrack(track, in: album.tracks)
                    selectedAlbum = nil
                },
                onPlayAll: {
                    if let first = album.tracks.first {
                        player.playTrack(first, in: album.tracks)
                    }
                    selectedAlbum = nil
                },
                onShuffleAll: {
                    if let first = album.tracks.shuffled().first {
                        player.playTrack(first, in: album.tracks.shuffled())
                    }
                    selectedAlbum = nil
                }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(item: $actionTrack) { track in
            TrackActionSheet(
                track: track,
                isFavorite: repo.isFavorite(track.id),
                isDownloaded: repo.downloadedIds.contains(track.id),
                onFavoriteToggle: { repo.toggleFavorite(track) },
                onDownloadClick: { repo.downloadTrack(track) },
                onGoToArtist: {},
                onDismiss: { actionTrack = nil }
            )
            .presentationDetents([.fraction(0.4)])
        }
    }

    // Hero Header
    private func heroHeader(detail: ArtistDetail) -> some View {
        ZStack(alignment: .bottomLeading) {
            AsyncImage(url: URL(string: detail.artist.artworkUrl)) { img in
                img.resizable().aspectRatio(contentMode: .fill)
            } placeholder: {
                Color.darkSurface
            }
            .frame(height: 280)
            .frame(maxWidth: .infinity)
            .clipped()

            // Dark gradient overlay
            LinearGradient(
                colors: [Color.black.opacity(0.3), Color.black.opacity(0.6), Color.deepBlack],
                startPoint: .top,
                endPoint: .bottom
            )

            VStack(alignment: .leading, spacing: 4) {
                Text(detail.artist.name)
                    .font(.system(size: 34, weight: .black))
                    .foregroundColor(.textPrimary)

                HStack(spacing: 6) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 14))
                        .foregroundColor(theme.currentTheme.primary)

                    Text("Подтверждено Cloudify")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.textPrimary)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
        }
        .frame(height: 280)
    }

    // Action Bar
    private func actionBar(detail: ArtistDetail) -> some View {
        let isFav = repo.favoriteArtists.contains(detail.artist.name)

        return HStack(spacing: 14) {
            Button(action: {
                repo.toggleArtistFavorite(name: detail.artist.name)
            }) {
                Text(isFav ? "В избранном" : "Подписаться")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(isFav ? .textPrimary : (theme.currentTheme == .yellow ? .black : .white))
                    .padding(.horizontal, 18)
                    .padding(.vertical, 8)
                    .background(isFav ? Color(hex: 0x2A2A2A) : theme.currentTheme.primary)
                    .clipShape(Capsule())
            }

            Spacer()

            Button(action: {
                if !detail.allTracks.isEmpty {
                    let shuffled = detail.allTracks.shuffled()
                    player.playTrack(shuffled[0], in: shuffled)
                }
            }) {
                Image(systemName: "shuffle")
                    .font(.system(size: 22))
                    .foregroundColor(theme.currentTheme.primary)
                    .frame(width: 40, height: 40)
            }

            Button(action: {
                if let first = detail.popularTracks.first {
                    player.playTrack(first, in: detail.popularTracks)
                }
            }) {
                ZStack {
                    Circle()
                        .fill(theme.currentTheme.primary)
                        .frame(width: 52, height: 52)

                    Image(systemName: "play.fill")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(theme.currentTheme == .yellow ? .black : .white)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    // Numbered Top 5 Tracks
    private func popularTracksSection(detail: ArtistDetail) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Популярное")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)
                .padding(.top, 10)

            ForEach(Array(detail.popularTracks.prefix(5).enumerated()), id: \.element.id) { index, track in
                let isCur = player.currentTrack?.id == track.id
                let isPlaying = isCur && player.isPlaying
                let isFav = repo.isFavorite(track.id)

                HStack(spacing: 12) {
                    // Number or Equalizer
                    ZStack {
                        if isPlaying {
                            PlayingEqualizerView()
                        } else {
                            Text("\(index + 1)")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(isCur ? theme.currentTheme.primary : .textMuted)
                        }
                    }
                    .frame(width: 22)

                    AsyncImage(url: URL(string: track.artworkUrl)) { img in
                        img.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.darkSurface
                    }
                    .frame(width: 48, height: 48)
                    .clipShape(RoundedRectangle(cornerRadius: 6))

                    VStack(alignment: .leading, spacing: 3) {
                        Text(track.title)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(isCur ? theme.currentTheme.primary : .textPrimary)
                            .lineLimit(1)

                        Text(track.playsCount)
                            .font(.system(size: 12))
                            .foregroundColor(.textMuted)
                    }

                    Spacer()

                    Button(action: {
                        repo.toggleFavorite(track)
                    }) {
                        Image(systemName: isFav ? "heart.fill" : "heart")
                            .font(.system(size: 18))
                            .foregroundColor(isFav ? .accentPink : .textMuted)
                            .frame(width: 30, height: 30)
                    }

                    Button(action: {
                        actionTrack = track
                    }) {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 16))
                            .foregroundColor(.textMuted)
                            .frame(width: 26, height: 30)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .contentShape(Rectangle())
                .onTapGesture {
                    player.playTrack(track, in: detail.popularTracks)
                }
            }
        }
    }

    // "Альбомы и EP" Carousel
    private func albumsSection(detail: ArtistDetail) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Альбомы и EP")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.textPrimary)

                Spacer()

                Text("\(detail.albums.count) релиза")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.textMuted)
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(detail.albums) { album in
                        Button(action: {
                            selectedAlbum = album
                        }) {
                            VStack(alignment: .leading, spacing: 6) {
                                ZStack(alignment: .bottomTrailing) {
                                    AsyncImage(url: URL(string: album.artworkUrl)) { img in
                                        img.resizable().aspectRatio(contentMode: .fill)
                                    } placeholder: {
                                        Color.darkSurface
                                    }
                                    .frame(width: 140, height: 140)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))

                                    Button(action: {
                                        if let first = album.tracks.first {
                                            player.playTrack(first, in: album.tracks)
                                        }
                                    }) {
                                        ZStack {
                                            Circle()
                                                .fill(theme.currentTheme.primary)
                                                .frame(width: 36, height: 36)
                                            Image(systemName: "play.fill")
                                                .font(.system(size: 16, weight: .bold))
                                                .foregroundColor(theme.currentTheme == .yellow ? .black : .white)
                                        }
                                        .padding(8)
                                    }
                                }

                                Text(album.title)
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.textPrimary)
                                    .lineLimit(1)

                                Text("\(album.year) • \(album.trackCount) треков")
                                    .font(.system(size: 12))
                                    .foregroundColor(.textSecondary)
                            }
                            .frame(width: 140)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    // All Tracks Section
    private func allTracksSection(detail: ArtistDetail) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Все треки (SoundCloud)")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)
                .padding(.top, 16)

            ForEach(detail.allTracks) { track in
                TrackRowView(
                    track: track,
                    isCurrent: player.currentTrack?.id == track.id,
                    isPlaying: player.isPlaying,
                    isFavorite: repo.isFavorite(track.id),
                    onPlay: {
                        player.playTrack(track, in: detail.allTracks)
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

    private func loadArtistDetails() {
        Task {
            do {
                let tracks = try await CloudifyAPI.shared.searchTracks(query: artistName)
                let pop = Array(tracks.prefix(5))
                let matched = repo.getPopularArtists().first(where: { $0.name.lowercased() == artistName.lowercased() })
                let artwork = matched?.artworkUrl ?? (tracks.first?.artworkUrl ?? CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/artworks-MSGdnR5dCyyXfnbf-asrmMg-t500x500.jpg"))

                var albumsList: [Album] = []
                if tracks.count > 3 {
                    let chunk1 = Array(tracks.prefix(tracks.count / 2))
                    let chunk2 = Array(tracks.suffix(tracks.count - chunk1.count))
                    albumsList.append(
                        Album(
                            id: "alb_\(artistName)_1",
                            title: chunk1.first?.title ?? "Сингл",
                            artist: artistName,
                            artworkUrl: chunk1.first?.artworkUrl ?? artwork,
                            year: "2024",
                            trackCount: chunk1.count,
                            tracks: chunk1
                        )
                    )
                    albumsList.append(
                        Album(
                            id: "alb_\(artistName)_2",
                            title: chunk2.first?.title ?? "EP",
                            artist: artistName,
                            artworkUrl: chunk2.first?.artworkUrl ?? artwork,
                            year: "2023",
                            trackCount: chunk2.count,
                            tracks: chunk2
                        )
                    )
                }

                let art = Artist(
                    id: "art_\(artistName)",
                    name: artistName,
                    artworkUrl: artwork,
                    monthlyListeners: matched?.monthlyListeners ?? "839.6 тыс. слушателей",
                    isFavorite: repo.favoriteArtists.contains(artistName)
                )

                await MainActor.run {
                    self.artistDetail = ArtistDetail(
                        artist: art,
                        popularTracks: pop.isEmpty ? repo.favorites : pop,
                        allTracks: tracks.isEmpty ? repo.favorites : tracks,
                        albums: albumsList,
                        isFavorite: repo.favoriteArtists.contains(artistName)
                    )
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }
}

// Album Detail Sheet
public struct AlbumDetailSheet: View {
    public let album: Album
    public let onTrackClick: (Track) -> Void
    public let onPlayAll: () -> Void
    public let onShuffleAll: () -> Void

    @ObservedObject var theme = ThemeManager.shared

    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 16) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color.gray.opacity(0.4))
                    .frame(width: 36, height: 4)
                    .padding(.top, 10)

                // Cover + Meta
                AsyncImage(url: URL(string: album.artworkUrl)) { img in
                    img.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.darkSurface
                }
                .frame(width: 170, height: 170)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .shadow(color: Color.black.opacity(0.5), radius: 10, y: 5)

                VStack(spacing: 4) {
                    Text(album.title)
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.textPrimary)

                    Text("\(album.artist) • \(album.year) • \(album.tracks.count) треков")
                        .font(.system(size: 13))
                        .foregroundColor(.textSecondary)
                }

                // Play / Shuffle Buttons
                HStack(spacing: 16) {
                    Button(action: onPlayAll) {
                        HStack(spacing: 8) {
                            Image(systemName: "play.fill")
                            Text("Слушать всё")
                        }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(theme.currentTheme == .yellow ? .black : .white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(theme.currentTheme.primary)
                        .clipShape(Capsule())
                    }

                    Button(action: onShuffleAll) {
                        Image(systemName: "shuffle")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.textPrimary)
                            .frame(width: 44, height: 44)
                            .background(Color.darkSurface)
                            .clipShape(Circle())
                    }
                }

                Divider().background(Color.darkBorder)

                // Track list
                VStack(spacing: 2) {
                    ForEach(Array(album.tracks.enumerated()), id: \.element.id) { idx, trk in
                        Button(action: { onTrackClick(trk) }) {
                            HStack(spacing: 14) {
                                Text("\(idx + 1)")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.textMuted)
                                    .frame(width: 20)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(trk.title)
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.textPrimary)
                                        .lineLimit(1)
                                    Text(trk.artist)
                                        .font(.system(size: 12))
                                        .foregroundColor(.textSecondary)
                                        .lineLimit(1)
                                }

                                Spacer()

                                Text(trk.durationFormatted)
                                    .font(.system(size: 12, design: .monospaced))
                                    .foregroundColor(.textMuted)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 8)
                        }
                    }
                }

                Spacer(minLength: 40)
            }
        }
        .background(Color.darkSurfaceVariant.edgesIgnoringSafeArea(.all))
    }
}
