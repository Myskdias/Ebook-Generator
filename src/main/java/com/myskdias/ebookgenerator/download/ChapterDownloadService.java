package com.myskdias.ebookgenerator.download;

import com.myskdias.ebookgenerator.parser.BookImporter;
import com.myskdias.ebookgenerator.parser.HttpPageFetcher;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jsoup.Jsoup;

/** Download HTML to disk first. Extraction and EPUB generation are separate, later operations. */
public final class ChapterDownloadService {
    public record Request(URI firstUrl, Path folder, int maxChapters, String nextSelector) {
        public Request {
            if (firstUrl == null || folder == null || maxChapters < 0 || maxChapters > 10_000) {
                throw new IllegalArgumentException("Choose a web URL, a download folder and a valid chapter count");
            }
            String domain = firstUrl.getHost() == null ? "" : firstUrl.getHost().toLowerCase(Locale.ROOT);
            try { HttpPageFetcher.requireSite(firstUrl, domain); }
            catch (IOException ex) { throw new IllegalArgumentException("Enter a valid HTTP(S) chapter URL", ex); }
            firstUrl = DownloadNavigation.withoutFragment(firstUrl.normalize());
            folder = folder.toAbsolutePath().normalize();
            nextSelector = nextSelector == null ? "" : nextSelector.strip();
        }
    }
    public record Progress(int chapters, URI url, Path file) { }
    public record Result(Path manifest, DownloadArchive archive) { }
    private final BookImporter.WebReader reader;
    private final long delayMillis;

    public ChapterDownloadService() { this(null, 1000); }
    public ChapterDownloadService(BookImporter.WebReader reader, long delayMillis) {
        this.reader = reader;
        this.delayMillis = delayMillis;
    }

    public Result download(Request request, Consumer<Progress> progress, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        if (reader != null) return download(request, progress, cancelled, reader);
        try (var http = new HttpPageFetcher()) { return download(request, progress, cancelled, http::fetch); }
    }

    private Result download(Request request, Consumer<Progress> progress, BooleanSupplier cancelled,
                            BookImporter.WebReader fetch) throws IOException, InterruptedException {
        Files.createDirectories(request.folder());
        Path manifest = request.folder().resolve(DownloadArchive.FILE_NAME);
        var pages = new ArrayList<DownloadArchive.Page>();
        if (Files.exists(manifest)) {
            var previous = DownloadArchive.load(manifest);
            if (!previous.firstUrl().equals(request.firstUrl())) throw new IOException("This folder belongs to a different starting URL");
            for (var page : previous.pages()) {
                BookImporter.checkCancelled(cancelled);
                previous.pageFile(manifest, page);
            }
            pages.addAll(previous.pages());
        } else {
            try (var entries = Files.list(request.folder())) {
                if (entries.findAny().isPresent()) throw new IOException("Choose an empty folder or an existing downloaded-book folder");
            }
        }
        String domain = request.firstUrl().getHost().toLowerCase(Locale.ROOT);
        var visited = new HashSet<URI>();
        pages.forEach(page -> visited.add(page.url()));
        snapshot(request, pages, DownloadArchive.State.DOWNLOADING, "Downloading").save(manifest);
        try {
            while (true) {
                BookImporter.checkCancelled(cancelled);
                if (request.maxChapters() > 0 && pages.size() >= request.maxChapters()) {
                    return completed(request, manifest, pages, "Requested count reached");
                }
                URI next;
                if (pages.isEmpty()) next = request.firstUrl();
                else {
                    var last = pages.getLast();
                    Path file = snapshot(request, pages, DownloadArchive.State.DOWNLOADING, "")
                        .pageFile(manifest, last);
                    var target = DownloadNavigation.next(Files.readString(file, StandardCharsets.UTF_8),
                        last.url(), request.nextSelector(), domain);
                    if (target.isEmpty()) return completed(request, manifest, pages, "No next-chapter link found");
                    next = target.orElseThrow();
                }
                if (pages.size() >= 10_000) throw new IOException("Download exceeds 10,000 chapters");
                if (visited.contains(next)) throw new IOException("Next-chapter cycle detected: " + next);
                if (!pages.isEmpty() && delayMillis > 0) Thread.sleep(delayMillis);
                BookImporter.checkCancelled(cancelled);
                var page = fetch.read(next, domain);
                URI actual = DownloadNavigation.withoutFragment(page.uri().normalize());
                HttpPageFetcher.requireSite(actual, domain);
                if (visited.contains(actual)) throw new IOException("Redirect returns to an already downloaded chapter");
                BookImporter.checkCancelled(cancelled);
                byte[] bytes = savedHtml(page.html(), actual).getBytes(StandardCharsets.UTF_8);
                String filename = String.format(Locale.ROOT, "chapters/%06d.html", pages.size() + 1);
                Path destination = request.folder().resolve(filename);
                Files.createDirectories(destination.getParent());
                Path temp = Files.createTempFile(destination.getParent(), ".chapter-", ".tmp");
                try {
                    Files.write(temp, bytes);
                    Files.move(temp, destination); // Never replace an unrelated existing HTML file.
                } finally { Files.deleteIfExists(temp); }
                pages.add(new DownloadArchive.Page(actual, filename, DownloadArchive.sha256(bytes)));
                visited.add(next);
                visited.add(actual);
                snapshot(request, pages, DownloadArchive.State.DOWNLOADING, "Downloading").save(manifest);
                progress.accept(new Progress(pages.size(), actual, destination));
            }
        } catch (IOException | InterruptedException | CancellationException ex) {
            boolean interrupted = Thread.interrupted();
            boolean wasCancelled = cancelled.getAsBoolean() || ex instanceof InterruptedException || ex instanceof CancellationException;
            try {
                snapshot(request, pages, wasCancelled ? DownloadArchive.State.CANCELLED : DownloadArchive.State.FAILED,
                    ex.getMessage()).save(manifest);
            } catch (IOException saveError) { ex.addSuppressed(saveError); }
            finally { if (interrupted) Thread.currentThread().interrupt(); }
            throw ex;
        }
    }

    private static Result completed(Request request, Path manifest, java.util.List<DownloadArchive.Page> pages,
                                    String reason) throws IOException {
        var archive = snapshot(request, pages, DownloadArchive.State.COMPLETE, reason);
        archive.save(manifest);
        return new Result(manifest, archive);
    }

    private static DownloadArchive snapshot(Request request, java.util.List<DownloadArchive.Page> pages,
                                            DownloadArchive.State state, String message) {
        return new DownloadArchive(1, request.firstUrl(), request.maxChapters(), request.nextSelector(), state, message, pages);
    }

    static String savedHtml(String html, URI source) {
        var doc = Jsoup.parse(html);
        doc.select("meta[charset]").attr("charset", "UTF-8");
        doc.select("meta[http-equiv=content-type]").attr("content", "text/html; charset=UTF-8");
        doc.select("meta[name=ebook-generator-source-url]").remove();
        doc.head().prependElement("meta").attr("name", "ebook-generator-source-url").attr("content", source.toString());
        doc.charset(StandardCharsets.UTF_8);
        doc.outputSettings().prettyPrint(false);
        return doc.outerHtml();
    }
}
