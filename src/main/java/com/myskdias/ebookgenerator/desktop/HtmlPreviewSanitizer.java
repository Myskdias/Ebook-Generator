package com.myskdias.ebookgenerator.desktop;

import java.util.ArrayList;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Build an offline preview with no scripts, remote images, frames, or external CSS.
 * Parsing always uses the original source file, not this reduced DOM.
 */
public final class HtmlPreviewSanitizer {
    private HtmlPreviewSanitizer() { }

    public static String sanitize(String html) {
        Document document = Jsoup.parse(html);
        document.select("script, iframe, frame, frameset, object, embed, applet, "
                + "link, style, svg, math, picture, img, video, audio, source, track, "
                + "canvas, meta[http-equiv], base").remove();
        for (Element element : document.getAllElements()) {
            for (Attribute attribute : new ArrayList<>(element.attributes().asList())) {
                String key = attribute.getKey().toLowerCase(java.util.Locale.ROOT);
                if (key.startsWith("on") || key.equals("style") || key.equals("srcdoc")) {
                    element.removeAttr(attribute.getKey());
                }
            }
        }
        return document.outerHtml();
    }
}
