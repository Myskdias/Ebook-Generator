package com.myskdias.ebookgenerator.desktop;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myskdias.ebookgenerator.BookGenerationService;
import com.myskdias.ebookgenerator.parser.ParserProfile;
import com.myskdias.ebookgenerator.parser.BookImporter;
import com.myskdias.ebookgenerator.download.ChapterDownloadService;
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
import javafx.concurrent.WorkerStateEvent;
import javafx.concurrent.Task;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the actual JavaFX string-to-WebKit boundary, not just Jsoup round trips. */
class WebViewPreviewTest {
    @TempDir Path temp;
    private static final java.util.List<WebView> retainedViews = new java.util.ArrayList<>();
    @BeforeAll static void startToolkit() throws Exception {
        var started = new CompletableFuture<Void>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.complete(null);
        });
        started.get(20, TimeUnit.SECONDS);
    }

    @org.junit.jupiter.api.AfterEach void releasePreviewViews() throws Exception {
        onFx(() -> {
            retainedViews.forEach(view -> view.getEngine().load(null));
            retainedViews.clear();
            return null;
        });
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
            var view = new WebView();
            retainedViews.add(view); // Keep the owner alive until the asynchronous WebKit load finishes.
            var engine = view.getEngine();
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

    private static <T> CompletableFuture<T> completion(Task<T> task) {
        var completed = new CompletableFuture<T>();
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> completed.complete(task.getValue()));
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> completed.completeExceptionally(task.getException()));
        return completed;
    }

    @Test void desktopDownloadsThenCreatesAProfileThenGeneratesOffline() throws Exception {
        var calls = new AtomicInteger();
        var downloader = new ChapterDownloadService((uri, domain) -> {
            int number = calls.incrementAndGet();
            return new BookImporter.Page(uri, "<h1>Chapter " + number + "</h1><main><p>Café 😀 " + number
                + "</p><div class='ads'><p>Advertisement</p></div></main>"
                + (number == 1 ? "<a data-nav='next' href='/2'>Next</a>" : ""));
        }, 0);
        var app = new VisualParserApp(new DirectoryHistory(null), downloader);
        Stage stage = onFx(() -> { var window = new Stage(); app.start(window); return window; });
        try {
            var downloaded = onFx(() -> {
                var tabs = (TabPane) stage.getScene().lookup("#workspace-tabs");
                assertEquals(0, tabs.getSelectionModel().getSelectedIndex());
                ((TextField) stage.getScene().lookup("#download-first-url")).setText("https://example.com/1");
                ((TextField) stage.getScene().lookup("#download-folder")).setText(temp.resolve("download").toString());
                return completion(((ChapterDownloadPane) tabs.getTabs().getFirst().getContent()).startDownload());
            });
            var saved = downloaded.get(20, TimeUnit.SECONDS);
            assertEquals(2, saved.archive().pages().size());
            assertFalse(Files.exists(temp.resolve("book.epub")));
            var ready = new CompletableFuture<Void>();
            onFx(() -> {
                var tabs = (TabPane) stage.getScene().lookup("#workspace-tabs");
                var browser = (WebView) tabs.getTabs().get(1).getContent().lookup("WebView");
                browser.getEngine().getLoadWorker().stateProperty().addListener((o, old, state) -> {
                    if (state == Worker.State.SUCCEEDED) ready.complete(null);
                    if (state == Worker.State.FAILED) ready.completeExceptionally(browser.getEngine().getLoadWorker().getException());
                });
                ((Button) stage.getScene().lookup("#download-to-profile")).fire();
                assertEquals(1, tabs.getSelectionModel().getSelectedIndex());
                return null;
            });
            ready.get(20, TimeUnit.SECONDS);
            var generated = onFx(() -> {
                // Create the extraction profile only now. Saved downloads do not need its next selector.
                ((TextField) stage.getScene().lookup("#selector-title")).setText("h1");
                ((TextField) stage.getScene().lookup("#selector-content")).setText("main");
                assertTrue(((TextField) stage.getScene().lookup("#selector-next")).getText().isEmpty());
                ((TextField) stage.getScene().lookup("#selector-exclude")).setText(".ads");
                ((Button) stage.getScene().lookup("#add-exclusion")).fire();
                ((Button) stage.getScene().lookup("#profile-to-epub")).fire();
                var tabs = (TabPane) stage.getScene().lookup("#workspace-tabs");
                assertEquals(2, tabs.getSelectionModel().getSelectedIndex());
                return completion(((BookGenerationPane) tabs.getTabs().get(2).getContent()).startGeneration(temp.resolve("book.epub")));
            });
            assertEquals(2, generated.get(20, TimeUnit.SECONDS).chapters());
            assertEquals(2, calls.get());
            try (var zip = new java.util.zip.ZipFile(temp.resolve("book.epub").toFile())) {
                String chapter = new String(zip.getInputStream(zip.getEntry("OEBPS/chapter-0001.xhtml")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(chapter.contains("Café 😀"));
                assertFalse(chapter.contains("Advertisement"));
            }
        } finally { onFx(() -> { app.stop(); stage.close(); return null; }); }
    }

    @SuppressWarnings("unchecked")
    @Test void generationScreenProducesLimitedAndCompleteBooksFromOneChapter() throws Exception {
        for (int i = 1; i <= 3; i++) {
            Files.writeString(temp.resolve(i + ".html"), "<h1>Chapter " + i + "</h1><main><p>Page " + i + ".</p></main>"
                + (i < 3 ? "<a class='next' href='" + (i + 1) + ".html'>Next</a>" : ""));
        }
        var app = new VisualParserApp(new DirectoryHistory(null));
        Stage stage = onFx(() -> { var window = new Stage(); app.start(window); return window; });
        try {
            var limited = onFx(() -> {
                var tabs = (TabPane) stage.getScene().lookup("#workspace-tabs");
                assertEquals("1. Download", tabs.getSelectionModel().getSelectedItem().getText());
                tabs.getSelectionModel().select(2);
                var pane = (BookGenerationPane) tabs.getSelectionModel().getSelectedItem().getContent();
                pane.useProfile(new ParserProfile("Site", "example.com", "h1", "main", ".next"), "Test");
                pane.useFirstChapter(temp.resolve("1.html"));
                var scope = (ComboBox<BookGenerationPane.Scope>) stage.getScene().lookup("#generation-scope");
                var count = (Spinner<Integer>) stage.getScene().lookup("#generation-count");
                scope.setValue(BookGenerationPane.Scope.COUNT);
                count.getEditor().setText("0");
                assertThrows(IllegalArgumentException.class, () -> pane.startGeneration(temp.resolve("invalid.epub")));
                count.getEditor().setText("2");
                return completion(pane.startGeneration(temp.resolve("limited.epub")));
            });
            assertEquals(2, limited.get(20, TimeUnit.SECONDS).chapters());
            var whole = onFx(() -> {
                var tabs = (TabPane) stage.getScene().lookup("#workspace-tabs");
                var pane = (BookGenerationPane) tabs.getSelectionModel().getSelectedItem().getContent();
                ((ComboBox<BookGenerationPane.Scope>) stage.getScene().lookup("#generation-scope"))
                    .setValue(BookGenerationPane.Scope.ALL);
                return completion(pane.startGeneration(temp.resolve("whole.epub")));
            });
            assertEquals(3, whole.get(20, TimeUnit.SECONDS).chapters());
            try (var zip = new java.util.zip.ZipFile(temp.resolve("whole.epub").toFile())) {
                assertNotNull(zip.getEntry("OEBPS/chapter-0003.xhtml"));
                assertNull(zip.getEntry("OEBPS/chapter-0004.xhtml"));
            }
        } finally { onFx(() -> { app.stop(); stage.close(); return null; }); }
    }

    @Test void generationCancelInterruptsTheWorkerAndRestoresTheForm() throws Exception {
        Path first = Files.writeString(temp.resolve("1.html"), "<h1>Title</h1><main>Text</main>");
        var entered = new java.util.concurrent.CountDownLatch(1);
        var pane = onFx(() -> new BookGenerationPane(new DirectoryHistory(null), (request, progress, cancelled) -> {
            entered.countDown();
            new java.util.concurrent.CountDownLatch(1).await(); // Only cancellation should release this worker.
            throw new AssertionError("Worker should have been interrupted");
        }));
        Stage stage = onFx(() -> { var window = new Stage(); window.setScene(new Scene(pane, 900, 850)); window.show(); return window; });
        try {
            var completed = onFx(() -> {
                pane.useFirstChapter(first);
                pane.useProfile(new ParserProfile("Site", "example.com", "h1", "main", ".next"), "Test");
                return completion(pane.startGeneration(temp.resolve("cancelled.epub")));
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            onFx(() -> { pane.cancelGeneration(); return null; });
            assertThrows(java.util.concurrent.ExecutionException.class, () -> completed.get(10, TimeUnit.SECONDS));
            onFx(() -> {
                assertTrue(((Label) stage.getScene().lookup("#generation-status")).getText().contains("cancelled"));
                assertFalse(stage.getScene().lookup("#generate-epub").isDisabled());
                return null;
            });
            assertFalse(Files.exists(temp.resolve("cancelled.epub")));
        } finally { onFx(() -> { pane.cancelGeneration(); stage.close(); return null; }); }
    }

    @Test void editorAllowsUngroupingBeforeAndAfterSelectingClasslessElements() throws Exception {
        var app = new VisualParserApp(new DirectoryHistory(null));
        var loaded = new CompletableFuture<Void>();
        Stage stage = onFx(() -> {
            var window = new Stage();
            app.start(window);
            ((TabPane) window.getScene().lookup("#workspace-tabs")).getSelectionModel().select(1);
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
