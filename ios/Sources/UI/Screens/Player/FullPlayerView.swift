import SwiftUI
import AVKit

public struct FullPlayerView: View {
    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var repo = MusicRepository.shared
    @ObservedObject var theme = ThemeManager.shared

    public let onDismiss: () -> Void
    public let onArtistClick: (String) -> Void

    @State private var isDraggingSlider: Bool = false
    @State private var dragSliderValue: Double = 0.0
    @State private var showSleepTimerSheet: Bool = false
    @State private var showLyricsSheet: Bool = false
    @State private var dragOffset: CGFloat = 0.0

    public var body: some View {
        if let track = player.currentTrack {
            ZStack {
                Color.deepBlack.edgesIgnoringSafeArea(.all)

                VStack(spacing: 0) {
                    // Drag Handle & Top Bar
                    topBar(track: track)

                    Spacer(minLength: 10)

                    // Big Cover Artwork
                    artworkView(track: track)

                    Spacer(minLength: 16)

                    // Track Title, Artist, Favorite Heart
                    metaSection(track: track)

                    Spacer(minLength: 12)

                    // Timeline Scrubber
                    timelineSection

                    Spacer(minLength: 12)

                    // Main Controls (Shuffle, Prev, Play/Pause, Next, Repeat)
                    controlsSection

                    Spacer(minLength: 16)

                    // Bottom Bar (AirPlay / Device route, Lyrics, Queue)
                    bottomBarSection(track: track)

                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 24)
            }
            .offset(y: max(0, dragOffset))
            .gesture(
                DragGesture()
                    .onChanged { value in
                        if value.translation.height > 0 {
                            dragOffset = value.translation.height
                        }
                    }
                    .onEnded { value in
                        if value.translation.height > 120 {
                            onDismiss()
                        } else {
                            withAnimation(.spring()) {
                                dragOffset = 0
                            }
                        }
                    }
            )
            .sheet(isPresented: $showSleepTimerSheet) {
                sleepTimerSheet
                    .presentationDetents([.fraction(0.35)])
            }
            .sheet(isPresented: $showLyricsSheet) {
                lyricsSheet(track: track)
                    .presentationDetents([.medium, .large])
            }
        }
    }

    // Top Bar
    private func topBar(track: Track) -> some View {
        HStack {
            Button(action: onDismiss) {
                Image(systemName: "chevron.down")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.textPrimary)
                    .frame(width: 40, height: 40)
            }

            Spacer()

            VStack(spacing: 2) {
                Text("СЕЙЧАС ИГРАЕТ")
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.2)
                    .foregroundColor(theme.currentTheme.light)

                Text(track.genre)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.textPrimary)
            }

            Spacer()

            Button(action: {
                showSleepTimerSheet = true
            }) {
                Image(systemName: "moon.fill")
                    .font(.system(size: 18))
                    .foregroundColor(player.sleepTimerMinutes != nil ? theme.currentTheme.primary : .textMuted)
                    .frame(width: 40, height: 40)
            }
        }
        .padding(.top, 14)
    }

    // Big Cover Artwork
    private func artworkView(track: Track) -> some View {
        AsyncImage(url: URL(string: track.artworkUrl)) { img in
            img.resizable().aspectRatio(contentMode: .fill)
        } placeholder: {
            Color.darkSurface
        }
        .frame(maxWidth: .infinity)
        .aspectRatio(1.0, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .shadow(color: theme.currentTheme.primary.opacity(0.18), radius: 24, y: 12)
        .shadow(color: Color.black.opacity(0.6), radius: 14, y: 8)
    }

    // Track Meta
    private func metaSection(track: Track) -> some View {
        let isFav = repo.isFavorite(track.id)

        return HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                Text(track.title)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.textPrimary)
                    .lineLimit(1)

                Button(action: {
                    onDismiss()
                    onArtistClick(track.artist)
                }) {
                    Text(track.artist)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(theme.currentTheme.light)
                        .lineLimit(1)
                }
            }

            Spacer()

            Button(action: {
                repo.toggleFavorite(track)
            }) {
                Image(systemName: isFav ? "heart.fill" : "heart")
                    .font(.system(size: 26))
                    .foregroundColor(isFav ? .accentPink : .textMuted)
                    .frame(width: 44, height: 44)
            }
        }
    }

    // Timeline Scrubber
    private var timelineSection: some View {
        let currentPos = isDraggingSlider ? dragSliderValue : player.currentTime
        let total = max(1.0, player.duration)

        return VStack(spacing: 6) {
            Slider(
                value: Binding(
                    get: { currentPos },
                    set: { dragSliderValue = $0 }
                ),
                in: 0...total,
                onEditingChanged: { editing in
                    isDraggingSlider = editing
                    if !editing {
                        player.seek(to: dragSliderValue)
                    }
                }
            )
            .accentColor(theme.currentTheme.primary)

            HStack {
                Text(formatTime(currentPos))
                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                    .foregroundColor(.textSecondary)

                Spacer()

                Text(formatTime(total))
                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                    .foregroundColor(.textSecondary)
            }
        }
    }

    // Transport Controls
    private var controlsSection: some View {
        HStack(spacing: 0) {
            // Shuffle
            Button(action: {
                player.toggleShuffle()
            }) {
                Image(systemName: "shuffle")
                    .font(.system(size: 20))
                    .foregroundColor(player.isShuffle ? theme.currentTheme.primary : .textMuted)
                    .frame(maxWidth: .infinity)
            }

            // Previous
            Button(action: {
                player.previous()
            }) {
                Image(systemName: "backward.fill")
                    .font(.system(size: 26))
                    .foregroundColor(.textPrimary)
                    .frame(maxWidth: .infinity)
            }

            // Big Play/Pause Circular Button
            Button(action: {
                player.togglePlayPause()
            }) {
                ZStack {
                    Circle()
                        .fill(theme.currentTheme.primary)
                        .frame(width: 68, height: 68)
                        .shadow(color: theme.currentTheme.primary.opacity(0.3), radius: 12, y: 4)

                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(theme.currentTheme == .yellow ? .black : .white)
                }
            }
            .frame(maxWidth: .infinity)

            // Next
            Button(action: {
                player.next()
            }) {
                Image(systemName: "forward.fill")
                    .font(.system(size: 26))
                    .foregroundColor(.textPrimary)
                    .frame(maxWidth: .infinity)
            }

            // Repeat
            Button(action: {
                player.toggleRepeat()
            }) {
                Image(systemName: player.repeatMode == .one ? "repeat.1" : "repeat")
                    .font(.system(size: 20))
                    .foregroundColor(player.repeatMode != .off ? theme.currentTheme.primary : .textMuted)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    // Bottom Bar (AirPlay / Device route, Lyrics)
    private func bottomBarSection(track: Track) -> some View {
        HStack {
            // AirPlay picker
            AirPlayRoutePicker()
                .frame(width: 32, height: 32)

            Text("Динамик iPhone")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.textSecondary)

            Spacer()

            Button(action: {
                showLyricsSheet = true
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "quote.bubble.fill")
                        .font(.system(size: 14))
                    Text("Текст")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundColor(.textPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(Color.darkSurfaceVariant)
                .clipShape(Capsule())
            }
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        let sec = max(0, Int(seconds))
        let m = sec / 60
        let s = sec % 60
        return String(format: "%02d:%02d", m, s)
    }

    // Sleep Timer Sheet
    private var sleepTimerSheet: some View {
        VStack(spacing: 16) {
            Text("Таймер сна")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.textPrimary)
                .padding(.top, 16)

            HStack(spacing: 12) {
                ForEach([15, 30, 45, 60], id: \.self) { min in
                    Button("\(min) мин") {
                        player.setSleepTimer(minutes: min)
                        showSleepTimerSheet = false
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.textPrimary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.darkSurface)
                    .clipShape(Capsule())
                }
            }

            if player.sleepTimerMinutes != nil {
                Button("Отключить таймер") {
                    player.cancelSleepTimer()
                    showSleepTimerSheet = false
                }
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.accentPink)
                .padding(.top, 6)
            }

            Spacer()
        }
        .background(Color.darkSurfaceVariant.edgesIgnoringSafeArea(.all))
    }

    // Lyrics Sheet
    private func lyricsSheet(track: Track) -> some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 16) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color.gray.opacity(0.4))
                    .frame(width: 36, height: 4)
                    .padding(.top, 10)

                Text(track.title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.textPrimary)

                Text(track.artist)
                    .font(.system(size: 14))
                    .foregroundColor(.textSecondary)

                Divider().background(Color.darkBorder)

                Text(track.lyrics.isEmpty ? "Текст для этого трека пока не добавлен" : track.lyrics)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundColor(.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                    .padding(.top, 20)

                Spacer(minLength: 40)
            }
        }
        .background(Color.darkSurfaceVariant.edgesIgnoringSafeArea(.all))
    }
}

// Native AirPlay Picker wrapper
public struct AirPlayRoutePicker: UIViewRepresentable {
    public func makeUIView(context: Context) -> AVRoutePickerView {
        let routePicker = AVRoutePickerView()
        routePicker.tintColor = UIColor.white
        routePicker.activeTintColor = UIColor(red: 250/255, green: 204/255, blue: 21/255, alpha: 1.0)
        routePicker.prioritizesVideoDevices = false
        return routePicker
    }

    public func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}
