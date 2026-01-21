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
Run — GUI
```
./gradlew run --args="/path/to/music-folder"
```
### Features
#### Core

Folder-based music library scanning

Supports common audio formats (MP3, AAC/M4A, WAV)

Clean separation between playlist, playback engine, and UI

Cross-platform (macOS, Linux, Windows*)

* Windows support depends on JavaFX media codecs.

#### CLI

Interactive terminal commands

Full playback control (play, pause, seek, volume, next/prev)

Lightweight and script-friendly

#### GUI (JavaFX)

Phone-style interface

Album art display

Keyboard navigation (arrow keys + enter)

Playback controls:

Play / Pause / Stop

Next / Previous

Mix (shuffle)

Loop modes:

Off

Once (replays the track one extra time, then turns off automatically)

Repeat (loops indefinitely)

Visual feedback for playback state

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
### Architecture Overview
#### Track

Represents a single audio file.

Stores file path

Provides display-friendly name

#### Playlist

Manages the music library:

Loads tracks from a folder

Maintains current index

Handles next / previous / shuffle logic

#### PlayerEngine

Encapsulates JavaFX MediaPlayer:

Play, pause, resume, stop

Seek and volume control

End-of-track callbacks

#### Main (CLI)

Implements a command-line interface that delegates all logic to:

Playlist

PlayerEngine

#### MainApp (GUI)

JavaFX application that:

Reuses the same Playlist and PlayerEngine

Implements UI state, keyboard navigation, and loop/mix behavior

This separation allows features to be added or changed in one interface without breaking the other.

Supported Audio Formats

MP3 (.mp3)

AAC / M4A (.aac, .m4a)

WAV (.wav)

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
