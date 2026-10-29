package com.myskdias.ebookgenerator;

import static org.junit.jupiter.api.Assertions.*;
import com.myskdias.ebookgenerator.parser.ParserProfile;
import com.myskdias.ebookgenerator.parser.ParserProfileStore;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfiledCliTest {
    @TempDir Path temp;

    @Test void createsEpubFromLocalHtmlWithJsonProfile() throws Exception {
        Path output = temp.resolve("chapter-test.epub");
        Main.main(new String[] {
            "--title", "A Novel", "--author", "Author",
            "--profile", "src/test/resources/profiles/article.json",
            "--output", output.toString(),
            "src/test/resources/chapters/chapter-01.html",
            "src/test/resources/chapters/chapter-02.html"
        });
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertNotNull(zip.getEntry("OEBPS/chapter-0001.xhtml"));
            assertNotNull(zip.getEntry("OEBPS/chapter-0002.xhtml"));
        }
    }

    @Test void profiledCliPreservesDecodedUnicodeAndAppliesPersistedExclusions() throws Exception {
        Path legacy = temp.resolve("legacy.html");
        Files.writeString(legacy, "<meta charset='windows-1252'><span class='chapter-text'>Café</span>"
            + "<div id='chapter-content'><p>“Bonjour” — été.</p>"
            + "<div class='ads'><p>Advertisement three</p></div></div>", Charset.forName("windows-1252"));
        Path profile = temp.resolve("profile.json");
        new ParserProfileStore().save(profile, new ParserProfile("Sample", "example.com",
            ".chapter-text", "#chapter-content", "", List.of(".ads")));
        Path output = temp.resolve("unicode.epub");
        Main.main(new String[] {"--title", "Unicode", "--author", "Test",
            "--profile", profile.toString(), "--output", output.toString(),
            "src/test/resources/chapters/novelfull-sample.html", legacy.toString()});
        try (ZipFile zip = new ZipFile(output.toFile())) {
            String first = new String(zip.getInputStream(zip.getEntry("OEBPS/chapter-0001.xhtml"))
                .readAllBytes(), StandardCharsets.UTF_8);
            String second = new String(zip.getInputStream(zip.getEntry("OEBPS/chapter-0002.xhtml"))
                .readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(first.contains("Eyes..𝘯𝘵"));
            assertTrue(first.contains("中文 and 😀"));
            assertTrue(first.contains("Translator note:"));
            assertTrue(second.contains("“Bonjour” — été."));
            assertFalse(first.contains("Advertisement"));
            assertFalse(second.contains("Advertisement"));
        }
    }
}
