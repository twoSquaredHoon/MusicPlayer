package player;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class Playlist {
    private final Set<String> extensions;
    private List<Track> tracks = new ArrayList<>();
    private int idx = -1;

    public Playlist(Set<String> extensions) {
        this.extensions = extensions;
    }

    public void loadFromFolder(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) throw new IllegalArgumentException("Not a folder: " + folder);

        try (Stream<Path> s = Files.walk(folder)) {
            tracks = s.filter(Files::isRegularFile)
                    .filter(p -> extensions.contains(ext(p)))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .map(Track::new)
                    .toList();
        }
        idx = tracks.isEmpty() ? -1 : 0;
    }

    public boolean isEmpty() { return tracks.isEmpty(); }
    public int size() { return tracks.size(); }
    public int index() { return idx; }
    public List<Track> all() { return tracks; }

    public Track current() {
        if (idx < 0 || idx >= tracks.size()) return null;
        return tracks.get(idx);
    }

    public Track get(int i) {
        if (i < 0 || i >= tracks.size()) throw new IllegalArgumentException("Index out of range.");
        return tracks.get(i);
    }

    public Track setIndex(int i) {
        get(i); // validate
        idx = i;
        return current();
    }

    public Track next() {
        if (tracks.isEmpty()) return null;
        idx = (idx + 1) % tracks.size();
        return current();
    }

    public Track prev() {
        if (tracks.isEmpty()) return null;
        idx = (idx - 1 + tracks.size()) % tracks.size();
        return current();
    }

    public void list(int n) {
        int limit = Math.min(n, tracks.size());
        for (int i = 0; i < limit; i++) {
            System.out.printf("%4d  %s%n", i, tracks.get(i).displayName());
        }
        if (tracks.size() > limit) System.out.println("... (" + (tracks.size() - limit) + " more)");
    }

    private static String ext(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
