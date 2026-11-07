import SwiftUI
import PhotosUI

public struct ProfileAndSettingsView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var theme = ThemeManager.shared
    public let onBack: () -> Void

    @State private var showEditNameAlert: Bool = false
    @State private var newNameInput: String = ""
    @State private var showQualityDialog: Bool = false
    @State private var cacheSizeMb: Double = 0.0
    @State private var isSyncingNow: Bool = false
    @State private var toastMessage: String? = nil
    @State private var selectedPhotoItem: PhotosPickerItem? = nil

    private let audioOptions = [
        ("Высокое (320 kbps)", "Максимальное качество звука без сжатия"),
        ("Стандартное (128 kbps)", "Оптимальный баланс качества и скорости"),
        ("Экономия трафика (64 kbps)", "Быстрый старт при слабом интернете")
    ]

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

                    Button(action: onBack) {
                        Image(systemName: "house.fill")
                            .font(.system(size: 18))
                            .foregroundColor(theme.currentTheme.primary)
                            .frame(width: 40, height: 40)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 16)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 16) {
                        // User Profile Hero Card (Matching Android AuthScreen.kt)
                        HStack(spacing: 16) {
                            if !repo.user.avatarUrl.isEmpty {
                                AsyncImage(url: URL(string: repo.user.avatarUrl)) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Color.darkSurfaceVariant
                                }
                                .frame(width: 68, height: 68)
                                .clipShape(Circle())
                                .overlay(Circle().stroke(theme.currentTheme.primary, lineWidth: 2))
                            } else {
                                ZStack {
                                    Circle()
                                        .fill(Color(hex: 0x282828))
                                        .frame(width: 68, height: 68)
                                    Image(systemName: "person.fill")
                                        .font(.system(size: 30))
                                        .foregroundColor(.textSecondary)
                                }
                            }

                            VStack(alignment: .leading, spacing: 4) {
                                Text(repo.user.name.isEmpty ? "Пользователь Cloudify" : repo.user.name)
                                    .font(.system(size: 19, weight: .bold))
                                    .foregroundColor(.textPrimary)

                                if !repo.user.email.isEmpty {
                                    Text(repo.user.email)
                                        .font(.system(size: 13))
                                        .foregroundColor(theme.currentTheme.light)
                                }

                                HStack(spacing: 6) {
                                    Image(systemName: repo.user.isLoggedIn ? "checkmark.circle.fill" : "person.fill")
                                        .font(.system(size: 12))
                                        .foregroundColor(repo.user.isLoggedIn ? theme.currentTheme.light : .textMuted)
                                    Text(repo.user.isLoggedIn ? "Профиль активен" : "Гостевой режим")
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(repo.user.isLoggedIn ? Color(hex: 0xDDD6FE) : .textMuted)
                                }
                                .padding(.top, 2)
                            }

                            Spacer()
                        }
                        .padding(20)
                        .background(
                            repo.user.isLoggedIn
                                ? LinearGradient(colors: [theme.currentTheme.dark, Color(hex: 0x1E1B4B)], startPoint: .topLeading, endPoint: .bottomTrailing)
                                : LinearGradient(colors: [Color.darkSurfaceVariant, Color.darkSurface], startPoint: .topLeading, endPoint: .bottomTrailing)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 22))

                        // Upload Avatar from Gallery Button (Exact Android AuthScreen.kt)
                        PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                            HStack(spacing: 8) {
                                Image(systemName: "camera.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(theme.currentTheme.light)
                                Text("Загрузить своё фото из галереи")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(.textPrimary)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Color.darkSurfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(
                                        LinearGradient(
                                            colors: [theme.currentTheme.primary, theme.currentTheme.light],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        ),
                                        lineWidth: 1.5
                                    )
                            )
                        }
                        .onChange(of: selectedPhotoItem) { newItem in
                            Task {
                                if let data = try? await newItem?.loadTransferable(type: Data.self) {
                                    let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
                                    let avatarURL = documents.appendingPathComponent("avatar_\(Int(Date().timeIntervalSince1970)).jpg")
                                    try? data.write(to: avatarURL)
                                    await MainActor.run {
                                        repo.user.avatarUrl = avatarURL.absoluteString
                                        repo.saveToStorage()
                                        showToast("Фото профиля обновлено")
                                    }
                                }
                            }
                        }

                        // Change Name / Logout Buttons
                        HStack(spacing: 10) {
                            Button(action: {
                                newNameInput = repo.user.name
                                showEditNameAlert = true
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "pencil")
                                    Text("Изменить имя")
                                }
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(theme.currentTheme.light)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(Color.darkSurfaceVariant)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                            }

                            if repo.user.isLoggedIn {
                                Button(action: {
                                    repo.logout()
                                    showToast("Вы вышли из аккаунта")
                                    onBack()
                                }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "rectangle.portrait.and.arrow.right")
                                        Text("Выйти")
                                    }
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(.accentPink)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(Color.darkSurfaceVariant)
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                                }
                            }
                        }

                        // App Settings Section Title
                        HStack {
                            Text("Настройки приложения")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.textPrimary)
                            Spacer()
                        }
                        .padding(.top, 6)

                        // Settings Card
                        VStack(spacing: 16) {
                            // 1. Theme Color Selection (lowercase titles, no word wrap)
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Цветовая тема")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.textPrimary)
                                    Text("Фиолетовый или жёлтый стиль")
                                        .font(.system(size: 12))
                                        .foregroundColor(.textMuted)
                                }

                                Spacer()

                                HStack(spacing: 8) {
                                    themePill(mode: .violet, name: "фиолетовый", color: Color(hex: 0x8B5CF6))
                                    themePill(mode: .yellow, name: "жёлтый", color: Color(hex: 0xFACC15))
                                }
                            }

                            Divider().background(Color.darkBorder)

                            // 2. Audio Quality (Clickable with Dialog)
                            Button(action: { showQualityDialog = true }) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Качество звука")
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(.textPrimary)
                                        Text("Битрейт потока и формат аудио")
                                            .font(.system(size: 12))
                                            .foregroundColor(.textMuted)
                                    }

                                    Spacer()

                                    Text(repo.audioQuality)
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(theme.currentTheme.light)

                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.textMuted)
                                }
                            }

                            Divider().background(Color.darkBorder)

                            // 3. Cache & Storage Toggle
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Кэширование на устройство")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.textPrimary)
                                    Text(String(format: "Автосохранение (%.1f МБ занято)", cacheSizeMb))
                                        .font(.system(size: 12))
                                        .foregroundColor(.textMuted)
                                }

                                Spacer()

                                Toggle("", isOn: $repo.isCachingEnabled)
                                    .labelsHidden()
                                    .tint(theme.currentTheme.primary)
                            }

                            if cacheSizeMb > 0.05 {
                                HStack {
                                    Spacer()
                                    Button(action: {
                                        repo.clearCache()
                                        cacheSizeMb = repo.getCacheSizeMb()
                                        showToast("Кэш очищен")
                                    }) {
                                        Text("Очистить кэш")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(.accentPink)
                                    }
                                }
                            }

                            Divider().background(Color.darkBorder)

                            // 4. Cloudify v7.9 Brand Card with smaller clean borders
                            HStack(spacing: 12) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(theme.currentTheme == .yellow ? Color(hex: 0x1E1A0E) : Color(hex: 0x13101E))
                                        .frame(width: 44, height: 44)

                                    Image(uiImage: UIImage(named: theme.currentTheme.logoImageName) ?? UIImage())
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .frame(width: 40, height: 40)
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                }

                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Cloudify v7.9")
                                        .font(.system(size: 15, weight: .bold))
                                        .foregroundColor(.textPrimary)

                                    Text(theme.currentTheme == .yellow ? "Иконка: жёлтая" : "Иконка: фиолетовая")
                                        .font(.system(size: 12))
                                        .foregroundColor(theme.currentTheme.light)
                                }

                                Spacer()

                                Text(theme.currentTheme == .yellow ? "жёлтая" : "фиолетовая")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(theme.currentTheme.primary)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(theme.currentTheme.primary.opacity(0.15))
                                    .clipShape(Capsule())
                            }

                            Divider().background(Color.darkBorder)

                            // 5. Cloud Backup & Restore Buttons (Exact Android 7.9)
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Облачная синхронизация")
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(.textPrimary)
                                        Text("Сохранение треков и плейлистов в вашем аккаунте")
                                            .font(.system(size: 12))
                                            .foregroundColor(.textMuted)
                                    }

                                    Spacer()

                                    Image(systemName: "icloud.and.arrow.up.fill")
                                        .font(.system(size: 20))
                                        .foregroundColor(theme.currentTheme.primary)
                                }

                                HStack(spacing: 10) {
                                    Button(action: {
                                        isSyncingNow = true
                                        Task {
                                            await repo.syncToCloud()
                                            await MainActor.run {
                                                isSyncingNow = false
                                                showToast("Плейлисты и треки сохранены в облаке!")
                                            }
                                        }
                                    }) {
                                        HStack(spacing: 6) {
                                            Image(systemName: "arrow.up.circle.fill")
                                            Text("В облако")
                                                .fontWeight(.bold)
                                        }
                                        .font(.system(size: 13))
                                        .foregroundColor(.textPrimary)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 12)
                                        .background(theme.currentTheme.primary.opacity(0.18))
                                        .clipShape(RoundedRectangle(cornerRadius: 12))
                                    }

                                    Button(action: {
                                        guard !repo.user.email.isEmpty else {
                                            showToast("Укажите email в профиле")
                                            return
                                        }
                                        isSyncingNow = true
                                        Task {
                                            let res = await repo.restoreFromCloud(email: repo.user.email)
                                            await MainActor.run {
                                                isSyncingNow = false
                                                if res.success {
                                                    showToast("Восстановлено: \(res.count) любимых треков!")
                                                } else {
                                                    showToast("Ошибка связи с сервером")
                                                }
                                            }
                                        }
                                    }) {
                                        HStack(spacing: 6) {
                                            Image(systemName: "arrow.down.circle.fill")
                                            Text("Из облака")
                                                .fontWeight(.bold)
                                        }
                                        .font(.system(size: 13))
                                        .foregroundColor(.textPrimary)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 12)
                                        .background(theme.currentTheme.primary.opacity(0.18))
                                        .clipShape(RoundedRectangle(cornerRadius: 12))
                                    }
                                }
                            }
                        }
                        .padding(18)
                        .background(Color.darkSurfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: 18))

                        Spacer(minLength: 40)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                }
            }

            // Toast Alert
            if let toast = toastMessage {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.textPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color(hex: 0x1F2937).opacity(0.95))
                        .clipShape(Capsule())
                        .shadow(radius: 8)
                        .padding(.bottom, 30)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .onAppear {
            cacheSizeMb = repo.getCacheSizeMb()
        }
        .alert("Изменить имя профиля", isPresented: $showEditNameAlert) {
            TextField("Ваше имя", text: $newNameInput)
            Button("Сохранить") {
                let trimmed = newNameInput.trimmingCharacters(in: .whitespaces)
                if !trimmed.isEmpty {
                    repo.user.name = trimmed
                    repo.saveToStorage()
                    showToast("Имя сохранено: \(trimmed)")
                }
            }
            Button("Отмена", role: .cancel) {}
        }
        .confirmationDialog("Качество звука", isPresented: $showQualityDialog, titleVisibility: .visible) {
            ForEach(audioOptions, id: \.0) { option in
                Button(option.0) {
                    repo.audioQuality = option.0
                    showToast("Качество: \(option.0)")
                }
            }
            Button("Отмена", role: .cancel) {}
        } message: {
            Text("Выберите битрейт воспроизведения аудиопотока")
        }
    }

    private func themePill(mode: AppThemeMode, name: String, color: Color) -> some View {
        let isSelected = theme.currentTheme == mode

        return Button(action: {
            theme.setTheme(mode)
        }) {
            HStack(spacing: 5) {
                Circle()
                    .fill(color)
                    .frame(width: 8, height: 8)
                Text(name)
                    .font(.system(size: 12, weight: isSelected ? .bold : .regular))
                    .foregroundColor(isSelected ? .textPrimary : .textSecondary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(isSelected ? color.opacity(0.2) : Color.darkSurface)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isSelected ? color : Color.darkBorder, lineWidth: isSelected ? 1.5 : 1)
            )
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { toastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { toastMessage = nil }
        }
    }
}
