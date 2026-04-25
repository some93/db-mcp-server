package com.mcp.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    void load_applicationYaml_supportsLegacyAndNestedMcpConfig() {
        String path = Paths.get("src", "main", "resources", "application.yml").toString();

        AppConfig config = ConfigLoader.load(path);

        assertNotNull(config);
        assertEquals(2, config.getDatasources().size());
        assertFalse(config.getMcp().isExitOnDisconnect());
        assertTrue(config.getHttp().isEnabled());
    }
}
