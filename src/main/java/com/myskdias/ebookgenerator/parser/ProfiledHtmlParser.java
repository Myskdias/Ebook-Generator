package com.myskdias.ebookgenerator.parser;

import com.myskdias.ebookgenerator.model.Chapter;
import java.net.URI;
import java.util.List;
import java.util.Locale;
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
        Element clean = contentElement.clone();
        clean.select("script, style, noscript, iframe, nav, footer, button").remove();

        List<String> paragraphs = clean.select("p").stream()
            .map(Element::text).map(String::strip).filter(s -> !s.isBlank()).toList();

        String text;
        if (paragraphs.isEmpty()) {
            // For sites that use <br> instead of <p>, preserve line boundaries.
            clean.select("br").forEach(br -> br.after("\n"));
            text = clean.wholeText().strip().replaceAll("(?m)[ \t]+$", "")
                .replaceAll("(?:\r?\n[ \t]*){2,}", "\n\n");
        } else {
            text = String.join("\n\n", paragraphs);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                "Content selector matched no readable text: " + profile.contentSelector());
        }

        Optional<URI> next = Optional.empty();
        if (!profile.nextChapterSelector().isBlank()) {
            Element link = document.selectFirst(profile.nextChapterSelector());
            if (link != null) {
                String target = link.hasAttr("href") ? link.attr("href") : link.attr("data-href");
                if (!target.isBlank()) {
                    try {
                        URI resolved = sourceUri.resolve(target.strip()).normalize();
                        String scheme = resolved.getScheme() == null
                            ? "" : resolved.getScheme().toLowerCase(Locale.ROOT);
                        if ((scheme.equals("http") || scheme.equals("https"))
                            && resolved.getHost() != null && !resolved.equals(sourceUri.normalize())) {
                            next = Optional.of(resolved);
                        }
                    } catch (IllegalArgumentException ex) {
                        // A bad link should not prevent the chapter itself from being imported.
                    }
                }
            }
        }
        return new ParsedChapter(new Chapter(titleElement.text().strip(), text), next);
    }
}
