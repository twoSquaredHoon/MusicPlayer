## MusicPlayer

A lightweight CLI music player written in Java, using JavaFX MediaPlayer for audio playback.
Designed with a clean internal architecture so a graphical UI can be added later without rewriting core logic.

### Features

Terminal-based interactive music player

Folder-based music library scanning

Supports common audio formats

Clean separation between playlist, playback engine, and CLI

Cross-platform (macOS, Linux, Windows*)

* Windows support depends on JavaFX media codecs.

### Project Structure
```
MusicPlayerClient/
├─ build.gradle
├─ settings.gradle
├─ gradlew
├─ gradlew.bat
├─ gradle/
└─ src/main/java/player/
   ├─ Main.java          # CLI entry point
   ├─ MainApp.java       # JavaFX GUI entry point
   ├─ Track.java         # Single track abstraction
   ├─ Playlist.java      # Library + navigation logic
   └─ PlayerEngine.java  # JavaFX MediaPlayer wrapper
```
### Architecture Overview

#### Track
Wraps a file path and display name.

#### Playlist
Handles library loading, sorting, indexing, and next/previous navigation.

#### PlayerEngine
Encapsulates JavaFX MediaPlayer, playback state, seeking, and volume control.

#### Main
Implements the command-line interface and delegates all logic to the above classes.

This separation allows the same playback engine and playlist logic to be reused for a future GUI.

Supported Audio Formats

MP3 (.mp3)

AAC / M4A (.aac, .m4a)

WAV (.wav)

Note: Some Apple Music files may be DRM-protected and will not play.

### Requirements

Java 17+ (recommended)

JavaFX (handled automatically via Gradle)

No manual Gradle installation required (uses Gradle Wrapper)

### Build & Run Commands

All commands must be executed from:
```
MusicPlayer/MusicPlayerClient/
```

Build
```
./gradlew installDist
```
Run - CLI
```
./build/install/MusicPlayerClient/bin/MusicPlayerClient "<music-folder>"
```
Run - GUI
```
./gradlew run --args="/path/to/music-folder"
```
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
