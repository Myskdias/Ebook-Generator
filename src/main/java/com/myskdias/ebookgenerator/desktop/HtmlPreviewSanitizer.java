package com.myskdias.ebookgenerator.desktop;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Build an offline preview with optional presentation CSS and no external resources.
 * Parsing always uses the original source file, not this reduced DOM.
 */
public final class HtmlPreviewSanitizer {
    static final String SOURCE_SELECTOR = "data-ebook-source-selector";
    private HtmlPreviewSanitizer() { }

    public static String sanitize(String html) {
        return sanitize(html, false);
    }

    public static String sanitize(String html, boolean enableCss) {
        Document document = Jsoup.parse(html);
        // Save selectors before stripping nodes: nth-child indices in the source must stay valid.
        for (Element element : document.getAllElements()) {
            if (element != document) element.attr(SOURCE_SELECTOR, element.cssSelector());
        }
        document.select("script, iframe, frame, frameset, object, embed, applet, "
                + "link, svg, math, picture, img, video, audio, source, track, "
                + "canvas, meta, base, template, noscript").remove();
        for (Element style : document.select("style")) {
            String css = enableCss ? PreviewCss.stylesheet(style.data()) : "";
            if (css.isEmpty()) style.remove();
            else style.text(css);
        }
        for (Element element : document.getAllElements()) {
            for (Attribute attribute : new ArrayList<>(element.attributes().asList())) {
                String key = attribute.getKey().toLowerCase(java.util.Locale.ROOT);
                if (key.equals("style")) {
                    String css = enableCss ? PreviewCss.declarations(attribute.getValue()) : "";
                    if (css.isEmpty()) element.removeAttr(key);
                    else element.attr(key, css);
                } else if (key.startsWith("on") || key.equals("srcdoc")
                    || key.equals("src") || key.equals("srcset") || key.equals("background")
                    || key.equals("poster") || key.equals("action") || key.equals("formaction")
                    || key.equals("ping") || key.equals("manifest") || key.equals("codebase")
                    || key.equals("data") || key.equals("xlink:href")) {
                    element.removeAttr(attribute.getKey());
                }
            }
        }
        document.head().prependElement("meta").attr("charset", "UTF-8");
        document.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy")
            .attr("content", "default-src 'none'; script-src 'none'; style-src 'unsafe-inline'; "
                + "form-action 'none'; base-uri 'none'");
        // JavaFX loadContent corrupts supplementary characters on some platforms (CESU-8).
        // ASCII serialization emits numeric code-point entities, preserving the exact Unicode.
        document.outputSettings().charset(StandardCharsets.US_ASCII).prettyPrint(false);
        return document.outerHtml();
    }
}
