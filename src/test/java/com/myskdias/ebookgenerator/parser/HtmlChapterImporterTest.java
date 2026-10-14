package com.myskdias.ebookgenerator.parser;

import static org.junit.jupiter.api.Assertions.*;

import com.myskdias.ebookgenerator.model.Chapter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HtmlChapterImporterTest {

    @Test
    void extractsTitleAndParagraphsFromLocalHtml() throws Exception {
        Chapter chapter = new HtmlChapterImporter()
            .importFile(Path.of("src/test/resources/chapters/chapter-01.html"));
        assertEquals("Chapter 1: Arrival", chapter.title());
        assertEquals("The train arrived at dawn.\n\nA new story began.", chapter.content());
    }

    @Test
    void preservesUnicodeText() throws Exception {
        Chapter chapter = new HtmlChapterImporter()
            .importFile(Path.of("src/test/resources/chapters/chapter-02.html"));
        assertEquals("Chapter 2: Café", chapter.title());
        assertTrue(chapter.content().contains("Café"));
    }
}
