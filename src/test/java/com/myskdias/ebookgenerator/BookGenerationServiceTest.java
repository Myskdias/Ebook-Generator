package com.myskdias.ebookgenerator;

import static org.junit.jupiter.api.Assertions.*;
import com.myskdias.ebookgenerator.parser.ParserProfile;
import com.myskdias.ebookgenerator.parser.BookImporter;
import com.myskdias.ebookgenerator.download.ChapterDownloadService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BookGenerationServiceTest {
    @TempDir Path temp;
    private final ParserProfile profile = new ParserProfile("Book", "example.com", "h1", "main", ".next", List.of(".ads"));

    private Path sequence() throws IOException {
        Files.writeString(temp.resolve("1.html"), "<meta property='og:novel:book_name' content='A Book'>"
            + "<h1>First</h1><main><p>Café 😀</p><div class='ads'><p>Ad</p></div></main><a class='next' href='2.html'>Next</a>");
        Files.writeString(temp.resolve("2.html"), "<h1>Second</h1><main><p>Last page.</p></main>");
        return temp.resolve("1.html");
    }

    @Test void producesAnOrderedEpubFromOneStartingChapter() throws Exception {
        Path output = temp.resolve("book.epub");
        var request = new BookGenerationService.Request(sequence().toUri(), profile, 0, "", "", "", output);
        var result = new BookGenerationService().generate(request, p -> {}, () -> false);
        assertEquals(2, result.chapters());
        assertEquals("A Book", result.title());
        try (var zip = new ZipFile(output.toFile())) {
            String first = new String(zip.getInputStream(zip.getEntry("OEBPS/chapter-0001.xhtml")).readAllBytes(), StandardCharsets.UTF_8);
            String second = new String(zip.getInputStream(zip.getEntry("OEBPS/chapter-0002.xhtml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(first.contains("Café 😀"));
            assertFalse(first.contains(">Ad<"));
            assertTrue(second.contains("Last page."));
            assertNull(zip.getEntry("OEBPS/chapter-0003.xhtml"));
        }
    }

    @Test void failureOrCancellationLeavesAnExistingEpubUntouched() throws Exception {
        Path first = sequence();
        Path output = Files.writeString(temp.resolve("book.epub"), "Existing book");
        var request = new BookGenerationService.Request(first.toUri(), profile, 0, "Book", "Author", "en", output);
        Files.delete(temp.resolve("2.html"));
        assertThrows(IOException.class, () -> new BookGenerationService().generate(request, p -> {}, () -> false));
        assertEquals("Existing book", Files.readString(output));
        var cancelled = new AtomicBoolean();
        assertThrows(CancellationException.class, () -> new BookGenerationService().generate(request,
            p -> cancelled.set(true), cancelled::get));
        assertEquals("Existing book", Files.readString(output));
    }

    @Test void downloadedPagesCanBeConvertedWithDifferentProfilesWithoutAnyMoreDownloads() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var downloader = new ChapterDownloadService((uri, domain) -> {
            int number = calls.incrementAndGet();
            return new BookImporter.Page(uri, "<h1>Chapter " + number + "</h1><main><p>Café 😀</p>"
                + "<div class='ads'><p>Advertisement</p></div></main>"
                + (number == 1 ? "<a data-nav='next' href='/2'>Next</a>" : ""));
        }, 0);
        Path manifest = downloader.download(new ChapterDownloadService.Request(java.net.URI.create("https://example.com/1"),
            temp.resolve("download"), 0, ""), p -> {}, () -> false).manifest();
        // Create the extraction profiles only after the download has completed. Neither needs navigation.
        var clean = new ParserProfile("Clean", "example.com", "h1", "main", "", List.of(".ads"));
        var full = new ParserProfile("Full", "example.com", "h1", "main", "");
        for (var selected : List.of(clean, full)) {
            Path output = temp.resolve(selected.name() + ".epub");
            var request = BookGenerationService.Request.fromArchive(manifest, selected, 0, "", "", "", output);
            var result = new BookGenerationService().generate(request, p -> {}, () -> false);
            assertEquals(2, result.chapters());
            try (var zip = new ZipFile(output.toFile())) {
                String text = new String(zip.getInputStream(zip.getEntry("OEBPS/chapter-0001.xhtml")).readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(selected == full, text.contains("Advertisement"));
            }
        }
        assertEquals(2, calls.get(), "Changing a profile must never trigger a chapter download");
    }

    @Test void generationRejectsWebUrlsAndRequiresTheSeparateDownloadStage() {
        assertThrows(IllegalArgumentException.class, () -> new BookGenerationService.Request(
            java.net.URI.create("https://example.com/1"), profile, 1, "Book", "Author", "en", temp.resolve("book.epub")));
    }
}
