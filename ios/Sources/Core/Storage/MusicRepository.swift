import Foundation
import UIKit
import SwiftUI
import UserNotifications
import QuartzCore

public struct DownloadBannerState: Equatable {
    public let trackTitle: String
    public let currentProgress: Double // 0.0 to 1.0
    public let currentItem: Int
    public let totalItems: Int
    public let isCompleted: Bool

    public init(trackTitle: String, currentProgress: Double, currentItem: Int, totalItems: Int, isCompleted: Bool) {
        self.trackTitle = trackTitle
        self.currentProgress = currentProgress
        self.currentItem = currentItem
        self.totalItems = totalItems
        self.isCompleted = isCompleted
    }
}

public final class MusicRepository: ObservableObject {
    public static let shared = MusicRepository()

    @Published public var favorites: [Track] = []
    @Published public var favoriteIds: Set<String> = []
    @Published public var playlists: [Playlist] = []
    @Published public var favoriteArtists: Set<String> = []
    @Published public var recentTracks: [Track] = []
    @Published public var downloadedTracks: [Track] = []
    @Published public var downloadedIds: Set<String> = []
    @Published public var user: UserProfile = UserProfile()
    @Published public var isSyncing: Bool = false

    // Real-time Download Management
    @Published public var activeDownloads: Set<String> = []
    @Published public var downloadProgress: [String: Double] = [:]
    @Published public var downloadBanner: DownloadBannerState? = nil
    @Published public var downloadToastMessage: String? = nil
    @Published public var isProcessingQueue: Bool = false

    private var downloadQueue: [Track] = []
    private var currentBatchTotal: Int = 0
    private var currentBatchCompleted: Int = 0
    private var downloadTask: Task<Void, Never>? = nil

    private let documentsURL: URL

    private init() {
        documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        loadFromStorage()
    }

    // Persistence URLs
    private var favoritesFileURL: URL { documentsURL.appendingPathComponent("favorites.json") }
    private var playlistsFileURL: URL { documentsURL.appendingPathComponent("playlists.json") }
    private var artistsFileURL: URL { documentsURL.appendingPathComponent("artists.json") }
    private var userFileURL: URL { documentsURL.appendingPathComponent("user.json") }
    private var recentsFileURL: URL { documentsURL.appendingPathComponent("recents.json") }
    private var downloadsFileURL: URL { documentsURL.appendingPathComponent("downloads.json") }

    @Published public var isImportCompleted: Bool = UserDefaults.standard.bool(forKey: "cloudify_import_completed") {
        didSet { UserDefaults.standard.set(isImportCompleted, forKey: "cloudify_import_completed") }
    }

    private func loadFromStorage() {
        let decoder = JSONDecoder()

        if let data = try? Data(contentsOf: favoritesFileURL),
           let saved = try? decoder.decode([Track].self, from: data) {
            self.favorites = saved
            self.favoriteIds = Set(saved.map { $0.id })
        }

        if let data = try? Data(contentsOf: playlistsFileURL),
           let saved = try? decoder.decode([Playlist].self, from: data) {
            self.playlists = saved
        }

        if let data = try? Data(contentsOf: artistsFileURL),
           let saved = try? decoder.decode([String].self, from: data) {
            self.favoriteArtists = Set(saved)
        }

        if let data = try? Data(contentsOf: userFileURL),
           let saved = try? decoder.decode(UserProfile.self, from: data) {
            self.user = saved
        }

        if let data = try? Data(contentsOf: recentsFileURL),
           let saved = try? decoder.decode([Track].self, from: data) {
            self.recentTracks = saved
        }

        if let data = try? Data(contentsOf: downloadsFileURL),
           let saved = try? decoder.decode([Track].self, from: data) {
            let verified = saved.filter { track in
                guard let path = track.localFilePath, !path.isEmpty else { return false }
                return FileManager.default.fileExists(atPath: path)
            }
            self.downloadedTracks = verified
            self.downloadedIds = Set(verified.map { $0.id })
        }

        // Sync isDownloaded flag and local artwork with favorites
        for i in 0..<self.favorites.count {
            let safeId = self.favorites[i].id.replacingOccurrences(of: "[^a-zA-Z0-9_]", with: "_", options: .regularExpression)
            let artPath = documentsURL.appendingPathComponent("Downloads").appendingPathComponent("art_\(safeId).jpg").path
            if FileManager.default.fileExists(atPath: artPath) {
                self.favorites[i].localArtworkPath = artPath
            }
            if self.downloadedIds.contains(self.favorites[i].id) {
                self.favorites[i].isDownloaded = true
                let safeName = "track_\(safeId).mp3"
                self.favorites[i].localFilePath = documentsURL.appendingPathComponent("Downloads").appendingPathComponent(safeName).path
            }
        }
    }

    public func saveToStorage() {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .prettyPrinted

        try? (try? encoder.encode(favorites))?.write(to: favoritesFileURL)
        try? (try? encoder.encode(playlists))?.write(to: playlistsFileURL)
        try? (try? encoder.encode(Array(favoriteArtists)))?.write(to: artistsFileURL)
        try? (try? encoder.encode(user))?.write(to: userFileURL)
        try? (try? encoder.encode(recentTracks))?.write(to: recentsFileURL)
        try? (try? encoder.encode(downloadedTracks))?.write(to: downloadsFileURL)
    }

    // Favorites
    public func toggleFavorite(_ track: Track) {
        var mutableTrack = track
        if favoriteIds.contains(track.id) {
            favoriteIds.remove(track.id)
            favorites.removeAll { $0.id == track.id }
        } else {
            favoriteIds.insert(track.id)
            mutableTrack.isFavorite = true
            favorites.insert(mutableTrack, at: 0)
        }
        saveToStorage()

        if user.isLoggedIn {
            Task { await syncToCloud() }
        }
    }

    public func isFavorite(_ trackId: String) -> Bool {
        favoriteIds.contains(trackId)
    }

    // Artists
    public func toggleArtistFavorite(name: String) {
        if favoriteArtists.contains(name) {
            favoriteArtists.remove(name)
        } else {
            favoriteArtists.insert(name)
        }
        saveToStorage()

        if user.isLoggedIn {
            Task { await syncToCloud() }
        }
    }

    public func getPopularArtists() -> [Artist] {
        var list: [Artist] = []
        var seenArtists = Set<String>()

        // 1. Gather artists from user's favorites & listening history (Personalized, NO hardcoded mocks)
        let candidateTracks = favorites + recentTracks
        for track in candidateTracks {
            let artistName = track.artist.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !artistName.isEmpty, !seenArtists.contains(artistName.lowercased()) else { continue }
            seenArtists.insert(artistName.lowercased())

            list.append(
                Artist(
                    id: "artist_\(abs(artistName.hashValue))",
                    name: artistName,
                    artworkUrl: track.artworkUrl,
                    genre: "Исполнитель",
                    monthlyListeners: "Популярно у вас",
                    isFavorite: favoriteArtists.contains(artistName)
                )
            )
            if list.count >= 8 { break }
        }

        // 2. Favorite artist names if not already included
        for favArtist in favoriteArtists {
            guard !seenArtists.contains(favArtist.lowercased()) else { continue }
            seenArtists.insert(favArtist.lowercased())
            list.append(
                Artist(
                    id: "artist_\(abs(favArtist.hashValue))",
                    name: favArtist,
                    artworkUrl: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300",
                    genre: "Исполнитель",
                    monthlyListeners: "В избранном",
                    isFavorite: true
                )
            )
            if list.count >= 10 { break }
        }

        return list
    }

    public func getFavoriteReleases() async -> [ArtistRelease] {
        var releases: [ArtistRelease] = []
        var candidateNames: [String] = []

        if !favoriteArtists.isEmpty {
            candidateNames = Array(favoriteArtists)
        } else {
            var seen = Set<String>()
            for t in favorites + recentTracks {
                let name = t.artist.trimmingCharacters(in: .whitespacesAndNewlines)
                if !name.isEmpty && !seen.contains(name.lowercased()) {
                    seen.insert(name.lowercased())
                    candidateNames.append(name)
                    if candidateNames.count >= 5 { break }
                }
            }
        }

        for artistName in candidateNames {
            if let favTrack = favorites.first(where: { $0.artist.caseInsensitiveCompare(artistName) == .orderedSame }) {
                releases.append(ArtistRelease(
                    id: "rel_\(favTrack.id)",
                    artistName: artistName,
                    artistArtwork: favTrack.artworkUrl,
                    track: favTrack
                ))
            } else {
                do {
                    let tracks = try await CloudifyAPI.shared.searchTracks(query: artistName)
                    if let first = tracks.first {
                        releases.append(ArtistRelease(
                            id: "rel_\(first.id)",
                            artistName: artistName,
                            artistArtwork: first.artworkUrl,
                            track: first
                        ))
                    }
                } catch {}
            }
        }

        return releases
    }

    // Playlists
    public func createPlaylist(name: String, artworkUrl: String = "") {
        let newPl = Playlist(
            id: UUID().uuidString,
            name: name,
            artworkUrl: artworkUrl.isEmpty ? CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/artworks-MSGdnR5dCyyXfnbf-asrmMg-t500x500.jpg") : artworkUrl,
            trackCount: 0,
            isCustom: true
        )
        playlists.append(newPl)
        saveToStorage()

        if user.isLoggedIn {
            Task { await syncToCloud() }
        }
    }

    public func addTrackToPlaylist(playlistId: String, trackId: String) {
        guard let idx = playlists.firstIndex(where: { $0.id == playlistId }) else { return }
        if !playlists[idx].trackIds.contains(trackId) {
            playlists[idx].trackIds.append(trackId)
            playlists[idx].trackCount = playlists[idx].trackIds.count
            saveToStorage()
        }
    }

    // History & Listening Stats
    public func addRecentTrack(_ track: Track) {
        recentTracks.removeAll { $0.id == track.id }
        recentTracks.insert(track, at: 0)
        if recentTracks.count > 30 {
            recentTracks = Array(recentTracks.prefix(30))
        }
        saveToStorage()
    }

    public func addListenedSeconds(_ seconds: Int) {
        let current = UserDefaults.standard.integer(forKey: "cloudify_listened_sec")
        UserDefaults.standard.set(current + seconds, forKey: "cloudify_listened_sec")
    }

    public func getTotalListenedHours() -> Double {
        let seconds = UserDefaults.standard.integer(forKey: "cloudify_listened_sec")
        let hours = Double(seconds) / 3600.0
        return max(1.2, (hours * 10).rounded() / 10)
    }

    // Notification Support
    public func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    public func postNotification(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.1, repeats: false)
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }

    public func showDownloadToast(_ msg: String) {
        DispatchQueue.main.async {
            withAnimation { self.downloadToastMessage = msg }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                withAnimation { self.downloadToastMessage = nil }
            }
        }
    }

    private func updateBanner(title: String, progress: Double) {
        let currentIdx = min(self.currentBatchTotal, self.currentBatchCompleted + 1)
        self.downloadBanner = DownloadBannerState(
            trackTitle: title,
            currentProgress: progress,
            currentItem: currentIdx,
            totalItems: max(1, self.currentBatchTotal),
            isCompleted: false
        )
    }

    // Downloads
    public func downloadTrack(_ track: Track) {
        requestNotificationPermission()

        if downloadedIds.contains(track.id) {
            showDownloadToast("Трек «\(track.title)» уже скачан")
            return
        }

        if activeDownloads.contains(track.id) || downloadQueue.contains(where: { $0.id == track.id }) {
            showDownloadToast("Трек «\(track.title)» уже в очереди загрузки")
            return
        }

        if currentBatchTotal == 0 {
            currentBatchTotal = 1
            currentBatchCompleted = 0
        } else {
            currentBatchTotal += 1
        }

        activeDownloads.insert(track.id)
        downloadProgress[track.id] = 0.05
        downloadQueue.append(track)

        showDownloadToast("Скачивание: \(track.title)...")
        postNotification(title: "Скачивание трека", body: "«\(track.title)» (\(track.artist))")

        updateBanner(title: track.title, progress: 0.05)
        startQueueProcessingIfNeeded()
    }

    public func downloadAllTracks(_ tracks: [Track]) {
        requestNotificationPermission()

        let toDownload = tracks.filter { track in
            !downloadedIds.contains(track.id) &&
            !activeDownloads.contains(track.id) &&
            !downloadQueue.contains(where: { q in q.id == track.id })
        }

        if toDownload.isEmpty {
            showDownloadToast("Все треки уже скачаны")
            return
        }

        currentBatchTotal = currentBatchCompleted + toDownload.count
        for t in toDownload {
            activeDownloads.insert(t.id)
            downloadProgress[t.id] = 0.0
            downloadQueue.append(t)
        }

        showDownloadToast("Начато скачивание: \(toDownload.count) треков...")
        postNotification(title: "Cloudify: Загрузка началась", body: "Скачивание \(toDownload.count) треков...")

        if let first = toDownload.first {
            updateBanner(title: first.title, progress: 0.02)
        }
        startQueueProcessingIfNeeded()
    }

    private func startQueueProcessingIfNeeded() {
        guard !isProcessingQueue else { return }
        isProcessingQueue = true

        downloadTask?.cancel()
        downloadTask = Task {
            while !Task.isCancelled {
                var nextTrack: Track? = nil
                await MainActor.run {
                    if !self.downloadQueue.isEmpty {
                        nextTrack = self.downloadQueue.removeFirst()
                    }
                }

                guard let track = nextTrack else { break }
                await processSingleDownload(track)
            }

            await MainActor.run {
                self.isProcessingQueue = false
                let total = max(1, self.currentBatchTotal)
                self.downloadBanner = DownloadBannerState(
                    trackTitle: "Все треки скачаны (\(total))",
                    currentProgress: 1.0,
                    currentItem: total,
                    totalItems: total,
                    isCompleted: true
                )
                self.showDownloadToast("✓ Загрузка завершена (\(total) треков)")
                self.postNotification(title: "✓ Все треки скачаны", body: "Сохранено \(total) треков для офлайн прослушивания")

                self.currentBatchTotal = 0
                self.currentBatchCompleted = 0
            }

            try? await Task.sleep(nanoseconds: 3_500_000_000)
            await MainActor.run {
                if self.downloadQueue.isEmpty && self.activeDownloads.isEmpty {
                    withAnimation {
                        self.downloadBanner = nil
                    }
                }
            }
        }
    }

    private func processSingleDownload(_ track: Track) async {
        let destDir = documentsURL.appendingPathComponent("Downloads", isDirectory: true)
        try? FileManager.default.createDirectory(at: destDir, withIntermediateDirectories: true)

        let safeId = track.id.replacingOccurrences(of: "[^a-zA-Z0-9_]", with: "_", options: .regularExpression)
        let safeName = "track_\(safeId).mp3"
        let destURL = destDir.appendingPathComponent(safeName)

        // Check if file already exists on disk
        if FileManager.default.fileExists(atPath: destURL.path),
           let attr = try? FileManager.default.attributesOfItem(atPath: destURL.path),
           let size = attr[.size] as? Int64, size > 10000 {
            var localArtPath: String? = nil
            let safeArtName = "art_\(safeId).jpg"
            let artDestURL = destDir.appendingPathComponent(safeArtName)
            if FileManager.default.fileExists(atPath: artDestURL.path) {
                localArtPath = artDestURL.path
            } else if !track.artworkUrl.isEmpty,
                      let artURL = URL(string: track.artworkUrl),
                      let (artData, _) = try? await URLSession.shared.data(from: artURL),
                      artData.count > 500 {
                ArtworkCacheManager.shared.saveDownloadedArtwork(data: artData, urlString: track.artworkUrl, targetURL: artDestURL)
                localArtPath = artDestURL.path
            }

            await MainActor.run {
                self.markTrackAsDownloaded(track, localPath: destURL.path, localArtPath: localArtPath)
                self.activeDownloads.remove(track.id)
                self.downloadProgress[track.id] = 1.0
                self.currentBatchCompleted += 1
            }
            return
        }

        var streamUrlString = track.streamUrl
        if !streamUrlString.contains("/api/stream") && !streamUrlString.contains("/api/download") && !streamUrlString.hasPrefix("file:") {
            if let resolved = CloudifyAPI.getStreamUrl(webpageUrl: streamUrlString) {
                streamUrlString = resolved.absoluteString
            }
        }

        guard let url = URL(string: streamUrlString) else {
            await MainActor.run {
                _ = self.activeDownloads.remove(track.id)
            }
            return
        }

        var request = URLRequest(url: url)
        request.setValue("CloudifyApp/1.0", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 60

        do {
            let session = URLSession(configuration: .default)
            let (asyncBytes, response) = try await session.bytes(for: request)

            guard let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode) else {
                await MainActor.run {
                    _ = self.activeDownloads.remove(track.id)
                    self.showDownloadToast("Ошибка скачивания: \(track.title)")
                }
                return
            }

            let expectedLength = response.expectedContentLength
            var fileData = Data()
            if expectedLength > 0 {
                fileData.reserveCapacity(Int(expectedLength))
            }

            var lastReportTime: TimeInterval = 0

            for try await byte in asyncBytes {
                fileData.append(byte)
                if expectedLength > 0 {
                    let now = CACurrentMediaTime()
                    if now - lastReportTime > 0.2 {
                        lastReportTime = now
                        let pct = min(0.99, Double(fileData.count) / Double(expectedLength))
                        await MainActor.run {
                            self.downloadProgress[track.id] = pct
                            self.updateBanner(title: track.title, progress: pct)
                        }
                    }
                }
            }

            if fileData.count > 10000 {
                try fileData.write(to: destURL, options: .atomic)

                // Download and cache artwork alongside the track
                var localArtPath: String? = nil
                let safeArtName = "art_\(safeId).jpg"
                let artDestURL = destDir.appendingPathComponent(safeArtName)
                if !track.artworkUrl.isEmpty {
                    if let artURL = URL(string: track.artworkUrl),
                       let (artData, _) = try? await URLSession.shared.data(from: artURL),
                       artData.count > 500 {
                        ArtworkCacheManager.shared.saveDownloadedArtwork(data: artData, urlString: track.artworkUrl, targetURL: artDestURL)
                        localArtPath = artDestURL.path
                    }
                }

                await MainActor.run {
                    self.markTrackAsDownloaded(track, localPath: destURL.path, localArtPath: localArtPath)
                    _ = self.activeDownloads.remove(track.id)
                    self.downloadProgress[track.id] = 1.0
                    self.currentBatchCompleted += 1
                    self.showDownloadToast("✓ Скачано: \(track.title)")
                    self.postNotification(title: "✓ Скачано: \(track.title)", body: "\(track.artist) • доступно оффлайн")
                }
            } else {
                await MainActor.run {
                    _ = self.activeDownloads.remove(track.id)
                    self.showDownloadToast("Ошибка файла: \(track.title)")
                }
            }
        } catch {
            await MainActor.run {
                _ = self.activeDownloads.remove(track.id)
                self.showDownloadToast("Сбой соединения: \(track.title)")
            }
        }
    }

    private func markTrackAsDownloaded(_ track: Track, localPath: String, localArtPath: String? = nil) {
        var downloaded = track
        downloaded.isDownloaded = true
        downloaded.localFilePath = localPath
        if let art = localArtPath {
            downloaded.localArtworkPath = art
        }
        downloaded.streamUrl = URL(fileURLWithPath: localPath).absoluteString

        if let idx = self.downloadedTracks.firstIndex(where: { $0.id == track.id }) {
            self.downloadedTracks[idx] = downloaded
        } else {
            self.downloadedTracks.append(downloaded)
        }
        self.downloadedIds.insert(track.id)

        for i in 0..<self.favorites.count {
            if self.favorites[i].id == track.id {
                self.favorites[i].isDownloaded = true
                self.favorites[i].localFilePath = localPath
                if let art = localArtPath {
                    self.favorites[i].localArtworkPath = art
                }
            }
        }

        for i in 0..<self.recentTracks.count {
            if self.recentTracks[i].id == track.id {
                self.recentTracks[i].isDownloaded = true
                self.recentTracks[i].localFilePath = localPath
                if let art = localArtPath {
                    self.recentTracks[i].localArtworkPath = art
                }
            }
        }

        self.saveToStorage()
    }

    public func getCacheSizeMb() -> Double {
        var size: Int64 = 0
        let urls = [documentsURL]
        for dir in urls {
            if let enumerator = FileManager.default.enumerator(at: dir, includingPropertiesForKeys: [.fileSizeKey]) {
                for case let fileURL as URL in enumerator {
                    if let res = try? fileURL.resourceValues(forKeys: [.fileSizeKey]), let fSize = res.fileSize {
                        size += Int64(fSize)
                    }
                }
            }
        }
        return Double(size) / (1024.0 * 1024.0)
    }

    public func clearCache() {
        let destDir = documentsURL.appendingPathComponent("Downloads", isDirectory: true)
        try? FileManager.default.removeItem(at: destDir)
        downloadedTracks.removeAll()
        downloadedIds.removeAll()
        for i in 0..<favorites.count {
            favorites[i].isDownloaded = false
            favorites[i].localFilePath = nil
            favorites[i].localArtworkPath = nil
        }
        for i in 0..<recentTracks.count {
            recentTracks[i].isDownloaded = false
            recentTracks[i].localFilePath = nil
            recentTracks[i].localArtworkPath = nil
        }
        ArtworkCacheManager.shared.clearAllCache()
        saveToStorage()
    }

    // Cloud Sync
    public func syncToCloud() async {
        guard user.isLoggedIn, !user.email.isEmpty else { return }
        await MainActor.run { isSyncing = true }

        let favSync = favorites.map {
            FavoriteSyncDto(
                id: $0.id,
                title: $0.title,
                artist: $0.artist,
                artworkUrl: $0.artworkUrl,
                streamUrl: $0.streamUrl,
                durationMs: $0.durationMs,
                genre: $0.genre
            )
        }

        let plSync = playlists.map {
            PlaylistSyncDto(
                id: $0.id,
                name: $0.name,
                artworkUrl: $0.artworkUrl,
                createdAt: $0.createdAt,
                trackIds: $0.trackIds
            )
        }

        let artSync = favoriteArtists.map {
            ArtistSyncDto(name: $0)
        }

        let payload = SyncPayloadDto(
            user: UserSyncDto(id: user.id, name: user.name, email: user.email, avatarUrl: user.avatarUrl),
            favorites: favSync,
            playlists: plSync,
            favoriteArtists: artSync
        )

        _ = try? await CloudifyAPI.shared.syncUserData(payload: payload)
        await MainActor.run { isSyncing = false }
    }

    @discardableResult
    public func restoreFromCloud(email: String, displayName: String? = nil, photoUrl: String? = nil) async -> (success: Bool, count: Int) {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let userId = "google_\(abs(cleanEmail.hashValue))"
        let name = displayName ?? (cleanEmail.components(separatedBy: "@").first?.capitalized ?? "Пользователь Cloudify")
        let avatar = photoUrl ?? ""

        await MainActor.run {
            self.isSyncing = true
        }

        do {
            let res = try await CloudifyAPI.shared.getUserData(userId: userId, email: cleanEmail)

            let restoredFavCount = await MainActor.run { () -> Int in
                let finalName = res.user?.name.isEmpty == false ? (res.user?.name ?? name) : name
                let finalAvatar = res.user?.avatarUrl.isEmpty == false ? (res.user?.avatarUrl ?? avatar) : avatar

                self.user = UserProfile(
                    id: userId,
                    name: finalName,
                    email: cleanEmail,
                    avatarUrl: finalAvatar,
                    isLoggedIn: true
                )

                if !res.favorites.isEmpty {
                    self.favorites = res.favorites.map {
                        Track(
                            id: $0.id,
                            title: $0.title,
                            artist: $0.artist,
                            artworkUrl: $0.artworkUrl,
                            streamUrl: $0.streamUrl,
                            durationMs: $0.durationMs,
                            genre: $0.genre,
                            isFavorite: true
                        )
                    }
                    self.favoriteIds = Set(self.favorites.map { $0.id })
                }

                if !res.playlists.isEmpty {
                    self.playlists = res.playlists.map {
                        Playlist(
                            id: $0.id,
                            name: $0.name,
                            artworkUrl: $0.artworkUrl,
                            trackCount: $0.trackIds.count,
                            createdAt: $0.createdAt,
                            trackIds: $0.trackIds
                        )
                    }
                }

                if !res.favoriteArtists.isEmpty {
                    self.favoriteArtists = Set(res.favoriteArtists.map { $0.name })
                }

                self.saveToStorage()
                self.isSyncing = false
                return self.favorites.count
            }

            // If new account with no cloud library, sync initial profile up
            if res.favorites.isEmpty && res.playlists.isEmpty {
                await syncToCloud()
            }

            return (success: true, count: restoredFavCount)
        } catch {
            await MainActor.run {
                self.user = UserProfile(id: userId, name: name, email: cleanEmail, avatarUrl: avatar, isLoggedIn: true)
                self.saveToStorage()
                self.isSyncing = false
            }
            return (success: false, count: 0)
        }
    }

    public func logout() {
        self.user = UserProfile()
        saveToStorage()
    }

    // Dynamic Monthly Stats (Calculated exactly like Android 7.9)
    public func getMonthlyStats() -> MonthlyStats {
        let monthNames = [
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        ]
        let cal = Calendar.current
        let monthIdx = cal.component(.month, from: Date()) - 1
        let year = cal.component(.year, from: Date())
        let currentMonthName = "\(monthNames[max(0, min(11, monthIdx))]) \(year)"

        let totalSeconds = UserDefaults.standard.double(forKey: "cloudify_listened_sec")
        let hoursListened: Double
        if totalSeconds > 0 {
            hoursListened = (totalSeconds / 3600.0 * 10.0).rounded() / 10.0
        } else if !recentTracks.isEmpty {
            hoursListened = (Double(recentTracks.count) * 2.0 / 60.0 * 10.0).rounded() / 10.0
        } else {
            hoursListened = 0.0
        }

        if recentTracks.isEmpty {
            return MonthlyStats(
                monthName: currentMonthName,
                topArtist: "Пока нет данных",
                topArtistArtwork: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300",
                topTrack: "Включайте треки",
                topTrackArtwork: "",
                topTrackPlays: 0,
                hoursListened: hoursListened
            )
        }

        let totalPlays = recentTracks.count

        // Group by artist
        var artistCounts: [String: Int] = [:]
        for track in recentTracks {
            artistCounts[track.artist, default: 0] += 1
        }
        let topArtistEntry = artistCounts.max(by: { $0.value < $1.value })
        let topArtist = topArtistEntry?.key ?? "Любимый исполнитель"

        var topArtistAvatar = ""
        if let match = recentTracks.first(where: { $0.artist == topArtist }) {
            topArtistAvatar = match.artworkUrl
        }
        if topArtistAvatar.isEmpty {
            if let popMatch = getPopularArtists().first(where: { $0.name.caseInsensitiveCompare(topArtist) == .orderedSame }) {
                topArtistAvatar = popMatch.artworkUrl
            }
        }
        if topArtistAvatar.isEmpty {
            topArtistAvatar = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300"
        }

        // Group by track ID
        var trackCounts: [String: Int] = [:]
        for track in recentTracks {
            trackCounts[track.id, default: 0] += 1
        }
        let topTrackEntry = trackCounts.max(by: { $0.value < $1.value })
        let topTrackMatch = recentTracks.first(where: { $0.id == topTrackEntry?.key })
        let topTrackTitle = topTrackMatch?.title ?? "Любимый трек"
        let topTrackArtwork = topTrackMatch?.artworkUrl ?? ""
        let topTrackPlays = topTrackEntry?.value ?? 1

        return MonthlyStats(
            monthName: currentMonthName,
            topArtist: topArtist,
            topArtistArtwork: topArtistAvatar,
            topTrack: topTrackTitle,
            topTrackArtwork: topTrackArtwork,
            topTrackPlays: topTrackPlays,
            hoursListened: hoursListened
        )
    }

    // Import tracks from .txt (Spotify / Yandex / VK export)
    public func importTracksFromText(_ rawText: String) async -> (imported: Int, total: Int) {
        let lines = rawText.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") && !$0.hasPrefix("//") }
            .map { line -> String in
                if let range = line.range(of: #"^\s*\[?\d+[.)\]\-:]*\s*"#, options: .regularExpression) {
                    var s = line
                    s.removeSubrange(range)
                    return s.trimmingCharacters(in: .whitespacesAndNewlines)
                }
                return line
            }
            .filter { !$0.isEmpty }

        guard !lines.isEmpty else { return (0, 0) }

        var count = 0
        for query in lines {
            do {
                let results = try await CloudifyAPI.shared.searchTracks(query: query)
                if let first = results.first {
                    await MainActor.run {
                        if !self.favoriteIds.contains(first.id) {
                            var t = first
                            t.isFavorite = true
                            self.favorites.append(t)
                            self.favoriteIds.insert(t.id)
                            count += 1
                        }
                    }
                }
            } catch {}
        }

        await MainActor.run {
            self.isImportCompleted = true
            self.saveToStorage()
        }
        if user.isLoggedIn {
            await syncToCloud()
        }
        return (count, lines.count)
    }


    // Audio Quality & Caching Settings (Matching Android 7.9)
    @Published public var audioQuality: String = UserDefaults.standard.string(forKey: "cloudify_audio_quality") ?? "Высокое (320 kbps)" {
        didSet { UserDefaults.standard.set(audioQuality, forKey: "cloudify_audio_quality") }
    }

    @Published public var isCachingEnabled: Bool = UserDefaults.standard.object(forKey: "cloudify_caching_enabled") as? Bool ?? true {
        didSet { UserDefaults.standard.set(isCachingEnabled, forKey: "cloudify_caching_enabled") }
    }

    public func setAudioQuality(_ quality: String) {
        self.audioQuality = quality
    }

    public func setCachingEnabled(_ enabled: Bool) {
        self.isCachingEnabled = enabled
    }
}
