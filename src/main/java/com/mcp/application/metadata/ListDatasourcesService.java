package com.mcp.application.metadata;

import com.mcp.domain.health.DatasourceHealthStatus;
import com.mcp.domain.health.HealthService;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.config.DatasourceConfig;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ouyanghang
 */
@Service
public class ListDatasourcesService {

    private final AppConfig appConfig;
    private final HealthService healthService;

    public ListDatasourcesService(AppConfig appConfig, HealthService healthService) {
        this.appConfig = appConfig;
        this.healthService = healthService;
    }

    public McpResponse execute() {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> datasources = new ArrayList<>();

        for (DatasourceConfig ds : appConfig.getDatasources()) {
            DatasourceHealthStatus health = healthService.getStatus(ds.getName());
            Map<String, Object> item = new HashMap<>();
            item.put("name", ds.getName());
            item.put("type", ds.getType());
            item.put("healthStatus", health.getStatus());
            item.put("lastHealthCheckAt", health.getLastCheckedAtMs());
            item.put("healthMessage", health.getMessage());
            datasources.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("datasources", datasources);
        return McpResponse.ok(data, System.currentTimeMillis() - start);
    }
}

