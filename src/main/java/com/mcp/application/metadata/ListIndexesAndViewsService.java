package com.mcp.application.metadata;

import com.mcp.domain.audit.AuditLogger;
import com.mcp.domain.cache.DatasourceMetadata;
import com.mcp.domain.cache.MetadataCache;
import com.mcp.domain.health.HealthService;
import com.mcp.infrastructure.support.McpResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 工具 {@code listIndexesAndViews} 的应用服务，从元数据缓存中返回索引和视图信息。
 *
 * <p>直接读取缓存，无实时 SQL 查询。索引数据不含主键（PRIMARY），
 * 仅返回用户创建的普通索引和唯一索引。
 * @author ouyanghang
 */
@Service
public class ListIndexesAndViewsService {

    private final MetadataCache metadataCache;
    private final HealthService healthService;

    public ListIndexesAndViewsService(MetadataCache metadataCache, HealthService healthService) {
        this.metadataCache = metadataCache;
        this.healthService = healthService;
    }

    public McpResponse execute(String datasourceName) {
        long start = System.currentTimeMillis();
        healthService.quickCheck(datasourceName);

        DatasourceMetadata meta = metadataCache.get(datasourceName);
        if (meta == null) {
            AuditLogger.log("listIndexesAndViews", datasourceName, null, false,
                    System.currentTimeMillis() - start, "CACHE_NOT_READY", "Metadata cache not ready.");
            return McpResponse.error("CACHE_NOT_READY",
                    "Metadata cache not ready for datasource: " + datasourceName,
                    System.currentTimeMillis() - start);
        }

        List<Map<String, Object>> indexes = meta.getIndexes() == null ? Collections.emptyList() :
                meta.getIndexes().stream().map(idx -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("indexName", idx.getIndexName());
                    m.put("tableName", idx.getTableName());
                    m.put("unique", idx.isUnique());
                    m.put("columns", idx.getColumns());
                    return m;
                }).collect(Collectors.toList());

        List<Map<String, Object>> views = meta.getViews() == null ? Collections.emptyList() :
                meta.getViews().stream().map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("viewName", v.getViewName());
                    m.put("definition", v.getDefinition());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("indexes", indexes);
        data.put("views", views);
        if (meta.isStale()) data.put("cacheWarning", "Metadata may be stale: " + meta.getStaleReason());
        AuditLogger.log("listIndexesAndViews", datasourceName, null, true, System.currentTimeMillis() - start, null, null);
        return McpResponse.ok(data, System.currentTimeMillis() - start);
    }
}


