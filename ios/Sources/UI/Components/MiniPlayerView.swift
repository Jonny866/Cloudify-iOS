import SwiftUI

public struct MiniPlayerView: View {
    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var theme = ThemeManager.shared
    public let onOpenFullPlayer: () -> Void

    public var body: some View {
        if let track = player.currentTrack {
            VStack(spacing: 0) {
                // Main Mini Player Bar
                HStack(spacing: 12) {
                    // Track Thumbnail
                    AsyncImage(url: URL(string: track.artworkUrl)) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Color.darkSurfaceVariant
                    }
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 8))

                    // Title & Artist
                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.title)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.textPrimary)
                            .lineLimit(1)

                        Text(track.artist)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.textSecondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    // Play/Pause Button
                    Button(action: {
                        player.togglePlayPause()
                    }) {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(theme.currentTheme.primary)
                            .frame(width: 38, height: 38)
                    }

                    // Next Button
                    Button(action: {
                        player.next()
                    }) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.textSecondary)
                            .frame(width: 32, height: 32)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Color.darkSurfaceVariant)

                // Tiny Progress Line at Bottom
                GeometryReader { geo in
                    let progress = player.duration > 0 ? (player.currentTime / player.duration) : 0
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color.white.opacity(0.1))
                            .frame(height: 2)

                        Rectangle()
                            .fill(theme.currentTheme.primary)
                            .frame(width: max(0, min(geo.size.width, geo.size.width * CGFloat(progress))), height: 2)
                    }
                }
                .frame(height: 2)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .shadow(color: Color.black.opacity(0.4), radius: 8, x: 0, y: -2)
            .padding(.horizontal, 8)
            .padding(.bottom, 2)
            .contentShape(Rectangle())
            .onTapGesture {
                onOpenFullPlayer()
            }
        }
    }
}
