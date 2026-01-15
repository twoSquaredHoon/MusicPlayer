package player;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.util.concurrent.CountDownLatch;

public class PlayerEngine {
    private MediaPlayer player;
    private boolean paused = false;

    // Called by MediaPlayer when track ends
    private Runnable onEnd = null;

    public void setOnEnd(Runnable onEnd) {
        this.onEnd = onEnd;
    }

    public void play(Track track) {
        if (track == null) return;

        fx(() -> {
            disposeCurrent();

            Media media = new Media(track.path().toUri().toString());
            player = new MediaPlayer(media);

            player.setOnEndOfMedia(() -> {
                if (onEnd != null) onEnd.run();
            });
            player.setOnError(() -> System.out.println("Playback error: " + player.getError()));

            paused = false;
            player.play();
        });
    }

    public void pause() {
        fx(() -> {
            if (player == null) return;
            player.pause();
            paused = true;
        });
    }

    public void resume() {
        fx(() -> {
            if (player == null) return;
            player.play();
            paused = false;
        });
    }

    public void stop() {
        fx(() -> {
            if (player == null) return;
            player.stop();
            paused = false;
        });
    }

    public void seekSeconds(int seconds) {
        fx(() -> {
            if (player == null) return;
            player.seek(Duration.seconds(Math.max(0, seconds)));
        });
    }

    public void setVolume(double v) {
        double vol = Math.max(0.0, Math.min(1.0, v));
        fx(() -> {
            if (player == null) return;
            player.setVolume(vol);
        });
    }

    public void printNowPlaying(String labelPrefix, Track track) {
        fx(() -> {
            if (player == null || track == null) {
                System.out.println("Nothing playing.");
                return;
            }
            Duration t = player.getCurrentTime();
            Duration d = player.getTotalDuration();
            System.out.println(labelPrefix + track.displayName()
                    + "  " + fmt(t) + " / " + fmt(d)
                    + (paused ? " (paused)" : ""));
        });
    }

    public void shutdown() {
        fx(() -> {
            disposeCurrent();
            Platform.exit();
        });
    }

    private void disposeCurrent() {
        if (player != null) {
            player.stop();
            player.dispose();
            player = null;
        }
    }

    private static String fmt(Duration dur) {
        if (dur == null || dur.isUnknown()) return "--:--";
        int sec = (int) Math.floor(dur.toSeconds());
        int m = sec / 60;
        int s = sec % 60;
        return String.format("%d:%02d", m, s);
    }

    // Run something on JavaFX thread safely (no deadlock if already on FX thread)
    private static void fx(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { r.run(); }
            finally { latch.countDown(); }
        });
        try { latch.await(); } catch (InterruptedException ignored) {}
    }
}
