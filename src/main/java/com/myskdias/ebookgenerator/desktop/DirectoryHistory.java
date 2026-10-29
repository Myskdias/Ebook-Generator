package com.myskdias.ebookgenerator.desktop;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javafx.stage.FileChooser;

/** Remember chooser folders independently for HTML and profiles, including across restarts. */
final class DirectoryHistory {
    enum Kind { HTML, PROFILE }
    private static final String LAST = "lastDirectory";
    private final Preferences preferences;
    private final Map<String, String> session = new HashMap<>();

    DirectoryHistory(Preferences preferences) {
        this.preferences = preferences;
    }

    static DirectoryHistory forUser() {
        try {
            return new DirectoryHistory(Preferences.userNodeForPackage(DirectoryHistory.class));
        } catch (SecurityException ex) {
            return new DirectoryHistory(null);
        }
    }

    void configure(FileChooser chooser, Kind kind) {
        Path directory = initialDirectory(kind);
        if (directory != null) chooser.setInitialDirectory(directory.toFile());
    }

    Path initialDirectory(Kind kind) {
        for (String value : new String[] {read(key(kind)), read(LAST),
                Path.of(System.getProperty("user.home"), "Documents").toString(),
                System.getProperty("user.home")}) {
            Path available = existingParent(value);
            if (available != null) return available;
        }
        return null;
    }

    void remember(Kind kind, Path selectedFile) {
        if (selectedFile == null) return; // A cancelled dialog must not reset its folder.
        Path parent = selectedFile.toAbsolutePath().normalize().getParent();
        if (parent == null) return;
        write(key(kind), parent.toString());
        write(LAST, parent.toString());
        if (preferences != null) {
            try { preferences.flush(); }
            catch (BackingStoreException | SecurityException ignored) {
                // Session history remains useful if the OS preference store is unavailable.
            }
        }
    }

    private static String key(Kind kind) {
        return kind == Kind.HTML ? "htmlDirectory" : "profileDirectory";
    }

    private String read(String key) {
        if (session.containsKey(key)) return session.get(key);
        try { return preferences == null ? null : preferences.get(key, null); }
        catch (SecurityException ex) { return null; }
    }

    private void write(String key, String value) {
        session.put(key, value);
        if (preferences != null) {
            try { preferences.put(key, value); }
            catch (SecurityException | IllegalArgumentException ignored) { }
        }
    }

    private static Path existingParent(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            for (Path path = Path.of(value).toAbsolutePath().normalize(); path != null; path = path.getParent()) {
                if (Files.isDirectory(path) && Files.isReadable(path)) return path;
            }
        } catch (InvalidPathException | SecurityException ignored) { }
        return null;
    }
}
