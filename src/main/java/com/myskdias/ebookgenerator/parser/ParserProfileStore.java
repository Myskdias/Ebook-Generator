package com.myskdias.ebookgenerator.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Persists user-selected CSS selectors as JSON. No site-specific Java source is needed.
 * The caller chooses the path, so profile names cannot become filesystem paths.
 */
public final class ParserProfileStore {
    private final ObjectMapper json = new ObjectMapper();

    public ParserProfile load(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        return json.readValue(file.toFile(), ParserProfile.class);
    }

    public void save(Path file, ParserProfile profile) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(profile, "profile");
        Path destination = file.toAbsolutePath().normalize();
        if (!destination.getFileName().toString().endsWith(".json")) {
            throw new IllegalArgumentException("Profile file must end in .json");
        }
        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".profile-", ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), profile);
            try {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
