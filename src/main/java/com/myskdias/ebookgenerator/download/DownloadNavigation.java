package com.myskdias.ebookgenerator.download;

import com.myskdias.ebookgenerator.parser.HttpPageFetcher;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Navigation discovery happens before, and without, an extraction profile. */
final class DownloadNavigation {
    private DownloadNavigation() { }

    static Optional<URI> next(String html, URI source, String selector, String domain) throws IOException {
        var doc = Jsoup.parse(html);
        java.util.List<Element> candidates;
        if (!selector.isBlank()) {
            try { candidates = doc.select(selector); }
            catch (RuntimeException ex) { throw new IOException("Invalid next-link selector: " + selector, ex); }
        } else {
            candidates = doc.select("a[rel~=next], link[rel~=next], [data-nav=next], "
                + "#next_chap, #next_chapter, #next-chapter, a.next-chapter, a.next_chapter");
            if (candidates.isEmpty()) {
                candidates = doc.select("a[href]").stream().filter(link -> link.text().strip().matches(
                    "(?iu)(?:next(?:\\s+chapter)?|(?:chapitre\\s+)?suivant|下一章|下章)(?:\\s*[»›→>]+)?" )).toList();
            }
        }
        var targets = new LinkedHashSet<URI>();
        for (var link : candidates) {
            if (link.closest("[disabled], [aria-disabled=true], .disabled") != null) continue;
            String href = (link.hasAttr("href") ? link.attr("href") : link.attr("data-href")).strip();
            if (href.isEmpty() || href.startsWith("#")) continue;
            try {
                URI target = withoutFragment(source.resolve(href.replace(" ", "%20")).normalize());
                HttpPageFetcher.requireSite(target, domain);
                targets.add(target);
            } catch (IllegalArgumentException ex) { throw new IOException("Invalid next chapter URL: " + href, ex); }
        }
        if (targets.size() > 1) throw new IOException("Several different next links were found. Enter a more precise next-link selector.");
        return targets.stream().findFirst();
    }

    static URI withoutFragment(URI uri) {
        return URI.create(uri.toString().split("#", 2)[0]);
    }
}
