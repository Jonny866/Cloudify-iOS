import SwiftUI

public extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255.0,
            green: Double((hex >> 8) & 0xff) / 255.0,
            blue: Double(hex & 0xff) / 255.0,
            opacity: alpha
        )
    }

    // Core Solid Dark Palette
    static let deepBlack = Color(hex: 0x090810)
    static let darkSurface = Color(hex: 0x161320)
    static let darkSurfaceVariant = Color(hex: 0x221C30)
    static let darkBorder = Color(hex: 0x2E2642)

    // Secondary Accents
    static let accentPink = Color(hex: 0xEC4899)
    static let accentCyan = Color(hex: 0x06B6D4)
    static let accentGreen = Color(hex: 0x10B981)

    // Text Tokens
    static let textPrimary = Color(hex: 0xFFFFFF)
    static let textSecondary = Color(hex: 0x9CA3AF)
    static let textMuted = Color(hex: 0x6B7280)
}

public enum AppThemeMode: String, CaseIterable, Identifiable {
    case yellow = "yellow"
    case violet = "violet"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .yellow: return "Желтая тема"
        case .violet: return "Фиолетовая тема"
        }
    }

    public var primary: Color {
        switch self {
        case .yellow: return Color(hex: 0xFACC15)
        case .violet: return Color(hex: 0x8B5CF6)
        }
    }

    public var dark: Color {
        switch self {
        case .yellow: return Color(hex: 0xEAB308)
        case .violet: return Color(hex: 0x7C3AED)
        }
    }

    public var light: Color {
        switch self {
        case .yellow: return Color(hex: 0xFDE047)
        case .violet: return Color(hex: 0xA78BFA)
        }
    }

    public var glow: Color {
        switch self {
        case .yellow: return Color(hex: 0xFEF08A)
        case .violet: return Color(hex: 0xC4B5FD)
        }
    }

    public var logoImageName: String {
        switch self {
        case .yellow: return "cloudify_logo_yellow"
        case .violet: return "cloudify_logo_violet"
        }
    }
}

public final class ThemeManager: ObservableObject {
    public static let shared = ThemeManager()

    @Published public var currentTheme: AppThemeMode {
        didSet {
            UserDefaults.standard.set(currentTheme.rawValue, forKey: "app_theme_mode")
        }
    }

    private init() {
        let saved = UserDefaults.standard.string(forKey: "app_theme_mode") ?? "yellow"
        self.currentTheme = AppThemeMode(rawValue: saved) ?? .yellow
    }

    public func setTheme(_ theme: AppThemeMode) {
        currentTheme = theme
    }

    public func toggleTheme() {
        currentTheme = (currentTheme == .yellow) ? .violet : .yellow
    }
}
