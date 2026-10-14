package com.myskdias.ebookgenerator.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myskdias.ebookgenerator.parser.ParsedChapter;
import com.myskdias.ebookgenerator.parser.ParserProfile;
import com.myskdias.ebookgenerator.parser.ParserProfileStore;
import com.myskdias.ebookgenerator.parser.ProfiledHtmlParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
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
    private enum Role { TITLE, CONTENT, NEXT }

    private final ParserProfileStore store = new ParserProfileStore();
    private final ProfiledHtmlParser parser = new ProfiledHtmlParser();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Role, TextField> selectors = new EnumMap<>(Role.class);

    private WebView browser;
    private TextField profileName;
    private TextField domainName;
    private TextArea preview;
    private Label status;
    private Label openedFile;
    private ToggleGroup roles;

    private Stage stage;
    private Path sourcePath;
    private String sourceHtml;
    private Timeline selectionPoller;
    private String selectionScript;
    private Button parentButton;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        // WebView/WebEngine and controls must be created on the JavaFX Application Thread.
        browser = new WebView();
        profileName = new TextField();
        domainName = new TextField();
        preview = new TextArea();
        status = new Label("Open a local HTML chapter to begin.");
        openedFile = new Label("No HTML file selected");
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
        HBox header = new HBox(12, openHtml, loadProfile, openedFile);
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
                    status.setText("Page ready. Select a field on the right, then click its element.");
                } catch (RuntimeException ex) {
                    report("Could not install the element picker", ex);
                }
            } else if (current == Worker.State.FAILED) {
                report("Could not open the HTML preview", browser.getEngine().getLoadWorker().getException());
            }
        });

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

        primaryStage.setTitle("Ebook Generator — Visual parser editor");
        primaryStage.setScene(new Scene(layout, 1260, 830));
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
            new Label("Website domain"), domainName, new Separator(),
            new Label("1. Select a field; 2. Click its element in the page."));

        addRole(panel, Role.TITLE, "Chapter title", "e.g. h2.chapter-title");
        addRole(panel, Role.CONTENT, "Chapter content", "e.g. div#chapter-content");
        addRole(panel, Role.NEXT, "Next chapter (optional)", "e.g. a.next-chapter");

        parentButton = new Button("Select parent element");
        parentButton.setOnAction(e -> chooseParent());
        parentButton.setMaxWidth(Double.MAX_VALUE);
        panel.getChildren().add(parentButton);

        Label explanation = new Label(
            "Click the whole chapter container, not only one paragraph. "
            + "Use 'Select parent element' if your click selects a child.");
        explanation.setWrapText(true);
        panel.getChildren().addAll(explanation, new Separator());

        Button inspect = new Button("Preview extraction");
        inspect.setMaxWidth(Double.MAX_VALUE);
        inspect.setOnAction(e -> previewExtraction());

        Button save = new Button("Save JSON profile...");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(e -> saveProfile());
        panel.getChildren().addAll(inspect, save, new Label("Extracted chapter"), preview);
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
        choice.setUserData(role);
        choice.setToggleGroup(roles);
        choice.setMaxWidth(Double.MAX_VALUE);

        TextField input = new TextField();
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
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML files", "*.html", "*.htm"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        try {
            sourcePath = file.toPath();
            sourceHtml = Files.readString(sourcePath, StandardCharsets.UTF_8);
            String baseName = file.getName().replaceFirst("(?i)\\.html?$", "");
            if (profileName.getText().isBlank()) profileName.setText(baseName);
            openedFile.setText(file.getName());
            preview.clear();
            browser.getEngine().loadContent(HtmlPreviewSanitizer.sanitize(sourceHtml), "text/html");
            status.setText("Loading HTML preview...");
        } catch (IOException ex) {
            sourceHtml = null;
            sourcePath = null;
            report("Could not read the HTML file", ex);
        }
    }

    private void pollSelection() {
        if (sourceHtml == null) return;
        try {
            Object value = browser.getEngine().executeScript(
                "window.__ebookPicker ? window.__ebookPicker.takeSelection() : null");
            if (!(value instanceof String payload) || payload.isBlank()) return;
            JsonNode data = json.readTree(payload);
            String selector = data.path("selector").asText("");
            if (selector.isBlank()) return;
            Role role = activeRole();
            selectors.get(role).setText(selector);
            String text = data.path("text").asText("");
            if (text.length() > 75) text = text.substring(0, 75) + "...";
            status.setText(role + " selected: " + selector + "  —  " + text);
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
            selectors.get(Role.CONTENT).getText(), selectors.get(Role.NEXT).getText());
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
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Parser profile (*.json)", "*.json"));
            chooser.setInitialFileName(profile.domain().replaceAll("[^a-z0-9.-]", "_") + ".json");
            java.io.File file = chooser.showSaveDialog(stage);
            if (file == null) return;
            store.save(file.toPath(), profile);
            status.setText("Profile saved: " + file.getAbsolutePath());
        } catch (Exception ex) {
            report("Could not save the profile", ex);
        }
    }

    private void loadProfile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load a parser profile");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Parser profile (*.json)", "*.json"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        try {
            ParserProfile profile = store.load(file.toPath());
            profileName.setText(profile.name());
            domainName.setText(profile.domain());
            selectors.get(Role.TITLE).setText(profile.titleSelector());
            selectors.get(Role.CONTENT).setText(profile.contentSelector());
            selectors.get(Role.NEXT).setText(profile.nextChapterSelector());
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
    }
}
