import SwiftUI

public struct StatsScreenView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var theme = ThemeManager.shared

    @State private var stats = MonthlyStats()

    public init() {}

    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 16) {
                // Header (Matching Android StatsScreen.kt)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Итоги месяца")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundColor(.textPrimary)

                    Text(stats.monthName.isEmpty ? currentMonthName : stats.monthName)
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
                                .foregroundColor(Color(hex: 0xDDD6FE))

                            Text("\(String(format: "%.1f", stats.hoursListened)) ч")
                                .font(.system(size: 34, weight: .black))
                                .foregroundColor(.textPrimary)

                            Text("музыки прослушано в этом месяце")
                                .font(.system(size: 12))
                                .foregroundColor(Color(hex: 0xDDD6FE))
                        }

                        Spacer()

                        ZStack {
                            Circle()
                                .fill(Color.white.opacity(0.2))
                                .frame(width: 54, height: 54)

                            Image(systemName: "clock.fill")
                                .font(.system(size: 28))
                                .foregroundColor(.textPrimary)
                        }
                    }
                    .padding(18)
                }
                .frame(height: 130)
                .padding(.horizontal, 16)

                // Top Artist Card
                statDetailCard(
                    title: "ЛЮБИМЫЙ ИСПОЛНИТЕЛЬ",
                    value: stats.topArtist,
                    subtitle: "Твой артист №1 в этом месяце",
                    icon: "person.fill",
                    iconColor: .accentCyan,
                    imageUrl: stats.topArtistArtwork.isEmpty ? nil : stats.topArtistArtwork
                )

                // Top Track Card
                statDetailCard(
                    title: "ЛЮБИМАЯ ПЕСНЯ",
                    value: stats.topTrack,
                    subtitle: stats.topTrackPlays > 0 ? "Слушали \(stats.topTrackPlays) раз на повторе" : "Включайте больше музыки",
                    icon: "music.note",
                    iconColor: theme.currentTheme.light,
                    imageUrl: stats.topTrackArtwork.isEmpty ? nil : stats.topTrackArtwork
                )

                // Top Genre Card (Matching Android StatsScreen.kt)
                statDetailCard(
                    title: "ЛЮБИМЫЙ ЖАНР",
                    value: stats.topGenre,
                    subtitle: stats.genrePercent > 0 ? "\(stats.genrePercent)% прослушанных треков" : "Формируется по мере прослушивания",
                    icon: "flame.fill",
                    iconColor: theme.currentTheme == .yellow ? Color(hex: 0xFACC15) : .accentPink,
                    imageUrl: nil
                )

                Spacer(minLength: 120)
            }
        }
        .background(Color.deepBlack.edgesIgnoringSafeArea(.all))
        .onAppear {
            refreshStats()
        }
        .onChange(of: repo.recentTracks) { _ in
            refreshStats()
        }
    }

    private var currentMonthName: String {
        let monthNames = [
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        ]
        let cal = Calendar.current
        let idx = cal.component(.month, from: Date()) - 1
        let year = cal.component(.year, from: Date())
        return "\(monthNames[max(0, min(11, idx))]) \(year)"
    }

    private func refreshStats() {
        self.stats = repo.getMonthlyStats()
    }

    private func statDetailCard(
        title: String,
        value: String,
        subtitle: String,
        icon: String,
        iconColor: Color,
        imageUrl: String?
    ) -> some View {
        HStack(spacing: 14) {
            if let img = imageUrl, !img.isEmpty {
                AsyncImage(url: URL(string: img)) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.darkSurface
                }
                .frame(width: 54, height: 54)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.darkSurface)
                        .frame(width: 54, height: 54)

                    Image(systemName: icon)
                        .font(.system(size: 24))
                        .foregroundColor(iconColor)
                }
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.0)
                    .foregroundColor(.textMuted)

                Text(value)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.textPrimary)
                    .lineLimit(1)

                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(.textSecondary)
                    .lineLimit(1)
            }

            Spacer()
        }
        .padding(16)
        .background(Color.darkSurfaceVariant)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 16)
    }
}
