package com.myskdias.ebookgenerator;

import com.myskdias.ebookgenerator.epub.EpubGenerator;
import com.myskdias.ebookgenerator.model.Book;
import com.myskdias.ebookgenerator.parser.BookImporter;
import com.myskdias.ebookgenerator.download.DownloadArchive;
import com.myskdias.ebookgenerator.parser.ParserProfile;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Import the requested sequence completely before atomically replacing the destination EPUB. */
public final class BookGenerationService {
    public record Request(URI firstChapter, ParserProfile profile, int maxChapters,
                          String title, String author, String language, Path output, Path archive) {
        public Request(URI firstChapter, ParserProfile profile, int maxChapters, String title,
                       String author, String language, Path output) {
            this(firstChapter, profile, maxChapters, title, author, language, output, null);
        }

        public static Request fromArchive(Path manifest, ParserProfile profile, int maxChapters,
                                          String title, String author, String language, Path output) throws IOException {
            var archive = DownloadArchive.load(manifest);
            if (archive.pages().isEmpty()) throw new IOException("No chapters have been downloaded yet");
            Path first = archive.pageFile(manifest, archive.pages().getFirst());
            return new Request(first.toUri(), profile, maxChapters, title, author, language, output,
                manifest.toAbsolutePath());
        }
        public Request {
            Objects.requireNonNull(firstChapter, "First chapter");
            Objects.requireNonNull(profile, "Parser profile");
            Objects.requireNonNull(output, "EPUB destination");
            if (!"file".equalsIgnoreCase(firstChapter.getScheme()) || firstChapter.getRawAuthority() != null) {
                throw new IllegalArgumentException("Download chapters in step 1 before generating the EPUB");
            }
            title = title == null ? "" : title.strip();
            author = author == null ? "" : author.strip();
            language = language == null ? "" : language.strip();
            if (!language.isEmpty() && !language.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")) {
                throw new IllegalArgumentException("Invalid language tag: " + language);
            }
            if (maxChapters < 0 || maxChapters > BookImporter.MAX_CHAPTERS) {
                throw new IllegalArgumentException("Chapter count must be 1 to " + BookImporter.MAX_CHAPTERS);
            }
            if (archive == null && maxChapters != 1 && profile.nextChapterSelector().isBlank()) {
                throw new IllegalArgumentException("The profile needs a next-chapter selector to import a book");
            }
            if (!output.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".epub")) {
                throw new IllegalArgumentException("The output file must end in .epub");
            }
        }
    }
    public record Result(Path output, int chapters, BookImporter.Stop stop, String title) { }

    public Result generate(Request request, Consumer<BookImporter.Progress> progress,
                           BooleanSupplier cancelled) throws IOException, InterruptedException {
        return generate(request, new BookImporter((uri, domain) -> {
            throw new IOException("EPUB generation only reads downloaded files");
        }), progress, cancelled);
    }

    public Result generate(Request request, BookImporter importer, Consumer<BookImporter.Progress> progress,
                           BooleanSupplier cancelled) throws IOException, InterruptedException {
        BookImporter.checkCancelled(cancelled);
        var imported = request.archive() == null
            ? importer.read(request.firstChapter(), request.profile(), request.maxChapters(), progress, cancelled)
            : importer.readFiles(DownloadArchive.load(request.archive()).orderedFiles(request.archive(), cancelled),
                request.profile(), request.maxChapters(), progress, cancelled);
        String title = request.title().isBlank() ? imported.title() : request.title();
        String author = request.author().isBlank() ? imported.author() : request.author();
        if (author.isBlank()) author = "Unknown";
        String language = request.language().isBlank() ? imported.language() : request.language();
        BookImporter.checkCancelled(cancelled);
        var book = new Book(title, author, language, imported.chapters());
        Path path = new EpubGenerator().generate(book, request.output(), cancelled);
        return new Result(path, imported.chapters().size(), imported.stop(), title);
    }
}
