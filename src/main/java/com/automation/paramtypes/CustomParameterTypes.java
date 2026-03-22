package com.automation.paramtypes;

import com.automation.context.TestContext;
import com.automation.utils.RandomDataGenerator;
import io.cucumber.java.ParameterType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Custom Cucumber parameter types for runtime random value generation.
 *
 * Feature file usage (use the placeholder word WITHOUT braces):
 *   randomAlphanumeric  -> "DEV_aB3cD4eF5g"
 *   randomNumeric       -> "83749201"
 *   randomEmail         -> "dev_user_abcde@test.com"
 *   randomUUID          -> UUID string
 *   randomAlpha5        -> "aBcDe" (5 alphabetic chars)
 *   randomNum4          -> "7382"  (4 numeric digits)
 *   randomTimestamp     -> "DEV_20240315_143022_abc"
 *   randomPhone         -> "+15553847291"
 *
 * Step definition usage (Cucumber Expression parameter reference):
 *   @When("the user enters name {randomAlphanumeric}")
 *   public void enterName(String name) { ... }
 *
 * Feature file step text:
 *   When the user enters name randomAlphanumeric
 */
@Slf4j
public class CustomParameterTypes {

    @Autowired private RandomDataGenerator dataGenerator;
    @Autowired private TestContext testContext;

    @ParameterType("randomAlphanumeric")
    public String randomAlphanumeric(String input) {
        String v = dataGenerator.generateAlphanumeric();
        testContext.set("lastRandomAlphanumeric", v);
        log.info("Generated randomAlphanumeric: {}", v);
        return v;
    }

    @ParameterType("randomNumeric")
    public String randomNumeric(String input) {
        String v = dataGenerator.generateNumeric();
        testContext.set("lastRandomNumeric", v);
        log.info("Generated randomNumeric: {}", v);
        return v;
    }

    @ParameterType("randomEmail")
    public String randomEmail(String input) {
        String v = dataGenerator.generateEmail();
        testContext.set("lastRandomEmail", v);
        log.info("Generated randomEmail: {}", v);
        return v;
    }

    @ParameterType("randomUUID")
    public String randomUUID(String input) {
        String v = dataGenerator.generateUUID();
        testContext.set("lastRandomUUID", v);
        return v;
    }

    @ParameterType("randomAlpha5")
    public String randomAlpha5(String input) {
        String v = dataGenerator.generateAlphabetic(5);
        testContext.set("lastRandomAlpha", v);
        return v;
    }

    @ParameterType("randomNum4")
    public String randomNum4(String input) {
        String v = dataGenerator.generateNumeric(4);
        testContext.set("lastRandomNum", v);
        return v;
    }

    @ParameterType("randomTimestamp")
    public String randomTimestamp(String input) {
        String v = dataGenerator.generateTimestamped();
        testContext.set("lastRandomTimestamp", v);
        return v;
    }

    @ParameterType("randomPhone")
    public String randomPhone(String input) {
        String v = dataGenerator.generatePhoneNumber();
        testContext.set("lastRandomPhone", v);
        return v;
    }
}
