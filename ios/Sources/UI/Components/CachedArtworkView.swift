import SwiftUI

public struct CachedArtworkView: View {
    public let urlString: String
    public var localPath: String? = nil
    public var cornerRadius: CGFloat = 8

    @State private var image: UIImage? = nil

    public init(urlString: String, localPath: String? = nil, cornerRadius: CGFloat = 8) {
        self.urlString = urlString
        self.localPath = localPath
        self.cornerRadius = cornerRadius
        if let cached = ArtworkCacheManager.shared.getMemoryCachedImage(for: urlString) {
            _image = State(initialValue: cached)
        }
    }

    public var body: some View {
        ZStack {
            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                Color.darkSurface
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        .task(id: urlString + (localPath ?? "")) {
            if image == nil {
                if let loaded = await ArtworkCacheManager.shared.getImage(urlString: urlString, localPath: localPath) {
                    await MainActor.run {
                        self.image = loaded
                    }
                }
            }
        }
    }
}
