package com.myskdias.ebookgenerator.desktop;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkedCssLoaderTest {
    @TempDir Path temp;

    @Test void detectsOriginalUrlFromHttrackAndCanonicalMetadata() {
        assertEquals(URI.create("https://novelfull.net/book/chapter.html"), LinkedCssLoader.originalPage(
            "<!-- Mirrored from novelfull.net/book/chapter.html by HTTrack Website Copier -->").orElseThrow());
        assertEquals(URI.create("https://example.com/book/1"), LinkedCssLoader.originalPage(
            "<link rel='canonical' href='https://example.com/book/1'>").orElseThrow());
        assertTrue(LinkedCssLoader.originalPage("<link rel='canonical' href='file:///secret'>").isEmpty());
    }

    @Test void loadsSavedPageCssLocallyAndPreservesLinkedOrder() throws Exception {
        Path folder = Files.createDirectory(temp.resolve("Chapter_files"));
        Files.writeString(folder.resolve("site style.css"), "/* saved style */ p {color:red;background:url(x)}");
        var loader = new LinkedCssLoader(uri -> { throw new AssertionError("Should use local CSS"); });
        var result = loader.load("<link rel='stylesheet' href='Chapter_files/site%20style.css?v=1'>",
            temp.resolve("chapter.html"), null);
        assertEquals("p{color:red;}", result.styles().get("Chapter_files/site%20style.css?v=1"));
        assertTrue(result.messages().isEmpty());
    }

    @Test void resolvesWebLinksAgainstOriginalUrlAndFetchesOnlyStylesheetsOnce() throws Exception {
        var requested = new ArrayList<URI>();
        var loader = new LinkedCssLoader(uri -> { requested.add(uri); return "p{color:blue}"; });
        var result = loader.load("""
            <link rel="stylesheet" href="/web/style.css?v=1">
            <link rel="stylesheet" href="/web/style.css?v=1">
            <link rel="stylesheet" href="../custom.css">
            <link rel="stylesheet" href="//cdn.example.com/theme.css">
            <link rel="icon" href="icon.ico"><script src="app.js"></script>
            <link rel="stylesheet" href="disabled.css" disabled>
            """, temp.resolve("chapter.html"), URI.create("https://example.com/book/1.html"));
        assertEquals(List.of(URI.create("https://example.com/web/style.css?v=1"),
            URI.create("https://example.com/custom.css"), URI.create("https://cdn.example.com/theme.css")), requested);
        assertEquals(3, result.styles().size());
    }

    @Test void reportsMissingFilesAndHttpFailuresInsteadOfSilentlyDroppingCss() throws Exception {
        var loader = new LinkedCssLoader(uri -> { throw new IOException("HTTP 403"); });
        var missing = loader.load("<link rel='stylesheet' href='missing.css'>", temp.resolve("1.html"), null);
        assertTrue(missing.messages().getFirst().contains("original page URL"));
        var failure = loader.load("<link rel='stylesheet' href='https://example.com/x.css'>",
            temp.resolve("1.html"), null);
        assertTrue(failure.summary().contains("HTTP 403"));
        assertTrue(failure.styles().isEmpty());
    }

    @Test void blocksFileUrlsDirectoryTraversalAndPrivateNetworkResources() throws Exception {
        Files.writeString(temp.resolve("outside.css"), "p{color:red}");
        Path pages = Files.createDirectory(temp.resolve("pages"));
        var loader = new LinkedCssLoader();
        var result = loader.load("<link rel='stylesheet' href='../outside.css'>"
            + "<link rel='stylesheet' href='file:///secret.css'>"
            + "<link rel='stylesheet' href='http://127.0.0.1/private.css'>",
            pages.resolve("1.html"), null);
        assertTrue(result.styles().isEmpty());
        assertEquals(3, result.messages().size());
        assertTrue(result.summary().contains("private network address blocked"));
        for (String ip : List.of("0.0.0.0", "127.0.0.1", "192.168.1.5", "169.254.1.1", "::1", "fc00::1")) {
            assertFalse(LinkedCssLoader.publicAddress(InetAddress.getByName(ip)), ip);
        }
    }

    @Test void limitsLocalStylesheetSizeAndTotalSheetCount() throws Exception {
        Files.writeString(temp.resolve("huge.css"), " ".repeat(LinkedCssLoader.MAX_BYTES + 1));
        var loader = new LinkedCssLoader(uri -> "p{color:red}");
        var tooLarge = loader.load("<link rel='stylesheet' href='huge.css'>", temp.resolve("1.html"), null);
        assertTrue(tooLarge.summary().contains("exceeds 1 MiB"));
        String links = java.util.stream.IntStream.range(0, 14)
            .mapToObj(i -> "<link rel='stylesheet' href='https://example.com/" + i + ".css'>")
            .collect(java.util.stream.Collectors.joining());
        var bounded = loader.load(links, temp.resolve("1.html"), null);
        assertEquals(12, bounded.styles().size());
        assertTrue(bounded.summary().contains("additional sheets skipped"));
    }
}
