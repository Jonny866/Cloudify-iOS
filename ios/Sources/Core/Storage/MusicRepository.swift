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

    // Persistence
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

    private func saveToStorage() {
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

    // Playlists
    public func createPlaylist(name: String, artworkUrl: String = "") {
        let newPl = Playlist(
            id: UUID().uuidString,
            name: name,
            artworkUrl: artworkUrl.isEmpty ? "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500" : artworkUrl,
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

    // History
    public func addRecentTrack(_ track: Track) {
        recentTracks.removeAll { $0.id == track.id }
        recentTracks.insert(track, at: 0)
        if recentTracks.count > 30 {
            recentTracks = Array(recentTracks.prefix(30))
        }
        saveToStorage()
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

    public func restoreFromCloud(email: String, displayName: String? = nil) async {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let userId = "user_\(abs(cleanEmail.hashValue))"
        let name = displayName ?? (cleanEmail.components(separatedBy: "@").first?.capitalized ?? "Пользователь Cloudify")

        await MainActor.run {
            self.user = UserProfile(id: userId, name: name, email: cleanEmail, isLoggedIn: true)
            self.saveToStorage()
            self.isSyncing = true
        }

        if let res = try? await CloudifyAPI.shared.getUserData(userId: userId, email: cleanEmail) {
            await MainActor.run {
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
            }
        } else {
            await MainActor.run { self.isSyncing = false }
        }
    }

    // Default Seed Data (gone fludd, etc.)
    private func seedDefaultTracks() {
        let seeds: [Track] = [
            Track(
                id: "demo_fludd_1",
                title: "КОД ГИАС",
                artist: "GONE.Fludd",
                artworkUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500",
                streamUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                genre: "Hip-Hop",
                isFavorite: true
            ),
            Track(
                id: "demo_fludd_2",
                title: "Свежесть",
                artist: "GONE.Fludd",
                artworkUrl: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500",
                streamUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                genre: "Hip-Hop",
                isFavorite: true
            ),
            Track(
                id: "demo_miyagi_1",
                title: "Utopia",
                artist: "Miyagi & Эндшпиль",
                artworkUrl: "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500",
                streamUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                genre: "Reggae/Hip-Hop",
                isFavorite: true
            ),
            Track(
                id: "demo_kordhell_1",
                title: "Murder In My Mind",
                artist: "KORDHELL",
                artworkUrl: "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500",
                streamUrl: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                genre: "Phonk",
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
                artworkUrl: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500",
                trackCount: seeds.count,
                isCustom: false,
                trackIds: seeds.map { $0.id }
            )
        ]
        saveToStorage()
    }
}
