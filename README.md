<img width="1280" height="720" alt="GH Banner" src="https://github.com/user-attachments/assets/75e5298b-181f-4eb2-8ae9-1ac3b3e78ef6" />

<br>

# Ochinai Inaho

Inaho is a beautiful, lightweight, and feature-rich local music player for Android, built entirely with modern Android development tools — Kotlin and Jetpack Compose. Designed with a focus on fluid animations, deep audio customization, and a sleek user experience.

No accounts. No streaming. No cloud. Just your music, on your device.

<img width="1280" height="720" alt="Screenshots" src="https://github.com/user-attachments/assets/d2d29ddc-2ac3-419b-87ba-b256e61d51df" />

---

## Features

### Beautiful & Fluid UI

- **Jetpack Compose driven** — a fully modern UI with seamless animated screen transitions and reactive state
- **AMOLED Black theme** — a true-black option to save battery on OLED/AMOLED displays
- **Animated mini-player** — persistent bar at the bottom of your library with a live progress strip and animated play/pause icon
- **Dynamic album art** — extracted, downsampled, and aggressively cached (LRU + disk) for stutter-free scrolling
- **AMV Mode** — Allow users to play .mp4 (Like song covers, etc)
- **Audio Visualizers** — multiple built-in visualizer styles (Bars, Waveform, Smooth Line, Circle Pulse, Peaks)
- **Custom Themes** — choose between Inaho, Yamada, System, or a Custom color with an interactive color picker
- **Immersive Mode** — hide status bar and navigation bar for a distraction-free experience

### Advanced Audio & Playback

- **Yamada Audio Engine** — a custom audio engine with 7 built-in presets: Off, Smart, Rock, Jazz, Classic, Pop, Bass
  - *Smart* preset uses `DynamicsProcessing` (API 28+) for dynamic gain riding — boosting signal on beat drops and lifts, with automatic `LoudnessEnhancer` fallback on older devices
  - All presets backed by Android's native `Equalizer`; EQ choice persists across sessions
- **Playback speed control** — 0.5× to 2.0× in six steps, pitch-stable
- **Pitch control** — Idk why I even add this, this is done because someone request it
- **ReplayGain** — volume normalization across tracks (available in Yamada AE)
- **Crossfade** — smooth transitions between tracks, adjustable duration
- **Sleep timer** — auto-pause after 5, 10, 15, 20, 30, or 60 minutes
- **Background & lock screen playback** — full `MediaSessionCompat` integration for rich media notifications and hardware controls
- **Android Auto Support** — seamlessly browse and play your library, custom playlists, and toggle EQ presets right from your car's dashboard

### Lyrics Support

- **Online Lyrics Fetching** — seamlessly fetch synced (`.lrc`) or plain lyrics from LRCLIB using smart metadata matching
- **Offline Save** — save fetched lyrics locally to your device for offline viewing and playback
- **Dynamic Display** — synced lyrics scroll automatically with the music, powered by smooth animations

### Library Management

- **Smart sorting** — by title (A–Z / Z–A), artist, recently added, or shortest/longest duration
- **Folder filtering** — optionally restrict your library to the `/Music` folder, keeping out voice notes and app audio
- **Favorites** — heart any track to build a dedicated favorites list, persisted locally
- **Queue view** — "Up Next" panel that auto-scrolls to the current song and lets you jump to any track
- **Custom Playlist** — Go make your own playlist! 
- **Clear Cover Cache** — Clear it sometimes, it won't hurt

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material Design 3, Palette API |
| Concurrency | Kotlin Coroutines, StateFlow |
| Playback | `MediaPlayer`, `MediaSessionCompat`, `MediaBrowserServiceCompat`, `MediaStore API` |
| Audio FX | `Equalizer`, `DynamicsProcessing`, `LoudnessEnhancer`, `Visualizer` |
| Metadata & Lyrics | `jaudiotagger`, LRCLIB API |
| Pagination | AndroidX Paging 3 |
| Image | `Coil`, `MediaMetadataRetriever` + custom LRU + disk cache |

---

## Permissions

| Permission | Reason |
|---|---|
| `READ_MEDIA_AUDIO` / `READ_MEDIA_VIDEO` (API 33+) | Read local audio and video files (for AMV mode) |
| `READ_EXTERNAL_STORAGE` (below API 33) | Legacy equivalent for reading files |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Background media playback |
| `POST_NOTIFICATIONS` (API 33+) | Playback notification |
| `INTERNET` | Fetch lyrics from online sources |
| `RECORD_AUDIO` | Required to capture audio output for the Visualizers |

---

## Android Auto Setup

Because Ochinai Inaho is a sideloaded, third-party application, it will not appear in Android Auto by default. You must enable "Unknown sources" in Android Auto's Developer Settings:

1. Open the **Android Auto** settings on your phone (you can search for it in your phone's Settings app).
2. Scroll down to the bottom and tap **Version** (or **Version and permission info**) 10 times repeatedly until a prompt appears asking to enable Developer Mode. Tap **OK**.
3. Tap the **3-dot menu (⋮)** in the top right corner and select **Developer settings**.
4. Scroll down and check the box for **Unknown sources**.
5. Exit the settings and reconnect your phone to your car. Ochinai Inaho will now be available in your car's app launcher!

---

## The Story Behind "Inaho"

> *"Makasih buat covernya, aku suka."*

This app was originally built and named in honor of [Ochinai Inaho](https://www.youtube.com/@%E8%90%BD%E4%B9%83%E3%81%84%E3%81%AA%E3%81%BB), a Japanese VTuber whose vocal cover inspired its creation. It started as a tribute — something made with the quiet hope of being noticed (But at the end not noticed) — and evolved into a passion project and a gift to anyone who loves their local music library.

---

## Developer

Developed by **Kanagawa Yamada**

- Email: albert.wesley.dion@gmail.com
- X / Twitter: [@YamadaKernel](https://x.com/YamadaKernel)
- GitHub: [@LoggingNewMemory](https://github.com/LoggingNewMemory)
- YouTube: [@KanagawaYamada](https://youtube.com/@KanagawaYamada)

---

## License

This project is source-available. If you fork or copy it, please keep the credit comment in `MainActivity.kt` intact. That's all I ask.

---

*Untuk Inaho — semoga karirmu terus bersinar.*
