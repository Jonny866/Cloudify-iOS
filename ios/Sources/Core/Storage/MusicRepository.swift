import Foundation
import UIKit

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

    private let documentsURL: URL

    private init() {
        documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        loadFromStorage()
        if favorites.isEmpty {
            seedDefaultTracks()
        }
    }

    // Persistence URLs
    private var favoritesFileURL: URL { documentsURL.appendingPathComponent("favorites.json") }
    private var playlistsFileURL: URL { documentsURL.appendingPathComponent("playlists.json") }
    private var artistsFileURL: URL { documentsURL.appendingPathComponent("artists.json") }
    private var userFileURL: URL { documentsURL.appendingPathComponent("user.json") }
    private var recentsFileURL: URL { documentsURL.appendingPathComponent("recents.json") }

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
    }

    public func saveToStorage() {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .prettyPrinted

        try? (try? encoder.encode(favorites))?.write(to: favoritesFileURL)
        try? (try? encoder.encode(playlists))?.write(to: playlistsFileURL)
        try? (try? encoder.encode(Array(favoriteArtists)))?.write(to: artistsFileURL)
        try? (try? encoder.encode(user))?.write(to: userFileURL)
        try? (try? encoder.encode(recentTracks))?.write(to: recentsFileURL)
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
        return [
            Artist(
                id: "a_dabbackwood",
                name: "dabbackwood",
                artworkUrl: CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/avatars-zmVG9KSLGIaTyCxA-schJmA-t500x500.jpg"),
                genre: "Hip-Hop / Rap",
                monthlyListeners: "215 тыс. слушателей",
                isFavorite: favoriteArtists.contains("dabbackwood")
            ),
            Artist(
                id: "a_gone",
                name: "GONE.Fludd",
                artworkUrl: CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/avatars-qcDcKZDyCbj8ykiQ-FEOkHA-t500x500.jpg"),
                genre: "Hip-Hop / Rap",
                monthlyListeners: "839.6 тыс. слушателей",
                isFavorite: favoriteArtists.contains("GONE.Fludd")
            ),
            Artist(
                id: "a_miyagi",
                name: "Miyagi & Эндшпиль",
                artworkUrl: CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/avatars-zK5Rj5tpjLZYizRW-a6bEXA-t500x500.jpg"),
                genre: "Hip-Hop",
                monthlyListeners: "3.8M слушателей",
                isFavorite: favoriteArtists.contains("Miyagi & Эндшпиль")
            ),
            Artist(
                id: "a_kordhell",
                name: "KORDHELL",
                artworkUrl: CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/avatars-IwkAewk5iACtjj7t-mjkT9g-t500x500.jpg"),
                genre: "Phonk",
                monthlyListeners: "4.2M слушателей",
                isFavorite: favoriteArtists.contains("KORDHELL")
            ),
            Artist(
                id: "a_dvrst",
                name: "DVRST",
                artworkUrl: CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/avatars-91G3KMdmclZj1XN2-blvgeQ-t500x500.jpg"),
                genre: "Drift Phonk",
                monthlyListeners: "2.9M слушателей",
                isFavorite: favoriteArtists.contains("DVRST")
            )
        ]
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

    // Downloads
    public func downloadTrack(_ track: Track) {
        guard !downloadedIds.contains(track.id) else { return }
        guard let url = URL(string: track.streamUrl) else { return }

        let destDir = documentsURL.appendingPathComponent("Downloads", isDirectory: true)
        try? FileManager.default.createDirectory(at: destDir, withIntermediateDirectories: true)

        let safeName = "track_\(track.id.replacingOccurrences(of: "[^a-zA-Z0-9_]", with: "_", options: .regularExpression)).mp3"
        let destURL = destDir.appendingPathComponent(safeName)

        URLSession.shared.downloadTask(with: url) { [weak self] tempURL, _, error in
            guard let self = self, let tempURL = tempURL, error == nil else { return }
            do {
                if FileManager.default.fileExists(atPath: destURL.path) {
                    try FileManager.default.removeItem(at: destURL)
                }
                try FileManager.default.moveItem(at: tempURL, to: destURL)

                DispatchQueue.main.async {
                    var downloaded = track
                    downloaded.isDownloaded = true
                    downloaded.localFilePath = destURL.path
                    downloaded.streamUrl = destURL.absoluteString
                    self.downloadedTracks.append(downloaded)
                    self.downloadedIds.insert(track.id)
                }
            } catch {
                print("Download failed: \(error)")
            }
        }.resume()
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

    // Default Seed Data from real Cloudify backend
    private func seedDefaultTracks() {
        let streamGoneFludd = CloudifyAPI.getStreamUrl(webpageUrl: "https://soundcloud.com/gonefluddmus/kod-gias")?.absoluteString ?? "http://de-bots6.h1cloud.net:25066/api/stream?url=https%3A%2F%2Fsoundcloud.com%2Fgonefluddmus%2Fkod-gias"
        let coverGoneFludd = CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/artworks-MSGdnR5dCyyXfnbf-asrmMg-t500x500.jpg")

        let streamMiyagi = CloudifyAPI.getStreamUrl(webpageUrl: "https://soundcloud.com/miyagi_black/minor")?.absoluteString ?? "http://de-bots6.h1cloud.net:25066/api/stream?url=https%3A%2F%2Fsoundcloud.com%2Fmiyagi_black%2Fminor"
        let coverMiyagi = CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/artworks-HY4P9FmLUzbNs8YA-ommqfw-t500x500.jpg")

        let streamKordhell = CloudifyAPI.getStreamUrl(webpageUrl: "https://soundcloud.com/kordhell/murder-in-my-mind")?.absoluteString ?? "http://de-bots6.h1cloud.net:25066/api/stream?url=https%3A%2F%2Fsoundcloud.com%2Fkordhell%2Fmurder-in-my-mind"
        let coverKordhell = CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/artworks-IwkAewk5iACtjj7t-mjkT9g-t500x500.jpg")

        let streamDvrst = CloudifyAPI.getStreamUrl(webpageUrl: "https://soundcloud.com/dvrst/close-eyes")?.absoluteString ?? "http://de-bots6.h1cloud.net:25066/api/stream?url=https%3A%2F%2Fsoundcloud.com%2Fdvrst%2Fclose-eyes"
        let coverDvrst = CloudifyAPI.getCoverUrl(originalThumbnailUrl: "https://i1.sndcdn.com/artworks-91G3KMdmclZj1XN2-blvgeQ-t500x500.jpg")

        let seeds: [Track] = [
            Track(
                id: "2428883184",
                title: "КОД ГИАС",
                artist: "GONE.Fludd",
                artworkUrl: coverGoneFludd,
                streamUrl: streamGoneFludd,
                durationMs: 121655,
                genre: "Hip-Hop",
                isFavorite: true
            ),
            Track(
                id: "859281946",
                title: "Minor",
                artist: "Miyagi & Эндшпиль",
                artworkUrl: coverMiyagi,
                streamUrl: streamMiyagi,
                durationMs: 175846,
                genre: "Hip-Hop",
                isFavorite: true
            ),
            Track(
                id: "sc_kordhell_mim",
                title: "Murder In My Mind",
                artist: "KORDHELL",
                artworkUrl: coverKordhell,
                streamUrl: streamKordhell,
                durationMs: 145000,
                genre: "Phonk",
                isFavorite: true
            ),
            Track(
                id: "sc_dvrst_ce",
                title: "Close Eyes",
                artist: "DVRST",
                artworkUrl: coverDvrst,
                streamUrl: streamDvrst,
                durationMs: 132000,
                genre: "Drift Phonk",
                isFavorite: true
            )
        ]

        self.favorites = seeds
        self.favoriteIds = Set(seeds.map { $0.id })
        self.favoriteArtists = ["GONE.Fludd", "Miyagi & Эндшпиль", "KORDHELL", "DVRST", "dabbackwood"]
        self.playlists = [
            Playlist(
                id: "pl_favorites",
                name: "Любимые треки",
                artworkUrl: coverGoneFludd,
                trackCount: seeds.count,
                isCustom: false,
                trackIds: seeds.map { $0.id }
            )
        ]
        saveToStorage()
    }
}
