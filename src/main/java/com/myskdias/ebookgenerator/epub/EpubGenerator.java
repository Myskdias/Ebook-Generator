package com.myskdias.ebookgenerator.epub;

import com.myskdias.ebookgenerator.model.Book;
import com.myskdias.ebookgenerator.model.Chapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a self-contained EPUB 3 publication from plain-text chapters. */
public final class EpubGenerator {

    private static final String MIME_TYPE = "application/epub+zip";
    private static final String CSS = """
        body { font-family: serif; line-height: 1.5; }
        h1 { margin: 1.5em 0 1em; }
        p { margin: 0 0 1em; }
        """;

    public Path generate(Book book, Path output) throws IOException {
        if (book == null || output == null) {
            throw new IllegalArgumentException("Book and output path are required");
        }
        Path destination = output.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(parent, ".ebook-generator-", ".epub");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                writeMimeType(zip);
                add(zip, "META-INF/container.xml", containerXml());
                add(zip, "OEBPS/styles.css", CSS);
                add(zip, "OEBPS/nav.xhtml", navigation(book));
                add(zip, "OEBPS/content.opf", packageDocument(book));
                for (int index = 0; index < book.chapters().size(); index++) {
                    add(zip, "OEBPS/" + chapterFile(index), chapterDocument(book, book.chapters().get(index)));
                }
            }

            try {
                Files.move(temporary, destination,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeMimeType(ZipOutputStream zip) throws IOException {
        byte[] data = MIME_TYPE.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipEntry entry = new ZipEntry("mimetype");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static void add(ZipOutputStream zip, String name, String text) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String containerXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;
    }

    private static String packageDocument(Book book) {
        StringBuilder manifest = new StringBuilder();
        StringBuilder spine = new StringBuilder();
        for (int i = 0; i < book.chapters().size(); i++) {
            String id = "chapter-" + (i + 1);
            manifest.append("    <item id=\"").append(id)
                .append("\" href=\"").append(chapterFile(i))
                .append("\" media-type=\"application/xhtml+xml\"/>\n");
            spine.append("    <itemref idref=\"").append(id).append("\"/>\n");
        }
        String modified = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String identifier = "urn:uuid:" + UUID.randomUUID();
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="pub-id">%s</dc:identifier>
                <dc:title>%s</dc:title>
                <dc:creator>%s</dc:creator>
                <dc:language>%s</dc:language>
                <meta property="dcterms:modified">%s</meta>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="css" href="styles.css" media-type="text/css"/>
            %s  </manifest>
              <spine>
            %s  </spine>
            </package>
            """.formatted(escapeXml(identifier), escapeXml(book.title()), escapeXml(book.author()),
                escapeXml(book.language()), escapeXml(modified), manifest, spine);
    }

    private static String navigation(Book book) {
        StringBuilder links = new StringBuilder();
        for (int i = 0; i < book.chapters().size(); i++) {
            links.append("        <li><a href=\"")
                .append(chapterFile(i)).append("\">")
                .append(escapeXml(book.chapters().get(i).title()))
                .append("</a></li>\n");
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"
                  xmlns:epub="http://www.idpf.org/2007/ops"
                  xml:lang="%s" lang="%s">
              <head>
                <meta charset="utf-8"/>
                <title>Contents</title>
                <link rel="stylesheet" type="text/css" href="styles.css"/>
              </head>
              <body>
                <nav epub:type="toc" id="toc">
                  <h1>Contents</h1>
                  <ol>
            %s      </ol>
                </nav>
              </body>
            </html>
            """.formatted(escapeXml(book.language()), escapeXml(book.language()), links);
    }

    private static String chapterDocument(Book book, Chapter chapter) {
        StringBuilder paragraphs = new StringBuilder();
        for (String paragraph : chapter.content().strip().split("(?:\\R\\s*\\R)+")) {
            if (!paragraph.isBlank()) {
                String content = escapeXml(paragraph.strip()).replace("\r\n", "\n")
                    .replace("\r", "\n").replace("\n", "<br/>");
                paragraphs.append("    <p>").append(content).append("</p>\n");
            }
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="%s" lang="%s">
              <head>
                <meta charset="utf-8"/>
                <title>%s</title>
                <link rel="stylesheet" type="text/css" href="styles.css"/>
              </head>
              <body>
                <h1>%s</h1>
            %s  </body>
            </html>
            """.formatted(escapeXml(book.language()), escapeXml(book.language()),
                escapeXml(chapter.title()), escapeXml(chapter.title()), paragraphs);
    }

    private static String chapterFile(int index) {
        return String.format(Locale.ROOT, "chapter-%04d.xhtml", index + 1);
    }

    /**
     * XML 1.0 escaping, with replacement of characters forbidden by XML.
     * This method never interprets the supplied text as markup.
     */
    static String escapeXml(String input) {
        StringBuilder output = new StringBuilder();
        input.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '&' -> output.append("&amp;");
                case '<' -> output.append("&lt;");
                case '>' -> output.append("&gt;");
                case '"' -> output.append("&quot;");
                case '\'' -> output.append("&apos;");
                default -> {
                    if (codePoint == 9 || codePoint == 10 || codePoint == 13
                        || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                        || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                        || (codePoint >= 0x10000 && codePoint <= 0x10FFFF)) {
                        output.appendCodePoint(codePoint);
                    } else {
                        output.append('\uFFFD');
                    }
                }
            }
        });
        return output.toString();
    }
}
