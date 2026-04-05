package com.automation.uitestgen.writer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

/**
 * Writes generated test artifacts (Page Objects, feature files, step definitions)
 * to disk. Creates a versioned backup if the file already exists.
 */
@Slf4j
@Component
public class UITestFileWriter {

    /**
     * Writes content to path. If the file already exists, creates a backup.
     * Returns the absolute path of the written file.
     */
    public String write(String content, String relativePath, String label) throws IOException {
        Path path = Paths.get(relativePath).toAbsolutePath();
        Files.createDirectories(path.getParent());

        if (Files.exists(path)) {
            Path backup = path.resolveSibling(path.getFileName() + ".bak");
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            log.info("[UITestGen] Backed up existing {} -> {}", label, backup.getFileName());
        }

        Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("[UITestGen] Wrote {} -> {}", label, path);
        return path.toString();
    }
}
