package com.myskdias.ebookgenerator;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
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
}
