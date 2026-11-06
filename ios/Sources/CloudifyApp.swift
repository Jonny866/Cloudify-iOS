import SwiftUI

@main
struct CloudifyApp: App {
    @StateObject private var theme = ThemeManager.shared
    @StateObject private var player = AudioPlayerService.shared
    @StateObject private var repo = MusicRepository.shared

    @State private var isSplashVisible: Bool = true

    var body: some Scene {
        WindowGroup {
            ZStack {
                Color.deepBlack.edgesIgnoringSafeArea(.all)

                if repo.user.isLoggedIn {
                    MainTabView()
                } else {
                    WelcomeAuthView(
                        onLoginSuccess: {
                            // User is now logged in
                        }
                    )
                }

                if isSplashVisible {
                    SplashScreenView(
                        onFinish: {
                            withAnimation(.easeOut(duration: 0.35)) {
                                isSplashVisible = false
                            }
                        }
                    )
                    .transition(.opacity)
                    .zIndex(100)
                }
            }
            .environmentObject(theme)
            .environmentObject(player)
            .environmentObject(repo)
            .preferredColorScheme(.dark)
        }
    }
}
