package com.myskdias.ebookgenerator.desktop;

import com.myskdias.ebookgenerator.BookGenerationService;
import com.myskdias.ebookgenerator.parser.BookImporter;
import com.myskdias.ebookgenerator.parser.ParserProfile;
import com.myskdias.ebookgenerator.parser.ParserProfileStore;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** User workflow: first chapter + reusable profile, then all chapters or a requested count. */
final class BookGenerationPane extends ScrollPane {
    @FunctionalInterface interface Generator {
        BookGenerationService.Result generate(BookGenerationService.Request request,
            java.util.function.Consumer<BookImporter.Progress> progress,
            java.util.function.BooleanSupplier cancelled) throws Exception;
    }
    enum Scope {
        ALL("All chapters from the first chapter"), COUNT("A number of chapters");
        private final String label;
        Scope(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final DirectoryHistory directories;
    private final Generator generator;
    private final TextField firstChapter = new TextField();
    private final TextField profileName = new TextField();
    private final TextField bookTitle = new TextField();
    private final TextField author = new TextField();
    private final TextField language = new TextField();
    private final ComboBox<Scope> scope = new ComboBox<>();
    private final Spinner<Integer> count = new Spinner<>(1, BookImporter.MAX_CHAPTERS, 10);
    private final Label status = new Label("Choose the first chapter and a parser profile.");
    private final TextField output = new TextField();
    private final ProgressBar progress = new ProgressBar(0);
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private ParserProfile profile;
    private AtomicBoolean cancellation;
    private Thread worker;

    BookGenerationPane(DirectoryHistory directories) {
        this(directories, new BookGenerationService()::generate);
    }

    BookGenerationPane(DirectoryHistory directories, Generator generator) {
        this.directories = directories;
        this.generator = generator;
        setFitToWidth(true);
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(24));
        panel.setMaxWidth(900);
        Label heading = new Label("Generate an EPUB");
        heading.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label explanation = new Label("Start with one local HTML file or a web URL. The profile extracts each chapter "
            + "and follows its next-chapter link, applying the same exclusions throughout. "
            + "For local files, following chapters must already be saved in the same folder or its subfolders.");
        explanation.setWrapText(true);
        firstChapter.setId("generation-first-chapter");
        firstChapter.setPromptText("Path to the first HTML file, or https://...");
        Button chooseHtml = new Button("Choose HTML...");
        chooseHtml.setOnAction(e -> chooseHtml());
        profileName.setEditable(false);
        profileName.setPromptText("Choose a saved JSON profile");
        Button chooseProfile = new Button("Choose profile...");
        chooseProfile.setOnAction(e -> chooseProfile());
        scope.getItems().addAll(Scope.ALL, Scope.COUNT);
        scope.setValue(Scope.ALL);
        scope.setId("generation-scope");
        count.setEditable(true);
        count.setId("generation-count");
        count.disableProperty().bind(scope.valueProperty().isEqualTo(Scope.ALL));
        bookTitle.setPromptText("Detected from the first chapter if empty");
        author.setPromptText("Detected from the page, or Unknown if empty");
        language.setPromptText("Detected from the page, or en if empty");
        VBox form = new VBox(10,
            new Label("First chapter (local file or web address)"), row(firstChapter, chooseHtml),
            new Label("Parser profile"), row(profileName, chooseProfile),
            new Label("Chapters to include"), new HBox(12, scope, count),
            new Label("The count includes the selected first chapter."),
            new Label("Book title (optional)"), bookTitle,
            new Label("Author (optional)"), author, new Label("Language (optional, e.g. en or fr)"), language);
        form.disableProperty().bind(busy);
        Button generate = new Button("Generate EPUB...");
        generate.setId("generate-epub");
        generate.disableProperty().bind(busy);
        generate.setOnAction(e -> chooseOutputAndGenerate());
        Button cancel = new Button("Cancel");
        cancel.disableProperty().bind(busy.not());
        cancel.setOnAction(e -> cancelGeneration());
        progress.setMaxWidth(Double.MAX_VALUE);
        status.setWrapText(true);
        status.setId("generation-status");
        output.setEditable(false);
        output.setPromptText("The generated EPUB location will appear here.");
        output.setId("generation-output");
        panel.getChildren().addAll(heading, explanation, form, new HBox(12, generate, cancel),
            progress, status, new Label("EPUB file"), output);
        setContent(panel);
    }

    private static HBox row(TextField field, Button button) {
        HBox row = new HBox(10, field, button);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    void useProfile(ParserProfile profile, String description) {
        this.profile = profile;
        profileName.setText(profile.name() + " — " + description);
    }

    void useFirstChapter(Path path) { firstChapter.setText(path.toAbsolutePath().toString()); }

    private FileChooser chooser(String title, DirectoryHistory.Kind kind, String description, String... extensions) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        directories.configure(chooser, kind);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extensions));
        return chooser;
    }

    private void chooseHtml() {
        var file = chooser("Choose the first chapter", DirectoryHistory.Kind.HTML,
            "HTML files", "*.html", "*.htm", "*.xhtml").showOpenDialog(getScene().getWindow());
        if (file == null) return;
        directories.remember(DirectoryHistory.Kind.HTML, file.toPath());
        useFirstChapter(file.toPath());
    }

    private void chooseProfile() {
        var file = chooser("Choose a parser profile", DirectoryHistory.Kind.PROFILE,
            "JSON profiles", "*.json").showOpenDialog(getScene().getWindow());
        if (file == null) return;
        directories.remember(DirectoryHistory.Kind.PROFILE, file.toPath());
        try { useProfile(new ParserProfileStore().load(file.toPath()), file.getName()); }
        catch (Exception ex) { status.setText("Could not load the profile: " + ex.getMessage()); }
    }

    private void chooseOutputAndGenerate() {
        try {
            // Validate before opening the save dialog so errors keep the user in the form.
            request(Path.of("book.epub"));
            var chooser = chooser("Save the EPUB", DirectoryHistory.Kind.EPUB, "EPUB books", "*.epub");
            String name = bookTitle.getText().isBlank() ? profile.name() : bookTitle.getText().strip();
            chooser.setInitialFileName(name.replaceAll("[^\\p{L}\\p{N} ._-]", "_") + ".epub");
            var file = chooser.showSaveDialog(getScene().getWindow());
            if (file == null) return;
            directories.remember(DirectoryHistory.Kind.EPUB, file.toPath());
            startGeneration(file.toPath());
        } catch (Exception ex) { status.setText(ex.getMessage()); }
    }

    private BookGenerationService.Request request(Path output) {
        if (profile == null) throw new IllegalArgumentException("Choose a parser profile first.");
        String source = firstChapter.getText().strip();
        if (source.isEmpty()) throw new IllegalArgumentException("Choose the first HTML chapter or enter its web URL.");
        URI uri;
        try {
            if (source.matches("(?is)^https?://.*") || source.startsWith("file:")) uri = URI.create(source);
            else uri = Path.of(source).toAbsolutePath().normalize().toUri();
            if ("file".equalsIgnoreCase(uri.getScheme()) && !Files.isRegularFile(Path.of(uri))) {
                throw new IllegalArgumentException("First chapter file not found: " + source);
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid first chapter: " + ex.getMessage(), ex);
        }
        int limit = 0;
        if (scope.getValue() == Scope.COUNT) {
            try { limit = Integer.parseInt(count.getEditor().getText().strip()); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a whole number of chapters."); }
            if (limit < 1 || limit > BookImporter.MAX_CHAPTERS) {
                throw new IllegalArgumentException("Choose 1 to " + BookImporter.MAX_CHAPTERS + " chapters.");
            }
        }
        return new BookGenerationService.Request(uri, profile, limit,
            bookTitle.getText(), author.getText(), language.getText(), output);
    }

    Task<BookGenerationService.Result> startGeneration(Path destination) {
        if (busy.get()) throw new IllegalStateException("A generation is already running.");
        var request = request(destination);
        cancellation = new AtomicBoolean(false);
        var cancelled = cancellation;
        var task = new Task<BookGenerationService.Result>() {
            @Override protected BookGenerationService.Result call() throws Exception {
                updateMessage("Reading the first chapter...");
                return generator.generate(request, item -> {
                    updateMessage("Read " + item.chapters() + " chapter(s) — " + item.title());
                    updateProgress(item.chapters(), request.maxChapters() > 0 ? request.maxChapters() : -1);
                }, cancelled::get);
            }
        };
        status.textProperty().bind(task.messageProperty());
        progress.progressProperty().bind(task.progressProperty());
        output.setText(destination.toAbsolutePath().toString());
        busy.set(true);
        task.setOnSucceeded(e -> {
            finished();
            var result = task.getValue();
            progress.setProgress(1);
            status.setText("EPUB generated: " + result.chapters() + " chapter(s). "
                + (result.stop() == BookImporter.Stop.CHAPTER_LIMIT ? "Requested count reached."
                    : "No next-chapter link found after the last imported chapter."));
            output.setText(result.output().toString());
        });
        task.setOnFailed(e -> {
            finished();
            var error = task.getException();
            boolean wasCancelled = cancelled.get() || error instanceof CancellationException || error instanceof InterruptedException;
            status.setText(wasCancelled ? "Generation cancelled. No new EPUB was saved."
                : "Generation failed: " + error.getMessage() + ". No new EPUB was saved.");
        });
        worker = new Thread(task, "ebook-generation");
        worker.setDaemon(false); // Let cancellation clean up a temporary EPUB when the window closes.
        worker.start();
        return task;
    }

    private void finished() {
        status.textProperty().unbind();
        progress.progressProperty().unbind();
        busy.set(false);
    }

    void cancelGeneration() {
        if (!busy.get()) return;
        cancellation.set(true);
        if (worker != null) worker.interrupt();
    }
}
