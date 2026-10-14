package io.github.myskdias.ebookgenerator.model;

import java.util.Objects;

/**
 * A chapter whose content is plain text, with blank lines separating paragraphs.
 * Arbitrary HTML is intentionally not accepted by the EPUB serializer.
 */
public record Chapter(String title, String content) {

    public Chapter {
        Objects.requireNonNull(title, "Chapter title");
        Objects.requireNonNull(content, "Chapter content");
        if (title.isBlank()) {
            throw new IllegalArgumentException("Chapter title must not be blank");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("Chapter content must not be blank");
        }
    }
}
