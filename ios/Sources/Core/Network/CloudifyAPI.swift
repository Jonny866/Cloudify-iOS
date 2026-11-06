import Foundation

public struct TrackDto: Codable {
    public let id: String
    public let title: String
    public let uploader: String
    public let thumbnail: String?
    public let url: String
}

public struct UserSyncDto: Codable {
    public var id: String = ""
    public var name: String = ""
    public var email: String = ""
    public var avatarUrl: String = ""
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
}

public struct PlaylistSyncDto: Codable {
    public let id: String
    public let name: String
    public var artworkUrl: String = ""
    public var createdAt: Int64 = 0
    public var trackIds: [String] = []
}

public struct ArtistSyncDto: Codable {
    public let name: String
    public var artworkUrl: String = ""
    public var genre: String = "Исполнитель"
    public var monthlyListeners: String = ""
    public var addedAt: Int64 = 0
}

public struct SyncPayloadDto: Codable {
    public let user: UserSyncDto
    public var favorites: [FavoriteSyncDto] = []
    public var playlists: [PlaylistSyncDto] = []
    public var favoriteArtists: [ArtistSyncDto] = []
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

    public static func getStreamUrl(webpageUrl: String, quality: String = "high") -> URL? {
        guard let encoded = webpageUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return nil
        }
        let qParam = (quality.lowercased().contains("high") || quality.contains("320")) ? "high" : "standard"
        return URL(string: "\(baseURL)/api/stream?url=\(encoded)&quality=\(qParam)")
    }

    public static func getCoverUrl(originalThumbnailUrl: String?) -> String {
        guard let raw = originalThumbnailUrl, !raw.isEmpty else {
            return "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500"
        }
        guard let encoded = raw.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return raw
        }
        return "\(baseURL)/api/cover?url=\(encoded)"
    }

    public func searchTracks(query: String) async throws -> [Track] {
        guard let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(Self.baseURL)/api/search?q=\(encodedQuery)") else {
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
            return Track(
                id: dto.id,
                title: dto.title,
                artist: dto.uploader,
                artworkUrl: cover,
                streamUrl: stream,
                durationMs: 180000,
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
            return Track(
                id: dto.id,
                title: dto.title,
                artist: dto.uploader,
                artworkUrl: cover,
                streamUrl: stream,
                durationMs: 180000,
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
