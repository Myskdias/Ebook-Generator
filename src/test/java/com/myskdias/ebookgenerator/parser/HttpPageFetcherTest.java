package com.myskdias.ebookgenerator.parser;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpPageFetcherTest {
    private HttpServer server;
    private URI base;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }

    private void reply(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test void downloadsASequenceOverHttpWithoutFetchingScriptsOrAds() throws Exception {
        var subresources = new AtomicInteger();
        server.createContext("/ad.js", exchange -> { subresources.incrementAndGet(); exchange.close(); });
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().set("Location", "/book/1");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/book/1", exchange -> reply(exchange, 200, "text/html; charset=windows-1252",
            ("<h1>Café</h1><main><p>“First” — été.</p><div class='ads'><p>Ad</p></div></main>"
                + "<script src='/ad.js'></script><a class='next' href='2#text'>Next</a>")
                .getBytes(Charset.forName("windows-1252"))));
        server.createContext("/book/2", exchange -> reply(exchange, 200, "text/html",
            "<meta charset='UTF-8'><h1>中文</h1><main><p>😀 𝘯𝘵</p></main>".getBytes(StandardCharsets.UTF_8)));
        try (var fetcher = new HttpPageFetcher(address -> address.isLoopbackAddress())) {
            var importer = new BookImporter(fetcher::fetch, 0);
            var profile = new ParserProfile("HTTP", "127.0.0.1", "h1", "main", ".next", List.of(".ads"));
            var result = importer.read(base.resolve("/start"), profile, 0, p -> {}, () -> false);
            assertEquals(2, result.chapters().size());
            assertEquals("“First” — été.", result.chapters().getFirst().content());
            assertEquals("😀 𝘯𝘵", result.chapters().getLast().content());
            assertEquals(0, subresources.get());
        }
    }

    @Test void rejectsHttpErrorsWrongTypesAndRedirectsOutsideTheProfileDomain() throws Exception {
        server.createContext("/missing", exchange -> reply(exchange, 404, "text/html", "Missing".getBytes()));
        server.createContext("/not-html", exchange -> reply(exchange, 200, "application/json", "{}".getBytes()));
        server.createContext("/outside", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://unrelated.invalid/next");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        try (var fetcher = new HttpPageFetcher(address -> address.isLoopbackAddress())) {
            assertTrue(assertThrows(IOException.class, () -> fetcher.fetch(base.resolve("/missing"), "127.0.0.1"))
                .getMessage().contains("HTTP 404"));
            assertTrue(assertThrows(IOException.class, () -> fetcher.fetch(base.resolve("/not-html"), "127.0.0.1"))
                .getMessage().contains("HTML"));
            assertTrue(assertThrows(IOException.class, () -> fetcher.fetch(base.resolve("/outside"), "127.0.0.1"))
                .getMessage().contains("profile's domain"));
        }
        try (var production = new HttpPageFetcher()) {
            assertTrue(assertThrows(IOException.class, () -> production.fetch(base.resolve("/book/1"), "127.0.0.1"))
                .getMessage().contains("Private network"));
        }
    }

    @Test void boundsResponseSizesAndRejectsMalformedHtmlEncoding() throws Exception {
        server.createContext("/large", exchange -> reply(exchange, 200, "text/html",
            new byte[HttpPageFetcher.MAX_PAGE_BYTES + 1]));
        server.createContext("/broken", exchange -> reply(exchange, 200, "text/html; charset=UTF-8",
            new byte[] {'<', 'p', '>', (byte) 0xC3, 0x28}));
        try (var fetcher = new HttpPageFetcher(address -> address.isLoopbackAddress())) {
            assertThrows(IOException.class, () -> fetcher.fetch(base.resolve("/large"), "127.0.0.1"));
            assertTrue(assertThrows(IOException.class, () -> fetcher.fetch(base.resolve("/broken"), "127.0.0.1"))
                .getMessage().contains("invalid bytes were not replaced"));
        }
    }

    @Test void supportsGzipAndLetsBomOverrideHttpCharset() throws Exception {
        var buffer = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(buffer)) {
            gzip.write("\uFEFF<h1>中文</h1>".getBytes(StandardCharsets.UTF_8));
        }
        server.createContext("/gzip", exchange -> {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            reply(exchange, 200, "text/html; charset=windows-1252", buffer.toByteArray());
        });
        try (var fetcher = new HttpPageFetcher(address -> address.isLoopbackAddress())) {
            assertTrue(fetcher.fetch(base.resolve("/gzip"), "127.0.0.1").html().contains("中文"));
        }
    }
}
