# Ebook Generator

Local web-novel-to-EPUB desktop application (in development). The original implementation
is preserved on the [`old` branch](https://github.com/Myskdias/Ebook-Generator/tree/old);
ongoing development happens on `main`.

## Requirements

- JDK 25 (LTS) for this development version.
- The included Gradle Wrapper handles Gradle installation.

## Generate an EPUB from local HTML

Linux/macOS:

```sh
./gradlew run --args='--title "My Novel" --author "An Author" --language en --output build/my-novel.epub src/test/resources/chapters/chapter-01.html src/test/resources/chapters/chapter-02.html'
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The input chapter order is the order of the file arguments. The default HTML importer
makes a best effort to find chapter headings and paragraphs.

## Reusable site profiles (new)

A parser profile is a JSON file containing the CSS selectors for a chapter title, content,
and optional next-chapter link. No Java source edits or recompilation are necessary.
For example, `src/test/resources/profiles/article.json`:

```json
{
  "name": "Local HTML articles",
  "domain": "example.com",
  "titleSelector": "article h1",
  "contentSelector": "article",
  "nextChapterSelector": ""
}
```

Pass a saved profile to the CLI:

```sh
./gradlew run --args='--title "My Novel" --author "An Author" --profile src/test/resources/profiles/article.json --output build/profiled.epub src/test/resources/chapters/chapter-01.html src/test/resources/chapters/chapter-02.html'
```

The profile-aware parser returns both the extracted chapter and the resolved next-chapter
URL (for a later downloader). The current CLI only imports the explicitly listed local
HTML files; it does not crawl sites. The `domain` field is metadata for future profile
matching, not a network permission or an automatic download target.

**Current limitation:** chapter content is plain text. Inline formatting, images, a
visual DOM selector, network downloading and the desktop UI are future milestones.

## Test

```sh
./gradlew test epubcheckSample
```

The JUnit suite tests EPUB packaging, XML, navigation, imported HTML,
CSS selector profiles, JSON round-tripping, and CLI integration.
`epubcheckSample` runs EPUBCheck against a generated two-chapter EPUB.
GitHub Actions runs the checks on Linux, Windows, and macOS.

A standalone GUI and installers are not available yet.
