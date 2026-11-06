import Foundation
import AVFoundation
import MediaPlayer
import UIKit

public enum RepeatMode: String, CaseIterable {
    case off = "OFF"
    case all = "ALL"
    case one = "ONE"
}

public final class AudioPlayerService: NSObject, ObservableObject {
    public static let shared = AudioPlayerService()

    private var player: AVPlayer?
    private var timeObserverToken: Any?
    private var artworkCache = NSCache<NSString, UIImage>()

    @Published public var currentTrack: Track?
    @Published public var queue: [Track] = []
    @Published public var currentIndex: Int = 0
    @Published public var isPlaying: Bool = false
    @Published public var isBuffering: Bool = false
    @Published public var currentTime: Double = 0.0
    @Published public var duration: Double = 180.0
    @Published public var isShuffle: Bool = false
    @Published public var repeatMode: RepeatMode = .off
    @Published public var sleepTimerMinutes: Int? = nil

    private var sleepTimer: Timer?
    private var originalQueue: [Track] = []

    override private init() {
        super.init()
        setupAudioSession()
        setupRemoteCommands()
    }

    deinit {
        removeTimeObserver()
    }

    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.allowAirPlay, .allowBluetoothHFP, .allowBluetoothA2DP])
            try session.setActive(true)
        } catch {
            print("AudioPlayerService: Failed to setup AVAudioSession: \(error)")
        }
    }

    private func setupRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [weak self] _ in
            self?.play()
            return .success
        }

        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            self?.pause()
            return .success
        }

        commandCenter.togglePlayPauseCommand.isEnabled = true
        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.togglePlayPause()
            return .success
        }

        commandCenter.nextTrackCommand.isEnabled = true
        commandCenter.nextTrackCommand.addTarget { [weak self] _ in
            self?.next()
            return .success
        }

        commandCenter.previousTrackCommand.isEnabled = true
        commandCenter.previousTrackCommand.addTarget { [weak self] _ in
            self?.previous()
            return .success
        }

        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let self = self, let positionEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            self.seek(to: positionEvent.positionTime)
            return .success
        }
    }

    // Playback Controls
    public func playTrack(_ track: Track, in queue: [Track]? = nil) {
        if let newQueue = queue, !newQueue.isEmpty {
            self.originalQueue = newQueue
            self.queue = isShuffle ? newQueue.shuffled() : newQueue
            if let idx = self.queue.firstIndex(where: { $0.id == track.id }) {
                self.currentIndex = idx
            } else {
                self.queue.insert(track, at: 0)
                self.currentIndex = 0
            }
        } else {
            if self.queue.isEmpty {
                self.queue = [track]
                self.originalQueue = [track]
                self.currentIndex = 0
            }
        }

        startTrackPlayback(track)
    }

    private func startTrackPlayback(_ track: Track) {
        self.currentTrack = track
        self.currentTime = 0.0
        self.duration = max(1.0, Double(track.durationMs) / 1000.0)
        self.isBuffering = true

        MusicRepository.shared.addRecentTrack(track)

        guard let url = URL(string: track.streamUrl) else {
            isBuffering = false
            return
        }

        removeTimeObserver()
        let playerItem = AVPlayerItem(url: url)
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(itemDidFinishPlaying), name: .AVPlayerItemDidPlayToEndTime, object: playerItem)

        if player == nil {
            player = AVPlayer(playerItem: playerItem)
            player?.automaticallyWaitsToMinimizeStalling = true
        } else {
            player?.replaceCurrentItem(with: playerItem)
        }

        addPeriodicTimeObserver()
        player?.play()
        self.isPlaying = true
        self.isBuffering = false

        updateNowPlayingInfo()
    }

    public func play() {
        player?.play()
        isPlaying = true
        updateNowPlayingInfo()
    }

    public func pause() {
        player?.pause()
        isPlaying = false
        updateNowPlayingInfo()
    }

    public func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            play()
        }
    }

    public func next() {
        guard !queue.isEmpty else { return }
        if repeatMode == .one, currentTrack != nil {
            seek(to: 0)
            play()
            return
        }

        if currentIndex + 1 < queue.count {
            currentIndex += 1
            playTrack(queue[currentIndex])
        } else if repeatMode == .all {
            currentIndex = 0
            playTrack(queue[currentIndex])
        } else {
            pause()
            seek(to: 0)
        }
    }

    public func previous() {
        guard !queue.isEmpty else { return }
        if currentTime > 3.0 {
            seek(to: 0)
            return
        }

        if currentIndex > 0 {
            currentIndex -= 1
            playTrack(queue[currentIndex])
        } else {
            seek(to: 0)
        }
    }

    public func seek(to seconds: Double) {
        let time = CMTime(seconds: seconds, preferredTimescale: 600)
        player?.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            self?.currentTime = seconds
            self?.updateNowPlayingInfo()
        }
    }

    public func toggleShuffle() {
        isShuffle.toggle()
        if isShuffle {
            guard let cur = currentTrack else { return }
            var remaining = queue.filter { $0.id != cur.id }
            remaining.shuffle()
            self.queue = [cur] + remaining
            self.currentIndex = 0
        } else {
            guard let cur = currentTrack else { return }
            self.queue = self.originalQueue
            if let idx = self.queue.firstIndex(where: { $0.id == cur.id }) {
                self.currentIndex = idx
            }
        }
    }

    public func toggleRepeat() {
        switch repeatMode {
        case .off: repeatMode = .all
        case .all: repeatMode = .one
        case .one: repeatMode = .off
        }
    }

    public func setSleepTimer(minutes: Int) {
        sleepTimer?.invalidate()
        sleepTimerMinutes = minutes

        sleepTimer = Timer.scheduledTimer(withTimeInterval: TimeInterval(minutes * 60), repeats: false) { [weak self] _ in
            self?.pause()
            self?.sleepTimerMinutes = nil
        }
    }

    public func cancelSleepTimer() {
        sleepTimer?.invalidate()
        sleepTimer = nil
        sleepTimerMinutes = nil
    }

    @objc private func itemDidFinishPlaying() {
        next()
    }

    private var lastRecordedSecond: Int = 0

    private func addPeriodicTimeObserver() {
        lastRecordedSecond = 0
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserverToken = player?.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self else { return }
            let sec = CMTimeGetSeconds(time)
            if !sec.isNaN && !sec.isInfinite {
                self.currentTime = sec
                let intSec = Int(sec)
                if intSec > self.lastRecordedSecond && self.isPlaying {
                    let diff = intSec - self.lastRecordedSecond
                    if diff > 0 && diff < 5 {
                        MusicRepository.shared.addListenedSeconds(diff)
                    }
                    self.lastRecordedSecond = intSec
                }
            }
            if let curItem = self.player?.currentItem {
                let itemDuration = CMTimeGetSeconds(curItem.duration)
                if !itemDuration.isNaN && !itemDuration.isInfinite && itemDuration > 0 {
                    self.duration = itemDuration
                }
            }
        }
    }

    private func removeTimeObserver() {
        if let token = timeObserverToken {
            player?.removeTimeObserver(token)
            timeObserverToken = nil
        }
    }

    // Now Playing Info (Control Center & Lockscreen)
    private func updateNowPlayingInfo() {
        guard let track = currentTrack else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.artist,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTime,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0
        ]

        if let cachedImage = artworkCache.object(forKey: track.artworkUrl as NSString) {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: cachedImage.size) { _ in cachedImage }
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        } else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
            loadArtworkAsync(urlStr: track.artworkUrl)
        }
    }

    private func loadArtworkAsync(urlStr: String) {
        guard let url = URL(string: urlStr) else { return }
        URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            guard let self = self, let data = data, let image = UIImage(data: data) else { return }
            self.artworkCache.setObject(image, forKey: urlStr as NSString)
            DispatchQueue.main.async {
                if self.currentTrack?.artworkUrl == urlStr {
                    var curInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
                    curInfo[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                    MPNowPlayingInfoCenter.default().nowPlayingInfo = curInfo
                }
            }
        }.resume()
    }
}
