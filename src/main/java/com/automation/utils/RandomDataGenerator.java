package com.automation.utils;

import com.automation.config.EnvironmentConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

/**
 * Generates random alphanumeric, numeric, and other test data values.
 * Used by Cucumber parameter types to resolve placeholders at runtime.
 *
 * Thread-safe: each call uses its own random generation.
 */
@Slf4j
@Component
public class RandomDataGenerator {

    private final EnvironmentConfig config;
    private final Random random;

    @Autowired
    public RandomDataGenerator(EnvironmentConfig config) {
        this.config = config;
        long seed = config.getData().getRandomSeed();
        this.random = (seed == 0) ? new SecureRandom() : new Random(seed);
    }

    /**
     * Generate a random alphanumeric string.
     * Example output: "DEV_aBcD1234eF"
     */
    public String generateAlphanumeric() {
        int length = config.getData().getAlphanumericLength();
        String value = RandomStringUtils.randomAlphanumeric(length);
        String result = config.getData().getPrefix() + "_" + value;
        log.debug("Generated alphanumeric: {}", result);
        return result;
    }

    /**
     * Generate a random alphanumeric string with a custom length.
     */
    public String generateAlphanumeric(int length) {
        String value = RandomStringUtils.randomAlphanumeric(length);
        return config.getData().getPrefix() + "_" + value;
    }

    /**
     * Generate a random numeric string.
     * Example output: "12345678"
     */
    public String generateNumeric() {
        int length = config.getData().getNumericLength();
        String result = RandomStringUtils.randomNumeric(length);
        log.debug("Generated numeric: {}", result);
        return result;
    }

    /**
     * Generate a random numeric string with a custom length.
     */
    public String generateNumeric(int length) {
        return RandomStringUtils.randomNumeric(length);
    }

    /**
     * Generate a random email address.
     * Example output: "DEV_user_aBcDe@test.com"
     */
    public String generateEmail() {
        String username = config.getData().getPrefix().toLowerCase()
                + "_user_" + RandomStringUtils.randomAlphabetic(5).toLowerCase();
        return username + "@test.com";
    }

    /**
     * Generate a random phone number.
     * Example: "+1555" + 7 random digits
     */
    public String generatePhoneNumber() {
        return "+1555" + RandomStringUtils.randomNumeric(7);
    }

    /**
     * Generate a UUID.
     */
    public String generateUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a timestamped string (useful for unique names).
     * Example: "DEV_20240315_143022_abc"
     */
    public String generateTimestamped() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String suffix = RandomStringUtils.randomAlphabetic(3).toLowerCase();
        return config.getData().getPrefix() + "_" + timestamp + "_" + suffix;
    }

    /**
     * Generate a random integer within a range.
     */
    public int generateRandomInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    /**
     * Generate a random alphabetic string.
     */
    public String generateAlphabetic(int length) {
        return RandomStringUtils.randomAlphabetic(length);
    }
}
