package com.myskdias.ebookgenerator.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myskdias.ebookgenerator.parser.HtmlSource;
import com.myskdias.ebookgenerator.parser.ParsedChapter;
import com.myskdias.ebookgenerator.parser.ParserProfile;
import com.myskdias.ebookgenerator.parser.ParserProfileStore;
import com.myskdias.ebookgenerator.parser.ProfiledHtmlParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * First visual parser milestone. Open a local HTML page, pick selectors, validate against the
 * original HTML using ProfiledHtmlParser, and save a reusable JSON profile.
 *
 * A sanitized preview is displayed with WebView. JavaScript communicates through polling,
 * not an exposed Java-to-JavaScript bridge.
 */
public final class VisualParserApp extends Application {
    private enum Role { TITLE, CONTENT, NEXT, EXCLUDE }

    private final ParserProfileStore store = new ParserProfileStore();
    private final ProfiledHtmlParser parser = new ProfiledHtmlParser();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Role, TextField> selectors = new EnumMap<>(Role.class);
    private final DirectoryHistory directoryHistory;

    private WebView browser;
    private TextField profileName;
    private TextField domainName;
    private TextArea preview;
    private Label status;
    private Label openedFile;
    private Label encodingInfo;
    private CheckBox cssEnabled;
    private TextField originalPageUrl;
    private Label cssStatus;
    private CheckBox groupExclusions;
    private Label exclusionMatches;
    private String exclusionSingle = "";
    private String exclusionClasses = "";
    private Task<LinkedCssLoader.Result> cssTask;
    private LinkedCssLoader.Result cssCache;
    private String cachedCssOrigin;
    private int previewGeneration;
    private ListView<String> exclusions;
    private ToggleGroup roles;

    private Stage stage;
    private Path sourcePath;
    private String sourceHtml;
    private Timeline selectionPoller;
    private String selectionScript;
    private Button parentButton;
    private BookGenerationPane generationPane;
    private TabPane workspaceTabs;

    public VisualParserApp() {
        this(DirectoryHistory.forUser());
    }

    VisualParserApp(DirectoryHistory directoryHistory) {
        this.directoryHistory = directoryHistory;
    }

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        // WebView/WebEngine and controls must be created on the JavaFX Application Thread.
        browser = new WebView();
        browser.setContextMenuEnabled(false);
        profileName = new TextField();
        domainName = new TextField();
        preview = new TextArea();
        status = new Label("Open a local HTML chapter to begin.");
        openedFile = new Label("No HTML file selected");
        encodingInfo = new Label();
        encodingInfo.setWrapText(true);
        cssEnabled = new CheckBox("Preview CSS");
        cssEnabled.setOnAction(e -> reloadPreview());
        originalPageUrl = new TextField();
        originalPageUrl.setPromptText("https://site.example/novel/chapter.html");
        originalPageUrl.setOnAction(e -> { cssCache = null; reloadPreview(); });
        cssStatus = new Label("CSS disabled.");
        cssStatus.setWrapText(true);
        groupExclusions = new CheckBox("All elements with the same classes");
        groupExclusions.setId("group-exclusions");
        groupExclusions.setSelected(true);
        groupExclusions.setTooltip(new Tooltip("Blocks without classes keep their individual selector. "
            + "Your choice is kept when selecting another block or opening another page."));
        groupExclusions.setOnAction(e -> applyExclusionScope());
        exclusionMatches = new Label("Select a block to exclude.");
        exclusionMatches.setWrapText(true);
        exclusions = new ListView<>();
        exclusions.setPrefHeight(95);
        roles = new ToggleGroup();
        profileName.setPromptText("Profile name (e.g. NovelFull)");
        domainName.setPromptText("Domain (e.g. example.com)");
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(12);
        preview.setPromptText("The extracted chapter will appear here.");

        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(12));

        Button openHtml = new Button("Open HTML...");
        openHtml.setOnAction(e -> openHtml());
        Button loadProfile = new Button("Load profile...");
        loadProfile.setOnAction(e -> loadProfile());
        openedFile.setStyle("-fx-text-fill: #666666;");
        HBox header = new HBox(12, openHtml, loadProfile, cssEnabled, openedFile);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));
        HBox.setHgrow(openedFile, Priority.ALWAYS);
        layout.setTop(header);

        SplitPane split = new SplitPane();
        split.setOrientation(Orientation.HORIZONTAL);
        split.getItems().addAll(browser, editorPane());
        split.setDividerPositions(0.66);
        layout.setCenter(split);

        status.setWrapText(true);
        status.setPadding(new Insets(10, 2, 0, 2));
        layout.setBottom(status);

        browser.getEngine().getLoadWorker().stateProperty().addListener((obs, old, current) -> {
            if (current == Worker.State.SUCCEEDED && sourceHtml != null) {
                try {
                    browser.getEngine().executeScript(selectionScript);
                    syncExclusions();
                    status.setText("Page ready. Select a field on the right, then click its element.");
                } catch (RuntimeException ex) {
                    report("Could not install the element picker", ex);
                }
            } else if (current == Worker.State.FAILED) {
                report("Could not open the HTML preview", browser.getEngine().getLoadWorker().getException());
            }
        });
        browser.getEngine().setCreatePopupHandler(config -> null);

        try (InputStream stream = getClass().getResourceAsStream("selector.js")) {
            if (stream == null) throw new IOException("Missing selector.js resource");
            selectionScript = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            report("Could not load the DOM selector", ex);
            return;
        }

        selectionPoller = new Timeline(new KeyFrame(Duration.millis(180), e -> pollSelection()));
        selectionPoller.setCycleCount(Animation.INDEFINITE);
        selectionPoller.play();

        generationPane = new BookGenerationPane(directoryHistory);
        workspaceTabs = new TabPane(new Tab("Generate EPUB", generationPane), new Tab("Profile editor", layout));
        workspaceTabs.setId("workspace-tabs");
        workspaceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        primaryStage.setTitle("Ebook Generator");
        primaryStage.setScene(new Scene(workspaceTabs, 1260, 830));
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(650);
        primaryStage.show();
    }

    private ScrollPane editorPane() {
        VBox panel = new VBox(11);
        panel.setPadding(new Insets(12));
        panel.setPrefWidth(390);

        Label heading = new Label("Parser profile");
        heading.setStyle("-fx-font-size: 19px; -fx-font-weight: bold;");
        panel.getChildren().addAll(heading, new Label("Profile name"), profileName,
            new Label("Website domain"), domainName, encodingInfo,
            new Label("Original page URL (for linked CSS)"), originalPageUrl);
        Button reloadCss = new Button("Reload CSS");
        reloadCss.disableProperty().bind(cssEnabled.selectedProperty().not());
        reloadCss.setOnAction(e -> { cssCache = null; reloadPreview(); });
        panel.getChildren().addAll(reloadCss, cssStatus, new Separator(),
            new Label("1. Select a field; 2. Click its element in the page."));

        addRole(panel, Role.TITLE, "Chapter title", "e.g. h2.chapter-title");
        addRole(panel, Role.CONTENT, "Chapter content", "e.g. div#chapter-content");
        addRole(panel, Role.NEXT, "Next chapter (optional)", "e.g. a.next-chapter");
        addRole(panel, Role.EXCLUDE, "Block to exclude (ads, etc.)", "e.g. .ads");
        selectors.get(Role.EXCLUDE).textProperty().addListener((o, old, value) -> updateExclusionMatches());
        panel.getChildren().addAll(groupExclusions, exclusionMatches);

        parentButton = new Button("Select parent element");
        parentButton.setOnAction(e -> chooseParent());
        parentButton.setMaxWidth(Double.MAX_VALUE);
        panel.getChildren().add(parentButton);

        Button addExclusion = new Button("Add exclusion");
        addExclusion.setOnAction(e -> addExclusion());
        Button removeExclusion = new Button("Remove selected");
        removeExclusion.setOnAction(e -> {
            int index = exclusions.getSelectionModel().getSelectedIndex();
            if (index >= 0) exclusions.getItems().remove(index);
        });
        exclusions.getItems().addListener((javafx.collections.ListChangeListener<String>) c -> syncExclusions());
        panel.getChildren().addAll(new HBox(8, addExclusion, removeExclusion), exclusions);

        Label explanation = new Label(
            "Click the whole chapter container, not only one paragraph. "
            + "Use 'Select parent element' if your click selects a child. "
            + "To exclude an ad, select its block (dashed boxes mark empty blocks), "
            + "then Add exclusion. By default, all blocks with the same classes are excluded. "
            + "Uncheck the option to target only this block. Red outlines mark exclusions. "
            + "Linked CSS is filtered before display; scripts, images and fonts stay blocked.");
        explanation.setWrapText(true);
        panel.getChildren().addAll(explanation, new Separator());

        Button inspect = new Button("Preview extraction");
        inspect.setMaxWidth(Double.MAX_VALUE);
        inspect.setOnAction(e -> previewExtraction());

        Button save = new Button("Save JSON profile...");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(e -> saveProfile());
        Button generate = new Button("Generate EPUB from this chapter...");
        generate.setOnAction(e -> {
            if (sourcePath == null) { status.setText("Open a local HTML chapter first."); return; }
            try {
                generationPane.useProfile(buildProfile(true), "Current editor profile");
                generationPane.useFirstChapter(sourcePath);
                workspaceTabs.getSelectionModel().select(0);
            } catch (Exception ex) { status.setText(ex.getMessage()); }
        });
        panel.getChildren().addAll(inspect, save, generate, new Label("Extracted chapter"), preview);
        VBox.setVgrow(preview, Priority.ALWAYS);

        for (Toggle toggle : roles.getToggles()) {
            if (toggle.getUserData() == Role.TITLE) {
                roles.selectToggle(toggle);
                break;
            }
        }

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setMinWidth(325);
        return scroll;
    }

    private void addRole(VBox panel, Role role, String label, String prompt) {
        ToggleButton choice = new ToggleButton(label);
        choice.setId("role-" + role.name().toLowerCase(java.util.Locale.ROOT));
        choice.setUserData(role);
        choice.setToggleGroup(roles);
        choice.setMaxWidth(Double.MAX_VALUE);

        TextField input = new TextField();
        input.setId("selector-" + role.name().toLowerCase(java.util.Locale.ROOT));
        input.setPromptText(prompt);
        selectors.put(role, input);
        panel.getChildren().addAll(choice, input);
    }

    private Role activeRole() {
        Toggle current = roles.getSelectedToggle();
        if (current == null) {
            roles.selectToggle(roles.getToggles().getFirst());
            return Role.TITLE;
        }
        return (Role) current.getUserData();
    }

    private void openHtml() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a local HTML chapter");
        directoryHistory.configure(chooser, DirectoryHistory.Kind.HTML);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML files", "*.html", "*.htm"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        directoryHistory.remember(DirectoryHistory.Kind.HTML, file.toPath());
        try {
            loadHtml(file.toPath());
        } catch (IOException ex) {
            report("Could not read the HTML file", ex);
        }
    }

    void loadHtml(Path path) throws IOException {
        HtmlSource source = HtmlSource.read(path);
        sourcePath = path;
        sourceHtml = source.html();
        cssCache = null;
        originalPageUrl.setText(LinkedCssLoader.originalPage(sourceHtml).map(URI::toString).orElse(""));
        exclusionSingle = "";
        exclusionClasses = "";
        updateExclusionMatches();
        long replacements = source.replacementCharacters();
        encodingInfo.setText("Encoding: " + source.charset().name()
            + (replacements == 0 ? "" : " — " + replacements
                + " replacement character(s) already in the source; re-export the HTML to recover them."));
        String filename = path.getFileName().toString();
        String baseName = filename.replaceFirst("(?i)\\.html?$", "");
        if (profileName.getText().isBlank()) profileName.setText(baseName);
        openedFile.setText(filename);
        preview.clear();
        reloadPreview();
    }

    private void reloadPreview() {
        if (sourceHtml == null) return;
        if (cssTask != null) cssTask.cancel(true);
        int generation = ++previewGeneration;
        if (!cssEnabled.isSelected()) {
            cssStatus.setText("CSS disabled.");
            cssStatus.setTooltip(null);
            displayPreview(false, Map.of());
            return;
        }
        String originText = originalPageUrl.getText().strip();
        URI origin;
        try {
            origin = originText.isEmpty() ? null : URI.create(originText);
            if (origin != null && (origin.getHost() == null || origin.getUserInfo() != null
                || !("https".equalsIgnoreCase(origin.getScheme()) || "http".equalsIgnoreCase(origin.getScheme())))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            cssStatus.setText("Enter an HTTP(S) original page URL, or leave it blank for local CSS.");
            displayPreview(true, Map.of());
            return;
        }
        if (cssCache != null && originText.equals(cachedCssOrigin)) {
            showCssResult(cssCache);
            displayPreview(true, cssCache.styles());
            return;
        }
        // Keep the new document usable while CSS is fetched away from the application thread.
        displayPreview(true, Map.of());
        cssStatus.setText("Loading linked stylesheets...");
        cssStatus.setTooltip(null);
        String html = sourceHtml;
        Path path = sourcePath;
        Task<LinkedCssLoader.Result> task = new Task<>() {
            @Override protected LinkedCssLoader.Result call() throws InterruptedException {
                return new LinkedCssLoader().load(html, path, origin);
            }
        };
        cssTask = task;
        task.setOnSucceeded(e -> {
            if (generation != previewGeneration) return;
            cssCache = task.getValue();
            cachedCssOrigin = originText;
            showCssResult(cssCache);
            displayPreview(true, cssCache.styles());
        });
        task.setOnFailed(e -> {
            if (generation == previewGeneration) cssStatus.setText("Could not load linked CSS: "
                + task.getException().getMessage());
        });
        Thread.ofVirtual().start(task);
    }

    private void displayPreview(boolean enableCss, Map<String, String> styles) {
        browser.getEngine().loadContent(HtmlPreviewSanitizer.sanitize(sourceHtml, enableCss, styles), "text/html");
        status.setText("Loading HTML preview...");
    }

    private void showCssResult(LinkedCssLoader.Result result) {
        cssStatus.setText("Linked CSS: " + result.styles().size() + " loaded, " + result.messages().size()
            + " unavailable." + (result.messages().isEmpty() ? "" : " Hover for details."));
        cssStatus.setTooltip(new Tooltip(result.summary()));
    }

    private void applyExclusionScope() {
        if (exclusionSingle.isEmpty()) return;
        selectors.get(Role.EXCLUDE).setText(groupExclusions.isSelected() && !exclusionClasses.isEmpty()
            ? exclusionClasses : exclusionSingle);
        updateExclusionMatches();
    }

    private void updateExclusionMatches() {
        String selector = selectors.get(Role.EXCLUDE).getText().strip();
        if (sourceHtml == null || selector.isEmpty()) {
            exclusionMatches.setText("Select a block to exclude.");
            return;
        }
        try {
            int count = Jsoup.parse(sourceHtml).select(selector).size();
            boolean classless = groupExclusions.isSelected() && exclusionClasses.isEmpty()
                && !exclusionSingle.isEmpty() && selector.equals(exclusionSingle);
            exclusionMatches.setText(count + " matching element(s) in this chapter."
                + (classless ? " This block has no classes; only this element is targeted." : ""));
        } catch (RuntimeException ex) {
            exclusionMatches.setText("Invalid exclusion selector.");
        }
    }

    private void addExclusion() {
        String selector = selectors.get(Role.EXCLUDE).getText().strip();
        if (selector.isEmpty()) {
            status.setText("Select a block to exclude first.");
            return;
        }
        try {
            // Validate independently of title/content fields, which may still be empty.
            Jsoup.parse(sourceHtml == null ? "" : sourceHtml).select(selector);
            if (!exclusions.getItems().contains(selector)) exclusions.getItems().add(selector);
            status.setText("Excluded: " + selector + ". Preview extraction to check the result.");
        } catch (RuntimeException ex) {
            status.setText("Invalid exclusion: " + ex.getMessage());
        }
    }

    private void syncExclusions() {
        if (sourceHtml == null || browser.getEngine().getLoadWorker().getState() != Worker.State.SUCCEEDED) return;
        try {
            browser.getEngine().executeScript("if (window.__ebookPicker) window.__ebookPicker.setExclusions("
                + json.writeValueAsString(exclusions.getItems()) + ")");
        } catch (Exception ex) {
            status.setText("Could not highlight exclusions: " + ex.getMessage());
        }
    }

    void pollSelection() {
        if (sourceHtml == null) return;
        try {
            Object value = browser.getEngine().executeScript(
                "window.__ebookPicker ? window.__ebookPicker.takeSelection() : null");
            if (!(value instanceof String payload) || payload.isBlank()) return;
            JsonNode data = json.readTree(payload);
            String selector = data.path("selector").asText("");
            if (selector.isBlank()) return;
            Role role = activeRole();
            if (role == Role.EXCLUDE) {
                exclusionSingle = selector;
                exclusionClasses = data.path("classSelector").asText("");
                applyExclusionScope();
            } else {
                selectors.get(role).setText(selector);
            }
            String text = data.path("text").asText("");
            if (text.length() > 75) text = text.substring(0, 75) + "...";
            status.setText(role + " selected: " + selectors.get(role).getText() + "  —  " + text);
        } catch (Exception ex) {
            // WebEngine can briefly be unavailable while a document is loading.
            if (browser.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED) {
                status.setText("Selection error: " + ex.getMessage());
            }
        }
    }

    private void chooseParent() {
        if (sourceHtml == null) return;
        try {
            browser.getEngine().executeScript(
                "if (window.__ebookPicker) window.__ebookPicker.selectParent()");
        } catch (RuntimeException ex) {
            report("Could not select the parent element", ex);
        }
    }

    private ParserProfile buildProfile(boolean allowPreviewDefaults) {
        String name = profileName.getText().strip();
        String domain = domainName.getText().strip();
        if (allowPreviewDefaults) {
            if (name.isBlank()) name = "Preview";
            if (domain.isBlank()) domain = "preview.local";
        }
        return new ParserProfile(name, domain, selectors.get(Role.TITLE).getText(),
            selectors.get(Role.CONTENT).getText(), selectors.get(Role.NEXT).getText(),
            java.util.List.copyOf(exclusions.getItems()));
    }

    private void previewExtraction() {
        if (sourcePath == null || sourceHtml == null) {
            status.setText("Open a local HTML file first.");
            return;
        }
        try {
            ParserProfile profile = buildProfile(true);
            URI uri = sourcePath.toUri();
            ParsedChapter result = parser.parse(sourceHtml, uri, profile);
            StringBuilder text = new StringBuilder(result.chapter().title())
                .append("\n\n").append(result.chapter().content());
            if (!profile.nextChapterSelector().isBlank()) {
                Element next = Jsoup.parse(sourceHtml).selectFirst(profile.nextChapterSelector());
                text.append("\n\nNEXT LINK: ")
                    .append(next == null ? "(not found)" :
                        next.hasAttr("href") ? next.attr("href") : next.attr("data-href"));
            }
            preview.setText(text.toString());
            status.setText("Extraction valid. The preview uses the original HTML source.");
        } catch (Exception ex) {
            status.setText("Preview failed: " + ex.getMessage());
        }
    }

    private void saveProfile() {
        try {
            ParserProfile profile = buildProfile(false);
            if (sourceHtml != null && sourcePath != null) {
                // Do not save selectors that fail to extract the chosen example.
                parser.parse(sourceHtml, sourcePath.toUri(), profile);
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save parser profile");
            directoryHistory.configure(chooser, DirectoryHistory.Kind.PROFILE);
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Parser profile (*.json)", "*.json"));
            chooser.setInitialFileName(profile.domain().replaceAll("[^a-z0-9.-]", "_") + ".json");
            java.io.File file = chooser.showSaveDialog(stage);
            if (file == null) return;
            directoryHistory.remember(DirectoryHistory.Kind.PROFILE, file.toPath());
            store.save(file.toPath(), profile);
            status.setText("Profile saved: " + file.getAbsolutePath());
        } catch (Exception ex) {
            report("Could not save the profile", ex);
        }
    }

    private void loadProfile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load a parser profile");
        directoryHistory.configure(chooser, DirectoryHistory.Kind.PROFILE);
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Parser profile (*.json)", "*.json"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        directoryHistory.remember(DirectoryHistory.Kind.PROFILE, file.toPath());
        try {
            ParserProfile profile = store.load(file.toPath());
            profileName.setText(profile.name());
            domainName.setText(profile.domain());
            selectors.get(Role.TITLE).setText(profile.titleSelector());
            selectors.get(Role.CONTENT).setText(profile.contentSelector());
            selectors.get(Role.NEXT).setText(profile.nextChapterSelector());
            selectors.get(Role.EXCLUDE).clear();
            exclusions.getItems().setAll(profile.excludedSelectors());
            status.setText("Loaded profile: " + file.getName());
        } catch (Exception ex) {
            report("Could not load the profile", ex);
        }
    }

    private void report(String message, Throwable error) {
        String details = error == null ? "Unknown error" : error.getMessage();
        status.setText(message + ": " + details);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setHeaderText(message);
        alert.setContentText(details);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        if (selectionPoller != null) selectionPoller.stop();
        if (cssTask != null) cssTask.cancel(true);
        if (generationPane != null) generationPane.cancelGeneration();
    }
}
