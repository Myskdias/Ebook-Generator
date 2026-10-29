package com.myskdias.ebookgenerator.desktop;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryHistoryTest {
    @TempDir Path temp;

    @Test void restoresHtmlAndSharedLoadSaveProfileFoldersAfterReopening() throws Exception {
        var preferences = new MemoryPreferences();
        var history = new DirectoryHistory(preferences);
        Path html = Files.createDirectory(temp.resolve("html"));
        Path profiles = Files.createDirectory(temp.resolve("profiles"));
        Path books = Files.createDirectory(temp.resolve("books"));
        Path downloads = Files.createDirectory(temp.resolve("downloads"));
        history.remember(DirectoryHistory.Kind.HTML, html.resolve("chapter.html"));
        // The first profile dialog starts where HTML was last selected, until it has its own folder.
        assertEquals(html, history.initialDirectory(DirectoryHistory.Kind.PROFILE));
        history.remember(DirectoryHistory.Kind.PROFILE, profiles.resolve("site.json"));
        history.remember(DirectoryHistory.Kind.EPUB, books.resolve("book.epub"));
        history.rememberDirectory(DirectoryHistory.Kind.DOWNLOAD, downloads);
        var reopened = new DirectoryHistory(preferences);
        var openHtml = new FileChooser();
        var loadOrSaveProfile = new FileChooser();
        reopened.configure(openHtml, DirectoryHistory.Kind.HTML);
        reopened.configure(loadOrSaveProfile, DirectoryHistory.Kind.PROFILE);
        assertEquals(html.toFile(), openHtml.getInitialDirectory());
        assertEquals(profiles.toFile(), loadOrSaveProfile.getInitialDirectory());
        assertEquals(books, reopened.initialDirectory(DirectoryHistory.Kind.EPUB));
        assertEquals(downloads, reopened.initialDirectory(DirectoryHistory.Kind.DOWNLOAD));
        assertTrue(preferences.flushed);
    }

    @Test void cancellingASelectionDoesNotResetTheFolder() throws Exception {
        var history = new DirectoryHistory(null);
        Path directory = Files.createDirectory(temp.resolve("novel"));
        history.remember(DirectoryHistory.Kind.HTML, directory.resolve("1.html"));
        history.remember(DirectoryHistory.Kind.HTML, null);
        assertEquals(directory, history.initialDirectory(DirectoryHistory.Kind.HTML));
    }

    @Test void deletedFoldersFallBackToTheirNearestExistingParent() throws Exception {
        var preferences = new MemoryPreferences();
        var history = new DirectoryHistory(preferences);
        Path directory = Files.createDirectory(temp.resolve("removed"));
        history.remember(DirectoryHistory.Kind.PROFILE, directory.resolve("site.json"));
        Files.delete(directory);
        assertEquals(temp, new DirectoryHistory(preferences).initialDirectory(DirectoryHistory.Kind.PROFILE));
        preferences.values.put("profileDirectory", "\u0000invalid");
        preferences.values.put("lastDirectory", temp.toString());
        assertEquals(temp, new DirectoryHistory(preferences).initialDirectory(DirectoryHistory.Kind.PROFILE));
    }

    @Test void preferenceStoreFailureStillKeepsSessionHistory() throws Exception {
        var preferences = new MemoryPreferences();
        preferences.unavailable = true;
        var history = new DirectoryHistory(preferences);
        Path directory = Files.createDirectory(temp.resolve("selected"));
        assertDoesNotThrow(() -> history.remember(DirectoryHistory.Kind.HTML, directory.resolve("1.html")));
        assertEquals(directory, history.initialDirectory(DirectoryHistory.Kind.HTML));
    }

    /** Isolated preference backing store: tests never read/write the user's OS preferences. */
    private static final class MemoryPreferences extends AbstractPreferences {
        final Map<String, String> values = new HashMap<>();
        boolean flushed;
        boolean unavailable;
        MemoryPreferences() { super(null, ""); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void putSpi(String key, String value) {
            if (unavailable) throw new SecurityException("Store unavailable");
            values.put(key, value);
        }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { values.clear(); }
        @Override protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() throws BackingStoreException {
            if (unavailable) throw new BackingStoreException("Store unavailable");
            flushed = true;
        }
    }
}
