import Foundation

public struct Track: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var title: String
    public var artist: String
    public var artworkUrl: String
    public var streamUrl: String
    public var durationMs: Int64
    public var genre: String
    public var playsCount: String
    public var lyrics: String
    public var isFavorite: Bool
    public var isDownloaded: Bool
    public var localFilePath: String?

    public init(
        id: String,
        title: String,
        artist: String,
        artworkUrl: String,
        streamUrl: String,
        durationMs: Int64 = 180000,
        genre: String = "SoundCloud",
        playsCount: String = "2,419,050",
        lyrics: String = "Текст трека доступен в плеере...",
        isFavorite: Bool = false,
        isDownloaded: Bool = false,
        localFilePath: String? = nil
    ) {
        self.id = id
        self.title = title
        self.artist = artist
        self.artworkUrl = artworkUrl
        self.streamUrl = streamUrl
        self.durationMs = durationMs
        self.genre = genre
        self.playsCount = playsCount
        self.lyrics = lyrics
        self.isFavorite = isFavorite
        self.isDownloaded = isDownloaded
        self.localFilePath = localFilePath
    }

    public var durationFormatted: String {
        let totalSeconds = max(0, durationMs / 1000)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}

public struct Artist: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var name: String
    public var artworkUrl: String
    public var genre: String
    public var monthlyListeners: String
    public var isFavorite: Bool

    public init(
        id: String,
        name: String,
        artworkUrl: String,
        genre: String = "Исполнитель",
        monthlyListeners: String = "839.6 тыс. слушателей",
        isFavorite: Bool = false
    ) {
        self.id = id
        self.name = name
        self.artworkUrl = artworkUrl
        self.genre = genre
        self.monthlyListeners = monthlyListeners
        self.isFavorite = isFavorite
    }
}

public struct Album: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var title: String
    public var artist: String
    public var artworkUrl: String
    public var year: String
    public var trackCount: Int
    public var tracks: [Track]

    public init(
        id: String,
        title: String,
        artist: String,
        artworkUrl: String,
        year: String = "2024",
        trackCount: Int = 0,
        tracks: [Track] = []
    ) {
        self.id = id
        self.title = title
        self.artist = artist
        self.artworkUrl = artworkUrl
        self.year = year
        self.trackCount = trackCount
        self.tracks = tracks
    }
}

public struct ArtistDetail: Equatable {
    public var artist: Artist
    public var popularTracks: [Track]
    public var allTracks: [Track]
    public var albums: [Album]
    public var newRelease: Track?
    public var isFavorite: Bool

    public init(
        artist: Artist,
        popularTracks: [Track] = [],
        allTracks: [Track] = [],
        albums: [Album] = [],
        newRelease: Track? = nil,
        isFavorite: Bool = false
    ) {
        self.artist = artist
        self.popularTracks = popularTracks
        self.allTracks = allTracks
        self.albums = albums
        self.newRelease = newRelease
        self.isFavorite = isFavorite
    }
}

public struct Playlist: Identifiable, Codable, Equatable, Hashable {
    public let id: String
    public var name: String
    public var artworkUrl: String
    public var trackCount: Int
    public var isCustom: Bool
    public var createdAt: Int64
    public var trackIds: [String]

    public init(
        id: String,
        name: String,
        artworkUrl: String,
        trackCount: Int = 0,
        isCustom: Bool = true,
        createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        trackIds: [String] = []
    ) {
        self.id = id
        self.name = name
        self.artworkUrl = artworkUrl
        self.trackCount = trackCount
        self.isCustom = isCustom
        self.createdAt = createdAt
        self.trackIds = trackIds
    }
}

public struct UserProfile: Codable, Equatable {
    public var id: String
    public var name: String
    public var email: String
    public var avatarUrl: String
    public var isLoggedIn: Bool

    public init(
        id: String = "guest",
        name: String = "Гость",
        email: String = "Войдите для сохранения плейлистов",
        avatarUrl: String = "",
        isLoggedIn: Bool = false
    ) {
        self.id = id
        self.name = name
        self.email = email
        self.avatarUrl = avatarUrl
        self.isLoggedIn = isLoggedIn
    }
}

public struct MonthlyStats: Codable, Equatable {
    public var monthName: String
    public var topArtist: String
    public var topArtistArtwork: String
    public var topTrack: String
    public var topTrackArtwork: String
    public var topTrackPlays: Int
    public var hoursListened: Double
    public var topGenre: String
    public var genrePercent: Int

    public init(
        monthName: String = "",
        topArtist: String = "Пока нет данных",
        topArtistArtwork: String = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300",
        topTrack: String = "Включайте треки",
        topTrackArtwork: String = "",
        topTrackPlays: Int = 0,
        hoursListened: Double = 0.0,
        topGenre: String = "Определяется",
        genrePercent: Int = 0
    ) {
        self.monthName = monthName
        self.topArtist = topArtist
        self.topArtistArtwork = topArtistArtwork
        self.topTrack = topTrack
        self.topTrackArtwork = topTrackArtwork
        self.topTrackPlays = topTrackPlays
        self.hoursListened = hoursListened
        self.topGenre = topGenre
        self.genrePercent = genrePercent
    }
}

public struct QuickAccessItem: Identifiable, Equatable {
    public let id: String
    public let title: String
    public let artworkUrl: String
    public let isFavorites: Bool
    public let tracks: [Track]

    public init(id: String, title: String, artworkUrl: String, isFavorites: Bool = false, tracks: [Track] = []) {
        self.id = id
        self.title = title
        self.artworkUrl = artworkUrl
        self.isFavorites = isFavorites
        self.tracks = tracks
    }
}
