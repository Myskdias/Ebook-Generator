package com.myskdias.ebookgenerator.epub;

import static org.junit.jupiter.api.Assertions.*;

import com.myskdias.ebookgenerator.model.Book;
import com.myskdias.ebookgenerator.model.Chapter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EpubGeneratorTest {

    @TempDir
    Path temp;

    @Test
    void producesAnEpub3WithOrderedChaptersAndValidXml() throws Exception {
        Book book = new Book("A & B", "O'Neil", "en", List.of(
            new Chapter("Chapter <1>", "First & second.\n\nL'été commence."),
            new Chapter("Chapter 2", "A < B and C > D.")
        ));
        Path output = new EpubGenerator().generate(book, temp.resolve("book.epub"));

        try (ZipFile zip = new ZipFile(output.toFile())) {
            ZipEntry first = zip.entries().nextElement();
            assertEquals("mimetype", first.getName());
            assertEquals(ZipEntry.STORED, first.getMethod());
            assertEquals("application/epub+zip", read(zip, "mimetype"));
            assertNotNull(zip.getEntry("META-INF/container.xml"));
            assertNotNull(zip.getEntry("OEBPS/content.opf"));
            assertNotNull(zip.getEntry("OEBPS/nav.xhtml"));
            assertNotNull(zip.getEntry("OEBPS/styles.css"));
            assertNotNull(zip.getEntry("OEBPS/chapter-0001.xhtml"));
            assertNotNull(zip.getEntry("OEBPS/chapter-0002.xhtml"));

            String container = read(zip, "META-INF/container.xml");
            String opf = read(zip, "OEBPS/content.opf");
            String nav = read(zip, "OEBPS/nav.xhtml");
            String chapter = read(zip, "OEBPS/chapter-0001.xhtml");

            assertTrue(container.contains("OEBPS/content.opf"));
            assertTrue(opf.contains("version=\"3.0\""));
            assertTrue(opf.contains("A &amp; B"));
            assertTrue(opf.contains("O&apos;Neil"));
            assertTrue(opf.indexOf("chapter-0001.xhtml") < opf.indexOf("chapter-0002.xhtml"));
            assertTrue(nav.contains("Chapter &lt;1&gt;"));
            assertTrue(chapter.contains("First &amp; second."));
            assertTrue(chapter.contains("L&apos;été commence."));
            assertTrue(chapter.contains("<p>"));
            assertFalse(opf.contains("CoverImage"));

            assertXml(zip, "META-INF/container.xml");
            assertXml(zip, "OEBPS/content.opf");
            assertXml(zip, "OEBPS/nav.xhtml");
            assertXml(zip, "OEBPS/chapter-0001.xhtml");
            assertXml(zip, "OEBPS/chapter-0002.xhtml");
        }
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class,
            () -> new Chapter("", "Content"));
        assertThrows(IllegalArgumentException.class,
            () -> new Book("Novel", "Writer", "en", List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new Book("Novel", "Writer", "invalid_language", List.of(new Chapter("1", "text"))));
    }

    private static String read(ZipFile zip, String entry) throws Exception {
        try (InputStream in = zip.getInputStream(zip.getEntry(entry))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertXml(ZipFile zip, String entry) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream in = zip.getInputStream(zip.getEntry(entry))) {
            assertNotNull(factory.newDocumentBuilder().parse(in).getDocumentElement());
        }
    }
}
