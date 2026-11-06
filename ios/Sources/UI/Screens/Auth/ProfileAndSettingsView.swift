import SwiftUI

public struct ProfileAndSettingsView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var theme = ThemeManager.shared
    public let onBack: () -> Void

    @State private var audioQuality: String = "Высокое (320 kbps)"
    @State private var cacheSizeMb: Double = 0.0
    @State private var isSyncingNow: Bool = false
    @State private var toastMessage: String? = nil

    public init(onBack: @escaping () -> Void) {
        self.onBack = onBack
    }

    public var body: some View {
        ZStack {
            Color.deepBlack.edgesIgnoringSafeArea(.all)

            VStack(spacing: 0) {
                // Top Header Bar
                HStack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.textPrimary)
                            .frame(width: 40, height: 40)
                            .background(Color.darkSurfaceVariant)
                            .clipShape(Circle())
                    }

                    Spacer()

                    Text("Профиль и настройки")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.textPrimary)

                    Spacer()

                    Color.clear.frame(width: 40, height: 40)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 16)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 20) {
                        // User Profile Hero Card
                        HStack(spacing: 16) {
                            if !repo.user.avatarUrl.isEmpty {
                                AsyncImage(url: URL(string: repo.user.avatarUrl)) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Color.darkSurfaceVariant
                                }
                                .frame(width: 64, height: 64)
                                .clipShape(Circle())
                            } else {
                                ZStack {
                                    Circle()
                                        .fill(theme.currentTheme.primary.opacity(0.18))
                                        .frame(width: 64, height: 64)
                                    Image(systemName: "person.fill")
                                        .font(.system(size: 28))
                                        .foregroundColor(theme.currentTheme.primary)
                                }
                            }

                            VStack(alignment: .leading, spacing: 4) {
                                Text(repo.user.name.isEmpty ? "Пользователь Cloudify" : repo.user.name)
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(.textPrimary)

                                Text(repo.user.email.isEmpty ? "Гостевой аккаунт" : repo.user.email)
                                    .font(.system(size: 13))
                                    .foregroundColor(.textSecondary)

                                HStack(spacing: 4) {
                                    Circle()
                                        .fill(Color.green)
                                        .frame(width: 6, height: 6)
                                    Text("Облако Cloudify подключено")
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(Color.green)
                                }
                                .padding(.top, 2)
                            }

                            Spacer()
                        }
                        .padding(18)
                        .background(Color.darkSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.darkBorder, lineWidth: 1)
                        )

                        // 1. Theme Selection
                        VStack(alignment: .leading, spacing: 12) {
                            Text("ТЕМА ОФОРМЛЕНИЯ")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.textMuted)
                                .tracking(1)

                            HStack(spacing: 12) {
                                themeChoiceCard(
                                    title: "Фирменный желтый",
                                    color: Color(hex: 0xFACC15),
                                    isSelected: theme.currentTheme == .yellow
                                ) {
                                    theme.setTheme(.yellow)
                                }

                                themeChoiceCard(
                                    title: "Фирменный фиолетовый",
                                    color: Color(hex: 0x8B5CF6),
                                    isSelected: theme.currentTheme == .violet
                                ) {
                                    theme.setTheme(.violet)
                                }
                            }
                        }
                        .padding(18)
                        .background(Color.darkSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.darkBorder, lineWidth: 1)
                        )

                        // 2. Audio Quality Selection
                        VStack(alignment: .leading, spacing: 12) {
                            Text("КАЧЕСТВО АУДИО")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.textMuted)
                                .tracking(1)

                            HStack(spacing: 12) {
                                qualityPill(title: "320 kbps (Высокое)", isSelected: audioQuality.contains("320")) {
                                    audioQuality = "Высокое (320 kbps)"
                                }
                                qualityPill(title: "160 kbps (Стандарт)", isSelected: !audioQuality.contains("320")) {
                                    audioQuality = "Стандартное (160 kbps)"
                                }
                            }
                        }
                        .padding(18)
                        .background(Color.darkSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.darkBorder, lineWidth: 1)
                        )

                        // 3. Cache & Storage Management
                        VStack(alignment: .leading, spacing: 14) {
                            Text("ПАМЯТЬ И КЭШ")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.textMuted)
                                .tracking(1)

                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Размер сохраненного кэша")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.textPrimary)
                                    Text(String(format: "%.1f MB загруженных треков", cacheSizeMb))
                                        .font(.system(size: 12))
                                        .foregroundColor(.textSecondary)
                                }

                                Spacer()

                                Button(action: {
                                    repo.clearCache()
                                    cacheSizeMb = repo.getCacheSizeMb()
                                    showToast("Кэш очищен")
                                }) {
                                    Text("Очистить")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(theme.currentTheme.primary)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                        .background(Color.darkSurfaceVariant)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                }
                            }
                        }
                        .padding(18)
                        .background(Color.darkSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18)
                                .stroke(Color.darkBorder, lineWidth: 1)
                        )

                        // 4. Cloud Sync Trigger
                        Button(action: {
                            isSyncingNow = true
                            Task {
                                await repo.syncToCloud()
                                await MainActor.run {
                                    isSyncingNow = false
                                    showToast("Облако синхронизировано!")
                                }
                            }
                        }) {
                            HStack(spacing: 12) {
                                Image(systemName: "icloud.and.arrow.up.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(theme.currentTheme.primary)

                                Text(isSyncingNow ? "Синхронизация..." : "Синхронизировать с облаком")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.textPrimary)

                                Spacer()

                                if isSyncingNow {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: theme.currentTheme.primary))
                                }
                            }
                            .padding(18)
                            .background(Color.darkSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 18))
                            .overlay(
                                RoundedRectangle(cornerRadius: 18)
                                    .stroke(Color.darkBorder, lineWidth: 1)
                            )
                        }
                        .disabled(isSyncingNow)

                        // 5. Logout Button
                        Button(action: {
                            repo.logout()
                            onBack()
                        }) {
                            HStack {
                                Spacer()
                                Image(systemName: "rectangle.portrait.and.arrow.right")
                                    .foregroundColor(.accentPink)
                                Text("Выйти из аккаунта")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.accentPink)
                                Spacer()
                            }
                            .padding(16)
                            .background(Color.darkSurfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                        }

                        Spacer().frame(height: 30)
                    }
                    .padding(.horizontal, 16)
                }
            }

            // Toast Alert
            if let toast = toastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(Color.darkSurfaceVariant)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(theme.currentTheme.primary, lineWidth: 1))
                        .padding(.bottom, 30)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .onAppear {
            cacheSizeMb = repo.getCacheSizeMb()
        }
    }

    private func themeChoiceCard(title: String, color: Color, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Circle()
                    .fill(color)
                    .frame(width: 36, height: 36)
                    .overlay(
                        Circle().stroke(Color.white.opacity(0.3), lineWidth: isSelected ? 3 : 0)
                    )

                Text(title)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(isSelected ? .textPrimary : .textSecondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(isSelected ? theme.currentTheme.primary.opacity(0.12) : Color.darkSurfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(isSelected ? theme.currentTheme.primary : Color.clear, lineWidth: 1.5)
            )
        }
    }

    private func qualityPill(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(isSelected ? (theme.currentTheme == .yellow ? .black : .white) : .textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(isSelected ? theme.currentTheme.primary : Color.darkSurfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { toastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { toastMessage = nil }
        }
    }
}
