# Ebook Generator

Local web-novel-to-EPUB desktop application (in development). The original implementation
is preserved on the [\`old\` branch](https://github.com/Myskdias/Ebook-Generator/tree/old);
ongoing development happens on \`main\`.

## Requirements

- JDK 25 (LTS) for this development version.
- The included Gradle Wrapper handles Gradle installation.

## Visual parser editor (first working prototype)

The JavaFX visual parser editor can now open an **existing local HTML file**.
It shows a sanitized WebView preview. Choose each field on the right, click its
corresponding element in the preview, and use **Select parent element** when a click
selects only a child node. The editor produces a persistent JSON parser profile.

On Linux or macOS:

\`\`\`sh
./gradlew parserEditor
\`\`\`

On Windows:

\`\`\`powershell
.\gradlew.bat parserEditor
\`\`\`

1. Choose **Open HTML...** to select a downloaded chapter.
2. Choose **Chapter title** and click the title in the HTML preview.
3. Choose **Chapter content** and click the enclosing content container.
4. Choose **Next chapter** and click its link or button (optional for local import).
5. Enter a **profile name** and **website domain** (e.g. \`example.com\`).
6. Use **Preview extraction** to test the selectors against the source HTML.
7. Save the JSON profile with **Save JSON profile...**.

For safety, the offline preview strips embedded scripts, iframes, remote images and
external CSS. It does not execute the site's JavaScript or load remote web pages.
Selectors are generated from the preview and tested against the original HTML.
Sites that populate chapters only after JavaScript execution will need a browser-based
downloader in a later milestone. This is a **development tool**, not yet a packaged app.

## Generate an EPUB from local HTML

Linux/macOS:

\`\`\`sh
./gradlew run --args='--title "My Novel" --author "An Author" --language en --output build/my-novel.epub src/test/resources/chapters/chapter-01.html src/test/resources/chapters/chapter-02.html'
\`\`\`

On Windows, use \`gradlew.bat\` instead of \`./gradlew\`.

The order of the input chapters is the order of the file arguments.

### Using a saved site profile

Profiles contain CSS selectors for the chapter title, content, and optional
next-chapter link. Example JSON:

\`\`\`json
{
  "name": "Local HTML articles",
  "domain": "example.com",
  "titleSelector": "article h1",
  "contentSelector": "article",
  "nextChapterSelector": ""
}
\`\`\`

Save profiles through the visual editor, or edit the JSON manually. To use one:

\`\`\`sh
./gradlew run --args='--title "My Novel" --author "An Author" --profile src/test/resources/profiles/article.json --output build/profiled.epub src/test/resources/chapters/chapter-01.html src/test/resources/chapters/chapter-02.html'
\`\`\`

The \`domain\` field is metadata for future profile matching, not a network
permission or an automatic download target.

## Test

\`\`\`sh
./gradlew test epubcheckSample
\`\`\`

JUnit tests inspect EPUB packaging, XML, navigation, local HTML import, CSS profiles,
JSON round-trip, and HTML preview sanitization. \`epubcheckSample\` runs EPUBCheck
against a generated two-chapter EPUB.

GitHub Actions builds and tests the source on Linux, Windows, and macOS.
The JavaFX UI itself is not exercised by automated headless tests yet.

**Current limitations:** the extraction model stores plain text. Preserving inline
formatting and images, remote downloading, and standalone installers are still
future milestones.
