package com.automation.runner;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Maven Surefire entry point for: mvn test
 *
 * This class MUST live in src/test/java because Surefire only scans
 * test sources for @Suite classes. It delegates to all the glue code
 * (steps, hooks, param types) that lives in src/main/java so the same
 * code is available both for "mvn test" and "java -jar".
 *
 * How it works:
 *   1. Surefire finds this class (name ends with "Test" or has @Suite)
 *   2. JUnit Platform Suite starts the "cucumber" engine
 *   3. Cucumber scans the GLUE package (com.automation) — which is in src/main
 *   4. Cucumber loads features from FEATURES path
 *   5. Spring Boot context is bootstrapped via CucumberSpringConfiguration (in src/main)
 *
 * Run:
 *   mvn clean test                                  # all scenarios
 *   mvn test -Dcucumber.filter.tags="@smoke"        # only @smoke
 *   mvn test -Dcucumber.filter.tags="@newbrowser"   # only fresh-session tests
 *   mvn test -Dthread.count=8                       # 8 parallel threads
 *   mvn test -Puat                                  # UAT environment
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("com.automation")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.automation")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/main/resources/features")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,"
             + "html:target/cucumber-reports/report.html,"
             + "json:target/cucumber-reports/report.json,"
             + "timeline:target/cucumber-reports/timeline")
public class RunCucumberTest {
}
