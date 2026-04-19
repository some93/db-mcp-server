package com.mcp.application.metadata;

import com.mcp.adapter.spi.TableInfo;
import com.mcp.domain.audit.AuditLogger;
import com.mcp.domain.cache.DatasourceMetadata;
import com.mcp.domain.cache.MetadataCache;
import com.mcp.domain.health.HealthService;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 工具 {@code listTables} 的应用服务，从元数据缓存中返回数据源的表/视图列表。
 *
 * <p>直接读取缓存，无实时 SQL 查询，调用开销极低。
 * 返回结果按类型排序（TABLE 优先于 VIEW），组内按名称升序。
 * 缓存过期时在响应 {@code data.cacheWarning} 中提示，不中断返回。
 * @author ouyanghang
 */
@Service
public class ListTablesService {

    private final MetadataCache metadataCache;
    private final HealthService healthService;

    public ListTablesService(MetadataCache metadataCache, HealthService healthService) {
        this.metadataCache = metadataCache;
        this.healthService = healthService;
    }

    public McpResponse execute(String datasourceName) {
        long start = System.currentTimeMillis();
        healthService.quickCheck(datasourceName);

        DatasourceMetadata meta = metadataCache.get(datasourceName);
        if (meta == null) {
            AuditLogger.log("listTables", datasourceName, null, false,
                    System.currentTimeMillis() - start, "CACHE_NOT_READY", "Metadata cache not ready.");
            return McpResponse.error("CACHE_NOT_READY",
                    "Metadata cache not ready for datasource: " + datasourceName,
                    System.currentTimeMillis() - start);
        }

        List<TableInfo> tables = meta.getTables();
        if (tables == null || tables.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("tables", Collections.emptyList());
            data.put("hint", "No tables found. You can use createTable to create one.");
            if (meta.isStale()) data.put("cacheWarning", "Metadata may be stale: " + meta.getStaleReason());
            AuditLogger.log("listTables", datasourceName, null, true, System.currentTimeMillis() - start, null, null);
            return McpResponse.ok(data, System.currentTimeMillis() - start);
        }

        // 排序：TABLE 优先，组内按名升序
        List<Map<String, Object>> sorted = tables.stream()
                .sorted(Comparator.comparing(TableInfo::getTableType).thenComparing(TableInfo::getName))
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", t.getName());
                    m.put("tableType", t.getTableType());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("tables", sorted);
        if (meta.isStale()) data.put("cacheWarning", "Metadata may be stale: " + meta.getStaleReason());
        AuditLogger.log("listTables", datasourceName, null, true, System.currentTimeMillis() - start, null, null);
        return McpResponse.ok(data, System.currentTimeMillis() - start);
    }
}


