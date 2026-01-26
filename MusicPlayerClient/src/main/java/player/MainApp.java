package player;

import javafx.animation.Timeline;
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
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
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
import javafx.animation.KeyFrame;
import javafx.scene.shape.Rectangle;
import javafx.scene.input.ScrollEvent;

public class MainApp extends Application {
    // Accepted data types
    private static final Set<String> EXT = Set.of("mp3", "m4a", "aac", "wav");

    // Top level container for entire app, Everything (background, dancer, phone UI)
    // is stacked on top of each other here.
    // Might fix this in the future
    private StackPane root;
    private Pane clickBlocker;

    // Phone realted
    private VBox phone;
    private static final int PHONE_W = 240;
    private static final int PHONE_H = 480;
    private boolean phoneVisible = false;

    // Which screen is currently active on phone
    private enum Screen {
        LAUNCHER, MUSIC_LIST, MUSIC_PLAYER
    }

    // Phone starts on LAUNCHER
    private Screen screen = Screen.LAUNCHER;

    // UI elements inside phone
    private final ListView<String> appList = new ListView<>();
    private final ListView<Track> musicList = new ListView<>();

    // Launcher Aoo grid
    private final String[] apps = { "Music", "Messages", "Settings", "Notes", "Map", "Camera", "Clock" };
    private final java.util.List<Button> appTiles = new java.util.ArrayList<>();
    private int appFocus = 0;
    private static final int APP_COLS = 3;

    // Playback (data/engine)
    // data selection
    private final Playlist playlist = new Playlist(EXT);

    // playback and audio
    private final PlayerEngine engine = new PlayerEngine();

    // Phone screens
    private VBox launcherScreen;
    private VBox musicListScreen;
    private VBox musicPlayerScreen;

    private TranslateTransition marquee;

    // Loop, Mix, Random
    private enum LoopMode {
        OFF, ONCE, REPEAT
    }

    private LoopMode loopMode = LoopMode.OFF;
    // Asked for loop once but hasn't happened yet
    private boolean loopOnceArmed = false;
    private boolean mix = false;
    private final Random rng = new Random();

    // Music Playing Screen
    private ImageView albumArt;
    private final Label nowTrack = new Label("");
    private final Label statusBar = new Label("Stopped");
    private ToggleButton playPauseBtn;
    private boolean isPaused = false;
    private final java.util.List<ButtonBase> playerBtns = new java.util.ArrayList<>();
    private int playerFocus = 0;
    private boolean isPlaying = false;
    private Button loopBtn; // field
    private Slider progress;
    private final Label timeLabel = new Label("0:00 / 0:00");
    private Timeline progressTimer;
    private boolean userScrubbing = false;

    // dancing sprite
    private ImageView dancer;
    private java.util.List<Image> danceFrames;
    private Timeline danceTimeline;
    private int danceIdx = 0;

    // Design that is most likely to be fixed later anyway
    private static final String APP_STYLE_NORMAL = """
                -fx-background-color: rgba(255,255,255,0.10);
                -fx-text-fill: white;
                -fx-font-size: 11;
                -fx-background-radius: 12;
            """;

    private static final String APP_STYLE_ACTIVE = """
                -fx-background-color: #4caf50;
                -fx-text-fill: black;
                -fx-font-size: 11;
                -fx-background-radius: 12;
            """;

    // ---------------- Bot Sequence ----------------
    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("MusicPlayer");

        // Root container
        root = new StackPane();

        // Background image
        Image bg = new Image(
                getClass().getResource("/background/mkqeq4erhho5du.jpeg").toExternalForm());

        BackgroundImage bgImg = new BackgroundImage(
                bg,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(
                        BackgroundSize.AUTO, BackgroundSize.AUTO,
                        false, false, false, true));

        root.setBackground(new Background(bgImg));

        // Create phone (hidden off-screen initially)
        phone = createPhone(stage);
        phone.setTranslateY(PHONE_H + 40);

        // Dancing sprite (behind phone)
        initDancer();

        // Layer order: background → dancer → phone
        root.getChildren().addAll(dancer, phone);

        // Scene
        Scene scene = new Scene(root, 900, 600);
        scene.setCursor(Cursor.DEFAULT);
        attachKeyControls(scene);

        // IMPORTANT: keyboard focus
        root.setFocusTraversable(true);
        root.setOnMouseClicked(e -> root.requestFocus());
        Platform.runLater(root::requestFocus);

        stage.setScene(scene);
        stage.show();
    }

    // ---------------- Phone open/close + click blocking (disabled) ----------------
    private void showPhone() {
        if (phoneVisible)
            return;
        phoneVisible = true;

        // clickBlocker.setMouseTransparent(false); // block clicks outside phone

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

        // clickBlocker.setMouseTransparent(true);

        TranslateTransition t = new TranslateTransition(Duration.millis(180), phone);
        t.setToY(PHONE_H + 40);
        t.play();

        root.requestFocus();
    }

    // ---------------- Phone construction ----------------
    private VBox createPhone(Stage stage) throws Exception {
        // Stacks item vertically 10 spaces
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
        
        // Stacks screens in the phone
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
            if (playlist.isEmpty()) {
                engine.stop();
                isPlaying = false;
                isPaused = false;
                if (playPauseBtn != null)
                    playPauseBtn.setText("Play");
                statusBar.setText("Stopped");
                updateDanceState();
                return;
            }

            Track next;

            if (loopMode == LoopMode.REPEAT) {
                // repeat forever (button stays On)
                next = playlist.current();

            } else if (loopMode == LoopMode.ONCE) {
                if (loopOnceArmed) {
                    // ✅ FIRST end: do the one extra repeat,
                    // but immediately reset UI/state to OFF like you want.
                    loopOnceArmed = false;
                    loopMode = LoopMode.OFF;
                    if (loopBtn != null)
                        updateLoopButton(loopBtn); // Loop button flips to Off NOW

                    next = playlist.current(); // replay one time
                } else {
                    // SECOND end: now it advances normally (since loopMode is already OFF)
                    next = pickNextTrackOnEnd();
                }
            } else {
                // OFF: normal advance
                next = pickNextTrackOnEnd();
            }

            // Decide to play or stop the next song
            if (next != null) {
                engine.play(next);
                startProgressTimer();

                isPlaying = true;
                isPaused = false;
                if (playPauseBtn != null)
                    playPauseBtn.setText("Pause");
                statusBar.setText("Playing");
                nowTrack.setText(next.displayName());
                musicList.getSelectionModel().select(playlist.index());
                updateDanceState();
            } else {
                engine.stop();
                isPlaying = false;
                isPaused = false;
                if (playPauseBtn != null)
                    playPauseBtn.setText("Play");
                statusBar.setText("Stopped");
                updateDanceState();
            }
        }));

        return box;
    }

    // Builds the launcher screen
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
            tile.setPrefSize(64, 64);
            tile.setMinSize(64, 64);
            tile.setMaxSize(64, 64);
            tile.setFocusTraversable(true);

            tile.setStyle(APP_STYLE_NORMAL); // ✅ only set style once

            final int idx = i;
            tile.setOnAction(e -> openAppByIndex(idx));

            appTiles.add(tile);

            int r = i / APP_COLS;
            int c = i % APP_COLS;
            grid.add(tile, c, r);
        }

        VBox.setVgrow(grid, Priority.ALWAYS);
        v.getChildren().addAll(header, grid);

        updateAppTileStyles(); // ✅ highlight current selection on build
        return v;
    }

    private void updateAppTileStyles() {
        for (int i = 0; i < appTiles.size(); i++) {
            Button b = appTiles.get(i);
            b.setStyle(i == appFocus ? APP_STYLE_ACTIVE : APP_STYLE_NORMAL);
        }
    }

    private void focusAppTile(int idx) {
        if (appTiles.isEmpty())
            return;
        appFocus = Math.max(0, Math.min(appTiles.size() - 1, idx));
        updateAppTileStyles();
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

    musicList.setFixedCellSize(48);
    musicList.setPrefWidth(PHONE_W - 28);
    musicList.setMaxWidth(PHONE_W - 28);
    
    musicList.setCellFactory(lv -> new ListCell<>() {
        private final Label title = new Label();
        private final Label artist = new Label();
        private final VBox textBox = new VBox(2, title, artist);

        {
            title.setStyle("""
                -fx-text-fill: black;
                -fx-font-size: 12;
                -fx-font-weight: bold;
            """);

            artist.setStyle("""
                -fx-text-fill: black;
                -fx-font-size: 10;
            """);

            textBox.setAlignment(Pos.CENTER_LEFT);
            textBox.setMaxWidth(PHONE_W - 50);

            setText(null);
            setGraphic(textBox);
            setPadding(new Insets(6, 8, 6, 8));

            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(widthProperty().subtract(16));
            clip.heightProperty().bind(heightProperty());
            textBox.setClip(clip);
        }

        @Override
        protected void updateItem(Track item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                setStyle("-fx-background-color: transparent;");
                return;
            }

            title.setText(item.displayName());
            title.setMinWidth(Region.USE_PREF_SIZE);
            title.setMaxWidth(Region.USE_PREF_SIZE);
            title.setWrapText(false);

            artist.setText("Unknown Artist");

            setGraphic(textBox);
            updateSelectionStyle();
        }

        @Override
        public void updateSelected(boolean selected) {
            super.updateSelected(selected);
            updateSelectionStyle();

            if (marquee != null) marquee.stop();
            title.setTranslateX(0);

            if (selected) {
                double overflow = title.getWidth() - (musicList.getWidth() - 40);
                if (overflow > 0) {
                    marquee = new TranslateTransition(Duration.seconds(overflow / 30), title);
                    marquee.setFromX(0);
                    marquee.setToX(-overflow);
                    marquee.setAutoReverse(true);
                    marquee.setCycleCount(TranslateTransition.INDEFINITE);
                    marquee.play();
                }
            }
        }

        private void updateSelectionStyle() {
            if (isSelected()) {
                setStyle("""
                    -fx-background-color: rgba(76,175,80,0.85);
                    -fx-background-radius: 10;
                """);
            } else {
                setStyle("""
                    -fx-background-color: rgba(255,255,255,0.05);
                    -fx-background-radius: 10;
                """);
            }
        }
    });

    // Hide ALL scrollbars
    musicList.lookupAll(".scroll-bar").forEach(node -> {
        if (node instanceof ScrollBar) {
            ScrollBar bar = (ScrollBar) node;
            bar.setVisible(false);
            bar.setManaged(false);
            bar.setOpacity(0);
            bar.setPrefHeight(0);
            bar.setMaxHeight(0);
            bar.setPrefWidth(0);
            bar.setMaxWidth(0);
        }
    });

    musicList.skinProperty().addListener((obs, oldSkin, newSkin) -> {
        if (newSkin != null) {
            Platform.runLater(() -> {
                musicList.lookupAll(".scroll-bar").forEach(node -> {
                    if (node instanceof ScrollBar) {
                        ScrollBar bar = (ScrollBar) node;
                        bar.setVisible(false);
                        bar.setManaged(false);
                        bar.setOpacity(0);
                        bar.setPrefHeight(0);
                        bar.setMaxHeight(0);
                        bar.setPrefWidth(0);
                        bar.setMaxWidth(0);
                        bar.setDisable(true);
                    }
                });
            });
        }
    });

    musicList.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
        if (newV == null) return;
        int i = newV.intValue();
        if (i >= 0) musicList.scrollTo(i);
    });

    // Set initial selection to first item
    Platform.runLater(() -> {
        if (!musicList.getItems().isEmpty()) {
            musicList.getSelectionModel().select(0);
            musicList.scrollTo(0);
            musicList.requestFocus();
        }
    });

    VBox.setVgrow(musicList, Priority.ALWAYS);
    v.getChildren().addAll(header, musicList);

    // COMPLETELY DISABLE ALL TRACKPAD/MOUSE SCROLLING
    musicList.addEventFilter(ScrollEvent.ANY, ScrollEvent::consume);
    musicList.setOnScroll(ScrollEvent::consume);

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
        timeLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 11;");

        progress = new Slider(0, 1, 0);
        progress.setMaxWidth(PHONE_W - 28);
        progress.setFocusTraversable(true);

        progress.setOnMousePressed(e -> userScrubbing = true);
        progress.setOnMouseReleased(e -> {
            double total = engine.getTotalSeconds();
            if (total > 0.001) {
                engine.seekSeconds(progress.getValue() * total);
            }
            userScrubbing = false;
        });

        // Controls row
        Button prev = new Button("Prev");
        playPauseBtn = new ToggleButton("Play"); // <- start not playing
        Button stop = new Button("Stop");
        Button next = new Button("Next");

        prev.setOnAction(e -> {
            Track t = playlist.prev();
            if (t != null) {
                engine.play(t);
                startProgressTimer();

                isPlaying = true;
                isPaused = false;
                playPauseBtn.setText("Pause");

                musicList.getSelectionModel().select(playlist.index());
                nowTrack.setText(t.displayName());
                statusBar.setText("Playing");
                updateDanceState();
            }
        });

        next.setOnAction(e -> {
            Track t = pickNextManual(); // respects Mix
            if (t != null) {
                engine.play(t);
                startProgressTimer();

                isPlaying = true;
                isPaused = false;
                playPauseBtn.setText("Pause");

                musicList.getSelectionModel().select(playlist.index());
                nowTrack.setText(t.displayName());
                statusBar.setText("Playing");
                updateDanceState();
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
                updateDanceState();
            } else {
                engine.resume();
                isPaused = false;
                playPauseBtn.setText("Pause");
                statusBar.setText("Playing");
                updateDanceState();
            }
        });

        stop.setOnAction(e -> {
            engine.stop();
            isPlaying = false;
            isPaused = false;
            statusBar.setText("Stopped");
            playPauseBtn.setText("Play");
            stopProgressTimer();
            progress.setValue(0);
            timeLabel.setText("0:00 / 0:00");
            updateDanceState();

        });

        HBox row1 = new HBox(6, prev, playPauseBtn, stop, next);
        row1.setAlignment(Pos.CENTER);

        // Loop + Mix row

        // Loop + Mix row
        loopBtn = new Button(); // ✅ assign the FIELD, not a local var
        updateLoopButton(loopBtn);

        ToggleButton mixBtn = new ToggleButton("Mix");

        loopBtn.setOnAction(e -> {
            loopMode = switch (loopMode) {
                case OFF -> {
                    loopOnceArmed = true;
                    yield LoopMode.ONCE;
                }
                case ONCE -> {
                    loopOnceArmed = false;
                    yield LoopMode.REPEAT;
                }
                case REPEAT -> {
                    loopOnceArmed = false;
                    yield LoopMode.OFF;
                }
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

        v.getChildren().addAll(artWrap, nowTrack, statusBar, timeLabel, progress, row1, row2);
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
        startProgressTimer();

        isPlaying = true;

        // update player state/UI
        isPaused = false;
        playPauseBtn.setText("Pause");
        nowTrack.setText(t.displayName());
        statusBar.setText("Playing");

        // go to player screen, then focus the play/pause button
        showScreen(Screen.MUSIC_PLAYER);
        Platform.runLater(() -> focusPlayerBtn(1));

        updateDanceState();
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

    // ---------------- Playback logic ----------------

    private Track pickNextTrackOnEnd() {
        if (playlist.isEmpty())
            return null;

        // If looping (once or repeat), replay current track
        if (loopMode == LoopMode.ONCE || loopMode == LoopMode.REPEAT) {
            return playlist.current();
        }

        // Otherwise advance (respect Mix)
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

    private static String mmss(double seconds) {
        if (seconds < 0)
            seconds = 0;
        int s = (int) Math.floor(seconds + 0.0001);
        int m = s / 60;
        s = s % 60;
        return m + ":" + String.format("%02d", s);
    }

    private void startProgressTimer() {
        if (progressTimer != null)
            progressTimer.stop();

        progressTimer = new Timeline(new KeyFrame(Duration.millis(200), e -> {
            if (progress == null)
                return;
            if (!isPlaying)
                return;

            double cur = engine.getCurrentSeconds();
            double total = engine.getTotalSeconds();

            if (total <= 0.001) {
                timeLabel.setText(mmss(cur) + " / 0:00");
                if (!userScrubbing)
                    progress.setValue(0);
                return;
            }

            timeLabel.setText(mmss(cur) + " / " + mmss(total));

            if (!userScrubbing) {
                progress.setValue(cur / total);
            }
        }));
        progressTimer.setCycleCount(Timeline.INDEFINITE);
        progressTimer.play();
    }

    private void stopProgressTimer() {
        if (progressTimer != null)
            progressTimer.stop();
    }

    private void initDancer() {
        Image sheet = new Image(
                getClass().getResource("/sprites/zero.png").toExternalForm());

        int cols = 8;
        int rows = 3;
        int frameW = (int) sheet.getWidth() / cols;
        int frameH = (int) sheet.getHeight() / rows;

        dancer = new ImageView(sheet);
        dancer.setViewport(new javafx.geometry.Rectangle2D(0, 0, frameW, frameH));
        dancer.setSmooth(false);

        StackPane.setAlignment(dancer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(dancer, new Insets(0, 30, 30, 0));

        danceTimeline = new Timeline(new KeyFrame(Duration.millis(90), e -> {
            danceIdx = (danceIdx + 1) % (cols * rows);
            int x = (danceIdx % cols) * frameW;
            int y = (danceIdx / cols) * frameH;
            dancer.setViewport(new javafx.geometry.Rectangle2D(x, y, frameW, frameH));
        }));
        danceTimeline.setCycleCount(Timeline.INDEFINITE);

        danceFrameW = (int) sheet.getWidth() / cols;
        danceFrameH = (int) sheet.getHeight() / rows;

        dancer.setViewport(new javafx.geometry.Rectangle2D(0, 0, danceFrameW, danceFrameH));

    }

    // add these fields near dancer fields
    private int danceFrameW;
    private int danceFrameH;

    private void updateDanceState() {
        boolean shouldDance = isPlaying && !isPaused;
        if (danceTimeline == null || dancer == null)
            return;

        if (shouldDance) {
            if (danceTimeline.getStatus() != javafx.animation.Animation.Status.RUNNING) {
                danceTimeline.play();
            }
        } else {
            danceTimeline.stop();
            danceIdx = 0;
            dancer.setViewport(new javafx.geometry.Rectangle2D(0, 0, danceFrameW, danceFrameH));
        }
    }

    @Override
    public void stop() {
        engine.shutdown(); // stops music only when app exits
    }

    public static void main(String[] args) {
        launch(args);
    }
}