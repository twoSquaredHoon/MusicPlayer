package player;

import java.nio.file.Path;

public class Track {
    private final Path path;

    public Track(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    public String displayName() {
        return path.getFileName().toString();
    }

    @Override
    public String toString() {
        return displayName();
    }
}
