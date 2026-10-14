package com.myskdias.ebookgenerator;

import com.myskdias.ebookgenerator.epub.EpubGenerator;
import com.myskdias.ebookgenerator.model.Book;
import com.myskdias.ebookgenerator.model.Chapter;
import com.myskdias.ebookgenerator.parser.HtmlChapterImporter;
import com.myskdias.ebookgenerator.parser.ParserProfile;
import com.myskdias.ebookgenerator.parser.ParserProfileStore;
import com.myskdias.ebookgenerator.parser.ProfiledHtmlParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Developer-facing CLI for the first EPUB core milestone. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            usage();
            return;
        }

        String title = null;
        String author = null;
        String language = "en";
        Path output = null;
        Path profilePath = null;
        List<Path> inputs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--title" -> title = requireValue(args, ++i, "--title");
                case "--author" -> author = requireValue(args, ++i, "--author");
                case "--language" -> language = requireValue(args, ++i, "--language");
                case "--output" -> output = Path.of(requireValue(args, ++i, "--output"));
                case "--profile" -> profilePath = Path.of(requireValue(args, ++i, "--profile"));
                default -> {
                    if (args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
                    inputs.add(Path.of(args[i]));
                }
            }
        }
        if (title == null || author == null || output == null || inputs.isEmpty()) {
            usage();
            throw new IllegalArgumentException("Provide title, author, output and at least one HTML chapter");
        }

        HtmlChapterImporter importer = new HtmlChapterImporter();
        ParserProfile profile = profilePath == null ? null : new ParserProfileStore().load(profilePath);
        ProfiledHtmlParser profiledParser = new ProfiledHtmlParser();
        List<Chapter> chapters = new ArrayList<>();
        for (Path input : inputs) {
            if (profile == null) {
                chapters.add(importer.importFile(input));
            } else {
                String html = Files.readString(input, StandardCharsets.UTF_8);
                chapters.add(profiledParser.parse(html, input.toUri(), profile).chapter());
            }
        }
        Path file = new EpubGenerator().generate(new Book(title, author, language, chapters), output);
        System.out.println("Generated " + file + " (" + chapters.size() + " chapters)");
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException("Expected a value after " + flag);
        }
        return args[index];
    }

    private static void usage() {
        System.out.println("Usage: --title TITLE --author AUTHOR [--language en] [--profile PROFILE.json] --output BOOK.epub CHAPTER1.html [CHAPTER2.html ...]");
    }
}
