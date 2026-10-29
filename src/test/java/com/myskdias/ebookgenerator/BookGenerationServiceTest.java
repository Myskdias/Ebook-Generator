package com.myskdias.ebookgenerator;

import static org.junit.jupiter.api.Assertions.*;
import com.myskdias.ebookgenerator.parser.ParserProfile;
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
}
