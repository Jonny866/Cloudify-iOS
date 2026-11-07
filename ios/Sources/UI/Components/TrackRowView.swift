import SwiftUI

public struct TrackRowView: View {
    public let track: Track
    public let isCurrent: Bool
    public let isPlaying: Bool
    public let isFavorite: Bool
    public let isDownloaded: Bool
    public let isDownloading: Bool
    public let onPlay: () -> Void
    public let onFavoriteToggle: () -> Void
    public let onMoreClick: () -> Void

    @ObservedObject var theme = ThemeManager.shared

    public init(
        track: Track,
        isCurrent: Bool = false,
        isPlaying: Bool = false,
        isFavorite: Bool = false,
        isDownloaded: Bool = false,
        isDownloading: Bool = false,
        onPlay: @escaping () -> Void,
        onFavoriteToggle: @escaping () -> Void,
        onMoreClick: @escaping () -> Void
    ) {
        self.track = track
        self.isCurrent = isCurrent
        self.isPlaying = isPlaying
        self.isFavorite = isFavorite
        self.isDownloaded = isDownloaded
        self.isDownloading = isDownloading
        self.onPlay = onPlay
        self.onFavoriteToggle = onFavoriteToggle
        self.onMoreClick = onMoreClick
    }

    public var body: some View {
        HStack(spacing: 12) {
            // Artwork
            ZStack(alignment: .center) {
                AsyncImage(url: URL(string: track.artworkUrl)) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.darkSurface
                }
                .frame(width: 48, height: 48)
                .clipShape(RoundedRectangle(cornerRadius: 8))

                if isCurrent && isPlaying {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.black.opacity(0.55))
                        .frame(width: 48, height: 48)
                    PlayingEqualizerView()
                }
            }

            // Track info
            VStack(alignment: .leading, spacing: 3) {
                Text(track.title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(isCurrent ? theme.currentTheme.primary : .textPrimary)
                    .lineLimit(1)

                HStack(spacing: 4) {
                    if isDownloaded || track.isDownloaded {
                        Image(systemName: "arrow.down.circle.fill")
                            .font(.system(size: 11))
                            .foregroundColor(theme.currentTheme.primary)
                    } else if isDownloading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: theme.currentTheme.primary))
                            .scaleEffect(0.65)
                            .frame(width: 14, height: 14)
                    }
                    Text(track.artist)
                        .font(.system(size: 12))
                        .foregroundColor(.textSecondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            // Favorite Button
            Button(action: onFavoriteToggle) {
                Image(systemName: isFavorite ? "heart.fill" : "heart")
                    .font(.system(size: 18))
                    .foregroundColor(isFavorite ? .accentPink : .textMuted)
                    .frame(width: 32, height: 32)
            }

            // More Options (3 Dots)
            Button(action: onMoreClick) {
                Image(systemName: "ellipsis")
                    .font(.system(size: 16))
                    .foregroundColor(.textMuted)
                    .frame(width: 28, height: 32)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture {
            onPlay()
        }
    }
}
