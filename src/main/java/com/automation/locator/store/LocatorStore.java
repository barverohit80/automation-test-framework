package com.automation.locator.store;

import com.automation.locator.model.PageLocators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and saves PageLocators JSON files from/to resources/locators/.
 *
 * Read path:  classpath:locators/{pageName}.json  (works in packaged JAR)
 * Write path: src/main/resources/locators/{pageName}.json  (for extraction)
 *
 * Caches loaded locators in memory per page to avoid repeated disk I/O.
 */
@Slf4j
@Component
public class LocatorStore {

    private static final String LOCATORS_RESOURCE_DIR = "locators/";
    private static final String LOCATORS_FILE_DIR = "src/main/resources/locators/";

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final ConcurrentHashMap<String, PageLocators> cache = new ConcurrentHashMap<>();

    /**
     * Load locators for a page from classpath (resources/locators/{pageName}.json).
     * Returns null if the file doesn't exist.
     */
    public PageLocators load(String pageName) {
        // Check cache first
        PageLocators cached = cache.get(pageName);
        if (cached != null) return cached;

        String resourcePath = LOCATORS_RESOURCE_DIR + pageName + ".json";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.debug("[LocatorStore] No locator file found: {}", resourcePath);
                return null;
            }
            PageLocators loaded = mapper.readValue(is, PageLocators.class);
            cache.put(pageName, loaded);
            log.info("[LocatorStore] Loaded locators for '{}' ({} elements)",
                    pageName, loaded.getElements().size());
            return loaded;
        } catch (IOException e) {
            log.error("[LocatorStore] Failed to load {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    /**
     * Save extracted locators to src/main/resources/locators/{pageName}.json.
     * Also updates the in-memory cache.
     */
    public void save(PageLocators pageLocators) throws IOException {
        String pageName = pageLocators.getPageName();
        Path dir = Paths.get(LOCATORS_FILE_DIR);
        Files.createDirectories(dir);

        Path filePath = dir.resolve(pageName + ".json");

        // Backup existing file
        if (Files.exists(filePath)) {
            Path backup = filePath.resolveSibling(pageName + ".json.bak");
            Files.copy(filePath, backup, StandardCopyOption.REPLACE_EXISTING);
            log.info("[LocatorStore] Backed up existing: {}", backup.getFileName());
        }

        mapper.writeValue(filePath.toFile(), pageLocators);
        cache.put(pageName, pageLocators);

        log.info("[LocatorStore] Saved locators for '{}' -> {} ({} elements)",
                pageName, filePath, pageLocators.getElements().size());
    }

    /**
     * Clear cached locators (e.g., after re-extraction).
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Clear cached locators for a specific page.
     */
    public void clearCache(String pageName) {
        cache.remove(pageName);
    }
}
