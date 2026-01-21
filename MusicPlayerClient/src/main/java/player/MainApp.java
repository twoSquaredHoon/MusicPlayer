package player;

import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.Set;

public class MainApp extends Application {

    private static final Set<String> EXT = Set.of("mp3", "m4a", "aac", "wav");

    // fixed phone size (fullscreen-safe)
    private static final int PHONE_W = 240;
    private static final int PHONE_H = 480;

    private StackPane root;
    private Pane clickBlocker;

    private VBox phone;
    private boolean phoneVisible = false;

    // "Apps"
    private enum Screen { LAUNCHER, MUSIC_LIST, MUSIC_PLAYER }
    private Screen screen = Screen.LAUNCHER;

    // data + playback
    private final Playlist playlist = new Playlist(EXT);
    private final PlayerEngine engine = new PlayerEngine();
    private boolean loop = false;
    private boolean mix = false;
    private final Random rng = new Random();

    // UI elements inside phone
    private final ListView<String> appList = new ListView<>();
    private final ListView<Track> musicList = new ListView<>();

    private VBox launcherScreen;
    private VBox musicListScreen;
    private VBox musicPlayerScreen;

    private ImageView albumArt;
private final Label nowTrack = new Label("");
private final Label statusBar = new Label("Stopped");
private Button pauseBtn;
private Button resumeBtn;

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("MusicPlayer");

        root = new StackPane();
        root.setStyle("-fx-background-color: black;");

        // Create phone (loads music folder once for now)
        phone = createPhone(stage);
        phone.setTranslateY(PHONE_H + 40); // start hidden

        // Click blocker (blocks clicks outside phone when phone is open)
        clickBlocker = new Pane();
        clickBlocker.setPickOnBounds(true);
        clickBlocker.setMouseTransparent(true); // off by default
        clickBlocker.prefWidthProperty().bind(root.widthProperty());
        clickBlocker.prefHeightProperty().bind(root.heightProperty());
        clickBlocker.setOnMousePressed(e -> e.consume());
        clickBlocker.setOnMouseClicked(e -> e.consume());

        root.getChildren().addAll(clickBlocker, phone);

        Scene scene = new Scene(root, 900, 600);
        scene.setCursor(Cursor.DEFAULT);
        attachKeyControls(scene);

        stage.setScene(scene);
        stage.show();

        Platform.runLater(root::requestFocus);
    }

    // ---------------- Phone construction ----------------

    private VBox createPhone(Stage stage) throws Exception {
        VBox box = new VBox(10);
        box.setPrefSize(PHONE_W, PHONE_H);
        box.setMinSize(PHONE_W, PHONE_H);
        box.setMaxSize(PHONE_W, PHONE_H);

        box.setPadding(new Insets(14));
        box.setAlignment(Pos.TOP_CENTER);
        box.setStyle("""
            -fx-background-color: rgba(18,18,18,0.97);
            -fx-background-radius: 28;
            -fx-border-radius: 28;
            -fx-border-color: rgba(255,255,255,0.18);
        """);

        Label title = new Label("999K");
        title.setStyle("""
            -fx-text-fill: white;
            -fx-font-size: 14;
            -fx-font-weight: bold;
        """);

        // Build screens
        launcherScreen = buildLauncherScreen();
        musicListScreen = buildMusicListScreen();
        musicPlayerScreen = buildMusicPlayerScreen();

        // default screen
        showScreen(Screen.LAUNCHER);

        box.getChildren().addAll(title, launcherScreen, musicListScreen, musicPlayerScreen);

        // anchor phone bottom-left
        StackPane.setAlignment(box, Pos.BOTTOM_LEFT);
        StackPane.setMargin(box, new Insets(16));

        // allow clicks inside phone (and prevent passing through)
        box.setOnMousePressed(e -> e.consume());

        // Load music folder once (for now)
        Path folder = getFolderFromArgsOrPrompt(stage);
        if (folder != null && Files.isDirectory(folder)) {
            playlist.loadFromFolder(folder);
            musicList.getItems().setAll(playlist.all());
        }

        // Auto-advance (loop/mix/next)
        engine.setOnEnd(() -> {
            Track next = pickNextTrackOnEnd();
            if (next != null) engine.play(next);
        });

        return box;
    }

    private VBox buildLauncherScreen() {
        VBox v = new VBox(8);
        v.setAlignment(Pos.TOP_CENTER);

        Label header = new Label("Apps");
        header.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12;");

        // Add apps here (Music is the one that works right now)
        appList.getItems().setAll(
                "Music",
                "Messages",
                "Settings",
                "Notes",
                "Map"
        );

        appList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item);
            }
        });

        VBox.setVgrow(appList, Priority.ALWAYS);
        v.getChildren().addAll(header, appList);
        return v;
    }

    private VBox buildMusicListScreen() {
        VBox v = new VBox(8);
        v.setAlignment(Pos.TOP_CENTER);

        Label header = new Label("Music");
        header.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12;");

        musicList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.displayName());
            }
        });

        VBox.setVgrow(musicList, Priority.ALWAYS);
        v.getChildren().addAll(header, musicList);
        return v;
    }

    private VBox buildMusicPlayerScreen() {
    VBox v = new VBox(10);
    v.setAlignment(Pos.TOP_CENTER);
    v.setPadding(new Insets(10, 0, 0, 0));

    // Album image (top-middle)
    albumArt = new ImageView(loadOptionalImage("/sprites/album.png"));
    albumArt.setFitWidth(140);
    albumArt.setFitHeight(140);
    albumArt.setPreserveRatio(true);
    albumArt.setSmooth(false);

    StackPane artWrap = new StackPane(albumArt);
    artWrap.setPrefSize(140, 140);
    artWrap.setMaxSize(140, 140);
    artWrap.setStyle("""
        -fx-background-color: rgba(255,255,255,0.08);
        -fx-background-radius: 12;
        -fx-border-color: rgba(255,255,255,0.12);
        -fx-border-radius: 12;
    """);

    // Track label (under art)
    nowTrack.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
    nowTrack.setMaxWidth(PHONE_W - 28);
    nowTrack.setAlignment(Pos.CENTER);

    // Status bar (under track)
    statusBar.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 11;");

    // Controls row
    Button prev = new Button("Prev");
    pauseBtn = new Button("Pause");
    resumeBtn = new Button("Resume");
    Button stop = new Button("Stop");
    Button next = new Button("Next");

    prev.setOnAction(e -> {
        Track t = playlist.prev();
        if (t != null) {
            engine.play(t);
            musicList.getSelectionModel().select(playlist.index());
            nowTrack.setText(t.displayName());
            statusBar.setText("Playing");
        }
    });

    next.setOnAction(e -> {
        Track t = playlist.next();
        if (t != null) {
            engine.play(t);
            musicList.getSelectionModel().select(playlist.index());
            nowTrack.setText(t.displayName());
            statusBar.setText("Playing");
        }
    });

    pauseBtn.setOnAction(e -> {
        engine.pause();
        statusBar.setText("Paused");
    });

    resumeBtn.setOnAction(e -> {
        engine.resume();
        statusBar.setText("Playing");
    });

    stop.setOnAction(e -> {
        engine.stop();
        statusBar.setText("Stopped");
    });

    HBox row1 = new HBox(6, prev, pauseBtn, resumeBtn, stop, next);
    row1.setAlignment(Pos.CENTER);

    // Loop + Mix row
    ToggleButton loopBtn = new ToggleButton("Loop");
    ToggleButton mixBtn = new ToggleButton("Mix");
    loopBtn.setOnAction(e -> loop = loopBtn.isSelected());
    mixBtn.setOnAction(e -> mix = mixBtn.isSelected());

    HBox row2 = new HBox(8, loopBtn, mixBtn);
    row2.setAlignment(Pos.CENTER);

    v.getChildren().addAll(artWrap, nowTrack, statusBar, row1, row2);
    return v;
}

private javafx.scene.image.Image loadOptionalImage(String resourcePath) {
    try (var in = getClass().getResourceAsStream(resourcePath)) {
        if (in == null) return null;
        return new javafx.scene.image.Image(in);
    } catch (Exception ignored) {
        return null;
    }
}


    // ---------------- Keyboard control logic ----------------

    private void attachKeyControls(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {

            // UP opens phone (only when closed)
            if (!phoneVisible && e.getCode() == KeyCode.UP) {
                showPhone();
                e.consume();
                return;
            }

            // If phone not open, ignore everything else
            if (!phoneVisible) return;

            // ESC behavior: go back one level; if already launcher -> hide phone
            if (e.getCode() == KeyCode.ESCAPE) {
                if (screen == Screen.MUSIC_PLAYER) {
                    showScreen(Screen.MUSIC_LIST);
                    Platform.runLater(musicList::requestFocus);
                } else if (screen == Screen.MUSIC_LIST) {
                    showScreen(Screen.LAUNCHER);
                    Platform.runLater(appList::requestFocus);
                } else { // LAUNCHER
                    hidePhone();
                }
                e.consume();
                return;
            }

            // Arrow keys should navigate whichever list is active
            if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) {
                if (screen == Screen.LAUNCHER) {
                    Platform.runLater(appList::requestFocus);
                    return; // let ListView handle selection
                }
                if (screen == Screen.MUSIC_LIST) {
                    Platform.runLater(musicList::requestFocus);
                    return;
                }
                // player screen: block arrow keys (for now)
                e.consume();
                return;
            }

            // ENTER behavior depends on screen
            if (e.getCode() == KeyCode.ENTER) {
                if (screen == Screen.LAUNCHER) {
                    openSelectedApp();
                    e.consume();
                    return;
                }
                if (screen == Screen.MUSIC_LIST) {
                    playSelectedTrack();
                    showScreen(Screen.MUSIC_PLAYER);
                    e.consume();
                    return;
                }
                // player screen: ignore ENTER for now
                e.consume();
                return;
            }

            // Phone open: block everything else for now (tight control)
            e.consume();
        });
    }

    private void openSelectedApp() {
        int sel = appList.getSelectionModel().getSelectedIndex();
        if (sel < 0) sel = 0;

        String app = appList.getItems().get(sel);
        if ("Music".equals(app)) {
            showScreen(Screen.MUSIC_LIST);
            Platform.runLater(musicList::requestFocus);
        } else {
            // placeholder: stay on launcher for now
            // (later: show a screen per app)
        }
    }

    private void playSelectedTrack() {
    if (playlist.isEmpty()) return;

    int sel = musicList.getSelectionModel().getSelectedIndex();
    if (sel < 0) sel = 0;

    Track t = playlist.setIndex(sel);
    if (t == null) return;

    engine.play(t);

    // update player screen UI
    nowTrack.setText(t.displayName());
    statusBar.setText("Playing");

    // go to player screen
    showScreen(Screen.MUSIC_PLAYER);
}


    // ---------------- Screen switching ----------------

    private void showScreen(Screen s) {
        screen = s;

        setScreenVisible(launcherScreen, s == Screen.LAUNCHER);
        setScreenVisible(musicListScreen, s == Screen.MUSIC_LIST);
        setScreenVisible(musicPlayerScreen, s == Screen.MUSIC_PLAYER);
    }

    private static void setScreenVisible(Region node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    // ---------------- Phone open/close + click blocking ----------------

    private void showPhone() {
        if (phoneVisible) return;
        phoneVisible = true;

        clickBlocker.setMouseTransparent(false); // block clicks outside phone

        // Always open to launcher
        showScreen(Screen.LAUNCHER);

        TranslateTransition t = new TranslateTransition(Duration.millis(180), phone);
        t.setToY(0);
        t.play();

        Platform.runLater(() -> {
            appList.getSelectionModel().select(0);
            appList.requestFocus();
        });
    }

    private void hidePhone() {
        if (!phoneVisible) return;
        phoneVisible = false;

        clickBlocker.setMouseTransparent(true);

        TranslateTransition t = new TranslateTransition(Duration.millis(180), phone);
        t.setToY(PHONE_H + 40);
        t.play();

        root.requestFocus();
    }

    // ---------------- Playback logic ----------------

    private Track pickNextTrackOnEnd() {
        if (playlist.isEmpty()) return null;

        if (loop) return playlist.current();

        if (mix) {
            int n = playlist.size();
            if (n <= 1) return playlist.current();
            int cur = playlist.index();
            int r;
            do { r = rng.nextInt(n); } while (r == cur);
            return playlist.setIndex(r);
        }

        return playlist.next();
    }

    // ---------------- Folder selection ----------------

    private Path getFolderFromArgsOrPrompt(Stage stage) {
        var args = getParameters().getRaw();
        if (!args.isEmpty()) return Paths.get(args.get(0));

        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose Music Folder");
        File chosen = dc.showDialog(stage);
        return (chosen == null) ? null : chosen.toPath();
    }

    @Override
    public void stop() {
        engine.shutdown(); // stops music only when app exits
    }

    public static void main(String[] args) {
        launch(args);
    }
}
