package com.myskdias.ebookgenerator.desktop;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;

/** Fetch only the explicitly linked stylesheets; WebView still has no resource access. */
final class LinkedCssLoader {
    static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_SHEETS = 12;
    private static final Pattern MIRROR = Pattern.compile("(?i)Mirrored from ([^\\s<>]+) by HTTrack");
    private static final Pattern CHARSET = Pattern.compile("(?i)charset\\s*=\\s*[\"']?([a-z0-9_-]+)");

    @FunctionalInterface interface Fetcher {
        String fetch(URI uri) throws IOException, InterruptedException;
    }

    record Result(Map<String, String> styles, List<String> messages) {
        Result { styles = Map.copyOf(styles); messages = List.copyOf(messages); }
        String summary() {
            return "Linked CSS: " + styles.size() + " loaded"
                + (messages.isEmpty() ? "." : "; " + String.join("; ", messages));
        }
    }

    private final Fetcher fetcher;

    LinkedCssLoader() { this(LinkedCssLoader::fetchRemote); }
    LinkedCssLoader(Fetcher fetcher) { this.fetcher = fetcher; }

    static Optional<URI> originalPage(String html) {
        var doc = Jsoup.parse(html);
        for (var node : doc.select("meta[name=ebook-generator-source-url][content], link[rel=canonical][href], meta[property=og:url][content]")) {
            String value = node.hasAttr("href") ? node.attr("href") : node.attr("content");
            try { if (webUri(URI.create(value))) return Optional.of(URI.create(value)); }
            catch (IllegalArgumentException ignored) { }
        }
        var mirror = MIRROR.matcher(html);
        if (mirror.find()) {
            try {
                String value = mirror.group(1);
                URI uri = URI.create(value.contains("://") ? value : "https://" + value);
                if (webUri(uri)) return Optional.of(uri);
            } catch (IllegalArgumentException ignored) { }
        }
        return Optional.empty();
    }

    Result load(String html, Path source, URI originalPage) throws InterruptedException {
        var styles = new LinkedHashMap<String, String>();
        var messages = new ArrayList<String>();
        var visited = new java.util.HashSet<String>();
        var links = Jsoup.parse(html).select("link[href]").stream()
            .filter(link -> {
                var rel = List.of(link.attr("rel").toLowerCase(Locale.ROOT).split("\\s+"));
                return rel.contains("stylesheet") && !rel.contains("alternate") && !link.hasAttr("disabled");
            }).toList();
        for (var link : links) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            String href = link.attr("href");
            if (!visited.add(href)) continue;
            if (visited.size() > MAX_SHEETS) {
                messages.add("additional sheets skipped (limit " + MAX_SHEETS + ")");
                break;
            }
            try {
                URI ref = URI.create(href.strip());
                String css = localCss(source, ref);
                if (css == null) {
                    URI target = ref.isAbsolute() ? ref : originalPage == null ? null : originalPage.resolve(ref);
                    if (target == null || !webUri(target)) {
                        throw new IOException("file missing; set the original page URL to resolve this link");
                    }
                    css = fetcher.fetch(target);
                }
                String safe = PreviewCss.stylesheet(css);
                if (safe.isEmpty()) throw new IOException("no supported presentation rules");
                styles.put(href, safe);
            } catch (IOException | IllegalArgumentException ex) {
                messages.add(href + ": " + ex.getMessage());
            }
        }
        return new Result(styles, messages);
    }

    private static String localCss(Path source, URI ref) throws IOException {
        // Only relative .css files inside the selected HTML directory, including saved-page folders.
        if (source == null || ref.isAbsolute() || ref.getRawAuthority() != null || ref.getPath() == null
            || ref.getPath().startsWith("/") || !ref.getPath().toLowerCase(Locale.ROOT).endsWith(".css")) return null;
        Path root = source.toAbsolutePath().getParent().toRealPath();
        Path candidate = root.resolve(ref.getPath()).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) return null;
        candidate = candidate.toRealPath();
        if (!candidate.startsWith(root)) return null;
        try (var stream = Files.newInputStream(candidate)) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("stylesheet exceeds 1 MiB");
            return decode(bytes, "");
        }
    }

    private static boolean webUri(URI uri) {
        return uri.getHost() != null && uri.getUserInfo() == null
            && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
    }

    static boolean publicAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return !address.isAnyLocalAddress() && !address.isLoopbackAddress()
            && !address.isLinkLocalAddress() && !address.isSiteLocalAddress()
            && !address.isMulticastAddress() && !(bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc);
    }

    private static String fetchRemote(URI initial) throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            URI uri = initial;
            for (int redirects = 0; redirects <= 3; redirects++) {
                if (!webUri(uri)) throw new IOException("only HTTP(S) stylesheets are supported");
                for (var address : InetAddress.getAllByName(uri.getHost())) {
                    if (!publicAddress(address)) throw new IOException("private network address blocked");
                }
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                    .header("Accept", "text/css").header("User-Agent", "Ebook-Generator CSS preview")
                    .GET().build();
                var response = client.send(request,
                    HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_BYTES));
                if (List.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
                    URI next = uri.resolve(response.headers().firstValue("location")
                        .orElseThrow(() -> new IOException("redirect has no location")));
                    if ("https".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(next.getScheme())) {
                        throw new IOException("insecure redirect blocked");
                    }
                    uri = next;
                    continue;
                }
                if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                String type = response.headers().firstValue("content-type").orElse("");
                if (!type.toLowerCase(Locale.ROOT).matches("(?:text/css|application/css)(?:\\s*;.*)?")) {
                    throw new IOException("server did not return CSS");
                }
                return decode(response.body(), type);
            }
            throw new IOException("too many redirects");
        }
    }

    private static String decode(byte[] bytes, String contentType) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        var header = CHARSET.matcher(contentType);
        var declared = Pattern.compile("^@charset\\s+[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            .matcher(new String(bytes, 0, Math.min(bytes.length, 100), StandardCharsets.US_ASCII));
        try {
            if (header.find()) charset = Charset.forName(header.group(1));
            else if (declared.find()) charset = Charset.forName(declared.group(1));
            if (bytes.length >= 2 && ((bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe)
                || (bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff))) charset = StandardCharsets.UTF_16;
            if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb
                && bytes[2] == (byte) 0xbf) charset = StandardCharsets.UTF_8;
            String text = charset.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (IllegalArgumentException ex) {
            throw new IOException("unsupported CSS encoding", ex);
        }
    }
}
