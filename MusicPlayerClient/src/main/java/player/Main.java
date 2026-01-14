package player;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

public class Main {
    private static final Set<String> EXT = Set.of("mp3", "m4a", "aac", "wav"); // start simple
    private static final Scanner IN = new Scanner(System.in);

    private static List<Path> tracks = new ArrayList<>();
    private static int idx = -1;

    private static MediaPlayer player = null;
    private static boolean paused = false;

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

        loadLibrary(folder);
        System.out.println("Loaded " + tracks.size() + " tracks.");
        help();

        commandLoop();

        // Clean exit
        runFx(() -> {
            if (player != null) player.dispose();
            Platform.exit();
        });
    }

    private static void commandLoop() {
        while (true) {
            System.out.print("> ");
            if (!IN.hasNextLine()) return;   // exit cleanly if no stdin
            String line = IN.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase(Locale.ROOT);

            try {
                switch (cmd) {
                    case "help" -> help();
                    case "list" -> list(parts.length > 1 ? Integer.parseInt(parts[1]) : 30);
                    case "play" -> playIndex(Integer.parseInt(parts[1]));
                    case "pause" -> pause();
                    case "resume" -> resume();
                    case "stop" -> stop();
                    case "next" -> next();
                    case "prev" -> prev();
                    case "seek" -> seekSeconds(Integer.parseInt(parts[1]));
                    case "vol" -> setVolume(Double.parseDouble(parts[1]));
                    case "now" -> now();
                    case "quit", "exit" -> { return; }
                    default -> System.out.println("Unknown command. Type: help");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
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

    private static void loadLibrary(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) throw new IllegalArgumentException("Not a folder: " + folder);

        try (Stream<Path> s = Files.walk(folder)) {
            tracks = s.filter(Files::isRegularFile)
                    .filter(p -> EXT.contains(ext(p)))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
        idx = tracks.isEmpty() ? -1 : 0;
    }

    private static String ext(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static void list(int n) {
        int limit = Math.min(n, tracks.size());
        for (int i = 0; i < limit; i++) {
            System.out.printf("%4d  %s%n", i, tracks.get(i).getFileName());
        }
        if (tracks.size() > limit) System.out.println("... (" + (tracks.size() - limit) + " more)");
    }

    private static void playIndex(int i) {
        if (tracks.isEmpty()) { System.out.println("No tracks loaded."); return; }
        if (i < 0 || i >= tracks.size()) throw new IllegalArgumentException("Index out of range.");

        idx = i;
        Path path = tracks.get(idx);

        runFx(() -> {
            if (player != null) {
                player.stop();
                player.dispose();
            }

            Media media = new Media(path.toUri().toString());
            player = new MediaPlayer(media);

            player.setOnEndOfMedia(Main::next);
            player.setOnError(() -> System.out.println("Playback error: " + player.getError()));

            paused = false;
            player.play();
        });

        System.out.println("Playing: [" + idx + "] " + path.getFileName());
    }

    private static void pause() {
        runFx(() -> {
            if (player == null) return;
            player.pause();
            paused = true;
        });
        System.out.println("Paused.");
    }

    private static void resume() {
        runFx(() -> {
            if (player == null) return;
            player.play();
            paused = false;
        });
        System.out.println("Resumed.");
    }

    private static void stop() {
        runFx(() -> {
            if (player == null) return;
            player.stop();
            paused = false;
        });
        System.out.println("Stopped.");
    }

    private static void next() {
        if (tracks.isEmpty()) return;
        int ni = (idx + 1) % tracks.size();
        playIndex(ni);
    }

    private static void prev() {
        if (tracks.isEmpty()) return;
        int pi = (idx - 1 + tracks.size()) % tracks.size();
        playIndex(pi);
    }

    private static void seekSeconds(int seconds) {
        runFx(() -> {
            if (player == null) return;
            player.seek(Duration.seconds(Math.max(0, seconds)));
        });
        System.out.println("Seek -> " + seconds + "s");
    }

    private static void setVolume(double v) {
        double vol = Math.max(0.0, Math.min(1.0, v));
        runFx(() -> {
            if (player == null) return;
            player.setVolume(vol);
        });
        System.out.println("Volume -> " + vol);
    }

    private static void now() {
        runFx(() -> {
            if (player == null || idx < 0) {
                System.out.println("Nothing playing.");
                return;
            }
            Duration t = player.getCurrentTime();
            Duration d = player.getTotalDuration();
            System.out.println("Now: [" + idx + "] " + tracks.get(idx).getFileName()
                    + "  " + fmt(t) + " / " + fmt(d)
                    + (paused ? " (paused)" : ""));
        });
    }

    private static String fmt(Duration dur) {
        if (dur == null || dur.isUnknown()) return "--:--";
        int sec = (int) Math.floor(dur.toSeconds());
        int m = sec / 60;
        int s = sec % 60;
        return String.format("%d:%02d", m, s);
    }

    private static void runFx(Runnable r) {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { r.run(); }
            finally { latch.countDown(); }
        });
        try { latch.await(); } catch (InterruptedException ignored) {}
    }
}
