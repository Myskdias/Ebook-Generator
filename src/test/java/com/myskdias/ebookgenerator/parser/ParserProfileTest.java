package com.myskdias.ebookgenerator.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParserProfileTest {
    @TempDir Path temp;
    private static final String HTML = """
        <html><head><title>Not the chapter title</title></head><body>
        <header><a href="/">Home</a></header>
        <h2 class="chapter-title">Chapter 17: A &amp; B</h2>
        <main id="reader"><p>First &amp; second.</p>
        <p>Her <em>final</em> words.</p><script>bad()</script></main>
        <a class="next" href="../chapter-18.html">Next</a>
        </body></html>
        """;

    @Test void selectsTitleBodyAndResolvesNextLink() {
        var profile = new ParserProfile("Example", "EXAMPLE.COM", "h2.chapter-title",
            "main#reader", "a.next");
        var result = new ProfiledHtmlParser().parse(HTML,
            URI.create("https://example.com/novel/part/chapter-17.html"), profile);
        assertEquals("example.com", profile.domain());
        assertEquals("Chapter 17: A & B", result.chapter().title());
        assertEquals("First & second.\n\nHer final words.", result.chapter().content());
        assertEquals(URI.create("https://example.com/novel/chapter-18.html"),
            result.nextChapterUri().orElseThrow());
    }

    @Test void missingNextLinkIsNotAnError() {
        var profile = new ParserProfile("Example", "example.com", "h2", "main", "a.missing");
        var result = new ProfiledHtmlParser().parse(HTML,
            URI.create("https://example.com/17"), profile);
        assertTrue(result.nextChapterUri().isEmpty());
    }

    @Test void rejectsUnsafeNavigationAndMissingContent() {
        var profile = new ParserProfile("Example", "example.com", "h2", "main", "a.next");
        String attack = HTML.replace("../chapter-18.html", "javascript:alert(1)");
        var result = new ProfiledHtmlParser().parse(attack,
            URI.create("https://example.com/17"), profile);
        assertTrue(result.nextChapterUri().isEmpty());

        var missing = new ParserProfile("Example", "example.com", "h2", ".not-here", "a.next");
        assertThrows(IllegalArgumentException.class, () ->
            new ProfiledHtmlParser().parse(HTML, URI.create("https://example.com/17"), missing));
        assertThrows(IllegalArgumentException.class, () ->
            new ParserProfile("Example", "example.com", "h2[", "main", ""));
    }

    @Test void storesAndLoadsProfileWithoutRecompilation() throws Exception {
        var profile = new ParserProfile("Example Site", "example.com",
            "h2.chapter-title", "main#reader", "a.next");
        Path path = temp.resolve("profiles/example.json");
        var store = new ParserProfileStore();
        store.save(path, profile);
        assertEquals(profile, store.load(path));
        assertTrue(java.nio.file.Files.readString(path).contains("\"contentSelector\""));
    }

    @Test void readsParagraphFreeHtml() {
        var profile = new ParserProfile("Site", "example.com", "h1", ".body", "");
        String html = "<h1>Chapter 1</h1><div class='body'>Hello<br>World</div>";
        var output = new ProfiledHtmlParser().parse(html, URI.create("https://example.com/1"), profile);
        assertTrue(output.chapter().content().contains("Hello"));
        assertTrue(output.chapter().content().contains("World"));
        assertTrue(output.nextChapterUri().isEmpty());
    }
}
