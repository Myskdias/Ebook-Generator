package com.myskdias.ebookgenerator.desktop;

import java.util.Locale;
import java.util.Set;

/** Conservative, presentation-only CSS subset. No URLs, escapes, variables, or at-rules. */
final class PreviewCss {
    private static final Set<String> PROPERTIES = Set.of(
        "color", "background-color", "font", "font-family", "font-size", "font-style",
        "font-weight", "font-variant", "line-height", "letter-spacing", "word-spacing",
        "text-align", "text-decoration", "text-indent", "text-transform", "white-space",
        "word-break", "overflow-wrap", "vertical-align", "display", "box-sizing",
        "width", "min-width", "max-width", "height", "min-height", "max-height",
        "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
        "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
        "border", "border-width", "border-style", "border-color", "border-radius",
        "border-collapse", "border-spacing", "list-style-type", "list-style-position"
    );

    private PreviewCss() { }

    static String declarations(String css) {
        StringBuilder safe = new StringBuilder();
        for (String declaration : css.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon < 0) continue;
            String property = declaration.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = declaration.substring(colon + 1).strip();
            // This deliberately rejects CSS comments/escapes and non-presentation syntax.
            if (!PROPERTIES.contains(property) || value.isEmpty()
                || !value.matches("[a-zA-Z0-9\\s#.,%()'\"+\\-]+")) continue;
            String functions = value.toLowerCase(Locale.ROOT)
                .replaceAll("(?:rgba?|hsla?)\\([^()]*\\)", "");
            if (functions.contains("(") || functions.contains(")")) continue;
            safe.append(property).append(':').append(value).append(';');
        }
        return safe.toString();
    }

    static String stylesheet(String css) {
        StringBuilder safe = new StringBuilder();
        int offset = 0;
        while (offset < css.length()) {
            int open = css.indexOf('{', offset);
            if (open < 0) break;
            int depth = 1;
            int end = open + 1;
            while (end < css.length() && depth > 0) {
                char c = css.charAt(end++);
                if (c == '{') depth++;
                if (c == '}') depth--;
            }
            if (depth != 0) break;
            String selector = css.substring(offset, open).strip();
            String body = css.substring(open + 1, end - 1);
            // Only flat rules with simple CSS selectors. Drop @media, @import, @font-face, etc.
            if (selector.matches("[a-zA-Z0-9_#.*\\s:>+~,\\[\\]()='\"|^$\\-]+")
                && !body.contains("{") && !body.contains("}")) {
                String declarations = declarations(body);
                if (!declarations.isEmpty()) safe.append(selector).append('{')
                    .append(declarations).append('}');
            }
            offset = end;
        }
        return safe.toString();
    }
}
