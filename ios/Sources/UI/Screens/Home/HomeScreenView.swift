import SwiftUI

public struct HomeScreenView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var theme = ThemeManager.shared

    public let onArtistClick: (String) -> Void
    public let onProfileClick: () -> Void

    @State private var selectedFilter: String = "Все"
    @State private var popularForYou: [Track] = []
    @State private var favoriteReleases: [ArtistRelease] = []
    @State private var currentReleaseIndex: Int = 0
    @State private var actionTrack: Track? = nil
    @State private var isLoadingInitial: Bool = false

    private let filterTabs = ["Все", "Музыка", "Подкасты", "Аудиокниги"]

    public init(
        onArtistClick: @escaping (String) -> Void,
        onProfileClick: @escaping () -> Void
    ) {
        self.onArtistClick = onArtistClick
        self.onProfileClick = onProfileClick
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5...11: return "Доброе утро"
        case 12...17: return "Добрый день"
        case 18...22: return "Добрый вечер"
        default: return "Доброй ночи"
        }
    }

    private var popularArtists: [Artist] {
        repo.getPopularArtists()
    }

    // Quick Access items (up to 8 items, 1st is Liked Songs)
    private var quickAccessItems: [QuickAccessItem] {
        var items: [QuickAccessItem] = []

        // 1. Liked Songs card
        items.append(
            QuickAccessItem(
                id: "liked_songs",
                title: "Любимые треки",
                artworkUrl: "",
                isFavorites: true,
                tracks: repo.favorites
            )
        )

        // 2-8 from user history or favorites only (NO fake mock artists)
        var seenArtists = Set<String>()
        for track in repo.recentTracks {
            if seenArtists.insert(track.artist).inserted {
                items.append(
                    QuickAccessItem(
                        id: "recent_\(track.id)",
                        title: track.artist,
                        artworkUrl: track.artworkUrl,
                        isFavorites: false,
                        tracks: [track]
                    )
                )
                if items.count >= 8 { break }
            }
        }

        if items.count < 8 {
            for track in repo.favorites {
                if seenArtists.insert(track.artist).inserted {
                    items.append(
                        QuickAccessItem(
                            id: "fav_\(track.id)",
                            title: track.artist,
                            artworkUrl: track.artworkUrl,
                            isFavorites: false,
                            tracks: [track]
                        )
                    )
                    if items.count >= 8 { break }
                }
            }
        }

        return items
    }

    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            LazyVStack(spacing: 20) {
                // Top Bar: Profile + Filter Pills + Brand Logo
                topNavBar

                // Quick Access 2-Column Grid
                quickAccessGrid

                // "Новый релиз исполнителя" Featured Release Card / Dynamic Placeholder
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
            await loadInitialMusic()
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
                    ZStack {
                        Circle()
                            .fill(Color(hex: 0x282828))
                            .frame(width: 36, height: 36)
                        Image(systemName: "person.fill")
                            .font(.system(size: 16))
                            .foregroundColor(.textPrimary)
                    }
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
                                .foregroundColor(isSelected ? (theme.currentTheme == .yellow ? .black : .white) : .textPrimary)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(isSelected ? theme.currentTheme.primary : Color(hex: 0x2A2A2A))
                                .clipShape(Capsule())
                        }
                    }
                }
            }

            // Cloudify Logo (opens profile / toggles theme)
            Button(action: onProfileClick) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(theme.currentTheme == .yellow ? Color(hex: 0x1E1A0E) : Color(hex: 0x13101E))
                        .frame(width: 36, height: 36)

                    Image(uiImage: UIImage(named: theme.currentTheme.logoImageName) ?? UIImage())
                        .resizable()
                        .renderingMode(.original)
                        .frame(width: 28, height: 28)
                }
            }
        }
        .padding(.horizontal, 16)
    }

    // Quick Access 2-Column Grid (8 Cards)
    private var quickAccessGrid: some View {
        let columns = [
            GridItem(.flexible(), spacing: 8),
            GridItem(.flexible(), spacing: 8)
        ]

        return LazyVGrid(columns: columns, spacing: 8) {
            ForEach(quickAccessItems) { item in
                Button(action: {
                    if item.isFavorites {
                        if let first = repo.favorites.first {
                            player.playTrack(first, in: repo.favorites)
                        }
                    } else if !item.tracks.isEmpty {
                        player.playTrack(item.tracks[0], in: item.tracks)
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
                            .frame(width: 54, height: 54)
                        } else {
                            AsyncImage(url: URL(string: item.artworkUrl)) { img in
                                img.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Color.darkSurfaceVariant
                            }
                            .frame(width: 54, height: 54)
                            .clipped()
                        }

                        Text(item.title)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.textPrimary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)

                        Spacer()
                    }
                    .frame(height: 54)
                    .background(Color(hex: 0x242424))
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                }
            }
        }
        .padding(.horizontal, 16)
    }

    // Featured Release Card / Dynamic Placeholder (Matching Android 7.9 HorizontalPager)
    private var featuredReleaseCard: some View {
        Group {
            if favoriteReleases.count > 1 {
                VStack(spacing: 8) {
                    TabView(selection: $currentReleaseIndex) {
                        ForEach(Array(favoriteReleases.enumerated()), id: \.element.id) { index, rel in
                            singleReleaseCard(rel: rel)
                                .tag(index)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    .frame(height: 200)

                    // Page indicator dots (exact Android HomeScreen.kt)
                    HStack(spacing: 6) {
                        ForEach(0..<favoriteReleases.count, id: \.self) { idx in
                            Circle()
                                .fill(currentReleaseIndex == idx ? theme.currentTheme.primary : Color.gray.opacity(0.4))
                                .frame(width: currentReleaseIndex == idx ? 8 : 5, height: currentReleaseIndex == idx ? 8 : 5)
                                .animation(.easeInOut(duration: 0.2), value: currentReleaseIndex)
                        }
                    }
                }
            } else if let single = favoriteReleases.first {
                singleReleaseCard(rel: single)
            } else if let fav = repo.favorites.first {
                singleReleaseCard(rel: ArtistRelease(id: fav.id, artistName: fav.artist, artistArtwork: fav.artworkUrl, track: fav))
            } else {
                // Exact Android 7.9 empty placeholder card
                VStack(alignment: .leading, spacing: 6) {
                    Text("Новый релиз исполнителя")
                        .font(.system(size: 12))
                        .foregroundColor(.textMuted)

                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.darkSurfaceVariant)

                        VStack(spacing: 10) {
                            Image(systemName: "heart")
                                .font(.system(size: 28))
                                .foregroundColor(theme.currentTheme.light)

                            Text("Чтобы отобразить информацию, послушайте несколько любимых треков и артистов")
                                .font(.system(size: 13))
                                .foregroundColor(.textSecondary)
                                .multilineTextAlignment(.center)
                                .lineSpacing(4)
                                .padding(.horizontal, 20)
                        }
                        .padding(.vertical, 24)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func singleReleaseCard(rel: ArtistRelease) -> some View {
        let release = rel.track
        return VStack(alignment: .leading, spacing: 10) {
            // Header with artist avatar
            Button(action: {
                onArtistClick(rel.artistName)
            }) {
                HStack(spacing: 10) {
                    AsyncImage(url: URL(string: rel.artistArtwork.isEmpty ? release.artworkUrl : rel.artistArtwork)) { img in
                        img.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.darkSurfaceVariant
                    }
                    .frame(width: 46, height: 46)
                    .clipShape(Circle())

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Новый релиз исполнителя")
                            .font(.system(size: 12))
                            .foregroundColor(.textMuted)

                        Text(rel.artistName)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.textPrimary)
                    }
                }
            }
            .padding(.horizontal, 16)

            // Split Big Card
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.darkSurfaceVariant)

                HStack(spacing: 14) {
                    AsyncImage(url: URL(string: release.artworkUrl)) { img in
                        img.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.darkSurface
                    }
                    .frame(width: 130, height: 130)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Сингл")
                            .font(.system(size: 11))
                            .foregroundColor(.textMuted)

                        Text(release.title)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.textPrimary)
                            .lineLimit(1)

                        Text(release.artist)
                            .font(.system(size: 13))
                            .foregroundColor(.textSecondary)
                            .lineLimit(1)

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
                                Image(systemName: repo.isFavorite(release.id) ? "heart.fill" : "heart")
                                    .font(.system(size: 20))
                                    .foregroundColor(repo.isFavorite(release.id) ? .accentPink : .textSecondary)
                                    .frame(width: 32, height: 32)
                            }

                            Button(action: {
                                player.playTrack(release, in: [release], source: "Новый релиз: \(rel.artistName)")
                            }) {
                                ZStack {
                                    Circle()
                                        .fill(theme.currentTheme.primary)
                                        .frame(width: 42, height: 42)

                                    Image(systemName: (player.currentTrack?.id == release.id && player.isPlaying) ? "pause.fill" : "play.fill")
                                        .font(.system(size: 18))
                                        .foregroundColor(theme.currentTheme == .yellow ? .black : .white)
                                        .offset(x: (player.currentTrack?.id == release.id && player.isPlaying) ? 0 : 2)
                                }
                            }
                        }
                    }
                    .padding(.vertical, 12)
                    .padding(.trailing, 14)
                }
            }
            .frame(height: 130)
            .padding(.horizontal, 16)
        }
    }
    }

    // "Недавно прослушанные треки"
    private var recentTracksSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Недавно прослушанные треки")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)

            VStack(spacing: 4) {
                ForEach(repo.recentTracks.prefix(4)) { track in
                    TrackRowView(
                        track: track,
                        isCurrent: player.currentTrack?.id == track.id,
                        isPlaying: player.isPlaying,
                        isFavorite: repo.isFavorite(track.id),
                        isDownloaded: repo.downloadedIds.contains(track.id),
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
            .padding(.horizontal, 8)
        }
    }

    // "Популярно для тебя"
    private var popularForYouSection: some View {
        let tracks = popularForYou.isEmpty ? repo.favorites : popularForYou

        return VStack(alignment: .leading, spacing: 12) {
            Text("Популярно для тебя")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(tracks.prefix(8)) { track in
                        Button(action: {
                            player.playTrack(track, in: tracks)
                        }) {
                            VStack(alignment: .leading, spacing: 6) {
                                AsyncImage(url: URL(string: track.artworkUrl)) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Color.darkSurface
                                }
                                .frame(width: 140, height: 140)
                                .clipShape(RoundedRectangle(cornerRadius: 10))

                                Text(track.title)
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.textPrimary)
                                    .lineLimit(1)

                                Text(track.artist)
                                    .font(.system(size: 12))
                                    .foregroundColor(.textSecondary)
                                    .lineLimit(1)
                            }
                            .frame(width: 140)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    // "Популярные исполнители"
    private var popularArtistsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Популярные исполнители")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(popularArtists) { artist in
                        Button(action: {
                            onArtistClick(artist.name)
                        }) {
                            VStack(spacing: 8) {
                                AsyncImage(url: URL(string: artist.artworkUrl)) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    ZStack {
                                        Color.darkSurfaceVariant
                                        Text(String(artist.name.prefix(1)).uppercased())
                                            .font(.system(size: 32, weight: .bold))
                                            .foregroundColor(theme.currentTheme.light)
                                    }
                                }
                                .frame(width: 100, height: 100)
                                .clipShape(Circle())

                                Text(artist.name)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.textPrimary)
                                    .lineLimit(1)

                                Text("Исполнитель")
                                    .font(.system(size: 11))
                                    .foregroundColor(.textSecondary)
                            }
                            .frame(width: 105)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func loadInitialMusic() async {
        guard !isLoadingInitial else { return }
        isLoadingInitial = true

        let releases = await repo.getFavoriteReleases()
        await MainActor.run {
            self.favoriteReleases = releases
        }

        do {
            let popular = try await CloudifyAPI.shared.searchTracks(query: "phonk rap top music hits")
            await MainActor.run {
                self.popularForYou = popular
                self.isLoadingInitial = false
            }
        } catch {
            await MainActor.run {
                self.isLoadingInitial = false
            }
        }
    }
}
