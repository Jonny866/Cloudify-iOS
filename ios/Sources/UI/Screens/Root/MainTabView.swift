import SwiftUI

public struct MainTabView: View {
    @State private var selectedTab: MainTab = .home
    @State private var showFullPlayer: Bool = false
    @State private var showProfileScreen: Bool = false
    @State private var navArtistName: String? = nil

    @ObservedObject var player = AudioPlayerService.shared

    public init() {}

    public var body: some View {
        ZStack(alignment: .bottom) {
            Color.deepBlack.edgesIgnoringSafeArea(.all)

            // Current Tab Screen
            Group {
                switch selectedTab {
                case .home:
                    HomeScreenView(
                        onArtistClick: { name in navArtistName = name },
                        onProfileClick: { showProfileScreen = true }
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

            // Bottom Stack: Mini Player + Custom Tab Bar (Seamlessly flush with bottom edge)
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
            .background(
                Color.darkSurface
                    .ignoresSafeArea(edges: .bottom)
            )
        }
        .ignoresSafeArea(.keyboard)
        // Artist Screen Overlay
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
        // Profile & Settings Screen
        .fullScreenCover(isPresented: $showProfileScreen) {
            ProfileAndSettingsView(
                onBack: { showProfileScreen = false }
            )
        }
    }
}
