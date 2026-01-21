package player;

import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.InputStream;
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

    // screens inside phone
    private VBox listScreen;
    private VBox playerScreen;

    private final Playlist playlist = new Playlist(EXT);
    private final PlayerEngine engine = new PlayerEngine();

    private final ListView<Track> listView = new ListView<>();

    // player UI bits
    private final Label trackLabel = new Label("");
    private final Label statusBar = new Label("Stopped");
    private ImageView albumArt;

    private boolean loop = false;
    private boolean mix = false;
    private final Random rng = new Random();

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("MusicPlayer");

        root = new StackPane();
        root.setStyle("-fx-background-color: black;");

        // Create phone first (needs stage for folder picker)
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

        // blocker first, then phone on top
        root.getChildren().addAll(clickBlocker, phone);

        Scene scene = new Scene(root, 900, 600);
        scene.setCursor(javafx.scene.Cursor.DEFAULT);
        attachKeyControls(scene);

        stage.setScene(scene);
        stage.show();

        Platform.runLater(() -> root.requestFocus());
    }

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

        // Title
        Label title = new Label("999K");
        title.setStyle("""
            -fx-text-fill: white;
            -fx-font-size: 14;
            -fx-font-weight: bold;
        """);

        // Load folder + list items
        Path folder = getFolderFromArgsOrPrompt(stage);
        if (folder != null && Files.isDirectory(folder)) {
            playlist.loadFromFolder(folder);
            listView.getItems().setAll(playlist.all());
        } else {
            listView.getItems().clear();
        }

        listView.setFocusTraversable(true);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.displayName());
            }
        });

        // Build two screens
        listScreen = buildListScreen();
        playerScreen = buildPlayerScreen();
        showListScreen(); // default

        VBox.setVgrow(listScreen, Priority.ALWAYS);
        VBox.setVgrow(playerScreen, Priority.ALWAYS);

        box.getChildren().addAll(title, listScreen, playerScreen);

        // anchor phone bottom-left
        StackPane.setAlignment(box, Pos.BOTTOM_LEFT);
        StackPane.setMargin(box, new Insets(16));

        // allow clicks inside phone
        box.setOnMousePressed(e -> e.consume());

        // auto-advance based on loop/mix
        engine.setOnEnd(() -> {
            Track next = pickNextTrackOnEnd();
            if (next != null) {
                engine.play(next);
                Platform.runLater(() -> {
                    listView.getSelectionModel().select(playlist.index());
                    trackLabel.setText(next.displayName());
                    statusBar.setText("Playing");
                });
            }
        });

        return box;
    }

    private VBox buildListScreen() {
        VBox v = new VBox(8);
        v.setAlignment(Pos.TOP_CENTER);

        Label hint = new Label("↑/↓ select • ENTER play");
        hint.setStyle("-fx-text-fill: rgba(255,255,255,0.55); -fx-font-size: 11;");

        VBox.setVgrow(listView, Priority.ALWAYS);
        v.getChildren().addAll(hint, listView);
        return v;
    }

    private VBox buildPlayerScreen() {
        VBox v = new VBox(10);
        v.setAlignment(Pos.TOP_CENTER);
        v.setPadding(new Insets(8, 0, 0, 0));

        // Album art (top middle)
        albumArt = new ImageView(loadOptionalImage("/sprites/album.png"));
        albumArt.setFitWidth(140);
        albumArt.setFitHeight(140);
        albumArt.setPreserveRatio(true);
        albumArt.setSmooth(false);

        // If no image found, show a simple placeholder block
        StackPane artWrap = new StackPane(albumArt);
        artWrap.setPrefSize(140, 140);
        artWrap.setMaxSize(140, 140);
        artWrap.setStyle("""
            -fx-background-color: rgba(255,255,255,0.08);
            -fx-background-radius: 12;
            -fx-border-color: rgba(255,255,255,0.12);
            -fx-border-radius: 12;
        """);

        // Track name (optional but fits your “status bar below album” idea)
        trackLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
        trackLabel.setMaxWidth(PHONE_W - 30);
        trackLabel.setAlignment(Pos.CENTER);
        trackLabel.setWrapText(false);

        // Status bar below that
        statusBar.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 11;");

        // Controls row: prev, pause/resume, stop, next
        Button prev = new Button("Prev");
        Button pause = new Button("Pause");
        Button resume = new Button("Resume");
        Button stop = new Button("Stop");
        Button next = new Button("Next");

        prev.setOnAction(e -> {
            Track t = playlist.prev();
            if (t != null) {
                engine.play(t);
                listView.getSelectionModel().select(playlist.index());
                trackLabel.setText(t.displayName());
                statusBar.setText("Playing");
            }
        });

        next.setOnAction(e -> {
            Track t = playlist.next();
            if (t != null) {
                engine.play(t);
                listView.getSelectionModel().select(playlist.index());
                trackLabel.setText(t.displayName());
                statusBar.setText("Playing");
            }
        });

        pause.setOnAction(e -> {
            engine.pause();
            statusBar.setText("Paused");
        });

        resume.setOnAction(e -> {
            engine.resume();
            statusBar.setText("Playing");
        });

        stop.setOnAction(e -> {
            engine.stop();
            statusBar.setText("Stopped");
        });

        HBox row1 = new HBox(6, prev, pause, resume, stop, next);
        row1.setAlignment(Pos.CENTER);

        // Loop + Mix row
        ToggleButton loopBtn = new ToggleButton("Loop");
        ToggleButton mixBtn = new ToggleButton("Mix");

        loopBtn.setOnAction(e -> loop = loopBtn.isSelected());
        mixBtn.setOnAction(e -> mix = mixBtn.isSelected());

        HBox row2 = new HBox(8, loopBtn, mixBtn);
        row2.setAlignment(Pos.CENTER);

        v.getChildren().addAll(artWrap, trackLabel, statusBar, row1, row2);
        return v;
    }

    private void attachKeyControls(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {

            // ESC: if in player screen, go back to list screen; otherwise hide phone
            if (e.getCode() == KeyCode.ESCAPE) {
                if (phoneVisible && isPlayerScreenShowing()) {
                    showListScreen();
                    Platform.runLater(() -> listView.requestFocus());
                } else {
                    hidePhone();
                }
                e.consume();
                return;
            }

            // UP opens phone
            if (e.getCode() == KeyCode.UP && !phoneVisible) {
                showPhone();
                Platform.runLater(() -> listView.requestFocus());
                e.consume();
                return;
            }

            if (phoneVisible) {
                // In list screen: arrow nav should work
                if (!isPlayerScreenShowing() && (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN)) {
                    Platform.runLater(() -> listView.requestFocus());
                    return; // let ListView handle selection
                }

                // ENTER on list screen: play + switch to player screen
                if (!isPlayerScreenShowing() && e.getCode() == KeyCode.ENTER) {
                    playSelectedAndShowPlayer();
                    e.consume();
                    return;
                }

                // phone open = block other keys
                e.consume();
            }
        });
    }

    private boolean isPlayerScreenShowing() {
        return playerScreen.isVisible();
    }

    private void showListScreen() {
        listScreen.setVisible(true);
        listScreen.setManaged(true);
        playerScreen.setVisible(false);
        playerScreen.setManaged(false);
    }

    private void showPlayerScreen() {
        listScreen.setVisible(false);
        listScreen.setManaged(false);
        playerScreen.setVisible(true);
        playerScreen.setManaged(true);
    }

    private void playSelectedAndShowPlayer() {
        if (playlist.isEmpty()) return;

        int sel = listView.getSelectionModel().getSelectedIndex();
        if (sel < 0) sel = 0;

        Track t = playlist.setIndex(sel);
        if (t == null) return;

        engine.play(t);

        trackLabel.setText(t.displayName());
        statusBar.setText("Playing");

        showPlayerScreen();
    }

    private Track pickNextTrackOnEnd() {
        if (playlist.isEmpty()) return null;

        if (loop) {
            return playlist.current();
        }

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

    private void showPhone() {
        if (phoneVisible) return;
        phoneVisible = true;

        clickBlocker.setMouseTransparent(false);

        TranslateTransition t = new TranslateTransition(Duration.millis(180), phone);
        t.setToY(0);
        t.play();

        // always start in list screen when opening
        showListScreen();
        Platform.runLater(() -> listView.requestFocus());
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

    private Path getFolderFromArgsOrPrompt(Stage stage) {
        var args = getParameters().getRaw();
        if (!args.isEmpty()) {
            return Paths.get(args.get(0));
        }

        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose Music Folder");
        File chosen = dc.showDialog(stage);
        return (chosen == null) ? null : chosen.toPath();
    }

    private Image loadOptionalImage(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            return new Image(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void stop() {
        engine.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
