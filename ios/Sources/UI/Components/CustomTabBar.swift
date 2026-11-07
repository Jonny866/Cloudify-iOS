import SwiftUI

public enum MainTab: Int, CaseIterable {
    case home = 0
    case search = 1
    case library = 2
    case stats = 3

    public var title: String {
        switch self {
        case .home: return "Главная"
        case .search: return "Поиск"
        case .library: return "Моя музыка"
        case .stats: return "Статистика"
        }
    }

    public var icon: String {
        switch self {
        case .home: return "house.fill"
        case .search: return "magnifyingglass"
        case .library: return "music.note.list"
        case .stats: return "chart.bar.fill"
        }
    }
}

public struct CustomTabBar: View {
    @Binding public var selectedTab: MainTab
    @ObservedObject var theme = ThemeManager.shared

    public init(selectedTab: Binding<MainTab>) {
        self._selectedTab = selectedTab
    }

    public var body: some View {
        HStack(spacing: 0) {
            ForEach(MainTab.allCases, id: \.self) { tab in
                let isSelected = selectedTab == tab
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.15)) {
                        selectedTab = tab
                    }
                }) {
                    VStack(spacing: 3) {
                        Image(systemName: tab.icon)
                            .font(.system(size: 17, weight: isSelected ? .bold : .medium))
                            .foregroundColor(isSelected ? theme.currentTheme.primary : .textMuted)

                        Text(tab.title)
                            .font(.system(size: 10, weight: isSelected ? .bold : .medium))
                            .foregroundColor(isSelected ? theme.currentTheme.primary : .textMuted)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                    .padding(.top, 15)
                    .padding(.bottom, 5)
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .background(
            Color.darkSurface
                .overlay(
                    Rectangle()
                        .fill(Color.darkBorder.opacity(0.6))
                        .frame(height: 0.5),
                    alignment: .top
                )
        )
    }
}
