import SwiftUI

public struct MainTabView: View {
    @State private var selectedTab: MainTab = .home
    @State private var showFullPlayer: Bool = false
    @State private var showProfileScreen: Bool = false
    @State private var navArtistName: String? = nil

    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var repo = MusicRepository.shared

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

            // Bottom Stack: Download Banner + Floating Mini Player + Docked Custom Tab Bar
            VStack(spacing: 0) {
                // Download progress bar banner (if downloading)
                DownloadProgressBarView()

                // Mini Player floating above tabs
                MiniPlayerView(
                    onOpenFullPlayer: {
                        showFullPlayer = true
                    }
                )

                // Custom Tab Bar docked flush with bottom edge
                CustomTabBar(selectedTab: $selectedTab)
            }
            .ignoresSafeArea(edges: .bottom)

            // In-app Download Toast Notification
            if let toast = repo.downloadToastMessage {
                VStack {
                    Text(toast)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.textPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color(hex: 0x1F2937).opacity(0.96))
                        .clipShape(Capsule())
                        .shadow(color: Color.black.opacity(0.4), radius: 8, y: 4)
                        .padding(.top, 48)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                .animation(.spring(response: 0.35, dampingFraction: 0.8), value: toast)
            }
        }
        .edgesIgnoringSafeArea(.bottom)
        .ignoresSafeArea(.keyboard)
        // Artist Screen Overlay
        .overlay(
            Group {
                if let artist = navArtistName {
                    ArtistScreenView(
                        artistName: artist,
                        selectedTab: $selectedTab,
                        onBack: { navArtistName = nil },
                        onSelectTab: { tab in
                            selectedTab = tab
                            navArtistName = nil
                        },
                        onOpenFullPlayer: { showFullPlayer = true }
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
