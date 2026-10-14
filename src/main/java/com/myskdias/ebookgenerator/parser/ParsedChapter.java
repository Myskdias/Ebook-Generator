package com.myskdias.ebookgenerator.parser;

import com.myskdias.ebookgenerator.model.Chapter;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** A chapter plus the optional next-chapter URL, suitable for a preview or a future downloader. */
public record ParsedChapter(Chapter chapter, Optional<URI> nextChapterUri) {
    public ParsedChapter {
        Objects.requireNonNull(chapter, "chapter");
        Objects.requireNonNull(nextChapterUri, "nextChapterUri");
    }
}
