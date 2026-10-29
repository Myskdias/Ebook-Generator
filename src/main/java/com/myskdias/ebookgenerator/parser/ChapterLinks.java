package com.myskdias.ebookgenerator.parser;

import java.net.URI;
import java.util.Optional;
import org.jsoup.nodes.Document;

/** Shared navigation for previews and sequential imports; base tags never override the source. */
final class ChapterLinks {
    private ChapterLinks() { }

    static Optional<URI> next(Document document, URI source, String selector, boolean strict) {
        if (selector.isBlank()) return Optional.empty();
        var link = document.selectFirst(selector);
        if (link == null || link.closest("[disabled], [aria-disabled=true], .disabled") != null) return Optional.empty();
        String target = (link.hasAttr("href") ? link.attr("href") : link.attr("data-href")).strip();
        if (target.isEmpty() || target.startsWith("#")) return Optional.empty();
        try {
            URI resolved = withoutFragment(source.resolve(target.replace(" ", "%20")).normalize());
            boolean web = "https".equalsIgnoreCase(resolved.getScheme()) || "http".equalsIgnoreCase(resolved.getScheme());
            boolean local = "file".equalsIgnoreCase(source.getScheme())
                && "file".equalsIgnoreCase(resolved.getScheme()) && resolved.getRawAuthority() == null;
            if ((!web && !local) || resolved.getUserInfo() != null || (web && resolved.getHost() == null)) {
                throw new IllegalArgumentException("Unsupported next-chapter link: " + target);
            }
            if (local) resolved = URI.create(resolved.toString().split("\\?", 2)[0]);
            if (!strict && resolved.equals(withoutFragment(source.normalize()))) return Optional.empty();
            return Optional.of(resolved);
        } catch (IllegalArgumentException ex) {
            if (strict) throw new IllegalArgumentException("Invalid next-chapter link: " + target, ex);
            return Optional.empty();
        }
    }

    static URI withoutFragment(URI uri) {
        String value = uri.toString();
        int hash = value.indexOf('#');
        return hash < 0 ? uri : URI.create(value.substring(0, hash));
    }
}
