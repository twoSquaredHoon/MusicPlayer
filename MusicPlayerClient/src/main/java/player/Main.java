package player;

import javafx.application.Platform;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class Main {
    private static final Set<String> EXT = Set.of("mp3", "m4a", "aac", "wav");
    private static final Scanner IN = new Scanner(System.in);

    private static final Playlist playlist = new Playlist(EXT);
    private static final PlayerEngine engine = new PlayerEngine();

    public static void main(String[] args) throws Exception {
        // Start JavaFX runtime (no window needed)
        CountDownLatch fx = new CountDownLatch(1);
        Platform.startup(fx::countDown);
        fx.await();

        Path folder = (args.length > 0) ? Paths.get(args[0]) : null;
        if (folder == null) {
            System.out.print("Music folder path: ");
            folder = Paths.get(IN.nextLine().trim());
        }

        playlist.loadFromFolder(folder);
        System.out.println("Loaded " + playlist.size() + " tracks.");
        help();

        // auto-advance when song ends
        engine.setOnEnd(() -> {
            Track t = playlist.next();
            engine.play(t);
            if (t != null) System.out.println("Playing: [" + playlist.index() + "] " + t.displayName());
        });

        commandLoop();

        engine.shutdown();
    }

    private static void commandLoop() {
        while (true) {
            System.out.print("> ");
            if (!IN.hasNextLine()) return;
            String line = IN.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase(Locale.ROOT);

            try {
                switch (cmd) {
                    case "help" -> help();
                    case "list" -> playlist.list(parts.length > 1 ? Integer.parseInt(parts[1]) : 30);
                    case "play" -> playIndex(Integer.parseInt(parts[1]));
                    case "pause" -> { engine.pause(); System.out.println("Paused."); }
                    case "resume" -> { engine.resume(); System.out.println("Resumed."); }
                    case "stop" -> { engine.stop(); System.out.println("Stopped."); }
                    case "next" -> next();
                    case "prev" -> prev();
                    case "seek" -> { engine.seekSeconds(Integer.parseInt(parts[1])); System.out.println("Seek -> " + parts[1] + "s"); }
                    case "vol" -> { engine.setVolume(Double.parseDouble(parts[1])); System.out.println("Volume set."); }
                    case "now" -> engine.printNowPlaying("Now: [" + playlist.index() + "] ", playlist.current());
                    case "quit", "exit" -> { return; }
                    default -> System.out.println("Unknown command. Type: help");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void playIndex(int i) {
        if (playlist.isEmpty()) { System.out.println("No tracks loaded."); return; }
        Track t = playlist.setIndex(i);
        engine.play(t);
        System.out.println("Playing: [" + playlist.index() + "] " + t.displayName());
    }

    private static void next() {
        if (playlist.isEmpty()) return;
        Track t = playlist.next();
        engine.play(t);
        if (t != null) System.out.println("Playing: [" + playlist.index() + "] " + t.displayName());
    }

    private static void prev() {
        if (playlist.isEmpty()) return;
        Track t = playlist.prev();
        engine.play(t);
        if (t != null) System.out.println("Playing: [" + playlist.index() + "] " + t.displayName());
    }

    private static void help() {
        System.out.println("""
Commands:
  help
  list [n]         - show first n tracks (default 30)
  play <i>         - play track index i
  pause | resume | stop
  next | prev
  seek <seconds>   - jump to time
  vol <0..1>       - set volume
  now              - show current track/time
  quit
""");
    }
}
