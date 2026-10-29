package com.myskdias.ebookgenerator.parser;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlSourceTest {
    @TempDir Path temp;

    @Test void preservesUtf8SupplementaryCharactersAndEntities() throws Exception {
        var source = HtmlSource.read(Path.of("src/test/resources/chapters/novelfull-sample.html"));
        assertEquals(StandardCharsets.UTF_8, source.charset());
        assertTrue(source.html().contains("Eyes..𝘯𝘵"));
        assertEquals(0, source.replacementCharacters());
        var profile = new ParserProfile("NovelFull sample", "novelfull.net", "span.chapter-text",
            "#chapter-content", "#next_chap", java.util.List.of(".ads"));
        var result = new ProfiledHtmlParser().parse(source.html(), temp.toUri(), profile);
        assertTrue(result.chapter().content().contains("Café — “quotes”, & entities, 中文 and 😀."));
        assertTrue(result.chapter().content().contains("Eyes..𝘯𝘵"));
        assertFalse(result.chapter().content().contains("Advertisement"));
        assertTrue(result.chapter().content().contains("Translator note:"));
    }

    @Test void honorsLegacyHtmlCharsetInBothImporters() throws Exception {
        Path path = temp.resolve("legacy.html");
        Files.writeString(path, "<meta http-equiv='Content-Type' content='text/html;charset=windows-1252'>"
            + "<article><h1>Café</h1><p>“Bonjour” — été.</p></article>", Charset.forName("windows-1252"));
        var source = HtmlSource.read(path);
        assertEquals(Charset.forName("windows-1252"), source.charset());
        assertEquals("“Bonjour” — été.", new HtmlChapterImporter().importFile(path).content());
        var profile = new ParserProfile("Legacy", "example.com", "h1", "article", "");
        assertEquals("“Bonjour” — été.", new ProfiledHtmlParser()
            .parse(source.html(), path.toUri(), profile).chapter().content());
    }

    @Test void bomOverridesConflictingMetadata() throws Exception {
        Path path = temp.resolve("bom.html");
        Files.writeString(path, "\uFEFF<meta charset='windows-1252'><h1>Café 中文</h1>",
            StandardCharsets.UTF_16LE);
        var source = HtmlSource.read(path);
        assertEquals(StandardCharsets.UTF_16, source.charset());
        assertFalse(source.html().startsWith("\uFEFF"));
        assertTrue(source.html().contains("Café 中文"));
    }

    @Test void rejectsMalformedBytesRatherThanInventingReplacementCharacters() throws Exception {
        Path path = temp.resolve("broken.html");
        Files.write(path, new byte[] { '<', 'p', '>', (byte) 0xC3, 0x28 });
        IOException error = assertThrows(IOException.class, () -> HtmlSource.read(path));
        assertTrue(error.getMessage().contains("UTF-8"));
        assertTrue(error.getMessage().contains("invalid bytes were not replaced"));
    }

    @Test void diagnosesLiteralAndEntityReplacementCharactersAlreadyInSource() {
        var source = new HtmlSource("<p>A � B &#xfffd; C &#65533;</p>", StandardCharsets.UTF_8);
        assertEquals(3, source.replacementCharacters());
    }
}
