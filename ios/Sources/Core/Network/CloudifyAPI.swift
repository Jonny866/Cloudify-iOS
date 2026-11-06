import Foundation

public struct TrackDto: Codable {
    public let id: String
    public let title: String
    public let uploader: String
    public let thumbnail: String?
    public let url: String
    public let duration_ms: Int64?

    enum CodingKeys: String, CodingKey {
        case id
        case title
        case uploader
        case thumbnail
        case url
        case duration_ms
    }
}

public struct UserSyncDto: Codable {
    public var id: String = ""
    public var name: String = ""
    public var email: String = ""
    public var avatarUrl: String = ""

    public init(id: String = "", name: String = "", email: String = "", avatarUrl: String = "") {
        self.id = id
        self.name = name
        self.email = email
        self.avatarUrl = avatarUrl
    }
}

public struct FavoriteSyncDto: Codable {
    public let id: String
    public let title: String
    public let artist: String
    public var artworkUrl: String = ""
    public var streamUrl: String = ""
    public var durationMs: Int64 = 180000
    public var genre: String = "SoundCloud"
    public var lyrics: String = ""
    public var addedAt: Int64 = 0

    public init(
        id: String,
        title: String,
        artist: String,
        artworkUrl: String = "",
        streamUrl: String = "",
        durationMs: Int64 = 180000,
        genre: String = "SoundCloud",
        lyrics: String = "",
        addedAt: Int64 = 0
    ) {
        self.id = id
        self.title = title
        self.artist = artist
        self.artworkUrl = artworkUrl
        self.streamUrl = streamUrl
        self.durationMs = durationMs
        self.genre = genre
        self.lyrics = lyrics
        self.addedAt = addedAt
    }
}

public struct PlaylistSyncDto: Codable {
    public let id: String
    public let name: String
    public var artworkUrl: String = ""
    public var createdAt: Int64 = 0
    public var trackIds: [String] = []

    public init(id: String, name: String, artworkUrl: String = "", createdAt: Int64 = 0, trackIds: [String] = []) {
        self.id = id
        self.name = name
        self.artworkUrl = artworkUrl
        self.createdAt = createdAt
        self.trackIds = trackIds
    }
}

public struct ArtistSyncDto: Codable {
    public let name: String
    public var artworkUrl: String = ""
    public var genre: String = "Исполнитель"
    public var monthlyListeners: String = ""
    public var addedAt: Int64 = 0

    public init(name: String, artworkUrl: String = "", genre: String = "Исполнитель", monthlyListeners: String = "", addedAt: Int64 = 0) {
        self.name = name
        self.artworkUrl = artworkUrl
        self.genre = genre
        self.monthlyListeners = monthlyListeners
        self.addedAt = addedAt
    }
}

public struct SyncPayloadDto: Codable {
    public let user: UserSyncDto
    public var favorites: [FavoriteSyncDto] = []
    public var playlists: [PlaylistSyncDto] = []
    public var favoriteArtists: [ArtistSyncDto] = []

    public init(user: UserSyncDto, favorites: [FavoriteSyncDto] = [], playlists: [PlaylistSyncDto] = [], favoriteArtists: [ArtistSyncDto] = []) {
        self.user = user
        self.favorites = favorites
        self.playlists = playlists
        self.favoriteArtists = favoriteArtists
    }
}

public struct SyncResponseDto: Codable {
    public let status: String
    public var user_id: String?
    public var user: UserSyncDto?
    public var favorites: [FavoriteSyncDto] = []
    public var playlists: [PlaylistSyncDto] = []
    public var favoriteArtists: [ArtistSyncDto] = []
    public var savedFavorites: Int?
    public var savedPlaylists: Int?
}

public final class CloudifyAPI {
    public static let shared = CloudifyAPI()
    public static let baseURL = "http://de-bots6.h1cloud.net:25066"

    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 25
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)
    }

    private static func rfc3986Encode(_ string: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-_.~")
        return string.addingPercentEncoding(withAllowedCharacters: allowed) ?? string
    }

    public static func getStreamUrl(webpageUrl: String, quality: String = "high") -> URL? {
        let encoded = rfc3986Encode(webpageUrl)
        let qParam = (quality.lowercased().contains("high") || quality.contains("320")) ? "high" : "standard"
        return URL(string: "\(baseURL)/api/stream?url=\(encoded)&quality=\(qParam)")
    }

    public static func getCoverUrl(originalThumbnailUrl: String?) -> String {
        guard let raw = originalThumbnailUrl, !raw.isEmpty else {
            return "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500"
        }
        let encoded = rfc3986Encode(raw)
        return "\(baseURL)/api/cover?url=\(encoded)"
    }

    public func searchTracks(query: String) async throws -> [Track] {
        let encodedQuery = rfc3986Encode(query)
        guard let url = URL(string: "\(Self.baseURL)/api/search?q=\(encodedQuery)") else {
            return []
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Cloudify-iOS/7.9", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await session.data(for: request)
        guard let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 else {
            return []
        }

        let dtos = try JSONDecoder().decode([TrackDto].self, from: data)
        return dtos.map { dto in
            let stream = Self.getStreamUrl(webpageUrl: dto.url)?.absoluteString ?? dto.url
            let cover = Self.getCoverUrl(originalThumbnailUrl: dto.thumbnail)
            let duration = dto.duration_ms ?? 180000
            return Track(
                id: dto.id,
                title: dto.title,
                artist: dto.uploader,
                artworkUrl: cover,
                streamUrl: stream,
                durationMs: duration,
                genre: "SoundCloud"
            )
        }
    }

    public func getWaveTracks() async throws -> [Track] {
        guard let url = URL(string: "\(Self.baseURL)/api/wave") else { return [] }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Cloudify-iOS/7.9", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await session.data(for: request)
        guard let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 else {
            return []
        }

        let dtos = try JSONDecoder().decode([TrackDto].self, from: data)
        return dtos.map { dto in
            let stream = Self.getStreamUrl(webpageUrl: dto.url)?.absoluteString ?? dto.url
            let cover = Self.getCoverUrl(originalThumbnailUrl: dto.thumbnail)
            let duration = dto.duration_ms ?? 190000
            return Track(
                id: dto.id,
                title: dto.title,
                artist: dto.uploader,
                artworkUrl: cover,
                streamUrl: stream,
                durationMs: duration,
                genre: "Wave"
            )
        }
    }

    public func syncUserData(payload: SyncPayloadDto) async throws -> SyncResponseDto {
        guard let url = URL(string: "\(Self.baseURL)/api/user/sync") else {
            throw URLError(.badURL)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Cloudify-iOS/7.9", forHTTPHeaderField: "User-Agent")
        request.httpBody = try JSONEncoder().encode(payload)

        let (data, _) = try await session.data(for: request)
        return try JSONDecoder().decode(SyncResponseDto.self, from: data)
    }

    public func getUserData(userId: String?, email: String?) async throws -> SyncResponseDto {
        var components = URLComponents(string: "\(Self.baseURL)/api/user/sync")!
        var queryItems: [URLQueryItem] = []
        if let u = userId, !u.isEmpty { queryItems.append(URLQueryItem(name: "user_id", value: u)) }
        if let e = email, !e.isEmpty { queryItems.append(URLQueryItem(name: "email", value: e)) }
        components.queryItems = queryItems

        guard let url = components.url else { throw URLError(.badURL) }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Cloudify-iOS/7.9", forHTTPHeaderField: "User-Agent")

        let (data, _) = try await session.data(for: request)
        return try JSONDecoder().decode(SyncResponseDto.self, from: data)
    }
}
