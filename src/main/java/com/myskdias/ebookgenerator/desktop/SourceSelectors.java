package com.myskdias.ebookgenerator.desktop;

import java.util.stream.Collectors;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Prefer reusable selectors, using a positional source path only to disambiguate. */
final class SourceSelectors {
    private SourceSelectors() { }

    static String classes(Element element) {
        String classes = element.classNames().stream()
            .filter(name -> name.matches("[A-Za-z_][A-Za-z0-9_-]*"))
            .collect(Collectors.joining("."));
        return classes.isEmpty() ? "" : element.normalName() + "." + classes;
    }

    static String unique(Element element, Document source) {
        if (element.id().matches("[A-Za-z_][A-Za-z0-9_-]*")) {
            String selector = "#" + element.id();
            if (source.select(selector).size() == 1) return selector;
        }
        String classes = classes(element);
        if (!classes.isEmpty() && source.select(classes).size() == 1) return classes;
        return element.cssSelector();
    }
}
