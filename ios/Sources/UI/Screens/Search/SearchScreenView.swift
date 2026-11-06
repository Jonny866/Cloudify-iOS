import SwiftUI

public struct SearchScreenView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var theme = ThemeManager.shared

    public let onArtistClick: (String) -> Void

    @State private var query: String = ""
    @State private var searchResults: [Track] = []
    @State private var isSearching: Bool = false
    @State private var searchHistory: [String] = ["GONE.Fludd", "Miyagi", "KORDHELL", "Phonk"]
    @State private var actionTrack: Track? = nil
    @State private var searchTask: Task<Void, Never>? = nil

    private let genres = ["Хип-хоп", "Фонк", "Дрифт", "Рок", "Электроника", "Поп", "Рэп", "Лоу-фай"]

    public var body: some View {
        VStack(spacing: 14) {
            // Header
            HStack {
                Text("Поиск музыки")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)

            // Search Bar
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(theme.currentTheme.primary)
                    .font(.system(size: 18))

                TextField("Трек, артист или жанр...", text: $query)
                    .foregroundColor(.textPrimary)
                    .accentColor(theme.currentTheme.primary)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                    .onChange(of: query) { newQuery in
                        performSearchDebounced(newQuery)
                    }

                if !query.isEmpty {
                    Button(action: {
                        query = ""
                        searchResults = []
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.textSecondary)
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color.darkSurfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .padding(.horizontal, 16)

            // Content: Empty State vs Results
            if query.isEmpty {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 22) {
                        // Search History
                        if !searchHistory.isEmpty {
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    Text("История поиска")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.textPrimary)
                                    Spacer()
                                    Button("Очистить") {
                                        searchHistory.removeAll()
                                    }
                                    .font(.system(size: 12))
                                    .foregroundColor(.textMuted)
                                }

                                FlowLayout(spacing: 8) {
                                    ForEach(searchHistory, id: \.self) { item in
                                        Button(action: {
                                            query = item
                                            performSearch(item)
                                        }) {
                                            HStack(spacing: 6) {
                                                Image(systemName: "clock")
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.textMuted)
                                                Text(item)
                                                    .font(.system(size: 13, weight: .medium))
                                                    .foregroundColor(.textPrimary)
                                            }
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 7)
                                            .background(Color.darkSurfaceVariant)
                                            .clipShape(Capsule())
                                        }
                                    }
                                }
                            }
                        }

                        // Genres & Categories
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Популярные жанры")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.textPrimary)

                            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                                ForEach(genres, id: \.self) { genre in
                                    Button(action: {
                                        query = genre
                                        performSearch(genre)
                                    }) {
                                        ZStack(alignment: .bottomLeading) {
                                            RoundedRectangle(cornerRadius: 10)
                                                .fill(Color.darkSurfaceVariant)

                                            Text(genre)
                                                .font(.system(size: 15, weight: .bold))
                                                .foregroundColor(.textPrimary)
                                                .padding(14)
                                        }
                                        .frame(height: 70)
                                    }
                                }
                            }
                        }

                        Spacer(minLength: 120)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                }
            } else {
                // Results List
                if isSearching {
                    VStack {
                        Spacer()
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: theme.currentTheme.primary))
                            .scaleEffect(1.2)
                        Text("Поиск треков на сервере...")
                            .font(.system(size: 13))
                            .foregroundColor(.textSecondary)
                            .padding(.top, 10)
                        Spacer()
                    }
                } else if searchResults.isEmpty {
                    VStack(spacing: 12) {
                        Spacer()
                        Image(systemName: "music.note")
                            .font(.system(size: 44))
                            .foregroundColor(.textMuted)
                        Text("Ничего не найдено")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.textPrimary)
                        Text("Попробуйте изменить запрос")
                            .font(.system(size: 13))
                            .foregroundColor(.textMuted)
                        Spacer()
                    }
                } else {
                    ScrollView(.vertical, showsIndicators: false) {
                        LazyVStack(spacing: 4) {
                            ForEach(searchResults) { track in
                                TrackRowView(
                                    track: track,
                                    isCurrent: player.currentTrack?.id == track.id,
                                    isPlaying: player.isPlaying,
                                    isFavorite: repo.isFavorite(track.id),
                                    onPlay: {
                                        player.playTrack(track, in: searchResults)
                                        repo.addRecentTrack(track)
                                        if !searchHistory.contains(query) {
                                            searchHistory.insert(query, at: 0)
                                        }
                                    },
                                    onFavoriteToggle: {
                                        repo.toggleFavorite(track)
                                    },
                                    onMoreClick: {
                                        actionTrack = track
                                    }
                                )
                            }
                            Spacer(minLength: 120)
                        }
                    }
                }
            }
        }
        .background(Color.deepBlack.edgesIgnoringSafeArea(.all))
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

    private func performSearchDebounced(_ queryStr: String) {
        searchTask?.cancel()
        let trimmed = queryStr.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            searchResults = []
            isSearching = false
            return
        }

        searchTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000)
            if !Task.isCancelled {
                await performSearch(trimmed)
            }
        }
    }

    private func performSearch(_ term: String) {
        Task {
            await MainActor.run { isSearching = true }
            do {
                let results = try await CloudifyAPI.shared.searchTracks(query: term)
                await MainActor.run {
                    self.searchResults = results
                    self.isSearching = false
                }
            } catch {
                await MainActor.run {
                    self.isSearching = false
                }
            }
        }
    }
}

// FlowLayout for chips
private struct FlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.replacingUnspecifiedDimensions().width
        var currentX: CGFloat = 0
        var currentY: CGFloat = 0
        var lineHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if currentX + size.width > width {
                currentX = 0
                currentY += lineHeight + spacing
                lineHeight = 0
            }
            lineHeight = max(lineHeight, size.height)
            currentX += size.width + spacing
        }
        return CGSize(width: width, height: currentY + lineHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var currentX = bounds.minX
        var currentY = bounds.minY
        var lineHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if currentX + size.width > bounds.maxX {
                currentX = bounds.minX
                currentY += lineHeight + spacing
                lineHeight = 0
            }
            subview.place(at: CGPoint(x: currentX, y: currentY), proposal: .unspecified)
            lineHeight = max(lineHeight, size.height)
            currentX += size.width + spacing
        }
    }
}
