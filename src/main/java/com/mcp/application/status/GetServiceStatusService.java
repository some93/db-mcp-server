package com.mcp.application.status;

import com.mcp.bootstrap.HttpPortLogger;
import com.mcp.domain.cache.DatasourceMetadata;
import com.mcp.domain.cache.MetadataCache;
import com.mcp.domain.health.DatasourceHealthStatus;
import com.mcp.domain.health.HealthService;
import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ouyanghang
 */
@Service
public class GetServiceStatusService {

    private final AppConfig appConfig;
    private final HealthService healthService;
    private final MetadataCache metadataCache;
    private final HttpPortLogger httpPortLogger;
    private final long startTimeMs = System.currentTimeMillis();

    public GetServiceStatusService(AppConfig appConfig, HealthService healthService,
                                   MetadataCache metadataCache, HttpPortLogger httpPortLogger) {
        this.appConfig = appConfig;
        this.healthService = healthService;
        this.metadataCache = metadataCache;
        this.httpPortLogger = httpPortLogger;
    }

    public McpResponse execute() {
        long start = System.currentTimeMillis();

        // 服务基础信息
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", "1.0.0");
        data.put("uptimeMs", System.currentTimeMillis() - startTimeMs);
        data.put("jvmUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());

        // 运行模式
        Map<String, Object> modes = new LinkedHashMap<>();
        modes.put("mcpStdio", true);
        modes.put("httpDebug", appConfig.getHttp().isEnabled());
        int httpPort = httpPortLogger.getPort();
        if (appConfig.getHttp().isEnabled() && httpPort > 0) {
            modes.put("httpPort", httpPort);
            modes.put("httpBaseUrl", "http://127.0.0.1:" + httpPort);
        }
        data.put("modes", modes);

        // 数据源健康状态
        List<Map<String, Object>> datasources = appConfig.getDatasources().stream().map(ds -> {
            DatasourceHealthStatus h = healthService.getStatus(ds.getName());
            DatasourceMetadata meta = metadataCache.get(ds.getName());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", ds.getName());
            m.put("type", ds.getType());
            m.put("healthStatus", h.getStatus());
            m.put("healthMessage", h.getMessage());
            m.put("lastHealthCheckAt", h.getLastCheckedAtMs());
            if (meta != null) {
                m.put("cachedTables", meta.getTables() == null ? 0 : meta.getTables().size());
                m.put("cacheLoadedAt", meta.getLoadedAtMs());
                m.put("cacheStale", meta.isStale());
                if (meta.isStale()) m.put("cacheStaleReason", meta.getStaleReason());
            } else {
                m.put("cachedTables", null);
                m.put("cacheLoadedAt", null);
            }
            return m;
        }).collect(Collectors.toList());
        data.put("datasources", datasources);

        // 全局配置摘要
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("maxRows", appConfig.getSql().getMaxRows());
        config.put("sqlTimeoutSeconds", appConfig.getSql().getTimeoutSeconds());
        config.put("sqlMaxLength", appConfig.getSql().getMaxLength());
        config.put("batchInsertMaxRows", appConfig.getSql().getBatchInsertMaxRows());
        config.put("cacheRefreshIntervalSeconds", appConfig.getCache().getRefreshIntervalSeconds());
        config.put("healthCheckIntervalSeconds", appConfig.getHealth().getCheckIntervalSeconds());
        config.put("timezone", appConfig.getTimezone());
        data.put("config", config);

        return McpResponse.ok(data, System.currentTimeMillis() - start);
    }
}

