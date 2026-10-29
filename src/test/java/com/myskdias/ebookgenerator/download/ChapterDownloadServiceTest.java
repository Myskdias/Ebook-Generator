package com.myskdias.ebookgenerator.download;

import static org.junit.jupiter.api.Assertions.*;
import com.myskdias.ebookgenerator.parser.BookImporter;
import com.myskdias.ebookgenerator.parser.HtmlSource;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChapterDownloadServiceTest {
    @TempDir Path temp;
    private final URI first = URI.create("https://example.com/book/100");

    private String html(int number, String next) {
        return "<meta charset='windows-1252'><h1>Chapter " + number + "</h1>"
            + "<main><p>Café 😀 𝘯𝘵 " + number + "</p><div class='ads'><p>Advertisement</p></div></main>"
            + "<script>siteCode()</script>" + next;
    }

    @Test void downloadsWithoutAProfileAndPreservesPagesAndTheirOrder() throws Exception {
        var requested = new ArrayList<URI>();
        var service = new ChapterDownloadService((uri, domain) -> {
            requested.add(uri);
            return new BookImporter.Page(uri, uri.equals(first)
                ? html(1, "<a data-nav='next' href='1'>Next</a>") : html(2, ""));
        }, 0);
        var result = service.download(new ChapterDownloadService.Request(first, temp, 0, ""), p -> {}, () -> false);
        assertEquals(List.of(first, URI.create("https://example.com/book/1")), requested);
        var restored = DownloadArchive.load(result.manifest());
        assertEquals(DownloadArchive.State.COMPLETE, restored.state());
        assertEquals(2, restored.pages().size());
        assertEquals("chapters/000001.html", restored.pages().getFirst().file());
        assertEquals("chapters/000002.html", restored.pages().getLast().file());
        var source = HtmlSource.read(restored.orderedFiles(result.manifest()).getFirst());
        assertEquals(StandardCharsets.UTF_8, source.charset());
        assertTrue(source.html().contains("Café 😀 𝘯𝘵"));
        assertTrue(source.html().contains("Advertisement"), "Exclusion belongs to the later profile stage");
        assertTrue(source.html().contains("siteCode()"), "Download does not apply the preview sanitizer");
        assertTrue(source.html().contains("ebook-generator-source-url"));
    }

    @Test void countIncludesFirstAndCanBeExtendedWithoutRedownloadingIt() throws Exception {
        var requested = new ArrayList<URI>();
        var service = new ChapterDownloadService((uri, domain) -> {
            requested.add(uri);
            return new BookImporter.Page(uri, uri.equals(first)
                ? html(1, "<a rel='next' href='/2'>Next</a>") : html(2, ""));
        }, 0);
        service.download(new ChapterDownloadService.Request(first, temp, 1, ""), p -> {}, () -> false);
        assertEquals(List.of(first), requested);
        var result = service.download(new ChapterDownloadService.Request(first, temp, 0, ""), p -> {}, () -> false);
        assertEquals(List.of(first, URI.create("https://example.com/2")), requested);
        assertEquals(2, result.archive().pages().size());
    }

    @Test void cancellationKeepsTheSavedPagesAndResumeSkipsThem() throws Exception {
        var cancelled = new AtomicBoolean();
        var requested = new ArrayList<URI>();
        var service = new ChapterDownloadService((uri, domain) -> {
            requested.add(uri);
            return new BookImporter.Page(uri, uri.equals(first)
                ? html(1, "<a href='/2'>Next Chapter</a>") : html(2, ""));
        }, 0);
        var request = new ChapterDownloadService.Request(first, temp, 0, "");
        assertThrows(CancellationException.class, () -> service.download(request, p -> cancelled.set(true), cancelled::get));
        var saved = DownloadArchive.load(temp.resolve(DownloadArchive.FILE_NAME));
        assertEquals(DownloadArchive.State.CANCELLED, saved.state());
        assertEquals(1, saved.pages().size());
        cancelled.set(false);
        var result = service.download(request, p -> {}, cancelled::get);
        assertEquals(2, result.archive().pages().size());
        assertEquals(List.of(first, URI.create("https://example.com/2")), requested);
    }

    @Test void failedRequestsCanBeRetriedWithoutStartingOver() throws Exception {
        var failure = new AtomicBoolean(true);
        var requested = new ArrayList<URI>();
        var service = new ChapterDownloadService((uri, domain) -> {
            requested.add(uri);
            if (!uri.equals(first) && failure.get()) throw new IOException("HTTP 503");
            return new BookImporter.Page(uri, uri.equals(first)
                ? html(1, "<a id='next_chap' href='/2'>Next</a>") : html(2, ""));
        }, 0);
        var request = new ChapterDownloadService.Request(first, temp, 0, "");
        assertThrows(IOException.class, () -> service.download(request, p -> {}, () -> false));
        assertEquals(DownloadArchive.State.FAILED, DownloadArchive.load(temp.resolve(DownloadArchive.FILE_NAME)).state());
        failure.set(false);
        assertEquals(2, service.download(request, p -> {}, () -> false).archive().pages().size());
        assertEquals(List.of(first, URI.create("https://example.com/2"), URI.create("https://example.com/2")), requested);
    }

    @Test void ambiguousNavigationCanBeCorrectedAfterTheFirstPageWasSaved() throws Exception {
        var service = new ChapterDownloadService((uri, domain) -> new BookImporter.Page(uri,
            uri.equals(first) ? html(1, "<a rel='next' href='/wrong'>Next</a><a id='right' rel='next' href='/2'>Next</a>")
                : html(2, "")), 0);
        assertTrue(assertThrows(IOException.class, () -> service.download(
            new ChapterDownloadService.Request(first, temp, 0, ""), p -> {}, () -> false)).getMessage().contains("Several"));
        var resumed = service.download(new ChapterDownloadService.Request(first, temp, 0, "#right"), p -> {}, () -> false);
        assertEquals(URI.create("https://example.com/2"), resumed.archive().pages().getLast().url());
    }

    @Test void cyclesAndLinksOutsideTheSiteStopAndKeepTheDownload() throws Exception {
        var service = new ChapterDownloadService((uri, domain) -> new BookImporter.Page(uri,
            html(1, "<a rel='next' href='" + first + "'>Next</a>")), 0);
        assertTrue(assertThrows(IOException.class, () -> service.download(
            new ChapterDownloadService.Request(first, temp, 0, ""), p -> {}, () -> false)).getMessage().contains("cycle"));
        assertEquals(1, DownloadArchive.load(temp.resolve(DownloadArchive.FILE_NAME)).pages().size());
        assertThrows(IllegalArgumentException.class, () -> new ChapterDownloadService.Request(URI.create("http:/bad"), temp, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new ChapterDownloadService.Request(URI.create("file:///local"), temp, 0, ""));
    }

    @Test void refusesUnrelatedNonemptyFoldersAndChangedSavedChapters() throws Exception {
        Path unrelated = Files.writeString(temp.resolve("keep.txt"), "Keep me");
        var service = new ChapterDownloadService((uri, domain) -> new BookImporter.Page(uri, html(1, "")), 0);
        assertThrows(IOException.class, () -> service.download(new ChapterDownloadService.Request(first, temp, 1, ""), p -> {}, () -> false));
        assertEquals("Keep me", Files.readString(unrelated));
        Path folder = Files.createDirectory(temp.resolve("book"));
        var result = service.download(new ChapterDownloadService.Request(first, folder, 1, ""), p -> {}, () -> false);
        Path page = result.archive().orderedFiles(result.manifest()).getFirst();
        Files.writeString(page, "Changed");
        assertTrue(assertThrows(IOException.class, () -> result.archive().orderedFiles(result.manifest())).getMessage().contains("changed"));
    }

    @Test void manifestCannotReferenceFilesOutsideItsDownloadFolder() throws Exception {
        Path manifest = temp.resolve(DownloadArchive.FILE_NAME);
        var archive = new DownloadArchive(1, first, 1, "", DownloadArchive.State.COMPLETE, "",
            List.of(new DownloadArchive.Page(first, "../secret.html", "unused")));
        archive.save(manifest);
        assertThrows(IOException.class, () -> DownloadArchive.load(manifest).orderedFiles(manifest));
    }
}
