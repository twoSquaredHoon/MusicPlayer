package player;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class DancerSprite extends StackPane {

    private final Image sheet;
    private final Canvas canvas;
    private final GraphicsContext g;
    private final Timeline timeline;

    private final int cols;
    private final int frameW;
    private final int frameH;
    private final int frames;
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

        this.sheet = new Image(getClass().getResourceAsStream(resourcePath));

        this.canvas = new Canvas(frameW * (double) scale, frameH * (double) scale);
        this.g = canvas.getGraphicsContext2D();
        this.g.setImageSmoothing(false);

        setSnapToPixel(true);
        getChildren().add(canvas);

        drawFrame(idleFrame, scale);

        timeline = new Timeline(new KeyFrame(Duration.millis(1000.0 / fps), e -> {
            frame = (frame + 1) % frames;
            drawFrame(frame, scale);
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    private void drawFrame(int i, int scale) {
        int col = i % cols;
        int row = i / cols;

        double sx = col * frameW;
        double sy = row * frameH;

        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.drawImage(sheet,
                sx, sy, frameW, frameH,          // source rect
                0, 0, frameW * scale, frameH * scale // dest rect
        );
    }

    public void startDancing() {
        if (timeline.getStatus() != Animation.Status.RUNNING) timeline.play();
    }

    public void stopDancing() {
        timeline.stop();
        frame = idleFrame;
        drawFrame(idleFrame, (int)(canvas.getWidth() / frameW));
    }
}
