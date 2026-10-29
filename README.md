# Ebook Generator

Web-novel-to-EPUB desktop application for local files and web URLs (in development). The original implementation
is preserved on the [`old` branch](https://github.com/Myskdias/Ebook-Generator/tree/old);
ongoing development happens on `main`.

## Requirements

- JDK 25 (LTS) for this development version.
- The included Gradle Wrapper handles Gradle installation.

## Download, choose a profile, then generate

Launch with `./gradlew parserEditor` on Linux/macOS or `.\gradlew.bat parserEditor`
on Windows. The desktop app has three separate tabs:

1. **Download.** Enter the first chapter URL, choose an empty folder and select all
   following chapters or a count (including the starting chapter). No extraction
   profile is needed. Next links are detected from standard navigation attributes
   and labels; an optional next-link selector can resolve unusual or ambiguous sites.
   Click **Download / resume**. Each HTML page is saved immediately, before extraction.
2. **Profile.** Click **Profile the saved chapters** to preview the first saved page.
   Load a JSON profile or create one visually for the title, content and exclusions.
   Check the extraction, then click **Use this profile for the EPUB**. A next-chapter
   selector in the extraction profile is not required for this saved download.
3. **EPUB.** Generate from all saved chapters or a selected count, optionally setting
   title, author and language. This step reads local files in the recorded download
   order and has no chapter downloader. The profile can be changed and another EPUB
   generated without downloading anything again.

The download folder contains `chapters/000001.html`, etc., plus
`chapter-download.json`, which records source URLs, order and completion status.
Keep this file with its `chapters` folder. **Open downloaded book...** reopens it
after restarting the app. To extend a numbered download or retry after cancellation
or a failed request, reopen the same folder and click **Download / resume**; already
saved pages are checked and reused. Downloads never overwrite unrelated HTML files.

The saved HTML retains the page content, including advertising, so exclusions can
be decided later. It is serialized as UTF-8 with matching charset metadata and its
original source URL for CSS preview resolution. The saved pages are checked for
missing or changed files before conversion. Re-downloading and extraction are
independent operations; cancelling EPUB generation keeps the source pages intact.

**Use existing HTML files...** in the first tab also supports chapters downloaded
with another tool. Select the first local HTML file, then load/create a profile.
For this older local-file workflow, its next selector follows links within that
file's folder or subfolders. It does not switch to downloading online links.

The downloader fetches HTML only, without running scripts or loading subresources.
It waits one second between pages, stays on the source domain or its subdomains,
and limits a page to 4 MiB and a book to 10,000 chapters. HTTP errors and cycles stop
the download while keeping saved pages. **All chapters** ends when no next link is
found; check the reported count and set a custom navigation selector if more pages
were expected. Incomplete downloads are labelled when reopened for conversion.
Login, browser-script and challenge pages need a separate browser-based downloader.

For saved pages from the current NovelFull layout, an example extraction profile is
included at [`examples/profiles/novelfull-web.json`](examples/profiles/novelfull-web.json).
The full staged workflow was checked with two live chapters. Older HTTrack archives
can use their existing extraction profiles.

## Visual parser editor

The **2. Profile** tab can open an **existing local HTML file**.
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
   **Use this profile for the EPUB** applies it to the saved book and opens step 3;
   saving the JSON first is optional.

File dialogs remember the last HTML, profile, download and EPUB output folders separately,
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

The `domain` field is metadata for local extraction profiles. Download navigation
uses the source URL's domain and is configured independently of this profile.

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
atomic output and the three desktop stages. Download tests cover profile-independent
navigation, saved order, resuming, source integrity and repeated offline extraction
with different profiles. HTTP tests use local
servers rather than depending on public sites. `epubcheckSample` runs EPUBCheck against a
generated two-chapter EPUB. On a Linux machine without a display, use
`xvfb-run -a ./gradlew test epubcheckSample` (requires Xvfb and JavaFX GTK libraries).

GitHub Actions builds and tests the source on Linux, Windows, and macOS.
Linux runs the WebView tests under Xvfb. Test reports are uploaded for each OS.
File chooser interactions and the complete editor workflow still need manual testing.

**Current limitations:** the extraction model stores plain text. Preserving inline
formatting and images, pages requiring browser execution, and standalone installers
are still future milestones.
