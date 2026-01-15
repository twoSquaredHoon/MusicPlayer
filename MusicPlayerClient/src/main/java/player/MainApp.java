package player;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class MainApp extends Application {

    private static final Set<String> EXT = Set.of("mp3", "m4a", "aac", "wav");

    private final Playlist playlist = new Playlist(EXT);
    private final PlayerEngine engine = new PlayerEngine();

    private final ListView<Track> listView = new ListView<>();
    private final Label nowPlaying = new Label("Now: (nothing)");

    private boolean paused = false;

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("MusicPlayer");

        // 1) Load folder (arg0 or DirectoryChooser)
        Path folder = getFolderFromArgsOrPrompt(stage);
        if (folder == null) {
            // user cancelled folder picking
            Platform.exit();
            return;
        }

        playlist.loadFromFolder(folder);
        listView.getItems().setAll(playlist.all());
        nowPlaying.setText("Loaded " + playlist.size() + " tracks from: " + folder.getFileName());

        // 2) Auto-advance when track ends
        engine.setOnEnd(() -> {
            Track t = playlist.next();
            if (t != null) {
                engine.play(t);
                Platform.runLater(() -> {
                    listView.getSelectionModel().select(playlist.index());
                    nowPlaying.setText("Now: " + t.displayName());
                });
            }
        });

        // 3) UI Controls
        Button playBtn = new Button("Play");
        Button pauseBtn = new Button("Pause");
        Button resumeBtn = new Button("Resume");
        Button stopBtn = new Button("Stop");
        Button prevBtn = new Button("Prev");
        Button nextBtn = new Button("Next");

        Slider vol = new Slider(0.0, 1.0, 0.8);
        vol.setShowTickLabels(true);
        vol.setPrefWidth(160);
        engine.setVolume(vol.getValue());
        vol.valueProperty().addListener((obs, oldV, newV) -> engine.setVolume(newV.doubleValue()));

        // 4) Button actions
        playBtn.setOnAction(e -> playSelectedOrCurrent());
        pauseBtn.setOnAction(e -> { engine.pause(); paused = true; });
        resumeBtn.setOnAction(e -> { engine.resume(); paused = false; });
        stopBtn.setOnAction(e -> { engine.stop(); paused = false; });

        prevBtn.setOnAction(e -> {
            Track t = playlist.prev();
            if (t != null) {
                engine.play(t);
                listView.getSelectionModel().select(playlist.index());
                nowPlaying.setText("Now: " + t.displayName());
            }
        });

        nextBtn.setOnAction(e -> {
            Track t = playlist.next();
            if (t != null) {
                engine.play(t);
                listView.getSelectionModel().select(playlist.index());
                nowPlaying.setText("Now: " + t.displayName());
            }
        });

        // 5) Double-click track to play
        listView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                playSelectedOrCurrent();
            }
        });

        // Layout
        HBox controls = new HBox(8, prevBtn, playBtn, pauseBtn, resumeBtn, stopBtn, nextBtn,
                new Separator(), new Label("Vol"), vol);
        controls.setPadding(new Insets(10));

        VBox root = new VBox(8, nowPlaying, listView, controls);
        root.setPadding(new Insets(10));
        VBox.setVgrow(listView, Priority.ALWAYS);

        stage.setScene(new Scene(root, 720, 480));
        stage.show();
    }

    private void playSelectedOrCurrent() {
        if (playlist.isEmpty()) return;

        int sel = listView.getSelectionModel().getSelectedIndex();
        Track t = (sel >= 0) ? playlist.setIndex(sel) : playlist.current();
        if (t == null) return;

        engine.play(t);
        listView.getSelectionModel().select(playlist.index());
        nowPlaying.setText("Now: " + t.displayName() + (paused ? " (was paused)" : ""));
        paused = false;
    }

    private Path getFolderFromArgsOrPrompt(Stage stage) {
        // Try args[0]
        var args = getParameters().getRaw();
        if (!args.isEmpty()) {
            return Paths.get(args.get(0));
        }

        // Prompt DirectoryChooser
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose Music Folder");
        File chosen = dc.showDialog(stage);
        return (chosen == null) ? null : chosen.toPath();
    }

    @Override
    public void stop() {
        // Ensure MediaPlayer is disposed and JavaFX exits cleanly
        engine.shutdown();
    }
}
