package com.arc.aggregator.controller;

import com.arc.aggregator.service.CsvWriterService;
import com.arc.aggregator.service.SplunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class DataController {

    private final SplunkService splunkService;
    private final CsvWriterService csvWriterService;

    /**
     * Aggregates data by executing a Splunk query and exporting results to CSV
     * 
     * @param request The request containing the Splunk query
     * @return JSON response with status and file path
     */
    @PostMapping("/aggregate")
    public ResponseEntity<Map<String, String>> aggregateData(@RequestBody AggregateRequest request) {
        try {
            log.info("Received aggregation request for query: {}", request.getQuery());

            // Execute Splunk query
            Map<String, Object> results = splunkService.runQuery(request.getQuery());

            // Write results to CSV
            String csvFilePath = csvWriterService.writeToCsv(results);

            // Return success response
            Map<String, String> response = Map.of(
                "status", "success",
                "file", csvFilePath
            );

            log.info("Aggregation completed successfully. CSV file: {}", csvFilePath);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during aggregation: {}", e.getMessage(), e);
            
            Map<String, String> errorResponse = Map.of(
                "status", "error",
                "message", e.getMessage()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Request DTO for aggregation endpoint
     */
    public static class AggregateRequest {
        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
} 