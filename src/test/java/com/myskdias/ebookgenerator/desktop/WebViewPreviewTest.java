package com.myskdias.ebookgenerator.desktop;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises the actual JavaFX string-to-WebKit boundary, not just Jsoup round trips. */
class WebViewPreviewTest {
    @BeforeAll static void startToolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        });
        started.get(20, TimeUnit.SECONDS);
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { result.complete(operation.call()); }
            catch (Throwable ex) { result.completeExceptionally(ex); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    private static WebEngine load(String html, boolean css) throws Exception {
        return load(html, css, java.util.Map.of());
    }

    private static WebEngine load(String html, boolean css, java.util.Map<String, String> linkedCss) throws Exception {
        var loaded = new CompletableFuture<WebEngine>();
        onFx(() -> {
            var engine = new WebView().getEngine();
            engine.getLoadWorker().stateProperty().addListener((o, old, state) -> {
                if (state == Worker.State.SUCCEEDED) loaded.complete(engine);
                if (state == Worker.State.FAILED) loaded.completeExceptionally(
                    engine.getLoadWorker().getException());
            });
            engine.loadContent(HtmlPreviewSanitizer.sanitize(html, css, linkedCss), "text/html");
            return null;
        });
        return loaded.get(20, TimeUnit.SECONDS);
    }

    @Test void editorAllowsUngroupingBeforeAndAfterSelectingClasslessElements() throws Exception {
        var app = new VisualParserApp(new DirectoryHistory(null));
        var loaded = new CompletableFuture<Void>();
        Stage stage = onFx(() -> {
            var window = new Stage();
            app.start(window);
            try {
                var group = (CheckBox) window.getScene().lookup("#group-exclusions");
                assertFalse(group.isDisabled());
                assertTrue(group.isSelected());
                group.fire();
                assertFalse(group.isSelected(), "The user must be able to uncheck before selecting a block");
                var browser = (WebView) window.getScene().lookup("WebView");
                browser.getEngine().getLoadWorker().stateProperty().addListener((o, old, state) -> {
                    if (state == Worker.State.SUCCEEDED) loaded.complete(null);
                    if (state == Worker.State.FAILED) loaded.completeExceptionally(
                        browser.getEngine().getLoadWorker().getException());
                });
                app.loadHtml(Path.of("src/test/resources/chapters/novelfull-sample.html"));
            } catch (Throwable ex) {
                app.stop();
                window.close();
                throw ex;
            }
            return window;
        });
        try {
            loaded.get(20, TimeUnit.SECONDS);
            onFx(() -> {
                var group = (CheckBox) stage.getScene().lookup("#group-exclusions");
                var input = (TextField) stage.getScene().lookup("#selector-exclude");
                var role = (ToggleButton) stage.getScene().lookup("#role-exclude");
                var browser = (WebView) stage.getScene().lookup("WebView");
                role.fire();
                // A paragraph without a class was the case that disabled the checkbox.
                browser.getEngine().executeScript("document.querySelector('#chapter-content > p').click()");
                app.pollSelection();
                String paragraph = input.getText();
                assertFalse(group.isDisabled());
                assertFalse(group.isSelected());
                group.fire();
                assertTrue(group.isSelected());
                assertEquals(paragraph, input.getText(), "Classless selection keeps its individual selector");
                group.fire();
                assertFalse(group.isSelected());
                browser.getEngine().executeScript("document.querySelector('.ads').click()");
                app.pollSelection();
                String single = input.getText();
                assertNotEquals("div.ads.ads-holder", single);
                group.fire();
                assertEquals("div.ads.ads-holder", input.getText());
                group.fire();
                assertEquals(single, input.getText());
                browser.getEngine().executeScript("window.__ebookPicker.selectParent()");
                app.pollSelection();
                assertFalse(group.isSelected(), "Parent selection must preserve the user's scope choice");
                app.loadHtml(Path.of("src/test/resources/chapters/chapter-01.html"));
                assertFalse(group.isDisabled());
                assertFalse(group.isSelected(), "Opening another HTML file must preserve the scope choice");
                return null;
            });
        } finally {
            onFx(() -> { app.stop(); stage.close(); return null; });
        }
    }

    @Test void preservesActualWebViewUnicodeAndPickerAcrossBothCssModes() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/chapters/novelfull-sample.html"));
        String script = Files.readString(Path.of("src/main/resources/com/myskdias/ebookgenerator/desktop/selector.js"));
        for (boolean css : new boolean[] {false, true}) {
            var engine = load(html, css);
            onFx(() -> {
                assertEquals("Eyes..𝘯𝘵", engine.executeScript(
                    "document.querySelectorAll('#chapter-content > p')[1].textContent"));
                assertEquals("Café — “quotes”, & entities, 中文 and 😀.", engine.executeScript(
                    "document.querySelector('#chapter-content > p').textContent"));
                assertEquals(css ? "rgb(34, 51, 68)" : "rgb(0, 0, 0)", engine.executeScript(
                    "getComputedStyle(document.querySelector('#chapter-content')).color"));
                engine.executeScript(script);
                assertEquals("undefined", engine.executeScript("typeof window.remoteAd"));
                engine.executeScript("window.__ebookPicker.setExclusions(['.ads'])");
                assertEquals(2, ((Number) engine.executeScript(
                    "document.querySelectorAll('[data-ebook-excluded=true]').length")).intValue());
                engine.executeScript("document.querySelectorAll('.ads')[1].firstElementChild.click()");
                engine.executeScript("window.__ebookPicker.selectParent()");
                String payload = (String) engine.executeScript("window.__ebookPicker.takeSelection()");
                var picked = new ObjectMapper().readTree(payload);
                String selector = picked.get("selector").asText();
                assertEquals("div.ads.ads-holder", picked.get("classSelector").asText());
                var original = Jsoup.parse(html);
                assertEquals(1, original.select(selector).size());
                assertEquals("Advertisement two", original.selectFirst(selector).text());
                assertEquals("24px", engine.executeScript(
                    "getComputedStyle(document.querySelector('.ads > div')).minHeight"));
                engine.executeScript("window.__ebookPicker.setExclusions([])");
                assertEquals(0, ((Number) engine.executeScript(
                    "document.querySelectorAll('[data-ebook-excluded]').length")).intValue());
                return null;
            });
        }
    }

    @Test void rendersLinkedResponsiveCssWithoutGivingWebViewResourceAccess() throws Exception {
        String html = "<link rel='stylesheet' href='site.css'><p id='story'>Text</p>";
        var styles = java.util.Map.of("site.css", "/* comment */ @import url(https://remote);"
            + "@media screen and (min-width:0px) {p{color:rgb(20, 30, 40)!important;font-size:23px}}"
            + "p{background-image:url(https://remote/pixel)}");
        for (boolean css : new boolean[] {true, false}) {
            var engine = load(html, css, styles);
            onFx(() -> {
                assertEquals(css ? "rgb(20, 30, 40)" : "rgb(0, 0, 0)", engine.executeScript(
                    "getComputedStyle(document.querySelector('#story')).color"));
                assertEquals("none", engine.executeScript(
                    "getComputedStyle(document.querySelector('#story')).backgroundImage"));
                return null;
            });
        }
    }

    @Test void cspBlocksResourceRequestsEvenIfCssIsInsertedAfterSanitizing() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pixel", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/pixel";
            for (boolean css : new boolean[] {false, true}) {
                var engine = load("<p>Isolated preview</p>", css);
                var settled = new CompletableFuture<Void>();
                onFx(() -> {
                    engine.executeScript("const style = document.createElement('style');"
                        + "style.textContent = 'p { background-image:url(" + url + ") }';"
                        + "document.head.appendChild(style);"
                        + "const img = document.createElement('img'); img.src = '" + url + "';"
                        + "document.body.appendChild(img);");
                    var delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(750));
                    delay.setOnFinished(e -> settled.complete(null));
                    delay.play();
                    return null;
                });
                settled.get(5, TimeUnit.SECONDS);
            }
            assertEquals(0, requests.get(), "The preview must never fetch resources");
        } finally {
            server.stop(0);
        }
    }
}
