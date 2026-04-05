package com.automation.uitestgen.model;

import lombok.Data;
import java.util.List;

@Data
public class GeneratedUITest {
    private String pageObjectContent;   // full Java Page Object class
    private String featureContent;      // Gherkin .feature file
    private String stepDefContent;      // Java step definition class

    private String pageObjectFilePath;
    private String featureFilePath;
    private String stepDefFilePath;

    private List<String> generatedLocators;  // summary for reporting
    private List<String> conflicts;          // duplicate step warnings
}
