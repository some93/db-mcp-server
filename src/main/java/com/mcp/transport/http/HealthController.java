package com.mcp.transport.http;

import com.mcp.domain.health.HealthService;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author ouyanghang
 */
@RestController
public class HealthController {

    private final HealthService healthService;
    private final long startTimeMs = System.currentTimeMillis();

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /** 进程存活检查，始终返回 200 */
    @GetMapping("/health")
    public McpResponse health() {
        long costMs = System.currentTimeMillis() - startTimeMs;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceStatus", "UP");
        data.put("uptimeMs", System.currentTimeMillis() - startTimeMs);
        data.put("datasources", healthService.getAllStatuses().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.getDatasourceName());
            m.put("status", s.getStatus());
            m.put("message", s.getMessage());
            m.put("lastCheckedAt", s.getLastCheckedAtMs());
            return m;
        }).collect(Collectors.toList()));
        return McpResponse.ok(data, 0);
    }

    /** 就绪检查：所有数据源 UP 才返回 200，否则 503 */
    @GetMapping("/ready")
    public ResponseEntity<McpResponse> ready() {
        long start = System.currentTimeMillis();
        if (healthService.allUp()) {
            return ResponseEntity.ok(McpResponse.ok("All datasources are ready.", null, System.currentTimeMillis() - start));
        }
        String notReady = healthService.getAllStatuses().stream()
                .filter(s -> !"UP".equals(s.getStatus()))
                .map(s -> s.getDatasourceName() + "(" + s.getStatus() + ")")
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(503)
                .body(McpResponse.error("DATASOURCE_NOT_READY",
                        "Not ready. Unhealthy datasources: " + notReady,
                        System.currentTimeMillis() - start));
    }
}

