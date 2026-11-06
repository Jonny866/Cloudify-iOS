import SwiftUI

@main
struct CloudifyApp: App {
    @StateObject private var theme = ThemeManager.shared
    @StateObject private var player = AudioPlayerService.shared
    @StateObject private var repo = MusicRepository.shared

    var body: some Scene {
        WindowGroup {
            MainTabView()
                .environmentObject(theme)
                .environmentObject(player)
                .environmentObject(repo)
                .preferredColorScheme(.dark)
        }
    }
}
