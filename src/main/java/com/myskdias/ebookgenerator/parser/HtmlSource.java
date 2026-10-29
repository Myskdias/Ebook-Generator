package com.myskdias.ebookgenerator.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;

/** One decoding path for the editor, profiled CLI, and generic importer. */
public record HtmlSource(String html, Charset charset) {
    public static HtmlSource read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        // Jsoup detects a BOM first, then HTML charset metadata, then defaults to UTF-8.
        Charset charset = Jsoup.parse(new ByteArrayInputStream(bytes), null,
            path.toUri().toString()).charset();
        try {
            String html = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
            if (html.startsWith("\uFEFF")) html = html.substring(1);
            return new HtmlSource(html, charset);
        } catch (CharacterCodingException ex) {
            throw new IOException("Cannot decode " + path.getFileName() + " as " + charset.name()
                + ". Check the file's declared encoding; invalid bytes were not replaced.", ex);
        }
    }

    /** Already-lost characters cannot be reconstructed by changing the preview's encoding. */
    public long replacementCharacters() {
        return Jsoup.parse(html).text().codePoints().filter(cp -> cp == 0xFFFD).count();
    }
}
