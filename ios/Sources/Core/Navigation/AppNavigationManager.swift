import SwiftUI

public final class AppNavigationManager: ObservableObject {
    public static let shared = AppNavigationManager()

    @Published public var selectedTab: MainTab = .home
    @Published public var navArtistName: String? = nil
    @Published public var showFullPlayer: Bool = false
    @Published public var showProfileScreen: Bool = false

    private init() {}

    public func navigateTo(tab: MainTab) {
        withAnimation(.easeInOut(duration: 0.15)) {
            selectedTab = tab
            navArtistName = nil
        }
    }

    public func openArtist(_ name: String) {
        withAnimation(.easeInOut(duration: 0.25)) {
            navArtistName = name
        }
    }
}
