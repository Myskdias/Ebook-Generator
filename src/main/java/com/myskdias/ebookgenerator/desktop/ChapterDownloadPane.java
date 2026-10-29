package com.myskdias.ebookgenerator.desktop;

import com.myskdias.ebookgenerator.download.ChapterDownloadService;
import com.myskdias.ebookgenerator.download.DownloadArchive;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/** Step 1 downloads durable HTML without loading or applying an extraction profile. */
final class ChapterDownloadPane extends ScrollPane {
    private final DirectoryHistory directories;
    private final Consumer<Path> continueToProfile;
    private final ChapterDownloadService service;
    private final TextField firstUrl = new TextField();
    private final TextField folder = new TextField();
    private final TextField nextSelector = new TextField();
    private final ComboBox<BookGenerationPane.Scope> scope = new ComboBox<>();
    private final Spinner<Integer> count = new Spinner<>(1, 10_000, 10);
    private final Label status = new Label("Download chapters before choosing an extraction profile.");
    private final ProgressBar progress = new ProgressBar(0);
    private final Button continueButton = new Button("2. Load or create a profile");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private Path manifest;
    private AtomicBoolean cancellation;
    private Thread worker;
    private Consumer<Path> existingHtml = path -> {};

    ChapterDownloadPane(DirectoryHistory directories, Consumer<Path> continueToProfile) {
        this(directories, continueToProfile, new ChapterDownloadService());
    }

    ChapterDownloadPane(DirectoryHistory directories, Consumer<Path> continueToProfile, ChapterDownloadService service) {
        this.directories = directories;
        this.continueToProfile = continueToProfile;
        this.service = service;
        setFitToWidth(true);
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(24));
        panel.setMaxWidth(900);
        Label heading = new Label("1. Download the chapters");
        heading.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label explanation = new Label("Save the HTML pages first. No extraction profile is needed here. "
            + "Next-chapter links are detected automatically. The saved pages can be reopened and reused later.");
        explanation.setWrapText(true);
        firstUrl.setId("download-first-url");
        firstUrl.setPromptText("https://.../first-chapter.html");
        folder.setId("download-folder");
        folder.setPromptText("An empty folder, or the same downloaded-book folder to resume");
        Button chooseFolder = new Button("Choose folder...");
        chooseFolder.setOnAction(e -> chooseFolder());
        HBox destination = new HBox(10, folder, chooseFolder);
        HBox.setHgrow(folder, Priority.ALWAYS);
        scope.getItems().addAll(BookGenerationPane.Scope.ALL, BookGenerationPane.Scope.COUNT);
        scope.setValue(BookGenerationPane.Scope.ALL);
        scope.setId("download-scope");
        count.setEditable(true);
        count.setId("download-count");
        count.disableProperty().bind(scope.valueProperty().isEqualTo(BookGenerationPane.Scope.ALL));
        nextSelector.setId("download-next-selector");
        nextSelector.setPromptText("Automatic — leave empty unless the site's next link needs a custom CSS selector");
        VBox form = new VBox(10, new Label("First chapter URL"), firstUrl,
            new Label("Download folder"), destination,
            new Label("Chapters to download"), new HBox(12, scope, count),
            new Label("The count includes the selected first chapter."),
            new Label("Next-link selector (optional navigation setting)"), nextSelector);
        form.disableProperty().bind(busy);
        Button download = new Button("Download / resume");
        download.setId("download-chapters");
        download.disableProperty().bind(busy);
        download.setOnAction(e -> {
            try { startDownload(); }
            catch (Exception ex) { status.setText(ex.getMessage()); }
        });
        Button open = new Button("Open downloaded book...");
        open.disableProperty().bind(busy);
        open.setOnAction(e -> chooseArchive());
        Button cancel = new Button("Cancel");
        cancel.disableProperty().bind(busy.not());
        cancel.setOnAction(e -> cancelDownload());
        Button local = new Button("Use existing HTML files...");
        local.disableProperty().bind(busy);
        local.setOnAction(e -> chooseExistingHtml());
        continueButton.setDisable(true);
        continueButton.setId("download-to-profile");
        continueButton.setOnAction(e -> { if (manifest != null) continueToProfile.accept(manifest); });
        progress.setMaxWidth(Double.MAX_VALUE);
        status.setWrapText(true);
        status.setId("download-status");
        panel.getChildren().addAll(heading, explanation, form, new HBox(12, download, cancel, open),
            progress, status, continueButton, local);
        setContent(panel);
    }

    void onExistingHtml(Consumer<Path> action) { existingHtml = action; }

    private void chooseExistingHtml() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the first saved HTML chapter");
        directories.configure(chooser, DirectoryHistory.Kind.HTML);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML files", "*.html", "*.htm"));
        var selected = chooser.showOpenDialog(getScene().getWindow());
        if (selected != null) {
            directories.remember(DirectoryHistory.Kind.HTML, selected.toPath());
            existingHtml.accept(selected.toPath());
        }
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose an empty folder for this book, or its previous download folder");
        Path previous = directories.initialDirectory(DirectoryHistory.Kind.DOWNLOAD);
        if (previous != null) chooser.setInitialDirectory(previous.toFile());
        var selected = chooser.showDialog(getScene().getWindow());
        if (selected == null) return;
        folder.setText(selected.getAbsolutePath());
        directories.rememberDirectory(DirectoryHistory.Kind.DOWNLOAD, selected.toPath());
    }

    private void chooseArchive() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open " + DownloadArchive.FILE_NAME);
        directories.configure(chooser, DirectoryHistory.Kind.DOWNLOAD);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Downloaded book", "*.json"));
        var selected = chooser.showOpenDialog(getScene().getWindow());
        if (selected == null) return;
        try { openArchive(selected.toPath()); }
        catch (Exception ex) { status.setText("Could not open downloaded book: " + ex.getMessage()); }
    }

    void openArchive(Path path) throws java.io.IOException {
        var archive = DownloadArchive.load(path);
        manifest = path.toAbsolutePath();
        firstUrl.setText(archive.firstUrl().toString());
        folder.setText(manifest.getParent().toString());
        nextSelector.setText(archive.nextSelector());
        scope.setValue(archive.requestedChapters() == 0 ? BookGenerationPane.Scope.ALL : BookGenerationPane.Scope.COUNT);
        if (archive.requestedChapters() > 0) count.getValueFactory().setValue(archive.requestedChapters());
        directories.rememberDirectory(DirectoryHistory.Kind.DOWNLOAD, manifest.getParent());
        showSaved(archive);
    }

    private void showSaved(DownloadArchive archive) {
        continueButton.setDisable(archive.pages().isEmpty() || busy.get());
        continueButton.setText("2. Profile the " + archive.pages().size() + " saved chapter(s)");
        status.setText(archive.pages().size() + " chapter(s) saved. " + archive.message());
    }

    Task<ChapterDownloadService.Result> startDownload() {
        if (busy.get()) throw new IllegalStateException("A download is already running");
        if (firstUrl.getText().isBlank() || folder.getText().isBlank()) {
            throw new IllegalArgumentException("Enter the first chapter URL and choose a download folder");
        }
        int limit;
        try { limit = scope.getValue() == BookGenerationPane.Scope.ALL ? 0 : Integer.parseInt(count.getEditor().getText().strip()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a whole number of chapters"); }
        if (scope.getValue() == BookGenerationPane.Scope.COUNT && limit < 1) throw new IllegalArgumentException("Choose at least one chapter");
        var request = new ChapterDownloadService.Request(URI.create(firstUrl.getText().strip()),
            Path.of(folder.getText().strip()), limit, nextSelector.getText());
        directories.rememberDirectory(DirectoryHistory.Kind.DOWNLOAD, request.folder());
        cancellation = new AtomicBoolean();
        var cancelled = cancellation;
        manifest = request.folder().resolve(DownloadArchive.FILE_NAME);
        var task = new Task<ChapterDownloadService.Result>() {
            @Override protected ChapterDownloadService.Result call() throws Exception {
                updateMessage("Downloading HTML pages...");
                return service.download(request, item -> {
                    updateMessage(item.chapters() + " chapter(s) saved — " + item.url());
                    updateProgress(item.chapters(), request.maxChapters() > 0 ? request.maxChapters() : -1);
                }, cancelled::get);
            }
        };
        busy.set(true);
        continueButton.setDisable(true);
        status.textProperty().bind(task.messageProperty());
        progress.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> {
            finished(); progress.setProgress(1);
            showSaved(task.getValue().archive());
        });
        task.setOnFailed(e -> {
            finished();
            int saved = 0;
            if (Files.isRegularFile(manifest)) {
                try { var archive = DownloadArchive.load(manifest); saved = archive.pages().size(); showSaved(archive); }
                catch (Exception ignored) { }
            }
            status.setText((cancelled.get() ? "Download cancelled. " : "Download stopped: " + task.getException().getMessage() + ". ")
                + saved + " chapter(s) kept. Resume in the same folder to continue.");
        });
        worker = new Thread(task, "chapter-download");
        worker.setDaemon(false);
        worker.start();
        return task;
    }

    private void finished() {
        status.textProperty().unbind(); progress.progressProperty().unbind(); busy.set(false);
    }

    void cancelDownload() {
        if (!busy.get()) return;
        cancellation.set(true);
        if (worker != null) worker.interrupt();
    }
}
