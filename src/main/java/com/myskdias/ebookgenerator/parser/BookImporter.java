package com.myskdias.ebookgenerator.parser;

import com.myskdias.ebookgenerator.model.Chapter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Follows only the profile's next link, preserving chapter order and avoiding partial success on errors. */
public final class BookImporter {
    public static final int MAX_CHAPTERS = 10_000;
    public enum Stop { NO_NEXT_LINK, CHAPTER_LIMIT, SAVED_FILES }
    public record Page(URI uri, String html) { }
    public record Progress(int chapters, String title, URI source) { }
    public record Result(List<Chapter> chapters, Stop stop, String title, String author, String language) {
        public Result { chapters = List.copyOf(chapters); }
    }
    @FunctionalInterface public interface WebReader {
        Page read(URI uri, String domain) throws IOException, InterruptedException;
    }

    private final WebReader webReader;
    private final long delayMillis;

    public BookImporter(WebReader webReader) { this(webReader, 1000); }
    BookImporter(WebReader webReader, long delayMillis) {
        this.webReader = webReader;
        this.delayMillis = delayMillis;
    }

    /** Convert an explicit saved-page order, without consulting navigation or using the network. */
    public Result readFiles(List<Path> files, ParserProfile profile, int maxChapters,
                            Consumer<Progress> progress, BooleanSupplier cancelled) throws IOException {
        if (files.isEmpty()) throw new IOException("No downloaded chapters were selected");
        int count = maxChapters == 0 ? files.size() : Math.min(maxChapters, files.size());
        var chapters = new ArrayList<Chapter>();
        String title = "", author = "", language = "en";
        for (int i = 0; i < count; i++) {
            checkCancelled(cancelled);
            Path file = files.get(i);
            try {
                String html = HtmlSource.read(file).html();
                var chapter = new ProfiledHtmlParser().parse(html, file.toUri(), profile).chapter();
                chapters.add(chapter);
                if (i == 0) {
                    var doc = Jsoup.parse(html);
                    title = metadata(doc, "og:novel:book_name");
                    if (title.isBlank()) title = chapter.title();
                    author = metadata(doc, "og:novel:author");
                    String lang = doc.selectFirst("html").attr("lang");
                    if (lang.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")) language = lang;
                }
                progress.accept(new Progress(i + 1, chapter.title(), file.toUri()));
            } catch (IOException | IllegalArgumentException ex) {
                throw new IOException("Saved chapter " + (i + 1) + " at " + file + ": " + ex.getMessage(), ex);
            }
        }
        checkCancelled(cancelled);
        return new Result(chapters, count < files.size() ? Stop.CHAPTER_LIMIT : Stop.SAVED_FILES, title, author, language);
    }

    /** maxChapters == 0 follows the chain until no next link is found. The first chapter is included. */
    public Result read(URI first, ParserProfile profile, int maxChapters, Consumer<Progress> progress,
                       BooleanSupplier cancelled) throws IOException, InterruptedException {
        if (maxChapters < 0 || maxChapters > MAX_CHAPTERS) throw new IllegalArgumentException("Invalid chapter count");
        if (maxChapters != 1 && profile.nextChapterSelector().isBlank()) {
            throw new IllegalArgumentException("Select a next-chapter link in the profile before importing a book");
        }
        URI current = ChapterLinks.withoutFragment(first.normalize());
        boolean local = "file".equalsIgnoreCase(current.getScheme());
        if (local) current = URI.create(current.toString().split("\\?", 2)[0]);
        Path root = local ? Path.of(current).toRealPath().getParent() : null;
        var visited = new HashSet<URI>();
        var contents = new HashSet<String>();
        var chapters = new ArrayList<Chapter>();
        String bookTitle = "", author = "", language = "";
        while (true) {
            checkCancelled(cancelled);
            int chapterNumber = chapters.size() + 1;
            URI requested = current;
            try {
                if (local) {
                    if (!"file".equalsIgnoreCase(current.getScheme()) || current.getRawAuthority() != null) {
                        throw new IOException("A local chapter points to a website. Download the chapters in step 1 first");
                    }
                    Path file = Path.of(current).toRealPath();
                    if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                        throw new IOException("Next chapter is outside the first chapter's folder: " + file);
                    }
                    current = file.toUri();
                } else {
                    HttpPageFetcher.requireSite(current, profile.domain());
                }
                if (visited.contains(current)) throw new IOException("Next-chapter cycle detected at " + current);
                if (!local && !chapters.isEmpty() && delayMillis > 0) Thread.sleep(delayMillis);
                checkCancelled(cancelled);
                Page page = local ? new Page(current, HtmlSource.read(Path.of(current)).html())
                    : webReader.read(current, profile.domain());
                URI actual = ChapterLinks.withoutFragment(page.uri().normalize());
                if (!local) HttpPageFetcher.requireSite(actual, profile.domain());
                if (visited.contains(actual)) throw new IOException("Redirect returns to an already imported chapter");
                visited.add(current);
                visited.add(actual);
                checkCancelled(cancelled);
                var parsed = new ProfiledHtmlParser().parse(page.html(), actual, profile);
                if (!contents.add(fingerprint(parsed.chapter()))) throw new IOException("The same chapter was returned twice");
                chapters.add(parsed.chapter());
                Document document = Jsoup.parse(page.html());
                if (chapters.size() == 1) {
                    bookTitle = metadata(document, "og:novel:book_name");
                    if (bookTitle.isBlank()) bookTitle = parsed.chapter().title();
                    author = metadata(document, "og:novel:author");
                    language = document.selectFirst("html").attr("lang");
                    if (!language.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")) language = "en";
                }
                progress.accept(new Progress(chapters.size(), parsed.chapter().title(), actual));
                checkCancelled(cancelled);
                if (maxChapters > 0 && chapters.size() >= maxChapters) {
                    return new Result(chapters, Stop.CHAPTER_LIMIT, bookTitle, author, language);
                }
                var next = ChapterLinks.next(document, actual, profile.nextChapterSelector(), true);
                if (next.isEmpty()) return new Result(chapters, Stop.NO_NEXT_LINK, bookTitle, author, language);
                if (chapters.size() >= MAX_CHAPTERS) throw new IOException("Import exceeds " + MAX_CHAPTERS + " chapters");
                current = next.orElseThrow();
            } catch (IOException | IllegalArgumentException ex) {
                throw new IOException("Chapter " + chapterNumber + " at " + requested + ": " + ex.getMessage(), ex);
            }
        }
    }

    private static String metadata(Document document, String property) {
        var meta = document.selectFirst("meta[property='" + property + "']");
        return meta == null ? "" : meta.attr("content").strip();
    }

    private static String fingerprint(Chapter chapter) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((chapter.title() + "\u0000" + chapter.content()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Generation cancelled");
        }
    }
}
