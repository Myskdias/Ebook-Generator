# Ebook Generator

Local web-novel-to-EPUB desktop application (in development). The original implementation
is preserved on the [`old` branch](https://github.com/Myskdias/Ebook-Generator/tree/old);
ongoing development happens on `main`.

## Requirements

- JDK 25 (LTS) for this development version.
- The included Gradle Wrapper handles Gradle installation.

## Visual parser editor (first working prototype)

The JavaFX visual parser editor can now open an **existing local HTML file**.
It shows a sanitized WebView preview. Choose each field on the right, click its
corresponding element in the preview, and use **Select parent element** when a click
selects only a child node. The editor produces a persistent JSON parser profile.

On Linux or macOS:

```sh
./gradlew parserEditor
```

On Windows:

```powershell
.\gradlew.bat parserEditor
```

1. Choose **Open HTML...** to select a downloaded chapter.
2. Choose **Chapter title** and click the title in the HTML preview.
3. Choose **Chapter content** and click the enclosing content container.
4. Choose **Next chapter** and click its link or button (optional for local import).
5. Enter a **profile name** and **website domain** (e.g. `example.com`).
6. Use **Preview extraction** to test the selectors against the source HTML.
7. Save the JSON profile with **Save JSON profile...**.

Use **Preview CSS (embedded / inline)** to toggle a conservative subset of the
page's presentation styles. Changing it reloads only the preview; selected profile
fields and exclusions are retained, and extraction always uses the source HTML.
Linked stylesheets (including local ones), images, fonts and other external resources
remain blocked. CSS imports, URL values, variables, escapes and at-rules are discarded.
This is an offline selection aid, not a full-fidelity browser rendering.

To remove advertising or other unwanted content, choose **Block to exclude**, click
the block, optionally use **Select parent element**, and click **Add exclusion**.
Empty containers left behind by removed ad scripts have dashed placeholders so they
can still be selected. Saved exclusions have red outlines. Select an entry and use
**Remove selected** to undo it. You can edit the candidate selector before adding it;
for example, `.ads` excludes every matching ad container. Use **Preview extraction**
to check the result. Translator notes are kept unless explicitly excluded.

For safety, the offline preview strips embedded scripts, iframes, remote images and
external CSS and sets a restrictive Content Security Policy in both CSS modes.
It does not execute the site's JavaScript or expose Java objects to the page.
Picker selectors refer to the original DOM, so removing unsafe siblings from the
preview does not change positional selectors used for extraction.
Sites that populate chapters only after JavaScript execution will need a browser-based
downloader in a later milestone. This is a **development tool**, not yet a packaged app.

## Generate an EPUB from local HTML

Linux/macOS:

```sh
./gradlew run --args='--title "My Novel" --author "An Author" --language en --output build/my-novel.epub src/test/resources/chapters/chapter-01.html src/test/resources/chapters/chapter-02.html'
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The order of the input chapters is the order of the file arguments.

### Using a saved site profile

Profiles contain CSS selectors for the chapter title, content, and optional
next-chapter link. Example JSON:

```json
{
  "name": "Local HTML articles",
  "domain": "example.com",
  "titleSelector": "article h1",
  "contentSelector": "article",
  "nextChapterSelector": "",
  "excludedSelectors": [".ads", ".advertisement"]
}
```

Save profiles through the visual editor, or edit the JSON manually. To use one:

```sh
./gradlew run --args='--title "My Novel" --author "An Author" --profile src/test/resources/profiles/article.json --output build/profiled.epub src/test/resources/chapters/chapter-01.html src/test/resources/chapters/chapter-02.html'
```

The `domain` field is metadata for future profile matching, not a network
permission or an automatic download target.

`excludedSelectors` is optional: existing profiles load with an empty exclusion
list. Exclusions are validated and deduplicated when loading/saving, and apply in
both the editor and `--profile` imports. They are resolved against the full source
DOM before removing matches, preserving contextual selectors and navigation links.

### Character encodings

The editor and both import paths use the same reader: a BOM takes priority over
HTML charset metadata, with UTF-8 as the fallback. Invalid byte sequences fail with
an encoding error instead of silently becoming replacement characters. The editor
shows the detected encoding and warns if `U+FFFD` replacement characters already
exist in the decoded source (including HTML entities).

In the supplied NovelFull chapter, `Eyes..` is followed by `U+1D62F` and `U+1D635`
(mathematical sans-serif italic letters n and t). The file is valid UTF-8 with no
replacement characters. On Windows, JavaFX `loadContent` converted those two
supplementary characters into twelve `U+FFFD` characters. The preview now serializes
non-ASCII characters as HTML entities before handing them to WebView; extraction
keeps the original characters. No speculative text repair or normalization is applied.
The synthetic `novelfull-sample.html` fixture preserves this exact reproduction
string, alongside accents, smart quotes, CJK, emoji and repeated advertising blocks.

## Test

```sh
./gradlew test epubcheckSample
```

JUnit tests inspect EPUB packaging, XML, navigation, encoding detection, malformed
input, exclusions, legacy JSON profiles, and CSS preview sanitization. Real WebView
integration tests check Unicode rendering, CSS on/off, the picker, exclusion
highlighting, and CSP resource blocking. `epubcheckSample` runs EPUBCheck against a
generated two-chapter EPUB. On a Linux machine without a display, use
`xvfb-run -a ./gradlew test epubcheckSample` (requires Xvfb and JavaFX GTK libraries).

GitHub Actions builds and tests the source on Linux, Windows, and macOS.
Linux runs the WebView tests under Xvfb. Test reports are uploaded for each OS.
File chooser interactions and the complete editor workflow still need manual testing.

**Current limitations:** the extraction model stores plain text. Preserving inline
formatting and images, remote downloading, and standalone installers are still
future milestones.
