package player;

import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
// add to imports
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
import java.util.List;
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
    private enum Screen {
        LAUNCHER, MUSIC_LIST, MUSIC_PLAYER
    }

    private Screen screen = Screen.LAUNCHER;

    // data + playback
    private final Playlist playlist = new Playlist(EXT);
    private final PlayerEngine engine = new PlayerEngine();

    private enum LoopMode {
        OFF, ONCE, REPEAT
    }

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

    private ToggleButton playPauseBtn;
    private boolean isPaused = false;

    private final java.util.List<ButtonBase> playerBtns = new java.util.ArrayList<>();
    private int playerFocus = 0;

    private boolean isPlaying = false;

    // launcher grid
    private final String[] apps = { "Music", "Messages", "Settings", "Notes", "Map", "Camera", "Clock" };
    private final java.util.List<Button> appTiles = new java.util.ArrayList<>();
    private int appFocus = 0;

    private static final int APP_COLS = 3; // 3 columns grid

    private Button loopBtn; // field

    private LoopMode loopMode = LoopMode.OFF;

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

        // Auto-advance (3-stage loop + mix + normal next) - MUST update UI on FX thread
        engine.setOnEnd(() -> Platform.runLater(() -> {
            Track next;

            // 3-stage loop:
            // ONCE -> replay current once, then turn OFF
            // REPEAT-> replay forever
            // OFF -> go to next (respecting Mix)
            if (loopMode == LoopMode.ONCE || loopMode == LoopMode.REPEAT) {
                next = playlist.current(); // replay same track

                if (loopMode == LoopMode.ONCE) {
                    loopMode = LoopMode.OFF;
                    if (loopBtn != null)
                        updateLoopButton(loopBtn); // loopBtn should be a field
                }
            } else {
                next = pickNextTrackOnEnd(); // should respect Mix if you want
            }

            if (next != null) {
                engine.play(next);
                isPlaying = true;
                isPaused = false;
                if (playPauseBtn != null)
                    playPauseBtn.setText("Pause");
                if (statusBar != null)
                    statusBar.setText("Playing");
                if (nowTrack != null)
                    nowTrack.setText(next.displayName());
                if (musicList != null)
                    musicList.getSelectionModel().select(playlist.index());
            } else {
                engine.stop();
                isPlaying = false;
                isPaused = false;
                if (playPauseBtn != null)
                    playPauseBtn.setText("Play");
                if (statusBar != null)
                    statusBar.setText("Stopped");
            }
        }));

        return box;
    }

    private VBox buildLauncherScreen() {
        VBox v = new VBox(10);
        v.setAlignment(Pos.TOP_CENTER);

        Label header = new Label("Apps");
        header.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.TOP_CENTER);

        appTiles.clear();

        for (int i = 0; i < apps.length; i++) {
            String name = apps[i];

            Button tile = new Button(name);
            tile.setPrefSize(64, 64); // square
            tile.setMinSize(64, 64);
            tile.setMaxSize(64, 64);
            tile.setFocusTraversable(true);

            tile.setStyle("""
                        -fx-background-color: rgba(255,255,255,0.10);
                        -fx-text-fill: white;
                        -fx-font-size: 11;
                        -fx-background-radius: 12;
                    """);

            final int idx = i;
            tile.setOnAction(e -> openAppByIndex(idx)); // mouse click opens too

            appTiles.add(tile);

            int r = i / APP_COLS;
            int c = i % APP_COLS;
            grid.add(tile, c, r);
        }

        VBox.setVgrow(grid, Priority.ALWAYS);
        v.getChildren().addAll(header, grid);
        return v;
    }

    private void focusAppTile(int idx) {
        if (appTiles.isEmpty())
            return;
        appFocus = Math.max(0, Math.min(appTiles.size() - 1, idx));
        Platform.runLater(() -> appTiles.get(appFocus).requestFocus());
    }

    private void moveAppFocus(KeyCode code) {
        int i = appFocus;
        int rows = (int) Math.ceil(appTiles.size() / (double) APP_COLS);

        int r = i / APP_COLS;
        int c = i % APP_COLS;

        if (code == KeyCode.LEFT)
            c--;
        if (code == KeyCode.RIGHT)
            c++;
        if (code == KeyCode.UP)
            r--;
        if (code == KeyCode.DOWN)
            r++;

        c = Math.max(0, Math.min(APP_COLS - 1, c));
        r = Math.max(0, Math.min(rows - 1, r));

        int ni = r * APP_COLS + c;
        if (ni >= appTiles.size()) {
            // clamp to last item in that row
            ni = appTiles.size() - 1;
        }
        focusAppTile(ni);
    }

    private void openAppByIndex(int idx) {
        String app = apps[idx];
        if ("Music".equals(app)) {
            showScreen(Screen.MUSIC_LIST);
            Platform.runLater(musicList::requestFocus);
        }
        // others: do nothing for now
    }

    private VBox buildMusicListScreen() {
        VBox v = new VBox(8);
        v.setAlignment(Pos.TOP_CENTER);

        Label header = new Label("Music");
        header.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12;");

        musicList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Track item, boolean empty) {
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
        playPauseBtn = new ToggleButton("Play"); // <- start not playing
        Button stop = new Button("Stop");
        Button next = new Button("Next");

        prev.setOnAction(e -> {
            Track t = playlist.prev();
            if (t != null) {
                engine.play(t);
                isPlaying = true;
                isPaused = false;
                playPauseBtn.setText("Pause");

                musicList.getSelectionModel().select(playlist.index());
                nowTrack.setText(t.displayName());
                statusBar.setText("Playing");
            }
        });

        next.setOnAction(e -> {
            Track t = pickNextManual(); // respects Mix
            if (t != null) {
                engine.play(t);
                isPlaying = true;
                isPaused = false;
                playPauseBtn.setText("Pause");

                musicList.getSelectionModel().select(playlist.index());
                nowTrack.setText(t.displayName());
                statusBar.setText("Playing");
            }
        });

        playPauseBtn.setOnAction(e -> {
            if (!isPlaying) {
                playSelectedTrack(); // must call engine.play(track) inside AND set playlist index
                isPlaying = true;
                isPaused = false;
                playPauseBtn.setText("Pause");
                statusBar.setText("Playing");
                return;
            }

            if (!isPaused) {
                engine.pause();
                isPaused = true;
                playPauseBtn.setText("Play");
                statusBar.setText("Paused");
            } else {
                engine.resume();
                isPaused = false;
                playPauseBtn.setText("Pause");
                statusBar.setText("Playing");
            }
        });

        stop.setOnAction(e -> {
            engine.stop();
            isPlaying = false;
            isPaused = false;
            statusBar.setText("Stopped");
            playPauseBtn.setText("Play");
        });

        HBox row1 = new HBox(6, prev, playPauseBtn, stop, next);
        row1.setAlignment(Pos.CENTER);

        // Loop + Mix row
        Button loopBtn = new Button(); // <- 3-state loop button
        updateLoopButton(loopBtn);

        ToggleButton mixBtn = new ToggleButton("Mix");

        loopBtn.setOnAction(e -> {
            loopMode = switch (loopMode) {
                case OFF -> LoopMode.ONCE;
                case ONCE -> LoopMode.REPEAT;
                case REPEAT -> LoopMode.OFF;
            };
            updateLoopButton(loopBtn);
        });

        mixBtn.setOnAction(e -> {
            mix = mixBtn.isSelected();
            mixBtn.setStyle(mix ? "-fx-background-color: #ff9800; -fx-text-fill: black;" : "");
        });

        playerBtns.clear();
        playerBtns.addAll(List.of(prev, playPauseBtn, stop, next, loopBtn, mixBtn));

        HBox row2 = new HBox(8, loopBtn, mixBtn);
        row2.setAlignment(Pos.CENTER);

        v.getChildren().addAll(artWrap, nowTrack, statusBar, row1, row2);
        return v;
    }

    private javafx.scene.image.Image loadOptionalImage(String resourcePath) {
        try (var in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null)
                return null;
            return new javafx.scene.image.Image(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Track pickNextManual() {
        if (playlist.isEmpty())
            return null;

        if (mix) {
            int n = playlist.size();
            if (n <= 1)
                return playlist.current();
            int cur = playlist.index();
            int r;
            do {
                r = rng.nextInt(n);
            } while (r == cur);
            return playlist.setIndex(r);
        }

        return playlist.next();
    }

    // Helper (optional but recommended): keep loop button visuals in one place
    private void updateLoopButton(Button loopBtn) {
        switch (loopMode) {
            case OFF -> {
                loopBtn.setText("Loop: Off");
                loopBtn.setStyle("");
            }
            case ONCE -> {
                loopBtn.setText("Loop: Once");
                loopBtn.setStyle("-fx-background-color: #90caf9; -fx-text-fill: black;");
            }
            case REPEAT -> {
                loopBtn.setText("Loop: On");
                loopBtn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: black;");
            }
        }
    }

    // Make sure your Playlist has a "current()" method.
    // If it doesn't, add this in Playlist:
    // public Track current() { return (tracks.isEmpty() ? null :
    // tracks.get(index)); }
    // (adjust names to your Playlist class)

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
            if (!phoneVisible)
                return;

            // ESC behavior: go back one level; if already launcher -> hide phone
            if (e.getCode() == KeyCode.ESCAPE) {
                if (screen == Screen.MUSIC_PLAYER) {
                    showScreen(Screen.MUSIC_LIST);
                    Platform.runLater(musicList::requestFocus);
                } else if (screen == Screen.MUSIC_LIST) {
                    showScreen(Screen.LAUNCHER);
                    Platform.runLater(() -> focusAppTile(appFocus)); // grid focus
                } else { // LAUNCHER
                    hidePhone();
                }
                e.consume();
                return;
            }

            // -------- MUSIC PLAYER: keyboard controls for buttons --------
            if (screen == Screen.MUSIC_PLAYER) {
                if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT
                        || e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) {
                    movePlayerFocus(e.getCode());
                    e.consume();
                    return;
                }

                if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                    fireFocusedPlayerBtn();
                    e.consume();
                    return;
                }

                e.consume();
                return;
            }
            // ------------------------------------------------------------

            // -------- LAUNCHER (GRID): arrow keys move tile selection ----
            if (screen == Screen.LAUNCHER) {
                if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT
                        || e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) {
                    moveAppFocus(e.getCode());
                    e.consume();
                    return;
                }

                if (e.getCode() == KeyCode.ENTER) {
                    openAppByIndex(appFocus);
                    e.consume();
                    return;
                }

                e.consume();
                return;
            }
            // ------------------------------------------------------------

            // -------- MUSIC LIST: up/down selects, enter plays ----------
            if (screen == Screen.MUSIC_LIST) {
                if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) {
                    Platform.runLater(musicList::requestFocus);
                    return; // let ListView handle selection
                }

                if (e.getCode() == KeyCode.ENTER) {
                    playSelectedTrack(); // switches to MUSIC_PLAYER
                    e.consume();
                    return;
                }

                e.consume();
            }
            // ------------------------------------------------------------
        });
    }

    private void playSelectedTrack() {
        if (playlist.isEmpty())
            return;

        int sel = musicList.getSelectionModel().getSelectedIndex();
        if (sel < 0)
            sel = 0;

        Track t = playlist.setIndex(sel);
        if (t == null)
            return;

        engine.play(t);
        isPlaying = true;

        // update player state/UI
        isPaused = false;
        playPauseBtn.setText("Pause");
        nowTrack.setText(t.displayName());
        statusBar.setText("Playing");

        // go to player screen, then focus the play/pause button
        showScreen(Screen.MUSIC_PLAYER);
        Platform.runLater(() -> focusPlayerBtn(1));
    }

    private void focusPlayerBtn(int idx) {
        if (playerBtns.isEmpty())
            return;
        playerFocus = Math.max(0, Math.min(playerBtns.size() - 1, idx));
        ButtonBase b = playerBtns.get(playerFocus);
        Platform.runLater(b::requestFocus);
    }

    private void movePlayerFocus(KeyCode code) {
        // indices: 0 prev, 1 play/pause, 2 stop, 3 next, 4 loop, 5 mix
        int i = playerFocus;

        boolean topRow = (i <= 3);
        if (topRow) {
            if (code == KeyCode.LEFT)
                focusPlayerBtn(Math.max(0, i - 1));
            if (code == KeyCode.RIGHT)
                focusPlayerBtn(Math.min(3, i + 1));
            if (code == KeyCode.DOWN)
                focusPlayerBtn(i <= 1 ? 4 : 5); // prev/pause -> loop, stop/next -> mix
            if (code == KeyCode.UP)
                focusPlayerBtn(i);
        } else { // loop/mix
            if (code == KeyCode.LEFT || code == KeyCode.RIGHT)
                focusPlayerBtn(i == 4 ? 5 : 4);
            if (code == KeyCode.UP)
                focusPlayerBtn(i == 4 ? 1 : 2); // loop -> pause, mix -> stop (or choose 3)
            if (code == KeyCode.DOWN)
                focusPlayerBtn(i);
        }
    }

    private void fireFocusedPlayerBtn() {
        if (playerBtns.isEmpty())
            return;
        playerBtns.get(playerFocus).fire(); // works for Button + ToggleButton
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
        if (phoneVisible)
            return;
        phoneVisible = true;

        clickBlocker.setMouseTransparent(false); // block clicks outside phone

        // Always open to launcher
        if (isPlaying) {
            showScreen(Screen.MUSIC_PLAYER);
            Platform.runLater(() -> focusPlayerBtn(1)); // play/pause
        } else {
            showScreen(Screen.LAUNCHER);
            Platform.runLater(appList::requestFocus);
        }

        TranslateTransition t = new TranslateTransition(Duration.millis(180), phone);
        t.setToY(0);
        t.play();

        Platform.runLater(() -> {
            appList.getSelectionModel().select(0);
            appList.requestFocus();
        });
    }

    private void hidePhone() {
        if (!phoneVisible)
            return;
        phoneVisible = false;

        clickBlocker.setMouseTransparent(true);

        TranslateTransition t = new TranslateTransition(Duration.millis(180), phone);
        t.setToY(PHONE_H + 40);
        t.play();

        root.requestFocus();
    }

    // ---------------- Playback logic ----------------

private Track pickNextTrackOnEnd() {
    if (playlist.isEmpty())
        return null;

    // Loop logic is now handled by loopMode
    if (loopMode == LoopMode.ONCE || loopMode == LoopMode.REPEAT) {
        return playlist.current();
    }

    if (mix) {
        int n = playlist.size();
        if (n <= 1)
            return playlist.current();

        int cur = playlist.index();
        int r;
        do {
            r = rng.nextInt(n);
        } while (r == cur);

        return playlist.setIndex(r);
    }

    return playlist.next();
}


    // ---------------- Folder selection ----------------

    private Path getFolderFromArgsOrPrompt(Stage stage) {
        var args = getParameters().getRaw();
        if (!args.isEmpty())
            return Paths.get(args.get(0));

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
