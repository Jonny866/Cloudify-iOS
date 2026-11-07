import SwiftUI

public struct DownloadProgressBarView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var theme = ThemeManager.shared

    public init() {}

    public var body: some View {
        if let banner = repo.downloadBanner {
            VStack(spacing: 6) {
                HStack(spacing: 8) {
                    if banner.isCompleted {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.accentGreen)
                    } else {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: theme.currentTheme.primary))
                            .scaleEffect(0.7)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(banner.isCompleted ? "✓ Загрузка завершена" : (banner.totalItems > 1 ? "Загрузка \(banner.currentItem) из \(banner.totalItems):" : "Загрузка трека:"))
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(.textMuted)

                            Spacer()

                            Text(banner.isCompleted ? "\(banner.totalItems) треков" : "\(Int(banner.currentProgress * 100))%")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(theme.currentTheme.light)
                        }

                        Text(banner.trackTitle)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.textPrimary)
                            .lineLimit(1)
                    }
                }

                // Progress Bar Track
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Color.white.opacity(0.12))
                            .frame(height: 3)

                        Capsule()
                            .fill(banner.isCompleted ? Color.accentGreen : theme.currentTheme.primary)
                            .frame(width: max(0, min(geo.size.width, geo.size.width * CGFloat(banner.currentProgress))), height: 3)
                            .animation(.linear(duration: 0.2), value: banner.currentProgress)
                    }
                }
                .frame(height: 3)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Color.darkSurfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.3), radius: 6, y: 3)
            .padding(.horizontal, 12)
            .padding(.bottom, 4)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .animation(.spring(response: 0.35, dampingFraction: 0.8), value: banner)
        }
    }
}
