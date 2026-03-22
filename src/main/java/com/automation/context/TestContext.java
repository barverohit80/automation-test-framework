package com.automation.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized TestContext — scoped per Cucumber scenario (via cucumber-glue scope).
 *
 * Stores arbitrary objects during scenario execution so they can be reused
 * across different step definition classes without tight coupling.
 *
 * Thread-safe: uses ConcurrentHashMap for parallel execution.
 *
 * Usage in step definitions:
 *   testContext.set("orderId", "ORD-12345");
 *   String orderId = testContext.get("orderId", String.class);
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class TestContext {

    private final Map<String, Object> contextMap = new ConcurrentHashMap<>();

    /**
     * Store an object by key.
     */
    public void set(String key, Object value) {
        log.debug("[Thread-{}] TestContext SET: {} = {}",
                Thread.currentThread().getId(), key, value);
        contextMap.put(key, value);
    }

    /**
     * Retrieve an object by key with type casting.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = contextMap.get(key);
        if (value == null) {
            log.warn("[Thread-{}] TestContext GET: key '{}' not found", Thread.currentThread().getId(), key);
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(String.format(
                    "TestContext key '%s' expected type %s but found %s",
                    key, type.getSimpleName(), value.getClass().getSimpleName()));
        }
        return (T) value;
    }

    /**
     * Retrieve a string value (convenience method).
     */
    public String getString(String key) {
        return get(key, String.class);
    }

    /**
     * Retrieve an integer value (convenience method).
     */
    public Integer getInt(String key) {
        return get(key, Integer.class);
    }

    /**
     * Check if a key exists.
     */
    public boolean containsKey(String key) {
        return contextMap.containsKey(key);
    }

    /**
     * Remove a specific key.
     */
    public void remove(String key) {
        contextMap.remove(key);
    }

    /**
     * Clear all stored context (called after each scenario).
     */
    public void clear() {
        log.debug("[Thread-{}] TestContext CLEARED ({} entries)",
                Thread.currentThread().getId(), contextMap.size());
        contextMap.clear();
    }

    /**
     * Get current context size (useful for debugging).
     */
    public int size() {
        return contextMap.size();
    }

    /**
     * Get a snapshot of all keys (for logging/debugging).
     */
    public java.util.Set<String> keys() {
        return contextMap.keySet();
    }

    @Override
    public String toString() {
        return "TestContext{entries=" + contextMap.size() + ", keys=" + contextMap.keySet() + "}";
    }
}
