import SwiftUI

public struct PlayingEqualizerView: View {
    @ObservedObject var theme = ThemeManager.shared
    @State private var phase1: CGFloat = 0.3
    @State private var phase2: CGFloat = 0.8
    @State private var phase3: CGFloat = 0.5

    public var body: some View {
        HStack(alignment: .bottom, spacing: 2.5) {
            RoundedRectangle(cornerRadius: 1.5)
                .fill(theme.currentTheme.primary)
                .frame(width: 3, height: 14 * phase1)
            RoundedRectangle(cornerRadius: 1.5)
                .fill(theme.currentTheme.primary)
                .frame(width: 3, height: 14 * phase2)
            RoundedRectangle(cornerRadius: 1.5)
                .fill(theme.currentTheme.primary)
                .frame(width: 3, height: 14 * phase3)
        }
        .frame(width: 16, height: 14, alignment: .bottom)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.45).repeatForever(autoreverses: true)) {
                phase1 = 0.95
            }
            withAnimation(.easeInOut(duration: 0.35).delay(0.1).repeatForever(autoreverses: true)) {
                phase2 = 0.25
            }
            withAnimation(.easeInOut(duration: 0.55).delay(0.2).repeatForever(autoreverses: true)) {
                phase3 = 0.9
            }
        }
    }
}
