import SwiftUI

public struct MiniPlayerView: View {
    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var theme = ThemeManager.shared
    public let onOpenFullPlayer: () -> Void

    public init(onOpenFullPlayer: @escaping () -> Void) {
        self.onOpenFullPlayer = onOpenFullPlayer
    }

    public var body: some View {
        if let track = player.currentTrack {
            let isFav = repo.isFavorite(track.id)

            VStack(spacing: 0) {
                // Main Mini Player Bar
                HStack(spacing: 8) {
                    // Track Thumbnail
                    AsyncImage(url: URL(string: track.artworkUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color(hex: 0x2E2642)
                    }
                    .frame(width: 42, height: 42)
                    .clipShape(RoundedRectangle(cornerRadius: 9))

                    // Title & Artist
                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.title)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.textPrimary)
                            .lineLimit(1)

                        Text(track.artist)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(theme.currentTheme.light)
                            .lineLimit(1)
                    }

                    Spacer(minLength: 4)

                    // Favorite Heart Button
                    Button(action: {
                        repo.toggleFavorite(track)
                    }) {
                        Image(systemName: isFav ? "heart.fill" : "heart")
                            .font(.system(size: 16))
                            .foregroundColor(isFav ? .accentPink : .textMuted)
                            .frame(width: 30, height: 30)
                    }

                    // Previous Button
                    Button(action: {
                        player.previous()
                    }) {
                        Image(systemName: "backward.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.textPrimary)
                            .frame(width: 28, height: 28)
                    }

                    // Play/Pause Button
                    Button(action: {
                        player.togglePlayPause()
                    }) {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.textPrimary)
                            .frame(width: 32, height: 32)
                    }

                    // Next Button
                    Button(action: {
                        player.next()
                    }) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.textSecondary)
                            .frame(width: 28, height: 28)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.darkSurfaceVariant)

                // Slim Progress Line
                GeometryReader { geo in
                    let progress = player.duration > 0 ? (player.currentTime / player.duration) : 0
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color.white.opacity(0.08))
                            .frame(height: 2)

                        Rectangle()
                            .fill(theme.currentTheme.primary)
                            .frame(width: max(0, min(geo.size.width, geo.size.width * CGFloat(progress))), height: 2)
                    }
                }
                .frame(height: 2)
            }
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .shadow(color: Color.black.opacity(0.35), radius: 6, x: 0, y: -2)
            .padding(.horizontal, 10)
            .padding(.bottom, 4)
            .contentShape(Rectangle())
            .onTapGesture {
                onOpenFullPlayer()
            }
        }
    }
}
