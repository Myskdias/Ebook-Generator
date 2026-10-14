package com.myskdias.ebookgenerator.parser;

import java.net.IDN;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.select.Selector;

/**
 * Reusable extraction rules for a website.
 * Selectors are CSS selectors used by Jsoup and, later, by the visual editor.
 */
public record ParserProfile(
    String name,
    String domain,
    String titleSelector,
    String contentSelector,
    String nextChapterSelector
) {
    public ParserProfile {
        name = requireNonBlank(name, "Profile name");
        domain = normalizedDomain(domain);
        titleSelector = checkedSelector(titleSelector, "Title selector", true);
        contentSelector = checkedSelector(contentSelector, "Content selector", true);
        // A local HTML import can work without a next-chapter link.
        nextChapterSelector = checkedSelector(nextChapterSelector, "Next-chapter selector", false);
    }

    private static String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.strip();
    }

    private static String normalizedDomain(String value) {
        String domain = requireNonBlank(value, "Domain").toLowerCase(Locale.ROOT);
        try {
            domain = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid domain: " + domain, ex);
        }
        if (domain.length() > 253 || domain.startsWith(".") || domain.endsWith(".")
            || domain.contains("..") || domain.indexOf('.') < 0) {
            throw new IllegalArgumentException("Expected a domain name, e.g. example.com");
        }
        return domain;
    }

    private static String checkedSelector(String input, String label, boolean required) {
        if (input == null || input.isBlank()) {
            if (required) {
                throw new IllegalArgumentException(label + " must not be blank");
            }
            return "";
        }
        String selector = input.strip();
        try {
            // Parse the selector before the profile is persisted; it need not match this dummy page.
            Selector.select(selector, Jsoup.parseBodyFragment("<div></div>"));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid " + label.toLowerCase(Locale.ROOT)
                + ": " + selector, ex);
        }
        return selector;
    }
}
