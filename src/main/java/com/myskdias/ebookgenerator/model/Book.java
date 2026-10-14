package com.myskdias.ebookgenerator.model;

import java.util.List;
import java.util.Objects;

/** Immutable book metadata and its ordered chapters. */
public record Book(String title, String author, String language, List<Chapter> chapters) {

    public Book {
        Objects.requireNonNull(title, "Book title");
        Objects.requireNonNull(author, "Book author");
        Objects.requireNonNull(language, "Book language");
        Objects.requireNonNull(chapters, "Book chapters");

        if (title.isBlank() || author.isBlank() || language.isBlank()) {
            throw new IllegalArgumentException("Title, author and language must not be blank");
        }
        if (!language.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")) {
            throw new IllegalArgumentException("Invalid BCP 47 language tag: " + language);
        }
        chapters = List.copyOf(chapters);
        if (chapters.isEmpty()) {
            throw new IllegalArgumentException("A book must contain at least one chapter");
        }
    }
}
