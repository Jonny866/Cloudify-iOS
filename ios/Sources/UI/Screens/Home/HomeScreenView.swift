import SwiftUI

public struct HomeScreenView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var theme = ThemeManager.shared

    public let onArtistClick: (String) -> Void
    public let onProfileClick: () -> Void

    @State private var selectedFilter: String = "Все"
    @State private var waveTracks: [Track] = []
    @State private var actionTrack: Track? = nil

    private let filterTabs = ["Все", "Музыка", "Подкасты", "Аудиокниги"]

    private var popularArtists: [Artist] {
        [
            Artist(id: "art_1", name: "GONE.Fludd", artworkUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400", monthlyListeners: "1.4 млн"),
            Artist(id: "art_2", name: "Miyagi & Эндшпиль", artworkUrl: "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400", monthlyListeners: "2.1 млн"),
            Artist(id: "art_3", name: "KORDHELL", artworkUrl: "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=400", monthlyListeners: "890 тыс."),
            Artist(id: "art_4", name: "DVRST", artworkUrl: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400", monthlyListeners: "670 тыс."),
            Artist(id: "art_5", name: "dabbackwood", artworkUrl: "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400", monthlyListeners: "420 тыс.")
        ]
    }

    private var featuredRelease: Track {
        repo.favorites.first(where: { $0.artist == "GONE.Fludd" }) ??
        Track(
            id: "rel_gone_fludd_1",
            title: "КОД ГИАС",
            artist: "GONE.Fludd",
            artworkUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500",
            streamUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        )
    }

    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            LazyVStack(spacing: 20) {
                // Top Bar: Profile + Filter Pills + Brand Logo
                topNavBar

                // Quick Access 2-Column Grid
                quickAccessGrid

                // "Моя Волна" Interactive Banner
                myWaveBanner

                // "Новый релиз исполнителя" Featured Release Card
                featuredReleaseCard

                // "Недавно прослушанные треки"
                if !repo.recentTracks.isEmpty {
                    recentTracksSection
                }

                // "Популярно для тебя"
                popularForYouSection

                // "Популярные исполнители"
                popularArtistsSection

                Spacer(minLength: 120)
            }
            .padding(.top, 10)
        }
        .background(Color.deepBlack.edgesIgnoringSafeArea(.all))
        .task {
            loadWaveTracks()
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

    // Top Navigation Bar
    private var topNavBar: some View {
        HStack(spacing: 12) {
            Button(action: onProfileClick) {
                if !repo.user.avatarUrl.isEmpty {
                    AsyncImage(url: URL(string: repo.user.avatarUrl)) { img in
                        img.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.darkSurface
                    }
                    .frame(width: 36, height: 36)
                    .clipShape(Circle())
                } else {
                    Image(systemName: "person.circle.fill")
                        .font(.system(size: 32))
                        .foregroundColor(.textSecondary)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(filterTabs, id: \.self) { tab in
                        let isSelected = selectedFilter == tab
                        Button(action: {
                            withAnimation(.easeInOut(duration: 0.15)) {
                                selectedFilter = tab
                            }
                        }) {
                            Text(tab)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(isSelected ? .black : .textPrimary)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(isSelected ? theme.currentTheme.primary : Color(hex: 0x2A2A2A))
                                .clipShape(Capsule())
                        }
                    }
                }
            }

            // Cloudify Logo
            Button(action: {
                theme.toggleTheme()
            }) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(theme.currentTheme == .yellow ? Color(hex: 0x1E1A0E) : Color(hex: 0x13101E))
                        .frame(width: 36, height: 36)

                    Image(uiImage: UIImage(named: theme.currentTheme.logoImageName) ?? UIImage())
                        .resizable()
                        .renderingMode(.original)
                        .frame(width: 30, height: 30)
                }
            }
        }
        .padding(.horizontal, 16)
    }

    // Quick Access 2-Column Grid
    private var quickAccessGrid: some View {
        let items: [QuickAccessItem] = [
            QuickAccessItem(id: "fav", title: "Любимые треки", artworkUrl: "", isFavorites: true, tracks: repo.favorites),
            QuickAccessItem(id: "dab", title: "dabbackwood", artworkUrl: "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300"),
            QuickAccessItem(id: "gone", title: "GONE.Fludd", artworkUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300"),
            QuickAccessItem(id: "miyagi", title: "Miyagi & Эндшпиль", artworkUrl: "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300"),
            QuickAccessItem(id: "kord", title: "KORDHELL", artworkUrl: "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=300"),
            QuickAccessItem(id: "dvrst", title: "DVRST", artworkUrl: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300")
        ]

        return LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
            ForEach(items) { item in
                Button(action: {
                    if item.isFavorites {
                        if let first = repo.favorites.first {
                            player.playTrack(first, in: repo.favorites)
                        }
                    } else {
                        onArtistClick(item.title)
                    }
                }) {
                    HStack(spacing: 10) {
                        if item.isFavorites {
                            ZStack {
                                LinearGradient(
                                    colors: [Color(hex: 0x450AF5), Color(hex: 0xC4EFD9)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(.white)
                            }
                            .frame(width: 52, height: 52)
                        } else {
                            AsyncImage(url: URL(string: item.artworkUrl)) { img in
                                img.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Color.darkSurface
                            }
                            .frame(width: 52, height: 52)
                        }

                        Text(item.title)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.textPrimary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)

                        Spacer()
                    }
                    .background(Color(hex: 0x242424))
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                }
            }
        }
        .padding(.horizontal, 16)
    }

    // "Моя Волна" Banner
    private var myWaveBanner: some View {
        Button(action: {
            if !waveTracks.isEmpty {
                player.playTrack(waveTracks[0], in: waveTracks)
            } else if let first = repo.favorites.first {
                player.playTrack(first, in: repo.favorites)
            }
        }) {
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 16)
                    .fill(
                        LinearGradient(
                            colors: [theme.currentTheme.dark, theme.currentTheme.primary.opacity(0.85)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                HStack {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 6) {
                            Image(systemName: "waveform")
                                .font(.system(size: 14, weight: .bold))
                            Text("МОЯ ВОЛНА")
                                .font(.system(size: 12, weight: .bold))
                                .tracking(1.2)
                        }
                        .foregroundColor(.black.opacity(0.8))

                        Text("Бесконечный поток под ваш вкус")
                            .font(.system(size: 16, weight: .black))
                            .foregroundColor(.black)

                        Text("Подбирается автоматически нейросетью")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.black.opacity(0.7))
                    }

                    Spacer()

                    ZStack {
                        Circle()
                            .fill(Color.black.opacity(0.85))
                            .frame(width: 48, height: 48)

                        Image(systemName: (player.isPlaying && player.currentTrack?.genre == "Wave") ? "pause.fill" : "play.fill")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(theme.currentTheme.primary)
                    }
                }
                .padding(18)
            }
            .frame(height: 105)
            .padding(.horizontal, 16)
        }
    }

    // Featured Release Card
    private var featuredReleaseCard: some View {
        let release = featuredRelease
        let isFav = repo.isFavorite(release.id)

        return VStack(alignment: .leading, spacing: 10) {
            // Header
            Button(action: {
                onArtistClick(release.artist)
            }) {
                HStack(spacing: 10) {
                    AsyncImage(url: URL(string: release.artworkUrl)) { img in
                        img.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.darkSurface
                    }
                    .frame(width: 44, height: 44)
                    .clipShape(Circle())

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Новый релиз исполнителя")
                            .font(.system(size: 12))
                            .foregroundColor(.textMuted)

                        Text(release.artist)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.textPrimary)
                    }

                    Spacer()
                }
            }

            // Big Card
            HStack(spacing: 0) {
                AsyncImage(url: URL(string: release.artworkUrl)) { img in
                    img.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.darkSurface
                }
                .frame(width: 125, height: 125)
                .clipShape(RoundedRectangle(cornerRadius: 10))

                VStack(alignment: .leading, spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Сингл")
                            .font(.system(size: 11))
                            .foregroundColor(.textMuted)

                        Text(release.title)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.textPrimary)
                            .lineLimit(1)

                        Text(release.artist)
                            .font(.system(size: 13))
                            .foregroundColor(.textSecondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    HStack {
                        Button(action: {
                            actionTrack = release
                        }) {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 16))
                                .foregroundColor(.textSecondary)
                                .frame(width: 32, height: 32)
                        }

                        Spacer()

                        Button(action: {
                            repo.toggleFavorite(release)
                        }) {
                            Image(systemName: isFav ? "heart.fill" : "heart")
                                .font(.system(size: 20))
                                .foregroundColor(isFav ? .accentPink : .textSecondary)
                                .frame(width: 34, height: 34)
                        }

                        Button(action: {
                            player.playTrack(release, in: [release])
                        }) {
                            ZStack {
                                Circle()
                                    .fill(theme.currentTheme.primary)
                                    .frame(width: 40, height: 40)

                                Image(systemName: "play.fill")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(.black)
                            }
                        }
                    }
                }
                .padding(12)
            }
            .frame(height: 125)
            .background(Color.darkSurfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .onTapGesture {
                player.playTrack(release, in: [release])
            }
        }
        .padding(.horizontal, 16)
    }

    // Recent Tracks Section
    private var recentTracksSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Недавно прослушанные")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)

            ForEach(repo.recentTracks.prefix(4)) { track in
                TrackRowView(
                    track: track,
                    isCurrent: player.currentTrack?.id == track.id,
                    isPlaying: player.isPlaying,
                    isFavorite: repo.isFavorite(track.id),
                    onPlay: {
                        player.playTrack(track, in: repo.recentTracks)
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

    // Popular For You
    private var popularForYouSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Популярно для тебя")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(repo.favorites) { track in
                        Button(action: {
                            player.playTrack(track, in: repo.favorites)
                        }) {
                            VStack(alignment: .leading, spacing: 6) {
                                AsyncImage(url: URL(string: track.artworkUrl)) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Color.darkSurface
                                }
                                .frame(width: 135, height: 135)
                                .clipShape(RoundedRectangle(cornerRadius: 8))

                                Text(track.title)
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.textPrimary)
                                    .lineLimit(1)

                                Text(track.artist)
                                    .font(.system(size: 12))
                                    .foregroundColor(.textSecondary)
                                    .lineLimit(1)
                            }
                            .frame(width: 135)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    // Popular Artists
    private var popularArtistsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Популярные исполнители")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(popularArtists) { artist in
                        Button(action: {
                            onArtistClick(artist.name)
                        }) {
                            VStack(spacing: 6) {
                                AsyncImage(url: URL(string: artist.artworkUrl)) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Color.darkSurface
                                }
                                .frame(width: 95, height: 95)
                                .clipShape(Circle())

                                Text(artist.name)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.textPrimary)
                                    .lineLimit(1)

                                Text("Исполнитель")
                                    .font(.system(size: 11))
                                    .foregroundColor(.textSecondary)
                            }
                            .frame(width: 100)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func loadWaveTracks() {
        Task {
            if let wave = try? await CloudifyAPI.shared.getWaveTracks(), !wave.isEmpty {
                await MainActor.run {
                    self.waveTracks = wave
                }
            }
        }
    }
}
