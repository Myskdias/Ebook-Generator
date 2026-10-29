package com.myskdias.ebookgenerator.desktop;

import java.util.Locale;
import java.util.Set;

/** Conservative presentation CSS. URLs, imports, escapes and variables never pass through. */
final class PreviewCss {
    private static final Set<String> PROPERTIES = Set.of(
        "color", "background", "background-color", "font", "font-family", "font-size", "font-style",
        "font-weight", "font-variant", "line-height", "letter-spacing", "word-spacing",
        "text-align", "text-decoration", "text-indent", "text-transform", "white-space",
        "word-break", "overflow-wrap", "vertical-align", "display", "box-sizing",
        "width", "min-width", "max-width", "height", "min-height", "max-height",
        "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
        "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
        "border", "border-width", "border-style", "border-color", "border-radius",
        "border-collapse", "border-spacing", "list-style-type", "list-style-position",
        "float", "clear", "position", "top", "right", "bottom", "left", "z-index",
        "overflow", "overflow-x", "overflow-y", "visibility", "opacity", "box-shadow",
        "text-shadow", "border-top", "border-right", "border-bottom", "border-left",
        "outline", "outline-offset", "table-layout", "caption-side",
        "flex", "flex-direction", "flex-wrap", "flex-grow", "flex-shrink", "flex-basis",
        "align-items", "align-content", "align-self", "justify-content", "gap", "order"
    );

    private PreviewCss() { }

    static String declarations(String css) {
        css = withoutComments(css);
        StringBuilder safe = new StringBuilder();
        for (String declaration : css.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon < 0) continue;
            String property = declaration.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = declaration.substring(colon + 1).strip();
            boolean important = value.matches("(?is).*!\\s*important\\s*$");
            if (important) value = value.replaceFirst("(?i)!\\s*important\\s*$", "").strip();
            // This deliberately rejects CSS comments/escapes and non-presentation syntax.
            if (!PROPERTIES.contains(property) || value.isEmpty()
                || !value.matches("[a-zA-Z0-9\\s#.,%()'\"+*/\\-]+")) continue;
            String functions = value.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(?:rgba?|hsla?|calc|min|max|clamp)\\(", "(");
            if (functions.matches("(?s).*[a-z_-]\\s*\\(.*")) continue;
            safe.append(property).append(':').append(value);
            if (important) safe.append("!important");
            safe.append(';');
        }
        return safe.toString();
    }

    static String stylesheet(String css) {
        return stylesheet(withoutComments(css), 0);
    }

    static boolean safeMedia(String media) {
        return !media.isBlank() && media.matches("[a-zA-Z0-9\\s:().,%\\-]+");
    }

    private static String withoutComments(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static String stylesheet(String css, int nesting) {
        if (nesting > 4) return "";
        StringBuilder safe = new StringBuilder();
        int offset = 0;
        while (offset < css.length()) {
            int open = css.indexOf('{', offset);
            int semicolon = css.indexOf(';', offset);
            if (css.substring(offset).stripLeading().startsWith("@")
                && semicolon >= 0 && (open < 0 || semicolon < open)) {
                // Skip @charset and @import without losing the following ordinary rule.
                offset = semicolon + 1;
                continue;
            }
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
            if (selector.toLowerCase(Locale.ROOT).startsWith("@media ")
                && safeMedia(selector.substring(7))) {
                String rules = stylesheet(body, nesting + 1);
                if (!rules.isEmpty()) safe.append(selector).append('{').append(rules).append('}');
            } else if (selector.matches("[a-zA-Z0-9_#.*\\s:>+~,\\[\\]()='\"|^$\\-]+")
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
