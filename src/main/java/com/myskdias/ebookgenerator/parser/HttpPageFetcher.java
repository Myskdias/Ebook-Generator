package com.myskdias.ebookgenerator.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** HTML-only downloader. It never executes scripts or fetches page subresources. */
public final class HttpPageFetcher implements AutoCloseable {
    public static final int MAX_PAGE_BYTES = 4 * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Predicate<InetAddress> allowedAddress;

    public HttpPageFetcher() { this(HttpPageFetcher::publicAddress); }

    // Local HTTP servers are allowed only in isolated integration tests.
    HttpPageFetcher(Predicate<InetAddress> allowedAddress) { this.allowedAddress = allowedAddress; }

    public BookImporter.Page fetch(URI initial, String domain) throws IOException, InterruptedException {
        URI uri = initial;
        for (int redirects = 0; redirects <= 5; redirects++) {
            requireSite(uri, domain);
            for (var address : InetAddress.getAllByName(uri.getHost())) {
                if (!allowedAddress.test(address)) throw new IOException("Private network address blocked: " + uri.getHost());
            }
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Accept", "text/html, application/xhtml+xml")
                .header("User-Agent", "Ebook-Generator/0.1 (chapter import)").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofByteArray(), MAX_PAGE_BYTES));
            if (List.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
                URI next;
                String location = response.headers().firstValue("location").orElse("");
                if (location.isBlank()) throw new IOException("Redirect has no location: " + uri);
                try { next = uri.resolve(location); }
                catch (IllegalArgumentException ex) { throw new IOException("Invalid redirect", ex); }
                if ("https".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(next.getScheme())) {
                    throw new IOException("Insecure redirect blocked");
                }
                uri = next;
                continue;
            }
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " at " + uri);
            String type = response.headers().firstValue("content-type").orElse("");
            if (!type.toLowerCase(Locale.ROOT).matches("(?:text/html|application/xhtml\\+xml)(?:\\s*;.*)?")) {
                throw new IOException("The server did not return an HTML chapter: " + uri);
            }
            byte[] bytes = response.body();
            String encoding = response.headers().firstValue("content-encoding").orElse("identity");
            if (encoding.equalsIgnoreCase("gzip")) {
                try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                    bytes = gzip.readNBytes(MAX_PAGE_BYTES + 1);
                }
                if (bytes.length > MAX_PAGE_BYTES) throw new IOException("Chapter exceeds 4 MiB after decompression");
            } else if (!encoding.equalsIgnoreCase("identity")) {
                throw new IOException("Unsupported HTTP content encoding: " + encoding);
            }
            var matcher = Pattern.compile("(?i)charset\\s*=\\s*[\"']?([a-z0-9_-]+)").matcher(type);
            String charset = matcher.find() ? matcher.group(1) : null;
            return new BookImporter.Page(uri, HtmlSource.decode(bytes, charset, uri.toString()).html());
        }
        throw new IOException("Too many redirects: " + initial);
    }

    public static void requireSite(URI uri, String domain) throws IOException {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || host.isEmpty() || uri.getUserInfo() != null || !(host.equals(domain) || host.endsWith("." + domain))) {
            throw new IOException("Chapter URL must stay on the source domain (" + domain + "): " + uri);
        }
    }

    private static boolean publicAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return !address.isAnyLocalAddress() && !address.isLoopbackAddress() && !address.isSiteLocalAddress()
            && !address.isLinkLocalAddress() && !address.isMulticastAddress()
            && !(bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc);
    }

    @Override public void close() { client.close(); }
}
