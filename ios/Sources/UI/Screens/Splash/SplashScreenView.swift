import SwiftUI

public struct SplashScreenView: View {
    @ObservedObject var theme = ThemeManager.shared
    public let onFinish: () -> Void

    @State private var scale: CGFloat = 0.75
    @State private var opacity: Double = 0.0
    @State private var textOpacity: Double = 0.0

    public var body: some View {
        ZStack {
            Color.deepBlack.edgesIgnoringSafeArea(.all)

            // Ambient background glow
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            theme.currentTheme.primary.opacity(0.35),
                            Color.clear
                        ],
                        center: .center,
                        startRadius: 20,
                        endRadius: 160
                    )
                )
                .frame(width: 280, height: 280)
                .scaleEffect(scale * 1.15)
                .opacity(opacity * 0.7)

            VStack(spacing: 20) {
                // App Logo Box
                ZStack {
                    RoundedRectangle(cornerRadius: 32)
                        .fill(theme.currentTheme == .yellow ? Color(hex: 0x1E1A0E) : Color(hex: 0x13101E))
                        .frame(width: 130, height: 130)
                        .overlay(
                            RoundedRectangle(cornerRadius: 32)
                                .stroke(theme.currentTheme.primary.opacity(0.2), lineWidth: 1)
                        )

                    Image(uiImage: UIImage(named: theme.currentTheme.logoImageName) ?? UIImage())
                        .resizable()
                        .renderingMode(.original)
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 100, height: 100)
                }
                .scaleEffect(scale)
                .opacity(opacity)

                VStack(spacing: 6) {
                    Text("Cloudify")
                        .font(.system(size: 28, weight: .heavy))
                        .tracking(1.2)
                        .foregroundColor(.textPrimary)

                    Text("Музыка без границ")
                        .font(.system(size: 13, weight: .medium))
                        .tracking(0.5)
                        .foregroundColor(theme.currentTheme.light)
                }
                .opacity(textOpacity)
            }
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.65)) {
                scale = 1.0
            }
            withAnimation(.easeOut(duration: 0.4)) {
                opacity = 1.0
            }
            withAnimation(.easeOut(duration: 0.5).delay(0.1)) {
                textOpacity = 1.0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.95) {
                onFinish()
            }
        }
    }
}
