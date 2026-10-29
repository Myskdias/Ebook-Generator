# Ebook Generator

Web-novel-to-EPUB desktop application for local files and web URLs (in development). The original implementation
is preserved on the [`old` branch](https://github.com/Myskdias/Ebook-Generator/tree/old);
ongoing development happens on `main`.

## Requirements

- JDK 25 (LTS) for this development version.
- The included Gradle Wrapper handles Gradle installation.

## Generate a book from its first chapter

Launch the desktop app with `./gradlew parserEditor` on Linux/macOS or
`.\gradlew.bat parserEditor` on Windows. **Generate EPUB** is the first tab.

1. Choose the **first HTML chapter**, or paste its HTTP(S) **web URL**.
2. Choose the saved **JSON parser profile** for that version of the site.
3. Choose **All chapters from the first chapter** or **A number of chapters**.
   The count includes the selected chapter: choosing 10 imports it and 9 successors.
4. Optionally fill in the book title, author and language. Otherwise they are detected
   from the first page where possible, with its chapter title, `Unknown` and `en` as fallbacks.
5. Click **Generate EPUB...**, choose the destination, and follow progress. **Cancel**
   stops reading and writing without publishing a partial EPUB.

The profile's next-chapter selector determines the order; filenames are never sorted
or guessed. Local chapters must already exist in the first file's folder or subfolders.
Missing files stop the job with the failing chapter and path. Local mode does not
silently switch to downloading if a link points to a website: start from a web URL
to download the sequence instead. The EPUB output folder is remembered separately.

Online mode fetches only the HTML pages, without executing JavaScript or downloading
scripts, images, ads or fonts. It waits one second between chapters and restricts URLs
and redirects to the profile's domain or its subdomains. HTTP errors, invalid text,
unsupported links, cycles and duplicate chapters stop the job. A page is limited to
4 MiB and a book to 10,000 chapters. Sites that require login, browser scripts or a
challenge page need a separate browser-based downloader; this app does not bypass them.

**All chapters** stops when the profile finds no usable next link. The completion
message reports that reason and the exact chapter count, so check the profile if
you expected more chapters. A numbered import may also end sooner if the chain ends.
The destination is written only after the sequence imports successfully; an existing
EPUB is left intact on an import error or cancellation.

For the current online NovelFull layout, an example profile is included at
[`examples/profiles/novelfull-web.json`](examples/profiles/novelfull-web.json).
It was checked against two live chapters. The live markup differs from older HTTrack
archives, so use your existing archive profile for those downloaded files.

## Visual parser editor

The **Profile editor** tab can open an **existing local HTML file**.
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
   **Generate EPUB from this chapter...** transfers the current chapter and profile
   directly to the generation tab; saving the profile first is optional.

File dialogs remember the last HTML, profile and EPUB output folders separately,
including after restarting the editor. Loading and saving profiles share the profile
folder. The first dialog of a kind can reuse the most recently chosen folder;
cancelling a dialog does not reset it. If a saved folder has been removed, the dialog
starts in its nearest available parent. Folder history is stored in local OS user
preferences; if that store is unavailable, it is still retained for the current session.

Use **Preview CSS** to toggle the page's presentation styles, including linked CSS.
The application reads relative `.css` files within the HTML folder (including saved-page
subfolders). When a stylesheet is unavailable locally, HTTP(S) links can be fetched in
the background. Relative web links use **Original page URL**, detected automatically
from HTTrack comments or canonical/OG metadata when available; otherwise enter that URL
and press **Reload CSS**. The CSS status shows how many sheets loaded and explains
missing files or HTTP errors in its tooltip. Loaded sheets are cached for CSS toggles;
**Reload CSS** fetches them again.

Every sheet is filtered and embedded in the isolated preview in its original order.
Common typography/layout declarations, `!important` and responsive `@media` rules are
supported. CSS imports, URL values, variables, escapes, web fonts and other at-rules
are discarded. Scripts, images and other embedded resources remain blocked. The CSS
loader uses bounded requests without cookies or authentication, rejects private
network addresses and unsafe redirects, and limits each sheet to 1 MiB (12 per page).
This remains a selection aid, not a full-fidelity browser rendering. Changing CSS
retains profile fields and exclusions; extraction always uses the original HTML.

To remove advertising or other unwanted content, choose **Block to exclude**, click
the block, optionally use **Select parent element**, and click **Add exclusion**.
**All elements with the same classes** is selected by default: selecting an ad
container proposes e.g. `div.ads.ads-holder.ads-middle.text-center` rather than a
positional path. The match count tells you how many blocks it will exclude. Uncheck
the option to target only the clicked block. If the clicked child has no classes,
select its parent until you reach the classed container. A selector such as `.ads`
can also be entered manually. Unique IDs/classes are preferred for title/navigation
selectors too, falling back to source positions only when needed to distinguish nodes.
The checkbox always remains usable, even before selecting anything or when a block
has no classes. Its choice is kept when selecting another block or opening another
HTML page. Blocks without classes keep their individual selector in either mode.
Empty containers left behind by removed ad scripts have dashed placeholders so they
can still be selected. Saved exclusions have red outlines. Select an entry and use
**Remove selected** to undo it. You can edit the candidate selector before adding it;
for example, `.ads` excludes every matching ad container. Use **Preview extraction**
to check the result. Translator notes are kept unless explicitly excluded.

For safety, the preview strips embedded scripts, iframes and remote images and sets
a restrictive Content Security Policy in both CSS modes. WebView itself cannot fetch
resources; the application fetches only linked stylesheets when CSS is enabled.
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

The `domain` field is metadata for local files. Online imports require chapter URLs
and redirects to stay on this domain or its subdomains.

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
integration tests check Unicode rendering, linked/responsive CSS on/off, the picker,
class-wide exclusions across reordered chapters, and CSP resource blocking.
Stylesheet loader tests cover local paths, original URL detection, link resolution,
missing resources, filtering and loading limits. Book-generation tests cover local
and HTTP chapter chains, counts, encoding, redirects, cycles, missing pages, cancellation,
atomic output and both generation modes in the desktop form. HTTP tests use local
servers rather than depending on public sites. `epubcheckSample` runs EPUBCheck against a
generated two-chapter EPUB. On a Linux machine without a display, use
`xvfb-run -a ./gradlew test epubcheckSample` (requires Xvfb and JavaFX GTK libraries).

GitHub Actions builds and tests the source on Linux, Windows, and macOS.
Linux runs the WebView tests under Xvfb. Test reports are uploaded for each OS.
File chooser interactions and the complete editor workflow still need manual testing.

**Current limitations:** the extraction model stores plain text. Preserving inline
formatting and images, pages requiring browser execution, and standalone installers
are still future milestones.
