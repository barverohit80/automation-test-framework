package com.automation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EnvironmentConfig.class)
public class SpringConfig {
    // @SpringBootApplication already scans com.automation.
    // This class exists solely to register EnvironmentConfig as a bean.
}
