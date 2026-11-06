import SwiftUI

public struct WelcomeAuthView: View {
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var theme = ThemeManager.shared
    public let onLoginSuccess: () -> Void

    @State private var isSignUpMode: Bool = false
    @State private var inputName: String = ""
    @State private var inputEmail: String = ""
    @State private var isLoggingIn: Bool = false
    @State private var loginStatusText: String = ""
    @State private var showGoogleEmailAlert: Bool = false
    @State private var googleEmailInput: String = ""
    @State private var toastMessage: String? = nil

    public init(onLoginSuccess: @escaping () -> Void) {
        self.onLoginSuccess = onLoginSuccess
    }

    public var body: some View {
        ZStack {
            Color.deepBlack.edgesIgnoringSafeArea(.all)

            // Ambient background aura
            VStack {
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                theme.currentTheme.primary.opacity(0.22),
                                Color.clear
                            ],
                            center: .center,
                            startRadius: 20,
                            endRadius: 180
                        )
                    )
                    .frame(width: 340, height: 340)
                    .offset(y: -50)
                Spacer()
            }
            .edgesIgnoringSafeArea(.all)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 20) {
                    Spacer().frame(height: 30)

                    // App Logo
                    ZStack {
                        RoundedRectangle(cornerRadius: 26)
                            .fill(theme.currentTheme == .yellow ? Color(hex: 0x1E1A0E) : Color(hex: 0x13101E))
                            .frame(width: 92, height: 92)
                            .overlay(
                                RoundedRectangle(cornerRadius: 26)
                                    .stroke(theme.currentTheme.primary.opacity(0.25), lineWidth: 1)
                            )

                        Image(uiImage: UIImage(named: theme.currentTheme.logoImageName) ?? UIImage())
                            .resizable()
                            .renderingMode(.original)
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 70, height: 70)
                    }

                    // Title & Subtitle
                    VStack(spacing: 6) {
                        Text("Cloudify")
                            .font(.system(size: 32, weight: .heavy))
                            .tracking(1.2)
                            .foregroundColor(.textPrimary)

                        Text("Музыка без границ и блокировок")
                            .font(.system(size: 14, weight: .medium))
                            .tracking(0.5)
                            .foregroundColor(theme.currentTheme.light)
                    }

                    Spacer().frame(height: 10)

                    // Mode Selector Tabs: Вход / Регистрация
                    HStack(spacing: 4) {
                        tabButton(title: "Вход", isSelected: !isSignUpMode) {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                isSignUpMode = false
                            }
                        }

                        tabButton(title: "Регистрация", isSelected: isSignUpMode) {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                isSignUpMode = true
                            }
                        }
                    }
                    .padding(4)
                    .background(Color.darkSurfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                    // Card Container
                    VStack(spacing: 16) {
                        // Google Button
                        Button(action: {
                            showGoogleEmailAlert = true
                        }) {
                            HStack(spacing: 10) {
                                Text("G")
                                    .font(.system(size: 22, weight: .black))
                                    .foregroundColor(Color(hex: 0x4285F4))

                                Text(isSignUpMode ? "Продолжить с Google" : "Войти через Google")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(Color(hex: 0x1F1F1F))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                        .disabled(isLoggingIn)

                        // Divider with text
                        HStack {
                            Rectangle()
                                .fill(Color.darkBorder)
                                .frame(height: 1)
                            Text(isSignUpMode ? "  ИЛИ РУЧНАЯ РЕГИСТРАЦИЯ  " : "  ИЛИ ВХОД ПО EMAIL  ")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.textMuted)
                            Rectangle()
                                .fill(Color.darkBorder)
                                .frame(height: 1)
                        }
                        .padding(.vertical, 4)

                        if isSignUpMode {
                            // Name field
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Ваше имя или никнейм")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.textMuted)

                                HStack(spacing: 10) {
                                    Image(systemName: "person.fill")
                                        .foregroundColor(theme.currentTheme.light)
                                    TextField("Например: Александр", text: $inputName)
                                        .foregroundColor(.textPrimary)
                                }
                                .padding(14)
                                .background(Color.darkSurfaceVariant)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(Color.darkBorder, lineWidth: 1)
                                )
                            }

                            // Email field
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Email (для облака и плейлистов)")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.textMuted)

                                HStack(spacing: 10) {
                                    Image(systemName: "envelope.fill")
                                        .foregroundColor(.textMuted)
                                    TextField("myaccount@gmail.com", text: $inputEmail)
                                        .keyboardType(.emailAddress)
                                        .autocapitalization(.none)
                                        .disableAutocorrection(true)
                                        .foregroundColor(.textPrimary)
                                }
                                .padding(14)
                                .background(Color.darkSurfaceVariant)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(Color.darkBorder, lineWidth: 1)
                                )
                            }

                            // Submit Registration Button
                            Button(action: {
                                performLogin(email: inputEmail, displayName: inputName.isEmpty ? "Слушатель Cloudify" : inputName)
                            }) {
                                Text("Создать профиль и начать")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(theme.currentTheme == .yellow ? .black : .white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 50)
                                    .background(theme.currentTheme.primary)
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                            }
                            .disabled(isLoggingIn)
                        } else {
                            // Login Mode: Enter Email
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Email вашего аккаунта")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.textMuted)

                                HStack(spacing: 10) {
                                    Image(systemName: "envelope.fill")
                                        .foregroundColor(theme.currentTheme.light)
                                    TextField("например: jonnylermentov02@gmail.com", text: $inputEmail)
                                        .keyboardType(.emailAddress)
                                        .autocapitalization(.none)
                                        .disableAutocorrection(true)
                                        .foregroundColor(.textPrimary)
                                }
                                .padding(14)
                                .background(Color.darkSurfaceVariant)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(Color.darkBorder, lineWidth: 1)
                                )
                            }

                            // Submit Login Button
                            Button(action: {
                                performLogin(email: inputEmail, displayName: nil)
                            }) {
                                Text("Войти и восстановить музыку")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(theme.currentTheme == .yellow ? .black : .white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 50)
                                    .background(theme.currentTheme.primary)
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                            }
                            .disabled(isLoggingIn)
                        }

                        // Fast Guest Login Button
                        Button(action: {
                            repo.user = UserProfile(
                                id: "guest_\(Int64(Date().timeIntervalSince1970 * 1000))",
                                name: "Гость",
                                email: "Войдите для сохранения плейлистов",
                                isLoggedIn: true
                            )
                            repo.saveToStorage()
                            onLoginSuccess()
                        }) {
                            Text("Войти как гость без сохранения")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.textMuted)
                                .padding(.vertical, 6)
                        }
                        .disabled(isLoggingIn)
                    }
                    .padding(20)
                    .background(Color.darkSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 22))
                    .overlay(
                        RoundedRectangle(cornerRadius: 22)
                            .stroke(Color.darkBorder, lineWidth: 1)
                    )

                    Spacer().frame(height: 40)
                }
                .padding(.horizontal, 24)
            }

            // Loading HUD
            if isLoggingIn {
                ZStack {
                    Color.black.opacity(0.65).edgesIgnoringSafeArea(.all)
                    VStack(spacing: 16) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: theme.currentTheme.primary))
                            .scaleEffect(1.4)
                        Text(loginStatusText)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                    }
                    .padding(28)
                    .background(Color.darkSurfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(theme.currentTheme.primary.opacity(0.3), lineWidth: 1)
                    )
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
                        .shadow(radius: 10)
                        .padding(.bottom, 40)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        // Google Email Fallback Dialog
        .alert("Вход через Google", isPresented: $showGoogleEmailAlert) {
            TextField("Google Email (напр. user@gmail.com)", text: $googleEmailInput)
                .autocapitalization(.none)
            Button("Войти") {
                if !googleEmailInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    performLogin(email: googleEmailInput, displayName: nil)
                }
            }
            Button("Отмена", role: .cancel) { }
        } message: {
            Text("Укажите ваш Google Email для входа и восстановления библиотеки треков с сервера:")
        }
    }

    private func tabButton(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(isSelected ? (theme.currentTheme == .yellow ? .black : .white) : .textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(isSelected ? theme.currentTheme.primary : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private func performLogin(email: String, displayName: String?) {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanEmail.isEmpty else {
            showToast("Пожалуйста, укажите Email")
            return
        }

        isLoggingIn = true
        loginStatusText = "Подключение к серверу и поиск треков..."

        Task {
            let result = await repo.restoreFromCloud(email: cleanEmail, displayName: displayName)
            await MainActor.run {
                self.isLoggingIn = false
                if result.count > 0 {
                    showToast("Облако: Восстановлено \(result.count) любимых треков!")
                } else {
                    showToast("Вход выполнен: \(repo.user.name)!")
                }
                onLoginSuccess()
            }
        }
    }

    private func showToast(_ msg: String) {
        withAnimation { toastMessage = msg }
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
            withAnimation { toastMessage = nil }
        }
    }
}
