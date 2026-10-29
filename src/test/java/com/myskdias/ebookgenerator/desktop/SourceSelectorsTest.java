package com.myskdias.ebookgenerator.desktop;

import static org.junit.jupiter.api.Assertions.*;
import com.myskdias.ebookgenerator.parser.ParserProfile;
import com.myskdias.ebookgenerator.parser.ProfiledHtmlParser;
import java.net.URI;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class SourceSelectorsTest {
    @Test void groupsAllMatchingClassesWithoutPositionalSelectorsAcrossChapters() {
        String html = "<h1>Title</h1><main id='chapter-content'><p>Story.</p>"
            + "<div class='ads ads-holder ads-middle text-center'><p>Ad one</p></div>"
            + "<p>Note.</p><div class='ads ads-holder ads-middle text-center'><p>Ad two</p></div></main>";
        var source = Jsoup.parse(html);
        var ad = source.selectFirst(".ads");
        String group = SourceSelectors.classes(ad);
        assertEquals("div.ads.ads-holder.ads-middle.text-center", group);
        assertEquals(2, source.select(group).size());
        assertEquals(1, source.select(SourceSelectors.unique(ad, source)).size());
        var profile = new ParserProfile("Site", "example.com", "h1", "main", "", List.of(group));
        var parser = new ProfiledHtmlParser();
        assertEquals("Story.\n\nNote.", parser.parse(html, URI.create("https://example.com/1"), profile)
            .chapter().content());
        String next = html.replace("<p>Story.</p>", "<p>Extra paragraph.</p><p>Story.</p>")
            .replace("<p>Note.</p>", "<hr><p>Note.</p>");
        String content = parser.parse(next, URI.create("https://example.com/2"), profile).chapter().content();
        assertFalse(content.contains("Ad one"));
        assertFalse(content.contains("Ad two"));
        assertTrue(content.contains("Extra paragraph."));
    }

    @Test void prefersUniqueIdsAndClassesForTitleAndNavigation() {
        var source = Jsoup.parse("<div><h2><a class='chapter-title'>Title</a></h2>"
            + "<a id='next_chap' class='btn next'>Next</a><span>Other</span></div>");
        assertEquals("a.chapter-title", SourceSelectors.unique(source.selectFirst(".chapter-title"), source));
        assertEquals("#next_chap", SourceSelectors.unique(source.selectFirst("#next_chap"), source));
        assertEquals("", SourceSelectors.classes(source.selectFirst("span")));
    }
}
