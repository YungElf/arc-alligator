package com.arc.aggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import lombok.Data;

@Configuration
@EnableConfigurationProperties(AppConfig.AppProperties.class)
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Data
    @ConfigurationProperties(prefix = "app")
    public static class AppProperties {
        private Splunk splunk = new Splunk();
        private String outputDirectory = "output";

        @Data
        public static class Splunk {
            private String baseUrl;
            private String authToken;
            private String username;
            private String password;
        }
    }
} 