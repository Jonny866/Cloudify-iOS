import SwiftUI
import UniformTypeIdentifiers

public enum LibraryTab: Int, CaseIterable {
    case favorites = 0
    case playlists = 1

    public var title: String {
        switch self {
        case .favorites: return "Любимые"
        case .playlists: return "Плейлисты"
        }
    }
}

public enum SortOrder: String, CaseIterable {
    case dateAdded = "По дате добавления"
    case title = "По названию"
    case artist = "По исполнителю"
}

public struct LibraryScreenView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var theme = ThemeManager.shared

    public let onArtistClick: (String) -> Void

    @State private var selectedTab: LibraryTab = .favorites
    @State private var sortOrder: SortOrder = .dateAdded
    @State private var showCreatePlaylistSheet: Bool = false
    @State private var newPlaylistName: String = ""
    @State private var actionTrack: Track? = nil
    @State private var showFileImporter: Bool = false
    @State private var isImporting: Bool = false
    @State private var importResultText: String? = nil

    public init(onArtistClick: @escaping (String) -> Void) {
        self.onArtistClick = onArtistClick
    }

    private var sortedFavorites: [Track] {
        switch sortOrder {
        case .dateAdded:
            return repo.favorites
        case .title:
            return repo.favorites.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        case .artist:
            return repo.favorites.sorted { $0.artist.localizedCaseInsensitiveCompare($1.artist) == .orderedAscending }
        }
    }

    private var nonDownloadedFavorites: [Track] {
        repo.favorites.filter { !repo.downloadedIds.contains($0.id) }
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header (Matching Android 7.9 LibraryScreen.kt)
            HStack {
                Text("Моя медиатека")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.textPrimary)

                Spacer()

                if repo.isSyncing || isImporting {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: theme.currentTheme.primary))
                        .scaleEffect(0.8)
                }

                if selectedTab == .playlists {
                    Button(action: {
                        showCreatePlaylistSheet = true
                    }) {
                        Image(systemName: "plus")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(theme.currentTheme.primary)
                            .frame(width: 36, height: 36)
                    }
                }

                Menu {
                    Button(action: { sortOrder = .dateAdded }) {
                        Label("По дате добавления", systemImage: sortOrder == .dateAdded ? "checkmark" : "")
                    }
                    Button(action: { sortOrder = .title }) {
                        Label("По названию", systemImage: sortOrder == .title ? "checkmark" : "")
                    }
                    Button(action: { sortOrder = .artist }) {
                        Label("По исполнителю", systemImage: sortOrder == .artist ? "checkmark" : "")
                    }
                    Divider()
                    Button(action: { showFileImporter = true }) {
                        Label("Импорт треков (.txt)", systemImage: "doc.badge.plus")
                    }
                } label: {
                    Image(systemName: "arrow.up.arrow.down")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.textSecondary)
                        .frame(width: 36, height: 36)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 10)

            // Primary Tabs: "Любимые" and "Плейлисты" (Matching Android 7.9 exactly)
            HStack(spacing: 0) {
                tabButton(tab: .favorites, label: "Любимые (\(repo.favorites.count))")
                tabButton(tab: .playlists, label: "Плейлисты")
            }
            .background(Color.deepBlack)

            Divider()
                .background(Color.darkBorder)

            // Tab Content
            ScrollView(.vertical, showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    if let resultMsg = importResultText {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.accentGreen)
                            Text(resultMsg)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.textPrimary)
                            Spacer()
                            Button(action: { importResultText = nil }) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 12))
                                    .foregroundColor(.textMuted)
                            }
                        }
                        .padding(12)
                        .background(Color.darkSurfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal, 16)
                        .padding(.top, 10)
                    }

                    switch selectedTab {
                    case .favorites:
                        favoritesActionHeader
                        if repo.favorites.isEmpty {
                            favoritesEmptyState
                        } else {
                            ForEach(sortedFavorites) { track in
                                TrackRowView(
                                    track: track,
                                    isCurrent: player.currentTrack?.id == track.id,
                                    isPlaying: player.isPlaying,
                                    isFavorite: true,
                                    isDownloaded: repo.downloadedIds.contains(track.id),
                                    isDownloading: repo.activeDownloads.contains(track.id),
                                    onPlay: {
                                        player.playTrack(track, in: sortedFavorites, source: "Любимые треки")
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
                    case .playlists:
                        playlistsTabContent
                    }

                    Spacer(minLength: 120)
                }
            }
        }
        .background(Color.deepBlack.edgesIgnoringSafeArea(.all))
        .alert("Новый плейлист", isPresented: $showCreatePlaylistSheet) {
            TextField("Название плейлиста", text: $newPlaylistName)
            Button("Создать") {
                let trimmed = newPlaylistName.trimmingCharacters(in: .whitespaces)
                if !trimmed.isEmpty {
                    repo.createPlaylist(name: trimmed)
                    newPlaylistName = ""
                }
            }
            Button("Отмена", role: .cancel) {}
        }
        .sheet(isPresented: $showFileImporter) {
            DocumentPickerView(
                onPick: { url in
                    showFileImporter = false
                    handleImportedFile(url: url)
                },
                onCancel: {
                    showFileImporter = false
                }
            )
            .ignoresSafeArea(.all)
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

    private func handleImportedFile(url: URL) {
        var textContent: String?
        if let data = try? Data(contentsOf: url) {
            textContent = String(data: data, encoding: .utf8)
                ?? String(data: data, encoding: .windowsCP1251)
                ?? String(data: data, encoding: .utf16)
                ?? String(data: data, encoding: .ascii)
        } else if let str = try? String(contentsOf: url, encoding: .utf8) {
            textContent = str
        }

        try? FileManager.default.removeItem(at: url)

        guard let text = textContent, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            importResultText = "Файл пуст или повреждён"
            return
        }

        Task {
            await MainActor.run { isImporting = true }
            let res = await repo.importTracksFromText(text)
            await MainActor.run {
                isImporting = false
                repo.isImportCompleted = true
                importResultText = "Импортировано \(res.imported) из \(res.total) треков"
            }
        }
    }

    private func tabButton(tab: LibraryTab, label: String) -> some View {
        let isSelected = selectedTab == tab

        return Button(action: {
            withAnimation(.easeInOut(duration: 0.15)) {
                selectedTab = tab
            }
        }) {
            VStack(spacing: 6) {
                Text(label)
                    .font(.system(size: 14, weight: isSelected ? .bold : .medium))
                    .foregroundColor(isSelected ? theme.currentTheme.light : .textMuted)

                Rectangle()
                    .fill(isSelected ? theme.currentTheme.primary : Color.clear)
                    .frame(height: 2)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 10)
        }
    }

    // Favorites Action Header Row (Matching Android 7.9)
    private var favoritesActionHeader: some View {
        HStack(spacing: 8) {
            if !nonDownloadedFavorites.isEmpty {
                Button(action: {
                    if !repo.isProcessingQueue {
                        repo.downloadAllTracks(nonDownloadedFavorites)
                    }
                }) {
                    HStack(spacing: 6) {
                        if repo.isProcessingQueue {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: theme.currentTheme == .yellow ? .black : .white))
                            Text("Загрузка...")
                                .font(.system(size: 12, weight: .bold))
                                .lineLimit(1)
                        } else {
                            Image(systemName: "arrow.down.circle.fill")
                                .font(.system(size: 14))
                            Text(nonDownloadedFavorites.count == repo.favorites.count ? "Скачать все" : "Скачать (\(nonDownloadedFavorites.count))")
                                .font(.system(size: 12, weight: .bold))
                                .lineLimit(1)
                        }
                    }
                    .foregroundColor(theme.currentTheme == .yellow ? .black : .white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(theme.currentTheme.primary)
                    .clipShape(Capsule())
                }
            }

            if !repo.isImportCompleted {
                Button(action: {
                    showFileImporter = true
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "square.and.arrow.down")
                            .font(.system(size: 13))
                        Text("Импорт .txt")
                            .font(.system(size: 12, weight: .semibold))
                            .lineLimit(1)
                    }
                    .foregroundColor(theme.currentTheme.light)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.darkSurfaceVariant)
                    .clipShape(Capsule())
                    .overlay(
                        Capsule().stroke(theme.currentTheme.light.opacity(0.3), lineWidth: 1)
                    )
                }
            }

            if !repo.favorites.isEmpty {
                Button(action: {
                    let shuffled = repo.favorites.shuffled()
                    if let first = shuffled.first {
                        player.playTrack(first, in: shuffled, source: "Любимые треки")
                    }
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "shuffle")
                            .font(.system(size: 14))
                        Text("Перемешать")
                            .font(.system(size: 12, weight: .semibold))
                            .lineLimit(1)
                    }
                    .foregroundColor(.textPrimary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.darkSurfaceVariant)
                    .clipShape(Capsule())
                }
            }

            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 4)
    }

    private var favoritesEmptyState: some View {
        VStack(spacing: 12) {
            Spacer(minLength: 40)
            Image(systemName: "heart.slash")
                .font(.system(size: 48))
                .foregroundColor(theme.currentTheme.light)

            Text("Нет любимых треков")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.textPrimary)

            Text("Нажимайте на сердечко у любых песен или нажмите кнопку «Импорт .txt» для загрузки треков из Яндекс Музыки, ВК или Spotify")
                .font(.system(size: 13))
                .foregroundColor(.textSecondary)
                .multilineTextAlignment(.center)
                .lineSpacing(4)
                .padding(.horizontal, 32)

            Button(action: { showFileImporter = true }) {
                HStack(spacing: 8) {
                    Image(systemName: "square.and.arrow.down")
                    Text("Импортировать треки из .txt")
                        .fontWeight(.bold)
                }
                .foregroundColor(theme.currentTheme == .yellow ? .black : .white)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(theme.currentTheme.primary)
                .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .padding(.top, 10)

            Spacer(minLength: 80)
        }
    }

    // Playlists Tab
    private var playlistsTabContent: some View {
        VStack(spacing: 12) {
            if repo.playlists.isEmpty {
                VStack(spacing: 12) {
                    Spacer(minLength: 40)
                    Image(systemName: "music.note.list")
                        .font(.system(size: 48))
                        .foregroundColor(theme.currentTheme.light)

                    Text("Нет плейлистов")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.textPrimary)

                    Text("Создавайте персональные подборки и слушайте любимую музыку без ограничений")
                        .font(.system(size: 13))
                        .foregroundColor(.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)

                    Button(action: { showCreatePlaylistSheet = true }) {
                        HStack(spacing: 8) {
                            Image(systemName: "plus")
                            Text("Создать первый плейлист")
                                .fontWeight(.bold)
                        }
                        .foregroundColor(theme.currentTheme == .yellow ? .black : .white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(theme.currentTheme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .padding(.top, 10)

                    Spacer(minLength: 80)
                }
            } else {
                ForEach(repo.playlists) { pl in
                    HStack(spacing: 14) {
                        AsyncImage(url: URL(string: pl.artworkUrl)) { img in
                            img.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            ZStack {
                                Color.darkSurfaceVariant
                                Image(systemName: "music.note.list")
                                    .foregroundColor(theme.currentTheme.light)
                            }
                        }
                        .frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 10))

                        VStack(alignment: .leading, spacing: 3) {
                            Text(pl.name)
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.textPrimary)
                                .lineLimit(1)

                            Text("\(pl.trackCount) треков")
                                .font(.system(size: 12))
                                .foregroundColor(.textSecondary)
                        }

                        Spacer()

                        Image(systemName: "chevron.right")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.textMuted)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }
            }
        }
        .padding(.top, 10)
    }
}

// Native Document Picker ensuring .txt and other files are selectable on iOS
public struct DocumentPickerView: UIViewControllerRepresentable {
    public let onPick: (URL) -> Void
    public var onCancel: (() -> Void)? = nil

    public init(onPick: @escaping (URL) -> Void, onCancel: (() -> Void)? = nil) {
        self.onPick = onPick
        self.onCancel = onCancel
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    public func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let types: [UTType] = [
            .plainText,
            .text,
            .utf8PlainText,
            UTType(filenameExtension: "txt") ?? .plainText,
            .item,
            .data
        ]
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        picker.shouldShowFileExtensions = true
        return picker
    }

    public func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    public class Coordinator: NSObject, UIDocumentPickerDelegate {
        let parent: DocumentPickerView

        init(parent: DocumentPickerView) {
            self.parent = parent
        }

        public func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else { return }
            parent.onPick(url)
        }

        public func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            parent.onCancel?()
        }
    }
}
