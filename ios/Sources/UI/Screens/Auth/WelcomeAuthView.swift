import SwiftUI

public struct WelcomeAuthView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var theme = ThemeManager.shared
    public let onDismiss: () -> Void

    @State private var email: String = ""
    @State private var displayName: String = ""
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil

    public var body: some View {
        VStack(spacing: 24) {
            // Drag handle
            RoundedRectangle(cornerRadius: 3)
                .fill(Color.gray.opacity(0.4))
                .frame(width: 36, height: 4)
                .padding(.top, 12)

            // Header Icon & Title
            VStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(theme.currentTheme.primary.opacity(0.15))
                        .frame(width: 70, height: 70)

                    Image(systemName: "person.badge.key.fill")
                        .font(.system(size: 32))
                        .foregroundColor(theme.currentTheme.primary)
                }

                Text(repo.user.isLoggedIn ? "Профиль Cloudify" : "Вход в Cloudify")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.textPrimary)

                Text(repo.user.isLoggedIn ? "Ваша медиатека синхронизирована с сервером" : "Введите email для восстановления ваших любимых треков и плейлистов")
                    .font(.system(size: 13))
                    .foregroundColor(.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            if repo.user.isLoggedIn {
                // Logged in card
                VStack(spacing: 12) {
                    HStack(spacing: 14) {
                        Image(systemName: "person.circle.fill")
                            .font(.system(size: 44))
                            .foregroundColor(theme.currentTheme.primary)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(repo.user.name)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.textPrimary)

                            Text(repo.user.email)
                                .font(.system(size: 13))
                                .foregroundColor(.textSecondary)
                        }

                        Spacer()
                    }
                    .padding(16)
                    .background(Color.darkSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    Button(action: {
                        repo.user = UserProfile()
                    }) {
                        Text("Выйти из аккаунта")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.accentPink)
                            .padding(.top, 8)
                    }
                }
                .padding(.horizontal, 20)
            } else {
                // Login Form
                VStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Email")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.textMuted)

                        TextField("example@mail.com", text: $email)
                            .keyboardType(.emailAddress)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                            .foregroundColor(.textPrimary)
                            .padding(14)
                            .background(Color.darkSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Имя (необязательно)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.textMuted)

                        TextField("Ваше имя", text: $displayName)
                            .foregroundColor(.textPrimary)
                            .padding(14)
                            .background(Color.darkSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    if let err = errorMessage {
                        Text(err)
                            .font(.system(size: 12))
                            .foregroundColor(.accentPink)
                    }

                    Button(action: {
                        let clean = email.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !clean.isEmpty else {
                            errorMessage = "Пожалуйста, введите Email"
                            return
                        }
                        errorMessage = nil
                        isLoading = true

                        Task {
                            await repo.restoreFromCloud(email: clean, displayName: displayName.isEmpty ? nil : displayName)
                            await MainActor.run {
                                isLoading = false
                                onDismiss()
                            }
                        }
                    }) {
                        HStack(spacing: 8) {
                            if isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .black))
                            } else {
                                Text("Войти и синхронизировать")
                                    .font(.system(size: 15, weight: .bold))
                            }
                        }
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(theme.currentTheme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .disabled(isLoading)
                }
                .padding(.horizontal, 20)
            }

            Spacer()
        }
        .background(Color.darkSurfaceVariant.edgesIgnoringSafeArea(.all))
    }
}
