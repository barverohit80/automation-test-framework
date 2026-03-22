package com.automation.utils;

import com.automation.config.EnvironmentConfig;
import com.automation.driver.DriverFactory;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class ScreenshotUtils {

    @Autowired
    private DriverFactory driverFactory;

    @Autowired
    private EnvironmentConfig config;

    /**
     * Capture screenshot as byte array (for embedding in reports).
     */
    public byte[] captureScreenshot() {
        if (!driverFactory.hasDriver()) return new byte[0];
        try {
            return ((TakesScreenshot) driverFactory.getDriver()).getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            log.error("Failed to capture screenshot: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Save screenshot to the configured directory.
     */
    public String saveScreenshot(String scenarioName) {
        if (!driverFactory.hasDriver()) return null;

        try {
            byte[] screenshot = captureScreenshot();
            if (screenshot.length == 0) return null;

            String dir = config.getReporting().getScreenshotDir();
            Files.createDirectories(Paths.get(dir));

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String safeName = scenarioName.replaceAll("[^a-zA-Z0-9]", "_");
            String fileName = safeName + "_" + timestamp + ".png";
            Path filePath = Paths.get(dir, fileName);

            Files.write(filePath, screenshot);
            log.info("Screenshot saved: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("Failed to save screenshot: {}", e.getMessage());
            return null;
        }
    }
}
