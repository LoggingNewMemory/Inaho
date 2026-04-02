<img width="1280" height="720" alt="GH Banner" src="https://github.com/user-attachments/assets/56fd88d4-124b-484e-98d1-633f6a3f4f52" />

<br>

# Inaho Music Player

Inaho is a beautiful, lightweight, and feature-rich local music player for Android, built entirely with modern Android development tools — Kotlin and Jetpack Compose. Designed with a focus on fluid animations, deep audio customization, and a sleek user experience.

No accounts. No streaming. No cloud. Just your music, on your device.

<img width="1280" height="720" alt="Screenshots" src="https://github.com/user-attachments/assets/e18f20a8-35c6-49e9-8876-c4ede454ed54" />

---

## ✨ Features

### 🎨 Beautiful & Fluid UI

- **Jetpack Compose driven** — a fully modern UI with seamless animated screen transitions and reactive state
- **AMOLED Black theme** — a true-black option to save battery on OLED/AMOLED displays
- **Animated mini-player** — persistent bar at the bottom of your library with a live progress strip and animated play/pause icon
- **Dynamic album art** — extracted, downsampled, and aggressively cached (LRU + disk) for stutter-free scrolling

### 🎧 Advanced Audio & Playback

- **Yamada EQ** — a custom audio engine with 7 built-in presets: Off, Smart, Rock, Jazz, Classic, Pop, Bass
  - *Smart* preset uses `DynamicsProcessing` (API 28+) for dynamic gain riding — boosting signal on beat drops and lifts, with automatic `LoudnessEnhancer` fallback on older devices
  - All presets backed by Android's native `Equalizer`; EQ choice persists across sessions
- **Playback speed control** — 0.5× to 2.0× in six steps, pitch-stable
- **Sleep timer** — auto-pause after 5, 10, 15, 20, 30, or 60 minutes
- **Background & lock screen playback** — full `MediaSessionCompat` integration for rich media notifications and hardware controls

### 📁 Library Management

- **Smart sorting** — by title (A–Z / Z–A), artist, recently added, or duration
- **Folder filtering** — optionally restrict your library to the `/Music` folder, keeping out voice notes and app audio
- **Favorites** — heart any track to build a dedicated favorites list, persisted locally
- **Queue view** — "Up Next" panel that auto-scrolls to the current song and lets you jump to any track

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material Design 3 |
| Concurrency | Kotlin Coroutines, StateFlow |
| Playback | `MediaPlayer`, `MediaSessionCompat`, `MediaStore API` |
| Audio FX | `Equalizer`, `DynamicsProcessing`, `LoudnessEnhancer` |
| Pagination | AndroidX Paging 3 |
| Image | `MediaMetadataRetriever` + custom LRU + disk cache |

---

## 📋 Permissions

| Permission | Reason |
|---|---|
| `READ_MEDIA_AUDIO` (API 33+) | Read local audio files |
| `READ_EXTERNAL_STORAGE` (below API 33) | Legacy equivalent |
| `FOREGROUND_SERVICE` | Background playback |
| `POST_NOTIFICATIONS` (API 33+) | Playback notification |

---

## 📱 Requirements

- **Android 8.0+** (API 26) — minimum
- **Android 9.0+** (API 28) — required for Smart EQ's `DynamicsProcessing`; older devices fall back gracefully

---

## 📖 The Story Behind "Inaho"

> *"Makasih buat covernya, aku suka."*

This app was originally built and named in honor of [Ochinai Inaho](https://www.youtube.com/@%E8%90%BD%E4%B9%83%E3%81%84%E3%81%AA%E3%81%BB), a Japanese VTuber whose vocal cover inspired its creation. It started as a tribute — something made with the quiet hope of being noticed (But at the end not noticed) — and evolved into a passion project and a gift to anyone who loves their local music library.

---

## 👨‍💻 Developer

Developed by **Kanagawa Yamada**

- Email: albert.wesley.dion@gmail.com
- X / Twitter: [@Kanagawa_Yamada](https://x.com/Kanagawa_Yamada)
- GitHub: [@LoggingNewMemory](https://github.com/LoggingNewMemory)
- YouTube: [@KanagawaYamada](https://youtube.com/@KanagawaYamada)

---

## 📄 License

This project is source-available. If you fork or copy it, please keep the credit comment in `MainActivity.kt` intact. That's all I ask.

---

*Untuk Inaho — semoga karirmu terus bersinar.*
