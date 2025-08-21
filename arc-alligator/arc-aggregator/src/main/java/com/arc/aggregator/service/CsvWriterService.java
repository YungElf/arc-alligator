package com.arc.aggregator.service;

import com.arc.aggregator.config.AppConfig.AppProperties;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvWriterService {

    private final AppProperties appProperties;

    /**
     * Writes Splunk query results to a CSV file
     * 
     * @param results The results from Splunk query
     * @return The path to the generated CSV file
     */
    public String writeToCsv(Map<String, Object> results) {
        try {
            // Ensure output directory exists
            Path outputDir = Paths.get(appProperties.getOutputDirectory());
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "splunk_results_" + timestamp + ".csv";
            Path filePath = outputDir.resolve(filename);

            // Extract results array from Splunk response
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultsList = extractResultsList(results);

            if (resultsList == null || resultsList.isEmpty()) {
                log.warn("No results to write to CSV");
                return filePath.toString();
            }

            // Write to CSV
            try (CSVWriter writer = new CSVWriter(new FileWriter(filePath.toFile()))) {
                // Write header
                if (!resultsList.isEmpty()) {
                    String[] headers = resultsList.get(0).keySet().toArray(new String[0]);
                    writer.writeNext(headers);

                    // Write data rows
                    for (Map<String, Object> row : resultsList) {
                        String[] csvRow = new String[headers.length];
                        for (int i = 0; i < headers.length; i++) {
                            Object value = row.get(headers[i]);
                            csvRow[i] = value != null ? value.toString() : "";
                        }
                        writer.writeNext(csvRow);
                    }
                }
            }

            log.info("CSV file written successfully: {}", filePath);
            return filePath.toString();

        } catch (IOException e) {
            log.error("Error writing CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to write CSV file", e);
        }
    }

    /**
     * Extracts the results list from Splunk response
     * Splunk typically returns results in a nested structure
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractResultsList(Map<String, Object> results) {
        // Try different possible result structures
        if (results.containsKey("results")) {
            return (List<Map<String, Object>>) results.get("results");
        } else if (results.containsKey("entry")) {
            return (List<Map<String, Object>>) results.get("entry");
        } else if (results.containsKey("data")) {
            return (List<Map<String, Object>>) results.get("data");
        } else {
            // If no standard structure, try to find any list in the response
            for (Object value : results.values()) {
                if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> list = (List<Map<String, Object>>) value;
                    if (!list.isEmpty() && list.get(0) instanceof Map) {
                        return list;
                    }
                }
            }
        }
        return null;
    }
} 