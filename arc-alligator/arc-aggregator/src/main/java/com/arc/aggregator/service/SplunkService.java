package com.arc.aggregator.service;

import com.arc.aggregator.config.AppConfig.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SplunkService {

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;

    /**
     * Executes a Splunk query and returns results as JSON
     * 
     * @param query The Splunk SPL query to execute
     * @return Map containing the query results
     */
    public Map<String, Object> runQuery(String query) {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(appProperties.getSplunk().getBaseUrl())
                .path("/services/search/jobs/export")
                .queryParam("search", query)
                .queryParam("output_mode", "json")
                .queryParam("count", "0")
                .build()
                .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + appProperties.getSplunk().getAuthToken());
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("Executing Splunk query: {}", query);
            ResponseEntity<Map> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Splunk query executed successfully");
                return response.getBody();
            } else {
                log.error("Splunk query failed with status: {}", response.getStatusCode());
                throw new RuntimeException("Splunk query failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error executing Splunk query: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to execute Splunk query", e);
        }
    }
} 