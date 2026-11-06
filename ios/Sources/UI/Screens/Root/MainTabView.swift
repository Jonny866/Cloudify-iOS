import SwiftUI

public struct MainTabView: View {
    @State private var selectedTab: MainTab = .home
    @State private var showFullPlayer: Bool = false
    @State private var showAuthSheet: Bool = false
    @State private var navArtistName: String? = nil

    @ObservedObject var player = AudioPlayerService.shared

    public var body: some View {
        ZStack(alignment: .bottom) {
            Color.deepBlack.edgesIgnoringSafeArea(.all)

            // Current Tab Screen
            Group {
                switch selectedTab {
                case .home:
                    HomeScreenView(
                        onArtistClick: { name in navArtistName = name },
                        onProfileClick: { showAuthSheet = true }
                    )
                case .search:
                    SearchScreenView(
                        onArtistClick: { name in navArtistName = name }
                    )
                case .library:
                    LibraryScreenView(
                        onArtistClick: { name in navArtistName = name }
                    )
                case .stats:
                    StatsScreenView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // Bottom Stack: Mini Player + Custom Tab Bar
            VStack(spacing: 0) {
                // Mini Player docked above tabs
                MiniPlayerView(
                    onOpenFullPlayer: {
                        showFullPlayer = true
                    }
                )

                // Custom Tab Bar
                CustomTabBar(selectedTab: $selectedTab)
            }
        }
        // Artist Screen Full Screen / Slide in
        .overlay(
            Group {
                if let artist = navArtistName {
                    ArtistScreenView(
                        artistName: artist,
                        onBack: { navArtistName = nil }
                    )
                    .transition(.move(edge: .trailing))
                }
            }
        )
        .animation(.easeInOut(duration: 0.25), value: navArtistName)
        // Full Player Modal
        .fullScreenCover(isPresented: $showFullPlayer) {
            FullPlayerView(
                onDismiss: { showFullPlayer = false },
                onArtistClick: { name in
                    showFullPlayer = false
                    navArtistName = name
                }
            )
        }
        // Auth / Profile Sheet
        .sheet(isPresented: $showAuthSheet) {
            WelcomeAuthView(
                onDismiss: { showAuthSheet = false }
            )
            .presentationDetents([.medium, .large])
        }
    }
}
