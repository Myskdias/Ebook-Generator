package com.myskdias.ebookgenerator.parser;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BookImporterTest {
    @TempDir Path temp;
    private final ParserProfile profile = new ParserProfile("Test", "example.com", "h1", "main", ".next", List.of(".ads"));
    private final BookImporter local = new BookImporter((uri, domain) -> { throw new AssertionError("No network for local imports"); }, 0);

    private String html(int number, String next) {
        return "<html lang='fr'><meta property='og:novel:book_name' content='Le livre'>"
            + "<meta property='og:novel:author' content='Auteur'><h1>Chapitre " + number + "</h1>"
            + "<main><p>Café 😀 " + number + ".</p><div class='ads'><p>Advertisement</p></div>"
            + "<p>Translator note.</p></main>" + next + "</html>";
    }

    private Path write(String name, int number, String next) throws IOException {
        return Files.writeString(temp.resolve(name), html(number, next));
    }

    @Test void followsLocalLinksInTheirOrderAndPreservesUnicodeAndExclusions() throws Exception {
        Path first = write("first.html", 1, "<a class='next' href='last%20part.html?cached=1#content'>Next</a>");
        write("last part.html", 2, "<a class='next' data-href='middle.html'>Next</a>");
        write("middle.html", 3, "<a class='next disabled' href='first.html'>Next</a>");
        var progress = new ArrayList<BookImporter.Progress>();
        var result = local.read(first.toUri(), profile, 0, progress::add, () -> false);
        assertEquals(List.of("Chapitre 1", "Chapitre 2", "Chapitre 3"),
            result.chapters().stream().map(c -> c.title()).toList());
        assertEquals(BookImporter.Stop.NO_NEXT_LINK, result.stop());
        assertEquals("Le livre", result.title());
        assertEquals("Auteur", result.author());
        assertEquals("fr", result.language());
        assertEquals(3, progress.size());
        assertEquals(3, progress.getLast().chapters());
        result.chapters().forEach(chapter -> {
            assertTrue(chapter.content().contains("Café 😀"));
            assertTrue(chapter.content().contains("Translator note."));
            assertFalse(chapter.content().contains("Advertisement"));
        });
    }

    @Test void requestedCountIncludesFirstAndDoesNotFetchBeyondTheLimit() throws Exception {
        Path first = write("1.html", 1, "<a class='next' href='2.html'>Next</a>");
        write("2.html", 2, "<a class='next' href='missing.html'>Next</a>");
        var result = local.read(first.toUri(), profile, 2, p -> {}, () -> false);
        assertEquals(2, result.chapters().size());
        assertEquals(BookImporter.Stop.CHAPTER_LIMIT, result.stop());
        assertEquals(1, local.read(first.toUri(), profile, 1, p -> {}, () -> false).chapters().size());
    }

    @Test void reportsAnEarlyEndWhenFewerChaptersAreAvailableThanRequested() throws Exception {
        Path first = write("1.html", 1, "<a class='next' aria-disabled='true' href='2.html'>Next</a>");
        var result = local.read(first.toUri(), profile, 5, p -> {}, () -> false);
        assertEquals(1, result.chapters().size());
        assertEquals(BookImporter.Stop.NO_NEXT_LINK, result.stop());
    }

    @Test void rejectsMissingFilesCyclesUnsafeLinksAndLinksOutsideTheBookFolder() throws Exception {
        Path first = write("1.html", 1, "<a class='next' href='missing.html'>Next</a>");
        assertTrue(assertThrows(IOException.class, () -> local.read(first.toUri(), profile, 0, p -> {}, () -> false))
            .getMessage().contains("missing.html"));
        write("1.html", 1, "<a class='next' href='./1.html?different=1#part'>Next</a>");
        assertTrue(assertThrows(IOException.class, () -> local.read(first.toUri(), profile, 0, p -> {}, () -> false))
            .getMessage().contains("cycle"));
        write("1.html", 1, "<a class='next' href='javascript:evil()'>Next</a>");
        assertTrue(assertThrows(IOException.class, () -> local.read(first.toUri(), profile, 0, p -> {}, () -> false))
            .getMessage().contains("Invalid next-chapter link"));
        Path child = Files.createDirectory(temp.resolve("book"));
        Path inside = Files.writeString(child.resolve("1.html"), html(1, "<a class='next' href='../1.html'>Next</a>"));
        assertTrue(assertThrows(IOException.class, () -> local.read(inside.toUri(), profile, 0, p -> {}, () -> false))
            .getMessage().contains("outside"));
        write("1.html", 1, "<a class='next' href='https://example.com/2'>Next</a>");
        assertTrue(assertThrows(IOException.class, () -> local.read(first.toUri(), profile, 0, p -> {}, () -> false))
            .getMessage().contains("Download the chapters in step 1"));
    }

    @Test void requiresNavigationForASequenceButSupportsASingleChapterProfile() throws Exception {
        Path first = write("1.html", 1, "");
        var single = new ParserProfile("Single", "example.com", "h1", "main", "");
        assertThrows(IllegalArgumentException.class, () -> local.read(first.toUri(), single, 0, p -> {}, () -> false));
        assertEquals(1, local.read(first.toUri(), single, 1, p -> {}, () -> false).chapters().size());
        assertThrows(IllegalArgumentException.class, () -> local.read(first.toUri(), profile, -1, p -> {}, () -> false));
    }

    @Test void stopsBeforeReadingTheNextChapterOnCancellation() throws Exception {
        Path first = write("1.html", 1, "<a class='next' href='missing.html'>Next</a>");
        var cancelled = new AtomicBoolean();
        assertThrows(CancellationException.class, () -> local.read(first.toUri(), profile, 0,
            p -> cancelled.set(true), cancelled::get));
    }

    @Test void followsWebRedirectsAndRejectsDuplicateChaptersAndDomainChanges() throws Exception {
        var requests = new ArrayList<URI>();
        var importer = new BookImporter((uri, domain) -> {
            requests.add(uri);
            return uri.getPath().equals("/1")
                ? new BookImporter.Page(URI.create("https://example.com/book/1"), html(1, "<a class='next' href='2'>Next</a>"))
                : new BookImporter.Page(uri, html(2, ""));
        }, 0);
        var result = importer.read(URI.create("https://example.com/1"), profile, 0, p -> {}, () -> false);
        assertEquals(List.of(URI.create("https://example.com/1"), URI.create("https://example.com/book/2")), requests);
        assertEquals(2, result.chapters().size());
        var duplicate = new BookImporter((uri, domain) -> new BookImporter.Page(uri,
            html(1, "<a class='next' href='/other'>Next</a>")), 0);
        assertTrue(assertThrows(IOException.class, () -> duplicate.read(URI.create("https://example.com/1"),
            profile, 0, p -> {}, () -> false)).getMessage().contains("same chapter"));
        var outside = new BookImporter((uri, domain) -> new BookImporter.Page(uri,
            html(1, "<a class='next' href='https://unrelated.example/2'>Next</a>")), 0);
        assertTrue(assertThrows(IOException.class, () -> outside.read(URI.create("https://example.com/1"),
            profile, 0, p -> {}, () -> false)).getMessage().contains("source domain"));
    }
}
