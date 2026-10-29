package com.myskdias.ebookgenerator.download;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** Persistent source pages and their explicit order, independent of any extraction profile. */
public record DownloadArchive(int version, URI firstUrl, int requestedChapters, String nextSelector,
                              State state, String message, List<Page> pages) {
    public static final String FILE_NAME = "chapter-download.json";
    public enum State { DOWNLOADING, COMPLETE, CANCELLED, FAILED }
    public record Page(URI url, String file, String sha256) { }
    private static final ObjectMapper JSON = new ObjectMapper();

    public DownloadArchive {
        if (version != 1 || firstUrl == null || state == null || pages == null
            || pages.size() > 10_000 || requestedChapters < 0 || requestedChapters > 10_000) {
            throw new IllegalArgumentException("Invalid downloaded-book manifest");
        }
        nextSelector = nextSelector == null ? "" : nextSelector;
        message = message == null ? "" : message;
        pages = List.copyOf(pages);
    }

    public static DownloadArchive load(Path manifest) throws IOException {
        if (Files.size(manifest) > 10 * 1024 * 1024) throw new IOException("Downloaded-book manifest is too large");
        return JSON.readValue(manifest.toFile(), DownloadArchive.class);
    }

    public void save(Path manifest) throws IOException {
        Path destination = manifest.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent());
        Path temp = Files.createTempFile(destination.getParent(), ".download-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), this);
            try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    public Path pageFile(Path manifest, Page page) throws IOException {
        if (page == null || page.file() == null || !page.file().matches("chapters/[0-9]{6}\\.html")) {
            throw new IOException("Invalid saved chapter path");
        }
        Path root = manifest.toAbsolutePath().getParent().toRealPath();
        Path file = root.resolve(page.file()).toRealPath();
        if (!file.startsWith(root) || !Files.isRegularFile(file) || Files.size(file) > 32 * 1024 * 1024) {
            throw new IOException("Invalid saved chapter: " + page.file());
        }
        if (!sha256(Files.readAllBytes(file)).equals(page.sha256())) {
            throw new IOException("Saved chapter was changed: " + file);
        }
        return file;
    }

    public List<Path> orderedFiles(Path manifest) throws IOException {
        return orderedFiles(manifest, () -> false);
    }

    public List<Path> orderedFiles(Path manifest, java.util.function.BooleanSupplier cancelled) throws IOException {
        var files = new java.util.ArrayList<Path>();
        var names = new java.util.HashSet<String>();
        for (Page page : pages) {
            com.myskdias.ebookgenerator.parser.BookImporter.checkCancelled(cancelled);
            if (!names.add(page.file())) throw new IOException("Duplicate saved chapter path: " + page.file());
            files.add(pageFile(manifest, page));
        }
        if (files.isEmpty()) throw new IOException("This download has no saved chapters yet");
        return List.copyOf(files);
    }

    public static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
