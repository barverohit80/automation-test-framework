package com.automation.locator.store;

import com.automation.locator.model.PageLocators;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages locator persistence: loading from classpath, saving to file system, in-memory caching.
 *
 * Locator files are stored as JSON: src/main/resources/locators/{pageName}.json
 *
 * Usage:
 *   PageLocators page = locatorStore.load("LoginPage");
 *   locatorStore.save(page);
 */
@Slf4j
@Component
public class LocatorStore {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, PageLocators> cache = new ConcurrentHashMap<>();

    @Value("${app.locators.resource-dir:locators/}")
    private String resourceDir;

    @Value("${app.locators.file-dir:src/main/resources/locators/}")
    private String fileDir;

    /**
     * Load locators from classpath (resources/locators/{pageName}.json).
     * Returns null if file does not exist.
     * Results are cached in memory.
     */
    public PageLocators load(String pageName) {
        if (cache.containsKey(pageName)) {
            log.debug("[LocatorStore] Cache hit for page: {}", pageName);
            return cache.get(pageName);
        }

        try {
            String resourcePath = resourceDir + pageName + ".json";
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            java.net.URL resourceUrl = classLoader.getResource(resourcePath);

            if (resourceUrl == null) {
                log.debug("[LocatorStore] Resource not found: {}", resourcePath);
                return null;
            }

            File file = new File(resourceUrl.getFile());
            PageLocators pageLocators = mapper.readValue(file, PageLocators.class);
            cache.put(pageName, pageLocators);
            log.info("[LocatorStore] Loaded locators for page: {} (elements: {})",
                    pageName, pageLocators.getElements().size());
            return pageLocators;
        } catch (Exception e) {
            log.warn("[LocatorStore] Failed to load locators for page '{}': {}", pageName, e.getMessage());
            return null;
        }
    }

    /**
     * Save locators to file system (src/main/resources/locators/{pageName}.json).
     * Creates directory if it doesn't exist.
     * Updates in-memory cache.
     */
    public void save(PageLocators pageLocators) {
        try {
            String pageName = pageLocators.getPageName();

            // Create directory if needed
            Path dirPath = Paths.get(fileDir);
            Files.createDirectories(dirPath);

            // Write JSON file
            Path filePath = dirPath.resolve(pageName + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), pageLocators);

            // Update cache
            cache.put(pageName, pageLocators);

            log.info("[LocatorStore] Saved locators for page: {} (elements: {}) to {}",
                    pageName, pageLocators.getElements().size(), filePath);
        } catch (Exception e) {
            log.error("[LocatorStore] Failed to save locators for page '{}': {}",
                    pageLocators.getPageName(), e.getMessage(), e);
        }
    }

    /**
     * Get cached locators without loading from disk.
     * Returns null if not in cache.
     */
    public PageLocators getFromCache(String pageName) {
        return cache.get(pageName);
    }

    /**
     * Clear the in-memory cache for testing purposes.
     */
    public void clearCache() {
        cache.clear();
        log.debug("[LocatorStore] Cache cleared");
    }
}
