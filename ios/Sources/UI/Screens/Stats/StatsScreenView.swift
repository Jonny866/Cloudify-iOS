import SwiftUI

public struct StatsScreenView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var theme = ThemeManager.shared

    @State private var stats = MonthlyStats()
    @State private var syncStatusMessage: String? = nil

    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                // Header
                VStack(alignment: .leading, spacing: 4) {
                    Text("Итоги месяца")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundColor(.textPrimary)

                    Text(stats.monthName)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(theme.currentTheme.light)
                }
                .padding(.horizontal, 16)
                .padding(.top, 14)

                // Hero Card: Hours Listened
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 20)
                        .fill(
                            LinearGradient(
                                colors: [theme.currentTheme.dark, theme.currentTheme.primary],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )

                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("ВРЕМЯ С МУЗЫКОЙ")
                                .font(.system(size: 11, weight: .bold))
                                .tracking(1.0)
                                .foregroundColor(.black.opacity(0.8))

                            Text("\(String(format: "%.1f", stats.hoursListened)) ч")
                                .font(.system(size: 36, weight: .black))
                                .foregroundColor(.black)

                            Text("музыки прослушано в этом месяце")
                                .font(.system(size: 12))
                                .foregroundColor(.black.opacity(0.8))
                        }

                        Spacer()

                        ZStack {
                            Circle()
                                .fill(Color.black.opacity(0.2))
                                .frame(width: 54, height: 54)

                            Image(systemName: "clock.fill")
                                .font(.system(size: 26))
                                .foregroundColor(.black)
                        }
                    }
                    .padding(20)
                }
                .frame(height: 130)
                .padding(.horizontal, 16)

                // Top Artist Card
                statCard(
                    badge: "ЛЮБИМЫЙ ИСПОЛНИТЕЛЬ",
                    title: stats.topArtist,
                    subtitle: "Твой артист №1 в этом месяце",
                    imageUrl: stats.topArtistArtwork,
                    icon: "person.fill",
                    iconColor: .accentCyan
                )

                // Top Track Card
                statCard(
                    badge: "ЛЮБИМАЯ ПЕСНЯ",
                    title: stats.topTrack,
                    subtitle: "Слушали \(stats.topTrackPlays) раз на повторе",
                    imageUrl: stats.topTrackArtwork,
                    icon: "music.note",
                    iconColor: theme.currentTheme.light
                )

                // Theme Selector
                VStack(alignment: .leading, spacing: 10) {
                    Text("Тема оформления")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.textPrimary)

                    HStack(spacing: 12) {
                        themeCard(
                            themeMode: .yellow,
                            title: "Желтая тема",
                            color: Color(hex: 0xFACC15)
                        )

                        themeCard(
                            themeMode: .violet,
                            title: "Фиолетовая тема",
                            color: Color(hex: 0x8B5CF6)
                        )
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                // Cloud Backup & Sync
                VStack(alignment: .leading, spacing: 10) {
                    Text("Облачная синхронизация")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.textPrimary)

                    Button(action: {
                        Task {
                            await repo.syncToCloud()
                            await MainActor.run {
                                syncStatusMessage = "Данные успешно синхронизированы!"
                            }
                            try? await Task.sleep(nanoseconds: 2_500_000_000)
                            await MainActor.run {
                                syncStatusMessage = nil
                            }
                        }
                    }) {
                        HStack(spacing: 12) {
                            Image(systemName: "arrow.triangle.2.circlepath")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(theme.currentTheme.primary)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(repo.isSyncing ? "Синхронизация..." : "Синхронизировать с облаком")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.textPrimary)

                                Text(repo.user.isLoggedIn ? "Аккаунт: \(repo.user.email)" : "Войдите для синхронизации библиотеки")
                                    .font(.system(size: 12))
                                    .foregroundColor(.textMuted)
                            }

                            Spacer()

                            if repo.isSyncing {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: theme.currentTheme.primary))
                            }
                        }
                        .padding(14)
                        .background(Color.darkSurfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }

                    if let msg = syncStatusMessage {
                        Text(msg)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.accentGreen)
                            .padding(.horizontal, 4)
                    }
                }
                .padding(.horizontal, 16)

                // App Info Footer
                HStack {
                    Spacer()
                    VStack(spacing: 4) {
                        Text("Cloudify для iOS")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.textSecondary)
                        Text("Версия 7.9 • Сборка с Ad-Hoc / Ksign")
                            .font(.system(size: 11))
                            .foregroundColor(.textMuted)
                    }
                    Spacer()
                }
                .padding(.top, 16)

                Spacer(minLength: 120)
            }
        }
        .background(Color.deepBlack.edgesIgnoringSafeArea(.all))
    }

    private func statCard(badge: String, title: String, subtitle: String, imageUrl: String, icon: String, iconColor: Color) -> some View {
        HStack(spacing: 14) {
            AsyncImage(url: URL(string: imageUrl)) { img in
                img.resizable().aspectRatio(contentMode: .fill)
            } placeholder: {
                Color.darkSurface
            }
            .frame(width: 56, height: 56)
            .clipShape(RoundedRectangle(cornerRadius: 12))

            VStack(alignment: .leading, spacing: 3) {
                Text(badge)
                    .font(.system(size: 10, weight: .bold))
                    .tracking(1.0)
                    .foregroundColor(.textMuted)

                Text(title)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.textPrimary)
                    .lineLimit(1)

                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(.textSecondary)
                    .lineLimit(1)
            }

            Spacer()
        }
        .padding(14)
        .background(Color.darkSurfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 16)
    }

    private func themeCard(themeMode: AppThemeMode, title: String, color: Color) -> some View {
        let isSelected = theme.currentTheme == themeMode

        return Button(action: {
            theme.setTheme(themeMode)
        }) {
            HStack(spacing: 10) {
                Circle()
                    .fill(color)
                    .frame(width: 22, height: 22)
                    .overlay(
                        Circle().stroke(Color.white, lineWidth: isSelected ? 2 : 0)
                    )

                Text(title)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(isSelected ? .textPrimary : .textSecondary)

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(color)
                }
            }
            .padding(12)
            .background(Color.darkSurfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? color : Color.clear, lineWidth: 1.5)
            )
        }
    }
}
