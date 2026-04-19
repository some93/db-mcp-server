package com.mcp.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;

/**
 * @author ouyanghang
 */
public class ConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static AppConfig load(String configPath) {
        File file = new File(configPath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Config file not found: " + configPath);
        }
        try {
            AppConfig config = YAML_MAPPER.readValue(file, AppConfig.class);
            validate(config);
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse config file: " + configPath, e);
        }
    }

    private static void validate(AppConfig config) {
        if (config.getDatasources() == null || config.getDatasources().isEmpty()) {
            throw new IllegalStateException("At least one datasource must be configured");
        }
        for (DatasourceConfig ds : config.getDatasources()) {
            if (ds.getName() == null || ds.getName().isBlank()) {
                throw new IllegalStateException("Datasource name must not be blank");
            }
            if (ds.getType() == null || ds.getType().isBlank()) {
                throw new IllegalStateException("Datasource type must not be blank: " + ds.getName());
            }
            if (ds.getUrl() == null || ds.getUrl().isBlank()) {
                throw new IllegalStateException("Datasource url must not be blank: " + ds.getName());
            }
        }
    }
}

