package com.myskdias.ebookgenerator.parser;

import com.myskdias.ebookgenerator.model.Chapter;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Offline extraction using the same CSS selectors that the visual editor will create.
 * It does not download pages or execute JavaScript.
 */
public final class ProfiledHtmlParser {
    public ParsedChapter parse(String html, URI sourceUri, ParserProfile profile) {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(sourceUri, "sourceUri");
        Objects.requireNonNull(profile, "profile");

        Document document = Jsoup.parse(html, sourceUri.toString());
        Element titleElement = document.selectFirst(profile.titleSelector());
        if (titleElement == null || titleElement.text().isBlank()) {
            throw new IllegalArgumentException(
                "Title selector matched no readable title: " + profile.titleSelector());
        }

        Element contentElement = document.selectFirst(profile.contentSelector());
        if (contentElement == null) {
            throw new IllegalArgumentException(
                "Content selector matched no element: " + profile.contentSelector());
        }

        // Never mutate the original document: the navigation selector may point inside the content.
        Document contentDocument = document.clone();
        Element clean = contentDocument.selectFirst(profile.contentSelector());
        // Resolve all selectors in the complete, unchanged DOM before removing anything.
        // Cloning only the content loses ancestors and breaks contextual/positional selectors.
        List<Element> excluded = profile.excludedSelectors().stream()
            .flatMap(selector -> contentDocument.select(selector).stream()).distinct().toList();
        for (Element element : excluded) {
            if (element == clean || clean.parents().contains(element)) {
                throw new IllegalArgumentException("An excluded selector removes the chapter container");
            }
        }
        excluded.forEach(Element::remove);
        clean.select("script, style, noscript, iframe, nav, footer, button").remove();

        List<String> paragraphs = clean.select("p").stream()
            .map(Element::text).map(String::strip).filter(s -> !s.isBlank()).toList();

        String text;
        if (paragraphs.isEmpty()) {
            // For sites that use <br> instead of <p>, preserve line boundaries.
            // Jsoup wholeText already emits a newline for each <br>.
            text = clean.wholeText().strip().replaceAll("(?m)[ \t]+$", "")
                .replaceAll("(?:\r?\n[ \t]*){2,}", "\n\n");
        } else {
            text = String.join("\n\n", paragraphs);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                "Content selector matched no readable text: " + profile.contentSelector());
        }

        Optional<URI> next = ChapterLinks.next(document, sourceUri, profile.nextChapterSelector(), false);
        return new ParsedChapter(new Chapter(titleElement.text().strip(), text), next);
    }
}
