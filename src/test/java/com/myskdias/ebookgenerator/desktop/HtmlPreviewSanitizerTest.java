package com.myskdias.ebookgenerator.desktop;

import static org.junit.jupiter.api.Assertions.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class HtmlPreviewSanitizerTest {
    @Test void removesExecutableAndExternalContentButKeepsSelectableDom() {
        String unsafe = """
            <html><head><link href="https://remote.example/style.css" rel="stylesheet">
            <style>@import url(https://remote.example/evil.css)</style></head>
            <body onload="run()">
            <h2 class="chapter-title" onclick="run()">Chapter 1</h2>
            <main id="reader"><p style="background:url(https://remote.example/pixel)">
              Read me</p><script>run()</script></main>
            <a class="next" href="chapter-2.html">Next</a>
            <img src="https://remote.example/image.jpg">
            <iframe src="https://remote.example/iframe"></iframe>
            </body></html>
            """;
        var result = Jsoup.parse(HtmlPreviewSanitizer.sanitize(unsafe));
        assertTrue(result.select("script, style, iframe, img, link").isEmpty());
        assertEquals("Chapter 1", result.selectFirst("h2.chapter-title").text());
        assertEquals("Read me", result.selectFirst("main#reader p").text());
        assertEquals("chapter-2.html", result.selectFirst("a.next").attr("href"));
        assertFalse(result.outerHtml().contains("onclick"));
        assertFalse(result.outerHtml().contains("onload"));
        assertFalse(result.outerHtml().contains("background:url"));
    }
}
