import SwiftUI

public struct MainTabView: View {
    @ObservedObject var nav = AppNavigationManager.shared
    @ObservedObject var repo = MusicRepository.shared

    public init() {}

    public var body: some View {
        ZStack(alignment: .bottom) {
            Color.deepBlack.edgesIgnoringSafeArea(.all)

            // Current Tab Screen
            Group {
                switch nav.selectedTab {
                case .home:
                    HomeScreenView(
                        onArtistClick: { name in nav.openArtist(name) },
                        onProfileClick: { nav.showProfileScreen = true }
                    )
                case .search:
                    SearchScreenView(
                        onArtistClick: { name in nav.openArtist(name) }
                    )
                case .library:
                    LibraryScreenView(
                        onArtistClick: { name in nav.openArtist(name) }
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
                        nav.showFullPlayer = true
                    }
                )

                // Custom Tab Bar docked flush with bottom edge
                CustomTabBar()
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
                if let artist = nav.navArtistName {
                    ArtistScreenView(
                        artistName: artist,
                        onBack: { nav.navArtistName = nil },
                        onSelectTab: { tab in
                            nav.navigateTo(tab: tab)
                        },
                        onOpenFullPlayer: { nav.showFullPlayer = true }
                    )
                    .transition(.move(edge: .trailing))
                }
            }
        )
        .animation(.easeInOut(duration: 0.25), value: nav.navArtistName)
        // Full Player Modal
        .fullScreenCover(isPresented: $nav.showFullPlayer) {
            FullPlayerView(
                onDismiss: { nav.showFullPlayer = false },
                onArtistClick: { name in
                    nav.showFullPlayer = false
                    nav.openArtist(name)
                }
            )
        }
        // Profile & Settings Screen
        .fullScreenCover(isPresented: $nav.showProfileScreen) {
            ProfileAndSettingsView(
                onBack: { nav.showProfileScreen = false }
            )
        }
    }
}
