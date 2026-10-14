package io.github.myskdias.ebookgenerator.parser;

import io.github.myskdias.ebookgenerator.model.Chapter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Minimal local-file importer for the first milestone.
 * Site-specific extraction rules will be implemented separately.
 */
public final class HtmlChapterImporter {

    public Chapter importFile(Path path) throws IOException {
        Document doc = Jsoup.parse(path.toFile(), "UTF-8");

        Element heading = doc.selectFirst("article h1, main h1, h1, article h2, main h2, h2");
        String title = heading != null ? heading.text().strip() : doc.title().strip();
        if (title.isBlank()) {
            String filename = path.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            title = dot > 0 ? filename.substring(0, dot) : filename;
        }

        Element root = doc.selectFirst("article, main, .chapter-content, .chapter-c, body");
        if (root == null) {
            throw new IOException("No readable HTML content in " + path);
        }

        List<String> paragraphs = root.select("p").eachText().stream()
            .map(String::strip)
            .filter(text -> !text.isBlank())
            .toList();

        if (paragraphs.isEmpty()) {
            Element cleaned = root.clone();
            cleaned.select("h1, h2, script, style, nav, footer").remove();
            String text = cleaned.text().strip();
            if (text.isBlank()) {
                throw new IOException("No readable chapter text in " + path);
            }
            paragraphs = List.of(text);
        }

        return new Chapter(title, String.join("\n\n", paragraphs));
    }
}
