package com.orhoj.trackunit.demo.sync;

import com.orhoj.demo.rentalapi.model.SynchronizationRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DryRunWriter {

    private final ObjectMapper objectMapper;

    public DryRunWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(Path outputFile, List<SynchronizationRequest> requests) throws IOException {
        // Clean file for each dry run.
        Files.writeString(outputFile, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        int seq = 0;
        for (SynchronizationRequest body : requests) {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("X-Update-Strategy", "REPLACE");
            // TODO Is the typo correct?
            headers.put("X-Validate-Overlaping-Contract-Items", "false");
            headers.put("X-Validate-Asset-Exists", "false");
            headers.put("X-Validate-Rental-Period", "true");

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("seq", ++seq);
            record.put("method", "POST");
            record.put("path", "/v1/synchronization");
            record.put("headers", headers);
            record.put("body", body);

            String jsonLine = objectMapper.writeValueAsString(record);
            Files.writeString(outputFile, jsonLine + System.lineSeparator(), StandardOpenOption.APPEND);
        }
    }
}
