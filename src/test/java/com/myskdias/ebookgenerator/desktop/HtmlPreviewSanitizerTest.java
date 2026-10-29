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

    @Test void cssToggleKeepsOnlyPresentationStylesInAnIsolatedDocument() {
        String html = """
            <link rel="stylesheet" href="file:///secret.css">
            <style>p { color: red; background-image: url(https://remote/pixel); }
              @font-face { font-family: evil; src: url(file:///font); }
              @media screen { p { background: url(https://remote); } }
              .reader { font-size: 18px; }</style>
            <body background="https://remote/image" onload="evil()">
            <form action="https://remote"><button formaction="https://remote">Go</button></form>
            <p id="story" style="font-weight: bold; color: rgb(1, 2, 3); background: url(x); --x: url(x)">Text</p>
            <img src="file:///secret"><script>alert(1)</script><iframe srcdoc="evil"></iframe>
            """;
        for (boolean css : new boolean[] {false, true}) {
            var document = Jsoup.parse(HtmlPreviewSanitizer.sanitize(html, css));
            assertTrue(document.select("link, img, script, iframe, [onload], [src], [action], [formaction], [background]").isEmpty());
            assertTrue(document.selectFirst("meta[http-equiv=Content-Security-Policy]")
                .attr("content").contains("default-src 'none'"));
            if (css) {
                assertTrue(document.selectFirst("style").data().contains("color:red"));
                assertTrue(document.selectFirst("#story").attr("style").contains("font-weight:bold"));
            } else {
                assertTrue(document.select("style, [style]").isEmpty());
            }
            String styles = document.select("style").stream().map(org.jsoup.nodes.Element::data)
                .collect(java.util.stream.Collectors.joining())
                + document.selectFirst("#story").attr("style");
            assertFalse(styles.contains("url("));
            assertFalse(styles.contains("@font-face"));
        }
    }

    @Test void rejectsObfuscatedResourceCssAndMarkupBreakouts() {
        String[] unsafe = {"background-image:u\\72l(https://remote)", "color:var(--remote)",
            "width:expression(alert(1))", "font-family:url(https://remote)",
            "background: u/**/rl(x)", "color:red!important", "--x:url(x)"};
        for (String css : unsafe) assertEquals("", PreviewCss.declarations(css));
        assertEquals("", PreviewCss.stylesheet("@import 'https://remote'; p {color:red}"));
        assertEquals("", PreviewCss.stylesheet("p</style><script>evil()</script> {color:red}"));
    }

    @Test void serializesSupplementaryUnicodeWithoutSendingSurrogatesToWebView() {
        String html = "<p>Eyes..𝘯𝘵 &amp; 中文 😀 café</p>";
        String sanitized = HtmlPreviewSanitizer.sanitize(html);
        assertTrue(sanitized.codePoints().allMatch(cp -> cp < 128));
        assertEquals(Jsoup.parse(html).text(), Jsoup.parse(sanitized).text());
    }

    @Test void pickerSelectorsReferToOriginalDomEvenAfterSiblingsAreRemoved() {
        String html = "<main><div id='duplicate'>A</div><img><div id='duplicate'>B</div></main>";
        var preview = Jsoup.parse(HtmlPreviewSanitizer.sanitize(html));
        var original = Jsoup.parse(html);
        for (var element : preview.select("main, div")) {
            String selector = element.attr(HtmlPreviewSanitizer.SOURCE_SELECTOR);
            assertEquals(1, original.select(selector).size(), selector);
            assertEquals(element.text(), original.selectFirst(selector).text());
        }
    }
}
