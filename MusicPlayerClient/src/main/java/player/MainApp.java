package player;

import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class MainApp extends Application {

    private static final Set<String> EXT = Set.of("mp3", "m4a", "aac", "wav");

    // Fixed phone size (independent of fullscreen/window size)
    private static final int PHONE_W = 240;
    private static final int PHONE_H = 480;

    private final Playlist playlist = new Playlist(EXT);
    private final ListView<Track> listView = new ListView<>();

    private boolean phoneVisible = false;

    private void lockPhoneFocus() {
        Platform.runLater(() -> {
            if (phoneVisible) {
                listView.requestFocus();
            }
        });
    }

    private VBox phone;

    private StackPane root;
    private Pane clickBlocker;

    private final PlayerEngine engine = new PlayerEngine();

    private void playSelected() {
        if (playlist.isEmpty())
            return;

        int sel = listView.getSelectionModel().getSelectedIndex();
        if (sel < 0)
            sel = 0;

        Track t = playlist.setIndex(sel);
        if (t == null)
            return;

        engine.play(t);
    }

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("MusicPlayer");

        root = new StackPane();
        root.setStyle("-fx-background-color: black;");

        // Create phone first
        phone = createPhone(stage);
        phone.setTranslateY(PHONE_H + 40); // start hidden

        // Create click blocker (blocks clicks outside phone when enabled)
        clickBlocker = new Pane();
        clickBlocker.setPickOnBounds(true);
        clickBlocker.setMouseTransparent(true); // off by default
        clickBlocker.prefWidthProperty().bind(root.widthProperty());
        clickBlocker.prefHeightProperty().bind(root.heightProperty());
        clickBlocker.setOnMousePressed(e -> e.consume());
        clickBlocker.setOnMouseClicked(e -> e.consume());

        // Add blocker first, then phone on top
        root.getChildren().addAll(clickBlocker, phone);

        Scene scene = new Scene(root, 900, 600);
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

        Label title = new Label("PRC-999k");
        title.setStyle("""
                    -fx-text-fill: white;
                    -fx-font-size: 14;
                    -fx-font-weight: bold;
                """);

        listView.setFocusTraversable(true);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.displayName());
                }
            }
        });

        // Load music folder (args[0] or folder picker) and populate list
        Path folder = getFolderFromArgsOrPrompt(stage);
        if (folder != null && Files.isDirectory(folder)) {
            playlist.loadFromFolder(folder);
            listView.getItems().setAll(playlist.all());
        } else {
            // If user cancels folder chooser, just show empty list (no exit)
            listView.getItems().clear();
        }

        VBox.setVgrow(listView, javafx.scene.layout.Priority.ALWAYS);
        box.getChildren().addAll(title, listView);

        StackPane.setAlignment(box, Pos.BOTTOM_LEFT);
        StackPane.setMargin(box, new Insets(16));
        return box;
    }

    private void attachKeyControls(Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {

            if (e.getCode() == KeyCode.ESCAPE) {
                hidePhone();
                e.consume();
                return;
            }

            if (e.getCode() == KeyCode.UP && !phoneVisible) {
                showPhone();
                Platform.runLater(() -> listView.requestFocus());
                e.consume();
                return;
            }

            if (phoneVisible) {
                // keep arrow navigation inside the list
                if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) {
                    Platform.runLater(() -> listView.requestFocus());
                    return; // let ListView move selection
                }

                // ENTER = play
                if (e.getCode() == KeyCode.ENTER) {
                    playSelected();
                    e.consume();
                    return;
                }

                // phone open = block other keys
                e.consume();
            }
        });
    }

    private void showPhone() {
        if (phoneVisible)
            return;
        phoneVisible = true;

        clickBlocker.setMouseTransparent(false); // in showPhone()
        clickBlocker.setMouseTransparent(true); // in hidePhone()

        TranslateTransition t = new TranslateTransition(Duration.millis(180), phone);
        t.setToY(0);
        t.play();

        lockPhoneFocus();
    }

    private void hidePhone() {
        if (!phoneVisible)
            return;
        phoneVisible = false;

        TranslateTransition t = new TranslateTransition(Duration.millis(180), phone);
        t.setToY(PHONE_H + 40);
        t.play();
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

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        engine.shutdown();
    }

}
