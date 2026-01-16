package player;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class DancerSprite extends StackPane {

    private final ImageView view = new ImageView();
    private final Timeline timeline;

    private final int cols;
    private final int frameW;
    private final int frameH;
    private final int frames;     // total frames used (we'll use 0..frames-1)
    private final int idleFrame;
    private int frame = 0;

    public DancerSprite(String resourcePath,
                        int sheetW, int sheetH,
                        int cols, int rows,
                        int frames, int idleFrame,
                        int fps,
                        int scale) {

        this.cols = cols;
        this.frameW = sheetW / cols;
        this.frameH = sheetH / rows;
        this.frames = frames;
        this.idleFrame = idleFrame;

        Image img = new Image(getClass().getResourceAsStream(resourcePath));
        view.setImage(img);

        // Pixel look
        view.setFitWidth(frameW * (double) scale);
        view.setFitHeight(frameH * (double) scale);
        view.setPreserveRatio(false);
        view.setSmooth(false);

        getChildren().add(view);

        setFrame(idleFrame);

        timeline = new Timeline(new KeyFrame(Duration.millis(1000.0 / fps), e -> nextFrame()));
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    private void nextFrame() {
        frame = (frame + 1) % frames;
        setFrame(frame);
    }

    private void setFrame(int i) {
        int col = i % cols;
        int row = i / cols; // row-major order
        int x = col * frameW;
        int y = row * frameH;
        view.setViewport(new Rectangle2D(x, y, frameW, frameH));
    }

    public void startDancing() {
        if (timeline.getStatus() != Animation.Status.RUNNING) timeline.play();
    }

    public void stopDancing() {
        timeline.stop();
        frame = idleFrame;
        setFrame(idleFrame);
    }
}
