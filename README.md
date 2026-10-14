# Ebook Generator

Local web-novel-to-EPUB desktop application (in development). The first milestone is a
**Java EPUB core** that produces an EPUB 3 file from local HTML chapters. No Calibre is
needed for this sample.

The previous Java implementation is preserved on the [`old` branch](https://github.com/Myskdias/Ebook-Generator/tree/old).
Development continues on `main`.

## Requirements

- JDK 25 (LTS) to build or run this development version.
- The included Gradle Wrapper downloads Gradle automatically. No separate Gradle installation is required.

## Generate an EPUB

On Linux or macOS:

```sh
./gradlew run --args='--title "My Novel" --author "An Author" --language en --output build/my-novel.epub src/test/resources/chapters/chapter-01.html src/test/resources/chapters/chapter-02.html'
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The chapter order is exactly the order of the HTML files provided. In this initial
version, the local HTML importer looks for an `article`, `main`, or chapter content
container, then extracts the paragraphs (`<p>`). It uses a page heading as the chapter
title. It is deliberately **not yet** a general-purpose site parser.

You can also create a sample EPUB from the included fixtures:

```sh
./gradlew generateSample
```

The result will be `build/samples/demo.epub`.

## Test

```sh
./gradlew test epubcheckSample
```

The JUnit tests inspect the EPUB ZIP layout, XHTML, metadata, navigation, ordering,
Unicode, and XML escaping. The separate `epubcheckSample` task runs the official
EPUBCheck 5.3.0 validator on a generated sample.

GitHub Actions runs these checks on Linux, Windows, and macOS.

## Current design

```text
model/        Book and Chapter: validated, immutable book data
parser/       Simple local HTML importer (Jsoup), replaceable later
epub/         EPUB 3 packaging, navigation, XHTML and metadata
Main.java     Minimal CLI that joins the importer and EPUB generator
```

The `Chapter.content` field is **plain text**. Paragraphs are separated by blank
lines. The EPUB generator escapes all XML-sensitive characters; it does not interpret
its input as arbitrary HTML. Preserving inline formatting and images is planned for a
later model iteration.

## Next steps

1. Stable parser profiles and visual selection of DOM elements.
2. Download manager with cache, progress and resumable jobs.
3. JavaFX interface and standalone installers for Windows, macOS and Linux.

For now, this is a developer-facing CLI, not the final standalone desktop app.
