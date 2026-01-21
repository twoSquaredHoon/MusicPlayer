## MusicPlayer

A lightweight music player written in Java, featuring both a terminal-based CLI and a JavaFX GUI.
Designed with a clean internal architecture so playback logic is shared across interfaces without duplication.

The GUI simulates a compact “phone-style” music player with keyboard and button controls.

### Build & Run

All commands must be executed from:
```
MusicPlayer/MusicPlayerClient/
```
Build
```
./gradlew installDist
```
Run — CLI
```
./build/install/MusicPlayerClient/bin/MusicPlayerClient "<music-folder>"
```
Run — GUI (Recommended)
```
./gradlew run --args="/path/to/music-folder"
```

### Project Structure
```
MusicPlayerClient/
├─ build.gradle
├─ settings.gradle
├─ gradlew
├─ gradlew.bat
├─ gradle/
└─ src/
   └─ main/
      ├─ java/
      │  └─ player/
      │     ├─ Main.java          # CLI entry point
      │     ├─ MainApp.java       # JavaFX GUI entry point
      │     ├─ DancerSprite.java  # Sprite-sheet dancer animation (play = dance, stop = idle)
      │     ├─ Track.java         # Single track abstraction
      │     ├─ Playlist.java      # Library + navigation logic
      │     └─ PlayerEngine.java  # JavaFX MediaPlayer wrapper
      └─ resources/
         └─ sprites/
            ├─ dancer.png         # Sprite sheet asset
            └─ album.png          # Default album art

```

### Features
#### Core
Folder-based music library scanning

#### CLI
Interactive terminal commands
Full playback control (play, pause, seek, volume, next/prev)
Lightweight and script-friendly

#### GUI (JavaFX)
Can be fully navigated only using keyboard
Arrow Keys: toogle
Enter: Enter
ESC: Escape

### Supported Audio Formats
- MP3 (.mp3)
- AAC / M4A (.aac, .m4a)
- WAV (.wav)
Note: Some Apple Music files may be DRM-protected and will not play.

### Requirements
Java 17+ (recommended)
JavaFX (handled automatically via Gradle)
No manual Gradle installation required (uses Gradle Wrapper)

### CLI Commands
```
help
list [n]         - show first n tracks (default 30)
play <i>         - play track index i
pause | resume | stop
next | prev
seek <seconds>   - jump to time
vol <0..1>       - set volume
now              - show current track/time
quit
```
