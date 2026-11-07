import Foundation
import UIKit
import CryptoKit

public final class ArtworkCacheManager {
    public static let shared = ArtworkCacheManager()

    private let memoryCache = NSCache<NSString, UIImage>()
    private let fileManager = FileManager.default
    private let diskCacheDir: URL

    private init() {
        memoryCache.countLimit = 400
        memoryCache.totalCostLimit = 120 * 1024 * 1024 // 120 MB

        let caches = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first!
        diskCacheDir = caches.appendingPathComponent("CloudifyArtworkCache", isDirectory: true)
        try? fileManager.createDirectory(at: diskCacheDir, withIntermediateDirectories: true)
    }

    private func cacheKey(for urlString: String) -> String {
        let hash = SHA256.hash(data: Data(urlString.utf8))
        return hash.compactMap { String(format: "%02x", $0) }.joined() + ".jpg"
    }

    /// Fast synchronous memory-only check
    public func getMemoryCachedImage(for urlString: String) -> UIImage? {
        guard !urlString.isEmpty else { return nil }
        return memoryCache.object(forKey: urlString as NSString)
    }

    /// Retrieves image checking Memory -> Explicit local path -> Disk cache -> Network download
    public func getImage(urlString: String, localPath: String?) async -> UIImage? {
        if urlString.isEmpty && (localPath == nil || localPath!.isEmpty) {
            return nil
        }

        // 1. Memory Cache
        if !urlString.isEmpty, let mem = memoryCache.object(forKey: urlString as NSString) {
            return mem
        }

        // 2. Explicit Local Path (Downloaded with track)
        if let path = localPath, !path.isEmpty, fileManager.fileExists(atPath: path) {
            if let image = UIImage(contentsOfFile: path) {
                if !urlString.isEmpty {
                    memoryCache.setObject(image, forKey: urlString as NSString)
                }
                return image
            }
        }

        // 3. Persistent Disk Cache
        if !urlString.isEmpty {
            let diskFile = diskCacheDir.appendingPathComponent(cacheKey(for: urlString))
            if fileManager.fileExists(atPath: diskFile.path), let diskImage = UIImage(contentsOfFile: diskFile.path) {
                memoryCache.setObject(diskImage, forKey: urlString as NSString)
                return diskImage
            }
        }

        // 4. Download from network & store to disk and memory
        guard let url = URL(string: urlString) else { return nil }
        do {
            var request = URLRequest(url: url)
            request.timeoutInterval = 15
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode),
               let downloadedImage = UIImage(data: data) {
                // Save to disk
                let diskFile = diskCacheDir.appendingPathComponent(cacheKey(for: urlString))
                try? data.write(to: diskFile, options: .atomic)
                // Save to memory
                memoryCache.setObject(downloadedImage, forKey: urlString as NSString)
                return downloadedImage
            }
        } catch {
            // Ignore download failures gracefully
        }

        return nil
    }

    /// Pre-caches image data directly to local path and cache
    public func saveDownloadedArtwork(data: Data, urlString: String, targetURL: URL) {
        try? data.write(to: targetURL, options: .atomic)
        if let image = UIImage(data: data) {
            if !urlString.isEmpty {
                memoryCache.setObject(image, forKey: urlString as NSString)
            }
            if !urlString.isEmpty {
                let diskFile = diskCacheDir.appendingPathComponent(cacheKey(for: urlString))
                try? data.write(to: diskFile, options: .atomic)
            }
        }
    }

    public func clearAllCache() {
        memoryCache.removeAllObjects()
        try? fileManager.removeItem(at: diskCacheDir)
        try? fileManager.createDirectory(at: diskCacheDir, withIntermediateDirectories: true)
    }
}
