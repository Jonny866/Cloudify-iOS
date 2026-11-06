import SwiftUI

public struct TrackActionSheet: View {
    public let track: Track
    public let isFavorite: Bool
    public let isDownloaded: Bool
    public let onFavoriteToggle: () -> Void
    public let onDownloadClick: () -> Void
    public let onGoToArtist: () -> Void
    public let onDismiss: () -> Void

    @ObservedObject var theme = ThemeManager.shared

    public var body: some View {
        VStack(spacing: 20) {
            // Drag handle
            RoundedRectangle(cornerRadius: 3)
                .fill(Color.gray.opacity(0.4))
                .frame(width: 36, height: 4)
                .padding(.top, 10)

            // Header: Cover + Info
            HStack(spacing: 14) {
                AsyncImage(url: URL(string: track.artworkUrl)) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.darkSurface
                }
                .frame(width: 54, height: 54)
                .clipShape(RoundedRectangle(cornerRadius: 10))

                VStack(alignment: .leading, spacing: 3) {
                    Text(track.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.textPrimary)
                        .lineLimit(1)

                    Text(track.artist)
                        .font(.system(size: 13))
                        .foregroundColor(.textSecondary)
                        .lineLimit(1)
                }

                Spacer()
            }
            .padding(.horizontal, 20)

            Divider().background(Color.darkBorder)

            // Actions list
            VStack(spacing: 12) {
                actionRow(
                    icon: isFavorite ? "heart.slash.fill" : "heart.fill",
                    color: isFavorite ? .accentPink : theme.currentTheme.primary,
                    title: isFavorite ? "Удалить из любимых" : "Добавить в любимые",
                    action: {
                        onFavoriteToggle()
                        onDismiss()
                    }
                )

                actionRow(
                    icon: isDownloaded ? "checkmark.circle.fill" : "arrow.down.circle.fill",
                    color: isDownloaded ? .accentGreen : theme.currentTheme.primary,
                    title: isDownloaded ? "Трек скачан на устройство" : "Скачать для офлайн",
                    action: {
                        if !isDownloaded {
                            onDownloadClick()
                        }
                        onDismiss()
                    }
                )

                actionRow(
                    icon: "person.circle.fill",
                    color: .textSecondary,
                    title: "Перейти к исполнителю (\(track.artist))",
                    action: {
                        onDismiss()
                        onGoToArtist()
                    }
                )

                actionRow(
                    icon: "square.and.arrow.up",
                    color: .textSecondary,
                    title: "Поделиться треком",
                    action: {
                        onDismiss()
                        let shareURL = URL(string: track.streamUrl) ?? URL(string: "https://cloudify.app")!
                        let av = UIActivityViewController(activityItems: [track.title + " - " + track.artist, shareURL], applicationActivities: nil)
                        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                           let root = windowScene.windows.first?.rootViewController {
                            root.present(av, animated: true)
                        }
                    }
                )
            }
            .padding(.horizontal, 20)

            Spacer(minLength: 20)
        }
        .background(Color.darkSurfaceVariant.edgesIgnoringSafeArea(.all))
    }

    private func actionRow(icon: String, color: Color, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(color)
                    .frame(width: 28)

                Text(title)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.textPrimary)

                Spacer()
            }
            .padding(.vertical, 8)
        }
    }
}
